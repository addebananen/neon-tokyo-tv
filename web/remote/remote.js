/* Neon Remote: the phone as the Neon Tokyo TV's remote.
 *
 * Two homes, one file:
 *  - On the TV (http://<tv>:8765/) it is the remote itself: it talks to the TV's small server
 *    (app/…/remote_input/phone/PhoneRemoteServer.kt), with a key from the QR code or the code.
 *  - In Neon Tokyo Web (https://…/remote/) it can't reach the TV (browsers keep https pages away
 *    from http addresses on the home network), so it remembers your TVs and opens their remote.
 */
(() => {
  'use strict';

  const KEY = 'nt.remote.key';
  const TVS = 'nt.remote.tvs';
  const RECENT = 'nt.remote.recent';
  const WHERE = 'nt.remote.where';
  const PORT = 8765;

  const $ = (sel, root = document) => root.querySelector(sel);
  const app = $('#app');
  const store = {
    get(k, d) { try { const v = localStorage.getItem(k); return v == null ? d : JSON.parse(v); } catch { return d; } },
    set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* private mode */ } },
  };
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  // ---------- Icons ----------
  const I = {
    up: '<path d="M6 15l6-6 6 6"/>', down: '<path d="M6 9l6 6 6-6"/>', left: '<path d="M15 6l-6 6 6 6"/>', right: '<path d="M9 6l6 6-6 6"/>',
    back: '<path d="M9 14L4 9l5-5"/><path d="M4 9h10.5a5.5 5.5 0 010 11H11"/>',
    home: '<path d="M4 11l8-7 8 7"/><path d="M6 9.5V20h12V9.5"/>',
    play: '<path d="M7 5l11 7-11 7z" fill="currentColor" stroke="none"/>',
    playpause: '<path d="M4 5.5l8 6.5-8 6.5z" fill="currentColor" stroke="none"/><path d="M15.5 6v12M20 6v12" stroke-width="2.4"/>',
    mic: '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5.5 11a6.5 6.5 0 0013 0M12 17.5V21"/>',
    kbd: '<rect x="2.5" y="6" width="19" height="12" rx="2.5"/><path d="M6 10h.01M9.5 10h.01M13 10h.01M16.5 10h.01M6.5 14h11"/>',
    volup: '<path d="M4 9.5h3.5L12 5v14l-4.5-4.5H4z"/><path d="M16 9a4 4 0 010 6M18.5 6.5a7.5 7.5 0 010 11"/>',
    voldown: '<path d="M4 9.5h3.5L12 5v14l-4.5-4.5H4z"/><path d="M16 9a4 4 0 010 6"/>',
    mute: '<path d="M4 9.5h3.5L12 5v14l-4.5-4.5H4z"/><path d="M16 9.5l5 5M21 9.5l-5 5"/>',
    control: '<rect x="3.5" y="3.5" width="7" height="7" rx="3.5"/><rect x="13.5" y="3.5" width="7" height="7" rx="2"/><rect x="3.5" y="13.5" width="7" height="7" rx="2"/><rect x="13.5" y="13.5" width="7" height="7" rx="3.5"/>',
    power: '<path d="M12 3v8"/><path d="M6.4 6.6a8 8 0 1011.2 0"/>',
    rewind: '<path d="M11 6l-7 6 7 6zM20 6l-7 6 7 6z" fill="currentColor" stroke="none"/>',
    forward: '<path d="M13 6l7 6-7 6zM4 6l7 6-7 6z" fill="currentColor" stroke="none"/>',
    remote: '<rect x="7" y="2.5" width="10" height="19" rx="5"/><circle cx="12" cy="8" r="2.2"/><path d="M12 14v.01M12 17v.01"/>',
    search: '<circle cx="11" cy="11" r="6.5"/><path d="M20 20l-4.2-4.2"/>',
    apps: '<rect x="3.5" y="3.5" width="7" height="7" rx="2"/><rect x="13.5" y="3.5" width="7" height="7" rx="2"/><rect x="3.5" y="13.5" width="7" height="7" rx="2"/><rect x="13.5" y="13.5" width="7" height="7" rx="2"/>',
    close: '<path d="M6 6l12 12M18 6L6 18"/>',
    clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
    warn: '<path d="M12 4l9 16H3z"/><path d="M12 10v4.5M12 17.5v.01"/>',
    tv: '<rect x="2.5" y="4.5" width="19" height="13" rx="2.5"/><path d="M8 21h8"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    send: '<path d="M5 12h13M13 6l6 6-6 6"/>',
  };
  const svg = (name) => `<svg viewBox="0 0 24 24" aria-hidden="true">${I[name] || ''}</svg>`;

  // ---------- Haptics ----------
  // iOS 18 Safari ticks when a switch flips (no vibrate API there); elsewhere the vibrate API
  const hapticBox = $('#haptic');
  const haptic = (strong) => {
    try {
      if (navigator.vibrate) { navigator.vibrate(strong ? 18 : 8); return; }
      hapticBox?.click();
    } catch { /* fine without */ }
  };

  let toastTimer = 0;
  const toast = (text, warn = false, ms = 2200) => {
    let el = $('.toast');
    if (!el) { el = document.createElement('div'); el.className = 'toast'; el.setAttribute('role', 'status'); document.body.append(el); }
    el.textContent = text;
    el.classList.toggle('warn', warn);
    requestAnimationFrame(() => el.classList.add('on'));
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('on'), ms);
  };

  // ---------- Where we are ----------
  const onTv = location.protocol === 'http:' && !/github\.io$/.test(location.hostname);
  if (onTv) tvRemote(); else launcher();

  // =====================================================================
  // In Neon Tokyo Web: remember the TVs and open their remote
  // =====================================================================
  function launcher() {
    const tvs = store.get(TVS, []);
    const hashTv = new URLSearchParams(location.hash.slice(1)).get('tv');
    if (hashTv) { remember(hashTv); open(hashTv); return; }

    function remember(host, name) {
      const list = store.get(TVS, []).filter((t) => t.host !== host);
      list.unshift({ host, name: name || 'TV', at: Date.now() });
      store.set(TVS, list.slice(0, 6));
    }
    function open(host, pin) {
      const url = `http://${host}/${pin ? `#pin=${encodeURIComponent(pin)}` : ''}`;
      location.href = url;
    }

    app.innerHTML = `
      <div class="top"><div class="who"><h1>Neon Remote</h1><div class="now">Your phone as the TV's remote</div></div>
        <a class="round" href="../" aria-label="Back to Neon Tokyo">${svg('close')}</a></div>
      <div class="center" style="justify-content:flex-start;padding-top:12px;overflow-y:auto">
        ${tvs.length ? `<div class="section">Your TVs</div><div class="list" style="gap:10px">${tvs.map((t) => `
          <button class="tv-row" data-host="${esc(t.host)}">${svg('tv')}<span class="who"><b>${esc(t.name)}</b><small>${esc(t.host)}</small></span>${svg('right')}</button>`).join('')}</div>` : ''}
        <div class="card">
          <h2 style="font-size:20px">${tvs.length ? 'Add another TV' : 'Connect to your TV'}</h2>
          <ol class="steps">
            <li>On the TV: <b>Neon Tokyo › Settings › Personalization › Phone remote › Show the code</b>.</li>
            <li>Easiest: scan the QR code with the iPhone's <b>Camera</b>. Done.</li>
            <li>Or type the TV's address and the four-digit code here.</li>
          </ol>
          <div class="field"><input id="host" inputmode="decimal" autocomplete="off" placeholder="192.168.1.20" aria-label="TV address"></div>
          <div class="field"><input id="pin" inputmode="numeric" maxlength="4" autocomplete="one-time-code" placeholder="Code on the TV" aria-label="Code"><button class="go" id="add" disabled>Connect</button></div>
          <p class="say">Phone and TV need to be on the same Wi-Fi. The remote opens from the TV itself, so it is quick and nothing goes over the internet.</p>
        </div>
      </div>`;

    app.querySelectorAll('.tv-row').forEach((b) => b.addEventListener('click', () => { haptic(); open(b.dataset.host); }));
    const host = $('#host'), pin = $('#pin'), add = $('#add');
    const norm = () => {
      let h = host.value.trim().replace(/^https?:\/\//, '').replace(/\/.*$/, '');
      if (!h) return '';
      if (!/:\d+$/.test(h)) h += `:${PORT}`;
      return h;
    };
    const ok = () => { add.disabled = !/^[\w.-]+:\d+$/.test(norm()) || !/^\d{4}$/.test(pin.value.trim()); };
    host.addEventListener('input', ok);
    pin.addEventListener('input', ok);
    if (!tvs.length) host.value = '192.168.1.';
    add.addEventListener('click', () => { const h = norm(); remember(h); haptic(true); open(h, pin.value.trim()); });
    pin.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !add.disabled) add.click(); });
  }

  // =====================================================================
  // On the TV: the remote
  // =====================================================================
  function tvRemote() {
    let key = '';
    const hash = new URLSearchParams(location.hash.slice(1));
    key = hash.get('k') || store.get(KEY, '') || '';
    const pinFromWeb = hash.get('pin');

    // The key stays in the address: a home-screen remote on iOS starts with its own empty storage
    const keepKey = (k) => {
      key = k;
      store.set(KEY, k);
      history.replaceState(null, '', `${location.pathname}#k=${encodeURIComponent(k)}`);
    };

    const api = async (path, body) => {
      const res = await fetch(`/api/${path}`, {
        method: body ? 'POST' : 'GET',
        headers: { 'X-Neon-Key': key, ...(body ? { 'Content-Type': 'application/json' } : {}) },
        body: body ? JSON.stringify(body) : undefined,
        cache: 'no-store',
      });
      if (res.status === 401) { pair(); throw new Error('pair'); }
      return res.json();
    };

    (async () => {
      if (pinFromWeb) {
        const k = await tryPin(pinFromWeb);
        if (!k) { pair('That code didn’t work. Show the code on the TV again.'); return; }
      }
      try {
        const hello = await (await fetch('/api/hello', { headers: { 'X-Neon-Key': key }, cache: 'no-store' })).json();
        if (!hello.paired) { pair(); return; }
        keepKey(key);
        remote(hello);
      } catch {
        offline();
      }
    })();

    async function tryPin(code) {
      try {
        const res = await fetch('/api/pair', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ pin: code }) });
        if (!res.ok) return null;
        const { k } = await res.json();
        keepKey(k);
        return k;
      } catch { return null; }
    }

    function offline() {
      app.innerHTML = `<div class="center"><div class="logo">Neon Remote</div>
        <div class="card"><h2 style="font-size:20px">Can't reach the TV</h2>
        <p class="say">Make sure the TV is on and that the phone is on the same Wi-Fi, then try again.</p>
        <button class="go" id="again">Try again</button></div></div>`;
      $('#again').addEventListener('click', () => location.reload());
    }

    // ---------- Pairing with the code ----------
    function pair(note) {
      app.innerHTML = `<div class="center">
        <div class="logo">Neon Remote</div>
        <div><h2 style="font-size:24px">Type the code on the TV</h2>
        <p class="say" id="pair-note">${esc(note || 'On the TV: Neon Tokyo › Settings › Personalization › Phone remote › Show the code. Or scan its QR code with the Camera.')}</p></div>
        <label class="pins" id="pins"><span></span><span></span><span></span><span></span>
          <input id="pin" inputmode="numeric" pattern="[0-9]*" maxlength="4" autocomplete="one-time-code" aria-label="Four-digit code"></label>
      </div>`;
      const input = $('#pin'), pins = $('#pins'), boxes = [...pins.querySelectorAll('span')];
      const draw = () => {
        const v = input.value.replace(/\D/g, '').slice(0, 4);
        input.value = v;
        boxes.forEach((b, i) => { b.textContent = v[i] || ''; b.classList.toggle('full', !!v[i]); b.classList.toggle('at', i === v.length); });
      };
      input.addEventListener('input', async () => {
        draw();
        pins.classList.remove('bad');
        if (input.value.length !== 4) return;
        haptic();
        const k = await tryPin(input.value);
        if (k) {
          haptic(true);
          try { remote(await (await fetch('/api/hello', { headers: { 'X-Neon-Key': k } })).json()); } catch { offline(); }
        } else {
          pins.classList.add('bad');
          haptic(true);
          $('#pair-note').textContent = 'Wrong code, or the code screen on the TV has closed. Open it and try again.';
          setTimeout(() => { input.value = ''; draw(); }, 450);
        }
      });
      draw();
      setTimeout(() => input.focus(), 250);
    }

    // ---------- The remote ----------
    function remote(hello) {
      let state = { name: hello.name || 'TV' };
      let page = 'remote';
      let where = store.get(WHERE, 'smart');
      let warnedService = false;

      app.innerHTML = `
        <div class="top">
          <div class="who"><h1 id="tv-name">${esc(state.name)}</h1><div class="now"><i class="dot" id="dot"></i><span id="now">Connecting…</span></div></div>
          <button class="round" id="power" aria-label="Turn the TV off">${svg('power')}</button>
        </div>
        <div class="pages">
          <section class="page" data-page="remote">
            <div id="banner"></div>
            <div class="pad" id="pad" aria-label="Touchpad: swipe to move, tap to select">
              <div class="light"></div>
              <i class="edge up"></i><i class="edge down"></i><i class="edge left"></i><i class="edge right"></i>
              <div class="chev">${svg('up')}</div>
              <div class="hint">Swipe to move · tap to select</div>
            </div>
            <div class="keys labelled">
              <button class="key" data-key="back" aria-label="Back">${svg('back')}<span class="lab">Back</span></button>
              <button class="key" data-key="home" aria-label="Home">${svg('home')}<span class="lab">Home</span></button>
              <button class="key mic" id="mic" aria-label="Talk">${svg('mic')}<span class="lab">Talk</span></button>
              <button class="key" data-key="playpause" aria-label="Play or pause">${svg('playpause')}<span class="lab">Play</span></button>
              <button class="key small" data-key="control" aria-label="Control Center">${svg('control')}<span class="lab">Control</span></button>
              <button class="key small" data-key="voldown" data-repeat aria-label="Volume down">${svg('voldown')}<span class="lab">Vol −</span></button>
              <button class="key small" data-key="volup" data-repeat aria-label="Volume up">${svg('volup')}<span class="lab">Vol +</span></button>
              <button class="key small" id="kbd" aria-label="Keyboard">${svg('kbd')}<span class="lab">Type</span></button>
            </div>
            <div class="vol" id="vol" hidden><button class="round" data-key="mute" aria-label="Mute">${svg('mute')}</button><div class="bar"><i></i></div></div>
          </section>
          <section class="page scroll away" data-page="search">
            <div class="field"><input id="q" type="search" enterkeyhint="search" autocomplete="off" autocorrect="off" spellcheck="false" placeholder="Search or say “open YouTube”"><button class="round" id="qmic" aria-label="Talk">${svg('mic')}</button></div>
            <div class="chips" id="where">
              <button class="chip" data-where="smart">Anything</button>
              <button class="chip" data-where="neon">Neon Tokyo</button>
              <button class="chip" data-where="youtube">YouTube</button>
              <button class="chip" data-where="spotify">Spotify</button>
              <button class="chip" data-where="youtube music">YT Music</button>
              <button class="chip" data-where="tv">Google TV</button>
            </div>
            <p class="say" id="where-say"></p>
            <div class="section" id="recent-h">Recent</div>
            <div class="list" id="recent"></div>
          </section>
          <section class="page scroll away" data-page="apps">
            <div class="apps" id="apps"></div>
          </section>
        </div>
        <nav class="tabs" id="tabs">
          <i class="mark"></i>
          <button class="tab on" data-page="remote">${svg('remote')}Remote</button>
          <button class="tab" data-page="search">${svg('search')}Search</button>
          <button class="tab" data-page="apps">${svg('apps')}Apps</button>
        </nav>
        <div class="scrim" id="scrim"></div>
        <div class="sheet" id="kb" role="dialog" aria-label="Type on the TV">
          <i class="grab"></i>
          <div class="head"><h2 id="kb-title">Type on the TV</h2><button class="round" id="kb-close" aria-label="Close">${svg('close')}</button></div>
          <div class="field"><input id="kb-in" type="text" enterkeyhint="go" autocomplete="off" autocorrect="off" autocapitalize="off" spellcheck="false"><button class="go" id="kb-go">${svg('send')}</button></div>
          <div class="note" id="kb-note"></div>
        </div>
        <div class="sheet voice" id="voice" role="dialog" aria-label="Listening">
          <div class="orb" id="orb"><i>${svg('mic')}</i></div>
          <div class="heard dim" id="heard">Listening…</div>
          <div class="note" id="voice-note">Try “open YouTube”, “play city pop on Spotify” or a film.</div>
        </div>`;

      // ---------- Sending, in order ----------
      let chain = Promise.resolve();
      const send = (path, body) => (chain = chain.then(() => api(path, body)).catch((e) => {
        if (e.message !== 'pair') toast('The TV didn’t answer', true);
        return {};
      }));
      const outcome = (r) => {
        if (r?.result === 'service') serviceHint();
        return r;
      };
      const press = (k) => send('key', { key: k }).then(outcome);

      function serviceHint() {
        if (warnedService) { toast('Turn on Neon Tokyo overlays on the TV first', true); return; }
        warnedService = true;
        $('#banner').innerHTML = `<div class="banner">${svg('warn')}<div>To steer apps other than Neon, turn on <b>Neon Tokyo overlays</b> on the TV: Settings › System › Accessibility. <button id="ban-x">OK</button></div></div>`;
        $('#ban-x').addEventListener('click', () => { $('#banner').innerHTML = ''; });
      }

      // ---------- Live state from the TV ----------
      let es = null, lostAt = 0, lostTimer = 0;
      const connect = () => {
        es?.close();
        es = new EventSource(`/api/events?k=${encodeURIComponent(key)}`);
        es.addEventListener('state', (e) => { try { show(JSON.parse(e.data)); } catch { /* skip */ } });
        es.onopen = () => { lostAt = 0; clearTimeout(lostTimer); $('#dot').className = 'dot on'; };
        es.onerror = () => {
          $('#dot').className = 'dot';
          if (!lostAt) lostAt = Date.now();
          $('#now').textContent = 'Reconnecting…';
          clearTimeout(lostTimer);
          lostTimer = setTimeout(() => { if (lostAt) { $('#dot').className = 'dot off'; $('#now').textContent = 'Can’t reach the TV · same Wi-Fi?'; } }, 6000);
        };
      };
      connect();
      // Back from the lock screen or another app: a fresh line at once
      document.addEventListener('visibilitychange', () => { if (!document.hidden && (!es || es.readyState === 2 || lostAt)) connect(); });

      let lastTyped = 0;
      function show(s) {
        state = s;
        $('#tv-name').textContent = s.name || 'TV';
        $('#now').textContent = s.appLabel ? `On the TV: ${s.appLabel}` : 'Connected';
        const vol = $('#vol');
        if (s.volume && s.volume.max > 0) {
          vol.hidden = false;
          vol.classList.toggle('muted', !!s.volume.muted);
          vol.querySelector('i').style.width = `${Math.round((s.volume.muted ? 0 : s.volume.level / s.volume.max) * 100)}%`;
        }
        if (s.service === false && !warnedService && sessionStorage.getItem('nt.svc') !== '1') {
          sessionStorage.setItem('nt.svc', '1');
          serviceHint();
        }
        $$apps.mark(s.app);
        // A text field came up on the TV: the keyboard comes up here, like an Apple TV's
        const f = s.field;
        if (f && !kb.open && Date.now() - kb.closedAt > 1500) kb.show(f, true);
        else if (f && kb.open) kb.update(f, Date.now() - lastTyped > 1200);
        else if (!f && kb.open && kb.auto) kb.hide();
      }

      // ---------- Tabs ----------
      const tabs = $('#tabs'), mark = tabs.querySelector('.mark');
      const placeMark = () => {
        const on = tabs.querySelector('.tab.on');
        mark.style.transform = `translateX(${on.offsetLeft + on.offsetWidth / 2 - 22}px)`;
      };
      tabs.addEventListener('click', (e) => {
        const b = e.target.closest('.tab');
        if (!b || b.dataset.page === page) return;
        haptic();
        page = b.dataset.page;
        tabs.querySelectorAll('.tab').forEach((t) => t.classList.toggle('on', t === b));
        document.querySelectorAll('.page').forEach((p) => p.classList.toggle('away', p.dataset.page !== page));
        placeMark();
        if (page === 'apps') $$apps.load();
      });
      requestAnimationFrame(placeMark);
      addEventListener('resize', placeMark);

      // ---------- Buttons ----------
      app.querySelectorAll('[data-key]').forEach((b) => {
        let repeat = 0;
        const stop = () => { clearInterval(repeat); clearTimeout(repeat); repeat = 0; b.classList.remove('pressed'); };
        b.addEventListener('pointerdown', (e) => {
          e.preventDefault();
          b.classList.add('pressed');
          haptic();
          press(b.dataset.key);
          if (b.hasAttribute('data-repeat')) {
            repeat = setTimeout(() => { repeat = setInterval(() => { haptic(); press(b.dataset.key); }, 140); }, 420);
          }
        });
        ['pointerup', 'pointercancel', 'pointerleave'].forEach((t) => b.addEventListener(t, stop));
        b.addEventListener('contextmenu', (e) => e.preventDefault());
      });

      // Power: tap, then tap again within 3 s
      const power = $('#power');
      let armed = 0;
      power.addEventListener('click', () => {
        haptic(true);
        if (armed) {
          clearTimeout(armed); armed = 0; power.classList.remove('armed');
          press('sleep').then((r) => { if (r?.result === 'ok') toast('Good night'); });
          return;
        }
        power.classList.add('armed');
        toast('Tap again to turn the TV off');
        armed = setTimeout(() => { armed = 0; power.classList.remove('armed'); }, 3000);
      });

      touchpad($('#pad'), press);

      // ---------- Keyboard sheet ----------
      const scrim = $('#scrim');
      const kb = {
        open: false, auto: false, closedAt: 0, target: 'field', timer: 0,
        el: $('#kb'), input: $('#kb-in'),
        show(f, auto) {
          this.auto = !!auto;
          this.target = f?.target || 'field';
          $('#kb-title').textContent = this.target === 'neon' ? 'Search Neon Tokyo' : f?.app ? `Type in ${f.app}` : 'Type on the TV';
          this.input.type = f?.password ? 'password' : 'text';
          this.input.placeholder = f?.hint || (f ? '' : 'Type here, it appears on the TV');
          this.input.value = f?.text || '';
          $('#kb-note').textContent = f ? (this.target === 'neon' ? 'Results show on the TV as you type.' : 'Letters appear on the TV as you type.') : 'Pick a text field on the TV first, or use Search.';
          this.el.classList.add('on'); scrim.classList.add('on');
          this.open = true;
          haptic();
          // iOS only opens the keyboard from a tap; an auto-opened sheet waits for one
          if (!auto) this.input.focus();
        },
        update(f, take) {
          if (take && document.activeElement !== this.input) this.input.value = f.text || '';
          else if (take && !this.input.value && f.text) this.input.value = f.text;
        },
        hide() {
          this.el.classList.remove('on'); scrim.classList.remove('on');
          this.open = false; this.closedAt = Date.now();
          this.input.blur();
        },
      };
      const typeNow = (submit) => {
        clearTimeout(kb.timer);
        lastTyped = Date.now();
        return send('text', { text: kb.input.value, submit }).then((r) => {
          if (r?.result === 'none' && submit) toast('Nothing on the TV takes text right now', true);
          return outcome(r);
        });
      };
      kb.input.addEventListener('input', () => { lastTyped = Date.now(); clearTimeout(kb.timer); kb.timer = setTimeout(() => typeNow(false), 140); });
      kb.input.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); typeNow(true); haptic(true); if (kb.target !== 'neon') kb.hide(); else kb.input.blur(); } });
      $('#kb-go').addEventListener('click', () => { typeNow(true); haptic(true); kb.hide(); });
      $('#kb-close').addEventListener('click', () => kb.hide());
      scrim.addEventListener('click', () => { kb.hide(); voice.stop(); });
      $('#kbd').addEventListener('click', () => { haptic(); kb.show(state.field || null, false); });

      // ---------- Search ----------
      const q = $('#q');
      const whereSay = {
        smart: 'Say or type anything: <b>open YouTube</b>, <b>play lofi on Spotify</b>, or a film or series to find in Neon Tokyo.',
        neon: 'Films and series from your addons, in Neon Tokyo on the TV.',
        youtube: 'Opens YouTube on the TV with your search.',
        spotify: 'Opens Spotify on the TV with your search.',
        'youtube music': 'Opens YouTube Music on the TV with your search.',
        tv: 'Google TV’s own search, across every app.',
      };
      const setWhere = (w) => {
        where = whereSay[w] ? w : 'smart';
        store.set(WHERE, where);
        $('#where').querySelectorAll('.chip').forEach((c) => c.classList.toggle('on', c.dataset.where === where));
        $('#where-say').innerHTML = whereSay[where];
      };
      $('#where').addEventListener('click', (e) => { const c = e.target.closest('.chip'); if (c) { haptic(); setWhere(c.dataset.where); } });
      setWhere(where);

      const recent = () => {
        const list = store.get(RECENT, []);
        $('#recent-h').hidden = !list.length;
        $('#recent').innerHTML = list.map((r) => `<button class="row" data-q="${esc(r)}">${svg('clock')}<span>${esc(r)}</span>${svg('send')}</button>`).join('');
      };
      $('#recent').addEventListener('click', (e) => { const r = e.target.closest('.row'); if (r) { q.value = r.dataset.q; go(r.dataset.q); } });
      recent();

      async function go(text, how = where) {
        text = (text || '').trim();
        if (!text) return;
        haptic(true);
        store.set(RECENT, [text, ...store.get(RECENT, []).filter((r) => r.toLowerCase() !== text.toLowerCase())].slice(0, 8));
        recent();
        q.blur();
        const r = outcome(await send('search', { q: text, where: how }));
        if (r?.result === 'ok') toast(r.did || 'Sent to the TV');
        else if (r?.result !== 'service') toast('The TV couldn’t search that', true);
      }
      q.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); go(q.value); } });

      // ---------- Voice ----------
      const voice = makeVoice({
        sheet: $('#voice'), heard: $('#heard'), orb: $('#orb'), note: $('#voice-note'), scrim,
        done: (text) => {
          // A text field on the TV takes what was said; anything else is a command or a search
          if (state.field) { kb.show(state.field, false); kb.input.value = text; typeNow(true); kb.hide(); toast(`Typed “${text}”`); }
          else go(text, 'smart');
        },
        fallback: () => {
          // No speech in this browser: the keyboard's own dictation (the mic key) does it
          switchTo('search');
          q.focus();
          toast('Tap the 🎤 on the keyboard and talk', false, 3200);
        },
      });
      $('#mic').addEventListener('click', () => { haptic(true); voice.start(); });
      $('#qmic').addEventListener('click', () => { haptic(true); voice.start(); });
      function switchTo(p) { tabs.querySelector(`.tab[data-page="${p}"]`)?.click(); }

      // ---------- Apps ----------
      const $$apps = {
        loaded: false, now: null,
        async load() {
          if (this.loaded) return;
          this.loaded = true;
          const box = $('#apps');
          box.innerHTML = '<p class="say">Loading the TV’s apps…</p>';
          let list = [];
          try { list = (await api('apps')).apps || []; } catch { this.loaded = false; box.innerHTML = '<p class="say">Couldn’t load the apps.</p>'; return; }
          box.innerHTML = list.map((a) => `<button class="app-tile" data-pkg="${esc(a.pkg)}"><span class="art"><img alt="" loading="lazy" decoding="async" src="/api/icon?pkg=${encodeURIComponent(a.pkg)}&k=${encodeURIComponent(key)}"><b class="letter">${esc((a.label || '?').slice(0, 1))}</b></span><span class="name">${esc(a.label)}</span></button>`).join('');
          box.querySelectorAll('img').forEach((img) => {
            const done = () => { if (img.naturalWidth && img.naturalWidth === img.naturalHeight) img.classList.add('icon'); img.classList.add('in'); };
            if (img.complete && img.naturalWidth) done(); else img.addEventListener('load', done, { once: true });
          });
          this.mark(this.now);
        },
        mark(pkg) {
          this.now = pkg;
          document.querySelectorAll('.app-tile').forEach((t) => t.classList.toggle('now', t.dataset.pkg === pkg));
        },
      };
      $('#apps').addEventListener('click', async (e) => {
        const t = e.target.closest('.app-tile');
        if (!t) return;
        haptic(true);
        const r = outcome(await send('open', { pkg: t.dataset.pkg }));
        if (r?.result === 'ok') { toast(`Opening ${t.querySelector('.name').textContent}`); switchTo('remote'); }
      });
    }
  }

  // ---------- The touchpad ----------
  // A swipe moves one step every 52 px along its main direction, more for a quick flick; a short
  // tap that hardly moved is OK. The light follows the finger and the edge it moves toward glows.
  function touchpad(pad, press) {
    const light = pad.querySelector('.light'), chev = pad.querySelector('.chev');
    const STEP = 52;
    let id = null, sx = 0, sy = 0, ax = 0, ay = 0, t0 = 0, moved = false, axis = null, steps = 0, lastX = 0, lastY = 0, lastT = 0, vx = 0, vy = 0;
    const rot = { up: '0deg', right: '90deg', down: '180deg', left: '270deg' };
    const fire = (dir) => {
      steps++;
      press(dir);
      haptic();
      const edge = pad.querySelector(`.edge.${dir}`);
      edge.classList.add('lit');
      setTimeout(() => edge.classList.remove('lit'), 90);
      chev.style.setProperty('--r', rot[dir]);
      chev.classList.remove('go'); void chev.offsetWidth; chev.classList.add('go');
    };
    const at = (e) => { const r = pad.getBoundingClientRect(); return [e.clientX - r.left, e.clientY - r.top]; };

    pad.addEventListener('pointerdown', (e) => {
      if (id !== null) return;
      id = e.pointerId;
      pad.setPointerCapture(id);
      [sx, sy] = at(e);
      ax = sx; ay = sy; lastX = sx; lastY = sy; lastT = t0 = performance.now(); vx = vy = 0;
      moved = false; axis = null; steps = 0;
      pad.classList.add('touching', 'used');
      light.style.transform = `translate(${sx}px, ${sy}px)`;
    });
    pad.addEventListener('pointermove', (e) => {
      if (e.pointerId !== id) return;
      const [x, y] = at(e);
      const now = performance.now(), dt = Math.max(1, now - lastT);
      vx = vx * 0.6 + ((x - lastX) / dt) * 0.4; vy = vy * 0.6 + ((y - lastY) / dt) * 0.4;
      lastX = x; lastY = y; lastT = now;
      light.style.transform = `translate(${x}px, ${y}px)`;
      const dx = x - ax, dy = y - ay;
      if (!moved && Math.hypot(x - sx, y - sy) > 10) moved = true;
      if (!moved) return;
      // The axis follows the finger, so an L-shaped swipe can turn a corner
      const nowAxis = Math.abs(dx) > Math.abs(dy) ? 'x' : 'y';
      if (axis && nowAxis !== axis && Math.max(Math.abs(dx), Math.abs(dy)) > STEP * 0.6) { axis = nowAxis; }
      if (!axis) axis = nowAxis;
      if (axis === 'x' && Math.abs(dx) >= STEP) { fire(dx > 0 ? 'right' : 'left'); ax += Math.sign(dx) * STEP; ay = y; }
      else if (axis === 'y' && Math.abs(dy) >= STEP) { fire(dy > 0 ? 'down' : 'up'); ay += Math.sign(dy) * STEP; ax = x; }
    });
    const end = (e) => {
      if (e.pointerId !== id) return;
      id = null;
      pad.classList.remove('touching');
      const dt = performance.now() - t0;
      if (!moved && dt < 450) {
        const [x, y] = at(e);
        const ring = document.createElement('i');
        ring.className = 'ring';
        ring.style.left = `${x}px`; ring.style.top = `${y}px`;
        pad.append(ring);
        setTimeout(() => ring.remove(), 520);
        press('ok');
        haptic(true);
        return;
      }
      // A flick glides on a little, like the Siri Remote: up to four more steps
      const v = axis === 'x' ? vx : vy;
      const speed = Math.abs(v);
      if (e.type === 'pointerup' && speed > 0.9 && moved) {
        const extra = Math.min(4, Math.floor((speed - 0.6) * 2.2));
        const dir = axis === 'x' ? (v > 0 ? 'right' : 'left') : (v > 0 ? 'down' : 'up');
        for (let i = 0; i < extra; i++) setTimeout(() => fire(dir), 70 * (i + 1) + i * i * 12);
      }
    };
    pad.addEventListener('pointerup', end);
    pad.addEventListener('pointercancel', end);
    pad.addEventListener('contextmenu', (e) => e.preventDefault());
  }

  // ---------- Voice ----------
  // Safari's speech recognition where it is allowed; it needs a secure page, which the TV's
  // page (http) is not, so there the keyboard's dictation key takes over.
  function makeVoice({ sheet, heard, orb, note, scrim, done, fallback }) {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    let rec = null, final = '', active = false;
    const lang = (navigator.language || 'sv-SE').toLowerCase().startsWith('sv') ? 'sv-SE' : (navigator.language || 'en-US');
    const close = () => { sheet.classList.remove('on'); scrim.classList.remove('on'); active = false; };
    return {
      start() {
        if (!SR || !window.isSecureContext) { fallback(); return; }
        try {
          rec = new SR();
          rec.lang = lang;
          rec.interimResults = true;
          rec.maxAlternatives = 1;
          rec.continuous = false;
        } catch { fallback(); return; }
        final = '';
        heard.textContent = 'Listening…'; heard.classList.add('dim');
        note.textContent = 'Try “open YouTube”, “play city pop on Spotify” or a film.';
        orb.classList.remove('quiet');
        sheet.classList.add('on'); scrim.classList.add('on');
        active = true;
        rec.onresult = (e) => {
          let text = '';
          for (let i = 0; i < e.results.length; i++) text += e.results[i][0].transcript;
          final = text.trim();
          heard.textContent = final || 'Listening…';
          heard.classList.toggle('dim', !final);
        };
        rec.onerror = (e) => {
          if (e.error === 'not-allowed' || e.error === 'service-not-allowed') { close(); fallback(); return; }
          orb.classList.add('quiet');
          note.textContent = e.error === 'no-speech' ? 'Didn’t hear anything. Tap the mic and try again.' : 'Couldn’t listen just now.';
          setTimeout(close, 1400);
        };
        rec.onend = () => {
          if (!active) return;
          orb.classList.add('quiet');
          if (final) { haptic(true); setTimeout(() => { close(); done(final); }, 350); }
          else setTimeout(close, 900);
        };
        try { rec.start(); } catch { close(); fallback(); }
      },
      stop() { if (active) { active = false; try { rec?.abort(); } catch { /* gone */ } close(); } },
    };
  }
})();
