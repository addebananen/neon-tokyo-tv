// Screenshots of Neon Tokyo Web with real catalogs, for the web-screens branch.
// Usage: node web-screens.cjs <out dir> [base url]   (needs the playwright package on NODE_PATH)
// The base url defaults to the live site, so the pictures show exactly what visitors get.

const { chromium, devices } = require('playwright');
const fs = require('fs');
const path = require('path');

const OUT = process.argv[2] || 'web-screens';
const BASE = (process.argv[3] || 'https://addebananen.github.io/neon-tokyo-tv/').replace(/#.*$/, '');
fs.mkdirSync(OUT, { recursive: true });
const log = [];
const note = (s) => { log.push(s); console.log(s); };

// A first visit that skips the intro and has one profile, so the app opens straight on Home.
// No addons and no seededAt: the app installs its own starter addons, as for a real first visit.
function seed({ theme, intro }) {
  if (localStorage.getItem('neontokyo.v1')) return;
  localStorage.setItem('neontokyo.v1', JSON.stringify({
    profiles: [{ id: 'p1', name: 'Adrian', color: '#FF3D9A', emoji: '', theme, updated_at_ms: Date.now() }],
    activeProfile: 'p1',
    settings: { intro, sounds: false, hideUnplayable: false, player: 'builtin' },
  }));
}

async function open(browser, opts, theme = 'neon_tokyo', intro = false) {
  const ctx = await browser.newContext(opts);
  await ctx.addInitScript(seed, { theme, intro });
  const page = await ctx.newPage();
  page.on('pageerror', (e) => note(`pageerror: ${e.message}`));
  page.on('console', (m) => { if (m.type() === 'error') note(`console: ${m.text()}`); });
  return { ctx, page };
}

async function go(page, hash, wait) {
  await page.goto(BASE + hash);
  await page.waitForTimeout(wait);
}

async function shot(page, name) {
  await page.screenshot({ path: path.join(OUT, `${name}.png`) });
  note(`shot ${name}`);
}

async function keys(page, list, gap = 160) {
  for (const k of list) { await page.keyboard.press(k); await page.waitForTimeout(gap); }
}

(async () => {
  const browser = await chromium.launch();
  const tv = { viewport: { width: 1440, height: 810 }, deviceScaleFactor: 2 };

  {
    const { ctx, page } = await open(browser, tv, 'neon_tokyo', true);
    await page.goto(BASE + '#/home');
    await page.waitForTimeout(1300);
    await shot(page, 'web-intro');
    await ctx.close();
  }

  {
    const { ctx, page } = await open(browser, tv);
    await go(page, '#/home', 9000);
    await shot(page, 'web-home');
    await keys(page, ['ArrowDown', 'ArrowDown']);
    await page.waitForTimeout(1400);
    await shot(page, 'web-home-focus');
    await keys(page, ['ArrowRight', 'ArrowRight', 'ArrowRight'], 220);
    await page.waitForTimeout(1400);
    await shot(page, 'web-home-slide');
    await go(page, '#/detail/movie/tt15239678', 6000);
    await shot(page, 'web-details');
    await go(page, '#/detail/series/tt0903747', 6000);
    await shot(page, 'web-series');
    await go(page, '#/search?q=dune', 6000);
    await shot(page, 'web-search');
    await go(page, '#/settings', 2500);
    await shot(page, 'web-settings');
    await ctx.close();
  }

  {
    const { ctx, page } = await open(browser, tv, 'deep_blue');
    await go(page, '#/home', 9000);
    await keys(page, ['ArrowDown', 'ArrowDown']);
    await page.waitForTimeout(1400);
    await shot(page, 'web-deep-blue');
    await ctx.close();
  }

  {
    const { ctx, page } = await open(browser, { ...devices['iPhone 15 Pro'] });
    await go(page, '#/home', 9000);
    await shot(page, 'web-phone-home');
    await go(page, '#/detail/movie/tt15239678', 6000);
    await shot(page, 'web-phone-details');
    await ctx.close();
  }

  await browser.close();
  fs.writeFileSync(path.join(OUT, 'web.log'), log.join('\n') + '\n');
})().catch((e) => {
  console.error(e);
  fs.writeFileSync(path.join(OUT, 'web.log'), log.concat(String(e && e.stack || e)).join('\n') + '\n');
  process.exit(1);
});
