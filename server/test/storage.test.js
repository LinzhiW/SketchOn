import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { localStorage, supabaseStorage } from "../src/storage.js";

const KEY = "01a1bed5-c29d-7890.glb";

test("local storage keeps and returns files, and the server serves them", async () => {
  const store = localStorage(join(mkdtempSync(join(tmpdir(), "sk-")), "/"));
  assert.equal(await store.has(KEY), false);
  await store.put(KEY, Buffer.from("glb"));
  assert.equal(await store.has(KEY), true);
  assert.equal((await store.get(KEY)).toString(), "glb");
  assert.equal(store.publicUrl(KEY), null);
});

test("storage refuses keys that could escape its folder", async () => {
  const store = localStorage(join(mkdtempSync(join(tmpdir(), "sk-")), "/"));
  await assert.rejects(store.put("../secret.glb", Buffer.from("x")));
  await assert.rejects(store.has("x.exe"));
});

test("supabase storage uploads with the service key and serves from the public URL", async () => {
  const calls = [];
  const fetchImpl = async (url, options = {}) => {
    calls.push({ url, method: options.method ?? "GET", headers: options.headers ?? {} });
    return { ok: true, status: 200, arrayBuffer: async () => new ArrayBuffer(0) };
  };
  const store = await supabaseStorage({ url: "https://abc.supabase.co/", serviceKey: "svc", fetchImpl });
  await store.put(KEY, Buffer.from("glb"), "model/gltf-binary");
  assert.equal(calls[0].url, "https://abc.supabase.co/storage/v1/bucket");
  assert.equal(calls[1].url, `https://abc.supabase.co/storage/v1/object/pieces/${KEY}`);
  assert.equal(calls[1].headers.Authorization, "Bearer svc");
  assert.equal(calls[1].headers["x-upsert"], "true");
  assert.equal(store.publicUrl(KEY), `https://abc.supabase.co/storage/v1/object/public/pieces/${KEY}`);
});
