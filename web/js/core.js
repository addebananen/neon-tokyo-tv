// Neon Tokyo Web: the Stremio addon protocol and the watch rules.
//
// These rules mirror the shared Rust core (native/neon-core: addon.rs, stream.rs, watch.rs,
// starter.rs) rule for rule, so the web app ranks sources and builds "Continue watching" the
// same way as the TV, Apple and Windows apps. Stored entries use the core's JSON field names.

export const CINEMETA_URL = 'https://v3-cinemeta.strem.io';
const STREAMING_HOST = 'https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club';
const ANIME_URL =
  'https://1fe84bc728af-stremio-anime-catalogs.baby-beamup.club/' +
  '%7B%22cinemeta%22%3A%22on%22%2C%22myanimelist_top-all-time%22%3A%22on%22%2C' +
  '%22myanimelist_top-airing%22%3A%22on%22%2C%22anilist_trending-now%22%3A%22on%22%2C' +
  '%22anilist_top-anime%22%3A%22on%22%2C%22kitsu_most-popular%22%3A%22on%22%7D';

/** Streaming Catalogs provider codes sold in Sweden, in card order. */
export const SERVICES = [
  ['nfx', 'Netflix'], ['hbm', 'HBO Max'], ['dnp', 'Disney+'], ['amp', 'Prime Video'],
  ['atp', 'Apple TV+'], ['sst', 'SkyShowtime'], ['pmp', 'Paramount+'], ['cru', 'Crunchyroll'],
];

function streamingUrl() {
  const providers = SERVICES.map((s) => s[0]).join(',');
  const config = [providers, '', 'SE', '1792700000000', '0', '1', 'SE'].join(':');
  return `${STREAMING_HOST}/${encodeURIComponent(btoa(config))}`;
}

/** The starter addons, same three as the Android app: all metadata-only. */
export function starterAddonUrls() {
  return [CINEMETA_URL, streamingUrl(), ANIME_URL];
}

/** Turns whatever someone pastes (stremio://, …/manifest.json, a bare base) into a base URL. */
export function addonBase(input) {
  let u = String(input || '').trim();
  if (!u) throw new Error('Tom adress');
  if (u.startsWith('stremio://')) u = 'https://' + u.slice('stremio://'.length);
  if (!/^https?:\/\//i.test(u)) u = 'https://' + u;
  u = u.replace(/\/manifest\.json(\?.*)?$/i, '').replace(/\/+$/, '');
  new URL(u); // throws on nonsense
  return u;
}

const TIMEOUT_MS = 15000;
const memo = new Map();

/** GET JSON with a timeout; identical requests in flight share one fetch, answers cache 10 min. */
export async function getJson(url, { cacheMs = 600000 } = {}) {
  const hit = memo.get(url);
  if (hit && (hit.pending || Date.now() - hit.at < cacheMs)) return hit.promise;
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
  const promise = fetch(url, { signal: ctrl.signal })
    .then((r) => {
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      return r.json();
    })
    .finally(() => clearTimeout(timer));
  const entry = { promise, pending: true, at: Date.now() };
  memo.set(url, entry);
  promise.then(
    () => { entry.pending = false; entry.at = Date.now(); },
    () => memo.delete(url),
  );
  return promise;
}

export async function fetchManifest(base) {
  const m = await getJson(`${base}/manifest.json`, { cacheMs: 3600000 });
  if (!m || !m.id || !m.name) throw new Error('Inte ett Stremio-tillägg');
  return m;
}

function resourceOf(manifest, name) {
  for (const r of manifest.resources || []) {
    if (r === name) return { name, types: manifest.types || [], idPrefixes: manifest.idPrefixes };
    if (r && r.name === name) {
      return { name, types: r.types || manifest.types || [], idPrefixes: r.idPrefixes || manifest.idPrefixes };
    }
  }
  return null;
}

/** True when this addon answers `resource` for this type and id. */
export function serves(manifest, resource, type, id) {
  const r = resourceOf(manifest, resource);
  if (!r) return false;
  if (type && r.types.length && !r.types.includes(type)) return false;
  if (id && Array.isArray(r.idPrefixes) && r.idPrefixes.length) {
    return r.idPrefixes.some((p) => id.startsWith(p));
  }
  return true;
}

function extras(catalog) {
  if (Array.isArray(catalog.extra)) return catalog.extra;
  const req = catalog.extraRequired || [];
  return (catalog.extraSupported || []).map((name) => ({ name, isRequired: req.includes(name) }));
}

/** Catalogs a home row can show: no required extra except an optional genre. */
export function isBrowsable(catalog) {
  return !extras(catalog).some((e) => e.isRequired);
}

export function supportsSearch(catalog) {
  return extras(catalog).some((e) => e.name === 'search');
}

export function genreOptions(catalog) {
  const g = extras(catalog).find((e) => e.name === 'genre');
  return (g && g.options) || [];
}

export function catalogUrl(base, type, id, extra = {}) {
  const parts = Object.entries(extra)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(v)}`);
  const tail = parts.length ? `/${parts.join('&')}` : '';
  return `${base}/catalog/${encodeURIComponent(type)}/${encodeURIComponent(id)}${tail}.json`;
}

export async function fetchCatalog(base, type, id, extra) {
  const json = await getJson(catalogUrl(base, type, id, extra));
  return (json.metas || []).filter((m) => m && m.id && m.name);
}

export async function fetchMeta(base, type, id) {
  const json = await getJson(`${base}/meta/${encodeURIComponent(type)}/${encodeURIComponent(id)}.json`);
  if (!json || !json.meta) throw new Error('Ingen info');
  return json.meta;
}

export async function fetchStreams(base, type, id) {
  const json = await getJson(`${base}/stream/${encodeURIComponent(type)}/${encodeURIComponent(id)}.json`, { cacheMs: 120000 });
  return Array.isArray(json.streams) ? json.streams : [];
}

export async function fetchSubtitles(base, type, id) {
  const json = await getJson(`${base}/subtitles/${encodeURIComponent(type)}/${encodeURIComponent(id)}.json`);
  return Array.isArray(json.subtitles) ? json.subtitles : [];
}

// ---------- Streams (stream.rs) ----------

export const Quality = { Unknown: 0, Sd: 1, Hd720: 2, FullHd1080: 3, Uhd4k: 4 };
const QUALITY_LABEL = ['', 'SD', '720p', '1080p', '4K'];

function detectQuality(s) {
  const l = s.toLowerCase();
  if (l.includes('2160p') || l.includes('4k') || l.includes('uhd')) return Quality.Uhd4k;
  if (l.includes('1080p') || l.includes('1080i') || l.includes('fhd')) return Quality.FullHd1080;
  if (l.includes('720p')) return Quality.Hd720;
  if (['480p', '576p', '360p', 'sd '].some((x) => l.includes(x))) return Quality.Sd;
  return Quality.Unknown;
}

function detectTags(s) {
  const l = s.toLowerCase();
  const words = l.split(/[^a-z0-9+]/);
  const has = (w) => words.includes(w);
  const tags = [];
  if (has('dv') || l.includes('dolby vision') || l.includes('dovi')) tags.push('DV');
  if (has('hdr') || has('hdr10') || has('hdr10+')) tags.push('HDR');
  if (has('hevc') || has('x265') || has('h265')) tags.push('HEVC');
  if (has('atmos')) tags.push('Atmos');
  if (has('remux')) tags.push('Remux');
  return tags;
}

function detectSize(s) {
  const tokens = s.split(/\s+/).filter(Boolean);
  for (let i = 0; i < tokens.length; i++) {
    const t = tokens[i];
    const digits = (t.match(/^[0-9.,]*/) || [''])[0];
    const rest = t.slice(digits.length);
    let unit = rest || tokens[i + 1] || '';
    unit = unit.replace(/[^A-Za-z]+$/, '');
    const u = unit === 'GB' || unit === 'GiB' ? 'GB' : unit === 'MB' || unit === 'MiB' ? 'MB' : null;
    if (u && /\d/.test(digits)) return `${digits.replace(/,/g, '.')} ${u}`;
  }
  return null;
}

function containerOf(s) {
  const l = s.toLowerCase();
  const path = l.split(/[?#]/)[0];
  if (path.endsWith('.m3u8') || l.includes('m3u8')) return 'hls';
  if (path.endsWith('.mkv')) return 'mkv';
  if (path.endsWith('.mp4') || path.endsWith('.m4v') || path.endsWith('.mov')) return 'mp4';
  if (path.endsWith('.webm')) return 'webm';
  if (path.endsWith('.avi')) return 'avi';
  return null;
}

function detectContainer(url, filename, text) {
  return (filename && containerOf(filename)) || (url && containerOf(url)) ||
    text.split(/\s+/).map(containerOf).find(Boolean) || 'unknown';
}

/** Which containers this browser's <video> (plus hls.js) can open. */
export function browserCanPlay(container) {
  const v = document.createElement('video');
  switch (container) {
    case 'mp4': case 'unknown': return true;
    case 'hls': return !!v.canPlayType('application/vnd.apple.mpegurl') || 'MediaSource' in window || 'ManagedMediaSource' in window;
    case 'webm': return !!v.canPlayType('video/webm');
    // Chromium opens Matroska with web codecs inside (H.264/VP9/AV1 + AAC/Opus); Safari never does
    case 'mkv': return /Chrome|Edg|CrKey/.test(navigator.userAgent) || !!v.canPlayType('video/x-matroska');
    default: return false;
  }
}

/** One source, labelled as the core does. Null when it has no address at all. */
export function parseStream(addonName, s) {
  const url = s.url || null;
  const infoHash = s.infoHash || null;
  const ytId = s.ytId || null;
  const externalUrl = s.externalUrl || null;
  if (!url && !infoHash && !ytId && !externalUrl) return null;
  const name = s.name || addonName;
  const title = s.title || s.description || '';
  const hints = s.behaviorHints || {};
  const filename = hints.filename || '';
  const haystack = `${name}\n${title}\n${filename}`;
  const quality = detectQuality(haystack);
  const isTorrent = !!infoHash && !url;
  const container = detectContainer(url, filename, haystack);
  const needsHeaders = !!(hints.proxyHeaders && hints.proxyHeaders.request && Object.keys(hints.proxyHeaders.request).length);
  return {
    addonName, name, title, url, infoHash, ytId, externalUrl,
    fileIdx: s.fileIdx ?? null,
    quality, qualityLabel: QUALITY_LABEL[quality], tags: detectTags(haystack),
    sizeLabel: detectSize(haystack), container, isTorrent,
    bingeGroup: hints.bingeGroup || null,
    subtitles: Array.isArray(s.subtitles) ? s.subtitles : [],
    // A browser cannot set headers like User-Agent on a <video>, so those need the TV app
    webPlayable: !isTorrent && !!url && !needsHeaders && browserCanPlay(container),
    // MKV opens in Chromium only when its codecs are web codecs, so it ranks below sure things
    webSure: !isTorrent && !!url && !needsHeaders && ['mp4', 'hls', 'webm', 'unknown'].includes(container),
  };
}

/** Playable here first, then by quality, then the addon's own order (rank_streams). */
export function rankStreams(list) {
  return list
    .map((s, i) => [i, s])
    .sort(([ia, a], [ib, b]) => (b.webPlayable - a.webPlayable) || (b.webSure - a.webSure) || (b.quality - a.quality) || (ia - ib))
    .map(([, s]) => s);
}

// ---------- Watch rules (watch.rs) ----------

export const WATCHED_RATIO = 0.9;
export const WATCHED_REMAINING_MS = 30000;

export function episodePlaybackId(seriesId, video) {
  return `${seriesId}:${video.season ?? 0}:${video.episode ?? video.number ?? 0}`;
}

export function episodeStreamId(seriesId, video) {
  return video.id || episodePlaybackId(seriesId, video);
}

export function episodeTitle(video) {
  const s = Math.max(1, video.season ?? 1);
  const e = Math.max(1, video.episode ?? video.number ?? 1);
  return `S${s}:E${e}` + (video.title || video.name ? ` - ${video.title || video.name}` : '');
}

export function isEpisodeId(id) {
  const p = id.split(':');
  return p.length >= 3 && /^\d+$/.test(p[p.length - 1]) && /^\d+$/.test(p[p.length - 2]);
}

export function canonicalSeriesId(id) {
  return isEpisodeId(id) ? id.split(':').slice(0, -2).join(':') : id;
}

function regularEpisodes(videos) {
  return (videos || [])
    .filter((v) => (v.season ?? 0) > 0 && (v.episode ?? v.number ?? 0) > 0)
    .sort((a, b) => (a.season - b.season) || ((a.episode ?? a.number) - (b.episode ?? b.number)));
}

export function findNextEpisode(seriesId, playbackId, videos) {
  const list = regularEpisodes(videos);
  let i = list.findIndex((v) => episodePlaybackId(seriesId, v) === playbackId);
  if (i < 0 && isEpisodeId(playbackId)) {
    const p = playbackId.split(':');
    const s = +p[p.length - 2], e = +p[p.length - 1];
    i = list.findIndex((v) => v.season === s && (v.episode ?? v.number) === e);
  }
  return i >= 0 ? list[i + 1] || null : null;
}

export function recordProgress(existing, { profileId, id, kind, title, poster, background, logo, positionMs, durationMs }) {
  const duration = Math.max(0, Math.round(durationMs || 0));
  const position = Math.round(duration > 0 ? Math.min(positionMs, duration) : positionMs);
  const ratio = duration > 0 ? position / duration : 0;
  const completed = duration > 0 && (ratio >= WATCHED_RATIO || duration - position <= WATCHED_REMAINING_MS);
  return {
    profile_id: profileId, id, kind: kind || 'movie', title,
    poster: poster || existing?.poster || null,
    background: background || existing?.background || null,
    logo: logo || existing?.logo || null,
    position_ms: position, duration_ms: duration, watched: completed,
    updated_at_ms: Date.now(), deleted: false,
  };
}

export function resumePositionMs(entry) {
  return entry && !entry.watched && !entry.deleted && entry.position_ms >= 10000 ? entry.position_ms : 0;
}

/** "Continue watching", newest first: what was left part way, then next episodes that have aired. */
export function continueWatching(history, nextUp, profileId) {
  const today = new Date().toISOString().slice(0, 10);
  const mine = history.filter((e) => e.profile_id === profileId && !e.deleted)
    .sort((a, b) => b.updated_at_ms - a.updated_at_ms);
  const out = [];
  const seriesSeen = new Set();
  const moviesSeen = new Set();
  for (const e of mine.filter((x) => !x.watched)) {
    if (e.kind === 'series') {
      const sid = canonicalSeriesId(e.id);
      if (seriesSeen.has(sid)) continue;
      seriesSeen.add(sid);
      const p = e.id.split(':');
      const ep = isEpisodeId(e.id);
      out.push({
        id: sid, kind: 'series', name: e.title, poster: e.poster, background: e.background, logo: e.logo,
        progress: e.duration_ms ? e.position_ms / e.duration_ms : 0, hasNewEpisode: false,
        season: ep ? +p[p.length - 2] : null, episode: ep ? +p[p.length - 1] : null,
        resumeId: e.id, resumeMs: e.position_ms, at: e.updated_at_ms,
      });
    } else {
      if (moviesSeen.has(e.id)) continue;
      moviesSeen.add(e.id);
      out.push({
        id: e.id, kind: e.kind, name: e.title, poster: e.poster, background: e.background, logo: e.logo,
        progress: e.duration_ms ? e.position_ms / e.duration_ms : 0, hasNewEpisode: false,
        season: null, episode: null, resumeId: e.id, resumeMs: e.position_ms, at: e.updated_at_ms,
      });
    }
  }
  for (const n of nextUp.filter((x) => x.profile_id === profileId && !x.deleted)) {
    if (seriesSeen.has(n.series_id)) continue;
    const released = n.next_released ? n.next_released.slice(0, 10) : null;
    if (released && released > today) continue;
    if (n.is_complete && !released) continue;
    out.push({
      id: n.series_id, kind: 'series', name: n.title, poster: n.poster, background: n.background || null, logo: null,
      progress: 0, hasNewEpisode: n.is_complete || n.is_new_episode,
      season: n.next_season, episode: n.next_episode,
      resumeId: `${n.series_id}:${n.next_season}:${n.next_episode}`, resumeMs: 0, at: n.updated_at_ms,
    });
  }
  return out.sort((a, b) => b.at - a.at);
}

export function nextUpAfter({ profileId, seriesId, title, poster, background, playbackId, videos }) {
  const next = findNextEpisode(seriesId, playbackId, videos);
  if (next) {
    return {
      profile_id: profileId, series_id: seriesId, title, poster, background,
      next_season: next.season, next_episode: next.episode ?? next.number,
      next_episode_title: next.title || next.name || null,
      next_released: next.released || null, is_complete: false, is_new_episode: false,
      updated_at_ms: Date.now(), deleted: false,
    };
  }
  return {
    profile_id: profileId, series_id: seriesId, title, poster, background,
    next_season: 0, next_episode: 0, next_episode_title: null, next_released: null,
    is_complete: true, is_new_episode: false, updated_at_ms: Date.now(), deleted: false,
  };
}
