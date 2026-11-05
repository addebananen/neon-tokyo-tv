// The same cases as native/neon-core's Rust tests, so the web rules stay in step with the core.
// Run: node web/test/core.test.mjs
import assert from 'node:assert/strict';

globalThis.document = { createElement: () => ({ canPlayType: () => '' }) };
Object.defineProperty(globalThis, 'navigator', { value: { userAgent: 'Mozilla/5.0 (X11; Linux x86_64) Chrome/130.0' }, configurable: true });
globalThis.window = { MediaSource: function () {} };
globalThis.btoa = (s) => Buffer.from(s, 'binary').toString('base64');

const core = await import('../js/core.js');

const STREAMS = [
  { name: 'Torrentio\n4k', title: 'Dune.Part.Two.2024.2160p.UHD.BluRay.REMUX.DV.HDR.HEVC.Atmos.mkv\n👤 120 💾 61.2 GB', infoHash: 'abc', fileIdx: 0 },
  { name: 'RD+ 1080p', title: 'Dune Part Two 1080p WEB-DL\n💾 6,4 GB', url: 'https://debrid.example/dl/dune.mp4?token=1' },
  { name: 'RD+ 4K', title: 'Dune.Part.Two.2160p.mkv', url: 'https://debrid.example/dl/123', behaviorHints: { filename: 'Dune.Part.Two.2160p.DV.mkv', bingeGroup: 'rd-4k' } },
  { name: 'Live', title: 'HLS', url: 'https://iptv.example/live/1.m3u8', behaviorHints: { proxyHeaders: { request: { 'User-Agent': 'VLC' } } } },
  { title: 'no address at all' },
];

const s = STREAMS.map((x) => core.parseStream('Torrentio', x)).filter(Boolean);
assert.equal(s.length, 4);
assert.ok(s[0].isTorrent);
assert.equal(s[0].quality, core.Quality.Uhd4k);
assert.deepEqual(s[0].tags, ['DV', 'HDR', 'HEVC', 'Atmos', 'Remux']);
assert.equal(s[0].sizeLabel, '61.2 GB');
assert.equal(s[0].container, 'mkv');
assert.equal(s[1].container, 'mp4');
assert.equal(s[1].sizeLabel, '6.4 GB');
assert.equal(s[1].qualityLabel, '1080p');
assert.equal(s[2].container, 'mkv');
assert.equal(s[2].bingeGroup, 'rd-4k');
assert.equal(s[3].container, 'hls');
assert.equal(s[3].webPlayable, false, 'a browser cannot send the User-Agent header');

// Chrome: sure things first (MP4), then MKV it may open, then the rest
assert.deepEqual(core.rankStreams(s).map((x) => x.name), ['RD+ 1080p', 'RD+ 4K', 'Torrentio\n4k', 'Live']);

assert.equal(core.starterAddonUrls()[1],
  'https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/bmZ4LGhibSxkbnAsYW1wLGF0cCxzc3QscG1wLGNydTo6U0U6MTc5MjcwMDAwMDAwMDowOjE6U0U%3D');

assert.equal(core.addonBase('stremio://x.example/abc/manifest.json'), 'https://x.example/abc');

// Watched rule and Continue watching
const e = core.recordProgress(null, { profileId: 'p', id: 'tt1:1:2', kind: 'series', title: 'Show', positionMs: 2_380_000, durationMs: 2_400_000 });
assert.equal(e.watched, true);
const half = core.recordProgress(null, { profileId: 'p', id: 'tt9', kind: 'movie', title: 'Film', positionMs: 600_000, durationMs: 6_000_000 });
assert.equal(half.watched, false);
assert.equal(core.resumePositionMs(half), 600_000);
const videos = [1, 2, 3].map((n) => ({ id: `tt1:1:${n}`, season: 1, episode: n, title: 'E' + n }));
assert.equal(core.findNextEpisode('tt1', 'tt1:1:2', videos).episode, 3);
const next = core.nextUpAfter({ profileId: 'p', seriesId: 'tt1', title: 'Show', playbackId: 'tt1:1:2', videos });
const cw = core.continueWatching([e, half], [next], 'p');
assert.deepEqual(Object.fromEntries(cw.map((c) => [c.id, c.resumeId])), { tt1: 'tt1:1:3', tt9: 'tt9' });

console.log('core rules: all good');

// ---------- Trakt (mirrors trakt.rs tests) ----------
{
  const m = core.traktScrobbleBody('tt1', 'movie', 42.123);
  assert.deepEqual(m, { movie: { ids: { imdb: 'tt1' } }, progress: 42.12 });
  const e = core.traktScrobbleBody('tmdb:1399:2:5', 'series', 90);
  assert.equal(e.show.ids.tmdb, 1399);
  assert.deepEqual(e.episode, { season: 2, number: 5 });
  assert.equal(core.traktScrobbleBody('kitsu:1:1:1', 'series', 5), null);

  const w = (id, kind, watched, dur = 1) => ({ profile_id: 'p', id, kind, title: 'T', position_ms: watched ? dur : 1, duration_ms: dur, watched, updated_at_ms: 1000, deleted: false });
  const body = core.traktHistoryBody([w('tt9:1:1', 'series', true), w('tt9:1:2', 'series', true), w('tt1', 'movie', true), w('tt2', 'movie', false, 6e6)]);
  assert.equal(body.movies.length, 1);
  assert.equal(body.shows[0].seasons[0].episodes.length, 2);
  assert.equal(core.traktHistoryBody([]), null);

  const mine = { ...core.recordProgress(null, { profileId: 'p', id: 'tmdb:1399:1:1', kind: 'series', title: 'GoT', positionMs: 10, durationMs: 3_600_000 }), updated_at_ms: 5 };
  const playback = [
    { progress: 50, paused_at: '2026-11-03T20:00:00.000Z', type: 'episode', episode: { season: 1, number: 1 }, show: { title: 'GoT', ids: { imdb: 'tt0944947', tmdb: 1399 } } },
    { progress: 25, paused_at: '2026-11-03T21:00:00.000Z', type: 'movie', movie: { title: 'Dune', ids: { imdb: 'tt1160419', tmdb: 438631 } } },
    { progress: 0.2, paused_at: '2026-11-03T21:00:00.000Z', type: 'movie', movie: { title: 'Skip', ids: { imdb: 'tt0' } } },
  ];
  const pm = core.traktMergePlayback(playback, [mine], 'p');
  assert.equal(pm.changed, 2);
  assert.equal(pm.history.find((x) => x.id === 'tmdb:1399:1:1').position_ms, 1_800_000);
  const dune = pm.history.find((x) => x.id === 'tt1160419');
  assert.equal(dune.duration_ms, core.PERCENT_DURATION_MS);
  assert.equal(core.resumePositionMs(dune), 0);
  assert.ok(Math.abs(core.resumeFraction(dune) - 0.25) < 1e-6);
  assert.equal(core.traktMergePlayback(playback, pm.history, 'p').changed, 0);

  const movies = [{ last_watched_at: '2026-11-01T10:00:00Z', movie: { title: 'Dune', ids: { imdb: 'tt1' } } }];
  const shows = [{ last_watched_at: '2026-11-02T10:00:00Z', show: { title: 'S', ids: { imdb: 'tt9' } },
    seasons: [{ number: 0, episodes: [{ number: 1 }] }, { number: 1, episodes: [{ number: 1, last_watched_at: '2026-11-02T10:00:00Z' }, { number: 2 }] }] }];
  const wm = core.traktMergeWatched(movies, shows, [], 'p', 100);
  assert.equal(wm.changed, 3);
  assert.ok(wm.history.every((x) => x.watched));
  assert.ok(wm.history.some((x) => x.id === 'tt9:1:2'));
  assert.equal(core.traktPendingHistory(wm.history, 'p', 0).length, 3);
  assert.equal(core.traktActivityStamp({ movies: { watched_at: 'a', paused_at: 'b' }, episodes: { watched_at: 'c' } }), 'a|b|c|');
  console.log('trakt rules ok');
}
