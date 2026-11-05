// Remote and keyboard navigation: arrow keys move focus to the nearest control in that
// direction, as a D-pad does on the TV. Mouse and touch work as normal; the neon focus glow
// shows only while someone is using keys.

const FOCUSABLE = 'a[href],button:not([disabled]),input,select,textarea,[tabindex]:not([tabindex="-1"])';
const DIRS = { ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down' };
let scope = null; // a modal or the player traps focus inside itself

export function setScope(el) { scope = el; }

function visible(el) {
  if (el.closest('[inert],[hidden]')) return false;
  const r = el.getBoundingClientRect();
  if (r.width < 2 || r.height < 2) return false;
  const cs = getComputedStyle(el);
  return cs.visibility !== 'hidden' && cs.opacity !== '0';
}

function candidates() {
  const root = scope && document.contains(scope) ? scope : document.getElementById('app');
  return [...root.querySelectorAll(FOCUSABLE)].filter(visible);
}

/** The control nearest to `from` in direction `dir`, favouring ones in line with it.
 *  Scores every control by its box first (cheap) and checks visibility only for the best few,
 *  so a key press stays quick on a home page with hundreds of cards. */
function nearest(from, dir) {
  const root = scope && document.contains(scope) ? scope : document.getElementById('app');
  const a = from.getBoundingClientRect();
  const ax = a.left + a.width / 2, ay = a.top + a.height / 2;
  // Moving sideways inside a row: only that row's cards are in the running
  const track = (dir === 'left' || dir === 'right') && from.closest('.row-track');
  const pool = track ? track.querySelectorAll(FOCUSABLE) : root.querySelectorAll(FOCUSABLE);
  const scored = [];
  for (const el of pool) {
    if (el === from || from.contains(el)) continue;
    const b = el.getBoundingClientRect();
    if (b.width < 2 || b.height < 2) continue;
    // Rows scroll sideways: moving up/down from a card should land on the card in view,
    // so compare against the viewport-clamped centre.
    const bx = Math.min(Math.max(b.left + b.width / 2, 0), innerWidth), by = b.top + b.height / 2;
    let main, cross;
    if (dir === 'left') { main = a.left - b.right; cross = Math.abs(by - ay); if (b.left + b.width / 2 >= ax - 1) continue; }
    else if (dir === 'right') { main = b.left - a.right; cross = Math.abs(by - ay); if (b.left + b.width / 2 <= ax + 1) continue; }
    else if (dir === 'up') { main = a.top - b.bottom; cross = Math.abs(bx - ax); if (b.top + b.height / 2 >= ay - 1) continue; }
    else { main = b.top - a.bottom; cross = Math.abs(bx - ax); if (b.top + b.height / 2 <= ay + 1) continue; }
    // Overlapping lines count as zero distance along the move
    const overlap = dir === 'left' || dir === 'right'
      ? Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top)
      : Math.min(a.right, b.right) - Math.max(a.left, b.left);
    scored.push([Math.max(main, 0) + cross * (overlap > 0 ? 0.3 : 2.2), el]);
  }
  scored.sort((x, y) => x[0] - y[0]);
  for (const [, el] of scored) if (visible(el)) return el;
  if (track) return nearest(from.closest('.row') || track, dir) || null;
  return null;
}

// ---------- Neon Home's slide ----------
// Rows slide so the focused card sits at the same spot (the pivot, a gutter in from the left),
// and the page slides so the focused row sits at the same height. Both run on Neon Home's
// slide spring (response 0.39 s, damping 0.9): a new key press while it moves takes over the
// motion, keeping its speed, so holding a key glides instead of stuttering.

const K = (2 * Math.PI / 0.39) ** 2, C = 4 * Math.PI * 0.9 / 0.39;
const springs = new Map(); // element (or window) -> { x, v, target, raf }

function readPos(el) { return el === window ? scrollY : el.scrollLeft; }
function writePos(el, x) { if (el === window) scrollTo(0, x); else el.scrollLeft = x; }
function maxPos(el) {
  return el === window ? document.documentElement.scrollHeight - innerHeight : el.scrollWidth - el.clientWidth;
}

export function glideTo(el, target) {
  target = Math.max(0, Math.min(maxPos(el), Math.round(target)));
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) { writePos(el, target); return; }
  let s = springs.get(el);
  if (!s) {
    const x = readPos(el);
    if (Math.abs(x - target) < 1) return;
    s = { x, v: 0, target, raf: 0, last: performance.now() };
    springs.set(el, s);
  } else if (Math.abs(readPos(el) - s.x) > 2) {
    s.x = readPos(el); // someone scrolled by hand meanwhile: start from where it is
  }
  s.target = target;
  if (s.raf) return;
  s.last = performance.now();
  const step = (now) => {
    let dt = Math.min(0.05, (now - s.last) / 1000);
    s.last = now;
    while (dt > 0) { // small steps keep the spring stable on slow frames
      const h = Math.min(dt, 1 / 240);
      s.v += (K * (s.target - s.x) - C * s.v) * h;
      s.x += s.v * h;
      dt -= h;
    }
    if (Math.abs(s.target - s.x) < 0.5 && Math.abs(s.v) < 8) {
      writePos(el, s.target);
      springs.delete(el);
      return;
    }
    writePos(el, s.x);
    s.raf = requestAnimationFrame(step);
  };
  s.raf = requestAnimationFrame(step);
}

/** Stops a glide (a wheel, touch or drag takes over). */
function stopGlide(el) {
  const s = springs.get(el);
  if (s) { cancelAnimationFrame(s.raf); springs.delete(el); }
}

export function focusEl(el, { scroll = true } = {}) {
  if (!el) return;
  el.focus({ preventScroll: true });
  if (!scroll) return;
  const row = el.closest('.row-track');
  if (row) {
    const pad = parseFloat(getComputedStyle(row).scrollPaddingLeft) || 48;
    const keys = document.documentElement.classList.contains('kbd');
    const r = el.getBoundingClientRect(), t = row.getBoundingClientRect();
    // With keys: the pivot. With mouse or touch: only bring it into view.
    if (keys || r.left < t.left + pad || r.right > t.right - pad) glideTo(row, row.scrollLeft + (r.left - t.left) - pad);
  }
  const section = el.closest('[data-nav-section]') || el;
  const sr = section.getBoundingClientRect();
  const top = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--nav-h')) || 80;
  if (section.dataset.navSection === 'hero') { glideTo(window, 0); return; }
  if (section.classList.contains('row') && document.documentElement.classList.contains('kbd')) {
    // The focused row at a steady height, a little above the middle of the screen
    const pivot = Math.max(top + 12, Math.min(innerHeight * 0.4, innerHeight - sr.height - 24));
    glideTo(window, scrollY + sr.top - pivot);
    return;
  }
  // Elsewhere: only when it is (or will be, once the page settles) out of view
  const sw = springs.get(window);
  const offset = sw ? sw.target - scrollY : 0;
  const at = sr.top - offset, bottom = sr.bottom - offset;
  if (at < top + 8 || bottom > innerHeight - 16) glideTo(window, scrollY + sr.top - top - 24);
}

export function focusFirst(root = document) {
  const el = root.querySelector('[data-autofocus]') || [...root.querySelectorAll(FOCUSABLE)].find(visible);
  if (el) focusEl(el, { scroll: false });
}

export function install({ onBack }) {
  const html = document.documentElement;
  addEventListener('pointerdown', () => html.classList.remove('kbd'), { passive: true });
  addEventListener('wheel', (e) => { stopGlide(window); const t = e.target.closest && e.target.closest('.row-track'); if (t) stopGlide(t); }, { passive: true });
  addEventListener('touchstart', () => { for (const el of [...springs.keys()]) stopGlide(el); }, { passive: true });
  addEventListener('keydown', (e) => {
    const t = e.target;
    const typing = t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT');
    if (e.key === 'Escape' || e.key === 'GoBack' || e.key === 'BrowserBack' || (e.key === 'Backspace' && !typing)) {
      e.preventDefault();
      onBack();
      return;
    }
    const dir = DIRS[e.key];
    if (!dir) {
      if (e.key === 'Tab') html.classList.add('kbd');
      return;
    }
    if (e.defaultPrevented) return;
    if (typing && (dir === 'left' || dir === 'right') && t.type !== 'range') {
      const pos = t.selectionStart ?? 0;
      if ((dir === 'left' && pos > 0) || (dir === 'right' && pos < (t.value || '').length)) return;
    }
    html.classList.add('kbd');
    const from = document.activeElement && document.activeElement !== document.body ? document.activeElement : null;
    e.preventDefault();
    if (!from || !candidates().includes(from)) { focusFirst(scope || document); return; }
    const next = nearest(from, dir);
    if (next) focusEl(next);
  });
}
