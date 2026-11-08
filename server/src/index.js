// A small HTTP server between the headset and fal. The fal key lives here, never in the APK
// or the browser.
//
//   POST /api/render  { "sketch": "data:image/png;base64,...", "style": "gold",
//                       "engine": "klein" | "nano", "seed": 1234 }
//   -> 200 { "imageUrl": "...", "requestId": "...", "ms": 840 }
//   GET  /            the sketch test page (public/sketch.html)

import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { fal } from "@fal-ai/client";
import { render, RenderError } from "./render.js";

const PORT = Number(process.env.PORT ?? 8787);
const MAX_BODY_BYTES = 3 * 1024 * 1024;
const PAGE = new URL("../public/sketch.html", import.meta.url);

if (!process.env.FAL_KEY) {
  console.error("FAL_KEY is not set. Put it in server/.env.local.");
  process.exit(1);
}
fal.config({ credentials: process.env.FAL_KEY });

async function run(model, input) {
  const result = await fal.run(model, { input });
  return { images: result?.data?.images, requestId: result?.requestId };
}

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
  const text = typeof detail === "string" ? detail : error?.message ?? "render failed";
  return text.replace(/fal_sk\S*/gi, "[hidden]").slice(0, 300);
}

createServer(async (req, res) => {
  if (req.method === "GET" && (req.url === "/" || req.url === "/sketch")) {
    res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
    return res.end(await readFile(PAGE));
  }
  if (req.method !== "POST" || req.url !== "/api/render") return send(res, 404, { error: "not found" });
  try {
    send(res, 200, await render(await readJson(req), run));
  } catch (error) {
    const status = error instanceof RenderError ? error.status : error?.status ?? 500;
    const reason = reasonOf(error);
    console.error(`render failed (${status}): ${reason}`);
    send(res, status, { error: reason });
  }
}).listen(PORT, () => console.log(`render server on http://localhost:${PORT}`));
