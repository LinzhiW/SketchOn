/*OWNERS*/
const spec = JSON.parse(document.querySelector('#pico-design-spec').textContent);
const images = JSON.parse(document.querySelector('#pico-images').textContent);
const app = document.querySelector('#app');
const nodes = Object.fromEntries(spec.nodes.map(n => [n.id, n]));
const color = name => spec.theme.brandColors[name] || `var(--sui-${name.replace(/[A-Z]/g, c => '-' + c.toLowerCase())})`;
const PX_PER_M = 600;
const ring = nodes['ring-on-hand'], picker = nodes['hand-picker'];
const SPOTS = { thumb: [36, 224, 58, 172, -38], index: [104, 62, 46, 180, -8], middle: [160, 20, 46, 216, -2], ring: [216, 44, 46, 196, 5], little: [272, 104, 42, 150, 14] };
let dataCase = 'normal', stateId = spec.initialStateId, finger = picker.props.default;
let tipDeg = 0, armed = true, dragStartX = null, clicks = 0, clickTimer = null;
const menu = () => spec.dataCases[dataCase].menu;
let level = menu().root, index = 0, trail = [];
const items = () => menu().levels[level] || [];
function act(id) {
  const a = spec.actions.find(x => x.id === id);
  if (!a) return;
  if (a.type === 'increment') { const n = items().length; if (n) index = (index + a.value + n) % n; }
  if (id === 'open-item' || id === 'button-confirm') { const to = items()[index]?.opens; if (to) { trail.push([level, index]); level = to; index = 0; } }
  if (id === 'go-back' || id === 'button-back') { const t = trail.pop(); if (t) [level, index] = t; }
  paint();
}
function fire(nodeId, event, value) {
  if (nodeId === 'hand-picker') finger = value;
  const t = spec.transitions.find(x => x.from === stateId && x.trigger.nodeId === nodeId && x.trigger.event === event);
  if (t) stateId = t.to;
  const id = nodes[nodeId].events?.[event];
  if (!t && id) act(id); else paint();
}
function ringSvg() {
  const item = items()[index], p = ring.parts;
  const href = item ? images['frame-' + item.frame] : '';
  return `<svg viewBox="0 0 240 320" width="240" height="320" role="img" aria-label="${ring.accessibilityLabel}">
    <defs><clipPath id="screen-clip"><circle cx="120" cy="110" r="66"/></clipPath></defs>
    <rect x="76" y="150" width="88" height="160" rx="22" fill="${color(p.band.appearance.fill)}"/>
    <circle cx="120" cy="110" r="84" fill="${color(p.bezel.appearance.fill)}"/>
    <circle cx="120" cy="110" r="66" fill="${color(p.screen.appearance.fill)}"/>
    ${href ? `<image id="ring-page" href="${href}" x="54" y="44" width="132" height="132" clip-path="url(#screen-clip)" style="image-rendering:pixelated"/>` : ''}
  </svg>`;
}
function handSvg() {
  const { widthDp: w, heightDp: h } = picker.props, parts = picker.parts;
  const fingers = picker.props.fingers.map(f => {
    const [x, y, fw, fh, tilt] = SPOTS[f], on = f === finger;
    const fill = on ? color(parts.finger.states.selected.fill) : color(parts.finger.appearance.fill);
    const markFill = color(parts.ringMark.appearance.fill);
    const mark = on ? `<rect x="${x}" y="${y + fh * 0.72 - 7}" width="${fw}" height="14" fill="${markFill}"/><circle cx="${x + fw / 2}" cy="${y + fh * 0.72}" r="11" fill="${markFill}"/>` : '';
    return `<g data-finger="${f}" style="cursor:pointer" transform="rotate(${tilt} ${x + fw / 2} ${y + fh / 2})"><rect x="${x}" y="${y}" width="${fw}" height="${fh}" rx="${fw / 2}" fill="${fill}"/>${mark}</g>`;
  }).join('');
  return `<svg viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" role="img" aria-label="${picker.accessibilityLabel}"><rect x="100" y="210" width="216" height="200" rx="${parts.palm.appearance.radiusDp}" fill="${color(parts.palm.appearance.fill)}"/>${fingers}</svg>`;
}
function surface(el, node) {
  const a = node.appearance;
  surfaceOwners[node.id](el);
  el.style.setProperty('--pico-design-surface-fill', color(a.fill));
  el.style.borderRadius = a.radiusDp + 'px';
  el.style.padding = (node.layout?.paddingDp ?? 0) + 'px';
  if (node.layout?.widthDp) el.style.width = node.layout.widthDp + 'px';
  if (node.layout?.maxWidthDp) el.style.maxWidth = node.layout.maxWidthDp + 'px';
}
function textEl(node) {
  const t = document.createElement('sui-text');
  Object.entries(node.props || {}).forEach(([k, v]) => t.setAttribute(k, v));
  if (node.textStyle) t.setAttribute('variant', node.textStyle.typography === 'title' ? 'titleLarge' : 'bodyLarge');
  t.setAttribute('color', color(node.appearance?.foreground || node.textStyle?.color || 'labelPrimaryLight'));
  t.textContent = node.content;
  return t;
}
function renderNode(node) {
  const el = document.createElement('div');
  el.dataset.nodeId = node.id;
  if (node.visibleInStates && !node.visibleInStates.includes(stateId)) el.hidden = true;
  if (node.kind === 'layout') {
    el.className = node.layout.mode === 'row' ? 'row' : 'overlay';
    node.children.forEach(id => el.append(renderNode(nodes[id])));
  } else if (node.id === 'ring-on-hand') {
    el.style.transform = `rotate(${tipDeg}deg)`;
    el.innerHTML = ringSvg();
  } else if (node.id === 'ring-mirror') {
    const item = items()[index];
    if (item) el.innerHTML = `<img alt="${node.accessibilityLabel}" src="${images['frame-' + item.frame]}" width="${node.props.sizeDp}" height="${node.props.sizeDp}" style="image-rendering:pixelated;border-radius:50%;display:block;margin:0 auto">`;
  } else if (node.id === 'hand-picker') {
    el.innerHTML = handSvg();
    el.querySelectorAll('[data-finger]').forEach(g => g.addEventListener('click', () => fire('hand-picker', 'tapFinger', g.dataset.finger)));
  } else if (node.component === 'sui-icon-button') {
    const b = document.createElement('sui-icon-button');
    b.setAttribute('aria-label', node.accessibilityLabel);
    b.innerHTML = images[node.assetId];
    b.addEventListener('click', () => fire(node.id, 'click'));
    el.append(b);
  } else if (node.component === 'sui-button') {
    const b = document.createElement('sui-button');
    Object.entries(node.props || {}).forEach(([k, v]) => b.setAttribute(k, v));
    b.setAttribute('text', node.content);
    b.textContent = node.content;
    b.addEventListener('click', () => fire(node.id, 'click'));
    el.append(b);
  } else {
    if (node.appearance?.fill) surface(el, node);
    el.className = 'column';
    el.append(textEl(node));
    (node.children || []).forEach(id => el.append(renderNode(nodes[id])));
  }
  return el;
}
function paint() {
  const s = spec.surfaces[0];
  const stage = document.createElement('section');
  stage.id = s.id;
  stage.style.width = s.defaultSize.width * PX_PER_M + 240 + 'px';
  stage.style.height = s.defaultSize.height * PX_PER_M + 'px';
  stage.append(renderNode(nodes[spec.states.find(x => x.id === stateId).surfaceRoots[s.id]]));
  app.replaceChildren(stage);
  app.dataset.state = stateId;
  app.dataset.frame = items()[index]?.frame ?? '';
}
function airClick() {
  clicks += 1;
  clearTimeout(clickTimer);
  if (clicks === 2) { clicks = 0; fire('ring-on-hand', 'airDoubleClick'); return; }
  clickTimer = setTimeout(() => { clicks = 0; fire('ring-on-hand', 'airClick'); }, ring.props.doubleClickWaitMs);
}
const onMove = x => {
  if (dragStartX === null) return;
  tipDeg = Math.max(-60, Math.min(60, (x - dragStartX) / 2));
  const { tipEnterDeg: on, tipExitDeg: off } = ring.props;
  if (armed && Math.abs(tipDeg) >= on) { armed = false; fire('ring-on-hand', tipDeg > 0 ? 'tipRight' : 'tipLeft'); return; }
  if (!armed && Math.abs(tipDeg) <= off) armed = true;
  const n = app.querySelector('[data-node-id="ring-on-hand"]');
  if (n) n.style.transform = `rotate(${tipDeg}deg)`;
};
app.addEventListener('pointerenter', () => fire('ring-on-hand', 'handFound'));
app.addEventListener('pointerleave', () => { dragStartX = null; tipDeg = 0; armed = true; fire('ring-on-hand', 'handLost'); });
app.addEventListener('pointerdown', e => { if (stateId === 'ring-worn') dragStartX = e.clientX; });
window.addEventListener('pointermove', e => onMove(e.clientX));
window.addEventListener('pointerup', () => { dragStartX = null; if (tipDeg !== 0) { tipDeg = 0; armed = true; paint(); } });
window.__preview = {
  setDataCase(c) {
    dataCase = c; level = menu().root; index = 0; trail = []; stateId = spec.initialStateId;
    if (c === 'error') { stateId = 'hand-searching'; fire('ring-on-hand', 'trackingDenied'); } else paint();
  },
  fire, airClick,
  tilt(deg) { dragStartX = 0; onMove(deg * 2); },
  release() { dragStartX = null; tipDeg = 0; armed = true; paint(); },
  state: () => ({ stateId, finger, level, index, frame: app.dataset.frame, dataCase }),
};
paint();
