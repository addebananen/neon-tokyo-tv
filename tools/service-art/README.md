# Streaming service art

The scenes on the streaming service cards and in the home screen hero, drawn for Neon
Tokyo: one 80s neon scene per service about what it is known for, no logos.

- `scenes.js` draws every scene as SVG, with the background and the subject apart, so
  the app can lift the subject off its scene on focus.
- `render.js` renders each scene in the three card shapes and as the hero, at 2x.
- `pack.py` turns those into the WebP files in `app/src/main/assets/service_art` and
  prints where each hero's subject sits, for `ServiceArt.heroSubjects`.

```
npm i playwright && npx playwright install chromium
node render.js
python3 pack.py
```
