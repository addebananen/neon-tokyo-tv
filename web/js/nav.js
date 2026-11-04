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

/** The control nearest to `from` in direction `dir`, favouring ones in line with it. */
function nearest(from, dir) {
  const a = from.getBoundingClientRect();
  const ax = a.left + a.width / 2, ay = a.top + a.height / 2;
  // Rows scroll sideways: moving up/down from a card should land on the card in view,
  // so compare against the viewport-clamped centre.
  let best = null, bestScore = Infinity;
  for (const el of candidates()) {
    if (el === from || from.contains(el)) continue;
    const b = el.getBoundingClientRect();
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
    const score = Math.max(main, 0) + cross * (overlap > 0 ? 0.3 : 2.2);
    if (score < bestScore) { bestScore = score; best = el; }
  }
  return best;
}

export function focusEl(el, { scroll = true } = {}) {
  if (!el) return;
  el.focus({ preventScroll: true });
  if (!scroll) return;
  const row = el.closest('.row-track');
  if (row) {
    // Keep the focused card a little in from the left edge, like the TV rows
    const r = el.getBoundingClientRect(), t = row.getBoundingClientRect();
    const pad = parseFloat(getComputedStyle(row).scrollPaddingLeft) || 48;
    if (r.left < t.left + pad || r.right > t.right - pad) {
      row.scrollTo({ left: row.scrollLeft + (r.left - t.left) - pad, behavior: 'smooth' });
    }
  }
  const section = el.closest('[data-nav-section]') || el;
  const sr = section.getBoundingClientRect();
  const top = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--nav-h')) || 80;
  if (sr.top < top + 8 || sr.bottom > innerHeight - 16) {
    const target = scrollY + sr.top - top - (section.dataset.navSection === 'hero' ? 999 : 24);
    scrollTo({ top: Math.max(0, target), behavior: 'smooth' });
  }
}

export function focusFirst(root = document) {
  const el = root.querySelector('[data-autofocus]') || [...root.querySelectorAll(FOCUSABLE)].find(visible);
  if (el) focusEl(el, { scroll: false });
}

export function install({ onBack }) {
  const html = document.documentElement;
  addEventListener('pointerdown', () => html.classList.remove('kbd'), { passive: true });
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
