// Small shared helpers: HTML escaping, images (with the metahub fallback), toasts, modals, sounds.

import * as nav from './nav.js';

export const $ = (sel, root = document) => root.querySelector(sel);
export const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

export function esc(v) {
  return String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

export function html(str) {
  const t = document.createElement('template');
  t.innerHTML = str.trim();
  return t.content.firstElementChild;
}

// Cinemeta's images.metahub.space is blocked on some networks (it is on Adrian's): probe it once
// and, if it does not answer, load those images through wsrv.nl as the TV app does.
// The last answer is remembered, so the app never waits for the probe after the first run.
let metahubOk = (() => { try { return localStorage.getItem('nt.metahub') !== '0'; } catch { return true; } })();
export function probeMetahub() {
  return new Promise((resolve) => {
    const img = new Image();
    const done = (ok) => { metahubOk = ok; try { localStorage.setItem('nt.metahub', ok ? '1' : '0'); } catch { /* ignore */ } resolve(ok); };
    const timer = setTimeout(() => done(false), 4000);
    img.onload = () => { clearTimeout(timer); done(true); };
    img.onerror = () => { clearTimeout(timer); done(false); };
    img.src = 'https://images.metahub.space/poster/small/tt0111161/img';
  });
}

export function imgUrl(url, width) {
  if (!url) return '';
  let u = String(url);
  if (u.startsWith('//')) u = 'https:' + u;
  if (u.startsWith('http://') && location.protocol === 'https:') u = 'https://' + u.slice(7);
  if (!metahubOk && u.includes('images.metahub.space')) {
    return `https://wsrv.nl/?url=${encodeURIComponent(u)}${width ? `&w=${width}` : ''}&output=webp`;
  }
  return u;
}

/** <img> that fades in and falls back to the title on a neon gradient. */
export function poster(url, title, width = 300, cls = '') {
  const src = imgUrl(url, width);
  return `<div class="poster ${cls}">${src ? `<img src="${esc(src)}" alt="" loading="lazy" decoding="async" onload="this.classList.add('in')" onerror="this.remove()">` : ''}<span class="poster-fallback">${esc(title)}</span></div>`;
}

let toastTimer = 0;
export function toast(msg, kind = '') {
  let el = $('#toast');
  if (!el) { el = html('<div id="toast" role="status" aria-live="polite"></div>'); document.body.append(el); }
  el.textContent = msg;
  el.className = 'show ' + kind;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.className = ''; }, 3200);
}

/** Opens a modal; resolves when it closes. `build(close)` returns the panel's element. */
export function modal(build, { cls = '' } = {}) {
  return new Promise((resolve) => {
    const prevFocus = document.activeElement;
    const wrap = html(`<div class="modal ${cls}" role="dialog" aria-modal="true"><div class="modal-scrim" data-close></div></div>`);
    let closed = false;
    const close = (value) => {
      if (closed) return;
      closed = true;
      wrap.classList.add('out');
      nav.setScope(null);
      modalStack.pop();
      setTimeout(() => wrap.remove(), 220);
      if (prevFocus && document.contains(prevFocus)) prevFocus.focus({ preventScroll: true });
      resolve(value);
    };
    const panel = build(close);
    wrap.append(panel);
    wrap.querySelector('[data-close]').addEventListener('click', () => close());
    document.body.append(wrap);
    modalStack.push(close);
    nav.setScope(wrap);
    requestAnimationFrame(() => { wrap.classList.add('in'); nav.focusFirst(panel); });
  });
}

export const modalStack = [];

export function closeTopModal() {
  const close = modalStack[modalStack.length - 1];
  if (close) { close(); return true; }
  return false;
}

// Synth blips like the TV app's (Settings › Sounds); off by default.
let audio = null;
export function blip(kind = 'move', enabled = false) {
  if (!enabled) return;
  try {
    audio = audio || new AudioContext();
    const o = audio.createOscillator(), g = audio.createGain();
    const f = { move: 880, select: 1320, back: 520 }[kind] || 880;
    o.type = 'triangle';
    o.frequency.setValueAtTime(f, audio.currentTime);
    o.frequency.exponentialRampToValueAtTime(f * 1.5, audio.currentTime + 0.05);
    g.gain.setValueAtTime(0.045, audio.currentTime);
    g.gain.exponentialRampToValueAtTime(0.0001, audio.currentTime + 0.09);
    o.connect(g).connect(audio.destination);
    o.start(); o.stop(audio.currentTime + 0.1);
  } catch { /* no audio */ }
}

export function fmtTime(sec) {
  if (!isFinite(sec) || sec < 0) sec = 0;
  sec = Math.floor(sec);
  const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
  return (h ? h + ':' + String(m).padStart(2, '0') : m) + ':' + String(s).padStart(2, '0');
}

export const isApple = /iPhone|iPad|iPod|Macintosh/.test(navigator.userAgent) && 'ontouchend' in document || /iPhone|iPad|iPod/.test(navigator.userAgent);
export const isStandalone = matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
