import { test } from "node:test";
import assert from "node:assert/strict";
import { createTo3d, TRIPO_MODEL } from "../src/to3d.js";
import { RenderError } from "../src/render.js";

const IMAGE = "data:image/jpeg;base64,/9j/4AAQ";

function fakes({ status = "COMPLETED", data = { model_mesh: { url: "https://fal.media/m.glb" } } } = {}) {
  const calls = { uploads: 0, submits: [], downloads: [] };
  const saved = new Map();
  const fal = {
    storage: { upload: async () => (calls.uploads++, "https://fal.media/in.jpg") },
    queue: {
      submit: async (model, { input }) => (calls.submits.push({ model, input }), { request_id: "job-12345678" }),
      status: async () => ({ status, queue_position: 2 }),
      result: async () => ({ data }),
    },
  };
  const store = {
    has: (id) => saved.has(id),
    save: (id, bytes) => saved.set(id, bytes),
    urlFor: (id) => `/models/${id}.glb`,
  };
  const download = async (url) => (calls.downloads.push(url), Buffer.from("glb"));
  return { to3d: createTo3d({ fal, store, download }), calls, saved };
}

test("start uploads a data URI and submits Tripo facing the drawing", async () => {
  const { to3d, calls } = fakes();
  assert.deepEqual(await to3d.start(IMAGE), { jobId: "job-12345678" });
  assert.equal(calls.uploads, 1);
  assert.equal(calls.submits[0].model, TRIPO_MODEL);
  assert.equal(calls.submits[0].input.image_url, "https://fal.media/in.jpg");
  assert.equal(calls.submits[0].input.orientation, "align_image");
});

test("start rejects anything that is not an image", async () => {
  const { to3d } = fakes();
  await assert.rejects(to3d.start("http://example.com/a.jpg"), RenderError);
  await assert.rejects(to3d.start(42), RenderError);
});

test("check reports a running job", async () => {
  const { to3d } = fakes({ status: "IN_PROGRESS" });
  assert.deepEqual(await to3d.check("job-12345678"), { status: "running", queuePosition: 2 });
});

test("check downloads a finished model once, preferring the PBR one", async () => {
  const { to3d, calls, saved } = fakes({
    data: { model_mesh: { url: "https://fal.media/m.glb" }, pbr_model: { url: "https://fal.media/p.glb" } },
  });
  const done = { status: "done", modelUrl: "/models/job-12345678.glb" };
  assert.deepEqual(await to3d.check("job-12345678"), done);
  assert.deepEqual(await to3d.check("job-12345678"), done);
  assert.deepEqual(calls.downloads, ["https://fal.media/p.glb"]);
  assert.equal(saved.size, 1);
});

test("check rejects a job id that could escape the models folder", async () => {
  const { to3d } = fakes();
  await assert.rejects(to3d.check("../../etc/passwd"), (e) => e.status === 400);
});
