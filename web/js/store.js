// Everything Neon Tokyo Web remembers lives in this browser's localStorage: addons (shared by
// every profile, as on the TV), profiles, and per profile the watch history, next-up records,
// watchlist and look. Export/import moves it all between browsers as one JSON file.

import { starterAddonUrls, fetchManifest, addonBase } from './core.js';

const KEY = 'neontokyo.v1';
const listeners = new Set();

export const AVATAR_COLORS = ['#FF3D9A', '#3EF0FF', '#B46BFF', '#FFC46B', '#5CFFB0', '#FF7A59', '#8FDCFF', '#F6EAFF'];

function blank() {
  return {
    addons: [],          // [{ base, manifest }]
    profiles: [],        // [{ id, name, color, emoji, theme }]
    activeProfile: null,
    history: [],         // WatchEntry (core JSON names)
    nextUp: [],          // SeriesNextUp
    watchlist: [],       // { profile_id, id, type, name, poster, added_at_ms }
    settings: { intro: true, sounds: false, hideUnplayable: false, player: 'builtin' },
    seededAt: 0,
    addonsUpdatedAt: 0,
    tombstones: { profiles: {}, watchlist: {} }, // removals, kept so sync cannot bring them back
  };
}

let state = load();

function load() {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) return { ...blank(), ...JSON.parse(raw) };
  } catch { /* private mode or broken JSON: start fresh */ }
  return blank();
}

let saveTimer = 0;
function save() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    try { localStorage.setItem(KEY, JSON.stringify(state)); } catch { /* storage full or blocked */ }
  }, 120);
}

export function get() { return state; }

export function update(fn, meta) {
  fn(state);
  save();
  listeners.forEach((l) => l(state, meta));
}

/** Takes in what sync merged; this device's settings and active profile stay as they are. */
export function applySynced(merged) {
  update((s) => {
    for (const k of ['addons', 'addonsUpdatedAt', 'profiles', 'history', 'nextUp', 'watchlist', 'tombstones']) s[k] = merged[k];
    if (s.activeProfile && !s.profiles.some((p) => p.id === s.activeProfile)) s.activeProfile = null;
    if (s.addons.length) s.seededAt = s.seededAt || Date.now();
  }, { fromSync: true });
}

const tomb = (s) => (s.tombstones = s.tombstones || { profiles: {}, watchlist: {} });
const touchAddons = (s) => { s.addonsUpdatedAt = Date.now(); };

export function subscribe(fn) { listeners.add(fn); return () => listeners.delete(fn); }

// ---------- Profiles ----------

export function profile() {
  return state.profiles.find((p) => p.id === state.activeProfile) || null;
}

export function addProfile(name, color, emoji) {
  const p = { id: 'p' + Math.random().toString(36).slice(2, 9), name: name.trim() || 'Profile', color, emoji: emoji || '', theme: 'neon_tokyo', updated_at_ms: Date.now() };
  update((s) => { s.profiles.push(p); });
  return p;
}

export function editProfile(id, patch) {
  update((s) => { Object.assign(s.profiles.find((p) => p.id === id) || {}, patch, { updated_at_ms: Date.now() }); });
}

export function removeProfile(id) {
  update((s) => {
    s.profiles = s.profiles.filter((p) => p.id !== id);
    tomb(s).profiles[id] = Date.now();
    s.history = s.history.filter((e) => e.profile_id !== id);
    s.nextUp = s.nextUp.filter((e) => e.profile_id !== id);
    s.watchlist = s.watchlist.filter((e) => e.profile_id !== id);
    if (s.activeProfile === id) s.activeProfile = null;
  });
}

export function selectProfile(id) { update((s) => { s.activeProfile = id; }); }

// ---------- Addons ----------

/** Installs the starter pack once, the first time the app opens. */
export async function seedAddons() {
  if (state.seededAt) return;
  const results = await Promise.allSettled(starterAddonUrls().map(async (base) => ({ base, manifest: await fetchManifest(base) })));
  const ok = results.filter((r) => r.status === 'fulfilled').map((r) => r.value);
  if (!ok.length) return;
  update((s) => {
    for (const a of ok) if (!s.addons.some((x) => x.base === a.base)) s.addons.push(a);
    s.seededAt = Date.now();
    touchAddons(s);
  });
}

export async function installAddon(input) {
  const base = addonBase(input);
  const manifest = await fetchManifest(base);
  update((s) => {
    const i = s.addons.findIndex((a) => a.base === base);
    if (i >= 0) s.addons[i] = { base, manifest };
    else s.addons.push({ base, manifest });
    touchAddons(s);
  });
  return manifest;
}

export function removeAddon(base) { update((s) => { s.addons = s.addons.filter((a) => a.base !== base); touchAddons(s); }); }

export function moveAddon(base, dir) {
  update((s) => {
    const i = s.addons.findIndex((a) => a.base === base);
    const j = i + dir;
    if (i < 0 || j < 0 || j >= s.addons.length) return;
    [s.addons[i], s.addons[j]] = [s.addons[j], s.addons[i]];
    touchAddons(s);
  });
}

/** Refreshes manifests in the background so catalogs follow addon updates. */
export async function refreshManifests() {
  await Promise.allSettled(state.addons.map(async (a) => {
    const m = await fetchManifest(a.base);
    a.manifest = m;
  }));
  save();
}

// ---------- Watch state ----------

export function entry(id) {
  const pid = state.activeProfile;
  return state.history.find((e) => e.profile_id === pid && e.id === id && !e.deleted) || null;
}

export function putEntry(e) {
  update((s) => {
    const i = s.history.findIndex((x) => x.profile_id === e.profile_id && x.id === e.id);
    if (i >= 0) s.history[i] = e; else s.history.push(e);
    if (s.history.length > 1500) s.history.sort((a, b) => b.updated_at_ms - a.updated_at_ms).length = 1500;
  });
}

export function putNextUp(n) {
  update((s) => {
    const i = s.nextUp.findIndex((x) => x.profile_id === n.profile_id && x.series_id === n.series_id);
    if (i >= 0) s.nextUp[i] = n; else s.nextUp.push(n);
  });
}

/** Removes a title from "Continue watching" (kept as a deleted marker, like the core's sync rule). */
export function dismissContinue(id) {
  const pid = state.activeProfile;
  update((s) => {
    for (const e of s.history) {
      if (e.profile_id === pid && (e.id === id || e.id.startsWith(id + ':')) && !e.watched) {
        e.deleted = true; e.updated_at_ms = Date.now();
      }
    }
    for (const n of s.nextUp) if (n.profile_id === pid && n.series_id === id) { n.deleted = true; n.updated_at_ms = Date.now(); }
  });
}

export function setWatched(id, kind, title, watched) {
  const pid = state.activeProfile;
  const old = entry(id);
  putEntry({
    ...(old || { profile_id: pid, id, kind, title, poster: null, background: null, logo: null, duration_ms: 0 }),
    position_ms: watched ? (old?.duration_ms || 0) : 0, watched, deleted: false, updated_at_ms: Date.now(),
  });
}

export function inWatchlist(id) {
  return state.watchlist.some((w) => w.profile_id === state.activeProfile && w.id === id);
}

export function toggleWatchlist(meta) {
  const pid = state.activeProfile;
  update((s) => {
    const i = s.watchlist.findIndex((w) => w.profile_id === pid && w.id === meta.id);
    if (i >= 0) { s.watchlist.splice(i, 1); tomb(s).watchlist[pid + '|' + meta.id] = Date.now(); }
    else s.watchlist.unshift({ profile_id: pid, id: meta.id, type: meta.type, name: meta.name, poster: meta.poster || null, added_at_ms: Date.now() });
  });
}

// ---------- Backup ----------

export function exportJson() {
  // Stremio and Trakt sign-ins stay on this device: a backup file must not carry a session key
  // (Trakt's live under their own storage keys and are never in state)
  const profiles = state.profiles.map((p) => ({ ...p, stremio: null }));
  return JSON.stringify({ app: 'neon-tokyo-web', version: 1, exported_at_ms: Date.now(), ...state, profiles }, null, 1);
}

export function importJson(text) {
  const data = JSON.parse(text);
  if (!data || !Array.isArray(data.profiles) || !Array.isArray(data.addons)) throw new Error('Inte en Neon Tokyo-backup');
  update((s) => {
    Object.assign(s, blank(), data, { seededAt: data.seededAt || Date.now() });
    delete s.app; delete s.version; delete s.exported_at_ms;
  });
}

export function addonBaseFor(input) { return addonBase(input); }
