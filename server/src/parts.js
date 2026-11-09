// Splits a posed ring into parts that can be coloured separately: the band, and whatever
// sticks out of it (a stone, ears, wings...).
//
// In the standard pose (ringfit.js) the band is the ring around the Z axis with outer radius
// 0.5. A triangle whose centre lies further than BAND_EDGE from the Z axis is decoration.
// Decoration lying on the band's face, inside that radius, stays with the band; painting
// parts by brush is the later fix.
//
// The split keeps the vertices as they are and gives each part its own index list and its
// own copy of the material, so the app can colour one material without touching the other.

const BAND_EDGE = 0.53;
const MIN_SHARE = 0.02;
const BIN_CHUNK = 0x004e4942;

function transform(m, p) {
  return [0, 1, 2].map((r) => m[r] * p[0] + m[4 + r] * p[1] + m[8 + r] * p[2] + m[12 + r]);
}

/**
 * `glb` is from readGlb, `matrix` the pose's column-major matrix. Returns the new binary
 * chunk (with its header) and the number of parts; the JSON is changed in place.
 */
export function splitParts(glb, matrix) {
  const { json, bin } = glb;
  const mesh = json.meshes?.[json.nodes?.[0]?.mesh];
  if (!mesh || mesh.primitives.length !== 1) return { rest: glb.rest, parts: 1 };
  const prim = mesh.primitives[0];
  if (prim.indices === undefined) return { rest: glb.rest, parts: 1 };

  const posAcc = json.accessors[prim.attributes.POSITION];
  const posView = json.bufferViews[posAcc.bufferView];
  const posStride = posView.byteStride ?? 12;
  const posStart = (posView.byteOffset ?? 0) + (posAcc.byteOffset ?? 0);
  const vertex = (i) => {
    const o = posStart + i * posStride;
    return [bin.readFloatLE(o), bin.readFloatLE(o + 4), bin.readFloatLE(o + 8)];
  };

  const idxAcc = json.accessors[prim.indices];
  const idxView = json.bufferViews[idxAcc.bufferView];
  const idxStart = (idxView.byteOffset ?? 0) + (idxAcc.byteOffset ?? 0);
  const size = { 5121: 1, 5123: 2, 5125: 4 }[idxAcc.componentType];
  if (!size) return { rest: glb.rest, parts: 1 };
  const index = (i) => {
    const o = idxStart + i * size;
    return size === 4 ? bin.readUInt32LE(o) : size === 2 ? bin.readUInt16LE(o) : bin.readUInt8(o);
  };

  const band = [];
  const deco = [];
  for (let t = 0; t + 2 < idxAcc.count; t += 3) {
    const tri = [index(t), index(t + 1), index(t + 2)];
    const c = [0, 0, 0];
    for (const v of tri) {
      const p = transform(matrix, vertex(v));
      for (let k = 0; k < 3; k++) c[k] += p[k] / 3;
    }
    (Math.hypot(c[0], c[1]) > BAND_EDGE ? deco : band).push(...tri);
  }
  const total = band.length + deco.length;
  if (deco.length < total * MIN_SHARE || band.length < total * MIN_SHARE) {
    return { rest: glb.rest, parts: 1 };
  }

  // Append the two index lists to the binary data, each 4-byte aligned.
  const pieces = [bin];
  let length = bin.length;
  const pad = () => {
    const extra = (4 - (length % 4)) % 4;
    if (extra) {
      pieces.push(Buffer.alloc(extra));
      length += extra;
    }
  };
  const addIndices = (list) => {
    pad();
    const data = Buffer.alloc(list.length * 4);
    list.forEach((v, i) => data.writeUInt32LE(v, i * 4));
    json.bufferViews.push({ buffer: 0, byteOffset: length, byteLength: data.length, target: 34963 });
    pieces.push(data);
    length += data.length;
    json.accessors.push({
      bufferView: json.bufferViews.length - 1,
      componentType: 5125,
      count: list.length,
      type: "SCALAR",
    });
    return json.accessors.length - 1;
  };
  const bandIndices = addIndices(band);
  const decoIndices = addIndices(deco);
  pad();
  json.buffers[0].byteLength = length;

  const decoMaterial = structuredClone(json.materials[prim.material ?? 0] ?? {});
  decoMaterial.name = "decoration";
  json.materials.push(decoMaterial);
  if (json.materials[prim.material ?? 0]) json.materials[prim.material ?? 0].name = "band";
  mesh.primitives = [
    { ...prim, indices: bandIndices },
    { ...prim, indices: decoIndices, material: json.materials.length - 1 },
  ];

  const data = Buffer.concat(pieces, length);
  const header = Buffer.alloc(8);
  header.writeUInt32LE(data.length, 0);
  header.writeUInt32LE(BIN_CHUNK, 4);
  return { rest: Buffer.concat([header, data]), parts: 2 };
}
