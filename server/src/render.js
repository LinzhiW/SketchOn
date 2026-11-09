// Turns a ring sketch into a photoreal render through fal.
//
// Two engines, chosen by comparing five models on the same sketch (2026-11-07):
//   klein  FLUX.2 Klein 4B edit. Follows the drawn shape, about 1.1 s and $0.003 a 512px image.
//          Used live while drawing.
//          Schema: https://fal.ai/models/fal-ai/flux-2/klein/4b/edit/llms.txt
//   nano   Nano Banana 2 edit. The most faithful to the sketch, but about 10 s and $0.08 an image.
//          Used for a final render on request, never live.
//          Schema: https://fal.ai/models/fal-ai/nano-banana-2/edit/llms.txt
// Rejected: z-image turbo controlnet ignored the control image, even fal's own example;
// z-image image-to-image barely changed the sketch; Kontext dev took 8.7 s; fal's realtime
// LCM socket opened but never answered.

// The headset sends ids, never free text, so the prompt stays a ring render.
// Material is what the ring is made of; style is how it is designed; view is which side the
// drawing shows. They are chosen separately and combined here.
export const MATERIALS = {
  gold: "polished yellow gold",
  silver: "brushed sterling silver",
  rose: "rose gold",
  platinum: "platinum",
  clay: "matte hand-shaped polymer clay in soft colours",
  resin: "glossy translucent coloured resin",
};

export const STYLES = {
  refined: "refined, elegant fine jewelry with delicate details",
  cute: "cute cartoon style with rounded, chunky, playful shapes",
  vintage: "vintage antique style with engraved patterns",
  minimal: "minimalist modern design with clean, simple surfaces",
  bold: "bold sculptural statement design",
};

export const VIEWS = {
  front: "seen from the front, looking through the ring's hole",
  side: "seen from the side, showing the band's profile",
  top: "seen from above, looking down on the top of the ring",
};

const MAX_SKETCH_BYTES = 2 * 1024 * 1024;
const SKETCH_PREFIX = /^data:image\/(png|jpeg|webp);base64,/;
const MAX_SEED = 2 ** 31 - 1;
const SIZE = { width: 512, height: 512 };

export class RenderError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function promptFor({ material, style, view }) {
  return (
    `Turn this line sketch into a photorealistic product render of a single finger ring made of ${material}, ` +
    `${style}, ${view}. ` +
    "Keep exactly the drawn shape, proportions and viewpoint. Faint grey guide lines are only a guide. " +
    "Studio lighting, plain light grey background."
  );
}

export const ENGINES = {
  klein: {
    model: "fal-ai/flux-2/klein/4b/edit",
    live: true,
    input: ({ sketch, words, seed }) => ({
      prompt: promptFor(words),
      image_urls: [sketch],
      image_size: SIZE,
      num_images: 1,
      output_format: "jpeg",
      sync_mode: true,
      ...(seed === undefined ? {} : { seed }),
    }),
  },
  nano: {
    model: "fal-ai/nano-banana-2/edit",
    live: false,
    input: ({ sketch, words }) => ({
      prompt: promptFor(words),
      image_urls: [sketch],
      num_images: 1,
      sync_mode: true,
    }),
  },
};

function pick(table, id, name, fallback) {
  const key = id ?? fallback;
  const words = table[key];
  if (!words) throw new RenderError(400, `${name} must be one of: ${Object.keys(table).join(", ")}`);
  return words;
}

export function buildInput({ sketch, material, style, view, engine = "klein", seed }) {
  if (typeof sketch !== "string" || !SKETCH_PREFIX.test(sketch)) {
    throw new RenderError(400, "sketch must be a base64 PNG, JPEG or WebP data URI");
  }
  if (sketch.length > MAX_SKETCH_BYTES) {
    throw new RenderError(413, "sketch is too large");
  }
  const words = {
    material: pick(MATERIALS, material, "material", "gold"),
    style: pick(STYLES, style, "style", "refined"),
    view: pick(VIEWS, view, "view", "front"),
  };
  const spec = ENGINES[engine];
  if (!spec) {
    throw new RenderError(400, `engine must be one of: ${Object.keys(ENGINES).join(", ")}`);
  }
  if (seed !== undefined && !(Number.isInteger(seed) && seed >= 0 && seed <= MAX_SEED)) {
    throw new RenderError(400, "seed must be a whole number from 0 to 2147483647");
  }
  return spec.input({ sketch, words, seed });
}

/**
 * `run(model, input)` calls fal and resolves to `{ images, requestId? }`.
 * It is passed in so tests never call the paid API.
 */
export async function render(request, run) {
  const engine = request?.engine ?? "klein";
  const input = buildInput({ ...request, engine });
  const started = Date.now();
  const result = await run(ENGINES[engine].model, input);
  const image = result?.images?.[0];
  if (!image?.url) throw new RenderError(502, "fal returned no image");
  return { imageUrl: image.url, requestId: result.requestId ?? null, ms: Date.now() - started };
}
