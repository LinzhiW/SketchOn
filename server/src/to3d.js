// Turns a finished render into a 3D model with Tripo on fal.
//
// Tripo takes 30 to 90 seconds, so this is a job: start it, then poll it. When it finishes,
// the GLB is downloaded once into server/models/, so the headset fetches it from here.
// orientation "align_image" keeps the model facing the way the drawing faces: X right, Y up,
// the ring hole along Z. That is how the app's ring rig expects a ring to sit.
// Schema: https://fal.ai/models/tripo3d/tripo/v2.5/image-to-3d/llms.txt

import { RenderError } from "./render.js";

export const TRIPO_MODEL = "tripo3d/tripo/v2.5/image-to-3d";
const IMAGE_PREFIX = /^data:image\/(png|jpeg|webp);base64,/;
const MAX_IMAGE_CHARS = 4 * 1024 * 1024;
const JOB_ID = /^[A-Za-z0-9-]{8,80}$/;

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
 */
export function createTo3d({ fal, store, download }) {
  async function start(image) {
    if (typeof image !== "string") throw new RenderError(400, "image is required");
    let url = image;
    if (IMAGE_PREFIX.test(image)) {
      if (image.length > MAX_IMAGE_CHARS) throw new RenderError(413, "image is too large");
      const [header, data] = image.split(",");
      const type = header.slice(5, header.indexOf(";"));
      url = await fal.storage.upload(new Blob([Buffer.from(data, "base64")], { type }));
    } else if (!/^https:\/\//.test(image)) {
      throw new RenderError(400, "image must be a data URI or an https URL");
    }
    const { request_id: jobId } = await fal.queue.submit(TRIPO_MODEL, { input: tripoInput(url) });
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
    store.save(jobId, await download(mesh));
    return { status: "done", modelUrl: store.urlFor(jobId) };
  }

  return { start, check };
}
