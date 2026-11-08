import { test } from "node:test";
import assert from "node:assert/strict";
import { buildInput, render, RenderError, ENGINES } from "../src/render.js";

const SKETCH = "data:image/png;base64,iVBORw0KGgo=";

test("builds the Klein input from a sketch and a style", () => {
  const input = buildInput({ sketch: SKETCH, style: "gold" });
  assert.deepEqual(input.image_urls, [SKETCH]);
  assert.match(input.prompt, /yellow gold/);
  assert.deepEqual(input.image_size, { width: 512, height: 512 });
  assert.equal("seed" in input, false);
});

test("passes a fixed seed to Klein", () => {
  assert.equal(buildInput({ sketch: SKETCH, style: "rose", seed: 42 }).seed, 42);
});

test("builds the Nano Banana input without a seed", () => {
  const input = buildInput({ sketch: SKETCH, style: "silver", engine: "nano", seed: 42 });
  assert.deepEqual(input.image_urls, [SKETCH]);
  assert.equal("seed" in input, false);
});

test("only Klein runs live", () => {
  assert.equal(ENGINES.klein.live, true);
  assert.equal(ENGINES.nano.live, false);
});

test("rejects a style that is not on the list", () => {
  assert.throws(() => buildInput({ sketch: SKETCH, style: "anything you like" }), RenderError);
});

test("rejects an unknown engine and a bad seed", () => {
  assert.throws(() => buildInput({ sketch: SKETCH, style: "gold", engine: "other" }), RenderError);
  assert.throws(() => buildInput({ sketch: SKETCH, style: "gold", seed: -1 }), RenderError);
  assert.throws(() => buildInput({ sketch: SKETCH, style: "gold", seed: 1.5 }), RenderError);
});

test("rejects a sketch that is not an image data URI", () => {
  assert.throws(() => buildInput({ sketch: "https://example.com/a.png", style: "gold" }), RenderError);
});

test("calls the chosen engine once and returns the image URL", async () => {
  const calls = [];
  const run = async (model, input) => {
    calls.push({ model, input });
    return { images: [{ url: "https://fal.media/out.jpg" }], requestId: "req-1" };
  };
  const result = await render({ sketch: SKETCH, style: "silver", engine: "nano" }, run);
  assert.equal(result.imageUrl, "https://fal.media/out.jpg");
  assert.equal(result.requestId, "req-1");
  assert.equal(typeof result.ms, "number");
  assert.equal(calls.length, 1);
  assert.equal(calls[0].model, ENGINES.nano.model);
});

test("reports a missing image as an upstream error", async () => {
  const run = async () => ({ images: [] });
  await assert.rejects(render({ sketch: SKETCH, style: "rose" }, run), (e) => e.status === 502);
});
