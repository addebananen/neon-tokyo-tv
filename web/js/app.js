// Neon Tokyo Web: router, home, catalogs, search, details, sources, addons, settings, profiles.

import * as core from './core.js';
import * as store from './store.js';
import * as nav from './nav.js';
import { $, $$, esc, html, poster, imgUrl, toast, modal, closeTopModal, blip, probeMetahub, isApple, isStandalone } from './ui.js';
import { playIntro } from './intro.js';
import * as sync from './sync.js';
import * as trakt from './trakt.js';
import { worldHtml, leanToward, floorUnder, floorOff } from './world.js';
import { openPlayer, playerOpen, closePlayer, externalPlayers, chosenPlayer } from './player.js';

export const VERSION = '0.2.0-web.__BUILD__';

const main = () => $('#main');
const settings = () => store.get().settings;
const sound = (k) => blip(k, settings().sounds);

// ---------- Theme ----------

function applyTheme() {
  const p = store.profile();
  document.documentElement.dataset.theme = (p && p.theme) || 'neon_tokyo';
  const meta = $('meta[name="theme-color"]');
  if (meta) meta.content = document.documentElement.dataset.theme === 'deep_blue' ? '#061423' : '#0D0721';
}

// ---------- Shell ----------

const TABS = [
  ['home', 'Home', '<path d="M3 11.5 12 4l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>'],
  ['movies', 'Movies', '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M7 5v14M17 5v14M3 9.5h4M17 9.5h4M3 14.5h4M17 14.5h4"/>'],
  ['series', 'Series', '<rect x="3" y="6" width="18" height="12" rx="2"/><path d="m9 3 3 3 3-3"/>'],
  ['search', 'Search', '<circle cx="11" cy="11" r="6.5"/><path d="m16 16 4.5 4.5"/>'],
  ['addons', 'Addons', '<path d="M10 3h4v4h4v4h-2a2 2 0 1 0 0 4h2v4h-4v-2a2 2 0 1 0-4 0v2H6v-4H4a2 2 0 1 1 0-4h2V7h4z"/>'],
  ['settings', 'Settings', '<circle cx="12" cy="12" r="3"/><path d="M12 2.8v2.4M12 18.8v2.4M4.2 6.5l1.7 1.2M18.1 16.3l1.7 1.2M4.2 17.5l1.7-1.2M18.1 7.7l1.7-1.2M2.8 12h2.4M18.8 12h2.4"/>'],
];
const icon = (d) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${d}</svg>`;

function renderShell() {
  $('#app').innerHTML = `
    ${worldHtml()}
    <header id="topbar">
      <a href="#/profiles" class="profile-badge" id="profileBadge" aria-label="Switch profile"></a>
      <a href="#/home" class="brand" tabindex="-1" aria-label="Neon Tokyo"><span class="brand-word">NEON<b>TOKYO</b></span><span class="brand-jp">ネオン東京</span></a>
      <nav class="tabs" aria-label="Main">
        ${TABS.map(([id, label, d]) => `<a href="#/${id}" data-tab="${id}">${icon(d)}<span>${label}</span></a>`).join('')}
        <i class="tab-mark" aria-hidden="true"></i>
      </nav>
    </header>
    <main id="main" tabindex="-1"></main>`;
  updateBadge();
}

function updateBadge() {
  const p = store.profile();
  const b = $('#profileBadge');
  if (!b) return;
  b.innerHTML = p ? avatar(p, 'sm') + `<span>${esc(p.name)}</span>` : '';
}

function setTab(id) {
  const tabs = $('.tabs');
  if (!tabs) return;
  let active = null;
  $$('.tabs a').forEach((a) => {
    const on = a.dataset.tab === id;
    a.classList.toggle('active', on);
    if (on) { a.setAttribute('aria-current', 'page'); active = a; } else a.removeAttribute('aria-current');
  });
  const mark = $('.tab-mark');
  if (active && mark) {
    mark.style.opacity = 1;
    mark.style.transform = `translateX(${active.offsetLeft}px)`;
    mark.style.width = active.offsetWidth + 'px';
  } else if (mark) mark.style.opacity = 0;
}

function avatar(p, size = '') {
  const letter = p.emoji || (p.name || '?').trim().charAt(0).toUpperCase();
  return `<span class="avatar ${size}" style="--c:${esc(p.color)}">${esc(letter)}</span>`;
}

// ---------- Router ----------

const viewCache = new Map(); // hash -> { node, scrollY, focusKey }
let current = { hash: '', node: null };

function parseHash() {
  const raw = location.hash.replace(/^#\/?/, '') || 'home';
  const [path, qs] = raw.split('?');
  const parts = path.split('/').map(decodeURIComponent);
  return { name: parts[0], args: parts.slice(1), q: new URLSearchParams(qs || ''), hash: '#/' + raw };
}

function remember() {
  if (!current.node) return;
  const f = document.activeElement;
  const key = f && current.node.contains(f) ? f.dataset.key || null : null;
  const entry = { node: current.node, scrollY, focusKey: key };
  if (current.node.dataset.keep) viewCache.set(current.hash, entry);
  else viewCache.set(current.hash, { node: null, scrollY, focusKey: key });
}

async function route() {
  if (playerOpen()) closePlayer();
  while (closeTopModal()) { /* close any dialog left open */ }
  const r = parseHash();
  if (!store.profile() && r.name !== 'profiles') { location.replace('#/profiles'); return; }
  remember();
  document.body.classList.toggle('on-profiles', r.name === 'profiles');
  document.body.classList.toggle('quiet-world', !['home', 'movies', 'series', 'profiles', 'service'].includes(r.name));
  setTab(['home', 'movies', 'series', 'search', 'addons', 'settings'].includes(r.name) ? r.name : null);
  const cached = viewCache.get(r.hash);
  const m = main();
  let node = cached && cached.node;
  if (!node) {
    node = document.createElement('div');
    node.className = 'view view-' + r.name;
    const render = VIEWS[r.name] || VIEWS.home;
    render(node, r);
  } else if (node._refresh) node._refresh();
  m.replaceChildren(node);
  current = { hash: r.hash, node };
  node.classList.remove('enter'); void node.offsetWidth; node.classList.add('enter');
  const y = cached ? cached.scrollY : 0;
  scrollTo({ top: y, behavior: 'instant' });
  requestAnimationFrame(() => {
    const target = cached && cached.focusKey && node.querySelector(`[data-key="${CSS.escape(cached.focusKey)}"]`);
    if (target) nav.focusEl(target, { scroll: false });
    else if (document.documentElement.classList.contains('kbd')) nav.focusFirst(node);
  });
}

function back() {
  sound('back');
  if (closeTopModal()) return;
  if (playerOpen()) { closePlayer(); return; }
  const r = parseHash();
  if (r.name === 'home') { const tab = $('.tabs a[data-tab="home"]'); if (tab && document.activeElement !== tab) { nav.focusEl(tab); scrollTo({ top: 0, behavior: 'smooth' }); } return; }
  if (r.name === 'profiles') { if (store.profile()) location.hash = '#/home'; return; }
  if (history.length > 1) history.back(); else location.hash = '#/home';
}

// ---------- Rows and cards ----------

function typeWord(type) {
  return { movie: 'Movies', series: 'Series', anime: 'Anime', tv: 'TV', channel: 'Channels' }[type] || (type ? type[0].toUpperCase() + type.slice(1) : '');
}

function catalogTitle(addon, c) {
  const name = c.name || c.id;
  const w = typeWord(c.type);
  return new RegExp(w, 'i').test(name) || !w ? name : `${name} ${w}`;
}

/** `--art` for a card: the lit card's glow is its own picture, blurred, so it glows in the
 *  picture's colours. Only the lit card reads it, so nothing loads early. */
function artStyle(src) {
  return src ? ` style="--art:${esc(`url("${String(src).replace(/["\\\n]/g, (c) => '\\' + c)}")`)}"` : '';
}

const metahub = (kind, id) => (String(id).startsWith('tt') ? `https://images.metahub.space/${kind}/medium/${id}/img` : '');

function card(m, extra = '') {
  const type = m.type || 'movie';
  const key = `${type}:${m.id}`;
  return `<a class="card" href="#/detail/${encodeURIComponent(type)}/${encodeURIComponent(m.id)}" data-key="${esc(key)}"${artStyle(imgUrl(m.poster, 300))} ${extra}>
    ${poster(m.poster, m.name, 300)}
    <span class="card-title">${esc(m.name)}</span>
  </a>`;
}

/** A wide 2:1 tile: the backdrop with the title's logo on it, like an app tile on Neon Home. */
function wideTile({ href, key, art, logo, name, sub = '', badge = '', progress = 0, attrs = '', after = '' }) {
  const src = imgUrl(art, 640);
  return `<a class="card wide" href="${href}" data-key="${esc(key)}"${artStyle(src)} ${attrs}>
    <div class="poster">${src ? `<img src="${esc(src)}" alt="" loading="lazy" decoding="async" onload="this.classList.add('in')" onerror="this.remove()">` : ''}
      ${badge}
      <span class="wide-label">${logo ? `<img class="logo" src="${esc(imgUrl(logo, 500))}" alt="" loading="lazy" decoding="async" onerror="this.remove()">` : ''}<b>${esc(name)}</b>${sub ? `<em>${sub}</em>` : ''}</span>
      ${progress > 0 ? `<span class="progress"><i style="width:${Math.min(100, progress * 100).toFixed(1)}%"></i></span>` : ''}
    </div>${after}
  </a>`;
}

function wideCard(c) {
  const ep = c.season != null && c.episode ? `S${c.season}:E${c.episode}` : '';
  return wideTile({
    href: `#/detail/${encodeURIComponent(c.kind)}/${encodeURIComponent(c.id)}?resume=1`, key: `cw:${c.id}`,
    art: c.background || metahub('background', c.id) || c.poster, logo: c.logo || metahub('logo', c.id), name: c.name, sub: ep,
    badge: c.hasNewEpisode ? '<span class="pill new">New episode</span>' : '', progress: c.progress, attrs: `data-cw="${esc(c.id)}"`,
    after: `<button class="card-x" data-dismiss="${esc(c.id)}" tabindex="-1" aria-label="Remove from Continue watching">×</button>`,
  });
}

function wideMeta(m) {
  const type = m.type || 'movie';
  const facts = [m.releaseInfo || m.year, m.imdbRating ? `★ ${m.imdbRating}` : ''].filter(Boolean).map(esc).join(' · ');
  return wideTile({
    href: `#/detail/${encodeURIComponent(type)}/${encodeURIComponent(m.id)}`, key: `${type}:${m.id}`,
    art: m.background || metahub('background', m.id) || m.poster, logo: m.logo || metahub('logo', m.id), name: m.name, sub: facts,
  });
}

function rowShell(title, key, { more = '' } = {}) {
  return html(`<section class="row" data-nav-section="${esc(key)}">
    <header class="row-head"><h2>${esc(title)}</h2>${more}</header>
    <div class="row-track" role="list"><div class="row-skeleton">${'<span></span>'.repeat(8)}</div></div>
  </section>`);
}

/** A catalog row that loads when it scrolls near the screen. */
function catalogRow(addon, c, extra = {}, { wide = false } = {}) {
  const title = catalogTitle(addon, c);
  const seeAll = `#/catalog/${encodeURIComponent(addon.base)}/${encodeURIComponent(c.type)}/${encodeURIComponent(c.id)}`;
  const row = rowShell(title, `${addon.base}|${c.type}|${c.id}`, { more: `<a class="see-all" href="${seeAll}" tabindex="-1">See all</a>` });
  if (wide) row.classList.add('row-wide');
  row._load = async () => {
    const track = row.querySelector('.row-track');
    try {
      const metas = await core.fetchCatalog(addon.base, c.type, c.id, extra);
      if (!metas.length) { row.remove(); return; }
      track.innerHTML = wide
        ? metas.slice(0, 20).map((m) => wideMeta({ ...m, type: m.type || c.type })).join('')
        : metas.slice(0, 30).map((m) => card({ ...m, type: m.type || c.type })).join('') +
          `<a class="card more" href="${seeAll}" data-key="more:${esc(seeAll)}"><span>See all</span></a>`;
      row.dispatchEvent(new CustomEvent('loaded', { detail: metas, bubbles: true }));
    } catch {
      row.remove();
    }
  };
  return row;
}

const rowObserver = 'IntersectionObserver' in window ? new IntersectionObserver((entries) => {
  for (const e of entries) {
    if (e.isIntersecting && e.target._load) { const l = e.target._load; e.target._load = null; rowObserver.unobserve(e.target); l(); }
  }
}, { rootMargin: '900px 0px' }) : null;

function observeRow(row) {
  if (rowObserver) rowObserver.observe(row); else row._load && row._load();
}

function isServiceAddon(a) { return a.base.includes('stremio-netflix-catalog-addon'); }
const SERVICE_CODES = new Map(core.SERVICES);
const SERVICE_COLORS = {
  nfx: ['#E50914', '#3b0306'], hbm: ['#7B2CFF', '#12052e'], dnp: ['#1A6BFF', '#05133a'], amp: ['#00A8E1', '#032836'],
  atp: ['#CFCFCF', '#1b1b1f'], sst: ['#0F9DFF', '#1a0b3a'], pmp: ['#0064FF', '#041633'], cru: ['#F47521', '#3a1504'],
};

function servicesRow(addon) {
  const codes = [...new Set((addon.manifest.catalogs || []).map((c) => c.id).filter((id) => SERVICE_CODES.has(id)))];
  if (!codes.length) return null;
  const row = html(`<section class="row services" data-nav-section="services">
    <header class="row-head"><h2>Streaming services</h2></header>
    <div class="row-track" role="list">${codes.map((code) => {
      const [a, b] = SERVICE_COLORS[code] || ['#FF3D9A', '#2A0F4F'];
      return `<a class="card service" href="#/service/${encodeURIComponent(addon.base)}/${code}" data-key="svc:${code}" style="--s1:${a};--s2:${b}"><span>${esc(SERVICE_CODES.get(code))}</span></a>`;
    }).join('')}</div></section>`);
  return row;
}

// ---------- Hero ----------

function hero() {
  const el = html(`<section class="hero" data-nav-section="hero">
    <div class="hero-art"></div><div class="hero-shade"></div>
    <div class="hero-body">
      <div class="hero-logo"></div>
      <p class="hero-meta"></p>
      <p class="hero-text"></p>
      <div class="hero-actions">
        <a class="btn primary" data-hero-open data-key="hero-open" href="#/home">${icon('<path d="M7 4.5v15l12-7.5z" fill="currentColor"/>')}<span>Watch</span></a>
        <button class="btn ghost" data-hero-list data-key="hero-list">${icon('<path d="M12 5v14M5 12h14"/>')}<span>Watchlist</span></button>
      </div>
      <div class="hero-dots"></div>
    </div>
  </section>`);
  let items = [], i = 0, timer = 0;
  const show = (n) => {
    if (!items.length) return;
    i = (n + items.length) % items.length;
    const m = items[i];
    const art = el.querySelector('.hero-art');
    const img = new Image();
    img.decoding = 'async';
    img.src = imgUrl(m.background, 1920);
    img.className = 'hero-img';
    img.onload = () => {
      art.append(img);
      requestAnimationFrame(() => img.classList.add('in'));
      [...art.children].slice(0, -1).forEach((old) => { old.classList.remove('in'); setTimeout(() => old.remove(), 900); });
    };
    el.querySelector('.hero-logo').innerHTML = m.logo
      ? `<img src="${esc(imgUrl(m.logo, 800))}" alt="${esc(m.name)}" onerror="this.outerHTML='<h1>${esc(m.name).replace(/'/g, '&#39;')}</h1>'">`
      : `<h1>${esc(m.name)}</h1>`;
    const meta = [m.releaseInfo || m.year, m.runtime, m.imdbRating ? `★ ${m.imdbRating}` : '', (m.genres || []).slice(0, 3).join(' · ')].filter(Boolean);
    el.querySelector('.hero-meta').textContent = meta.join('   ');
    el.querySelector('.hero-text').textContent = m.description || '';
    const open = el.querySelector('[data-hero-open]');
    open.href = `#/detail/${encodeURIComponent(m.type)}/${encodeURIComponent(m.id)}`;
    const listBtn = el.querySelector('[data-hero-list]');
    listBtn.classList.toggle('on', store.inWatchlist(m.id));
    listBtn.querySelector('span').textContent = store.inWatchlist(m.id) ? 'In watchlist' : 'Watchlist';
    el.querySelector('.hero-dots').innerHTML = items.map((_, k) => `<i class="${k === i ? 'on' : ''}"></i>`).join('');
    el.querySelector('.hero-body').classList.remove('swap'); void el.offsetWidth; el.querySelector('.hero-body').classList.add('swap');
  };
  const tick = () => { clearInterval(timer); timer = setInterval(() => { if (!el.contains(document.activeElement) && document.contains(el) && !document.hidden) show(i + 1); }, 9000); };
  el.querySelector('[data-hero-list]').addEventListener('click', () => { if (items[i]) { store.toggleWatchlist(items[i]); show(i); sound('select'); } });
  el.addEventListener('keydown', (e) => {
    if (!items.length || !e.target.closest('.hero-actions')) return;
    const atEdge = (e.key === 'ArrowLeft' && e.target === el.querySelector('.hero-actions > :first-child')) ||
      (e.key === 'ArrowRight' && e.target === el.querySelector('.hero-actions > :last-child'));
    if (atEdge) { e.preventDefault(); show(i + (e.key === 'ArrowRight' ? 1 : -1)); tick(); }
  });
  el.setItems = (metas, type) => {
    if (items.length) return;
    items = metas.filter((m) => m.background).slice(0, 6).map((m) => ({ ...m, type: m.type || type }));
    if (!items.length) return;
    el.classList.add('ready');
    show(0); tick();
  };
  return el;
}

// ---------- Views ----------

const VIEWS = {};

VIEWS.profiles = (node) => {
  node.dataset.keep = '';
  let managing = false;
  const draw = () => {
    const { profiles } = store.get();
    node.innerHTML = `<div class="profiles">
      <h1 class="who">${profiles.length ? "Who's watching?" : 'Welcome to Neon Tokyo'}</h1>
      ${profiles.length ? '' : '<p class="who-sub">Make a profile to start. Each one keeps its own history, watchlist and look; addons are shared. In Settings you can then sign the profile in with Stremio.</p>'}
      <div class="profile-grid">
        ${profiles.map((p) => `<button class="profile ${managing ? 'managing' : ''}" data-pick="${p.id}" data-key="p:${p.id}">${avatar(p, 'lg')}<span>${esc(p.name)}</span>${managing ? '<em>Edit</em>' : ''}</button>`).join('')}
        <button class="profile add" data-add data-key="p:add"><span class="avatar lg add">+</span><span>Add profile</span></button>
      </div>
      ${profiles.length ? `<button class="btn ghost manage" data-manage data-key="p:manage">${managing ? 'Done' : 'Manage profiles'}</button>` : ''}
    </div>`;
  };
  node._refresh = draw;
  draw();
  node.addEventListener('click', async (e) => {
    const pick = e.target.closest('[data-pick]');
    if (pick) {
      const p = store.get().profiles.find((x) => x.id === pick.dataset.pick);
      if (managing) { await editProfileDialog(p); draw(); nav.focusFirst(node); return; }
      sound('select');
      pick.classList.add('chosen');
      store.selectProfile(p.id);
      applyTheme(); updateBadge();
      if (sync.linked()) sync.start();
      viewCache.forEach((v, k) => { if (k !== '#/profiles') viewCache.delete(k); });
      setTimeout(() => { location.hash = '#/home'; }, 260);
      return;
    }
    if (e.target.closest('[data-add]')) { const p = await editProfileDialog(null); draw(); if (p) nav.focusEl(node.querySelector(`[data-pick="${p.id}"]`)); return; }
    if (e.target.closest('[data-manage]')) { managing = !managing; draw(); nav.focusEl(node.querySelector('[data-manage]')); }
  });
};

function editProfileDialog(p) {
  return modal((close) => {
    let color = p ? p.color : store.AVATAR_COLORS[store.get().profiles.length % store.AVATAR_COLORS.length];
    const el = html(`<form class="panel profile-edit">
      <h2>${p ? 'Edit profile' : 'New profile'}</h2>
      <div class="pe-preview"></div>
      <label class="field"><span>Name</span><input name="name" maxlength="20" autocomplete="off" value="${esc(p ? p.name : '')}" placeholder="Adrian" data-autofocus></label>
      <div class="swatches">${store.AVATAR_COLORS.map((c) => `<button type="button" class="swatch" style="--c:${c}" data-c="${c}" aria-label="Colour ${c}"></button>`).join('')}</div>
      <div class="panel-actions">
        ${p ? '<button type="button" class="btn danger" data-del>Delete</button>' : ''}
        <button type="button" class="btn ghost" data-cancel>Cancel</button>
        <button class="btn primary">Save</button>
      </div>
    </form>`);
    const name = el.querySelector('input');
    const paint = () => {
      el.querySelector('.pe-preview').innerHTML = avatar({ name: name.value || '?', color }, 'lg');
      el.querySelectorAll('.swatch').forEach((s) => s.classList.toggle('on', s.dataset.c === color));
    };
    paint();
    name.addEventListener('input', paint);
    el.addEventListener('click', (e) => {
      const s = e.target.closest('.swatch');
      if (s) { color = s.dataset.c; paint(); }
      if (e.target.closest('[data-cancel]')) close(null);
      if (e.target.closest('[data-del]') && confirm(`Delete ${p.name}? Their history and watchlist go too.`)) { store.removeProfile(p.id); updateBadge(); close(null); }
    });
    el.addEventListener('submit', (e) => {
      e.preventDefault();
      if (p) { store.editProfile(p.id, { name: name.value.trim() || p.name, color }); updateBadge(); close(p); }
      else close(store.addProfile(name.value, color));
    });
    return el;
  });
}

function browseView(filter) {
  return (node) => {
    node.dataset.keep = '';
    const { addons } = store.get();
    const h = filter === 'all' ? hero() : null;
    if (h) node.append(h);
    else node.append(html(`<header class="page-head"><h1>${filter === 'movie' ? 'Movies' : 'Series'}</h1><span class="page-jp">${filter === 'movie' ? '映画' : 'シリーズ'}</span></header>`));

    const personal = html('<div class="personal"></div>');
    node.append(personal);
    const drawPersonal = () => {
      personal.replaceChildren();
      const st = store.get();
      const pid = st.activeProfile;
      let cw = core.continueWatching(st.history, st.nextUp, pid);
      if (filter !== 'all') cw = cw.filter((c) => (filter === 'movie' ? c.kind !== 'series' : c.kind === 'series'));
      if (cw.length) {
        const row = rowShell('Continue watching', 'cw');
        row.classList.add('row-wide');
        row.querySelector('.row-track').innerHTML = cw.slice(0, 20).map(wideCard).join('');
        personal.append(row);
      }
      let wl = st.watchlist.filter((w) => w.profile_id === pid).sort((a, b) => b.added_at_ms - a.added_at_ms);
      if (filter !== 'all') wl = wl.filter((w) => w.type === filter);
      if (wl.length) {
        const row = rowShell('Watchlist', 'wl');
        row.querySelector('.row-track').innerHTML = wl.map((w) => card({ id: w.id, type: w.type, name: w.name, poster: w.poster })).join('');
        personal.append(row);
      }
    };
    drawPersonal();
    node._refresh = drawPersonal;

    if (!addons.length) {
      node.append(html(`<div class="empty"><h2>No addons yet</h2><p>Neon Tokyo gets its films and series from Stremio addons.</p><a class="btn primary" href="#/addons">Add addons</a></div>`));
      return;
    }
    let heroFed = false, first = true;
    for (const addon of addons) {
      if (isServiceAddon(addon) && filter === 'all') {
        const sr = servicesRow(addon);
        if (sr) node.append(sr);
      }
      for (const c of addon.manifest.catalogs || []) {
        if (!core.isBrowsable(c)) continue;
        if (filter !== 'all' && c.type !== filter) continue;
        if (isServiceAddon(addon) && SERVICE_CODES.has(c.id)) continue; // behind the service cards
        const row = catalogRow(addon, c, {}, { wide: first && c.type !== 'anime' });
        if (c.type !== 'anime') first = false;
        if (h && !heroFed && c.type !== 'anime') {
          heroFed = true;
          row.addEventListener('loaded', (e) => h.setItems(e.detail, c.type), { once: true });
          node.append(row);
          row._load && (row._load(), row._load = null);
          continue;
        }
        node.append(row);
        observeRow(row);
      }
    }
  };
}

VIEWS.home = browseView('all');
VIEWS.movies = browseView('movie');
VIEWS.series = browseView('series');

VIEWS.service = (node, r) => {
  node.dataset.keep = '';
  const [base, code] = r.args;
  const addon = store.get().addons.find((a) => a.base === base);
  const name = SERVICE_CODES.get(code) || code;
  const [c1, c2] = SERVICE_COLORS[code] || ['#FF3D9A', '#2A0F4F'];
  node.append(html(`<header class="page-head service-head" style="--s1:${c1};--s2:${c2}"><h1>${esc(name)}</h1></header>`));
  if (!addon) return;
  for (const c of addon.manifest.catalogs || []) {
    if (c.id !== code || !core.isBrowsable(c)) continue;
    const row = catalogRow(addon, c);
    row.querySelector('h2').textContent = typeWord(c.type);
    node.append(row); observeRow(row);
  }
};

VIEWS.catalog = (node, r) => {
  node.dataset.keep = '';
  const [base, type, id] = r.args;
  const addon = store.get().addons.find((a) => a.base === base);
  const c = addon && (addon.manifest.catalogs || []).find((x) => x.type === type && x.id === id);
  if (!c) { node.innerHTML = '<div class="empty"><h2>Catalog not found</h2></div>'; return; }
  const genres = core.genreOptions(c);
  node.innerHTML = `<header class="page-head"><h1>${esc(catalogTitle(addon, c))}</h1><span class="page-sub">${esc(addon.manifest.name)}</span></header>
    ${genres.length ? `<div class="chips" data-nav-section="genres"><button class="chip on" data-genre="">All</button>${genres.map((g) => `<button class="chip" data-genre="${esc(g)}">${esc(g)}</button>`).join('')}</div>` : ''}
    <div class="grid"></div><div class="grid-foot"><span class="spinner"></span></div>`;
  const grid = node.querySelector('.grid');
  const foot = node.querySelector('.grid-foot');
  let skip = 0, genre = '', busy = false, done = false, seen = new Set();
  const more = async () => {
    if (busy || done) return;
    busy = true;
    const g = genre;
    try {
      const metas = await core.fetchCatalog(base, type, id, { genre: g, skip: skip || '' });
      if (g !== genre) return;
      const fresh = metas.filter((m) => !seen.has(m.id));
      fresh.forEach((m) => seen.add(m.id));
      skip += metas.length;
      grid.insertAdjacentHTML('beforeend', fresh.map((m) => card({ ...m, type: m.type || type })).join(''));
      if (!fresh.length) { done = true; foot.innerHTML = grid.children.length ? '' : '<p class="muted">Nothing here.</p>'; }
    } catch { done = true; foot.innerHTML = '<p class="muted">Could not load more.</p>'; }
    finally { busy = false; }
  };
  const io = new IntersectionObserver((e) => { if (e[0].isIntersecting) more(); }, { rootMargin: '1200px' });
  io.observe(foot);
  node.addEventListener('click', (e) => {
    const chip = e.target.closest('[data-genre]');
    if (!chip) return;
    node.querySelectorAll('.chip').forEach((x) => x.classList.toggle('on', x === chip));
    genre = chip.dataset.genre; skip = 0; done = false; seen = new Set(); grid.innerHTML = ''; foot.innerHTML = '<span class="spinner"></span>';
    more();
  });
  // Pressing down on the last row asks for more even before the observer fires
  node.addEventListener('focusin', (e) => { if (e.target.closest('.grid') && grid.lastElementChild && grid.lastElementChild.getBoundingClientRect().top < innerHeight * 1.5) more(); });
};

VIEWS.search = (node, r) => {
  node.innerHTML = `<div class="search-bar" data-nav-section="search">
      ${icon('<circle cx="11" cy="11" r="6.5"/><path d="m16 16 4.5 4.5"/>')}
      <input type="search" enterkeyhint="search" placeholder="Search films and series" value="${esc(r.q.get('q') || '')}" data-autofocus aria-label="Search">
    </div><div class="results"></div>`;
  const input = node.querySelector('input');
  const results = node.querySelector('.results');
  let timer = 0, seq = 0;
  const run = () => {
    const q = input.value.trim();
    history.replaceState(null, '', '#/search' + (q ? '?q=' + encodeURIComponent(q) : ''));
    const my = ++seq;
    results.replaceChildren();
    if (q.length < 2) {
      results.innerHTML = '<div class="empty small"><p>Type at least two letters. Every addon with search is asked at once.</p></div>';
      return;
    }
    let any = false;
    const rows = [];
    for (const addon of store.get().addons) {
      for (const c of addon.manifest.catalogs || []) {
        if (!core.supportsSearch(c)) continue;
        const row = rowShell(`${catalogTitle(addon, c)} · ${addon.manifest.name}`, `s|${addon.base}|${c.type}|${c.id}`);
        results.append(row); rows.push(row);
        core.fetchCatalog(addon.base, c.type, c.id, { search: q }).then((metas) => {
          if (my !== seq) return;
          if (!metas.length) { row.remove(); return; }
          any = true;
          row.querySelector('.row-track').innerHTML = metas.slice(0, 40).map((m) => card({ ...m, type: m.type || c.type })).join('');
        }).catch(() => row.remove()).finally(() => {
          if (my === seq && !any && !results.querySelector('.row')) results.innerHTML = `<div class="empty small"><p>Nothing found for “${esc(q)}”.</p></div>`;
        });
      }
    }
    if (!rows.length) results.innerHTML = '<div class="empty small"><p>None of your addons can search. Cinemeta can: add it under Addons.</p></div>';
  };
  input.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(run, 380); });
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') { clearTimeout(timer); run(); input.blur(); nav.focusFirst(results); } });
  run();
};

// ---------- Details ----------

function metaAddon(type, id) {
  const { addons } = store.get();
  return addons.find((a) => core.serves(a.manifest, 'meta', type, id)) || null;
}

async function loadMeta(type, id) {
  const tried = [];
  const first = metaAddon(type, id);
  if (first) tried.push(first.base);
  for (const a of store.get().addons) if (!tried.includes(a.base) && core.serves(a.manifest, 'meta', type, id)) tried.push(a.base);
  if (!tried.includes(core.CINEMETA_URL) && /^tt\d+/.test(id)) tried.push(core.CINEMETA_URL);
  let lastErr;
  for (const base of tried) {
    try { return await core.fetchMeta(base, type, id); } catch (e) { lastErr = e; }
  }
  throw lastErr || new Error('No addon has details for this title');
}

VIEWS.detail = (node, r) => {
  const [type, id] = r.args;
  node.innerHTML = `<div class="detail loading"><div class="detail-art"></div><div class="detail-shade"></div><div class="detail-body"><div class="spinner big"></div></div></div>`;
  loadMeta(type, id).then((meta) => renderDetail(node, { ...meta, type: meta.type || type, id: meta.id || id }, r.q.get('resume') === '1'))
    .catch((e) => { node.innerHTML = `<div class="empty"><h2>Could not open this title</h2><p>${esc(e.message)}</p><button class="btn" onclick="history.back()">Back</button></div>`; });
};

function seriesVideos(meta) {
  return (meta.videos || []).map((v) => ({ ...v, season: v.season ?? 0, episode: v.episode ?? v.number ?? 0 }));
}

/** What the big button plays for a series: the started episode, the next one, or the first. */
function seriesTarget(meta) {
  const st = store.get();
  const pid = st.activeProfile;
  const videos = seriesVideos(meta);
  const regular = videos.filter((v) => v.season > 0 && v.episode > 0).sort((a, b) => a.season - b.season || a.episode - b.episode);
  const byPid = (pbid) => videos.find((v) => core.episodePlaybackId(meta.id, v) === pbid);
  const started = st.history.filter((e) => e.profile_id === pid && !e.deleted && core.canonicalSeriesId(e.id) === meta.id)
    .sort((a, b) => b.updated_at_ms - a.updated_at_ms);
  const last = started[0];
  if (last && !last.watched && byPid(last.id)) return { video: byPid(last.id), resume: last.position_ms >= 10000 || core.resumeFraction(last) > 0 };
  if (last && last.watched) {
    const next = core.findNextEpisode(meta.id, last.id, videos);
    if (next) return { video: next, resume: false, next: true };
  }
  return { video: regular[0] || videos[0] || null, resume: false };
}

function renderDetail(node, meta, focusResume) {
  const isSeries = meta.type === 'series' || (meta.videos && meta.videos.length > 1);
  const e = !isSeries ? store.entry(meta.id) : null;
  const share = core.resumeFraction(e); // from Trakt: only how far, not the length
  const resumeMs = core.resumePositionMs(e) || (share ? 1 : 0);
  const runtime = meta.runtime || '';
  const facts = [meta.releaseInfo || meta.year, runtime, meta.imdbRating ? `<span class="imdb">IMDb</span> ${esc(meta.imdbRating)}` : ''].filter(Boolean);
  const trailer = (meta.trailerStreams || []).find((t) => t.ytId) || (meta.trailers || []).find((t) => t.source);
  const trailerId = trailer && (trailer.ytId || trailer.source);
  const cast = (meta.cast || (meta.links || []).filter((l) => l.category === 'Cast').map((l) => l.name)).slice(0, 8);
  const director = meta.director ? [].concat(meta.director) : (meta.links || []).filter((l) => l.category === 'Directors').map((l) => l.name);

  node.innerHTML = `<article class="detail">
    <div class="detail-art">${meta.background ? `<img src="${esc(imgUrl(meta.background, 1920))}" alt="" onload="this.classList.add('in')" onerror="this.remove()">` : ''}</div>
    <div class="detail-shade"></div>
    <div class="detail-body" data-nav-section="hero">
      <div class="detail-logo">${meta.logo ? `<img src="${esc(imgUrl(meta.logo, 800))}" alt="${esc(meta.name)}" onerror="this.outerHTML='<h1>${esc(meta.name).replace(/'/g, '&#39;')}</h1>'">` : `<h1>${esc(meta.name)}</h1>`}</div>
      <p class="facts">${facts.map((f) => `<span>${f}</span>`).join('')}${(meta.genres || []).slice(0, 4).map((g) => `<span class="genre">${esc(g)}</span>`).join('')}</p>
      <p class="desc" onclick="this.classList.toggle('open')">${esc(meta.description || '')}</p>
      <div class="detail-actions">
        <button class="btn primary big" data-play data-key="d:play">${icon('<path d="M7 4.5v15l12-7.5z" fill="currentColor"/>')}<span class="play-label"></span></button>
        ${trailerId ? `<button class="btn ghost" data-trailer="${esc(trailerId)}" data-key="d:trailer">${icon('<rect x="3" y="5" width="18" height="14" rx="3"/><path d="m10 9 5 3-5 3z" fill="currentColor"/>')}<span>Trailer</span></button>` : ''}
        <button class="btn ghost" data-list data-key="d:list"></button>
        ${!isSeries ? `<button class="btn ghost" data-watched data-key="d:watched"></button>` : ''}
        ${resumeMs ? '<button class="btn ghost" data-restart data-key="d:restart">' + icon('<path d="M4 12a8 8 0 1 0 2.4-5.7M4 4v4h4"/>') + '<span>From start</span></button>' : ''}
      </div>
      ${resumeMs && e.duration_ms ? `<div class="resume-bar"><span class="progress"><i style="width:${(e.position_ms / e.duration_ms * 100).toFixed(1)}%"></i></span><em>${share ? `${Math.round(share * 100)} % watched` : `${Math.round((e.duration_ms - e.position_ms) / 60000)} min left`}</em></div>` : ''}
      ${cast.length || director.length ? `<p class="credits">${director.length ? `<b>Director</b> ${esc(director.slice(0, 2).join(', '))}` : ''}${cast.length ? ` <b>Cast</b> ${esc(cast.join(', '))}` : ''}</p>` : ''}
    </div>
    ${isSeries ? '<section class="episodes" data-nav-section="episodes"><div class="seasons chips"></div><div class="ep-list"></div></section>' : ''}
  </article>`;

  const playBtn = node.querySelector('[data-play]');
  const listBtn = node.querySelector('[data-list]');
  const watchedBtn = node.querySelector('[data-watched]');
  const paintList = () => {
    const on = store.inWatchlist(meta.id);
    listBtn.classList.toggle('on', on);
    listBtn.innerHTML = icon(on ? '<path d="m5 12.5 4.5 4.5L19 7.5"/>' : '<path d="M12 5v14M5 12h14"/>') + `<span>${on ? 'In watchlist' : 'Watchlist'}</span>`;
  };
  const paintWatched = () => {
    if (!watchedBtn) return;
    const w = !!(store.entry(meta.id) || {}).watched;
    watchedBtn.classList.toggle('on', w);
    watchedBtn.innerHTML = icon('<circle cx="12" cy="12" r="8.5"/>' + (w ? '<path d="m8 12.3 2.8 2.8L16.2 9.6"/>' : '')) + `<span>${w ? 'Watched' : 'Mark watched'}</span>`;
  };
  paintList(); paintWatched();

  let season = null;
  const paintPlay = () => {
    const label = playBtn.querySelector('.play-label');
    if (isSeries) {
      const t = seriesTarget(meta);
      if (!t.video) { label.textContent = 'Sources'; return; }
      const ep = `S${t.video.season}:E${t.video.episode}`;
      label.textContent = t.resume ? `Resume ${ep}` : t.next ? `Next ${ep}` : `Play ${ep}`;
      if (season === null) season = t.video.season;
    } else {
      const en = store.entry(meta.id);
      label.textContent = core.resumePositionMs(en) || core.resumeFraction(en) ? 'Resume' : 'Play';
    }
  };
  paintPlay();

  const playItem = (video) => {
    const streamId = video ? core.episodeStreamId(meta.id, video) : meta.id;
    const playbackId = video ? core.episodePlaybackId(meta.id, video) : meta.id;
    openSources({ meta, video, streamId, playbackId, onClosed: () => { paintPlay(); paintWatched(); if (isSeries) drawEpisodes(); } });
  };

  playBtn.addEventListener('click', () => { sound('select'); playItem(isSeries ? seriesTarget(meta).video : null); });
  listBtn.addEventListener('click', () => { store.toggleWatchlist(meta); paintList(); sound('select'); });
  if (watchedBtn) watchedBtn.addEventListener('click', () => { const w = !(store.entry(meta.id) || {}).watched; store.setWatched(meta.id, meta.type, meta.name, w); paintWatched(); paintPlay(); });
  const restart = node.querySelector('[data-restart]');
  if (restart) restart.addEventListener('click', () => openSources({ meta, video: null, streamId: meta.id, playbackId: meta.id, fromStart: true, onClosed: () => { paintPlay(); paintWatched(); } }));
  const tr = node.querySelector('[data-trailer]');
  if (tr) tr.addEventListener('click', () => openTrailer(tr.dataset.trailer));

  // Episodes
  const videos = seriesVideos(meta);
  const seasons = [...new Set(videos.map((v) => v.season))].sort((a, b) => (a === 0) - (b === 0) || a - b);
  function drawEpisodes() {
    if (!isSeries) return;
    const box = node.querySelector('.seasons');
    if (season === null || !seasons.includes(season)) season = seasons.find((s) => s > 0) ?? seasons[0];
    box.innerHTML = seasons.map((s) => `<button class="chip ${s === season ? 'on' : ''}" data-season="${s}" data-key="season:${s}">${s === 0 ? 'Specials' : 'Season ' + s}</button>`).join('');
    const today = new Date().toISOString().slice(0, 10);
    const list = videos.filter((v) => v.season === season).sort((a, b) => a.episode - b.episode);
    node.querySelector('.ep-list').innerHTML = list.map((v) => {
      const pbid = core.episodePlaybackId(meta.id, v);
      const en = store.entry(pbid);
      const aired = !v.released || v.released.slice(0, 10) <= today;
      const prog = en && en.duration_ms && !en.watched ? en.position_ms / en.duration_ms : 0;
      return `<button class="episode ${en && en.watched ? 'watched' : ''} ${aired ? '' : 'upcoming'}" data-ep="${esc(pbid)}" data-key="ep:${esc(pbid)}">
        <span class="ep-thumb">${v.thumbnail ? `<img src="${esc(imgUrl(v.thumbnail, 400))}" alt="" loading="lazy" onload="this.classList.add('in')" onerror="this.remove()">` : ''}<b>${v.episode}</b>${prog ? `<span class="progress"><i style="width:${(prog * 100).toFixed(1)}%"></i></span>` : ''}${en && en.watched ? '<span class="check">✓</span>' : ''}</span>
        <span class="ep-text"><strong>${esc(v.title || v.name || 'Episode ' + v.episode)}</strong><small>${v.released ? new Date(v.released).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }) : ''}${aired ? '' : ' · upcoming'}</small><span>${esc(v.overview || v.description || '')}</span></span>
      </button>`;
    }).join('');
  }
  if (isSeries) {
    drawEpisodes();
    node.querySelector('.episodes').addEventListener('click', (ev) => {
      const s = ev.target.closest('[data-season]');
      if (s) { season = +s.dataset.season; drawEpisodes(); nav.focusEl(node.querySelector(`[data-season="${season}"]`), { scroll: false }); return; }
      const epb = ev.target.closest('[data-ep]');
      if (epb) { const v = videos.find((x) => core.episodePlaybackId(meta.id, x) === epb.dataset.ep); if (v) { sound('select'); playItem(v); } }
    });
    node.querySelector('.episodes').addEventListener('contextmenu', (ev) => {
      const epb = ev.target.closest('[data-ep]');
      if (!epb) return;
      ev.preventDefault();
      const en = store.entry(epb.dataset.ep);
      store.setWatched(epb.dataset.ep, 'series', meta.name, !(en && en.watched));
      drawEpisodes(); paintPlay();
    });
  }
  node._refresh = () => { paintPlay(); paintWatched(); paintList(); drawEpisodes(); };
  requestAnimationFrame(() => {
    if (focusResume || document.documentElement.classList.contains('kbd')) nav.focusEl(playBtn, { scroll: false });
  });
}

function openTrailer(ytId) {
  modal((close) => html(`<div class="panel trailer"><iframe src="https://www.youtube-nocookie.com/embed/${encodeURIComponent(ytId)}?autoplay=1&rel=0" allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen title="Trailer"></iframe><button class="btn ghost" data-autofocus onclick="this.dispatchEvent(new Event('closeme',{bubbles:true}))">Close</button></div>`), { cls: 'wide' });
  document.addEventListener('closeme', () => closeTopModal(), { once: true });
}

// ---------- Sources ----------

const UNPLAYABLE_WHY = {
  torrent: 'Torrent: plays in the TV app',
  headers: 'Needs headers a browser cannot send',
  container: 'This browser cannot open this file',
};

function whyNot(s) {
  if (s.isTorrent) return UNPLAYABLE_WHY.torrent;
  if (s.url && !s.webPlayable && s.container !== 'unknown' && !core.browserCanPlay(s.container)) return `${s.container.toUpperCase()}: ${isApple ? 'try VLC or Infuse' : 'try Chrome or Edge'}`;
  if (s.url && !s.webPlayable) return UNPLAYABLE_WHY.headers;
  return '';
}

function openSources(ctx) {
  const { meta, video } = ctx;
  const addons = store.get().addons.filter((a) => core.serves(a.manifest, 'stream', video ? 'series' : meta.type, ctx.streamId) || core.serves(a.manifest, 'stream', meta.type, ctx.streamId));
  modal((close) => {
    const el = html(`<aside class="panel sources">
      <header><h2>${esc(meta.name)}</h2>${video ? `<p>${esc(core.episodeTitle(video))}</p>` : ''}</header>
      <div class="src-list" role="list"></div>
      <footer class="src-foot"></footer>
    </aside>`);
    const list = el.querySelector('.src-list');
    const foot = el.querySelector('.src-foot');
    if (!addons.length) {
      list.innerHTML = `<div class="empty small"><p>None of your addons give sources. The starter addons only give catalogs and info: add your own source addon under Addons, or copy your Stremio addons there in one go.</p><a class="btn primary" href="#/addons" data-autofocus>Open Addons</a></div>`;
      return el;
    }
    list.innerHTML = '<div class="src-loading"><span class="spinner"></span><span>Asking ' + addons.length + ' addon' + (addons.length > 1 ? 's' : '') + '…</span></div>';
    let all = [];
    let pending = addons.length;
    const draw = () => {
      const ranked = core.rankStreams(all);
      const hide = settings().hideUnplayable;
      const shown = hide ? ranked.filter((s) => s.webPlayable || (!s.isTorrent && (s.url || s.externalUrl || s.ytId))) : ranked;
      const torrents = ranked.filter((s) => s.isTorrent).length;
      const hadFocus = el.contains(document.activeElement) ? document.activeElement.dataset.key : null;
      list.innerHTML = shown.map((s, i) => {
        const ext = chosenPlayer()[2] && s.url;
        const why = s.webPlayable || s.ytId || s.externalUrl || ext ? '' : whyNot(s);
        const lines = (s.title || '').split('\n').filter(Boolean);
        return `<div class="src ${why ? 'dim' : ''}" role="listitem">
          <button class="src-main" data-src="${i}" data-key="src:${esc(s.addonName + s.name + s.title + (s.url || s.infoHash || ''))}" ${s.isTorrent ? 'disabled' : ''}>
            <span class="q ${s.quality >= 4 ? 'uhd' : ''}">${esc(s.qualityLabel || '—')}</span>
            <span class="src-text"><strong>${esc(s.name.replace(/\n/g, ' · '))}</strong><small>${esc(lines.slice(0, 2).join(' · '))}</small>
              <span class="tags">${s.tags.map((t) => `<i>${t}</i>`).join('')}${s.sizeLabel ? `<i class="size">${esc(s.sizeLabel)}</i>` : ''}<i class="from">${esc(s.addonName)}</i>${why ? `<i class="why">${esc(why)}</i>` : ''}</span></span>
          </button>
          ${why && s.url ? `<div class="src-alt">${isApple ? `<a class="btn tiny" href="vlc://${esc(s.url)}" data-key="vlc:${i}">VLC</a><a class="btn tiny" href="infuse://x-callback-url/play?url=${encodeURIComponent(s.url)}" data-key="inf:${i}">Infuse</a>` : ''}<button class="btn tiny" data-copy="${i}" data-key="cp:${i}">Copy link</button></div>` : ''}
        </div>`;
      }).join('') || (pending ? '' : '<div class="empty small"><p>No sources found.</p></div>');
      if (pending) list.insertAdjacentHTML('beforeend', `<div class="src-loading"><span class="spinner"></span><span>${pending} addon${pending > 1 ? 's' : ''} still answering…</span></div>`);
      foot.textContent = torrents ? (torrents > 1 ? `${torrents} torrent sources play only in the Neon Tokyo TV app.` : 'One torrent source plays only in the Neon Tokyo TV app.') : '';
      el._shown = shown;
      const again = hadFocus && el.querySelector(`[data-key="${CSS.escape(hadFocus)}"]`);
      if (again) again.focus({ preventScroll: true });
      else if (!el.contains(document.activeElement) || document.activeElement === el) nav.focusFirst(el);
    };
    for (const a of addons) {
      core.fetchStreams(a.base, video ? 'series' : meta.type, ctx.streamId)
        .then((raw) => { all = all.concat(raw.map((s) => core.parseStream(a.manifest.name, s)).filter(Boolean)); })
        .catch(() => {})
        .finally(() => { pending--; draw(); });
    }
    el.addEventListener('click', async (e) => {
      const cp = e.target.closest('[data-copy]');
      if (cp) {
        const s = el._shown[+cp.dataset.copy];
        try { await navigator.clipboard.writeText(s.url); toast('Link copied'); } catch { prompt('Copy the link', s.url); }
        return;
      }
      const b = e.target.closest('[data-src]');
      if (!b) return;
      const s = el._shown[+b.dataset.src];
      if (s.ytId) { close(); openTrailer(s.ytId); return; }
      if (s.externalUrl && !s.url) { window.open(s.externalUrl, '_blank', 'noopener'); return; }
      if (!s.url) return;
      if (!s.webPlayable && !chosenPlayer()[2] && !confirm(`${whyNot(s)}. Try anyway?`)) return;
      sound('select');
      close('play');
      const others = core.rankStreams(all);
      openPlayer({ ...ctx, stream: s, alternatives: others, onClosed: ctx.onClosed });
    });
    return el;
  }, { cls: 'side' }).then((v) => { if (v !== 'play' && ctx.onClosed) ctx.onClosed(); });
}

/** Called by the player when an episode ends: the next one's sources, same bingeGroup first. */
export async function nextEpisodeStream(ctx) {
  const { meta, video } = ctx;
  if (!video) return null;
  const next = core.findNextEpisode(meta.id, ctx.playbackId, seriesVideos(meta));
  if (!next) return null;
  const streamId = core.episodeStreamId(meta.id, next);
  const addons = store.get().addons.filter((a) => core.serves(a.manifest, 'stream', 'series', streamId));
  const results = await Promise.allSettled(addons.map(async (a) => (await core.fetchStreams(a.base, 'series', streamId)).map((s) => core.parseStream(a.manifest.name, s)).filter(Boolean)));
  const all = core.rankStreams(results.flatMap((r) => (r.status === 'fulfilled' ? r.value : [])));
  const same = ctx.stream.bingeGroup && all.find((s) => s.webPlayable && s.bingeGroup === ctx.stream.bingeGroup);
  const pick = same || all.find((s) => s.webPlayable);
  if (!pick) return { video: next, streamId, playbackId: core.episodePlaybackId(meta.id, next), stream: null };
  return { ...ctx, video: next, streamId, playbackId: core.episodePlaybackId(meta.id, next), stream: pick, alternatives: all, fromStart: false };
}

export { openSources, seriesVideos };

// ---------- Addons ----------

VIEWS.addons = (node) => {
  const draw = () => {
    const { addons } = store.get();
    node.innerHTML = `<header class="page-head"><h1>Addons</h1><span class="page-sub">Shared by every profile</span></header>
      <form class="add-addon panel-inline" data-nav-section="add">
        <input name="url" placeholder="Paste an addon link (…/manifest.json or stremio://…)" autocomplete="off" inputmode="url" aria-label="Addon link">
        <button class="btn primary">Install</button>
      </form>
      <div class="addon-tools">
        <button class="btn ghost" data-stremio>${icon('<path d="M4 12h10M10 6l6 6-6 6"/><path d="M20 4v16"/>')}<span>${sync.linked() ? 'Get my addons from Stremio' : 'Sign in with Stremio to get my addons'}</span></button>
        <button class="btn ghost" data-starter>${icon('<path d="M4 12a8 8 0 1 0 2.4-5.7M4 4v4h4"/>')}<span>Restore starter addons</span></button>
      </div>
      <ul class="addon-list">${addons.map((a, i) => {
        const m = a.manifest;
        const res = (m.resources || []).map((r) => (typeof r === 'string' ? r : r.name));
        return `<li class="addon">
          <span class="addon-logo">${m.logo ? `<img src="${esc(imgUrl(m.logo, 120))}" alt="" onerror="this.remove()">` : ''}<b>${esc((m.name || '?')[0])}</b></span>
          <span class="addon-text"><strong>${esc(m.name)} <small>v${esc(m.version || '')}</small></strong><span>${esc(m.description || '')}</span>
            <span class="tags">${res.map((r) => `<i>${esc(r)}</i>`).join('')}${(m.types || []).map((t) => `<i class="t">${esc(t)}</i>`).join('')}</span></span>
          <span class="addon-actions">
            <button class="icon-btn" data-up="${i}" aria-label="Move up" ${i === 0 ? 'disabled' : ''}>${icon('<path d="m6 15 6-6 6 6"/>')}</button>
            <button class="icon-btn" data-down="${i}" aria-label="Move down" ${i === addons.length - 1 ? 'disabled' : ''}>${icon('<path d="m6 9 6 6 6-6"/>')}</button>
            ${m.behaviorHints && m.behaviorHints.configurable ? `<a class="icon-btn" href="${esc(a.base)}/configure" target="_blank" rel="noopener" aria-label="Configure">${icon('<circle cx="12" cy="12" r="3"/><path d="M12 3v3M12 18v3M3 12h3M18 12h3"/>')}</a>` : ''}
            <button class="icon-btn danger" data-rm="${i}" aria-label="Remove">${icon('<path d="M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13"/>')}</button>
          </span>
        </li>`;
      }).join('') || '<li class="empty small"><p>No addons yet.</p></li>'}</ul>
      <p class="muted note">Addons are the same as in Stremio and on your TV. Torrent sources are shown but play only in the TV app.</p>`;
  };
  draw();
  node.addEventListener('submit', async (e) => {
    e.preventDefault();
    const input = e.target.querySelector('input');
    const btn = e.target.querySelector('button');
    btn.disabled = true; btn.textContent = 'Installing…';
    try { const m = await store.installAddon(input.value); toast(`${m.name} installed`); invalidateBrowse(); draw(); }
    catch (err) { toast(`Could not install: ${err.message}`, 'bad'); btn.disabled = false; btn.textContent = 'Install'; }
  });
  node.addEventListener('click', async (e) => {
    const { addons } = store.get();
    const t = e.target.closest('button');
    if (!t) return;
    if (t.dataset.up) { store.moveAddon(addons[+t.dataset.up].base, -1); invalidateBrowse(); draw(); nav.focusEl(node.querySelector(`[data-up="${+t.dataset.up - 1}"]`) || node.querySelector(`[data-down="${+t.dataset.up - 1}"]`)); }
    else if (t.dataset.down) { store.moveAddon(addons[+t.dataset.down].base, 1); invalidateBrowse(); draw(); nav.focusEl(node.querySelector(`[data-down="${+t.dataset.down + 1}"]`) || node.querySelector(`[data-up="${+t.dataset.down + 1}"]`)); }
    else if (t.dataset.rm) { const a = addons[+t.dataset.rm]; if (confirm(`Remove ${a.manifest.name}?`)) { store.removeAddon(a.base); invalidateBrowse(); draw(); } }
    else if ('starter' in t.dataset) { store.update((s) => { s.seededAt = 0; }); await store.seedAddons(); invalidateBrowse(); draw(); toast('Starter addons are back'); }
    else if ('stremio' in t.dataset) {
      if (sync.linked()) { await sync.syncNow({ full: true }); toast('Addons are up to date with Stremio'); } else await stremioSignIn();
      invalidateBrowse(); draw();
    }
  });
};

function invalidateBrowse() { ['#/home', '#/movies', '#/series'].forEach((k) => viewCache.delete(k)); }

/** Signs the active profile in to Stremio. The password goes only to Stremio and is not kept. */
function stremioSignIn() {
  return modal((close) => {
    const el = html(`<form class="panel">
      <h2>Sign in with Stremio</h2>
      <p class="muted">Links ${esc(store.profile().name)} to your Stremio account: your addons come in, and your library and what you have watched stay in step with Stremio and the TV. Your password goes only to Stremio and is not kept.</p>
      <label class="field"><span>Email</span><input name="email" type="email" autocomplete="username" required data-autofocus></label>
      <label class="field"><span>Password</span><input name="password" type="password" autocomplete="current-password" required></label>
      <p class="form-error" hidden></p>
      <div class="panel-actions"><button type="button" class="btn ghost" data-cancel>Cancel</button><button class="btn primary">Sign in</button></div>
    </form>`);
    el.querySelector('[data-cancel]').addEventListener('click', () => close());
    el.addEventListener('submit', async (e) => {
      e.preventDefault();
      const err = el.querySelector('.form-error');
      const btn = el.querySelector('.btn.primary');
      btn.disabled = true; btn.textContent = 'Signing in…'; err.hidden = true;
      try {
        await sync.signIn(el.email.value.trim(), el.password.value);
        invalidateBrowse();
        toast('Signed in to Stremio');
        close(true);
      } catch (x) {
        err.textContent = x.message === 'Failed to fetch' ? 'Stremio could not be reached from this browser.' : x.message;
        err.hidden = false; btn.disabled = false; btn.textContent = 'Sign in';
      }
    });
    return el;
  });
}

function accountSection() {
  const p = store.profile();
  const st = sync.syncStatus();
  if (!sync.linked(p)) {
    return `<p class="muted">Sign in with your Stremio account to bring in your addons and keep your library and watch progress in step with Stremio and the TV.</p>
      <div class="row-btns"><button class="btn primary" data-signin>${icon('<circle cx="12" cy="8" r="4"/><path d="M4 20c1.5-4 4.5-6 8-6s6.5 2 8 6"/>')}<span>Sign in with Stremio</span></button></div>`;
  }
  const when = st.at ? new Date(st.at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';
  const line = st.state === 'syncing' ? 'Syncing…' : st.state === 'error' ? `Not synced: ${esc(st.error)}` : when ? `Synced at ${when}` : 'Signed in';
  return `<div class="account"><span class="avatar" style="--c:${esc(p.color)}">${esc((p.stremio.email || '?')[0].toUpperCase())}</span>
      <span><strong>${esc(p.stremio.email)}</strong><small class="${st.state === 'error' ? 'bad' : ''}">${line}</small></span></div>
    <div class="row-btns"><button class="btn ghost" data-syncnow>${icon('<path d="M4 12a8 8 0 0 1 14-5.3M20 12a8 8 0 0 1-14 5.3M18 3v4h-4M6 21v-4h4"/>')}<span>Sync now</span></button><button class="btn ghost danger" data-signout>Sign out</button></div>`;
}

function traktSection() {
  if (!trakt.available()) return '<p class="muted">This build was made without a Trakt key.</p>';
  const st = trakt.traktStatus();
  const code = trakt.signInCode();
  if (code) {
    const url = code.url.replace(/^https?:\/\//, '');
    return `<div class="trakt-code">
        <p class="muted">On your phone or here, go to <b>${esc(url)}</b> and type</p>
        <strong class="code-sign">${esc(code.userCode)}</strong>
        <p class="muted small">This page signs in by itself once the code is entered.</p>
      </div>
      <div class="row-btns"><a class="btn primary" href="${esc(code.url)}/${encodeURIComponent(code.userCode)}" target="_blank" rel="noopener" data-key="t:open">Open ${esc(url)}</a><button class="btn ghost" data-trakt-cancel>Cancel</button></div>`;
  }
  if (st.state === 'code') return '<p class="muted"><span class="spinner"></span> Getting a code from Trakt…</p>';
  if (!trakt.linked()) {
    return `<p class="muted">Scrobbles what you watch here and brings in what you watched in other apps (the TV, Omni, your phone), for ${esc(store.profile().name)}.</p>
      ${st.error ? `<p class="form-error">${esc(st.error)}</p>` : ''}
      <div class="row-btns"><button class="btn primary" data-trakt-in>${icon('<circle cx="12" cy="12" r="8.5"/><path d="m8.5 12 2.5 2.5 4.5-5"/>')}<span>Connect Trakt</span></button></div>`;
  }
  const name = trakt.username();
  const line = st.state === 'syncing' ? 'Syncing…' : st.error ? `Not synced: ${esc(st.error)}` : esc(st.note || 'Signed in');
  return `<div class="account"><span class="avatar trakt-mark">t</span>
      <span><strong>${esc(name || 'Trakt')}</strong><small class="${st.error ? 'bad' : ''}">${line}</small></span></div>
    <div class="row-btns"><button class="btn ghost" data-trakt-sync>${icon('<path d="M4 12a8 8 0 0 1 14-5.3M20 12a8 8 0 0 1-14 5.3M18 3v4h-4M6 21v-4h4"/>')}<span>Sync now</span></button><button class="btn ghost danger" data-trakt-out>Disconnect</button></div>`;
}

function updatesSection(note = '') {
  return `<div class="account"><span class="avatar ver-mark">${updateReady() ? '★' : '✓'}</span>
      <span><strong>Neon Tokyo Web ${esc(VERSION)}</strong><small>${esc(note || (updateReady() ? 'A new version is ready' : 'Updates download by themselves in the background'))}</small></span></div>
    <div class="row-btns">${updateReady() ? '<button class="btn primary" data-update-now>Restart into the new version</button>' : '<button class="btn ghost" data-update-check>Check for updates</button>'}</div>`;
}

// ---------- Settings ----------

VIEWS.settings = (node) => {
  const draw = () => {
    const p = store.profile();
    const s = settings();
    node.innerHTML = `<header class="page-head"><h1>Settings</h1><span class="page-sub">Neon Tokyo Web ${esc(VERSION)}</span></header>
      <section class="set-group"><h2>Look for ${esc(p.name)}</h2>
        <div class="theme-pick">
          ${[['neon_tokyo', 'Neon Tokyo', 'Magenta and cyan glow'], ['deep_blue', 'Deep Blue', 'Clear glass, Apple TV-like']].map(([id, name, sub]) => `<button class="theme-card ${p.theme === id ? 'on' : ''}" data-theme="${id}"><span class="tc-swatch ${id}"></span><strong>${name}</strong><small>${sub}</small></button>`).join('')}
        </div>
      </section>
      <section class="set-group"><h2>Stremio account for ${esc(p.name)}</h2><div data-account>${accountSection()}</div></section>
      <section class="set-group"><h2>Trakt for ${esc(p.name)}</h2><div data-trakt>${traktSection()}</div></section>
      <section class="set-group"><h2>Updates</h2><div data-updates>${updatesSection()}</div></section>
      <section class="set-group"><h2>Video player</h2>
        <p class="muted">Films open in this player on this device. Your own app plays more file types (MKV, Dolby Vision, Atmos) and keeps playing when the screen locks.</p>
        <div class="player-pick">${externalPlayers().map(([id, name]) => `<button class="chip ${(s.player || 'builtin') === id ? 'on' : ''}" data-player="${id}">${esc(name)}</button>`).join('')}</div>
      </section>
      <section class="set-group"><h2>App</h2>
        ${toggle('intro', 'Startup animation', 'The neon sign when the app opens', s.intro)}
        ${toggle('sounds', 'Sounds', 'Soft synth blips when you move and choose', s.sounds)}
        ${toggle('hideUnplayable', 'Hide sources this browser cannot play', 'Torrents and files that need the TV app', s.hideUnplayable)}
      </section>
      <section class="set-group"><h2>Backup</h2>
        <p class="muted">Moves profiles, addons, history and watchlists to another browser or phone.</p>
        <div class="row-btns"><button class="btn ghost" data-export>Save backup file</button><label class="btn ghost file-btn">Load backup file<input type="file" accept="application/json,.json" data-import hidden></label></div>
      </section>
      <section class="set-group"><h2>Install</h2>
        ${installHelp()}
      </section>
      <section class="set-group"><h2>Apple TV</h2>
        <p class="muted">Apple TV has no web browser, so a web page cannot run on it by itself. Two ways to get Neon Tokyo there:</p>
        <ul class="bullets">
          <li><b>AirPlay from iPhone or iPad:</b> start a film here and tap the AirPlay button in the player. The film plays full screen on the Apple TV; the phone becomes the remote.</li>
          <li><b>The real Apple TV app:</b> the SwiftUI app for Apple TV and iPhone is being built from the same Neon Tokyo code.</li>
        </ul>
      </section>`;
  };
  const off = sync.onChange(() => { if (document.contains(node)) { const sec = node.querySelector('[data-account]'); if (sec) sec.innerHTML = accountSection(); } else off(); });
  // Redraw the Trakt part in place; keep the focus on the same control
  const paintTrakt = () => {
    const sec = node.querySelector('[data-trakt]');
    if (!sec) return;
    const had = sec.contains(document.activeElement);
    sec.innerHTML = traktSection();
    if (had) nav.focusEl(sec.querySelector('button,a'), { scroll: false });
  };
  const offT = trakt.onChange(() => { if (document.contains(node)) paintTrakt(); else offT(); });
  const paintUpdates = (note) => { const sec = node.querySelector('[data-updates]'); if (sec) sec.innerHTML = updatesSection(note); };
  const onUpdate = () => { if (document.contains(node)) paintUpdates(); else updateListeners.delete(onUpdate); };
  updateListeners.add(onUpdate);
  const toggle = (k, title, sub, on) => `<button class="toggle ${on ? 'on' : ''}" data-toggle="${k}" role="switch" aria-checked="${on}"><span><strong>${title}</strong><small>${sub}</small></span><i></i></button>`;
  draw();
  node.addEventListener('click', (e) => {
    const t = e.target.closest('button');
    if (!t) return;
    if (t.dataset.theme) { store.editProfile(store.profile().id, { theme: t.dataset.theme }); applyTheme(); draw(); nav.focusEl(node.querySelector(`[data-theme="${t.dataset.theme}"]`), { scroll: false }); sound('select'); }
    if (t.dataset.player) { store.update((s) => { s.settings.player = t.dataset.player; }); draw(); nav.focusEl(node.querySelector(`[data-player="${t.dataset.player}"]`), { scroll: false }); sound('select'); }
    if (t.dataset.toggle) { const k = t.dataset.toggle; store.update((s) => { s.settings[k] = !s.settings[k]; }); draw(); nav.focusEl(node.querySelector(`[data-toggle="${k}"]`), { scroll: false }); sound('select'); }
    if ('export' in t.dataset) {
      const blob = new Blob([store.exportJson()], { type: 'application/json' });
      const a = Object.assign(document.createElement('a'), { href: URL.createObjectURL(blob), download: `neon-tokyo-backup-${new Date().toISOString().slice(0, 10)}.json` });
      a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 2000);
    }
    if ('signin' in t.dataset) stremioSignIn().then(draw);
    if ('syncnow' in t.dataset) sync.syncNow({ full: true }).then(() => { invalidateBrowse(); draw(); });
    if ('signout' in t.dataset && confirm('Sign this profile out of Stremio? What is saved here stays.')) { sync.signOut(); draw(); }
    if ('install' in t.dataset && deferredInstall) { deferredInstall.prompt(); deferredInstall = null; }
    if ('traktIn' in t.dataset) trakt.signIn();
    if ('traktCancel' in t.dataset) trakt.cancel();
    if ('traktSync' in t.dataset) trakt.syncNow().then(() => { invalidateBrowse(); });
    if ('traktOut' in t.dataset && confirm('Disconnect Trakt from this profile? What is saved here stays.')) trakt.signOut();
    if ('updateNow' in t.dataset) applyUpdate();
    if ('updateCheck' in t.dataset) { t.disabled = true; t.textContent = 'Checking…'; checkForUpdate().then((note) => paintUpdates(note)); }
  });
  node.addEventListener('change', async (e) => {
    if (!e.target.matches('[data-import]')) return;
    const f = e.target.files[0];
    if (!f) return;
    try { store.importJson(await f.text()); viewCache.clear(); applyTheme(); updateBadge(); toast('Backup loaded'); location.hash = '#/profiles'; }
    catch (x) { toast(x.message, 'bad'); }
  });
};

let deferredInstall = null;
addEventListener('beforeinstallprompt', (e) => { e.preventDefault(); deferredInstall = e; });

function installHelp() {
  if (isStandalone) return '<p class="muted">Neon Tokyo is installed as an app on this device.</p>';
  if (/iPhone|iPad|iPod/.test(navigator.userAgent) || isApple) {
    return '<p class="muted">On iPhone and iPad: tap <b>Share</b> in Safari, then <b>Add to Home Screen</b>. Neon Tokyo then opens full screen like an app.</p>';
  }
  return `<p class="muted">Install Neon Tokyo as an app: it gets its own window and icon, and opens straight in.</p>${deferredInstall ? '<button class="btn primary" data-install>Install app</button>' : '<p class="muted">In Chrome or Edge: the install icon at the right end of the address bar.</p>'}`;
}

// ---------- Continue watching remove ----------

document.addEventListener('click', (e) => {
  const x = e.target.closest('[data-dismiss]');
  if (!x) return;
  e.preventDefault(); e.stopPropagation();
  store.dismissContinue(x.dataset.dismiss);
  const cardEl = x.closest('.card');
  const row = cardEl.closest('.row');
  cardEl.remove();
  if (row && !row.querySelector('.card')) row.remove();
}, true);

// ---------- The light that follows the focus ----------
// With keys: the focused card pops (CSS), the floor light glides under it and the skyline
// leans along the row. With a mouse: the card under the pointer does the same.

function leanFor(card) {
  const track = card.parentElement;
  if (track && track.classList.contains('row-track') && track.scrollWidth > track.clientWidth) {
    leanToward((card.offsetLeft + card.offsetWidth / 2) / track.scrollWidth);
  } else {
    const r = card.getBoundingClientRect();
    leanToward((r.left + r.width / 2) / innerWidth);
  }
}

function installFocusLight() {
  const root = document.documentElement;
  document.addEventListener('focusin', (e) => {
    if (!root.classList.contains('kbd')) return;
    const t = e.target;
    if (t.closest('.card,.btn,.chip,.episode,.src-main,.profile')) sound('move');
    if (t.classList.contains('card')) { floorUnder(t, 'on'); leanFor(t); }
  });
  let lit = null;
  const unlight = () => { if (lit) { lit.classList.remove('lit'); floorOff(lit); lit = null; } };
  document.addEventListener('pointerover', (e) => {
    if (e.pointerType !== 'mouse') return;
    const c = e.target.closest && e.target.closest('.card');
    if (c === lit) return;
    unlight();
    if (!c) return;
    lit = c;
    c.classList.add('lit');
    floorUnder(c, 'lit');
    leanFor(c);
  }, { passive: true });
  root.addEventListener('pointerleave', unlight);
  addEventListener('hashchange', unlight);
}

// ---------- Updates ----------
// The service worker keeps the app on the device. When a new version is published it is
// fetched in the background; a pill offers to switch to it (and it switches by itself the
// next time the app opens).

let waitingWorker = null;
const updateListeners = new Set();
export const updateReady = () => !!waitingWorker;

function offerUpdate(worker) {
  waitingWorker = worker;
  updateListeners.forEach((f) => f());
  if ($('#updatePill')) return;
  const pill = html(`<button id="updatePill" class="update-pill"><i></i><span>New version ready</span><b>Restart</b></button>`);
  pill.addEventListener('click', applyUpdate);
  document.body.append(pill);
  requestAnimationFrame(() => pill.classList.add('in'));
}

function applyUpdate() {
  if (!waitingWorker) { location.reload(); return; }
  if (playerOpen()) closePlayer();
  waitingWorker.postMessage('skip-waiting');
}

async function watchUpdates() {
  if (!('serviceWorker' in navigator) || location.protocol !== 'https:') return;
  let reloading = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => { if (!reloading && waitingWorker) { reloading = true; location.reload(); } });
  const reg = await navigator.serviceWorker.register('sw.js').catch(() => null);
  if (!reg) return;
  const track = (w) => {
    if (!w) return;
    const ready = () => { if (w.state === 'installed' && navigator.serviceWorker.controller) offerUpdate(w); };
    ready();
    w.addEventListener('statechange', ready);
  };
  track(reg.waiting);
  reg.addEventListener('updatefound', () => track(reg.installing));
  const check = () => reg.update().catch(() => {});
  setInterval(() => { if (!document.hidden) check(); }, 30 * 60000);
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') check(); });
}

export async function checkForUpdate() {
  if (!('serviceWorker' in navigator)) return 'This browser cannot update apps in the background';
  const reg = await navigator.serviceWorker.getRegistration();
  if (!reg) return 'Updates work once the app is opened from its web address';
  await reg.update().catch(() => {});
  await new Promise((r) => setTimeout(r, 1500));
  return waitingWorker || reg.waiting ? 'A new version is ready' : 'This is the newest version';
}

// ---------- Start ----------

async function start() {
  renderShell();
  applyTheme();
  nav.install({ onBack: back });
  addEventListener('hashchange', route);
  addEventListener('resize', () => setTab((parseHash().name)));
  installFocusLight();
  const introDone = settings().intro && !sessionStorage.getItem('nt.introShown') ? playIntro() : Promise.resolve();
  try { sessionStorage.setItem('nt.introShown', '1'); } catch { /* ignore */ }
  const probe = probeMetahub();
  if (!store.get().seededAt) {
    // First run: the starter addons and the image check, while the intro plays
    const seeding = store.seedAddons().catch(() => {});
    await Promise.race([Promise.all([probe, seeding]), new Promise((r) => setTimeout(r, 4500))]);
  }
  await introDone;
  if (!store.get().profiles.length) location.replace('#/profiles');
  route();
  store.refreshManifests();
  if (sync.linked()) sync.start();
  if (trakt.linked()) trakt.start();
  watchUpdates();
}

start();
