// A small HTTP server between the headset and fal. The fal key lives here, never in the APK
// or the browser.
//
//   POST /api/render  { "sketch": "data:image/png;base64,...", "style": "gold",
//                       "engine": "klein" | "nano", "seed": 1234 }
//   -> 200 { "imageUrl": "...", "requestId": "...", "ms": 840 }
//   POST /api/to3d    { "image": "data:image/jpeg;base64,...", "name": "...", "material": "clay" }
//                     -> 200 { "jobId": "..." }
//   POST /api/jobs/ID/name { "name": "..." }  names a piece while it is being made
//   GET  /api/jobs/ID -> 200 { "status": "running" | "done" | "failed", "modelUrl"?, "error"? }
//   GET  /models/ID.glb  a finished model;  GET /models/ID.jpg  the render it was made from
//   GET  /api/library -> 200 { "pieces": [{ "id", "name"|null, "material"|null, "modelUrl", "pictureUrl"|null, "createdAt" }] }
//   GET  /            the sketch test page (public/sketch.html)

import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { fal } from "@fal-ai/client";
import { render, RenderError } from "./render.js";
import { createTo3d, isJobId } from "./to3d.js";

const PORT = Number(process.env.PORT ?? 8787);
const MAX_BODY_BYTES = 5 * 1024 * 1024;
const PAGE = new URL("../public/sketch.html", import.meta.url);
const MODELS_DIR = fileURLToPath(new URL("../models/", import.meta.url));

if (!process.env.FAL_KEY) {
  console.error("FAL_KEY is not set. Put it in server/.env.local.");
  process.exit(1);
}
fal.config({ credentials: process.env.FAL_KEY });
mkdirSync(MODELS_DIR, { recursive: true });

async function run(model, input) {
  const result = await fal.run(model, { input });
  return { images: result?.data?.images, requestId: result?.requestId };
}

const modelPath = (id) => `${MODELS_DIR}${id}.glb`;
const picturePath = (id) => `${MODELS_DIR}${id}.jpg`;
const infoPath = (id) => `${MODELS_DIR}${id}.json`;
const readInfo = (id) => {
  try {
    return JSON.parse(readFileSync(infoPath(id), "utf8"));
  } catch {
    return {};
  }
};

/** Every finished piece, newest first. There are no accounts yet, so this is everyone's work. */
function library() {
  return readdirSync(MODELS_DIR)
    .filter((f) => f.endsWith(".glb"))
    .map((f) => f.slice(0, -4))
    .filter(isJobId)
    .map((id) => ({
      id,
      name: readInfo(id).name ?? null,
      material: readInfo(id).material ?? null,
      modelUrl: `/models/${id}.glb`,
      pictureUrl: existsSync(picturePath(id)) ? `/models/${id}.jpg` : null,
      createdAt: statSync(modelPath(id)).mtimeMs,
    }))
    .sort((a, b) => b.createdAt - a.createdAt);
}
const to3d = createTo3d({
  fal,
  store: {
    has: (id) => existsSync(modelPath(id)),
    save: (id, bytes) => writeFileSync(modelPath(id), bytes),
    savePicture: (id, bytes) => writeFileSync(picturePath(id), bytes),
    saveInfo: (id, info) => writeFileSync(infoPath(id), JSON.stringify(info)),
    renameInfo: (id, name) => writeFileSync(infoPath(id), JSON.stringify({ ...readInfo(id), name })),
    urlFor: (id) => `/models/${id}.glb`,
  },
  download: async (url) => {
    const res = await fetch(url);
    if (!res.ok) throw new RenderError(502, `could not download the model (HTTP ${res.status})`);
    return Buffer.from(await res.arrayBuffer());
  },
  log: (line) => console.log(line),
});

function send(res, status, body) {
  res.writeHead(status, { "Content-Type": "application/json" });
  res.end(JSON.stringify(body));
}

async function readJson(req) {
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) throw new RenderError(413, "request is too large");
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw new RenderError(400, "body must be JSON");
  }
}

// fal error bodies explain things like an empty balance. They never contain the key,
// but strip anything key-shaped anyway before it reaches a log or the page.
function reasonOf(error) {
  const detail = error?.body?.detail;
  const text =
    typeof detail === "string" ? detail : detail ? JSON.stringify(detail) : error?.message ?? "failed";
  return text.replace(/fal_sk\S*/gi, "[hidden]").slice(0, 300);
}

async function route(req, res) {
  const url = req.url.split("?")[0];
  if (req.method === "GET" && (url === "/" || url === "/sketch")) {
    res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
    return res.end(await readFile(PAGE));
  }
  if (req.method === "POST" && url === "/api/render") {
    return send(res, 200, await render(await readJson(req), run));
  }
  if (req.method === "POST" && url === "/api/to3d") {
    const { image, name, material } = await readJson(req);
    const job = await to3d.start(image, name, material);
    console.log(`3D job started: ${job.jobId}`);
    return send(res, 200, job);
  }
  const naming = url.match(/^\/api\/jobs\/([^/]+)\/name$/);
  if (req.method === "POST" && naming) {
    const { name } = await readJson(req);
    return send(res, 200, to3d.rename(naming[1], name));
  }
  const job = url.match(/^\/api\/jobs\/([^/]+)$/);
  if (req.method === "GET" && job) {
    try {
      return send(res, 200, await to3d.check(job[1]));
    } catch (error) {
      if (error instanceof RenderError && error.status === 400) throw error;
      const reason = reasonOf(error);
      console.error(`3D job ${job[1]} failed: ${reason}`);
      return send(res, 200, { status: "failed", error: reason });
    }
  }
  if (req.method === "GET" && url === "/api/library") return send(res, 200, { pieces: library() });
  const file = url.match(/^\/models\/([^/]+)\.(glb|jpg)$/);
  if (req.method === "GET" && file && isJobId(file[1])) {
    const path = file[2] === "glb" ? modelPath(file[1]) : picturePath(file[1]);
    if (existsSync(path)) {
      res.writeHead(200, { "Content-Type": file[2] === "glb" ? "model/gltf-binary" : "image/jpeg" });
      return res.end(readFileSync(path));
    }
  }
  send(res, 404, { error: "not found" });
}

createServer(async (req, res) => {
  try {
    await route(req, res);
  } catch (error) {
    const status = error instanceof RenderError ? error.status : error?.status ?? 500;
    const reason = reasonOf(error);
    console.error(`${req.method} ${req.url} failed (${status}): ${reason}`);
    send(res, status, { error: reason });
  }
}).listen(PORT, () => console.log(`render server on http://localhost:${PORT}`));
