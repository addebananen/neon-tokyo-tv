// The built-in player and the hand-off to the viewer's own player (Infuse, VLC, …).
//
// Built-in: <video> with hls.js when the browser has no native HLS, neon controls, keys for a
// remote, progress saved every 10 s with the core's watched rule, next episode near the end,
// AirPlay on Apple devices and picture-in-picture.

import * as core from './core.js';
import * as store from './store.js';
import * as nav from './nav.js';
import { esc, html, fmtTime, toast, modal, isApple } from './ui.js';

let el = null;
let cleanup = null;

export const playerOpen = () => !!el;
export function closePlayer() { if (cleanup) cleanup(); }

// ---------- External players ----------

const UA = navigator.userAgent;
const IOS = /iPhone|iPad|iPod/.test(UA) || (/Macintosh/.test(UA) && 'ontouchend' in document);
const ANDROID = /Android/.test(UA);

/** Players this device can hand a link to, as [id, name, buildUrl]. */
export function externalPlayers() {
  const list = [['builtin', 'Neon Tokyo (built in)', null]];
  if (IOS) {
    list.push(
      ['infuse', 'Infuse', (u) => `infuse://x-callback-url/play?url=${encodeURIComponent(u)}`],
      ['vlc', 'VLC', (u) => `vlc-x-callback://x-callback-url/stream?url=${encodeURIComponent(u)}`],
      ['outplayer', 'Outplayer', (u) => `outplayer://${u}`],
      ['nplayer', 'nPlayer', (u) => `nplayer-${u}`],
      ['senplayer', 'SenPlayer', (u) => `SenPlayer://x-callback-url/play?url=${encodeURIComponent(u)}`],
    );
  } else if (ANDROID) {
    const intent = (u, pkg) => {
      const m = u.match(/^(https?):\/\/(.*)$/);
      if (!m) return u;
      return `intent://${m[2]}#Intent;scheme=${m[1]};action=android.intent.action.VIEW;type=video/*;${pkg ? `package=${pkg};` : ''}end`;
    };
    list.push(
      ['android-any', 'Choose app each time', (u) => intent(u)],
      ['vlc', 'VLC', (u) => intent(u, 'org.videolan.vlc')],
      ['mx', 'MX Player', (u) => intent(u, 'com.mxtech.videoplayer.ad')],
      ['justplayer', 'Just Player', (u) => intent(u, 'com.brouken.player')],
    );
  } else {
    list.push(['vlc', 'VLC (desktop)', (u) => `vlc://${u}`]);
  }
  return list;
}

export function chosenPlayer() {
  const id = store.get().settings.player || 'builtin';
  return externalPlayers().find((p) => p[0] === id) || externalPlayers()[0];
}

/** Hands the link to the chosen app; when the viewer comes back, asks if they finished. */
function playExternal(ctx, player) {
  const { meta, video, stream } = ctx;
  const p = store.profile();
  const old = store.entry(ctx.playbackId);
  // Keep it in "Continue watching" with whatever was known
  store.putEntry({ ...core.recordProgress(old, {
    profileId: p.id, id: ctx.playbackId, kind: video ? 'series' : meta.type, title: meta.name,
    poster: meta.poster, background: meta.background, logo: meta.logo,
    positionMs: old ? old.position_ms : 0, durationMs: old ? old.duration_ms : 0,
  }) });
  location.href = player[2](stream.url);
  const left = Date.now();
  const back = () => {
    if (document.hidden) return;
    document.removeEventListener('visibilitychange', back);
    if (Date.now() - left < 60000) { if (ctx.onClosed) ctx.onClosed(); return; }
    modal((close) => {
      const panel = html(`<div class="panel">
        <h2>Finished ${esc(video ? core.episodeTitle(video) : meta.name)}?</h2>
        <p class="muted">${esc(player[1])} does not tell Neon Tokyo how far you got.</p>
        <div class="panel-actions"><button class="btn ghost" data-no>Not yet</button><button class="btn primary" data-yes data-autofocus>Yes, mark watched</button></div>
      </div>`);
      panel.querySelector('[data-no]').onclick = () => close();
      panel.querySelector('[data-yes]').onclick = () => { markWatched(ctx); close(); };
      return panel;
    }).then(() => ctx.onClosed && ctx.onClosed());
  };
  setTimeout(() => document.addEventListener('visibilitychange', back), 800);
}

function markWatched(ctx) {
  import('./trakt.js').then((t) => t.soonSync()).catch(() => {});
  const { meta, video } = ctx;
  const p = store.profile();
  const old = store.entry(ctx.playbackId);
  const dur = (old && old.duration_ms) || 1;
  store.putEntry({ ...core.recordProgress(old, {
    profileId: p.id, id: ctx.playbackId, kind: video ? 'series' : meta.type, title: meta.name,
    poster: meta.poster, background: meta.background, logo: meta.logo, positionMs: dur, durationMs: dur,
  }), watched: true });
  if (video) {
    store.putNextUp(core.nextUpAfter({
      profileId: p.id, seriesId: meta.id, title: meta.name, poster: meta.poster, background: meta.background,
      playbackId: ctx.playbackId, videos: meta.videos || [],
    }));
  }
}

// ---------- Built-in player ----------

let hlsLoading = null;
function loadHls() {
  hlsLoading = hlsLoading || new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = 'vendor/hls-1.5.20.min.js';
    s.onload = () => resolve(window.Hls);
    s.onerror = reject;
    document.head.append(s);
  });
  return hlsLoading;
}

const ICON = {
  play: '<path d="M7 4.5v15l12-7.5z" fill="currentColor"/>',
  pause: '<rect x="6" y="4.5" width="4" height="15" rx="1" fill="currentColor"/><rect x="14" y="4.5" width="4" height="15" rx="1" fill="currentColor"/>',
  back10: '<path d="M4 12a8 8 0 1 0 2.4-5.7M4 4v4h4"/><text x="12" y="15.5" font-size="7" text-anchor="middle" fill="currentColor" stroke="none" font-family="Outfit">10</text>',
  fwd10: '<path d="M20 12a8 8 0 1 1-2.4-5.7M20 4v4h-4"/><text x="12" y="15.5" font-size="7" text-anchor="middle" fill="currentColor" stroke="none" font-family="Outfit">10</text>',
  cc: '<rect x="3" y="5" width="18" height="14" rx="3"/><path d="M10 10.5a2 2 0 1 0 0 3M16.5 10.5a2 2 0 1 0 0 3"/>',
  pip: '<rect x="3" y="5" width="18" height="14" rx="2"/><rect x="12" y="11" width="7" height="6" rx="1" fill="currentColor"/>',
  air: '<path d="M6 17H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1h16a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1h-2"/><path d="m12 15 5 6H7z" fill="currentColor"/>',
  full: '<path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/>',
  next: '<path d="M5 5v14l10-7z" fill="currentColor"/><path d="M18 5v14"/>',
  src: '<path d="M4 7h16M4 12h16M4 17h10"/>',
  close: '<path d="M15 5 8 12l7 7"/>',
};
const ic = (k) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICON[k]}</svg>`;

export function openPlayer(ctx) {
  const player = chosenPlayer();
  if (player[2] && ctx.stream.url) { playExternal(ctx, player); return; }
  if (el) closePlayer();
  const { meta } = ctx;
  const p = store.profile();
  const prevFocus = document.activeElement;

  el = html(`<div class="player" role="dialog" aria-label="Player">
    <video playsinline webkit-playsinline x-webkit-airplay="allow" preload="auto"></video>
    <div class="pl-buffer"><span class="spinner big"></span></div>
    <div class="pl-ui">
      <div class="pl-top">
        <button class="pl-btn" data-act="close" aria-label="Back">${ic('close')}</button>
        <div class="pl-title"><strong></strong><small></small></div>
        <button class="pl-btn" data-act="sources" aria-label="Sources">${ic('src')}</button>
      </div>
      <button class="pl-center" data-act="toggle" aria-label="Play or pause" tabindex="-1">${ic('play')}</button>
      <div class="pl-bottom">
        <div class="pl-seek"><span class="pl-buffered"></span><span class="pl-played"></span><input type="range" min="0" max="1000" step="1" value="0" aria-label="Position"><span class="pl-hover"></span></div>
        <div class="pl-row">
          <button class="pl-btn" data-act="back10" aria-label="Back 10 seconds">${ic('back10')}</button>
          <button class="pl-btn main" data-act="toggle" aria-label="Play or pause" data-autofocus>${ic('play')}</button>
          <button class="pl-btn" data-act="fwd10" aria-label="Forward 10 seconds">${ic('fwd10')}</button>
          <span class="pl-time"><b>0:00</b> / <span>0:00</span></span>
          <span class="pl-spacer"></span>
          <button class="pl-btn" data-act="next" aria-label="Next episode" hidden>${ic('next')}</button>
          <button class="pl-btn" data-act="subs" aria-label="Subtitles" hidden>${ic('cc')}</button>
          <button class="pl-btn" data-act="air" aria-label="AirPlay" hidden>${ic('air')}</button>
          <button class="pl-btn" data-act="pip" aria-label="Picture in picture" hidden>${ic('pip')}</button>
          <button class="pl-btn" data-act="full" aria-label="Full screen">${ic('full')}</button>
        </div>
      </div>
    </div>
    <div class="pl-nextcard" hidden><small>Up next</small><strong></strong><button class="btn primary" data-act="next">Play now <span class="count"></span></button></div>
    <div class="pl-error" hidden><h2>This source would not play</h2><p></p><div class="row-btns"><button class="btn primary" data-act="sources">Pick another source</button><button class="btn ghost" data-act="close">Close</button></div></div>
  </div>`);
  document.body.append(el);
  document.body.classList.add('playing');
  nav.setScope(el);
  history.pushState({ player: true }, '');

  const v = el.querySelector('video');
  const range = el.querySelector('input[type=range]');
  const played = el.querySelector('.pl-played');
  const buffered = el.querySelector('.pl-buffered');
  const timeB = el.querySelector('.pl-time b');
  const timeT = el.querySelector('.pl-time span');
  let hls = null, hideTimer = 0, saveTimer = 0, nextInfo = null, nextLoading = false, countdown = 0, seeking = false;
  let state = { ...ctx };

  const setTitle = () => {
    el.querySelector('.pl-title strong').textContent = meta.name;
    el.querySelector('.pl-title small').textContent = state.video ? core.episodeTitle(state.video) : (state.stream.qualityLabel ? `${state.stream.qualityLabel} · ${state.stream.addonName}` : state.stream.addonName);
  };

  const showUi = () => {
    el.classList.add('ui');
    clearTimeout(hideTimer);
    hideTimer = setTimeout(() => { if (!v.paused && !el.querySelector('.pl-ui').contains(document.activeElement) || !v.paused && !document.documentElement.classList.contains('kbd')) el.classList.remove('ui'); }, 3200);
  };

  const save = (final = false) => {
    if (!v.duration || !isFinite(v.duration) || v.currentTime < 1) return;
    const old = store.entry(state.playbackId);
    const e = core.recordProgress(old, {
      profileId: p.id, id: state.playbackId, kind: state.video ? 'series' : meta.type, title: meta.name,
      poster: meta.poster, background: state.video && state.video.thumbnail ? state.video.thumbnail : meta.background, logo: meta.logo,
      positionMs: v.currentTime * 1000, durationMs: v.duration * 1000,
    });
    store.putEntry(e);
    if (e.watched && state.video && (final || !old || !old.watched)) {
      store.putNextUp(core.nextUpAfter({ profileId: p.id, seriesId: meta.id, title: meta.name, poster: meta.poster, background: meta.background, playbackId: state.playbackId, videos: meta.videos || [] }));
    }
  };

  const load = async (stream, startAt) => {
    el.querySelector('.pl-error').hidden = true;
    el.classList.add('buffering');
    if (hls) { hls.destroy(); hls = null; }
    $$tracks();
    const url = stream.url;
    const nativeHls = v.canPlayType('application/vnd.apple.mpegurl');
    if (stream.container === 'hls' && !nativeHls) {
      try {
        const Hls = await loadHls();
        if (Hls && Hls.isSupported()) {
          hls = new Hls({ startPosition: startAt || -1, maxBufferLength: 60 });
          hls.on(Hls.Events.ERROR, (_, d) => { if (d.fatal) fail(d.details || 'HLS error'); });
          hls.loadSource(url);
          hls.attachMedia(v);
        } else v.src = url;
      } catch { v.src = url; }
    } else {
      v.src = url;
    }
    if (startAt) {
      const seek = () => { try { v.currentTime = startAt; } catch { /* not seekable yet */ } };
      v.addEventListener('loadedmetadata', seek, { once: true });
    }
    v.play().catch(() => { el.classList.add('ui'); });
    setTitle();
    loadSubtitles(stream);
  };

  const fail = (why) => {
    el.classList.remove('buffering');
    const box = el.querySelector('.pl-error');
    box.querySelector('p').textContent = why || 'The browser could not open the file.';
    box.hidden = false;
    nav.focusFirst(box);
  };

  // Subtitles: from the stream itself and from subtitle addons; SRT is turned into WebVTT
  const subsBtn = el.querySelector('[data-act="subs"]');
  let subs = [];
  function $$tracks() { [...v.querySelectorAll('track')].forEach((t) => { URL.revokeObjectURL(t.src); t.remove(); }); subs = []; subsBtn.hidden = true; }
  async function loadSubtitles(stream) {
    const type = state.video ? 'series' : meta.type;
    const fromAddons = store.get().addons.filter((a) => core.serves(a.manifest, 'subtitles', type, state.streamId));
    const lists = await Promise.allSettled(fromAddons.map((a) => core.fetchSubtitles(a.base, type, state.streamId)));
    subs = [...(stream.subtitles || []), ...lists.flatMap((r) => (r.status === 'fulfilled' ? r.value : []))].filter((s) => s.url);
    subsBtn.hidden = !subs.length;
  }
  async function pickSub(s) {
    [...v.textTracks].forEach((t) => { t.mode = 'disabled'; });
    if (!s) return;
    try {
      let text = await (await fetch(s.url)).text();
      if (!/^WEBVTT/.test(text.trim())) {
        text = 'WEBVTT\n\n' + text.replace(/\r/g, '').replace(/(\d\d:\d\d:\d\d),(\d\d\d)/g, '$1.$2');
      }
      const t = document.createElement('track');
      t.kind = 'subtitles'; t.label = s.lang || 'Subtitles'; t.srclang = (s.lang || 'und').slice(0, 2);
      t.src = URL.createObjectURL(new Blob([text], { type: 'text/vtt' }));
      v.append(t);
      t.addEventListener('load', () => { t.track.mode = 'showing'; });
      t.track.mode = 'showing';
    } catch { toast('Could not load those subtitles', 'bad'); }
  }
  const LANG = { swe: 'Svenska', sv: 'Svenska', eng: 'English', en: 'English', nor: 'Norsk', dan: 'Dansk', fin: 'Suomi' };
  const subsMenu = () => modal((close) => {
    const order = ['swe', 'sv', 'eng', 'en'];
    const sorted = subs.slice().sort((a, b) => ((order.indexOf(a.lang) + 1 || 99) - (order.indexOf(b.lang) + 1 || 99)));
    const panel = html(`<div class="panel subs-menu"><h2>Subtitles</h2><div class="subs-list">
      <button class="btn ghost" data-sub="-1" data-autofocus>Off</button>
      ${sorted.slice(0, 40).map((s, i) => `<button class="btn ghost" data-sub="${i}">${esc(LANG[s.lang] || s.lang || 'Unknown')}</button>`).join('')}
    </div></div>`);
    panel.addEventListener('click', (e) => { const b = e.target.closest('[data-sub]'); if (!b) return; const i = +b.dataset.sub; pickSub(i < 0 ? null : sorted[i]); close(); });
    return panel;
  }, { cls: 'side' });

  // Next episode
  const nextBtn = el.querySelector('[data-act="next"]');
  const nextCard = el.querySelector('.pl-nextcard');
  const prepareNext = async () => {
    if (!state.video || nextLoading || nextInfo) return;
    nextLoading = true;
    const { nextEpisodeStream } = await import('./app.js');
    nextInfo = await nextEpisodeStream(state).catch(() => null);
    nextLoading = false;
    if (nextInfo && nextInfo.stream) {
      nextBtn.hidden = false;
      nextCard.querySelector('strong').textContent = core.episodeTitle(nextInfo.video);
    }
  };
  const playNext = () => {
    if (!nextInfo || !nextInfo.stream) return;
    save(true);
    state = { ...state, ...nextInfo };
    nextInfo = null; countdown = 0;
    nextCard.hidden = true; nextBtn.hidden = true;
    load(state.stream, 0);
  };

  const tick = () => {
    const d = v.duration;
    if (!d || !isFinite(d)) return;
    const f = v.currentTime / d;
    if (!seeking) range.value = Math.round(f * 1000);
    played.style.transform = `scaleX(${f})`;
    timeB.textContent = fmtTime(v.currentTime);
    timeT.textContent = '-' + fmtTime(d - v.currentTime);
    try { if (v.buffered.length) buffered.style.transform = `scaleX(${v.buffered.end(v.buffered.length - 1) / d})`; } catch { /* ignore */ }
    const left = d - v.currentTime;
    if (state.video && left < 120) prepareNext();
    if (nextInfo && nextInfo.stream && left < 32 && nextCard.hidden && !countdown) {
      nextCard.hidden = false;
      countdown = 1;
    }
    if (!nextCard.hidden) nextCard.querySelector('.count').textContent = left > 0 ? `(${Math.ceil(Math.max(0, left - 2))})` : '';
  };

  const toggle = () => { if (v.paused) v.play().catch(() => {}); else v.pause(); showUi(); };
  const paintPlay = () => el.querySelectorAll('[data-act="toggle"]').forEach((b) => { b.innerHTML = ic(v.paused ? 'play' : 'pause'); });

  v.addEventListener('timeupdate', tick);
  v.addEventListener('play', () => { paintPlay(); showUi(); });
  v.addEventListener('pause', () => { paintPlay(); el.classList.add('ui'); save(); });
  v.addEventListener('waiting', () => el.classList.add('buffering'));
  v.addEventListener('playing', () => el.classList.remove('buffering'));
  v.addEventListener('canplay', () => el.classList.remove('buffering'));
  v.addEventListener('ended', () => { save(true); if (nextInfo && nextInfo.stream) playNext(); else close(); });
  v.addEventListener('error', () => { if (!hls) fail(v.error && v.error.code === 4 ? 'The file type or its codecs are not supported here. Try another source, or set your own player in Settings.' : 'The source stopped answering.'); });
  saveTimer = setInterval(() => { if (!v.paused) save(); }, 10000);

  range.addEventListener('input', () => { seeking = true; const t = (range.value / 1000) * (v.duration || 0); timeB.textContent = fmtTime(t); played.style.transform = `scaleX(${range.value / 1000})`; showUi(); });
  range.addEventListener('change', () => { seeking = false; if (v.duration) v.currentTime = (range.value / 1000) * v.duration; });

  // AirPlay (Safari) and picture-in-picture
  const airBtn = el.querySelector('[data-act="air"]');
  if (window.WebKitPlaybackTargetAvailabilityEvent) {
    v.addEventListener('webkitplaybacktargetavailabilitychanged', (e) => { airBtn.hidden = e.availability !== 'available'; });
  }
  const pipBtn = el.querySelector('[data-act="pip"]');
  pipBtn.hidden = !(document.pictureInPictureEnabled || v.webkitSupportsPresentationMode);

  const close = () => {
    if (!el) return;
    save(true);
    import('./trakt.js').then((t) => { t.scrobbleStop(store.entry(state.playbackId)); t.soonSync(); }).catch(() => {});
    clearInterval(saveTimer); clearTimeout(hideTimer);
    if (hls) hls.destroy();
    v.removeAttribute('src'); v.load();
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
    const node = el;
    el = null; cleanup = null;
    node.classList.add('out');
    setTimeout(() => node.remove(), 250);
    document.body.classList.remove('playing');
    nav.setScope(null);
    removeEventListener('keydown', keys, true);
    removeEventListener('popstate', onPop);
    if (history.state && history.state.player) history.back();
    if (prevFocus && document.contains(prevFocus)) prevFocus.focus({ preventScroll: true });
    if (ctx.onClosed) ctx.onClosed();
  };
  cleanup = close;
  const onPop = () => { if (el) { history.pushState(null, ''); history.back(); close(); } };
  setTimeout(() => addEventListener('popstate', onPop), 0);

  el.addEventListener('click', async (e) => {
    const b = e.target.closest('[data-act]');
    if (!b) { if (e.target === v || e.target.classList.contains('pl-ui')) { if (el.classList.contains('ui') && !('ontouchend' in document)) toggle(); else showUi(); } return; }
    const a = b.dataset.act;
    showUi();
    if (a === 'toggle') toggle();
    else if (a === 'back10') v.currentTime = Math.max(0, v.currentTime - 10);
    else if (a === 'fwd10') v.currentTime = Math.min(v.duration || 0, v.currentTime + 10);
    else if (a === 'close') close();
    else if (a === 'next') playNext();
    else if (a === 'subs') subsMenu();
    else if (a === 'air') v.webkitShowPlaybackTargetPicker && v.webkitShowPlaybackTargetPicker();
    else if (a === 'pip') {
      if (document.pictureInPictureElement) document.exitPictureInPicture();
      else if (v.requestPictureInPicture) v.requestPictureInPicture().catch(() => {});
      else if (v.webkitSetPresentationMode) v.webkitSetPresentationMode('picture-in-picture');
    } else if (a === 'full') {
      if (document.fullscreenElement) document.exitFullscreen();
      else if (el.requestFullscreen) el.requestFullscreen().catch(() => {});
      else if (v.webkitEnterFullscreen) v.webkitEnterFullscreen();
    } else if (a === 'sources') {
      const { openSources } = await import('./app.js');
      save(true);
      const keep = { ...state, onClosed: ctx.onClosed };
      close();
      openSources(keep);
    }
  });
  el.addEventListener('pointermove', showUi, { passive: true });
  el.addEventListener('touchstart', showUi, { passive: true });

  // Remote and keyboard: with the controls hidden, left/right seek and OK pauses, as on the TV
  const keys = (e) => {
    if (!el || document.querySelector('.modal')) return;
    const uiOn = el.classList.contains('ui');
    const k = e.key;
    if (k === ' ' || k === 'k' || k === 'MediaPlayPause') { e.preventDefault(); toggle(); return; }
    if (k === 'f') { el.querySelector('[data-act="full"]').click(); return; }
    if (k === 'm') { v.muted = !v.muted; toast(v.muted ? 'Muted' : 'Sound on'); return; }
    if (k === 'MediaFastForward') { v.currentTime += 30; showUi(); return; }
    if (k === 'MediaRewind') { v.currentTime -= 30; showUi(); return; }
    if (!uiOn || e.target === range || document.activeElement === document.body) {
      if (k === 'ArrowLeft' || k === 'ArrowRight') {
        e.preventDefault(); e.stopPropagation();
        v.currentTime = Math.max(0, Math.min(v.duration || 0, v.currentTime + (k === 'ArrowRight' ? 10 : -10)));
        showUi();
        return;
      }
      if (!uiOn && (k === 'ArrowUp' || k === 'ArrowDown' || k === 'Enter')) {
        e.preventDefault(); e.stopPropagation();
        showUi();
        nav.focusEl(el.querySelector('.pl-btn.main'), { scroll: false });
        return;
      }
    }
    if (uiOn) showUi();
  };
  addEventListener('keydown', keys, true);

  requestAnimationFrame(() => el.classList.add('in', 'ui'));
  const entry = store.entry(state.playbackId);
  const start = ctx.fromStart ? 0 : core.resumePositionMs(entry) / 1000;
  load(state.stream, start);
  // From Trakt only the share is known: seek there once the length is
  const share = ctx.fromStart ? 0 : core.resumeFraction(entry);
  if (share > 0.01) v.addEventListener('loadedmetadata', () => { if (isFinite(v.duration)) v.currentTime = v.duration * share; }, { once: true });
  setTimeout(() => nav.focusEl(el.querySelector('.pl-btn.main'), { scroll: false }), 50);
}
