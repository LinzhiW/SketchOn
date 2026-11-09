// Turns a colour picked on the headset's colour wheel into words an image model understands.
//
// Image models follow colour names far better than hex codes, so the prompt gets the nearest
// name with the hex beside it, for example "soft pink (#F4B6C2)".

const NAMES = [
  ["white", 0xffffff], ["cream", 0xf7f1e3], ["light grey", 0xc8c8c8], ["grey", 0x808080],
  ["charcoal", 0x3a3a3a], ["black", 0x111111],
  ["red", 0xd62828], ["cherry red", 0xb5172e], ["coral", 0xff7f6b], ["soft pink", 0xf4b6c2],
  ["hot pink", 0xff3fa4], ["peach", 0xffc8a2], ["orange", 0xf28c28], ["mustard", 0xd4a72c],
  ["lemon yellow", 0xfff09a], ["yellow", 0xf5d000], ["lime green", 0xa6d84a], ["mint green", 0xa8e6cf],
  ["green", 0x2e9e4f], ["forest green", 0x1f5e3a], ["teal", 0x1f8a8a], ["sky blue", 0x9ed2f5],
  ["blue", 0x2f6fd6], ["navy blue", 0x1d2f6b], ["lilac", 0xc8b6f2], ["purple", 0x7b3fbf],
  ["brown", 0x7a4a2a], ["beige", 0xd9c3a0],
];

const HEX = /^#?([0-9a-fA-F]{6})$/;

export function isHexColor(value) {
  return typeof value === "string" && HEX.test(value);
}

/** "#f4b6c2" -> "soft pink (#F4B6C2)". Throws on anything that is not a 6-digit hex colour. */
export function describeColor(value) {
  const match = HEX.exec(value ?? "");
  if (!match) throw new Error("not a hex colour");
  const rgb = parseInt(match[1], 16);
  const [r, g, b] = [rgb >> 16, (rgb >> 8) & 255, rgb & 255];
  let best = NAMES[0][0];
  let bestDistance = Infinity;
  for (const [name, c] of NAMES) {
    // Weighted RGB distance ("redmean"), close enough to how people see colour for naming.
    const [cr, cg, cb] = [c >> 16, (c >> 8) & 255, c & 255];
    const mean = (r + cr) / 2;
    const d = (2 + mean / 256) * (r - cr) ** 2 + 4 * (g - cg) ** 2 + (2 + (255 - mean) / 256) * (b - cb) ** 2;
    if (d < bestDistance) {
      bestDistance = d;
      best = name;
    }
  }
  return `${best} (#${match[1].toUpperCase()})`;
}
