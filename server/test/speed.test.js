import { test } from "node:test";
import assert from "node:assert/strict";
import { tripoInput } from "../src/to3d.js";
import { giveMaterial } from "../src/ringfit.js";
import { buildInput } from "../src/render.js";

const SKETCH = "data:image/png;base64,AAAA";

test("clay and resin get a plain texture to paint on; metal gets PBR", () => {
  for (const m of ["clay", "resin"]) {
    const input = tripoInput("https://x/y.png", m);
    assert.equal(input.texture, "standard");
    assert.equal(input.pbr, false);
  }
  const gold = tripoInput("https://x/y.png", "gold");
  assert.equal(gold.pbr, true);
  assert.equal(tripoInput("https://x/y.png", null).pbr, true);
});

test("a model with no material gets a white one per part", () => {
  const json = { meshes: [{ primitives: [{ attributes: {} }, { attributes: {}, material: 5 }] }] };
  giveMaterial(json);
  assert.equal(json.materials.length, 2);
  assert.equal(json.meshes[0].primitives[0].material, 0);
  assert.equal(json.meshes[0].primitives[1].material, 1);
});

test("a model that has its materials keeps them", () => {
  const json = { materials: [{ name: "tripo" }], meshes: [{ primitives: [{ material: 0 }] }] };
  giveMaterial(json);
  assert.deepEqual(json.materials, [{ name: "tripo" }]);
});

test("metal prompts add a stone only when drawn, and no view names one", () => {
  for (const view of ["front", "top", "side"]) {
    const { prompt } = buildInput({ sketch: SKETCH, material: "gold", view });
    assert.match(prompt, /only where the drawing clearly shows one/);
    assert.doesNotMatch(prompt, /\bstone on top\b|defines the stone|stone sits/);
  }
});
