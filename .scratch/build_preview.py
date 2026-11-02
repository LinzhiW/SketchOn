"""Render preview.html from design-spec.json. Scope: web_design_validation_only."""
import base64
import json
from pathlib import Path

HERE = Path(__file__).parent
SKILL = HERE.parent.parent / ".claude/skills/pico-spatial-app-designer"
UI = Path("C:/Users/linzh/Desktop/IDE/aspirium-web-gesture/assets/ui")
SOURCES = {"page-gem": "face-gem-dragon.png", "page-clock": "idle-clock.png", "page-pet": "pet-lizard.png"}

spec_text = (HERE / "design-spec.json").read_text(encoding="utf-8")
spec = json.loads(spec_text)
images = {k: "data:image/png;base64," + base64.b64encode((UI / v).read_bytes()).decode() for k, v in SOURCES.items()}
bundle = (SKILL / "assets/spatialui-web/spatialui.bundle.js").read_text(encoding="utf-8")

RENDERER = r"""
/*OWNERS*/
const spec = JSON.parse(document.querySelector('#pico-design-spec').textContent);
const images = JSON.parse(document.querySelector('#pico-images').textContent);
const app = document.querySelector('#app');
const nodes = Object.fromEntries(spec.nodes.map(n => [n.id, n]));
const brand = spec.theme.brandColors;
const color = name => brand[name] || `var(--sui-${name}, currentColor)`;
const PX_PER_M = 600;
let dataCase = 'normal', stateId = spec.initialStateId, pageIndex = spec.dataCases.normal.activePageIndex;
let tiltDeg = 0, armed = true, dragStartX = null, handIn = false;
const ring = nodes['ring-on-hand'];
const pages = () => spec.dataCases[dataCase].pages;
const fire = event => {
  const action = spec.actions.find(a => a.id === ring.events[event]);
  if (!action) return;
  if (action.type === 'transition') {
    const t = spec.transitions.find(x => x.id === action.transitionId);
    if (t.from === stateId) stateId = t.to;
  } else if (action.type === 'increment') {
    const n = pages().length;
    if (n) pageIndex = (pageIndex + action.value + n) % n;
  }
  paint();
};
function ringSvg() {
  const page = pages()[pageIndex];
  const p = ring.parts;
  const href = page ? images[page.assetId] : '';
  return `<svg viewBox="0 0 240 320" width="240" height="320" role="img" aria-label="${ring.accessibilityLabel}">
    <defs><clipPath id="screen-clip"><circle cx="120" cy="110" r="66"/></clipPath></defs>
    <rect x="76" y="150" width="88" height="160" rx="22" fill="${color(p.band.appearance.fill)}"/>
    <circle cx="120" cy="110" r="84" fill="${color(p.bezel.appearance.fill)}"/>
    <circle cx="120" cy="110" r="66" fill="${color(p.screen.appearance.fill)}"/>
    ${href ? `<image id="ring-page" href="${href}" x="54" y="44" width="132" height="132" clip-path="url(#screen-clip)" style="image-rendering:pixelated"/>` : ''}
  </svg>`;
}
function renderNode(node) {
  const el = document.createElement('div');
  el.dataset.nodeId = node.id;
  if (node.visibleInStates && !node.visibleInStates.includes(stateId)) el.hidden = true;
  if (node.kind === 'layout') {
    el.className = 'overlay';
    node.children.forEach(id => el.append(renderNode(nodes[id])));
  } else if (node.kind === 'domain_visual') {
    el.style.transform = `rotate(${tiltDeg}deg)`;
    el.style.transition = 'transform .08s linear';
    el.innerHTML = ringSvg();
  } else if (node.kind === 'spatialui') {
    const a = node.appearance || {};
    surfaceOwners[node.id](el);
    el.style.setProperty('--pico-design-surface-fill', brand[a.fill] || `var(--sui-${a.fill.replace(/[A-Z]/g, c => '-' + c.toLowerCase())})`);
    el.style.borderRadius = a.radiusDp + 'px';
    el.style.padding = (node.layout?.paddingDp ?? 0) + 'px';
    if (node.layout?.maxWidthDp) el.style.maxWidth = node.layout.maxWidthDp + 'px';
    const t = document.createElement(node.component);
    Object.entries(node.props || {}).forEach(([k, v]) => t.setAttribute(k, v));
    t.textContent = node.content;
    el.append(t);
  }
  return el;
}
function paint() {
  const s = spec.surfaces[0];
  const stage = document.createElement('section');
  stage.id = s.id;
  stage.style.width = stage.style.height = s.defaultSize.width * PX_PER_M + 'px';
  stage.append(renderNode(nodes[spec.states.find(x => x.id === stateId).surfaceRoots[s.id]]));
  app.replaceChildren(stage);
  app.dataset.state = stateId;
  app.dataset.page = pageIndex;
}
const onMove = e => {
  if (dragStartX === null) return;
  tiltDeg = Math.max(-60, Math.min(60, (e.clientX - dragStartX) / 2));
  const { tiltTriggerDeg: trig, tiltResetDeg: reset } = ring.props;
  if (armed && Math.abs(tiltDeg) >= trig) { armed = false; fire(tiltDeg > 0 ? 'tiltForward' : 'tiltBack'); return; }
  if (!armed && Math.abs(tiltDeg) <= reset) armed = true;
  const n = app.querySelector('[data-node-id="ring-on-hand"]');
  if (n) n.style.transform = `rotate(${tiltDeg}deg)`;
};
app.addEventListener('pointerenter', () => { handIn = true; fire('handFound'); });
app.addEventListener('pointerleave', () => { handIn = false; dragStartX = null; tiltDeg = 0; armed = true; fire('handLost'); });
app.addEventListener('pointerdown', e => { dragStartX = e.clientX; });
window.addEventListener('pointermove', onMove);
window.addEventListener('pointerup', () => { dragStartX = null; tiltDeg = 0; armed = true; paint(); });
window.__preview = {
  setDataCase(c) {
    dataCase = c; pageIndex = 0;
    if (c === 'error') { stateId = spec.initialStateId; fire('trackingDenied'); } else { stateId = spec.initialStateId; paint(); }
  },
  fire,
  tilt(deg) { dragStartX = 0; onMove({ clientX: deg * 2 }); },
  release() { dragStartX = null; tiltDeg = 0; armed = true; paint(); },
  state: () => ({ stateId, pageIndex, dataCase }),
};
paint();
"""

owners = "const surfaceOwners = {" + "".join(
    f"{json.dumps(n['id'])}: el => {{ el.dataset.designSurface = {json.dumps(n['id'])}; }},"
    for n in spec["nodes"] if n.get("appearance", {}).get("fill")) + "};"
RENDERER = RENDERER.replace("/*OWNERS*/", owners)
bundle_safe = bundle.replace("</script>", "<\\/script>")
html = f"""<!doctype html>
<html lang="zh"><head><meta charset="utf-8"><title>Gemtronic Ring</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body {{ margin:0; min-height:100vh; display:grid; place-items:center; background-color:#dad6d3; background-image:none; font-family:sans-serif; }}
[data-design-surface] {{ background: var(--pico-design-surface-fill); }}
#app {{ touch-action:none; user-select:none; }}
#stage-scene {{ display:grid; place-items:center; max-width:100vw; }}
.overlay {{ display:grid; place-items:center; }}
.overlay > * {{ grid-area:1/1; }}
.overlay > [hidden] {{ display:none; }}
</style>
<script>{bundle_safe}</script>
</head><body>
<main id="app" data-design-spec-revision="{spec['metadata']['revision']}"></main>
<script id="pico-design-spec" type="application/json">{spec_text}</script>
<script id="pico-images" type="application/json">{json.dumps(images)}</script>
<script type="module">{RENDERER}</script>
</body></html>
"""
(HERE / "preview.html").write_text(html, encoding="utf-8")
print("preview.html", len(html))
