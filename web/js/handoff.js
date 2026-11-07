// "Fortsätt på TV:n": hands what this phone is playing (or has paused) to a Neon Tokyo TV.
//
// It goes through the profile's Stremio account, so it works from anywhere, with no pairing:
// two hidden items in the account's library carry it (type "other", removed + temp, no progress,
// so Stremio's own apps never show them). See docs/handoff.md; the TV side is
// app/src/main/java/com/lumera/app/handoff/.
//  - neontokyo:tvs      which TVs are on (each TV writes itself every few minutes while on)
//  - neontokyo:handoff  the last thing sent; the TV that plays it writes its id back

import * as store from './store.js';
import * as sync from './sync.js';
import { esc, html, modal } from './ui.js';

export const MAILBOX_ID = 'neontokyo:handoff';
export const PRESENCE_ID = 'neontokyo:tvs';
const ON_WITHIN_MS = 10 * 60 * 1000;
const WAIT_MS = 25 * 1000;
const LAST_TV = 'nt.handoff.tv';

/** Whether this profile can send: it is signed in to Stremio (the TV must use the same account). */
export const available = (p = store.profile()) => sync.linked(p);

/** "iPhone", "iPad", "Android", "datorn": shown on the TV as "Fortsätter från …". */
export function deviceName(ua = (globalThis.navigator && navigator.userAgent) || '') {
  if (/iPhone/.test(ua)) return 'iPhone';
  if (/iPad/.test(ua) || (/Macintosh/.test(ua) && globalThis.document && 'ontouchend' in document)) return 'iPad';
  if (/Android/.test(ua)) return /Mobile/.test(ua) ? 'mobilen' : 'surfplattan';
  return 'datorn';
}

const iso = (ms) => new Date(ms).toISOString();

/** A library item Stremio's apps read without trouble and never show. */
export function hiddenItem(id, name, now = Date.now()) {
  return {
    _id: id, name, type: 'other', poster: '', posterShape: 'poster', removed: true, temp: true,
    _ctime: iso(now), _mtime: iso(now),
    state: { lastWatched: iso(now), timeWatched: 0, timeOffset: 0, overallTimeWatched: 0, timesWatched: 0, flaggedWatched: 0, duration: 0, video_id: id, watched: '', noNotif: false },
    behaviorHints: { defaultVideoId: null, featuredVideoId: null, hasScheduledVideos: false },
  };
}

function parse(text) { try { const o = JSON.parse(text); return o && typeof o === 'object' ? o : null; } catch { return null; } }

/** The TVs in the presence item that are on now, newest first. */
export function tvsOn(presenceName, now = Date.now()) {
  const o = parse(presenceName);
  const tvs = (o && o.tvs) || {};
  return Object.entries(tvs)
    .map(([id, t]) => ({ id, name: (t && t.name) || 'TV', seenAt: +(t && t.seenAt) || 0, on: !!(t && t.on) }))
    .filter((t) => t.on && now - t.seenAt < ON_WITHIN_MS)
    .sort((a, b) => b.seenAt - a.seenAt);
}

/** The message a TV reads (HandoffProtocol.parseMessage on the TV). */
export function message(item, { to = '*', profile = '', from = deviceName(), now = Date.now(), nonce } = {}) {
  return {
    v: 1, nonce: nonce || Math.random().toString(36).slice(2, 10) + now.toString(36), to, from, profile, sentAt: now,
    id: item.id, type: item.type === 'series' ? 'series' : 'movie', video: item.video || item.id,
    name: item.name || '', title: item.title || item.name || '',
    poster: item.poster || '', background: item.background || '', logo: item.logo || '',
    positionMs: Math.max(0, Math.round(item.positionMs || 0)), durationMs: Math.max(0, Math.round(item.durationMs || 0)),
  };
}

async function getItems(ids) {
  const p = store.profile();
  const items = await sync.stremioCall('datastoreGet', { authKey: p.stremio.authKey, collection: 'libraryItem', ids, all: false });
  return new Map((items || []).map((it) => [it._id, it]));
}

export async function listTvs() {
  const items = await getItems([PRESENCE_ID]);
  const presence = items.get(PRESENCE_ID);
  return tvsOn(presence && presence.name);
}

export async function send(item, to = '*') {
  const p = store.profile();
  const msg = message(item, { to, profile: p.name });
  await sync.stremioCall('datastorePut', { authKey: p.stremio.authKey, collection: 'libraryItem', changes: [hiddenItem(MAILBOX_ID, JSON.stringify(msg))] });
  return msg;
}

/** Resolves with the TV's name once a TV took [nonce]; null if none did in time or something newer was sent. */
export async function waitTaken(nonce, { timeoutMs = WAIT_MS, every = 1500, isCancelled = () => false } = {}) {
  const until = Date.now() + timeoutMs;
  while (Date.now() < until && !isCancelled()) {
    await new Promise((r) => setTimeout(r, every));
    try {
      const box = (await getItems([MAILBOX_ID])).get(MAILBOX_ID);
      const m = box && parse(box.name);
      if (!m || m.nonce !== nonce) return null;
      if (m.takenBy) return m.takenName || 'TV:n';
    } catch { /* a missed look; try again */ }
  }
  return null;
}

function rememberTv(id) { try { localStorage.setItem(LAST_TV, id); } catch { /* blocked */ } }
function lastTv() { try { return localStorage.getItem(LAST_TV); } catch { return null; } }

/** Which TV, when more than one is on: the one picked last time goes first. */
function pickTv(tvs) {
  const last = lastTv();
  const sorted = [...tvs].sort((a, b) => (b.id === last) - (a.id === last));
  return modal((close) => {
    const el = html(`<div class="panel handoff-panel">
      <h2>Vilken TV?</h2>
      <p class="muted">${tvs.length} TV-apparater med Neon Tokyo är på.</p>
      <div class="handoff-tvs">${sorted.map((t, i) => `<button class="btn ${i === 0 ? 'primary' : 'ghost'} big" data-tv="${esc(t.id)}" ${i === 0 ? 'data-autofocus' : ''}>${tvIcon}<span>${esc(t.name)}</span></button>`).join('')}</div>
      <div class="panel-actions"><button class="btn ghost" data-cancel>Avbryt</button></div>
    </div>`);
    el.addEventListener('click', (e) => {
      const b = e.target.closest('[data-tv]');
      if (b) close(tvs.find((t) => t.id === b.dataset.tv));
      else if (e.target.closest('[data-cancel]')) close(null);
    });
    return el;
  });
}

const tvIcon = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="12" rx="2"/><path d="M8 20h8"/></svg>';
export const TV_ICON = tvIcon;

/**
 * The whole "Fortsätt på TV:n" press: finds the TV, sends, and shows it being taken.
 * [item]: { id, type, video, name, title, poster, background, logo, positionMs, durationMs }.
 * Resolves true once a TV plays it. [onSent] runs as soon as it is on its way (the player pauses).
 */
export async function continueOnTv(item, { onSent } = {}) {
  if (!available()) {
    await modal((close) => {
      const el = html(`<div class="panel handoff-panel">
        <h2>Fortsätt på TV:n</h2>
        <p class="muted">Logga in ${esc((store.profile() || {}).name || 'profilen')} med Stremio i Inställningar, med samma konto som TV:n. Sedan skickar den här knappen det du tittar på till TV:n, på samma sekund.</p>
        <div class="panel-actions"><button class="btn ghost" data-cancel>Stäng</button><a class="btn primary" href="#/settings" data-autofocus>Till Inställningar</a></div>
      </div>`);
      el.addEventListener('click', (e) => { if (e.target.closest('[data-cancel], a')) close(); });
      return el;
    });
    return false;
  }

  let cancelled = false;
  let paint = () => {};
  let finish = () => {};
  const shown = modal((close) => {
    const el = html(`<div class="panel handoff-panel" aria-live="polite">
      <div class="handoff-art">${item.poster ? `<img src="${esc(item.poster)}" alt="" onerror="this.remove()">` : ''}<span class="handoff-beam"></span>${tvIcon}</div>
      <h2 class="handoff-title">Fortsätt på TV:n</h2>
      <p class="muted handoff-what">${esc(item.title || item.name || '')}</p>
      <p class="handoff-status"><span class="spinner"></span><span class="handoff-line">Letar efter TV:n…</span></p>
      <div class="panel-actions"><button class="btn ghost" data-cancel data-autofocus>Stäng</button><button class="btn primary" data-retry hidden>Försök igen</button></div>
    </div>`);
    paint = (line, state = 'busy') => {
      el.querySelector('.handoff-line').textContent = line;
      el.classList.toggle('done', state === 'done');
      el.classList.toggle('failed', state === 'failed');
      el.querySelector('.spinner').hidden = state !== 'busy';
      el.querySelector('[data-retry]').hidden = state !== 'failed';
    };
    finish = close;
    el.addEventListener('click', (e) => {
      if (e.target.closest('[data-cancel]')) { cancelled = true; close(false); }
      if (e.target.closest('[data-retry]')) close('retry');
    });
    return el;
  }, { cls: 'handoff' });

  const run = async () => {
    try {
      const tvs = await listTvs();
      let tv = null;
      if (tvs.length > 1) {
        tv = await pickTv(tvs);
        if (!tv) { finish(false); return false; }
        rememberTv(tv.id);
      } else if (tvs.length === 1) tv = tvs[0];
      if (cancelled) return false;
      paint(tv ? `Skickar till ${tv.name}…` : 'Skickar… Slå på TV:n så tar den emot det inom fem minuter.');
      const msg = await send(item, tv ? tv.id : '*');
      if (onSent) onSent();
      const taken = await waitTaken(msg.nonce, { timeoutMs: tv ? WAIT_MS : 5 * 60 * 1000, isCancelled: () => cancelled });
      if (cancelled) return false;
      if (taken) {
        paint(`Spelas på ${taken}`, 'done');
        setTimeout(() => finish(true), 1800);
        return true;
      }
      paint('TV:n svarade inte. Är den på, med Neon Tokyo inloggat på samma Stremio-konto?', 'failed');
      return false;
    } catch (e) {
      paint(e && e.message === 'Failed to fetch' ? 'Stremio gick inte att nå. Kolla nätet och försök igen.' : (e && e.message) || 'Det gick inte att skicka.', 'failed');
      return false;
    }
  };
  run();
  const result = await shown;
  if (result === 'retry') return continueOnTv(item, { onSent });
  return result === true;
}
