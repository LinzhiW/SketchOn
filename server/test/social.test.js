import { test } from "node:test";
import assert from "node:assert/strict";
import { openDb } from "../src/db.js";

// The rules in docs/colourways-plan.md, on SQLite in memory.

async function world() {
  let clock = 1_000;
  const db = await openDb(":memory:", { now: () => ++clock });
  const author = (await db.signUp("Linzhi", "secret-1")).user;
  const fan = (await db.signUp("Fan", "secret-1")).user;
  const other = (await db.signUp("Other", "secret-1")).user;
  await db.addPiece({ id: "job-ring-1", user: author, name: "Cat", material: "clay", ready: true });
  return { db, author, fan, other };
}

const RED = { 0: 0xffe5484d | 0 };
const BLUE = { 0: 0xff0090ff | 0, 1: 0xffffffff | 0 };

test("the author's colourways and everyone else's are listed apart", async () => {
  const { db, author, fan } = await world();
  const own = await db.saveColourway({ pieceId: "job-ring-1", user: author, paints: RED });
  const fork = await db.saveColourway({ pieceId: "job-ring-1", user: fan, paints: BLUE, hasTexture: true });
  const page = await db.pieceDetail("job-ring-1", null);
  assert.deepEqual(page.authorColourways.map((c) => c.id), [own]);
  assert.deepEqual(page.otherColourways.map((c) => c.id), [fork]);
  assert.equal(page.otherColourways[0].hasTexture, true);
  assert.deepEqual(page.otherColourways[0].paints, { 0: 0xff0090ff | 0, 1: -1 });
  await db.close();
});

test("recolouring someone's ring tells its author; colouring your own does not", async () => {
  const { db, author, fan } = await world();
  await db.saveColourway({ pieceId: "job-ring-1", user: author, paints: RED });
  assert.equal((await db.notifications(author)).length, 0);
  const fork = await db.saveColourway({ pieceId: "job-ring-1", user: fan, paints: BLUE });
  const told = await db.notifications(author);
  assert.equal(told.length, 1);
  assert.equal(told[0].kind, "recoloured");
  assert.equal(told[0].actor, "Fan");
  assert.equal(told[0].colourwayId, fork);
  assert.equal(told[0].pieceName, "Cat");
  assert.equal(told[0].seen, false);
  await db.markNotificationsSeen(author);
  assert.equal((await db.notifications(author))[0].seen, true);
  await db.close();
});

test("only the author's own colourway can be the cover", async () => {
  const { db, author, fan } = await world();
  const own = await db.saveColourway({ pieceId: "job-ring-1", user: author, paints: RED });
  const fork = await db.saveColourway({ pieceId: "job-ring-1", user: fan, paints: BLUE });
  await db.setCover("job-ring-1", own, author);
  assert.equal((await db.pieceDetail("job-ring-1")).piece.cover, own);
  await assert.rejects(db.setCover("job-ring-1", fork, author), (e) => e.status === 400);
  await assert.rejects(db.setCover("job-ring-1", own, fan), (e) => e.status === 403);
  const extras = await db.galleryExtras(["job-ring-1"]);
  assert.equal(extras["job-ring-1"].cover.id, own);
  await db.close();
});

test("featuring tells the colourist and puts it first; only the author may feature or hide", async () => {
  const { db, author, fan, other } = await world();
  const first = await db.saveColourway({ pieceId: "job-ring-1", user: fan, paints: BLUE });
  await db.saveColourway({ pieceId: "job-ring-1", user: other, paints: RED });
  await assert.rejects(db.featureColourway(first, fan, true), (e) => e.status === 403);
  await db.featureColourway(first, author, true);
  const page = await db.pieceDetail("job-ring-1");
  assert.equal(page.otherColourways[0].id, first);
  assert.equal(page.otherColourways[0].featured, true);
  const told = await db.notifications(fan);
  assert.equal(told[0].kind, "featured");
  assert.equal(told[0].actor, "Linzhi");
  // Featuring again does not tell them twice.
  await db.featureColourway(first, author, true);
  assert.equal((await db.notifications(fan)).length, 1);
  await db.close();
});

test("a hidden colourway shows only to the author, and its maker still sees it, marked", async () => {
  const { db, author, fan } = await world();
  const fork = await db.saveColourway({ pieceId: "job-ring-1", user: fan, paints: BLUE });
  await db.hideColourway(fork, author, true);
  assert.equal((await db.pieceDetail("job-ring-1", fan)).otherColourways.length, 0);
  assert.equal((await db.pieceDetail("job-ring-1", author)).otherColourways[0].hidden, true);
  const mine = await db.mine(fan);
  assert.equal(mine.colourways[0].status, "hidden");
  assert.equal(mine.colourways[0].onMyRing, false);
  await db.close();
});

test("likes and saves count people, not presses, and can be taken back", async () => {
  const { db, author, fan, other } = await world();
  const fork = await db.saveColourway({ pieceId: "job-ring-1", user: fan, paints: BLUE });
  await db.like(fan, "piece", "job-ring-1", true);
  await db.like(fan, "piece", "job-ring-1", true);
  await db.like(other, "piece", "job-ring-1", true);
  await db.save(fan, "job-ring-1", true);
  await db.save(fan, "job-ring-1", true);
  await db.like(author, "colourway", fork, true);
  let page = await db.pieceDetail("job-ring-1", fan);
  assert.equal(page.likes, 2);
  assert.equal(page.saves, 1);
  assert.equal(page.likedByMe, true);
  assert.equal(page.savedByMe, true);
  assert.equal(page.otherColourways[0].likes, 1);
  await db.like(fan, "piece", "job-ring-1", false);
  await db.save(fan, "job-ring-1", false);
  page = await db.pieceDetail("job-ring-1", fan);
  assert.equal(page.likes, 1);
  assert.equal(page.saves, 0);
  assert.equal(page.likedByMe, false);
  await assert.rejects(db.like(fan, "thing", "job-ring-1", true), (e) => e.status === 400);
  await db.close();
});

test("wearing counts each person once and keeps the latest time", async () => {
  const { db, author, fan } = await world();
  const fork = await db.saveColourway({ pieceId: "job-ring-1", user: author, paints: RED });
  await db.wear(fan, "job-ring-1", fork);
  await db.wear(fan, "job-ring-1", fork);
  await db.wear(fan, "job-ring-1", null);
  const page = await db.pieceDetail("job-ring-1");
  assert.equal(page.wearers, 1);
  assert.equal(page.authorColourways[0].wearers, 1);
  const wears = (await db.mine(fan)).wears;
  assert.equal(wears.length, 2);
  assert.equal(wears.every((w) => w.status === "live"), true);
  await db.close();
});

test("a deleted ring leaves the gallery but stays in records, marked removed and not wearable", async () => {
  const { db, author, fan } = await world();
  const fork = await db.saveColourway({ pieceId: "job-ring-1", user: fan, paints: BLUE });
  await db.wear(fan, "job-ring-1", fork);
  await db.save(fan, "job-ring-1", true);
  await assert.rejects(db.deletePiece("job-ring-1", fan), (e) => e.status === 403);
  await db.deletePiece("job-ring-1", author);
  assert.equal((await db.pieces()).length, 0);
  const mine = await db.mine(fan);
  assert.equal(mine.colourways[0].status, "removed");
  assert.equal(mine.wears[0].status, "removed");
  assert.equal(mine.saved[0].status, "removed");
  await assert.rejects(db.wear(fan, "job-ring-1", fork), (e) => e.status === 410);
  await assert.rejects(db.saveColourway({ pieceId: "job-ring-1", user: fan, paints: RED }), (e) => e.status === 410);
  await assert.rejects(db.pieceDetail("job-ring-1", fan), (e) => e.status === 410);
  await db.close();
});

test("only a colourway's maker deletes it; a deleted cover goes back to the render", async () => {
  const { db, author, fan } = await world();
  const own = await db.saveColourway({ pieceId: "job-ring-1", user: author, paints: RED });
  await db.setCover("job-ring-1", own, author);
  await assert.rejects(db.deleteColourway(own, fan), (e) => e.status === 403);
  await db.deleteColourway(own, author);
  const page = await db.pieceDetail("job-ring-1");
  assert.equal(page.piece.cover, null);
  assert.equal(page.authorColourways.length, 0);
  await db.close();
});

test("bad colours and unknown rings are refused", async () => {
  const { db, fan } = await world();
  await assert.rejects(db.saveColourway({ pieceId: "job-ring-1", user: fan, paints: { a: 1 } }), (e) => e.status === 400);
  await assert.rejects(db.saveColourway({ pieceId: "job-ring-1", user: fan, paints: [1] }), (e) => e.status === 400);
  await assert.rejects(db.saveColourway({ pieceId: "nope", user: fan, paints: RED }), (e) => e.status === 404);
  await assert.rejects(db.like(fan, "colourway", "cw-not-an-id", true), (e) => e.status === 404);
  await db.close();
});

test("an older database gets the new columns and keeps its rings", async () => {
  const { DatabaseSync } = await import("node:sqlite");
  const { mkdtempSync } = await import("node:fs");
  const { tmpdir } = await import("node:os");
  const { join } = await import("node:path");
  const path = join(mkdtempSync(join(tmpdir(), "sketchon-")), "old.db");
  const old = new DatabaseSync(path);
  old.exec(`CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT NOT NULL, salt BLOB NOT NULL, hash BLOB NOT NULL, created_at INTEGER NOT NULL)`);
  old.exec(`CREATE TABLE pieces (id TEXT PRIMARY KEY, user_id INTEGER, author TEXT, name TEXT, material TEXT, created_at INTEGER NOT NULL, ready INTEGER NOT NULL DEFAULT 0)`);
  old.exec(`INSERT INTO pieces VALUES ('job-old-ring', NULL, 'Linzhi', 'Old', 'gold', 5, 1)`);
  old.close();
  const db = await openDb(path);
  assert.equal((await db.pieces()).length, 1);
  assert.equal((await db.pieceDetail("job-old-ring")).piece.cover, null);
  await db.close();
  // Opening it again does not add the columns twice.
  const again = await openDb(path);
  assert.equal((await again.pieces())[0].id, "job-old-ring");
  await again.close();
});
