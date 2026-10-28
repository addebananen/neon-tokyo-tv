# Scene art

The scenes on the streaming service and genre cards and in the home screen hero, drawn for
Neon Tokyo: one 80s neon scene per service and per genre about what it is known for, no logos.
On a focused card the scene comes alive: parts move and effects play, with no sound.

- `scenes.js` (services) and `genres.js` (genres) draw every scene as SVG, with the background,
  the subject and each moving part apart, and say how the parts move and which effects play.
  `lib.js` has the shared drawing helpers and `frames.js` the card shapes.
- `render.js` renders each scene in the three card shapes and as the hero, at 2x, and writes
  `out/motion.json`.
- `pack.py` turns those into the WebP files in `app/src/main/assets/service_art`, crops the
  parts and the hero's subject to where they sit and writes `motion.json` there for the app
  (`SceneMotion.kt`). `ONLY=code,code` packs just those scenes.
- `preview.js` films the packed scenes moving with `player.js`, which plays motion.json the way
  the app does: `node preview.js out.mp4 g_horror,nfx wide 8`.

```
npm i playwright && npx playwright install chromium
node render.js
python3 pack.py
node preview.js preview.mp4
```

Motion, in local units round where the subject stands:
- a channel is a number or `{ b, k: { p, v: [[time, value, ease]] }, w: [[amp, period, phase]] }`;
  ease 0 is in and out, 1 straight, 2 speeding up, 3 slowing down
- a part has `pivot`, `group` (scene or subject), `under`, `hidden`, `sym` and channels
  `dx dy rot s sx sy a`; at rest it is where it was drawn
- effects: flash, glow, ring, rays, dots, digits and particles (dot, glowdot, puff, ring,
  streak, rect, bird, bat, heart, petal, spark); `keep` shows one at rest too
