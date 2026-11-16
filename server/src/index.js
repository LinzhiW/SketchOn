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
//
//   Colourways, likes, saves, wears and notifications (docs/colourways-plan.md):
//   GET  /api/pieces/ID              one ring's page: counts, the author's colourways, everyone else's
//   POST /api/pieces/ID/colourways [login] { "paints": { "0": argb }, "texture"?: PNG data URI,
//                                     "picture"?: JPEG data URI } -> { "id" }
//   POST /api/pieces/ID/cover [login] { "colourwayId": "..." | null };  DELETE /api/pieces/ID [login]
//   DELETE /api/colourways/ID [login];  POST /api/colourways/ID/feature|hide [login] { "on": true }
//   POST /api/likes [login] { "kind": "piece" | "colourway", "id", "on" }
//   POST /api/saves [login] { "pieceId", "on" };  POST /api/wears [login] { "pieceId", "colourwayId"|null }
//   GET  /api/me/library [login];  GET /api/notifications [login];  POST /api/notifications/seen [login]
//   GET  /colourways/ID.png  a painted texture;  GET /colourways/ID.jpg  a colourway's picture
//
//   GET  /            the sketch test page (public/sketch.html);  GET /health  for the host
//
// Where things are kept is set by the environment:
//   DATABASE_URL               Postgres (Supabase) for accounts and pieces; else server/data/sketchon.db
//   SUPABASE_URL + SUPABASE_SERVICE_KEY   Supabase Storage for models and pictures; else server/models/
//   PORT                       set by the host (Render); 8787 on a computer

import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { existsSync, mkdirSync, readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { fal } from "@fal-ai/client";
import { render, RenderError } from "./render.js";
import { createTo3d, isJobId } from "./to3d.js";
import { openDb } from "./db.js";
import { isColourwayId } from "./social.js";
import { localStorage, supabaseStorage } from "./storage.js";

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
const db = await openDb(process.env.DATABASE_URL || `${DATA_DIR}sketchon.db`);
const cloud = Boolean(process.env.SUPABASE_URL && process.env.SUPABASE_SERVICE_KEY);
const files = cloud
  ? await supabaseStorage({ url: process.env.SUPABASE_URL, serviceKey: process.env.SUPABASE_SERVICE_KEY })
  : localStorage(MODELS_DIR);
console.log(
  `accounts in ${process.env.DATABASE_URL ? "Postgres" : "SQLite"}, files in ${cloud ? "Supabase Storage" : "server/models"}`,
);

async function run(model, input) {
  const result = await fal.run(model, { input });
  return { images: result?.data?.images, requestId: result?.requestId };
}

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
// Only on a computer: in the cloud they are moved over by scripts/migrate-to-cloud.js.
if (!cloud) {
  for (const file of readdirSync(MODELS_DIR).filter((f) => f.endsWith(".glb"))) {
    const id = file.slice(0, -4);
    if (isJobId(id) && (await db.importLegacy(id, readInfo(id), LEGACY_AUTHOR))) {
      console.log(`imported ${id} (${readInfo(id).name ?? "unnamed"}) as made by ${LEGACY_AUTHOR}`);
    }
  }
}

const fileUrl = (id, ext) => files.publicUrl(`${id}.${ext}`) ?? `/models/${id}.${ext}`;

const colourwayUrl = (id, ext) => files.publicUrl(`${id}.${ext}`) ?? `/colourways/${id}.${ext}`;

/** Where the headset fetches a colourway's texture and picture, when it has them. */
const withUrls = (c) => ({
  ...c,
  textureUrl: c.hasTexture ? colourwayUrl(c.id, "png") : null,
  pictureUrl: c.hasPicture ? colourwayUrl(c.id, "jpg") : null,
});

/** Every finished piece, newest first, with its maker, likes, saves and cover colourway. */
async function library() {
  const pieces = (await db.pieces()).filter((p) => isJobId(p.id));
  const extras = await db.galleryExtras(pieces.map((p) => p.id));
  return pieces.map((p) => ({
    ...p,
    modelUrl: fileUrl(p.id, "glb"),
    // In the cloud every piece made by the app has its picture; on a computer, check.
    pictureUrl: cloud || existsSync(picturePath(p.id)) ? fileUrl(p.id, "jpg") : null,
    likes: extras[p.id].likes,
    saves: extras[p.id].saves,
    cover: extras[p.id].cover ? withUrls(extras[p.id].cover) : null,
  }));
}

/** The bytes of a data URI of one of [types], or null when there is none. */
function dataUriBytes(uri, types) {
  if (uri === undefined || uri === null) return null;
  const match = typeof uri === "string" && uri.match(/^data:image\/([a-z]+);base64,(.+)$/);
  if (!match || !types.includes(match[1])) throw new RenderError(400, `expected a ${types.join(" or ")} data URI`);
  return Buffer.from(match[2], "base64");
}

/** The signed-in user, or null: for pages anyone may see that show more when signed in. */
async function viewerOf(req) {
  const header = req.headers.authorization ?? "";
  return header.startsWith("Bearer ") ? db.userForToken(header.slice(7)) : null;
}

/** The signed-in user, or a 401. */
async function userOf(req) {
  const header = req.headers.authorization ?? "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  const user = await db.userForToken(token);
  if (!user) throw new RenderError(401, "Please log in.");
  return { user, token };
}

const to3d = createTo3d({
  fal,
  store: {
    has: (id) => files.has(`${id}.glb`),
    save: async (id, bytes) => {
      await files.put(`${id}.glb`, bytes, "model/gltf-binary");
      await db.markReady(id);
    },
    savePicture: (id, bytes) => files.put(`${id}.jpg`, bytes, "image/jpeg"),
    saveInfo: (id, info) => db.addPiece({ id, ...info }),
    renameInfo: (id, name, user) => db.renamePiece(id, name, user),
    urlFor: (id) => fileUrl(id, "glb"),
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
    const session = url === "/api/signup" ? await db.signUp(name, password) : await db.logIn(name, password);
    console.log(`${url === "/api/signup" ? "signed up" : "logged in"}: ${session.user.name}`);
    return send(res, 200, session);
  }
  if (req.method === "POST" && url === "/api/logout") {
    const header = req.headers.authorization ?? "";
    await db.logOut(header.startsWith("Bearer ") ? header.slice(7) : "");
    return send(res, 200, { ok: true });
  }
  if (req.method === "GET" && url === "/health") return send(res, 200, { ok: true });
  if (req.method === "GET" && url === "/api/me") return send(res, 200, { user: (await userOf(req)).user });
  if (req.method === "POST" && url === "/api/render") {
    await userOf(req);
    return send(res, 200, await render(await readJson(req), run));
  }
  if (req.method === "POST" && url === "/api/to3d") {
    const { user } = await userOf(req);
    const { image, name, material } = await readJson(req);
    const job = await to3d.start(image, name, material, user);
    console.log(`3D job started: ${job.jobId} by ${user.name}`);
    return send(res, 200, job);
  }
  const naming = url.match(/^\/api\/jobs\/([^/]+)\/name$/);
  if (req.method === "POST" && naming) {
    const { user } = await userOf(req);
    const { name } = await readJson(req);
    return send(res, 200, await to3d.rename(naming[1], name, user));
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
  if (req.method === "GET" && url === "/api/library") return send(res, 200, { pieces: await library() });
  const social = await socialRoute(req, url);
  if (social !== undefined) return send(res, 200, social);
  const colourFile = url.match(/^\/colourways\/([^/]+)\.(png|jpg)$/);
  if (req.method === "GET" && colourFile && isColourwayId(colourFile[1])) {
    const bytes = await files.get(`${colourFile[1]}.${colourFile[2]}`);
    if (bytes) {
      res.writeHead(200, { "Content-Type": colourFile[2] === "png" ? "image/png" : "image/jpeg" });
      return res.end(bytes);
    }
  }
  const file = url.match(/^\/models\/([^/]+)\.(glb|jpg)$/);
  if (req.method === "GET" && file && isJobId(file[1])) {
    const bytes = await files.get(`${file[1]}.${file[2]}`);
    if (bytes) {
      res.writeHead(200, { "Content-Type": file[2] === "glb" ? "model/gltf-binary" : "image/jpeg" });
      return res.end(bytes);
    }
  }
  send(res, 404, { error: "not found" });
}

/** The colourway, like, save, wear and notification routes; undefined when none matches. */
async function socialRoute(req, url) {
  const ringPage = url.match(/^\/api\/pieces\/([^/]+)$/);
  if (req.method === "GET" && ringPage) {
    const page = await db.pieceDetail(ringPage[1], await viewerOf(req));
    return {
      ...page,
      piece: { ...page.piece, modelUrl: fileUrl(page.piece.id, "glb"), pictureUrl: fileUrl(page.piece.id, "jpg") },
      authorColourways: page.authorColourways.map(withUrls),
      otherColourways: page.otherColourways.map(withUrls),
    };
  }
  if (req.method === "DELETE" && ringPage) {
    const { user } = await userOf(req);
    await db.deletePiece(ringPage[1], user);
    console.log(`ring ${ringPage[1]} deleted by ${user.name}`);
    return { ok: true };
  }
  const colouring = url.match(/^\/api\/pieces\/([^/]+)\/colourways$/);
  if (req.method === "POST" && colouring) {
    const { user } = await userOf(req);
    const { paints, texture, picture } = await readJson(req);
    const textureBytes = dataUriBytes(texture, ["png"]);
    const pictureBytes = dataUriBytes(picture, ["jpeg", "jpg"]);
    // The files go up first, under the id the row will get, so a saved row never points at
    // a file that is not there.
    const id = db.newColourwayId();
    if (textureBytes) await files.put(`${id}.png`, textureBytes, "image/png");
    if (pictureBytes) await files.put(`${id}.jpg`, pictureBytes, "image/jpeg");
    await db.saveColourway({
      id,
      pieceId: colouring[1],
      user,
      paints,
      hasTexture: Boolean(textureBytes),
      hasPicture: Boolean(pictureBytes),
    });
    console.log(`colourway ${id} on ${colouring[1]} by ${user.name}`);
    return { id };
  }
  const cover = url.match(/^\/api\/pieces\/([^/]+)\/cover$/);
  if (req.method === "POST" && cover) {
    const { user } = await userOf(req);
    const { colourwayId } = await readJson(req);
    await db.setCover(cover[1], colourwayId ?? null, user);
    return { ok: true };
  }
  const colourway = url.match(/^\/api\/colourways\/([^/]+)$/);
  if (req.method === "DELETE" && colourway) {
    const { user } = await userOf(req);
    await db.deleteColourway(colourway[1], user);
    return { ok: true };
  }
  const choice = url.match(/^\/api\/colourways\/([^/]+)\/(feature|hide)$/);
  if (req.method === "POST" && choice) {
    const { user } = await userOf(req);
    const { on } = await readJson(req);
    if (choice[2] === "feature") await db.featureColourway(choice[1], user, on === true);
    else await db.hideColourway(choice[1], user, on === true);
    return { ok: true };
  }
  if (req.method === "POST" && url === "/api/likes") {
    const { user } = await userOf(req);
    const { kind, id, on } = await readJson(req);
    await db.like(user, kind, id, on === true);
    return { ok: true };
  }
  if (req.method === "POST" && url === "/api/saves") {
    const { user } = await userOf(req);
    const { pieceId, on } = await readJson(req);
    await db.save(user, pieceId, on === true);
    return { ok: true };
  }
  if (req.method === "POST" && url === "/api/wears") {
    const { user } = await userOf(req);
    const { pieceId, colourwayId } = await readJson(req);
    await db.wear(user, pieceId, colourwayId ?? null);
    return { ok: true };
  }
  if (req.method === "GET" && url === "/api/me/library") {
    const { user } = await userOf(req);
    const mine = await db.mine(user);
    return {
      ...mine,
      rings: mine.rings.map((r) => ({ ...r, modelUrl: fileUrl(r.id, "glb"), pictureUrl: fileUrl(r.id, "jpg") })),
      colourways: mine.colourways.map(withUrls),
    };
  }
  if (req.method === "GET" && url === "/api/notifications") {
    const { user } = await userOf(req);
    return { notifications: await db.notifications(user) };
  }
  if (req.method === "POST" && url === "/api/notifications/seen") {
    const { user } = await userOf(req);
    await db.markNotificationsSeen(user);
    return { ok: true };
  }
  return undefined;
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
