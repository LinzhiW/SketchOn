// Where finished models (.glb) and their pictures (.jpg) are kept: a folder on a computer, or
// a public Supabase Storage bucket in the cloud. A cloud server's own disk is wiped on every
// deploy, so files there must live in storage.
//
// Both kinds: has(key), put(key, bytes, type), get(key) -> bytes or null, publicUrl(key) ->
// a URL the headset can fetch directly, or null when the server itself serves the file.

import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";

const SAFE_KEY = /^[A-Za-z0-9-]{8,80}\.(glb|jpg)$/;

function checkKey(key) {
  if (!SAFE_KEY.test(key)) throw new Error(`bad storage key ${key}`);
}

export function localStorage(dir) {
  mkdirSync(dir, { recursive: true });
  return {
    async has(key) {
      checkKey(key);
      return existsSync(dir + key);
    },
    async put(key, bytes) {
      checkKey(key);
      writeFileSync(dir + key, bytes);
    },
    async get(key) {
      checkKey(key);
      return existsSync(dir + key) ? readFileSync(dir + key) : null;
    },
    publicUrl() {
      return null;
    },
  };
}

/**
 * Supabase Storage over its REST API, with the service key (which stays on the server).
 * The bucket is public for reading, so the headset downloads models straight from it.
 */
export async function supabaseStorage({ url, serviceKey, bucket = "pieces", fetchImpl = fetch }) {
  const base = url.replace(/\/$/, "");
  const auth = { Authorization: `Bearer ${serviceKey}`, apikey: serviceKey };
  // Create the bucket on first run; "already exists" is fine.
  const made = await fetchImpl(`${base}/storage/v1/bucket`, {
    method: "POST",
    headers: { ...auth, "Content-Type": "application/json" },
    body: JSON.stringify({ id: bucket, name: bucket, public: true }),
  });
  if (!made.ok && made.status !== 409 && made.status !== 400) {
    throw new Error(`could not create the storage bucket (HTTP ${made.status})`);
  }
  const objectUrl = (key) => `${base}/storage/v1/object/${bucket}/${key}`;
  const publicUrl = (key) => `${base}/storage/v1/object/public/${bucket}/${key}`;
  return {
    async has(key) {
      checkKey(key);
      const res = await fetchImpl(publicUrl(key), { method: "HEAD" });
      return res.ok;
    },
    async put(key, bytes, type) {
      checkKey(key);
      const res = await fetchImpl(objectUrl(key), {
        method: "POST",
        headers: { ...auth, "Content-Type": type, "x-upsert": "true" },
        body: bytes,
      });
      if (!res.ok) throw new Error(`could not store ${key} (HTTP ${res.status})`);
    },
    async get(key) {
      checkKey(key);
      const res = await fetchImpl(publicUrl(key));
      return res.ok ? Buffer.from(await res.arrayBuffer()) : null;
    },
    publicUrl(key) {
      checkKey(key);
      return publicUrl(key);
    },
  };
}
