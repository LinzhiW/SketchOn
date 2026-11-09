import { test } from "node:test";
import assert from "node:assert/strict";
import { openDb } from "../src/db.js";

// These run on SQLite in memory. The same logic runs on Postgres in the cloud; set
// TEST_DATABASE_URL to a throwaway Postgres database to run them there too.
const urls = [":memory:", ...(process.env.TEST_DATABASE_URL ? [process.env.TEST_DATABASE_URL] : [])];

for (const url of urls) {
  const kind = url === ":memory:" ? "sqlite" : "postgres";
  const fresh = async (options) => {
    const db = await openDb(url, options);
    return db;
  };
  const unique = (name) => (kind === "sqlite" ? name : `${name}-${Date.now() % 100000}`);

  test(`[${kind}] sign up, log in and look up the user by token`, async () => {
    const db = await fresh();
    const name = unique("Linzhi");
    const { token, user } = await db.signUp(name, "secret-1");
    assert.equal(user.name, name);
    assert.deepEqual(await db.userForToken(token), user);
    const again = await db.logIn(name.toLowerCase(), "secret-1");
    assert.equal(again.user.id, user.id);
    assert.notEqual(again.token, token);
    await db.close();
  });

  test(`[${kind}] names are unique regardless of case, and rules are checked`, async () => {
    const db = await fresh();
    const name = unique("Linzhi");
    await db.signUp(name, "secret-1");
    await assert.rejects(db.signUp(name.toUpperCase(), "secret-2"), (e) => e.status === 409);
    await assert.rejects(db.signUp("x", "secret-2"), (e) => e.status === 400);
    await assert.rejects(db.signUp("Ok Name", "short"), (e) => e.status === 400);
    await db.close();
  });

  test(`[${kind}] a wrong password and an unknown name get the same answer`, async () => {
    const db = await fresh();
    const name = unique("Linzhi");
    await db.signUp(name, "secret-1");
    const same = (e) => e.status === 401 && /Wrong name or password/.test(e.message);
    await assert.rejects(db.logIn(name, "nope-nope"), same);
    await assert.rejects(db.logIn("Nobody-at-all", "secret-1"), same);
    await db.close();
  });

  test(`[${kind}] a logged-out token stops working`, async () => {
    const db = await fresh();
    const { token } = await db.signUp(unique("Linzhi"), "secret-1");
    await db.logOut(token);
    assert.equal(await db.userForToken(token), null);
    await db.close();
  });

  test(`[${kind}] tokens expire after 90 days`, async () => {
    let now = 1_000_000;
    const db = await fresh({ now: () => now });
    const { token } = await db.signUp(unique("Linzhi"), "secret-1");
    now += 91 * 24 * 60 * 60 * 1000;
    assert.equal(await db.userForToken(token), null);
    await db.close();
  });

  test(`[${kind}] pieces keep their author, show once ready, and only the maker renames`, async () => {
    const db = await fresh();
    const a = (await db.signUp(unique("Ana"), "secret-1")).user;
    const b = (await db.signUp(unique("Ben"), "secret-2")).user;
    const id = unique("job-1");
    await db.addPiece({ id, user: a, name: null, material: "clay", createdAt: 1 });
    assert.equal((await db.pieces()).some((p) => p.id === id), false);
    await db.markReady(id);
    await db.renamePiece(id, "Cloud", a);
    assert.deepEqual(await db.piece(id), { id, author: a.name, name: "Cloud", material: "clay", createdAt: 1 });
    assert.equal((await db.pieces()).some((p) => p.id === id), true);
    await assert.rejects(db.renamePiece(id, "Mine", b), (e) => e.status === 403);
    await db.close();
  });

  test(`[${kind}] pieces made before accounts are imported once, ready, with the given author`, async () => {
    const db = await fresh();
    const id = unique("old-1");
    assert.equal(await db.importLegacy(id, { name: "Butterfly", material: "clay", createdAt: 5 }, "Linzhi"), true);
    assert.equal(await db.importLegacy(id, { name: "Other" }, "Someone"), false);
    assert.equal((await db.piece(id)).author, "Linzhi");
    assert.equal((await db.pieces()).find((p) => p.id === id).name, "Butterfly");
    await db.close();
  });
}
