// Puts a generated ring into a standard pose, so the app can wear it without guessing.
//
// Tripo returns rings in any pose: the first one stood upright with its hole along X, the
// second lay flat with its hole along Y. This finds the hole in the mesh itself and rewrites
// the GLB's root node so that, in the model's own space:
//   - the hole runs along Z (the finger's axis in the app),
//   - the hole centre is at the origin,
//   - the side that sticks out furthest (a stone, usually) points to +Y,
//   - the band's outer diameter is 1, so the app only scales it to the finger.
//
// How the hole is found: the thinnest axis of the bounding box is taken as the hole's axis.
// The vertices are flattened along it onto a grid; empty cells not reachable from the grid's
// edge are the hole. Its centroid is the hole centre. The band's outer radius is the shortest
// distance from there to the edge of the box; the longest is where the stone is.

const GLB_MAGIC = 0x46546c67;
const JSON_CHUNK = 0x4e4f534a;
const GRID = 96;

export function readGlb(buffer) {
  if (buffer.readUInt32LE(0) !== GLB_MAGIC) throw new Error("not a GLB file");
  const jsonLength = buffer.readUInt32LE(12);
  if (buffer.readUInt32LE(16) !== JSON_CHUNK) throw new Error("GLB has no JSON chunk first");
  const json = JSON.parse(buffer.subarray(20, 20 + jsonLength).toString("utf8"));
  const rest = buffer.subarray(20 + jsonLength);
  const bin = rest.length >= 8 ? rest.subarray(8, 8 + rest.readUInt32LE(0)) : Buffer.alloc(0);
  return { json, bin, rest };
}

export function writeGlb(json, rest) {
  let text = Buffer.from(JSON.stringify(json), "utf8");
  const pad = (4 - (text.length % 4)) % 4;
  text = Buffer.concat([text, Buffer.alloc(pad, 0x20)]);
  const header = Buffer.alloc(20);
  header.writeUInt32LE(GLB_MAGIC, 0);
  header.writeUInt32LE(2, 4);
  header.writeUInt32LE(20 + text.length + rest.length, 8);
  header.writeUInt32LE(text.length, 12);
  header.writeUInt32LE(JSON_CHUNK, 16);
  return Buffer.concat([header, text, rest]);
}

/** All POSITION vertices of the mesh on the single root node, as [x, y, z] triples. */
export function positions({ json, bin }) {
  const mesh = json.meshes?.[json.nodes?.[0]?.mesh];
  if (!mesh) throw new Error("no mesh on the first node");
  const points = [];
  for (const primitive of mesh.primitives) {
    const accessor = json.accessors[primitive.attributes.POSITION];
    if (accessor.componentType !== 5126 || accessor.type !== "VEC3" || accessor.sparse) {
      throw new Error("unsupported POSITION layout");
    }
    const view = json.bufferViews[accessor.bufferView];
    const stride = view.byteStride ?? 12;
    const start = (view.byteOffset ?? 0) + (accessor.byteOffset ?? 0);
    for (let i = 0; i < accessor.count; i++) {
      const o = start + i * stride;
      points.push([bin.readFloatLE(o), bin.readFloatLE(o + 4), bin.readFloatLE(o + 8)]);
    }
  }
  return points;
}

/** Finds the hole and returns the pose as a column-major 4x4 matrix, plus what it found. */
export function ringPose(points) {
  const min = [Infinity, Infinity, Infinity];
  const max = [-Infinity, -Infinity, -Infinity];
  for (const p of points) {
    for (let k = 0; k < 3; k++) {
      if (p[k] < min[k]) min[k] = p[k];
      if (p[k] > max[k]) max[k] = p[k];
    }
  }
  const size = [0, 1, 2].map((k) => max[k] - min[k]);
  const axis = size.indexOf(Math.min(...size));
  const [u, v] = [0, 1, 2].filter((k) => k !== axis);

  // Flatten onto a grid and mark the cells that vertices fall in.
  const cell = Math.max(size[u], size[v]) / GRID;
  const cols = Math.max(1, Math.ceil(size[u] / cell));
  const rows = Math.max(1, Math.ceil(size[v] / cell));
  const filled = new Uint8Array(cols * rows);
  for (const p of points) {
    const c = Math.min(cols - 1, Math.floor((p[u] - min[u]) / cell));
    const r = Math.min(rows - 1, Math.floor((p[v] - min[v]) / cell));
    filled[r * cols + c] = 1;
  }
  // Close one-cell gaps between vertices on the band, so the hole is sealed.
  const solid = filled.slice();
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      if (filled[r * cols + c]) continue;
      let n = 0;
      for (let dr = -1; dr <= 1; dr++) {
        for (let dc = -1; dc <= 1; dc++) {
          const rr = r + dr;
          const cc = c + dc;
          if (rr >= 0 && rr < rows && cc >= 0 && cc < cols && filled[rr * cols + cc]) n++;
        }
      }
      if (n >= 5) solid[r * cols + c] = 1;
    }
  }
  // Empty cells reachable from the edge are outside; the rest of the empty cells are the hole.
  const outside = new Uint8Array(cols * rows);
  const stack = [];
  for (let c = 0; c < cols; c++) stack.push(c, (rows - 1) * cols + c);
  for (let r = 0; r < rows; r++) stack.push(r * cols, r * cols + cols - 1);
  while (stack.length) {
    const i = stack.pop();
    if (outside[i] || solid[i]) continue;
    outside[i] = 1;
    const r = Math.floor(i / cols);
    const c = i % cols;
    if (c > 0) stack.push(i - 1);
    if (c < cols - 1) stack.push(i + 1);
    if (r > 0) stack.push(i - cols);
    if (r < rows - 1) stack.push(i + cols);
  }
  let holeCells = 0;
  let sumU = 0;
  let sumV = 0;
  for (let i = 0; i < cols * rows; i++) {
    if (solid[i] || outside[i]) continue;
    holeCells++;
    sumU += (i % cols) + 0.5;
    sumV += Math.floor(i / cols) + 0.5;
  }

  const centre = [0, 0, 0];
  centre[axis] = (min[axis] + max[axis]) / 2;
  let holeFound = holeCells >= 4;
  if (holeFound) {
    centre[u] = min[u] + (sumU / holeCells) * cell;
    centre[v] = min[v] + (sumV / holeCells) * cell;
  } else {
    centre[u] = (min[u] + max[u]) / 2;
    centre[v] = (min[v] + max[v]) / 2;
  }

  // Distances from the hole centre to each side of the box, in the band's plane.
  const sides = [
    { dir: [u, 1], reach: max[u] - centre[u] },
    { dir: [u, -1], reach: centre[u] - min[u] },
    { dir: [v, 1], reach: max[v] - centre[v] },
    { dir: [v, -1], reach: centre[v] - min[v] },
  ];
  const outer = Math.min(...sides.map((s) => s.reach));
  if (!(outer > 0)) throw new Error("model has no size");
  const furthest = sides.reduce((a, b) => (b.reach > a.reach ? b : a));
  // A plain band reaches about equally far everywhere. Then keep the model's own +Y up if Y
  // lies in the band's plane, since Tripo keeps the picture's up as +Y.
  const hasStone = furthest.reach >= outer * 1.15;
  const up = hasStone || axis === 1 ? furthest.dir : [1, 1];

  const unit = (k, sign) => [0, 1, 2].map((i) => (i === k ? sign : 0));
  const zAxis = unit(axis, 1);
  const yAxis = unit(up[0], up[1]);
  const xAxis = [
    yAxis[1] * zAxis[2] - yAxis[2] * zAxis[1],
    yAxis[2] * zAxis[0] - yAxis[0] * zAxis[2],
    yAxis[0] * zAxis[1] - yAxis[1] * zAxis[0],
  ];
  const s = 1 / (2 * outer);
  const rowsOfR = [xAxis, yAxis, zAxis];
  // p' = s * R * (p - centre). Column-major: m[col * 4 + row].
  const m = new Array(16).fill(0);
  for (let row = 0; row < 3; row++) {
    for (let col = 0; col < 3; col++) m[col * 4 + row] = s * rowsOfR[row][col];
    m[12 + row] = -s * (rowsOfR[row][0] * centre[0] + rowsOfR[row][1] * centre[1] + rowsOfR[row][2] * centre[2]);
  }
  m[15] = 1;
  return { matrix: m, holeAxis: "xyz"[axis], holeFound, outerRadius: outer, centre };
}

/** Rewrites a Tripo GLB into the standard ring pose. Returns the new file and what was found. */
export function poseRing(buffer) {
  const glb = readGlb(buffer);
  if (glb.json.nodes?.length !== 1 || glb.json.scenes?.[0]?.nodes?.length !== 1) {
    throw new Error("expected one node");
  }
  const pose = ringPose(positions(glb));
  const node = glb.json.nodes[0];
  delete node.translation;
  delete node.rotation;
  delete node.scale;
  node.matrix = pose.matrix;
  return { glb: writeGlb(glb.json, glb.rest), pose };
}
