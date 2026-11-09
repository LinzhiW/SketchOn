// A small HTTP server between the headset and fal. The fal key lives here, never in the APK
// or the browser.
//
//   POST /api/signup, /api/login  { "name": "...", "password": "..." } -> 200 { "token", "user": { "id", "name" } }
//   POST /api/logout;  GET /api/me -> 200 { "user" }
//   Requests marked [login] need "Authorization: Bearer <token>".
//   POST /api/render [login] { "sketch": "data:image/png;base64,...", "style": "gold",
//                       "engine": "klein" | "nano", "seed": 1234 }
//   -> 200 { "imageUrl": "...", "requestId": "...", "ms": 840 }
//   POST /api/to3d [login] { "image": "data:image/jpeg;base64,...", "name": "...", "material": "clay" }
//                     -> 200 { "jobId": "..." }
//   POST /api/jobs/ID/name [login] { "name": "..." }  names a piece while it is being made
//   GET  /api/jobs/ID -> 200 { "status": "running" | "done" | "failed", "modelUrl"?, "error"? }
//   GET  /models/ID.glb  a finished model;  GET /models/ID.jpg  the render it was made from
//   GET  /api/library -> 200 { "pieces": [{ "id", "name"|null, "author"|null, "material"|null, "modelUrl", "pictureUrl"|null, "createdAt" }] }
//   GET  /            the sketch test page (public/sketch.html)

import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { fal } from "@fal-ai/client";
import { render, RenderError } from "./render.js";
import { createTo3d, isJobId } from "./to3d.js";
import { openDb } from "./db.js";

const PORT = Number(process.env.PORT ?? 8787);
const MAX_BODY_BYTES = 5 * 1024 * 1024;
const PAGE = new URL("../public/sketch.html", import.meta.url);
const MODELS_DIR = fileURLToPath(new URL("../models/", import.meta.url));
const DATA_DIR = fileURLToPath(new URL("../data/", import.meta.url));
// Pieces made before accounts existed were all made by the project's author.
const LEGACY_AUTHOR = process.env.LEGACY_AUTHOR ?? "Linzhi";

if (!process.env.FAL_KEY) {
  console.error("FAL_KEY is not set. Put it in server/.env.local.");
  process.exit(1);
}
fal.config({ credentials: process.env.FAL_KEY });
mkdirSync(MODELS_DIR, { recursive: true });
mkdirSync(DATA_DIR, { recursive: true });
const db = openDb(`${DATA_DIR}sketchon.db`);

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

// Pieces from before accounts kept their name and material in a .json file beside the model.
for (const file of readdirSync(MODELS_DIR).filter((f) => f.endsWith(".glb"))) {
  const id = file.slice(0, -4);
  if (isJobId(id) && db.importLegacy(id, readInfo(id), LEGACY_AUTHOR)) {
    console.log(`imported ${id} (${readInfo(id).name ?? "unnamed"}) as made by ${LEGACY_AUTHOR}`);
  }
}

/** Every finished piece, newest first, with its maker. */
function library() {
  return db
    .pieces()
    .filter((p) => isJobId(p.id) && existsSync(modelPath(p.id)))
    .map((p) => ({
      ...p,
      modelUrl: `/models/${p.id}.glb`,
      pictureUrl: existsSync(picturePath(p.id)) ? `/models/${p.id}.jpg` : null,
    }));
}

/** The signed-in user, or a 401. */
function userOf(req) {
  const header = req.headers.authorization ?? "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  const user = db.userForToken(token);
  if (!user) throw new RenderError(401, "Please log in.");
  return { user, token };
}

const to3d = createTo3d({
  fal,
  store: {
    has: (id) => existsSync(modelPath(id)),
    save: (id, bytes) => writeFileSync(modelPath(id), bytes),
    savePicture: (id, bytes) => writeFileSync(picturePath(id), bytes),
    saveInfo: (id, info) => db.addPiece({ id, ...info }),
    renameInfo: (id, name, user) => db.renamePiece(id, name, user),
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
  if (req.method === "POST" && (url === "/api/signup" || url === "/api/login")) {
    const { name, password } = await readJson(req);
    const session = url === "/api/signup" ? db.signUp(name, password) : db.logIn(name, password);
    console.log(`${url === "/api/signup" ? "signed up" : "logged in"}: ${session.user.name}`);
    return send(res, 200, session);
  }
  if (req.method === "POST" && url === "/api/logout") {
    const header = req.headers.authorization ?? "";
    db.logOut(header.startsWith("Bearer ") ? header.slice(7) : "");
    return send(res, 200, { ok: true });
  }
  if (req.method === "GET" && url === "/api/me") return send(res, 200, { user: userOf(req).user });
  if (req.method === "POST" && url === "/api/render") {
    userOf(req);
    return send(res, 200, await render(await readJson(req), run));
  }
  if (req.method === "POST" && url === "/api/to3d") {
    const { user } = userOf(req);
    const { image, name, material } = await readJson(req);
    const job = await to3d.start(image, name, material, user);
    console.log(`3D job started: ${job.jobId} by ${user.name}`);
    return send(res, 200, job);
  }
  const naming = url.match(/^\/api\/jobs\/([^/]+)\/name$/);
  if (req.method === "POST" && naming) {
    const { user } = userOf(req);
    const { name } = await readJson(req);
    return send(res, 200, to3d.rename(naming[1], name, user));
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
