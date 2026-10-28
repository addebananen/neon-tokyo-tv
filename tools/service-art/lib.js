// Drawing helpers shared by the scenes: number formatting, a seeded random, stars and
// sparkles, the shared glow filters, local units round the subject and a perspective floor.

const f = n => Number(n.toFixed(2));
function rng(seed) { let s = seed >>> 0 || 1; return () => (s = (Math.imul(s, 1664525) + 1013904223) >>> 0) / 4294967296; }

function sparkle(x, y, r, color = '#fff', o = 1, extra = '') {
  const k = r * 0.16;
  return `<path d="M${f(x)},${f(y - r)} Q${f(x + k)},${f(y - k)} ${f(x + r)},${f(y)} Q${f(x + k)},${f(y + k)} ${f(x)},${f(y + r)} Q${f(x - k)},${f(y + k)} ${f(x - r)},${f(y)} Q${f(x - k)},${f(y - k)} ${f(x)},${f(y - r)} Z" fill="${color}" opacity="${f(o)}" ${extra}/>`;
}

function stars(L, n, seed, maxY, color = '#ffffff', minY = 0) {
  const r = rng(seed); let out = '';
  for (let i = 0; i < n; i++) {
    const x = r() * L.W, y = minY + r() * (maxY - minY);
    const big = r() < 0.07;
    const rad = big ? 0.9 + r() * 0.5 : 0.3 + r() * 0.55;
    const o = 0.35 + r() * 0.65;
    out += `<circle cx="${f(x)}" cy="${f(y)}" r="${f(rad)}" fill="${color}" opacity="${f(o)}"/>`;
    if (big) out += sparkle(x, y, rad * 3.4, color, o * 0.7);
  }
  return out;
}

const commonDefs = `
  <filter id="b1" filterUnits="userSpaceOnUse" x="-3000" y="-3000" width="6000" height="6000"><feGaussianBlur stdDeviation="1"/></filter>
  <filter id="b2" filterUnits="userSpaceOnUse" x="-3000" y="-3000" width="6000" height="6000"><feGaussianBlur stdDeviation="2"/></filter>
  <filter id="b4" filterUnits="userSpaceOnUse" x="-3000" y="-3000" width="6000" height="6000"><feGaussianBlur stdDeviation="4"/></filter>
  <filter id="b8" filterUnits="userSpaceOnUse" x="-3000" y="-3000" width="6000" height="6000"><feGaussianBlur stdDeviation="8"/></filter>
  <filter id="b14" filterUnits="userSpaceOnUse" x="-3000" y="-3000" width="6000" height="6000"><feGaussianBlur stdDeviation="14"/></filter>
  <filter id="glow" filterUnits="userSpaceOnUse" x="-3000" y="-3000" width="6000" height="6000">
    <feGaussianBlur stdDeviation="2.4" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <filter id="glowBig" filterUnits="userSpaceOnUse" x="-3000" y="-3000" width="6000" height="6000">
    <feGaussianBlur stdDeviation="5" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>`;

function helpers(L) {
  const X = v => L.cx + v * L.s, Y = v => L.gy + v * L.s, S = v => v * L.s;
  // The frame's edges in local units, for sky decorations that should use the space there is
  // The room on the narrower side, so decorations stay in frame when the subject is off centre
  const halfW = Math.min(L.cx, L.W - L.cx) / L.s, skyTop = -L.gy / L.s, below = (L.H - L.gy) / L.s;
  return { X, Y, S, halfW, skyTop, below };
}

function floorGrid(L, y0, color, { rays = 12, rows = 6, opacity = 0.35, width = 1, spread = 1.8 } = {}) {
  let out = `<g stroke="${color}" stroke-width="${width}" opacity="${opacity}">`;
  const span = L.W / rays;
  for (let i = -rays; i <= rays; i++) {
    out += `<line x1="${f(L.cx + i * span * 0.18)}" y1="${f(y0)}" x2="${f(L.cx + i * span * spread)}" y2="${f(L.H + 4)}"/>`;
  }
  for (let j = 1; j <= rows; j++) {
    const t = j / rows, y = y0 + (L.H - y0) * t * t;
    out += `<line x1="0" y1="${f(y)}" x2="${L.W}" y2="${f(y)}"/>`;
  }
  return out + '</g>';
}

function cubicPoint(p0, p1, p2, p3, t) {
  const u = 1 - t;
  return [
    u * u * u * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t * t * t * p3[0],
    u * u * u * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t * t * t * p3[1]
  ];
}

// The same stars as stars() with the same seed, as twinkle points in local units: x, y, size, phase.
// Only every star bright enough to notice twinkles.
function twinkles(L, n, seed, maxY, minY = 0, minOpacity = 0.6) {
  const r = rng(seed), out = [];
  for (let i = 0; i < n; i++) {
    const x = r() * L.W, y = minY + r() * (maxY - minY);
    const big = r() < 0.07;
    const rad = big ? 0.9 + r() * 0.5 : 0.3 + r() * 0.55;
    const o = 0.35 + r() * 0.65;
    if (o >= minOpacity || big) out.push([f((x - L.cx) / L.s), f((y - L.gy) / L.s), f(rad * (big ? 4 : 2.6) / L.s), f((i * 0.618) % 1)]);
  }
  return out;
}

// Motion channels: a sine wave, and keyframes [[time, value]] repeating every period
const wave = (amp, period, phase = 0, base) => ({ ...(base !== undefined ? { b: base } : {}), w: [[amp, period, phase]] });
const keys = (period, v, lin = false) => ({ k: { p: period, v, ...(lin ? { lin: true } : {}) } });

module.exports = { f, rng, sparkle, stars, commonDefs, helpers, floorGrid, cubicPoint, twinkles, wave, keys };
