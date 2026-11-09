import { test } from "node:test";
import assert from "node:assert/strict";
import { buildInput, render, RenderError, ENGINES } from "../src/render.js";

const SKETCH = "data:image/png;base64,iVBORw0KGgo=";

test("builds the Klein input from a sketch, with defaults for material, style and view", () => {
  const input = buildInput({ sketch: SKETCH });
  assert.deepEqual(input.image_urls, [SKETCH]);
  assert.match(input.prompt, /yellow gold/);
  assert.deepEqual(input.image_size, { width: 512, height: 512 });
  assert.equal("seed" in input, false);
});

test("passes a fixed seed to Klein", () => {
  assert.equal(buildInput({ sketch: SKETCH, material: "rose", seed: 42 }).seed, 42);
});

test("builds the Nano Banana input without a seed", () => {
  const input = buildInput({ sketch: SKETCH, material: "silver", engine: "nano", seed: 42 });
  assert.deepEqual(input.image_urls, [SKETCH]);
  assert.equal("seed" in input, false);
});

test("only Klein runs live", () => {
  assert.equal(ENGINES.klein.live, true);
  assert.equal(ENGINES.nano.live, false);
});

test("rejects a material, style or view that is not on the list", () => {
  assert.throws(() => buildInput({ sketch: SKETCH, material: "anything you like" }), RenderError);
  assert.throws(() => buildInput({ sketch: SKETCH, style: "anything" }), RenderError);
  assert.throws(() => buildInput({ sketch: SKETCH, view: "back" }), RenderError);
});

test("puts the chosen colour into clay and resin, and leaves metals alone", () => {
  assert.match(buildInput({ sketch: SKETCH, material: "resin", color: "mint" }).prompt, /translucent mint green resin/);
  assert.doesNotMatch(buildInput({ sketch: SKETCH, material: "gold", color: "mint" }).prompt, /mint/);
  assert.throws(() => buildInput({ sketch: SKETCH, material: "clay", color: "plaid" }), RenderError);
});

test("combines material, style and view in the prompt", () => {
  const { prompt } = buildInput({ sketch: SKETCH, material: "clay", style: "cute", view: "top" });
  assert.match(prompt, /polymer clay/);
  assert.match(prompt, /cartoon/);
  assert.match(prompt, /looking down at the top/);
});

test("rejects an unknown engine and a bad seed", () => {
  assert.throws(() => buildInput({ sketch: SKETCH, engine: "other" }), RenderError);
  assert.throws(() => buildInput({ sketch: SKETCH, seed: -1 }), RenderError);
  assert.throws(() => buildInput({ sketch: SKETCH, seed: 1.5 }), RenderError);
});

test("rejects a sketch that is not an image data URI", () => {
  assert.throws(() => buildInput({ sketch: "https://example.com/a.png" }), RenderError);
});

test("calls the chosen engine once and returns the image URL", async () => {
  const calls = [];
  const run = async (model, input) => {
    calls.push({ model, input });
    return { images: [{ url: "https://fal.media/out.jpg" }], requestId: "req-1" };
  };
  const result = await render({ sketch: SKETCH, material: "silver", engine: "nano" }, run);
  assert.equal(result.imageUrl, "https://fal.media/out.jpg");
  assert.equal(result.requestId, "req-1");
  assert.equal(typeof result.ms, "number");
  assert.equal(calls.length, 1);
  assert.equal(calls[0].model, ENGINES.nano.model);
});

test("reports a missing image as an upstream error", async () => {
  const run = async () => ({ images: [] });
  await assert.rejects(render({ sketch: SKETCH, material: "rose" }, run), (e) => e.status === 502);
});
