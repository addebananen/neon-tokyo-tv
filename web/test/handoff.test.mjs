// "Fortsätt på TV:n" from the web app, against a stand-in for Stremio's datastore and a TV that
// behaves like the Android TV's HandoffReceiver. Run: node web/test/handoff.test.mjs
import assert from 'node:assert/strict';

const mem = new Map();
globalThis.localStorage = { getItem: (k) => (mem.has(k) ? mem.get(k) : null), setItem: (k, v) => mem.set(k, String(v)), removeItem: (k) => mem.delete(k) };
globalThis.document = { createElement: () => ({ canPlayType: () => '' }), addEventListener() {} };
Object.defineProperty(globalThis, 'navigator', { value: { userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)' }, configurable: true });
globalThis.window = globalThis;
globalThis.matchMedia = () => ({ matches: false });

// Stremio's datastore, as far as the hand-off uses it
const library = new Map();
const calls = [];
globalThis.NT_STREMIO_API = 'https://stremio.test/api/';
globalThis.fetch = async (url, init) => {
  const method = url.slice(globalThis.NT_STREMIO_API.length);
  const body = JSON.parse(init.body);
  calls.push(method);
  assert.equal(body.authKey, 'key-1');
  let result = null;
  if (method === 'datastoreGet') result = body.all ? [...library.values()] : body.ids.map((id) => library.get(id)).filter(Boolean);
  else if (method === 'datastorePut') { body.changes.forEach((it) => library.set(it._id, structuredClone(it))); result = { success: true }; }
  return { ok: true, status: 200, json: async () => ({ result }) };
};

const store = await import('../js/store.js');
const handoff = await import('../js/handoff.js');

store.update((s) => {
  s.profiles = [{ id: 'p1', name: 'Adrian', stremio: { authKey: 'key-1', email: 'a@example.com', lastSync: Date.now() } }];
  s.activeProfile = 'p1';
});
assert.ok(handoff.available());
assert.equal(handoff.deviceName(), 'iPhone');

// Hidden items stay out of Stremio's Library and Continue watching
const hidden = handoff.hiddenItem(handoff.MAILBOX_ID, '{}');
assert.equal(hidden.type, 'other');
assert.ok(hidden.removed && hidden.temp);
assert.equal(hidden.state.timeOffset, 0);

// Presence: only TVs that are on and were seen lately
const now = Date.now();
const presence = JSON.stringify({ v: 1, tvs: {
  a: { name: 'Vardagsrum', seenAt: now - 60_000, on: true },
  b: { name: 'Sovrum', seenAt: now - 30 * 60_000, on: true },
  c: { name: 'Kök', seenAt: now - 1000, on: false },
} });
assert.deepEqual(handoff.tvsOn(presence, now).map((t) => t.name), ['Vardagsrum']);
library.set(handoff.PRESENCE_ID, handoff.hiddenItem(handoff.PRESENCE_ID, presence));
assert.deepEqual((await handoff.listTvs()).map((t) => t.id), ['a']);

// What the TV reads
const item = { id: 'tt0903747', type: 'series', video: 'tt0903747:2:3', name: 'Breaking Bad', title: 'S2:E3 - Breaking Bad', poster: 'https://img/p.jpg', positionMs: 1234567.4, durationMs: 2820000 };
const sent = await handoff.send(item, 'a');
const box = JSON.parse(library.get(handoff.MAILBOX_ID).name);
assert.equal(box.v, 1);
assert.equal(box.to, 'a');
assert.equal(box.profile, 'Adrian');
assert.equal(box.from, 'iPhone');
assert.equal(box.video, 'tt0903747:2:3');
assert.equal(box.positionMs, 1234567);
assert.equal(box.nonce, sent.nonce);

// The TV takes it (as HandoffReceiver.claimAndPlay does): the phone sees which TV
setTimeout(() => {
  const it = library.get(handoff.MAILBOX_ID);
  const m = JSON.parse(it.name);
  library.set(handoff.MAILBOX_ID, { ...it, name: JSON.stringify({ ...m, takenBy: 'a', takenName: 'Vardagsrum', takenAt: Date.now() }) });
}, 30);
assert.equal(await handoff.waitTaken(sent.nonce, { timeoutMs: 2000, every: 20 }), 'Vardagsrum');

// Nobody takes it: null in time; something newer sent meanwhile: null at once
const lonely = await handoff.send(item);
assert.equal(await handoff.waitTaken(lonely.nonce, { timeoutMs: 120, every: 20 }), null);
const older = await handoff.send(item);
await handoff.send({ ...item, video: 'tt0903747:2:4' });
assert.equal(await handoff.waitTaken(older.nonce, { timeoutMs: 2000, every: 20 }), null);

// The JSON the Android TV's HandoffProtocolTest reads is the same shape
const shape = handoff.message(item, { now: 1000000, nonce: 'n1', profile: 'Adrian' });
assert.deepEqual(Object.keys(shape).sort(), ['background', 'durationMs', 'from', 'id', 'logo', 'name', 'nonce', 'positionMs', 'poster', 'profile', 'sentAt', 'title', 'to', 'type', 'v', 'video'].sort());

console.log('handoff ok');
