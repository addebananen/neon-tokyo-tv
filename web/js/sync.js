// Sign in with a Stremio account and keep a profile in step with it.
//
// Each profile can link its own Stremio account. Linked, it:
//  - brings in the account's addons (new ones are added; nothing is removed from Stremio),
//  - reads the account's library: watchlist (Stremio's Library) and how far each film or
//    episode got (Stremio's Continue Watching),
//  - writes back what is watched here, so Stremio, the TV and this browser show the same.
// Per film or series, the newest change wins, as in the shared core's sync rule.

import * as store from './store.js';
import * as core from './core.js';

const API = globalThis.NT_STREMIO_API || 'https://api.strem.io/api/'; // tests point this at a mock
const listeners = new Set();
let status = { state: 'idle', at: 0, error: '' };
let busy = null;
let pushTimer = 0;
let started = false;

function setStatus(patch) { status = { ...status, ...patch }; listeners.forEach((l) => l(status)); }
export const syncStatus = () => status;
export function onChange(fn) { listeners.add(fn); return () => listeners.delete(fn); }
export const linked = (p = store.profile()) => !!(p && p.stremio && p.stremio.authKey);

async function call(method, body) {
  const r = await fetch(API + method, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const j = await r.json().catch(() => ({}));
  if (j.error) {
    const msg = j.error.message || String(j.error);
    if (/session|auth/i.test(msg)) throw Object.assign(new Error('Signed out of Stremio, sign in again'), { auth: true });
    throw new Error(msg);
  }
  if (!r.ok) throw new Error('Stremio answered ' + r.status);
  return j.result;
}

/** Signs this profile in. The password goes only to Stremio and is never kept. */
export async function signIn(email, password) {
  const res = await call('login', { type: 'Login', email, password, facebook: false });
  if (!res || !res.authKey) throw new Error('Wrong email or password');
  const p = store.profile();
  store.editProfile(p.id, { stremio: { authKey: res.authKey, email: (res.user && res.user.email) || email, lastSync: 0 } });
  await syncNow({ full: true });
  start();
}

export function signOut() {
  const p = store.profile();
  if (!linked(p)) return;
  call('logout', { type: 'Logout', authKey: p.stremio.authKey }).catch(() => {});
  store.editProfile(p.id, { stremio: null });
  setStatus({ state: 'idle', error: '', at: 0 });
}

// ---------- Addons ----------

async function pullAddons(authKey) {
  const res = await call('addonCollectionGet', { type: 'AddonCollectionGet', authKey, update: true });
  const list = (res && res.addons) || [];
  let n = 0;
  store.update((s) => {
    for (const a of list) {
      if (!a.transportUrl || !a.manifest || a.manifest.id === 'org.stremio.local') continue;
      let base;
      try { base = core.addonBase(a.transportUrl); } catch { continue; }
      if (s.addons.some((x) => x.base === base)) continue;
      s.addons.push({ base, manifest: a.manifest });
      n++;
    }
    if (n) s.addonsUpdatedAt = Date.now();
  }, { fromSync: true });
  return n;
}

// ---------- Library ----------

const iso = (ms) => new Date(ms).toISOString();
const ms = (v) => (v ? Date.parse(v) || 0 : 0);

/** Stremio library items into this profile's history and watchlist. */
function absorb(items, pid) {
  store.update((s) => {
    const tomb = (s.tombstones = s.tombstones || { profiles: {}, watchlist: {} });
    tomb.watchlist = tomb.watchlist || {};
    for (const it of items) {
      if (!it || !it._id || it.type === 'other') continue;
      const mtime = ms(it._mtime);
      const kind = it.type === 'series' ? 'series' : it.type || 'movie';
      // Watchlist = Stremio's Library (not removed, not temporary)
      const wi = s.watchlist.findIndex((w) => w.profile_id === pid && w.id === it._id);
      const inLibrary = !it.removed && !it.temp;
      if (inLibrary && wi < 0 && mtime > (tomb.watchlist[pid + '|' + it._id] || 0)) {
        s.watchlist.push({ profile_id: pid, id: it._id, type: kind, name: it.name, poster: it.poster || null, added_at_ms: mtime });
      } else if (!inLibrary && wi >= 0 && mtime > (s.watchlist[wi].added_at_ms || 0)) {
        s.watchlist.splice(wi, 1);
      }
      // Progress = Stremio's state
      const st = it.state || {};
      const lastWatched = ms(st.lastWatched) || mtime;
      const videoId = st.video_id || (kind === 'series' ? null : it._id);
      if (!videoId) continue;
      const playbackId = kind === 'series' && !core.isEpisodeId(videoId) ? null : videoId;
      if (!playbackId) continue;
      const offset = Math.max(0, st.timeOffset || 0), duration = Math.max(0, st.duration || 0);
      const watchedFlag = !!st.flaggedWatched || (offset === 0 && (st.timesWatched || 0) > 0 && kind !== 'series');
      if (!offset && !watchedFlag) continue;
      const i = s.history.findIndex((e) => e.profile_id === pid && e.id === playbackId);
      const cur = i >= 0 ? s.history[i] : null;
      if (cur && cur.updated_at_ms >= lastWatched) continue;
      const entry = core.recordProgress(cur, {
        profileId: pid, id: playbackId, kind, title: it.name, poster: it.poster,
        positionMs: watchedFlag && !offset ? duration || 1 : offset, durationMs: duration || (watchedFlag ? 1 : 0),
      });
      entry.updated_at_ms = lastWatched;
      if (watchedFlag && !offset) entry.watched = true;
      if (i >= 0) s.history[i] = entry; else s.history.push(entry);
    }
  }, { fromSync: true });
}

/** This profile's changes since the last sync, as Stremio library items. */
function changes(items, pid, since) {
  const s = store.get();
  const byId = new Map(items.map((it) => [it._id, it]));
  const out = new Map();
  const now = Date.now();
  const base = (id, kind, name, poster) => {
    const old = out.get(id) || byId.get(id);
    return old ? JSON.parse(JSON.stringify(old)) : {
      _id: id, name, type: kind, poster: poster || '', posterShape: 'poster', removed: true, temp: true,
      _ctime: iso(now), _mtime: iso(now),
      state: { lastWatched: iso(now), timeWatched: 0, timeOffset: 0, overallTimeWatched: 0, timesWatched: 0, flaggedWatched: 0, duration: 0, video_id: id, watched: '', noNotif: false },
      behaviorHints: { defaultVideoId: null, featuredVideoId: null, hasScheduledVideos: false },
    };
  };
  // Newest first, so a series takes the episode watched last
  const history = s.history.filter((e) => e.profile_id === pid && !e.deleted && e.updated_at_ms > since)
    .sort((a, b) => a.updated_at_ms - b.updated_at_ms);
  for (const e of history) {
    const id = e.kind === 'series' ? core.canonicalSeriesId(e.id) : e.id;
    const remote = byId.get(id);
    if (remote && ms(remote.state && remote.state.lastWatched) > e.updated_at_ms) continue; // newer there
    const it = base(id, e.kind, e.title, e.poster);
    it.state = { ...it.state, lastWatched: iso(e.updated_at_ms), video_id: e.id, duration: e.duration_ms,
      timeOffset: e.watched ? 0 : e.position_ms, flaggedWatched: e.watched ? 1 : 0,
      timesWatched: (it.state.timesWatched || 0) + (e.watched ? 1 : 0) };
    if (e.kind === 'series' && core.isEpisodeId(e.id)) {
      const p = e.id.split(':');
      it.state.season = +p[p.length - 2]; it.state.episode = +p[p.length - 1];
    }
    it._mtime = iso(e.updated_at_ms);
    out.set(id, it);
  }
  for (const w of s.watchlist.filter((x) => x.profile_id === pid && x.added_at_ms > since)) {
    const it = base(w.id, w.type, w.name, w.poster);
    it.removed = false; it.temp = false; it._mtime = iso(w.added_at_ms);
    out.set(w.id, it);
  }
  for (const [key, at] of Object.entries((s.tombstones && s.tombstones.watchlist) || {})) {
    const [p, id] = [key.slice(0, key.indexOf('|')), key.slice(key.indexOf('|') + 1)];
    if (p !== pid || at <= since || !byId.has(id)) continue;
    const it = base(id);
    it.removed = true; it.temp = true; it._mtime = iso(at);
    out.set(id, it);
  }
  return [...out.values()];
}

/** Pull, merge, push. `full` also re-reads the addon list. */
export function syncNow({ full = false } = {}) {
  const p = store.profile();
  if (!linked(p)) return Promise.resolve();
  if (busy) return busy;
  busy = (async () => {
    setStatus({ state: 'syncing', error: '' });
    const { authKey, lastSync = 0 } = p.stremio;
    const startedAt = Date.now();
    try {
      if (full || !lastSync) await pullAddons(authKey);
      const items = (await call('datastoreGet', { authKey, collection: 'libraryItem', ids: [], all: true })) || [];
      const out = changes(items, p.id, lastSync);
      absorb(items, p.id);
      if (out.length) await call('datastorePut', { authKey, collection: 'libraryItem', changes: out });
      const fresh = store.profile();
      if (fresh && fresh.id === p.id && fresh.stremio) {
        store.update((s) => { const q = s.profiles.find((x) => x.id === p.id); q.stremio = { ...q.stremio, lastSync: startedAt }; }, { fromSync: true });
      }
      setStatus({ state: 'idle', at: Date.now(), error: '' });
    } catch (e) {
      setStatus({ state: 'error', error: e.message === 'Failed to fetch' ? 'Stremio could not be reached' : e.message });
      if (e.auth) store.editProfile(p.id, { stremio: { ...p.stremio, authKey: null } });
    } finally {
      busy = null;
    }
  })();
  return busy;
}

/** Syncs now, a few seconds after each change, and when the app comes back to the front. */
export function start() {
  if (started) { syncNow(); return; }
  started = true;
  syncNow();
  store.subscribe((s, meta) => {
    if (meta && meta.fromSync) return;
    clearTimeout(pushTimer);
    pushTimer = setTimeout(() => { pushTimer = 0; syncNow(); }, 5000);
  });
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden' && pushTimer) { clearTimeout(pushTimer); pushTimer = 0; syncNow(); }
    if (document.visibilityState === 'visible' && Date.now() - status.at > 60000) syncNow();
  });
}
