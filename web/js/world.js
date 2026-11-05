// The Neon Tokyo world behind every page, and the light that follows the focus: the skyline
// leans toward the lit card, and a pool of light glides along the floor under it.
//
// The world is drawn once. What moves is a transform or a fade (see .bg in neon.css), so the
// compositor does the work and the page's own thread stays free for scrolling and keys.

const Q = 0.8; // cross-line spacing ratio; the roll scales by 1/Q

function rng(seed) {
  let s = seed * 2654435761 >>> 0 || 1;
  return () => { s ^= s << 13; s >>>= 0; s ^= s >> 17; s ^= s << 5; s >>>= 0; return (s % 1e6) / 1e6; };
}

/** Stars as one element's shadows: painted once, then only faded. */
function stars(seed, count) {
  const r = rng(seed);
  const out = [];
  for (let i = 0; i < count; i++) {
    const x = (r() * 100).toFixed(2), y = (r() * r() * 44).toFixed(2);
    out.push(i % 9 === 0
      ? `${x}vw ${y}vh 4px 1.4px rgba(185,166,255,.32), ${x}vw ${y}vh 0 .8px rgba(255,255,255,.75)`
      : `${x}vw ${y}vh 0 .2px rgba(255,255,255,.6)`);
  }
  return out.join(',');
}

function lanes() {
  let l = '';
  for (let i = -26; i <= 26; i++) {
    const a = (0.55 * (1 - Math.abs(i) / 26 * 0.6) * 0.7).toFixed(3);
    l += `<line x1="${500 + i * 2}" y1="0" x2="${(500 + i * 1000 / 14).toFixed(1)}" y2="100" stroke-opacity="${a}" stroke-width="1.2"/>`;
  }
  return `<svg class="bg-lanes" viewBox="0 0 1000 100" preserveAspectRatio="none">${l}</svg>`;
}

function cross() {
  let l = '';
  for (let d = 100; d > 0.4; d *= Q) {
    const k = d / 100;
    l += `<line x1="0" y1="${d.toFixed(3)}" x2="100" y2="${d.toFixed(3)}" stroke-opacity="${(0.6 * Math.min(1, k * 1.6)).toFixed(3)}" stroke-width="${(0.6 + 2 * k).toFixed(2)}"/>`;
  }
  return `<svg class="bg-cross" viewBox="0 0 100 100" preserveAspectRatio="none">${l}</svg>`;
}

export function worldHtml() {
  return `<div class="bg" aria-hidden="true">
    <div class="bg-sky"></div>
    <div class="bg-glow"></div>
    <i class="bg-stars" style="box-shadow:${stars(7, 70)}"></i>
    <i class="bg-stars b" style="box-shadow:${stars(19, 50)}"></i>
    <i class="bg-shoot"></i>
    <img class="bg-city" src="img/world-city.webp" srcset="img/world-city-sm.webp 768w, img/world-city.webp 1280w" sizes="104vw" alt="" decoding="async" fetchpriority="low">
    <div class="bg-floor">${lanes()}${cross()}</div>
    <div class="bg-horizon"></div>
    <div class="bg-shade"></div>
    <div class="bg-quiet"></div>
  </div>`;
}

let bg = null;
let leanNow = 0;
/** Leans the skyline and floor toward a point on screen (0 = left edge, 1 = right edge). */
export function leanToward(xShare) {
  bg = bg && bg.isConnected ? bg : document.querySelector('.bg');
  if (!bg) return;
  const v = Math.round((Math.max(0, Math.min(1, xShare)) - 0.5) * 100) / 100;
  if (v === leanNow) return;
  leanNow = v;
  bg.style.setProperty('--lean', v);
}

/** Moves the row's floor light under `card` (it glides there on Neon Home's spring). */
export function floorUnder(card, cls = 'on') {
  const track = card && card.parentElement;
  if (!track || !track.classList.contains('row-track')) return;
  let f = track.firstElementChild && track.firstElementChild.classList.contains('floor') ? track.firstElementChild : null;
  const fresh = !f;
  if (fresh) { f = document.createElement('i'); f.className = 'floor'; f.setAttribute('aria-hidden', 'true'); track.prepend(f); }
  const poster = card.querySelector('.poster') || card;
  const w = card.offsetWidth * 1.3;
  const x = card.offsetLeft + card.offsetWidth / 2 - w / 2;
  const y = card.offsetTop + poster.offsetHeight - 12;
  if (fresh) f.style.transition = 'none';
  f.style.transform = `translate3d(${x}px, ${y}px, 0) scale(${(w / 100).toFixed(3)}, 1)`;
  if (fresh) { void f.offsetWidth; f.style.transition = ''; }
  f.classList.add(cls);
}

export function floorOff(card) {
  const track = card && card.parentElement;
  const f = track && track.firstElementChild;
  if (f && f.classList.contains('floor')) f.classList.remove('lit');
}
