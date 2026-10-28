// Films the packed scenes moving as they do on a focused card, all at once in a grid, from
// the files in the app's assets: node preview.js out.mp4 [codes] [frame] [seconds] [card width]
// Needs ffmpeg. Focus comes on at the start and goes off a second before the end.
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { chromium } = require(process.env.PLAYWRIGHT || 'playwright');

const ASSETS = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'assets', 'service_art');
const motion = JSON.parse(fs.readFileSync(path.join(ASSETS, 'motion.json'), 'utf8'));
const out = process.argv[2] || 'preview.mp4';
const codes = process.argv[3] && process.argv[3] !== 'all' ? process.argv[3].split(',') : Object.keys(motion);
const frame = process.argv[4] || 'wide';
const seconds = +(process.argv[5] || 8);
const cardW = +(process.argv[6] || 380);
const fps = 30;
const F = motion[codes[0]][frame].frame;
const cardH = Math.round(cardW * F.H / F.W);
const cols = Math.min(codes.length, Math.max(1, Math.floor(1900 / (cardW + 16))));
const rows = Math.ceil(codes.length / cols);
const W = cols * (cardW + 16) + 16, H = rows * (cardH + 16) + 16;

(async () => {
  const dir = fs.mkdtempSync(path.join(process.env.TMPDIR || '/tmp', 'scene-preview-'));
  const browser = await chromium.launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {});
  const page = await browser.newPage({ viewport: { width: W, height: H } });
  await page.setContent(`<html><body style="margin:0;background:#0b0618"><canvas id="c" width="${W}" height="${H}"></canvas></body></html>`);
  await page.addScriptTag({ path: path.join(__dirname, 'player.js') });
  const url = f => 'data:image/webp;base64,' + fs.readFileSync(path.join(ASSETS, f)).toString('base64');
  const scenes = codes.map(code => {
    const m = motion[code][frame];
    return { code, m, bg: url(`${code}_${frame}_bg.webp`), fg: url(`${code}_${frame}_fg.webp`),
      parts: Object.fromEntries(m.parts.map(p => [p.name, url(`${code}_${frame}_p_${p.name}.webp`)])) };
  });
  await page.evaluate(async ({ scenes }) => {
    const load = src => new Promise(r => { const i = new Image(); i.onload = () => r(i); i.src = src; });
    for (const s of scenes) {
      s.imgs = { bg: await load(s.bg), fg: await load(s.fg), parts: {} };
      for (const [n, u] of Object.entries(s.parts)) s.imgs.parts[n] = await load(u);
    }
    window.scenes = scenes;
  }, { scenes });
  let T = 0, e = 0;
  const total = Math.round(seconds * fps);
  for (let i = 0; i < total; i++) {
    const t = i / fps, focused = t < seconds - 1;
    // The app's energy: 700 ms in, 450 ms out; time runs at the energy's pace
    e = focused ? Math.min(1, e + 1 / (0.7 * fps)) : Math.max(0, e - 1 / (0.45 * fps));
    T += e / fps;
    const ease = e * e * (3 - 2 * e);
    await page.evaluate(({ T, ease, cols, cardW, cardH }) => {
      const c = document.getElementById('c'), ctx = c.getContext('2d');
      ctx.fillStyle = '#0b0618'; ctx.fillRect(0, 0, c.width, c.height);
      window.scenes.forEach((s, n) => {
        ctx.save();
        ctx.translate(16 + (n % cols) * (cardW + 16), 16 + Math.floor(n / cols) * (cardH + 16));
        ctx.beginPath(); ctx.roundRect(0, 0, cardW, cardH, 14); ctx.clip();
        ScenePlayer.drawScene(ctx, s.imgs, s.m, T, ease, ease, cardW, cardH);
        ctx.restore();
      });
    }, { T, ease, cols, cardW, cardH });
    await page.screenshot({ path: path.join(dir, `f${String(i).padStart(4, '0')}.png`) });
  }
  await browser.close();
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-framerate', String(fps), '-i', path.join(dir, 'f%04d.png'),
    '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '20', out]);
  fs.rmSync(dir, { recursive: true });
  console.log(`${out}: ${codes.length} scenes, ${seconds}s`);
})().catch(e => { console.error(e); process.exit(1); });
