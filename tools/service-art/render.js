// Renders the streaming service art: every scene in each hub card shape and as the home
// screen hero, with the background and the subject as separate PNG layers at 2x.
// pack.py then turns them into the WebP files in app/src/main/assets/service_art.
//
//   node render.js [codes] [frames]      e.g. node render.js nfx,hbm wide,hero
//
// Needs Playwright with Chromium; PLAYWRIGHT can point at the module and CHROMIUM at a browser.
const fs = require('fs');
const path = require('path');
const { chromium } = require(process.env.PLAYWRIGHT || 'playwright');
const { scenes, commonDefs } = require('./scenes');

// cx and gy are where the subject stands, s is its scale. The hero keeps its subject right
// of centre, clear of the title and the fades on the left of the home screen.
const FRAMES = {
  wide: { W: 448, H: 252, cx: 224, gy: 192, s: 1.0 },
  square: { W: 320, H: 320, cx: 160, gy: 254, s: 1.16 },
  tall: { W: 264, H: 396, cx: 132, gy: 340, s: 1.0 },
  hero: { W: 640, H: 360, cx: 474, gy: 216, s: 1.0 },
};
const OUT = path.join(__dirname, 'out');
const codes = process.argv[2] ? process.argv[2].split(',') : Object.keys(scenes);
const frames = process.argv[3] ? process.argv[3].split(',') : Object.keys(FRAMES);

function svgDoc(L, scene, layer) {
  const body = layer === 'bg'
    ? scene.bg
    : `<g transform="translate(${L.cx},${L.gy}) scale(${L.s})">${scene.fg}</g>${scene.fgGlobal || ''}`;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${L.W}" height="${L.H}" viewBox="0 0 ${L.W} ${L.H}"><defs>${commonDefs}${scene.defs}</defs>${body}</svg>`;
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {});
  const page = await browser.newPage({ deviceScaleFactor: 2 });
  for (const code of codes) {
    for (const frame of frames) {
      const L = FRAMES[frame];
      const scene = scenes[code](L);
      for (const layer of ['bg', 'fg']) {
        await page.setViewportSize({ width: L.W, height: L.H });
        await page.setContent(`<html><head><style>html,body{margin:0;background:transparent}svg{display:block}</style></head><body>${svgDoc(L, scene, layer)}</body></html>`);
        await page.screenshot({
          path: path.join(OUT, `${code}_${frame}_${layer}@2x.png`),
          omitBackground: layer === 'fg',
          clip: { x: 0, y: 0, width: L.W, height: L.H },
        });
      }
    }
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
