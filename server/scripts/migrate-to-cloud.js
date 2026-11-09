// Moves what this computer's server has made into the cloud: accounts and pieces from
// server/data/sketchon.db into Postgres (DATABASE_URL), and models and pictures from
// server/models into Supabase Storage (SUPABASE_URL, SUPABASE_SERVICE_KEY).
//
//   node --env-file=.env.local scripts/migrate-to-cloud.js
//
// Safe to run twice: rows and files that are already there are left alone or overwritten
// with the same content. Prints what it did, never any secret.

import { existsSync, readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { openDb } from "../src/db.js";
import { supabaseStorage } from "../src/storage.js";

const MODELS_DIR = fileURLToPath(new URL("../models/", import.meta.url));
const LOCAL_DB = fileURLToPath(new URL("../data/sketchon.db", import.meta.url));
const LEGACY_AUTHOR = process.env.LEGACY_AUTHOR ?? "Linzhi";

for (const name of ["DATABASE_URL", "SUPABASE_URL", "SUPABASE_SERVICE_KEY"]) {
  if (!process.env[name]) {
    console.error(`${name} is not set. Put it in server/.env.local.`);
    process.exit(1);
  }
}

const cloud = await openDb(process.env.DATABASE_URL);
const files = await supabaseStorage({ url: process.env.SUPABASE_URL, serviceKey: process.env.SUPABASE_SERVICE_KEY });

// Accounts and pieces, straight from the local SQLite file.
const { DatabaseSync } = await import("node:sqlite");
const local = existsSync(LOCAL_DB) ? new DatabaseSync(LOCAL_DB, { readOnly: true }) : null;
const { default: pg } = await import("pg");
const pool = new pg.Pool({ connectionString: process.env.DATABASE_URL, ssl: { rejectUnauthorized: false } });

const userIds = new Map();
if (local) {
  for (const u of local.prepare("SELECT id, name, salt, hash, created_at FROM users").all()) {
    const { rows } = await pool.query(
      `INSERT INTO users (name, salt, hash, created_at) VALUES ($1, $2, $3, $4)
       ON CONFLICT (lower(name)) DO UPDATE SET name = users.name RETURNING id`,
      [u.name, Buffer.from(u.salt), Buffer.from(u.hash), u.created_at],
    );
    userIds.set(u.id, rows[0].id);
    console.log(`account: ${u.name}`);
  }
}

const pieces = local
  ? local.prepare("SELECT id, user_id, author, name, material, created_at FROM pieces WHERE ready = 1").all()
  : [];
const known = new Set(pieces.map((p) => p.id));
// Models on disk that the local database never recorded are older pieces.
for (const file of readdirSync(MODELS_DIR).filter((f) => f.endsWith(".glb"))) {
  const id = file.slice(0, -4);
  if (known.has(id)) continue;
  let info = {};
  try {
    info = JSON.parse(readFileSync(`${MODELS_DIR}${id}.json`, "utf8"));
  } catch {}
  pieces.push({ id, user_id: null, author: LEGACY_AUTHOR, name: info.name ?? null, material: info.material ?? null, created_at: info.createdAt ?? Date.now() });
}

for (const p of pieces) {
  const glb = `${MODELS_DIR}${p.id}.glb`;
  if (!existsSync(glb)) {
    console.log(`skipped ${p.id}: no model file`);
    continue;
  }
  await files.put(`${p.id}.glb`, readFileSync(glb), "model/gltf-binary");
  const jpg = `${MODELS_DIR}${p.id}.jpg`;
  if (existsSync(jpg)) await files.put(`${p.id}.jpg`, readFileSync(jpg), "image/jpeg");
  await pool.query(
    `INSERT INTO pieces (id, user_id, author, name, material, created_at, ready) VALUES ($1, $2, $3, $4, $5, $6, 1)
     ON CONFLICT (id) DO NOTHING`,
    [p.id, p.user_id === null ? null : userIds.get(p.user_id) ?? null, p.author ?? LEGACY_AUTHOR, p.name, p.material, p.created_at],
  );
  console.log(`piece: ${p.name ?? p.id} by ${p.author ?? LEGACY_AUTHOR}${existsSync(jpg) ? "" : " (no picture)"}`);
}

console.log(`done: ${(await cloud.pieces()).length} pieces in the cloud`);
await pool.end();
await cloud.close();
local?.close();
