# Neon Tokyo Web

Neon Tokyo in the browser, and as a home-screen app on iPhone, iPad, Android and PC:
<https://addebananen.github.io/neon-tokyo-tv/>

- Same look as the TV app (Neon Tokyo and Deep Blue themes), same Stremio addons, profiles,
  Continue watching, watchlist, seasons and episodes, sources, trailers.
- Works with mouse, touch, keyboard and TV remotes (arrow keys move focus like a D-pad).
- Sign a profile in with Stremio: addons come in, library and watch progress stay in step.
- Plays MP4 and HLS in the browser (MKV in Chrome/Edge). Pick your own player per device in
  Settings (Infuse, VLC, Outplayer, nPlayer, SenPlayer on iPhone; VLC, MX, Just Player on Android).
- Apple TV has no browser: play to it with AirPlay from iPhone/iPad.

No build step: plain ES modules. `web/js/core.js` mirrors the shared Rust core
(`native/neon-core`) rule for rule; `node web/test/core.test.mjs` runs the same cases.
Run locally with `python3 -m http.server -d web 8080`.

Published by `.github/workflows/web.yml` on pushes to `main` that touch `web/`.
One-time setup: Settings › Pages › Source: **GitHub Actions**.
