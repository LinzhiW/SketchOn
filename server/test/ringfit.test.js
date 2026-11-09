import { test } from "node:test";
import assert from "node:assert/strict";
import { ringPose } from "../src/ringfit.js";

// A ring of points: outer radius 1, inner 0.8, 0.2 thick, lying flat (hole along Y),
// with a "stone" sticking out on +X, centred away from the origin.
function flatRing({ cx = 3, cy = -2, cz = 5 } = {}) {
  const pts = [];
  for (let a = 0; a < 360; a += 2) {
    for (const r of [0.8, 0.85, 0.9, 0.95, 1]) {
      for (const h of [-0.1, 0, 0.1]) {
        const t = (a * Math.PI) / 180;
        pts.push([cx + r * Math.cos(t), cy + h, cz + r * Math.sin(t)]);
      }
    }
  }
  for (let x = 1; x <= 1.6; x += 0.05) for (const h of [-0.1, 0, 0.1]) pts.push([cx + x, cy + h, cz]);
  return pts;
}

const apply = (m, p) => [0, 1, 2].map((r) => m[r] * p[0] + m[4 + r] * p[1] + m[8 + r] * p[2] + m[12 + r]);

test("finds the hole of a flat ring and stands it up with the stone on top", () => {
  const pose = ringPose(flatRing());
  assert.equal(pose.holeAxis, "y");
  assert.equal(pose.holeFound, true);
  // The hole centre moves to the origin.
  const centre = apply(pose.matrix, [3, -2, 5]);
  centre.forEach((c) => assert.ok(Math.abs(c) < 0.03, `centre ${centre}`));
  // The stone tip (+X in the input) ends up on +Y.
  const tip = apply(pose.matrix, [3 + 1.6, -2, 5]);
  assert.ok(tip[1] > 0.7, `tip ${tip}`);
  // Outer diameter becomes 1.
  const edge = apply(pose.matrix, [3 - 1, -2, 5]);
  assert.ok(Math.abs(Math.hypot(edge[0], edge[1]) - 0.5) < 0.03, `edge ${edge}`);
});

test("keeps the result a rotation, not a mirror", () => {
  const m = ringPose(flatRing()).matrix;
  const det =
    m[0] * (m[5] * m[10] - m[9] * m[6]) - m[4] * (m[1] * m[10] - m[9] * m[2]) + m[8] * (m[1] * m[6] - m[5] * m[2]);
  assert.ok(det > 0);
});
