// Sends a large video to the TV the way a phone does: the real upload page in a real browser,
// opened at an address that is not "secure" (like the TV's own http://192.168.x.x), where a page
// may not take the screen wake lock. Checks that the page keeps the phone awake some other way,
// that a page thrown away halfway carries on where the TV got to once the same video is picked
// again, and that the video then arrives whole.
//   node phone-page-test.js <page url> <video file> <status url>
let playwright;
try { playwright = require('playwright-core'); } catch (e) { playwright = require('playwright'); }
const { chromium } = playwright;

const [pageUrl, videoFile, statusUrl] = process.argv.slice(2);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const failures = [];
const fail = (why) => { console.log('FAIL: ' + why); failures.push(why); };
const status = async () => {
  try { return await (await fetch(statusUrl, { cache: 'no-store' })).json(); } catch (e) { return {}; }
};

(async () => {
  const size = require('fs').statSync(videoFile).size;
  const host = new URL(pageUrl).hostname;
  const browser = await chromium.launch({
    executablePath: process.env.CHROME || '/usr/bin/google-chrome',
    args: [`--host-resolver-rules=MAP ${host} 127.0.0.1`, '--autoplay-policy=user-gesture-required'],
  });
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
  let page = await context.newPage();
  await page.goto(pageUrl);
  const secure = await page.evaluate(() => window.isSecureContext);
  console.log(`page at ${pageUrl}: secure context ${secure}, wake lock ${await page.evaluate(() => 'wakeLock' in navigator)}`);
  if (secure) fail('the test page counted as secure, so it cannot show what a phone gets');

  // A phone's Wi-Fi, not the test machine's cable: about 6 MB/s up, so it can be cut off halfway
  const cdp = await context.newCDPSession(page);
  await cdp.send('Network.enable');
  await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 5, downloadThroughput: 12e6, uploadThroughput: 6e6 });
  await page.setInputFiles('#library', videoFile);
  await page.click('#send');
  await sleep(2000);
  const awake = await page.evaluate(() => { const a = document.getElementById('awake'); return !a.paused && a.readyState >= 2; });
  console.log(`keep-awake video playing: ${awake}`);
  if (!awake) fail('the page did not start the video that keeps the phone awake');

  // Thrown away halfway, as a phone may do with a page it put to sleep
  const started = Date.now();
  while ((await status()).received < size / 3) {
    if (Date.now() - started > 240000) { fail(`the video got no further than ${(await status()).received} of ${size} bytes`); break; }
    await sleep(100);
  }
  await page.close();
  await sleep(1500);
  const there = (await status()).received || 0;
  page = await context.newPage();
  await page.goto(pageUrl);
  await page.setInputFiles('#library', videoFile);
  await page.click('#send');
  await sleep(800);
  const shown = await page.textContent('#pct');
  console.log(`page opened again with ${there} of ${size} bytes on the TV: the phone shows "${shown}"`);
  if (!/^[1-9]/.test(shown.trim())) fail(`the same video picked again started over ("${shown}") instead of carrying on from ${there}`);

  // Sent, checked and made right for the TV: the page says it is on
  const deadline = Date.now() + 420000;
  let view = '';
  while (Date.now() < deadline) {
    view = await page.evaluate(() => [...document.querySelectorAll('.view')].find((v) => !v.hidden)?.id || '');
    if (view === 'done' || view === 'closed') break;
    if (view === 'check') { fail('the page went back to the start: ' + (await page.textContent('#checkMsg'))); break; }
    await sleep(1000);
  }
  const s = await status();
  console.log(`page ends on "${view}", TV state ${JSON.stringify(s)}, ${Math.round((Date.now() - started) / 1000)} s`);
  if (view !== 'done') fail(`the page never said the video is on (it shows "${view}", the TV says ${s.state})`);
  if (s.received !== size) fail(`the TV holds ${s.received} of ${size} bytes`);
  if (!(await page.evaluate(() => document.getElementById('awake').paused))) fail('the phone is still kept awake after it is done');
  await browser.close();
  process.exit(failures.length ? 1 : 0);
})().catch((e) => { console.log('FAIL: ' + e); process.exit(1); });
