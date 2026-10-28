// Renders the scene art for the streaming service and genre cards: every scene in each hub
// card shape and as the home screen hero, with the background, the subject and each moving
// part as separate PNG layers at 2x, and what moves how in out/motion.json.
// pack.py then turns them into the files in app/src/main/assets/service_art.
//
//   node render.js [codes] [frames]      e.g. node render.js nfx,g_horror wide,hero
//
// Needs Playwright with Chromium; PLAYWRIGHT can point at the module and CHROMIUM at a browser.
const fs = require('fs');
const path = require('path');
const { chromium } = require(process.env.PLAYWRIGHT || 'playwright');
const { scenes, commonDefs } = require('./scenes');
const { FRAMES } = require('./frames');

const OUT = path.join(__dirname, 'out');
// The app's own Kanit, for the few scenes with words in them
const FONT = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'res', 'font', 'kanit_semibold_italic.ttf');
const FONT_CSS = `@font-face{font-family:Kanit;src:url(data:font/ttf;base64,${fs.readFileSync(FONT).toString('base64')})}`;
const codes = process.argv[2] ? process.argv[2].split(',') : Object.keys(scenes);
const frames = process.argv[3] ? process.argv[3].split(',') : Object.keys(FRAMES);

// Subjects and parts are drawn in local units round where the subject stands, unless a
// part says it is drawn in the frame's own pixels.
const local = (L, svg) => `<g transform="translate(${L.cx},${L.gy}) scale(${L.s})">${svg}</g>`;

function svgDoc(L, scene, body) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${L.W}" height="${L.H}" viewBox="0 0 ${L.W} ${L.H}"><defs>${commonDefs}${scene.defs}</defs>${body}</svg>`;
}

function layers(L, scene) {
  const out = [
    ['bg', scene.bg, false],
    ['fg', local(L, scene.fg) + (scene.fgGlobal || ''), true],
  ];
  for (const p of scene.parts || []) out.push([`p_${p.name}`, p.frame ? p.svg : local(L, p.svg), true]);
  return out;
}

// What the app needs to move the parts and draw the effects; pack.py adds where each part sits
function motionOf(L, scene) {
  return {
    frame: { W: L.W, H: L.H, cx: L.cx, gy: L.gy, s: L.s },
    fg: scene.fgMotion || null,
    parts: (scene.parts || []).map(({ svg, frame, ...rest }) => rest),
    fx: scene.fx || [],
  };
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const motionFile = path.join(OUT, 'motion.json');
  const motion = fs.existsSync(motionFile) ? JSON.parse(fs.readFileSync(motionFile, 'utf8')) : {};
  const browser = await chromium.launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {});
  const page = await browser.newPage({ deviceScaleFactor: 2 });
  for (const code of codes) {
    motion[code] = motion[code] || {};
    for (const frame of frames) {
      const L = FRAMES[frame];
      const scene = scenes[code](L);
      motion[code][frame] = motionOf(L, scene);
      await page.setViewportSize({ width: L.W, height: L.H });
      for (const [name, body, clear] of layers(L, scene)) {
        await page.setContent(`<html><head><style>${FONT_CSS}html,body{margin:0;background:transparent}svg{display:block}</style></head><body>${svgDoc(L, scene, body)}</body></html>`);
        await page.evaluate(() => document.fonts.ready);
        await page.screenshot({
          path: path.join(OUT, `${code}_${frame}_${name}@2x.png`),
          omitBackground: clear,
          clip: { x: 0, y: 0, width: L.W, height: L.H },
        });
      }
    }
  }
  fs.writeFileSync(motionFile, JSON.stringify(motion));
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
