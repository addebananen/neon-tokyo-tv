// Trakt for a profile: sign in with a code on trakt.tv/activate, tell Trakt what was watched
// here (scrobble when the player closes, history for what was finished) and take in what was
// watched or paused in other apps (the TV, Omni, the phone). The rules are core.js's Trakt
// section, the same as the shared Rust core's.
//
// The sign-in stays in this browser (its own storage key per profile, never in a backup file).
// Trakt answers this page only from origins added to the Trakt app's "JavaScript (CORS)
// origins", so https://addebananen.github.io must be listed there.

import * as store from './store.js';
import * as core from './core.js';

const CLIENT_ID = '__TRAKT_ID__';
const API = globalThis.NT_TRAKT_API || 'https://api.trakt.tv/';
const listeners = new Set();
let status = { state: 'idle', at: 0, error: '', note: '' };
let code = null; // { userCode, url, expiresAt } while signing in
let polling = 0;
let busy = null;
let timer = 0;

export const available = () => !!CLIENT_ID && !CLIENT_ID.startsWith('__');
export const traktStatus = () => status;
export const signInCode = () => code;
export function onChange(fn) { listeners.add(fn); return () => listeners.delete(fn); }
function setStatus(patch) { status = { ...status, ...patch }; listeners.forEach((l) => l(status)); }

const key = (pid) => `nt.trakt.${pid}`;
function link(pid = store.get().activeProfile) {
  if (!pid) return null;
  try { return JSON.parse(localStorage.getItem(key(pid)) || 'null'); } catch { return null; }
}
function saveLink(pid, l) {
  try { if (l) localStorage.setItem(key(pid), JSON.stringify(l)); else localStorage.removeItem(key(pid)); } catch { /* blocked */ }
  listeners.forEach((fn) => fn(status));
}
export const linked = (pid) => !!link(pid);
export const username = () => (link() || {}).username || '';

async function call(path, body, token) {
  const headers = { 'Content-Type': 'application/json', 'trakt-api-version': '2', 'trakt-api-key': CLIENT_ID };
  if (token) headers.Authorization = `Bearer ${token}`;
  let r;
  try {
    r = await fetch(API + path, { method: body ? 'POST' : 'GET', headers, body: body ? JSON.stringify(body) : undefined });
  } catch {
    throw new Error('Trakt could not be reached from this page (it must list this address under its CORS origins)');
  }
  const json = await r.json().catch(() => null);
  return { status: r.status, ok: r.ok, json };
}

// ---------- Sign in ----------

/** Shows a code; once it is typed on trakt.tv/activate this profile is signed in. */
export async function signIn() {
  if (!available()) { setStatus({ error: 'This build has no Trakt key' }); return; }
  const pid = store.get().activeProfile;
  const myRun = ++polling;
  setStatus({ state: 'code', error: '' });
  try {
    const a = await call('oauth/device/code', { client_id: CLIENT_ID });
    if (!a.ok || !a.json || !a.json.device_code) throw new Error(`Trakt answered ${a.status}`);
    const d = a.json;
    code = { userCode: d.user_code, url: d.verification_url || 'https://trakt.tv/activate', expiresAt: Date.now() + (d.expires_in || 600) * 1000 };
    setStatus({});
    let wait = Math.max(1, d.interval || 5) * 1000;
    while (polling === myRun && Date.now() < code.expiresAt) {
      await new Promise((r) => setTimeout(r, wait));
      if (polling !== myRun) return;
      const p = await call('oauth/device/token', { code: d.device_code, client_id: CLIENT_ID });
      if (p.status === 200 || p.status === 201) {
        const t = p.json || {};
        const created = t.created_at ? t.created_at * 1000 : Date.now();
        const l = { access: t.access_token, refresh: t.refresh_token, expiresAt: created + (t.expires_in || 604800) * 1000, stamp: '', pushedAt: 0, username: '' };
        const me = await call('users/settings', null, l.access).catch(() => null);
        l.username = (me && me.json && me.json.user && (me.json.user.username || me.json.user.name)) || '';
        saveLink(pid, l);
        code = null;
        setStatus({ state: 'idle', error: '', note: l.username ? `Signed in as ${l.username}` : 'Signed in' });
        syncNow();
        start();
        return;
      }
      if (p.status === 400) continue; // not typed yet
      if (p.status === 429) { wait += 1000; continue; }
      code = null;
      const why = { 401: "Trakt wants this app's secret, which a web page cannot keep: connect Trakt in the TV or PC app", 403: "Trakt does not answer this page yet: add it to the Trakt app's CORS origins", 404: 'That code is not valid', 409: 'That code was already used', 410: 'The code ran out, get a new one', 418: 'Sign-in was declined on Trakt' }[p.status];
      setStatus({ state: 'idle', error: why || `Trakt answered ${p.status}` });
      return;
    }
    if (polling === myRun) { code = null; setStatus({ state: 'idle', error: 'The code ran out, try again' }); }
  } catch (e) {
    code = null;
    setStatus({ state: 'idle', error: e.message });
  }
}

export function cancel() { polling++; code = null; setStatus({ state: 'idle', error: '' }); }

export function signOut() {
  const pid = store.get().activeProfile;
  const l = link(pid);
  if (!l) return;
  call('oauth/revoke', { token: l.access, client_id: CLIENT_ID }, l.access).catch(() => {});
  saveLink(pid, null);
  setStatus({ state: 'idle', error: '', note: '' });
}

/** A token with more than a day left (Trakt tokens live a week; each refresh works once). */
async function fresh(pid) {
  let l = link(pid);
  if (!l) return null;
  if (l.refresh && l.expiresAt - Date.now() < 24 * 3600 * 1000) {
    const r = await call('oauth/token', { refresh_token: l.refresh, client_id: CLIENT_ID, grant_type: 'refresh_token' });
    if (r.status === 400 || r.status === 401) { saveLink(pid, null); throw new Error('Trakt sign-in ran out, sign in again'); }
    if (!r.ok || !r.json || !r.json.access_token) throw new Error(`Trakt answered ${r.status}`);
    const t = r.json;
    l = { ...l, access: t.access_token, refresh: t.refresh_token, expiresAt: (t.created_at ? t.created_at * 1000 : Date.now()) + (t.expires_in || 604800) * 1000 };
    saveLink(pid, l);
  }
  return l;
}

// ---------- Sync ----------

function applyHistory(history) {
  store.update((s) => { s.history = history; }, { fromSync: true });
}

/** Takes in what changed on Trakt (only when its activity stamp moved) and sends what was watched here. */
export function syncNow() {
  const pid = store.get().activeProfile;
  if (!available() || !link(pid)) return Promise.resolve();
  if (busy) return busy;
  busy = (async () => {
    setStatus({ state: 'syncing', error: '' });
    try {
      let l = await fresh(pid);
      const act = await call('sync/last_activities', null, l.access);
      if (act.status === 401) { saveLink(pid, null); throw new Error('Signed out of Trakt, sign in again'); }
      if (!act.ok) throw new Error(`Trakt answered ${act.status}`);
      const stamp = core.traktActivityStamp(act.json);
      let changed = 0;
      if (stamp !== l.stamp) {
        const [pb, mv, sh] = await Promise.all([
          call('sync/playback?limit=60', null, l.access),
          call('sync/watched/movies', null, l.access),
          call('sync/watched/shows', null, l.access),
        ]);
        let history = store.get().history;
        if (pb.ok && Array.isArray(pb.json)) { const m = core.traktMergePlayback(pb.json, history, pid); history = m.history; changed += m.changed; }
        if (mv.ok && sh.ok) { const m = core.traktMergeWatched(mv.json, sh.json, history, pid, 120); history = m.history; changed += m.changed; }
        if (changed) applyHistory(history);
      }
      const pending = core.traktPendingHistory(store.get().history, pid, l.pushedAt || 0);
      const startedAt = Date.now();
      let sent = 0;
      const body = core.traktHistoryBody(pending);
      if (body) { const r = await call('sync/history', body, l.access); if (r.ok) sent = pending.length; }
      l = { ...link(pid), stamp, pushedAt: !pending.length || sent ? startedAt : l.pushedAt };
      saveLink(pid, l);
      const clock = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      setStatus({ state: 'idle', at: Date.now(), error: '', note: changed || sent ? `Synced ${changed} in, ${sent} out · ${clock}` : `Synced and up to date · ${clock}` });
    } catch (e) {
      setStatus({ state: 'error', error: e.message });
    } finally {
      busy = null;
    }
  })();
  return busy;
}

/** When the player closes: where it stopped (Trakt counts a stop past 80 % as watched). */
export async function scrobbleStop(entry) {
  const pid = store.get().activeProfile;
  if (!available() || !entry || !link(pid) || !entry.duration_ms || entry.duration_ms === core.PERCENT_DURATION_MS) return;
  const pct = entry.watched ? 100 : entry.position_ms / entry.duration_ms * 100;
  if (pct < 1) return;
  const body = core.traktScrobbleBody(entry.id, entry.kind, pct);
  if (!body) return;
  try {
    const l = await fresh(pid);
    if (l) await call('scrobble/stop', body, l.access);
  } catch { /* the next sync sends finished ones as history */ }
}

/** Syncs now, every 5 minutes and on coming back to the page (the player asks once it closes). */
export function start() {
  if (timer || !available()) return;
  timer = setInterval(() => { if (!document.hidden) syncNow(); }, 300000);
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && Date.now() - status.at > 60000) syncNow();
  });
  syncNow();
}

/** After playback or a watched mark: tell Trakt soon, once. */
let soon = 0;
export function soonSync() {
  if (!available() || !link()) return;
  clearTimeout(soon);
  soon = setTimeout(syncNow, 5000);
}
