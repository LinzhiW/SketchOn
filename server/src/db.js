// Accounts and pieces, in one SQLite file (server/data/sketchon.db).
//
// Passwords are stored as scrypt hashes with a per-user salt, never as text. A login gives
// the app a random token; only the token's SHA-256 is stored, so the database alone cannot
// be used to sign in. Tokens last 90 days.
//
// Pieces (finished rings) record who made them. Pieces made before accounts existed are
// imported from their .json files with the author name given at start-up.

import { DatabaseSync } from "node:sqlite";
import { createHash, randomBytes, scryptSync, timingSafeEqual } from "node:crypto";
import { RenderError } from "./render.js";

const NAME = /^[A-Za-z0-9][A-Za-z0-9 ._-]{1,19}$/;
const MIN_PASSWORD = 6;
const MAX_PASSWORD = 100;
const TOKEN_DAYS = 90;
const DAY_MS = 24 * 60 * 60 * 1000;

const hashToken = (token) => createHash("sha256").update(token).digest("hex");
const hashPassword = (password, salt) => scryptSync(password, salt, 64);

export function openDb(path = ":memory:", { now = () => Date.now() } = {}) {
  const db = new DatabaseSync(path);
  db.exec(`
    PRAGMA journal_mode = WAL;
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY,
      name TEXT NOT NULL UNIQUE COLLATE NOCASE,
      salt BLOB NOT NULL,
      hash BLOB NOT NULL,
      created_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS tokens (
      hash TEXT PRIMARY KEY,
      user_id INTEGER NOT NULL REFERENCES users(id),
      expires_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS pieces (
      id TEXT PRIMARY KEY,
      user_id INTEGER REFERENCES users(id),
      author TEXT,
      name TEXT,
      material TEXT,
      created_at INTEGER NOT NULL
    );
  `);

  function checkCredentials(name, password) {
    if (typeof name !== "string" || !NAME.test(name.trim())) {
      throw new RenderError(400, "Names are 2 to 20 letters, numbers, spaces, dots, dashes or underscores.");
    }
    if (typeof password !== "string" || password.length < MIN_PASSWORD || password.length > MAX_PASSWORD) {
      throw new RenderError(400, `Passwords are ${MIN_PASSWORD} to ${MAX_PASSWORD} characters.`);
    }
  }

  function issueToken(userId) {
    const token = randomBytes(32).toString("hex");
    db.prepare("INSERT INTO tokens (hash, user_id, expires_at) VALUES (?, ?, ?)").run(
      hashToken(token),
      userId,
      now() + TOKEN_DAYS * DAY_MS,
    );
    return token;
  }

  function signUp(name, password) {
    checkCredentials(name, password);
    const clean = name.trim();
    if (db.prepare("SELECT 1 FROM users WHERE name = ?").get(clean)) {
      throw new RenderError(409, "That name is taken.");
    }
    const salt = randomBytes(16);
    const { lastInsertRowid } = db
      .prepare("INSERT INTO users (name, salt, hash, created_at) VALUES (?, ?, ?, ?)")
      .run(clean, salt, hashPassword(password, salt), now());
    const user = { id: Number(lastInsertRowid), name: clean };
    return { token: issueToken(user.id), user };
  }

  function logIn(name, password) {
    const row =
      typeof name === "string" ? db.prepare("SELECT id, name, salt, hash FROM users WHERE name = ?").get(name.trim()) : null;
    // The same message whether the name or the password is wrong.
    const wrong = new RenderError(401, "Wrong name or password.");
    if (!row || typeof password !== "string") throw wrong;
    const given = hashPassword(password, row.salt);
    if (given.length !== row.hash.length || !timingSafeEqual(given, row.hash)) throw wrong;
    return { token: issueToken(row.id), user: { id: row.id, name: row.name } };
  }

  function userForToken(token) {
    if (typeof token !== "string" || !token) return null;
    const row = db
      .prepare("SELECT users.id, users.name, tokens.expires_at FROM tokens JOIN users ON users.id = tokens.user_id WHERE tokens.hash = ?")
      .get(hashToken(token));
    if (!row || row.expires_at < now()) return null;
    return { id: row.id, name: row.name };
  }

  function logOut(token) {
    if (typeof token === "string") db.prepare("DELETE FROM tokens WHERE hash = ?").run(hashToken(token));
  }

  function addPiece({ id, user, name, material, createdAt }) {
    db.prepare(
      "INSERT OR REPLACE INTO pieces (id, user_id, author, name, material, created_at) VALUES (?, ?, ?, ?, ?, ?)",
    ).run(id, user?.id ?? null, user?.name ?? null, name ?? null, material ?? null, createdAt ?? now());
  }

  /** Only the piece's maker may name it. */
  function renamePiece(id, name, user) {
    const row = db.prepare("SELECT user_id FROM pieces WHERE id = ?").get(id);
    if (!row) throw new RenderError(404, "No such piece.");
    if (row.user_id !== null && row.user_id !== user?.id) throw new RenderError(403, "Only its maker can name this piece.");
    db.prepare("UPDATE pieces SET name = ? WHERE id = ?").run(name, id);
  }

  function piece(id) {
    const row = db.prepare("SELECT id, author, name, material, created_at AS createdAt FROM pieces WHERE id = ?").get(id);
    return row ? { ...row } : null;
  }

  function pieces() {
    return db
      .prepare("SELECT id, author, name, material, created_at AS createdAt FROM pieces ORDER BY created_at DESC")
      .all()
      .map((row) => ({ ...row }));
  }

  /** Imports a piece made before accounts, unless it is already known. */
  function importLegacy(id, info, author) {
    if (piece(id)) return false;
    db.prepare("INSERT INTO pieces (id, user_id, author, name, material, created_at) VALUES (?, NULL, ?, ?, ?, ?)").run(
      id,
      author,
      info?.name ?? null,
      info?.material ?? null,
      info?.createdAt ?? now(),
    );
    return true;
  }

  return { signUp, logIn, userForToken, logOut, addPiece, renamePiece, piece, pieces, importLegacy, close: () => db.close() };
}
