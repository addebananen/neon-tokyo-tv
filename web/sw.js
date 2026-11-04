// Keeps the app itself on the device so it opens instantly and offline. Addon answers always
// come from the network, and posters use the browser's normal cache.

const BUILD = '__BUILD__';
const SHELL = `nt-shell-${BUILD}`;
const FILES = [
  './', 'index.html', 'manifest.webmanifest', 'css/neon.css',
  'js/app.js', 'js/core.js', 'js/store.js', 'js/nav.js', 'js/ui.js', 'js/intro.js', 'js/player.js', 'js/sync.js',
  'fonts/kanit_semibold_italic.woff2', 'fonts/kanit_medium_italic.woff2', 'fonts/kanit_medium.woff2',
  'fonts/outfit_regular.woff2', 'fonts/outfit_medium.woff2', 'fonts/neon_jp.woff2',
  'icons/icon-192.png', 'icons/apple-touch-icon.png', 'icons/favicon-32.png',
];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(SHELL).then((c) => c.addAll(FILES)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil((async () => {
    for (const k of await caches.keys()) if (k.startsWith('nt-shell-') && k !== SHELL) await caches.delete(k);
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin === location.origin) {
    // App files: cache first, refreshed in the background
    e.respondWith((async () => {
      const cache = await caches.open(SHELL);
      const hit = await cache.match(req, { ignoreSearch: true });
      const net = fetch(req).then((r) => { if (r.ok && !url.pathname.includes('/vendor/')) cache.put(req, r.clone()); return r; }).catch(() => null);
      return hit || (await net) || cache.match('index.html');
    })());
    return;
  }
});
