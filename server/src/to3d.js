// Turns a finished render into a 3D model with Tripo on fal.
//
// Tripo takes 30 to 90 seconds, so this is a job: start it, then poll it. When it finishes,
// the GLB is downloaded once, put into the app's standard ring pose (ringfit.js), and kept in
// server/models/, so the headset fetches it from here.
// orientation "align_image" keeps the drawing's up as +Y, but the ring itself may still come
// back standing or lying flat, which is why every model is posed before it is kept.
// Schema: https://fal.ai/models/tripo3d/tripo/v2.5/image-to-3d/llms.txt

import { RenderError } from "./render.js";
import { poseRing } from "./ringfit.js";

export const TRIPO_MODEL = "tripo3d/tripo/v2.5/image-to-3d";
const IMAGE_PREFIX = /^data:image\/(png|jpeg|webp);base64,/;
const MAX_IMAGE_CHARS = 4 * 1024 * 1024;
const JOB_ID = /^[A-Za-z0-9-]{8,80}$/;
const MAX_NAME = 40;
const MATERIAL_ID = /^[a-z]{1,20}$/;

export function tripoInput(imageUrl) {
  return {
    image_url: imageUrl,
    texture: "standard",
    pbr: true,
    orientation: "align_image",
    face_limit: 30000,
  };
}

export function isJobId(id) {
  return typeof id === "string" && JOB_ID.test(id);
}

/**
 * `fal` needs: storage.upload(blob), queue.submit(model, {input}), queue.status(model, {requestId}),
 * queue.result(model, {requestId}). `store` needs: has(id), save(id, bytes), urlFor(id).
 * `download(url)` returns the file bytes. All are passed in so tests never call the paid API.
 * `pose(glb)` puts the ring in the app's standard pose (ringfit.js); tests may replace it.
 */
export function createTo3d({ fal, store, download, pose: pose_, log = () => {} }) {
  async function start(image, name, material, user) {
    if (typeof image !== "string") throw new RenderError(400, "image is required");
    const title = typeof name === "string" ? name.trim().slice(0, MAX_NAME) : "";
    let url = image;
    let picture = null;
    if (IMAGE_PREFIX.test(image)) {
      if (image.length > MAX_IMAGE_CHARS) throw new RenderError(413, "image is too large");
      const [header, data] = image.split(",");
      const type = header.slice(5, header.indexOf(";"));
      picture = Buffer.from(data, "base64");
      url = await fal.storage.upload(new Blob([picture], { type }));
    } else if (!/^https:\/\//.test(image)) {
      throw new RenderError(400, "image must be a data URI or an https URL");
    }
    const { request_id: jobId } = await fal.queue.submit(TRIPO_MODEL, { input: tripoInput(url) });
    // The render becomes the piece's picture in the gallery.
    if (picture) store.savePicture?.(jobId, picture);
    const made = typeof material === "string" && MATERIAL_ID.test(material) ? material : null;
    store.saveInfo?.(jobId, { name: title || null, material: made, createdAt: Date.now(), user });
    return { jobId };
  }

  async function check(jobId) {
    if (!isJobId(jobId)) throw new RenderError(400, "bad job id");
    if (store.has(jobId)) return { status: "done", modelUrl: store.urlFor(jobId) };
    const status = await fal.queue.status(TRIPO_MODEL, { requestId: jobId, logs: false });
    if (status.status !== "COMPLETED") {
      return { status: "running", queuePosition: status.queue_position ?? null };
    }
    const result = await fal.queue.result(TRIPO_MODEL, { requestId: jobId });
    const mesh = result?.data?.pbr_model?.url ?? result?.data?.model_mesh?.url;
    if (!mesh) return { status: "failed", error: "Tripo returned no model" };
    const raw = await download(mesh);
    store.save(jobId, posed(raw, jobId));
    return { status: "done", modelUrl: store.urlFor(jobId) };
  }

  // A model that cannot be posed is still worth showing, in whatever pose Tripo gave it.
  function posed(raw, jobId) {
    try {
      const { glb, pose } = (pose_ ?? poseRing)(raw);
      log(`3D job ${jobId}: hole along ${pose.holeAxis}, hole found: ${pose.holeFound}, parts: ${pose.parts ?? 1}`);
      return glb;
    } catch (error) {
      log(`3D job ${jobId}: could not pose the ring (${error.message}); keeping Tripo's pose`);
      return raw;
    }
  }

  /** Names a piece after its job started, so the user can name it while it is made. */
  function rename(jobId, name, user) {
    if (!isJobId(jobId)) throw new RenderError(400, "bad job id");
    const title = typeof name === "string" ? name.trim().slice(0, MAX_NAME) : "";
    if (!title) throw new RenderError(400, "name is required");
    store.renameInfo?.(jobId, title, user);
    return { jobId, name: title };
  }

  return { start, check, rename };
}
