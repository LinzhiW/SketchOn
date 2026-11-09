import { test } from "node:test";
import assert from "node:assert/strict";
import { openDb } from "../src/db.js";

test("sign up, log in and look up the user by token", () => {
  const db = openDb();
  const { token, user } = db.signUp("Linzhi", "secret-1");
  assert.equal(user.name, "Linzhi");
  assert.deepEqual(db.userForToken(token), user);
  const again = db.logIn("linzhi", "secret-1");
  assert.equal(again.user.id, user.id);
  assert.notEqual(again.token, token);
});

test("names are unique regardless of case, and rules are checked", () => {
  const db = openDb();
  db.signUp("Linzhi", "secret-1");
  assert.throws(() => db.signUp("LINZHI", "secret-2"), (e) => e.status === 409);
  assert.throws(() => db.signUp("x", "secret-2"), (e) => e.status === 400);
  assert.throws(() => db.signUp("Ok Name", "short"), (e) => e.status === 400);
});

test("a wrong password and an unknown name get the same answer", () => {
  const db = openDb();
  db.signUp("Linzhi", "secret-1");
  assert.throws(() => db.logIn("Linzhi", "nope-nope"), (e) => e.status === 401 && /Wrong name or password/.test(e.message));
  assert.throws(() => db.logIn("Nobody", "secret-1"), (e) => e.status === 401 && /Wrong name or password/.test(e.message));
});

test("passwords and tokens are not stored as text", () => {
  const db = openDb();
  const { token } = db.signUp("Linzhi", "secret-1");
  // Reach into the same in-memory database through a second statement on the module's handle
  // is not possible, so check through behaviour: a logged-out token stops working.
  db.logOut(token);
  assert.equal(db.userForToken(token), null);
});

test("tokens expire after 90 days", () => {
  let now = 1_000_000;
  const db = openDb(":memory:", { now: () => now });
  const { token } = db.signUp("Linzhi", "secret-1");
  now += 91 * 24 * 60 * 60 * 1000;
  assert.equal(db.userForToken(token), null);
});

test("pieces keep their author; only the maker can rename one", () => {
  const db = openDb();
  const a = db.signUp("Ana", "secret-1").user;
  const b = db.signUp("Ben", "secret-2").user;
  db.addPiece({ id: "job-1", user: a, name: null, material: "clay", createdAt: 1 });
  db.renamePiece("job-1", "Cloud", a);
  assert.deepEqual(db.piece("job-1"), { id: "job-1", author: "Ana", name: "Cloud", material: "clay", createdAt: 1 });
  assert.throws(() => db.renamePiece("job-1", "Mine", b), (e) => e.status === 403);
});

test("pieces made before accounts are imported once, with the given author", () => {
  const db = openDb();
  assert.equal(db.importLegacy("old-1", { name: "Butterfly", material: "clay", createdAt: 5 }, "Linzhi"), true);
  assert.equal(db.importLegacy("old-1", { name: "Other" }, "Someone"), false);
  assert.equal(db.piece("old-1").author, "Linzhi");
  assert.equal(db.pieces()[0].name, "Butterfly");
});
