// Original neon city-pop scenes for the streaming service cards. Each scene draws a
// background (sky, light, floor) and a foreground subject on its own layer, so the app
// can move them apart for a depth effect on focus.
//
// L = { W, H, cx, gy, s }: the frame, the subject's ground centre and its scale.
// Subjects are drawn in local units: origin on the ground, x in about [-120, 120], y up to -170.
const { f, rng, sparkle, stars, commonDefs, helpers, floorGrid, cubicPoint, twinkles, wave, keys } = require('./lib');

// ---------------------------------------------------------------------------------------
// Netflix: movie night at home. A red-lit 80s TV with rabbit ears, popcorn and tapes.
function nfx(L) {
  const { X, Y, S, skyTop } = helpers(L);
  // Warm string lights hung across the wall, one strand per frame width
  const bulbs = [];
  function fairyLights(L, skyTop) {
    const yTop = Y(Math.max(skyTop * 0.94, -186)), sag = S(16);
    let out = `<path d="M-10,${f(yTop)} Q${f(L.W / 2)},${f(yTop + sag * 2)} ${L.W + 10},${f(yTop)}" stroke="#3a1420" stroke-width="${f(S(1))}" fill="none"/>`;
    const n = Math.round(L.W / S(22));
    for (let i = 0; i <= n; i++) {
      const t = i / n, x = -10 + (L.W + 20) * t, y = yTop + sag * 2 * 2 * t * (1 - t);
      const c = i % 3 === 0 ? '#ffd27a' : i % 3 === 1 ? '#ff6b75' : '#ffe9c2';
      out += `<circle cx="${f(x)}" cy="${f(y + S(3))}" r="${f(S(4))}" fill="${c}" opacity="0.35" filter="url(#b2)"/><circle cx="${f(x)}" cy="${f(y + S(3))}" r="${f(S(1.6))}" fill="${c}"/>`;
      bulbs.push([f((x - L.cx) / L.s), f((y + S(3) - L.gy) / L.s), 9, f((i * 0.37) % 1)]);
    }
    return out;
  }
  const horizon = Y(-30);
  const defs = `
  <linearGradient id="nfxWall" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="${f(horizon)}">
    <stop offset="0" stop-color="#0a0206"/><stop offset="0.55" stop-color="#22050e"/><stop offset="1" stop-color="#420b1a"/>
  </linearGradient>
  <linearGradient id="nfxFloor" gradientUnits="userSpaceOnUse" x1="0" y1="${f(horizon)}" x2="0" y2="${L.H}">
    <stop offset="0" stop-color="#2e0811"/><stop offset="1" stop-color="#0a0205"/>
  </linearGradient>
  <radialGradient id="nfxHalo" gradientUnits="userSpaceOnUse" cx="${f(X(-16))}" cy="${f(Y(-78))}" r="${f(S(240))}">
    <stop offset="0" stop-color="#ff2b3a" stop-opacity="0.55"/><stop offset="0.3" stop-color="#ff2b3a" stop-opacity="0.2"/><stop offset="1" stop-color="#ff2b3a" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="nfxRay" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="#ff4d5a" stop-opacity="0.32"/><stop offset="1" stop-color="#ff4d5a" stop-opacity="0"/>
  </linearGradient>
  <radialGradient id="nfxScreen" cx="0.42" cy="0.4" r="0.8">
    <stop offset="0" stop-color="#ff9097"/><stop offset="0.45" stop-color="#ff2b3a"/><stop offset="1" stop-color="#7d0614"/>
  </radialGradient>
  <linearGradient id="nfxBody" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#5a1a2e"/><stop offset="0.5" stop-color="#3a0f1e"/><stop offset="1" stop-color="#1c0610"/>
  </linearGradient>
  <linearGradient id="nfxBucketShade" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="#000" stop-opacity="0.35"/><stop offset="0.35" stop-color="#000" stop-opacity="0"/><stop offset="0.8" stop-color="#000" stop-opacity="0.05"/><stop offset="1" stop-color="#000" stop-opacity="0.4"/>
  </linearGradient>
  <clipPath id="nfxScreenClip"><rect x="-64" y="-114" width="94" height="74" rx="17"/></clipPath>
  <clipPath id="nfxBucketClip"><path d="M80,-48 L120,-48 L115,0 L85,0 Z"/></clipPath>`;

  // Light fanning out from the screen
  let rays = '';
  const ox = X(-17), oy = Y(-77);
  [-168, -146, -124, -56, -34, -12, 12, 168].forEach((deg, i) => {
    const len = S(260 + (i % 3) * 40), w = S(14 + (i % 2) * 10);
    rays += `<g transform="translate(${f(ox)},${f(oy)}) rotate(${deg})"><polygon points="0,${f(-S(3))} ${f(len)},${f(-w)} ${f(len)},${f(w)} 0,${f(S(3))}" fill="url(#nfxRay)"/></g>`;
  });

  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#nfxWall)"/>
  <rect y="${f(horizon)}" width="${L.W}" height="${f(L.H - horizon)}" fill="url(#nfxFloor)"/>
  ${rays}
  <rect width="${L.W}" height="${L.H}" fill="url(#nfxHalo)"/>
  ${floorGrid(L, horizon, '#ff3a4a', { opacity: 0.22, rows: 5 })}
  <line x1="0" y1="${f(horizon)}" x2="${L.W}" y2="${f(horizon)}" stroke="#ff5a66" stroke-width="${f(S(1.2))}" opacity="0.55"/>
  <ellipse cx="${f(X(-12))}" cy="${f(Y(6))}" rx="${f(S(120))}" ry="${f(S(16))}" fill="#ff2b3a" opacity="0.32" filter="url(#b8)"/>
  ${stars(L, 26, 11, horizon - S(20), '#ffb3ba')}
  ${fairyLights(L, skyTop)}`;

  // Popcorn: puffs of cream with a warm shadow
  const r = rng(5);
  let popcorn = '';
  for (let i = 0; i < 16; i++) {
    const x = 82 + r() * 36, y = -50 - r() * 13 + Math.abs(x - 100) * 0.25, rad = 4.2 + r() * 3.2;
    popcorn += `<circle cx="${f(x)}" cy="${f(y)}" r="${f(rad)}" fill="#fff3d6"/><circle cx="${f(x + rad * 0.3)}" cy="${f(y + rad * 0.35)}" r="${f(rad * 0.55)}" fill="#f2c98a" opacity="0.7"/>`;
  }
  let stripes = '';
  for (let i = 0; i < 5; i++) {
    const t0 = 80 + i * 8, t1 = t0 + 8, b0 = 85 + i * 6, b1 = b0 + 6;
    stripes += `<polygon points="${t0},-48 ${t1},-48 ${b1},0 ${b0},0" fill="${i % 2 ? '#fff1f2' : '#ff2b3a'}"/>`;
  }
  let scan = '';
  for (let y = -112; y < -40; y += 3) scan += `<line x1="-64" y1="${y}" x2="30" y2="${y}"/>`;

  const fg = `
  <ellipse cx="0" cy="0" rx="118" ry="6" fill="#000" opacity="0.55" filter="url(#b4)"/>
  <!-- rabbit ears -->
  <g stroke="#d9b3c0" stroke-width="2.2" stroke-linecap="round">
    <line x1="-5" y1="-131" x2="-46" y2="-167"/><line x1="5" y1="-131" x2="42" y2="-169"/>
  </g>
  <circle cx="-46" cy="-167" r="3.4" fill="#ffd6dc"/><circle cx="42" cy="-169" r="3.4" fill="#ffd6dc"/>
  <ellipse cx="0" cy="-130" rx="14" ry="6" fill="#2b0c18" stroke="#ff5a66" stroke-opacity="0.6"/>
  <!-- legs -->
  <path d="M-62,-26 L-52,-26 L-57,0 L-67,0 Z M52,-26 L62,-26 L67,0 L57,0 Z" fill="#1a0610" stroke="#ff4d5a" stroke-opacity="0.35" stroke-width="0.8"/>
  <!-- cabinet -->
  <rect x="-82" y="-130" width="164" height="106" rx="14" fill="url(#nfxBody)"/>
  <rect x="-82" y="-130" width="164" height="106" rx="14" fill="none" stroke="#ff5a66" stroke-opacity="0.7" stroke-width="1.4"/>
  <path d="M-68,-127.5 H68" stroke="#ffb0b8" stroke-opacity="0.45" stroke-width="1.2" stroke-linecap="round"/>
  <!-- screen glow, bezel and picture -->
  <rect x="-68" y="-118" width="102" height="82" rx="20" fill="#ff2b3a" opacity="0.85" filter="url(#b8)"/>
  <rect x="-70" y="-120" width="106" height="86" rx="19" fill="#0f0308"/>
  <g clip-path="url(#nfxScreenClip)">
    <rect x="-64" y="-114" width="94" height="74" fill="url(#nfxScreen)"/>
    <path d="M-44,-118 C-30,-92 -52,-66 -36,-36" stroke="#ffc2c7" stroke-width="9" fill="none" opacity="0.45" filter="url(#b2)"/>
    <path d="M-20,-118 C-4,-90 -28,-64 -10,-36" stroke="#ffffff" stroke-width="13" fill="none" opacity="0.5" filter="url(#b2)"/>
    <path d="M6,-118 C18,-90 0,-62 16,-36" stroke="#ff6b75" stroke-width="8" fill="none" opacity="0.75" filter="url(#b1)"/>
    <g stroke="#3a0008" stroke-width="1" opacity="0.22">${scan}</g>
    <path d="M-58,-106 Q-50,-112 -30,-112" stroke="#fff" stroke-width="5" stroke-linecap="round" fill="none" opacity="0.28" filter="url(#b1)"/>
  </g>
  <!-- knobs and speaker -->
  <circle cx="59" cy="-104" r="8.5" fill="#2a0c17" stroke="#ff6b75" stroke-opacity="0.7"/>
  <line x1="59" y1="-104" x2="62" y2="-111" stroke="#ffd0d5" stroke-width="1.8" stroke-linecap="round"/>
  <circle cx="59" cy="-80" r="8.5" fill="#2a0c17" stroke="#ff6b75" stroke-opacity="0.7"/>
  <line x1="59" y1="-80" x2="53" y2="-85" stroke="#ffd0d5" stroke-width="1.8" stroke-linecap="round"/>
  <g stroke="#ff8a93" stroke-opacity="0.45" stroke-width="1.6" stroke-linecap="round">
    <line x1="48" y1="-62" x2="70" y2="-62"/><line x1="48" y1="-56" x2="70" y2="-56"/><line x1="48" y1="-50" x2="70" y2="-50"/><line x1="48" y1="-44" x2="70" y2="-44"/>
  </g>
  <circle cx="69" cy="-33" r="2" fill="#ff2b3a" filter="url(#glow)"/>
  <!-- popcorn -->
  <g>${popcorn}</g>
  <g clip-path="url(#nfxBucketClip)">${stripes}<rect x="78" y="-50" width="44" height="52" fill="url(#nfxBucketShade)"/></g>
  <path d="M79,-50 L121,-50 L120,-45 L80,-45 Z" fill="#ffe3e6"/>
  <path d="M80,-48 L120,-48 L115,0 L85,0 Z" fill="none" stroke="#ff8a93" stroke-opacity="0.6" stroke-width="0.9"/>
  <circle cx="74" cy="-3" r="3.4" fill="#fff3d6"/><circle cx="128" cy="-2.5" r="3" fill="#fff3d6"/><circle cx="66" cy="-2" r="2.4" fill="#f7dcae"/>
  <!-- tapes -->
  <g>
    <rect x="-124" y="-11" width="40" height="11" rx="1.5" fill="#160810" stroke="#ff5a66" stroke-opacity="0.5" stroke-width="0.8"/>
    <rect x="-116" y="-8" width="24" height="5" rx="1" fill="#ffe8ec" opacity="0.9"/>
    <rect x="-121" y="-22" width="40" height="11" rx="1.5" fill="#1d0a14" stroke="#ff5a66" stroke-opacity="0.5" stroke-width="0.8"/>
    <rect x="-113" y="-19" width="24" height="5" rx="1" fill="#ff2b3a" opacity="0.95"/>
  </g>`;
  // The set flickers like a picture changing, the lights twinkle, popcorn pops out of the bucket
  const flicker = keys(2.6, [[0, 0.5], [0.3, 0.75], [0.45, 0.4], [0.9, 0.62], [1.3, 0.5], [1.38, 0.85, 1], [1.46, 0.45], [2.1, 0.6]]);
  return {
    defs, bg, fg,
    fx: [
      { type: 'dots', layer: 'scene', pts: bulbs, color: '#ffe3a8', a: 0.15, tw: [0.55, 1.6] },
      { type: 'glow', layer: 'top', x: -17, y: -77, r: 78, color: '#ff2b3a', a: flicker },
      { type: 'particles', layer: 'top', shape: 'dot', n: 3, seed: 3, life: 1.3, from: [100, -60], w: 18, arc: [245, 295], speed: [70, 95], g: 190, size: [5, 7], color: ['#fff3d6', '#f7dcae'], fade: [0.02, 0.15] },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// HBO Max: epic fantasy. A dragon crossing a huge moon above a castle on a crag.
function hbm(L) {
  const { X, Y, S, halfW } = helpers(L);
  const horizon = Y(-34);
  const moon = { x: X(54), y: Y(-114), r: S(52) };
  const defs = `
  <linearGradient id="hbmSky" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="${f(horizon)}">
    <stop offset="0" stop-color="#06031a"/><stop offset="0.5" stop-color="#1c0e46"/><stop offset="1" stop-color="#53288f"/>
  </linearGradient>
  <radialGradient id="hbmMoon" cx="0.4" cy="0.38" r="0.7">
    <stop offset="0" stop-color="#fffbff"/><stop offset="0.7" stop-color="#e6d9ff"/><stop offset="1" stop-color="#c3abff"/>
  </radialGradient>
  <radialGradient id="hbmHalo" gradientUnits="userSpaceOnUse" cx="${f(moon.x)}" cy="${f(moon.y)}" r="${f(S(150))}">
    <stop offset="0.3" stop-color="#b48cff" stop-opacity="0.5"/><stop offset="0.6" stop-color="#9d6bff" stop-opacity="0.14"/><stop offset="1" stop-color="#9d6bff" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="hbmRock" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="#0d0620"/><stop offset="1" stop-color="#1a0c38"/>
  </linearGradient>`;

  const ridge = (y0, amp, seed, fill, step) => {
    const r = rng(seed); let d = `M0,${f(L.H)} L0,${f(y0)}`;
    for (let x = 0; x <= L.W + step; x += step) d += ` L${f(x)},${f(y0 - S(amp * (0.3 + r())))}`;
    return `<path d="${d} L${L.W},${f(L.H)} Z" fill="${fill}"/>`;
  };
  const craters = [[-18, -14, 9], [14, 8, 12], [-6, 22, 6], [22, -20, 5], [-28, 10, 4]]
    .map(([dx, dy, rr]) => `<circle cx="${f(moon.x + S(dx))}" cy="${f(moon.y + S(dy))}" r="${f(S(rr))}" fill="#b9a2f0" opacity="0.35"/>`).join('');

  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#hbmSky)"/>
  ${stars(L, 70, 21, horizon, '#e9dcff')}
  <rect width="${L.W}" height="${L.H}" fill="url(#hbmHalo)"/>
  <circle cx="${f(moon.x)}" cy="${f(moon.y)}" r="${f(moon.r)}" fill="url(#hbmMoon)"/>
  ${craters}
  ${ridge(horizon + S(4), 26, 3, '#2c1766', S(22))}
  ${ridge(horizon + S(14), 18, 8, '#1d0f4a', S(16))}
  <rect y="${f(horizon + S(2))}" width="${L.W}" height="${f(S(18))}" fill="#c9a8ff" opacity="0.12" filter="url(#b8)"/>
  <rect y="${f(horizon + S(16))}" width="${L.W}" height="${f(L.H)}" fill="#120830"/>`;

  // Battlements along the top of a wall or tower
  const merlons = (x0, x1, y, h = 5, w = 5, gap = 4) => {
    let d = '';
    for (let x = x0; x + w <= x1 + 0.01; x += w + gap) d += `M${x},${y} h${w} v${-h} h${-w} Z `;
    return d;
  };
  const castle = `
    M-104,-58 L-104,-118 L-82,-118 L-82,-84 L-50,-84 L-50,-142 L-38,-142 L-38,-164 L-26,-164 L-26,-142 L-14,-142 L-14,-84 L14,-84 L14,-108 L34,-108 L34,-84 L38,-84 L38,-58 Z
    ${merlons(-104, -82, -118)} ${merlons(-50, -14, -142)} ${merlons(-38, -26, -164, 4, 4, 4)} ${merlons(14, 34, -108)} ${merlons(-82, -50, -84, 4, 4, 4)} ${merlons(-14, 14, -84, 4, 4, 4)}`;
  const windows = [[-95, -104], [-90, -92], [-35, -128], [-29, -128], [-32, -112], [22, -96], [-66, -72], [-2, -72], [26, -72]]
    .map(([x, y]) => `<rect x="${x}" y="${y}" width="3.4" height="6.5" rx="1.7" fill="#ffc271"/>`).join('');

  // The dragon flies on its own layers: a wing behind, the body, a wing in front, so the
  // wings can beat round the shoulder while the whole of it glides
  const dg = svg => `<g transform="translate(${f(moon.x)},${f(moon.y + S(8))}) scale(${f(L.s * 0.74)})">${svg}</g>`;
  const wingBack = dg(`<path d="M-14,-5 C-20,-20 -18,-34 -8,-44 L-6,-50 L-3,-43 C6,-42 16,-36 22,-26 Q14,-24 12,-14 Q6,-16 2,-6 Z" fill="#1b0f3a"/>`);
  const body = dg(`<path d="M-46,-8 C-44,-11 -40,-13 -36,-13 L-31,-20 L-30,-13 L-25,-17 L-25,-11 C-18,-9 -12,-6 -6,-4 C6,-3 18,-1 30,2 C42,5 54,4 64,-1 L74,-5 L69,3 L76,8 L64,6 C52,11 40,12 28,10 C16,8 4,8 -6,5 C-14,3 -20,0 -27,-3 C-31,-4 -36,-4 -40,-4 L-47,-5 Z" fill="#0c0620"/>
    <path d="M6,8 l-3,6 l4,-1 M18,9 l-2,6 l4,-1" stroke="#0c0620" stroke-width="2.4" stroke-linecap="round" fill="none"/>`);
  const wingFront = dg(`<path d="M-8,-4 C-10,-22 -2,-40 12,-50 L16,-56 L18,-49 C32,-49 48,-40 60,-28 Q50,-25 47,-13 Q40,-17 32,-5 Q24,-9 16,0 Z" fill="#0c0620"/>
    <path d="M18,-49 L47,-13 M18,-49 L32,-5 M18,-49 L60,-28" stroke="#3a2470" stroke-width="1" opacity="0.8"/>`);
  const toLocal = (dx, dy) => [f((moon.x + dx * L.s * 0.74 - L.cx) / L.s), f((moon.y + S(8) + dy * L.s * 0.74 - L.gy) / L.s)];
  const glide = { dx: wave(7, 6.2), dy: wave(4, 3.1, 0.2) };
  const beat = (amp, ph) => ({ ...glide, rot: wave(amp, 0.95, ph) });

  const fg = `
  <g>
    <path d="M-150,0 L-150,-26 L-134,-30 L-124,-40 L-112,-48 L-104,-58 L-84,-60 L-40,-62 L0,-60 L28,-58 L40,-54 L52,-46 L64,-44 L76,-36 L90,-30 L102,-22 L118,-16 L132,-8 L150,-6 L150,0 Z" fill="url(#hbmRock)"/>
    <path d="M-104,-58 L-84,-60 L-40,-62 L0,-60 L28,-58 L40,-54 L52,-46 L64,-44 L76,-36 L90,-30 L102,-22 L118,-16 L132,-8 L150,-6" stroke="#8f6bff" stroke-opacity="0.45" stroke-width="1.2" fill="none"/>
    <path d="M-120,-36 L-100,-46 M-70,-50 L-48,-54 M70,-30 L88,-24 M104,-16 L120,-10 M-136,-22 L-122,-28" stroke="#8f6bff" stroke-opacity="0.3" stroke-width="1" fill="none"/>
    <path d="${castle}" fill="#140a2c"/>
    <path d="M-82,-118 L-82,-84 M-14,-142 L-14,-84 M-26,-164 L-26,-142 M34,-108 L34,-84 L38,-84 L38,-58" stroke="#a98bff" stroke-opacity="0.55" stroke-width="1.1" fill="none"/>
    <g filter="url(#glow)">${windows}</g>
    <path d="M-32,-164 L-32,-178" stroke="#140a2c" stroke-width="1.6"/>
    <path d="M-32,-178 L-20,-174.5 L-32,-171 Z" fill="#9d6bff"/>
  </g>`;
  const lit = [[-95, -104], [-90, -92], [-35, -128], [-29, -128], [-32, -112], [22, -96], [-66, -72], [-2, -72], [26, -72]]
    .map(([x, y], i) => [x + 1.7, y + 3.2, 9, f((i * 0.29) % 1)]);
  return {
    defs, bg, fg,
    parts: [
      { name: 'wingBack', group: 'subject', frame: true, svg: wingBack, pivot: toLocal(-4, -6), m: beat(-16, 0.08) },
      { name: 'body', group: 'subject', frame: true, svg: body, pivot: toLocal(4, 0), m: { ...glide, rot: wave(2, 3.1, 0.45) } },
      { name: 'wingFront', group: 'subject', frame: true, svg: wingFront, pivot: toLocal(-2, -4), m: beat(-18, 0) },
    ],
    fx: [
      { type: 'dots', layer: 'scene', pts: twinkles(L, 70, 21, horizon), color: '#e9dcff', a: 0.2, tw: [0.5, 2.4] },
      { type: 'glow', layer: 'scene', x: f((moon.x - L.cx) / L.s), y: f((moon.y - L.gy) / L.s), r: 110, color: '#b48cff', a: wave(0.1, 4.2, 0, 0.25) },
      { type: 'dots', layer: 'top', keep: true, pts: lit, color: '#ffc271', a: 0.2, tw: [0.25, 1.3] },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// Disney+: a fairy-tale castle under fireworks, with a shooting star sweeping over it.
function dnp(L) {
  const { X, Y, S, halfW, skyTop } = helpers(L);
  const horizon = Y(-26);
  const defs = `
  <linearGradient id="dnpSky" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="${f(horizon)}">
    <stop offset="0" stop-color="#040722"/><stop offset="0.55" stop-color="#11206a"/><stop offset="1" stop-color="#3a3cb4"/>
  </linearGradient>
  <linearGradient id="dnpWall" gradientUnits="userSpaceOnUse" x1="0" y1="-172" x2="0" y2="0">
    <stop offset="0" stop-color="#f4f1ff"/><stop offset="0.6" stop-color="#b8b0f4"/><stop offset="1" stop-color="#7b70d8"/>
  </linearGradient>
  <linearGradient id="dnpWallBack" gradientUnits="userSpaceOnUse" x1="0" y1="-150" x2="0" y2="0">
    <stop offset="0" stop-color="#c9c2fa"/><stop offset="1" stop-color="#6a5fc6"/>
  </linearGradient>
  <linearGradient id="dnpRoof" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="#4a5cf0"/><stop offset="0.5" stop-color="#2a33a8"/><stop offset="1" stop-color="#171d6e"/>
  </linearGradient>
  <radialGradient id="dnpBase" gradientUnits="userSpaceOnUse" cx="${f(X(0))}" cy="${f(Y(-20))}" r="${f(S(170))}">
    <stop offset="0" stop-color="#8f7dff" stop-opacity="0.55"/><stop offset="1" stop-color="#8f7dff" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="dnpTrail" gradientUnits="userSpaceOnUse" x1="-128" y1="-24" x2="118" y2="-122">
    <stop offset="0" stop-color="#ffffff" stop-opacity="0"/><stop offset="0.55" stop-color="#bfe9ff" stop-opacity="0.55"/><stop offset="1" stop-color="#ffffff" stop-opacity="1"/>
  </linearGradient>`;

  const burst = (lx, ly, R, color, seed, rays = 20) => {
    const r = rng(seed); const cx = X(lx), cy = Y(ly), RR = S(R);
    let out = `<circle cx="${f(cx)}" cy="${f(cy)}" r="${f(RR * 1.1)}" fill="${color}" opacity="0.16" filter="url(#b8)"/>`;
    out += `<g stroke="${color}" stroke-linecap="round" stroke-width="${f(S(1.4))}">`;
    for (let i = 0; i < rays; i++) {
      const a = (i / rays) * Math.PI * 2 + r() * 0.12;
      const r0 = RR * (0.28 + r() * 0.12), r1 = RR * (0.78 + r() * 0.22);
      out += `<line x1="${f(cx + Math.cos(a) * r0)}" y1="${f(cy + Math.sin(a) * r0)}" x2="${f(cx + Math.cos(a) * r1)}" y2="${f(cy + Math.sin(a) * r1)}" opacity="${f(0.55 + r() * 0.45)}"/>`;
    }
    out += '</g>';
    for (let i = 0; i < rays; i++) {
      const a = (i / rays) * Math.PI * 2 + 0.16;
      const rr = RR * (1.02 + r() * 0.1);
      out += `<circle cx="${f(cx + Math.cos(a) * rr)}" cy="${f(cy + Math.sin(a) * rr)}" r="${f(S(1.3))}" fill="#fff" opacity="0.9"/>`;
    }
    return out + `<circle cx="${f(cx)}" cy="${f(cy)}" r="${f(S(2.2))}" fill="#fff"/>`;
  };
  const fireworks = [
    burst(-halfW * 0.66, skyTop * 0.6, 30, '#ff6fd8', 1),
    burst(halfW * 0.7, skyTop * 0.74, 26, '#ffd36b', 2, 18),
    burst(-halfW * 0.2, skyTop * 0.9, 18, '#7ff3ff', 3, 16),
    burst(halfW * 0.34, skyTop * 0.45, 14, '#b58cff', 4, 14),
    skyTop < -240 ? burst(-halfW * 0.4, skyTop * 0.72, 22, '#7ff3ff', 5, 16) + burst(halfW * 0.4, skyTop * 0.6, 18, '#ff6fd8', 6) : ''
  ].join('');

  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#dnpSky)"/>
  ${stars(L, 70, 31, horizon, '#dfe6ff')}
  ${fireworks}
  <path d="M0,${f(horizon)} Q${f(L.W * 0.25)},${f(horizon - S(14))} ${f(L.W * 0.5)},${f(horizon - S(4))} T${L.W},${f(horizon - S(10))} L${L.W},${L.H} L0,${L.H} Z" fill="#1a1a5e"/>
  <rect width="${L.W}" height="${L.H}" fill="url(#dnpBase)"/>
  <rect y="${f(horizon + S(8))}" width="${L.W}" height="${L.H}" fill="#0d0e3c"/>`;

  // A pointed fairy-tale roof on a tower from x0 to x1 at y, with its tip at (ax, ay)
  const cone = (x0, x1, y, ay) => {
    const ax = (x0 + x1) / 2, w = x1 - x0;
    return `<path d="M${x0 - 3},${y} C${x0 + w * 0.12},${y - 6} ${ax - 2},${ay + (y - ay) * 0.45} ${ax},${ay} C${ax + 2},${ay + (y - ay) * 0.45} ${x1 - w * 0.12},${y - 6} ${x1 + 3},${y} Z" fill="url(#dnpRoof)"/>
            <path d="M${ax},${ay} L${ax},${ay - 9}" stroke="#e8e6ff" stroke-width="1"/>`;
  };
  const flag = (x, y, c) => `<path d="M${x},${y - 9} L${x + 9},${y - 6.5} L${x},${y - 4} Z" fill="${c}"/>`;
  const tower = (x0, x1, top, bottom, fill = 'url(#dnpWall)') => `<rect x="${x0}" y="${top}" width="${x1 - x0}" height="${bottom - top}" fill="${fill}"/>`;
  const win = (x, y, w = 3.6, h = 7) => `<path d="M${x},${y + h} v${-h + w / 2} a${w / 2},${w / 2} 0 0 1 ${w},0 v${h - w / 2} Z" fill="#ffe7a1"/>`;

  // The shooting star's trail
  const p0 = [-128, -24], p1 = [-96, -150], p2 = [44, -196], p3 = [118, -122];
  let trailSparks = '';
  const rs = rng(9);
  for (let i = 0; i < 16; i++) {
    const t = 0.35 + (i / 16) * 0.62, [x, y] = cubicPoint(p0, p1, p2, p3, t);
    trailSparks += sparkle(x + (rs() - 0.5) * 14, y + (rs() - 0.5) * 14, 1.2 + rs() * 2.6 * t, '#ffffff', 0.4 + t * 0.6);
  }

  const fg = `
  ${tower(-40, -28, -118, -40, 'url(#dnpWallBack)')}${cone(-40, -28, -118, -148)}${flag(-34, -148, '#ff6fd8')}
  ${tower(28, 40, -112, -40, 'url(#dnpWallBack)')}${cone(28, 40, -112, -142)}
  ${tower(-28, 28, -96, -40)}
  ${tower(-15, 15, -124, -96)}${cone(-20, 20, -124, -172)}${flag(0, -172, '#ffd36b')}
  <path d="M-28,-96 h56" stroke="#7b70d8" stroke-width="1.4"/>
  ${tower(-72, -50, -92, -24)}${cone(-72, -50, -92, -130)}${flag(-61, -130, '#7ff3ff')}
  ${tower(50, 72, -86, -24)}${cone(50, 72, -86, -122)}${flag(61, -122, '#ff6fd8')}
  ${tower(-98, -84, -58, -20)}${cone(-98, -84, -58, -82)}
  ${tower(84, 98, -54, -20)}${cone(84, 98, -54, -78)}
  <path d="M-104,0 L-104,-40 Q-100,-46 -96,-40 Q-92,-46 -88,-40 Q-84,-46 -80,-40 L80,-40 Q84,-46 88,-40 Q92,-46 96,-40 Q100,-46 104,-40 L104,0 Z" fill="url(#dnpWall)"/>
  <path d="M-14,0 L-14,-16 A14,14 0 0 1 14,-16 L14,0 Z" fill="#2a2274"/>
  <path d="M-10,0 L-10,-15 A10,10 0 0 1 10,-15 L10,0 Z" fill="#ffd77a" opacity="0.85" filter="url(#b1)"/>
  <g filter="url(#glow)">
    ${win(-36, -108)}${win(32, -102)}${win(-3.6, -116)}${win(-12, -84)}${win(8, -84)}${win(-63, -78)}${win(59, -72)}${win(-93, -44, 3, 6)}${win(89, -42, 3, 6)}
    ${win(-60, -34, 3, 6)}${win(-44, -30, 3, 6)}${win(41, -30, 3, 6)}${win(57, -34, 3, 6)}
  </g>
  <path d="M${p0} C${p1} ${p2} ${p3}" stroke="url(#dnpTrail)" stroke-width="5" fill="none" stroke-linecap="round" filter="url(#b2)"/>
  <path d="M${p0} C${p1} ${p2} ${p3}" stroke="url(#dnpTrail)" stroke-width="1.8" fill="none" stroke-linecap="round"/>
  ${trailSparks}
  <circle cx="${p3[0]}" cy="${p3[1]}" r="9" fill="#bfe9ff" opacity="0.6" filter="url(#b4)"/>
  ${sparkle(p3[0], p3[1], 11, '#ffffff', 1)}`;
  // New fireworks bloom over the castle one after another, the shooting star's trail glitters
  const life = 2.4;
  const firework = (x, y, color, off, seed) => [
    { type: 'particles', layer: 'scene', shape: 'glowdot', sync: true, off, n: 20, seed, life, from: [x, y], w: 14, h: 10, speed: [34, 40], g: 16, size: [2, 2.8], color, fade: [0.02, 0.55] },
    { type: 'glow', layer: 'scene', x, y, r: 46, color, a: keys(life, [[0, 0], [f(life - off - 0.01), 0, 1], [f(life - off), 0.55, 1], [f(Math.min(life - off + 0.5, life - 0.001)), 0]].sort((a, b) => a[0] - b[0]), true) },
  ];
  const rt = rng(9), trail = [];
  for (let i = 0; i < 16; i++) {
    const t = 0.35 + (i / 16) * 0.62, [x, y] = cubicPoint(p0, p1, p2, p3, t);
    trail.push([f(x + (rt() - 0.5) * 14), f(y + (rt() - 0.5) * 14), f(4 + t * 6), f((1 - i / 16) * 0.8)]);
  }
  return {
    defs, bg, fg,
    fx: [
      ...firework(-halfW * 0.45, skyTop * 0.62, '#ff6fd8', 0.4, 11),
      ...firework(halfW * 0.5, skyTop * 0.5, '#7ff3ff', 1.2, 12),
      ...firework(halfW * 0.05, skyTop * 0.8, '#ffd36b', 2.0, 13),
      { type: 'dots', layer: 'top', pts: trail, shape: 'spark', color: '#ffffff', a: 0.1, tw: [0.8, 1.1] },
      { type: 'glow', layer: 'top', x: p3[0], y: p3[1], r: 26, color: '#bfe9ff', a: wave(0.25, 1.1, 0, 0.45) },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// Prime Video: it comes in the package. An opened box spilling light, film and a play button.
function amp(L) {
  const { X, Y, S } = helpers(L);
  const horizon = Y(-44);
  const defs = `
  <linearGradient id="ampSky" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="${f(horizon)}">
    <stop offset="0" stop-color="#020a1e"/><stop offset="0.6" stop-color="#062c5a"/><stop offset="1" stop-color="#0b5c98"/>
  </linearGradient>
  <linearGradient id="ampFloor" gradientUnits="userSpaceOnUse" x1="0" y1="${f(horizon)}" x2="0" y2="${L.H}">
    <stop offset="0" stop-color="#06284e"/><stop offset="1" stop-color="#020a18"/>
  </linearGradient>
  <linearGradient id="ampRay" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="#bff4ff" stop-opacity="0.5"/><stop offset="1" stop-color="#2fc4ff" stop-opacity="0"/>
  </linearGradient>
  <radialGradient id="ampBurst" gradientUnits="userSpaceOnUse" cx="${f(X(10))}" cy="${f(Y(-80))}" r="${f(S(130))}">
    <stop offset="0" stop-color="#e8fbff" stop-opacity="0.8"/><stop offset="0.3" stop-color="#2fc4ff" stop-opacity="0.35"/><stop offset="1" stop-color="#2fc4ff" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="ampFront" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#e0a464"/><stop offset="1" stop-color="#a8683a"/>
  </linearGradient>
  <linearGradient id="ampSide" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#9a6234"/><stop offset="1" stop-color="#6e4222"/>
  </linearGradient>
  <radialGradient id="ampInside" cx="0.5" cy="0.6" r="0.7">
    <stop offset="0" stop-color="#ffffff"/><stop offset="0.5" stop-color="#9eeaff"/><stop offset="1" stop-color="#2a9fd8"/>
  </radialGradient>`;

  let rays = '';
  const ox = X(12), oy = Y(-76);
  for (let i = 0; i < 11; i++) {
    const deg = -162 + i * 14.4, len = S(250 + (i % 3) * 50), w = S(i % 2 ? 10 : 18);
    rays += `<g transform="translate(${f(ox)},${f(oy)}) rotate(${f(deg)})"><polygon points="0,${f(-S(4))} ${f(len)},${f(-w)} ${f(len)},${f(w)} 0,${f(S(4))}" fill="url(#ampRay)" opacity="${i % 2 ? 0.7 : 1}"/></g>`;
  }
  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#ampSky)"/>
  ${stars(L, 50, 41, horizon, '#d6f6ff')}
  ${rays}
  <rect y="${f(horizon)}" width="${L.W}" height="${f(L.H - horizon)}" fill="url(#ampFloor)"/>
  ${floorGrid(L, horizon, '#2fc4ff', { opacity: 0.3, rows: 5 })}
  <line x1="0" y1="${f(horizon)}" x2="${L.W}" y2="${f(horizon)}" stroke="#7fe0ff" stroke-width="${f(S(1.2))}" opacity="0.6"/>
  <rect width="${L.W}" height="${L.H}" fill="url(#ampBurst)"/>
  <ellipse cx="${f(X(10))}" cy="${f(Y(2))}" rx="${f(S(110))}" ry="${f(S(12))}" fill="#2fc4ff" opacity="0.35" filter="url(#b8)"/>`;

  // Film strip rising out of the box behind the play button
  const film = 'M-22,-78 C-52,-100 -78,-122 -66,-146 C-54,-168 -18,-158 8,-166 C34,-174 58,-170 78,-152';
  const confetti = [[-92, -120, '#ffd36b'], [-74, -60, '#ff6fd8'], [88, -126, '#7ff3ff'], [98, -86, '#ffd36b'], [-104, -92, '#7ff3ff'], [62, -58, '#ff6fd8']]
    .map(([x, y, c], i) => `<rect x="${x}" y="${y}" width="5" height="2.6" rx="1" fill="${c}" transform="rotate(${i * 37} ${x} ${y})"/>`).join('');
  const fg = `
  <ellipse cx="6" cy="0" rx="104" ry="6" fill="#000" opacity="0.5" filter="url(#b4)"/>
  <!-- glow from inside -->
  <ellipse cx="12" cy="-84" rx="70" ry="34" fill="#9eeaff" opacity="0.55" filter="url(#b14)"/>
  <!-- back flap and opening -->
  <polygon points="-32,-80 84,-80 80,-110 -28,-108" fill="#b77a42"/>
  <polygon points="-32,-80 84,-80 80,-110 -28,-108" fill="none" stroke="#7fe0ff" stroke-opacity="0.6" stroke-width="1"/>
  <polygon points="-58,-66 58,-66 84,-80 -32,-80" fill="url(#ampInside)"/>
  <!-- film and play button rising out -->
  <path d="${film}" stroke="#2fc4ff" stroke-width="24" fill="none" opacity="0.35" filter="url(#b4)"/>
  <path d="${film}" stroke="#061226" stroke-width="21" fill="none"/>
  <path d="${film}" stroke="#d8f6ff" stroke-width="17" fill="none" stroke-dasharray="3.2 3.2"/>
  <path d="${film}" stroke="#061226" stroke-width="11.5" fill="none"/>
  <path d="${film}" stroke="#8fe3ff" stroke-width="10" fill="none" stroke-dasharray="13 3" opacity="0.8"/>
  <path d="${film}" stroke="#ffffff" stroke-width="3" fill="none" stroke-dasharray="13 3" opacity="0.35"/>
  <circle cx="10" cy="-132" r="24" fill="#041a36" opacity="0.85"/>
  <circle cx="10" cy="-132" r="24" fill="none" stroke="#2fc4ff" stroke-width="3" filter="url(#glowBig)"/>
  <path d="M3,-144 L23,-132 L3,-120 Z" fill="#ffffff" stroke="#ffffff" stroke-width="3" stroke-linejoin="round" filter="url(#glow)"/>
  ${sparkle(-34, -128, 6, '#ffffff', 0.9)}${sparkle(52, -112, 4.5, '#bff4ff', 0.9)}${sparkle(38, -160, 3.5, '#ffffff', 0.8)}
  ${confetti}
  <!-- side flaps -->
  <polygon points="-58,-66 -32,-80 -60,-102 -88,-86" fill="#e8ad6c"/>
  <polygon points="-58,-66 -32,-80 -60,-102 -88,-86" fill="none" stroke="#7fe0ff" stroke-opacity="0.75" stroke-width="1"/>
  <polygon points="58,-66 84,-80 112,-92 88,-76" fill="#c98a4e"/>
  <polygon points="58,-66 84,-80 112,-92 88,-76" fill="none" stroke="#7fe0ff" stroke-opacity="0.75" stroke-width="1"/>
  <!-- the box -->
  <polygon points="58,-66 84,-80 84,-12 58,0" fill="url(#ampSide)"/>
  <rect x="-58" y="-66" width="116" height="66" fill="url(#ampFront)"/>
  <rect x="-9" y="-66" width="18" height="30" fill="#f2cf98" opacity="0.85"/>
  <path d="M-9,-36 l3,2 l3,-2 l3,2 l3,-2 l3,2 l3,-2" stroke="#f2cf98" stroke-width="1.4" fill="none" opacity="0.85"/>
  <rect x="20" y="-30" width="28" height="18" rx="1.5" fill="#f6fbff"/>
  <g stroke="#06142c" stroke-width="1"><line x1="24" y1="-25" x2="40" y2="-25"/><line x1="24" y1="-21" x2="36" y2="-21"/></g>
  <g stroke="#06142c" stroke-width="0.9">${[24, 26, 27.5, 30, 31, 33, 35.5, 37, 39, 41, 42.5, 44].map(x => `<line x1="${x}" y1="-18" x2="${x}" y2="-14"/>`).join('')}</g>
  <path d="M-58,0 L-58,-66 L58,-66 L84,-80 L84,-12 L58,0 Z M58,-66 L58,0" fill="none" stroke="#7fe0ff" stroke-opacity="0.85" stroke-width="1.2"/>`;
  // The light out of the box turns slowly, the play button sends out rings, confetti rains
  const { halfW, skyTop } = helpers(L);
  const ring = off => ({ type: 'ring', layer: 'top', x: 10, y: -132, lw: 1.6, color: '#7fe0ff', r: keys(1.8, [[0, 24, 1], [off, 24, 1], [Math.min(off + 1.2, 1.79), 64, 1]].filter((v, i, a) => i === 0 || v[0] > a[i - 1][0])), a: keys(1.8, [[0, 0, 1], [off, 0.7, 1], [Math.min(off + 1.2, 1.79), 0, 1]].filter((v, i, a) => i === 0 || v[0] > a[i - 1][0])) });
  return {
    defs, bg, fg,
    fx: [
      { type: 'rays', layer: 'scene', upper: true, x: 12, y: -76, n: 16, r0: 10, r1: 320, w: 2.4, spin: 6, color: '#bff4ff', a: 0.16 },
      { type: 'glow', layer: 'top', x: 12, y: -84, r: 80, sx: 1.6, color: '#9eeaff', a: wave(0.12, 1.8, 0, 0.3) },
      ring(0.01), ring(0.9),
      { type: 'particles', layer: 'top', shape: 'rect', n: 12, seed: 5, life: 4.2, x: -halfW, w: halfW * 2, y: skyTop - 10, h: 10, vy: (-skyTop + 40) / 4.2, sway: [10, 1.8], size: [4, 6], spin: 160, flipHz: 1.6, color: ['#ffd36b', '#ff6fd8', '#7ff3ff', '#ffffff'], fade: [0.05, 0.15] },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// Apple TV+: big-budget sci-fi. An astronaut over the earth, a retro rainbow in the visor.
function atp(L) {
  const { X, Y, S } = helpers(L);
  const pr = S(330), pc = { x: X(0), y: Y(-46) + pr };
  const rainbow = ['#61bb46', '#fdb827', '#f5821f', '#e03a3e', '#963d97', '#009ddc'];
  const defs = `
  <linearGradient id="atpSky" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="${f(Y(-46))}">
    <stop offset="0" stop-color="#010207"/><stop offset="0.7" stop-color="#070c22"/><stop offset="1" stop-color="#14204a"/>
  </linearGradient>
  <radialGradient id="atpPlanet" gradientUnits="userSpaceOnUse" cx="${f(pc.x - S(60))}" cy="${f(pc.y - pr)}" r="${f(pr * 1.1)}">
    <stop offset="0" stop-color="#2f7fd0"/><stop offset="0.25" stop-color="#0f3c7a"/><stop offset="0.7" stop-color="#071a3c"/><stop offset="1" stop-color="#030a1a"/>
  </radialGradient>
  <radialGradient id="atpSun" gradientUnits="userSpaceOnUse" cx="${f(X(84))}" cy="${f(Y(-45))}" r="${f(S(64))}">
    <stop offset="0" stop-color="#ffffff"/><stop offset="0.2" stop-color="#e8f4ff" stop-opacity="0.9"/><stop offset="1" stop-color="#8fd3ff" stop-opacity="0"/>
  </radialGradient>
  <radialGradient id="atpHelmet" cx="0.38" cy="0.32" r="0.75">
    <stop offset="0" stop-color="#ffffff"/><stop offset="0.6" stop-color="#e4e8f2"/><stop offset="1" stop-color="#9ea6bd"/>
  </radialGradient>
  <linearGradient id="atpSuit" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#f2f4fa"/><stop offset="1" stop-color="#a9b1c6"/>
  </linearGradient>
  <radialGradient id="atpVisor" cx="0.35" cy="0.3" r="0.9">
    <stop offset="0" stop-color="#2b3766"/><stop offset="0.5" stop-color="#0b1130"/><stop offset="1" stop-color="#02040c"/>
  </radialGradient>
  <clipPath id="atpVisorClip"><ellipse cx="0" cy="-100" rx="40" ry="31"/></clipPath>`;

  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#atpSky)"/>
  ${stars(L, 120, 51, Y(-40), '#ffffff')}
  <circle cx="${f(pc.x)}" cy="${f(pc.y)}" r="${f(pr + S(6))}" fill="#4aa8ff" opacity="0.5" filter="url(#b8)"/>
  <circle cx="${f(pc.x)}" cy="${f(pc.y)}" r="${f(pr)}" fill="url(#atpPlanet)"/>
  <circle cx="${f(pc.x)}" cy="${f(pc.y)}" r="${f(pr)}" fill="none" stroke="#cfeaff" stroke-width="${f(S(1.4))}" opacity="0.85"/>
  <path d="M${f(X(-150))},${f(Y(-20))} Q${f(X(-60))},${f(Y(-36))} ${f(X(30))},${f(Y(-30))}" stroke="#7fc2ff" stroke-width="${f(S(10))}" opacity="0.12" fill="none" filter="url(#b4)"/>
  <rect width="${L.W}" height="${L.H}" fill="url(#atpSun)"/>
  <ellipse cx="${f(X(84))}" cy="${f(Y(-45))}" rx="${f(S(130))}" ry="${f(S(1.4))}" fill="#ffffff" opacity="0.7" filter="url(#b1)"/>
  <circle cx="${f(X(84))}" cy="${f(Y(-45))}" r="${f(S(4))}" fill="#ffffff"/>`;

  const bands = rainbow.map((c, i) => `<path d="M-60,${-58 - i * 4.2} Q-6,${-112 - i * 4.2} 60,${-96 - i * 4.2}" stroke="${c}" stroke-width="4.4" fill="none"/>`).join('');
  const fg = `
  <!-- suit -->
  <path d="M-116,0 C-112,-26 -94,-40 -56,-48 L56,-48 C94,-40 112,-26 116,0 Z" fill="url(#atpSuit)"/>
  <path d="M-116,0 C-112,-26 -94,-40 -56,-48 L56,-48 C94,-40 112,-26 116,0" fill="none" stroke="#ffffff" stroke-opacity="0.9" stroke-width="1"/>
  <path d="M-84,-4 C-80,-20 -72,-30 -62,-36 M84,-4 C80,-20 72,-30 62,-36" stroke="#8d95ad" stroke-width="1.4" fill="none" opacity="0.7"/>
  <rect x="-26" y="-36" width="52" height="28" rx="5" fill="#d8dce8" stroke="#8d95ad" stroke-width="1"/>
  <rect x="-20" y="-31" width="20" height="10" rx="2" fill="#0b1130"/>
  <rect x="-18" y="-29" width="10" height="2" rx="1" fill="#61bb46"/><rect x="-18" y="-25.5" width="14" height="2" rx="1" fill="#009ddc"/>
  <circle cx="8" cy="-26" r="3" fill="#e03a3e"/><circle cx="17" cy="-26" r="3" fill="#fdb827"/>
  <rect x="4" y="-17" width="16" height="4" rx="2" fill="#8d95ad"/>
  <!-- neck ring -->
  <ellipse cx="0" cy="-52" rx="50" ry="10" fill="#8f98b2"/>
  <ellipse cx="0" cy="-54" rx="46" ry="8" fill="#c3c9d9"/>
  <!-- helmet -->
  <circle cx="0" cy="-104" r="57" fill="url(#atpHelmet)"/>
  <circle cx="0" cy="-104" r="57" fill="none" stroke="#ffffff" stroke-width="1.2"/>
  <rect x="-66" y="-128" width="12" height="20" rx="3" fill="#c9cfdd" stroke="#8d95ad" stroke-width="1"/>
  <circle cx="-60" cy="-122" r="3" fill="#bfe9ff" filter="url(#glow)"/>
  <ellipse cx="0" cy="-100" rx="44" ry="35" fill="#7c86a3"/>
  <ellipse cx="0" cy="-100" rx="40" ry="31" fill="url(#atpVisor)"/>
  <g clip-path="url(#atpVisorClip)">
    <g opacity="0.9">${bands}</g>
    <path d="M-60,-46 Q-6,-100 60,-84 L60,-40 L-60,-40 Z" fill="#0a1a3c" opacity="0.75"/>
    <path d="M-30,-120 Q-14,-128 6,-127" stroke="#ffffff" stroke-width="5" stroke-linecap="round" fill="none" opacity="0.55" filter="url(#b1)"/>
    ${sparkle(22, -116, 4, '#ffffff', 0.9)}
  </g>
  <path d="M-38,-142 A57,57 0 0 1 30,-152" stroke="#ffffff" stroke-width="3" fill="none" opacity="0.8" stroke-linecap="round"/>
  <path d="M46,-130 A57,57 0 0 1 52,-80" stroke="#cfeaff" stroke-width="2.4" fill="none" opacity="0.7" stroke-linecap="round" filter="url(#b1)"/>`;
  // The astronaut floats, the sun flares over the rim of the earth, the stars twinkle
  return {
    defs, bg, fg,
    fgMotion: { pivot: [0, -60], m: { dy: wave(-3.2, 3.8), rot: wave(1.6, 5.4, 0.2) } },
    fx: [
      { type: 'dots', layer: 'scene', pts: twinkles(L, 120, 51, Y(-40)), color: '#ffffff', a: 0.2, tw: [0.5, 2.8] },
      { type: 'glow', layer: 'scene', x: 84, y: -45, r: 70, color: '#cfeaff', a: wave(0.18, 2.6, 0, 0.4) },
      { type: 'glow', layer: 'scene', x: 84, y: -45, r: 40, sx: 4.5, color: '#ffffff', a: wave(0.12, 2.6, 0.1, 0.25) },
      { type: 'glow', layer: 'top', x: 22, y: -116, dy: wave(-3.2, 3.8), r: 9, color: '#ffffff', a: keys(4, [[0, 0], [2.4, 0], [2.7, 0.9], [3.2, 0]]) },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// SkyShowtime: premiere night. Searchlights sweeping the sky over an art deco theatre.
function sst(L) {
  const { X, Y, S } = helpers(L);
  const horizon = Y(-36);
  const defs = `
  <linearGradient id="sstSky" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="${f(horizon)}">
    <stop offset="0" stop-color="#0b0322"/><stop offset="0.45" stop-color="#2c0a55"/><stop offset="0.8" stop-color="#7a1a8c"/><stop offset="1" stop-color="#e0489c"/>
  </linearGradient>
  <linearGradient id="sstBeam" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="#fff2fb" stop-opacity="0.55"/><stop offset="0.6" stop-color="#ffc6ec" stop-opacity="0.14"/><stop offset="1" stop-color="#ffc6ec" stop-opacity="0"/>
  </linearGradient>
  <linearGradient id="sstFacade" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#3a0f63"/><stop offset="1" stop-color="#1a0733"/>
  </linearGradient>
  <linearGradient id="sstPanel" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#fff4fb"/><stop offset="1" stop-color="#ffc2e6"/>
  </linearGradient>
  <radialGradient id="sstDoor" cx="0.5" cy="1" r="1">
    <stop offset="0" stop-color="#fff0c2"/><stop offset="1" stop-color="#ff9ad6"/>
  </radialGradient>`;

  const beam = (lx, ly, deg, len, w) => `<g transform="translate(${f(X(lx))},${f(Y(ly))}) rotate(${deg})"><polygon points="0,${f(-S(2))} ${f(S(len))},${f(-S(w))} ${f(S(len))},${f(S(w))} 0,${f(S(2))}" fill="url(#sstBeam)"/></g>`;
  const beams = [[-60, -70, -118, 360, 22], [70, -70, -60, 360, 22], [-20, -80, -98, 380, 16], [30, -80, -76, 380, 16]];
  const r = rng(61);
  let city = `<path d="M0,${f(L.H)} L0,${f(horizon)}`;
  for (let x = 0; x <= L.W; x += S(14)) { const h = S(8 + r() * 22); city += ` L${f(x)},${f(horizon - h)} L${f(x + S(14))},${f(horizon - h)}`; }
  city += ` L${L.W},${f(L.H)} Z" fill="#240a40"/>`;
  const clouds = [[-150, -150, 70], [120, -168, 60], [40, -120, 50], [-60, -190, 55]]
    .map(([x, y, w]) => `<ellipse cx="${f(X(x))}" cy="${f(Y(y))}" rx="${f(S(w))}" ry="${f(S(7))}" fill="#ff9ad6" opacity="0.18" filter="url(#b4)"/>`).join('');
  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#sstSky)"/>
  ${stars(L, 50, 63, horizon - S(40), '#ffe6f6')}
  ${clouds}`;
  // The city stands in front of the searchlights, which sweep the sky on their own layers
  const street = `${city}
  <rect y="${f(horizon)}" width="${L.W}" height="${f(L.H)}" fill="#1a0730"/>
  <ellipse cx="${f(X(0))}" cy="${f(Y(2))}" rx="${f(S(120))}" ry="${f(S(14))}" fill="#ff9ad6" opacity="0.35" filter="url(#b8)"/>`;

  // Bulbs evenly spaced along a rectangle's edge
  const bulbsRect = (x0, y0, x1, y1, step, rr = 1.6) => {
    let out = '';
    for (let x = x0; x <= x1 + 0.01; x += step) out += `<circle cx="${f(x)}" cy="${y0}" r="${rr}"/><circle cx="${f(x)}" cy="${y1}" r="${rr}"/>`;
    for (let y = y0 + step; y < y1 - 0.01; y += step) out += `<circle cx="${x0}" cy="${f(y)}" r="${rr}"/><circle cx="${x1}" cy="${f(y)}" r="${rr}"/>`;
    return out;
  };
  const star5 = (cx, cy, R, rr) => {
    let d = '';
    for (let i = 0; i < 10; i++) { const a = -Math.PI / 2 + i * Math.PI / 5, rad = i % 2 ? rr : R; d += `${i ? 'L' : 'M'}${f(cx + Math.cos(a) * rad)},${f(cy + Math.sin(a) * rad)} `; }
    return d + 'Z';
  };
  const wings = `
    <rect x="-124" y="-58" width="48" height="58" fill="#1d0838"/><rect x="76" y="-52" width="48" height="52" fill="#1d0838"/>
    <g fill="#ffb3e0" opacity="0.7">${[-116, -104, -92].map(x => `<rect x="${x}" y="-46" width="6" height="8"/><rect x="${x}" y="-30" width="6" height="8"/>`).join('')}
    ${[86, 98, 110].map(x => `<rect x="${x}" y="-40" width="6" height="8"/><rect x="${x}" y="-24" width="6" height="8"/>`).join('')}</g>`;
  const fg = `
  ${wings}
  <!-- stepped art deco facade -->
  <path d="M-76,0 L-76,-96 L-56,-96 L-56,-110 L-36,-110 L-36,-122 L36,-122 L36,-110 L56,-110 L56,-96 L76,-96 L76,0 Z" fill="url(#sstFacade)"/>
  <path d="M-76,0 L-76,-96 L-56,-96 L-56,-110 L-36,-110 L-36,-122 L36,-122 L36,-110 L56,-110 L56,-96 L76,-96 L76,0" fill="none" stroke="#ff5fd2" stroke-width="1.4" stroke-opacity="0.85" filter="url(#glow)"/>
  <g stroke="#ff5fd2" stroke-opacity="0.35" stroke-width="1.2">
    <line x1="-62" y1="-90" x2="-62" y2="-78"/><line x1="-48" y1="-104" x2="-48" y2="-78"/><line x1="48" y1="-104" x2="48" y2="-78"/><line x1="62" y1="-90" x2="62" y2="-78"/>
    <line x1="-28" y1="-116" x2="-28" y2="-78"/><line x1="28" y1="-116" x2="28" y2="-78"/>
  </g>
  <!-- vertical sign -->
  <rect x="-13" y="-160" width="26" height="88" rx="3" fill="#2a0a4d" stroke="#ff5fd2" stroke-width="1.4" filter="url(#glow)"/>
  <g fill="#ffe9a8" filter="url(#glow)">${bulbsRect(-9, -156, 9, -76, 6, 1.3)}</g>
  <g fill="#ff5fd2" filter="url(#glow)">${[-145, -130, -115, -100, -86].map(y => `<path d="M0,${y - 5} L5,${y} L0,${y + 5} L-5,${y} Z"/>`).join('')}</g>
  <path d="${star5(0, -168, 9, 3.8)}" fill="#ffe9a8" filter="url(#glowBig)"/>
  <!-- marquee -->
  <polygon points="-94,-74 94,-74 82,-44 -82,-44" fill="#2e0b52"/>
  <polygon points="-94,-74 94,-74 82,-44 -82,-44" fill="none" stroke="#ff5fd2" stroke-width="1.2" filter="url(#glow)"/>
  <rect x="-70" y="-68" width="140" height="18" rx="2" fill="url(#sstPanel)"/>
  <g fill="#3a0f63" opacity="0.85">${[[-62, 14], [-44, 22], [-18, 10], [-4, 26], [26, 16], [46, 16]].map(([x, w]) => `<rect x="${x}" y="-62" width="${w}" height="6" rx="1"/>`).join('')}</g>
  <g fill="#ffe9a8" filter="url(#glow)">${Array.from({ length: 24 }, (_, i) => `<circle cx="${f(-90 + i * 7.8)}" cy="-71" r="1.5"/>`).join('')}${Array.from({ length: 21 }, (_, i) => `<circle cx="${f(-79 + i * 7.9)}" cy="-47" r="1.5"/>`).join('')}</g>
  <!-- doors and light on the pavement -->
  <rect x="-44" y="-40" width="88" height="40" fill="#14052a"/>
  ${[-40, -12, 16].map(x => `<rect x="${x}" y="-36" width="24" height="36" rx="1" fill="url(#sstDoor)" opacity="0.9"/>`).join('')}
  <path d="M-44,0 L44,0 L70,8 L-70,8 Z" fill="#ffb3e0" opacity="0.35" filter="url(#b2)"/>`;
  // Marquee bulbs chase round the sign and along the canopy
  const chase = [];
  const ring = (pts, k) => pts.forEach(([x, y], i) => chase.push([f(x), f(y), 5, f(-(i / pts.length) * k)]));
  ring(Array.from({ length: 24 }, (_, i) => [-90 + i * 7.8, -71]), 4);
  ring(Array.from({ length: 21 }, (_, i) => [79 - i * 7.9, -47]), 3.5);
  const sign = [];
  for (let y = -156; y <= -76; y += 6) sign.push([-9, y]);
  ring(sign, 3);
  return {
    defs, bg, fg,
    parts: [
      ...beams.map(([lx, ly, deg, len, w], i) => ({ name: `beam${i + 1}`, group: 'scene', frame: true, svg: beam(lx, ly, deg, len, w), pivot: [lx, ly], m: { rot: wave(i % 2 ? -11 : 11, 5.6 + i * 0.7, i * 0.17) } })),
      { name: 'street', group: 'scene', frame: true, svg: street },
    ],
    fx: [
      { type: 'dots', layer: 'top', pts: chase, shape: 'glowdot', color: '#ffe9a8', a: 0.05, tw: [0.9, 1.2] },
      { type: 'glow', layer: 'top', x: 0, y: -168, r: 22, color: '#ffe9a8', a: wave(0.3, 1.3, 0, 0.5) },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// Paramount+: a snow peak with the stars streaking past at warp speed.
function pmp(L) {
  const { X, Y, S, halfW, skyTop } = helpers(L);
  const horizon = Y(-24);
  const vp = { x: X(0), y: Y(-152) };
  const defs = `
  <linearGradient id="pmpSky" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="${f(horizon)}">
    <stop offset="0" stop-color="#01030f"/><stop offset="0.55" stop-color="#081c55"/><stop offset="1" stop-color="#1c4ccc"/>
  </linearGradient>
  <radialGradient id="pmpCore" gradientUnits="userSpaceOnUse" cx="${f(vp.x)}" cy="${f(vp.y)}" r="${f(S(90))}">
    <stop offset="0" stop-color="#ffffff" stop-opacity="0.9"/><stop offset="0.25" stop-color="#9fc0ff" stop-opacity="0.45"/><stop offset="1" stop-color="#4d7cff" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="pmpLit" gradientUnits="userSpaceOnUse" x1="-120" y1="-150" x2="20" y2="0">
    <stop offset="0" stop-color="#9fb8ff"/><stop offset="0.5" stop-color="#4d6fd8"/><stop offset="1" stop-color="#22358a"/>
  </linearGradient>
  <linearGradient id="pmpShade" gradientUnits="userSpaceOnUse" x1="0" y1="-150" x2="120" y2="0">
    <stop offset="0" stop-color="#2a3d96"/><stop offset="1" stop-color="#121c55"/>
  </linearGradient>`;

  const r = rng(71);
  let streaks = '';
  const reach = Math.hypot(L.W, L.H);
  for (let i = 0; i < 190; i++) {
    const a = r() * Math.PI * 2;
    const r0 = S(24) + r() * reach * 0.45, len = S(10) + r() * reach * 0.22 * (r0 / reach + 0.3);
    const w = S(0.35 + r() * 0.9);
    const x0 = vp.x + Math.cos(a) * r0, y0 = vp.y + Math.sin(a) * r0;
    const x1 = vp.x + Math.cos(a) * (r0 + len), y1 = vp.y + Math.sin(a) * (r0 + len);
    const nx = -Math.sin(a) * w, ny = Math.cos(a) * w;
    const c = r() < 0.25 ? '#9fc0ff' : '#ffffff';
    streaks += `<polygon points="${f(x0)},${f(y0)} ${f(x1 + nx)},${f(y1 + ny)} ${f(x1 - nx)},${f(y1 - ny)}" fill="${c}" opacity="${f(0.35 + r() * 0.55)}"/>`;
  }
  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#pmpSky)"/>
  ${stars(L, 60, 73, horizon, '#dfe8ff')}
  ${streaks}
  <rect width="${L.W}" height="${L.H}" fill="url(#pmpCore)"/>
  <rect y="${f(horizon)}" width="${L.W}" height="${f(L.H)}" fill="#050b26"/>`;

  const fg = `
  <path d="M-170,0 L-128,-46 L-112,-58 L-100,-72 L-86,-60 L-70,-48 L-40,-20 L-30,0 Z" fill="#141f5a"/>
  <path d="M-100,-72 L-112,-58 L-128,-46 L-170,0" stroke="#7f9cff" stroke-opacity="0.5" stroke-width="1" fill="none"/>
  <path d="M40,0 L64,-38 L80,-62 L96,-80 L112,-64 L130,-46 L170,-10 L170,0 Z" fill="#121b52"/>
  <path d="M96,-80 L80,-62 L64,-38" stroke="#7f9cff" stroke-opacity="0.45" stroke-width="1" fill="none"/>
  <path d="M-136,0 L-104,-40 L-84,-62 L-62,-84 L-42,-108 L-24,-128 L-10,-142 L0,-152 L14,-138 L32,-118 L52,-96 L72,-74 L94,-50 L118,-26 L140,0 Z" fill="url(#pmpShade)"/>
  <path d="M-136,0 L-104,-40 L-84,-62 L-62,-84 L-42,-108 L-24,-128 L-10,-142 L0,-152 L-4,-128 L6,-104 L-6,-80 L4,-56 L-8,-30 L2,0 Z" fill="url(#pmpLit)"/>
  <path d="M0,-152 L-10,-142 L-24,-128 L-34,-117 L-24,-112 L-18,-118 L-10,-106 L-2,-116 L6,-104 L14,-112 L22,-104 L30,-116 L32,-118 L14,-138 Z" fill="#f2f6ff"/>
  <path d="M0,-152 L14,-138 L32,-118 L30,-116 L22,-104 L14,-112 L6,-104 L-4,-128 Z" fill="#b9c8f2"/>
  <path d="M-136,0 L-104,-40 L-84,-62 L-62,-84 L-42,-108 L-24,-128 L-10,-142 L0,-152" stroke="#cfe0ff" stroke-width="1.3" fill="none" stroke-opacity="0.9" filter="url(#glow)"/>
  <path d="M-62,-84 L-50,-70 L-56,-52 M-24,-128 L-18,-100 L-26,-78 M32,-118 L26,-92 L34,-70 M72,-74 L66,-52" stroke="#9fb8ff" stroke-opacity="0.16" stroke-width="1" fill="none"/>
  <ellipse cx="0" cy="-22" rx="170" ry="12" fill="#cfe0ff" opacity="0.18" filter="url(#b8)"/>
  <path d="M-170,0 L-120,-14 L-70,-8 L-20,-18 L30,-10 L80,-20 L130,-8 L170,-14 L170,0 Z" fill="#0a1238"/>`;
  // Stars stream out of the peak at warp speed, its tip glinting
  return {
    defs, bg, fg,
    fx: [
      { type: 'particles', layer: 'scene', shape: 'streak', n: 40, seed: 17, life: 1.5, from: [0, -152], r0: [16, 70], speed: [40, 90], acc: 160, len: 26, size: [0.9, 1.8], color: ['#ffffff', '#ffffff', '#9fc0ff'], fade: [0.25, 0.2] },
      { type: 'glow', layer: 'scene', x: 0, y: -152, r: 80, color: '#9fc0ff', a: wave(0.12, 1.6, 0, 0.35) },
      { type: 'glow', layer: 'top', x: 0, y: -151, r: 14, color: '#ffffff', a: wave(0.35, 1.9, 0, 0.45) },
      { type: 'dots', layer: 'top', pts: [[0, -151, 16, 0]], shape: 'spark', color: '#ffffff', a: 0.25, tw: [0.6, 1.9] },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// Crunchyroll: anime night. A big bowl of ramen against a striped sun and manga speed lines.
function cru(L) {
  const { X, Y, S } = helpers(L);
  const c = { x: X(0), y: Y(-96) };
  const R = Math.hypot(L.W, L.H);
  const defs = `
  <radialGradient id="cruBg" gradientUnits="userSpaceOnUse" cx="${f(c.x)}" cy="${f(c.y)}" r="${f(R * 0.72)}">
    <stop offset="0" stop-color="#ff9a45"/><stop offset="0.25" stop-color="#f2622a"/><stop offset="0.6" stop-color="#8a2318"/><stop offset="1" stop-color="#2a0712"/>
  </radialGradient>
  <linearGradient id="cruSun" gradientUnits="userSpaceOnUse" x1="0" y1="${f(c.y - S(66))}" x2="0" y2="${f(c.y + S(66))}">
    <stop offset="0" stop-color="#fff4c9"/><stop offset="0.5" stop-color="#ffc06a"/><stop offset="1" stop-color="#ff6f8f"/>
  </linearGradient>
  <linearGradient id="cruBowl" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#2a1030"/><stop offset="1" stop-color="#12051a"/>
  </linearGradient>
  <linearGradient id="cruBroth" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#f6b65a"/><stop offset="1" stop-color="#d27a24"/>
  </linearGradient>
  <linearGradient id="cruSteam" gradientUnits="userSpaceOnUse" x1="0" y1="-92" x2="0" y2="-170">
    <stop offset="0" stop-color="#ffffff" stop-opacity="0.15"/><stop offset="0.3" stop-color="#ffffff" stop-opacity="0.8"/><stop offset="1" stop-color="#ffffff" stop-opacity="0"/>
  </linearGradient>
  <linearGradient id="cruSteamShade" gradientUnits="userSpaceOnUse" x1="0" y1="-92" x2="0" y2="-170">
    <stop offset="0" stop-color="#7a1d12" stop-opacity="0"/><stop offset="0.3" stop-color="#7a1d12" stop-opacity="0.3"/><stop offset="1" stop-color="#7a1d12" stop-opacity="0"/>
  </linearGradient>
  <linearGradient id="cruStick" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="#ffe7c2"/><stop offset="1" stop-color="#d9a066"/>
  </linearGradient>
  <pattern id="cruDots" patternUnits="userSpaceOnUse" width="${f(S(7))}" height="${f(S(7))}">
    <circle cx="${f(S(3.5))}" cy="${f(S(3.5))}" r="${f(S(1.3))}" fill="#ffd9a8"/>
  </pattern>
  <radialGradient id="cruDotFade" gradientUnits="userSpaceOnUse" cx="${f(c.x)}" cy="${f(c.y)}" r="${f(R * 0.6)}">
    <stop offset="0.45" stop-color="#000"/><stop offset="1" stop-color="#fff"/>
  </radialGradient>
  <mask id="cruDotMask" maskUnits="userSpaceOnUse" x="0" y="0" width="${L.W}" height="${L.H}"><rect width="${L.W}" height="${L.H}" fill="url(#cruDotFade)"/></mask>
  <clipPath id="cruBowlClip"><path d="M-104,-64 C-100,-22 -62,-4 -30,-2 L30,-2 C62,-4 100,-22 104,-64 Z"/></clipPath>
  <clipPath id="cruBrothClip"><ellipse cx="0" cy="-64" rx="96" ry="19"/></clipPath>`;

  const r = rng(81);
  let lines = '';
  for (let i = 0; i < 72; i++) {
    const a = (i / 72) * Math.PI * 2 + r() * 0.05;
    const r0 = S(84) + r() * S(46), w = S(1 + r() * 2.6);
    const x0 = c.x + Math.cos(a) * r0, y0 = c.y + Math.sin(a) * r0;
    const x1 = c.x + Math.cos(a) * R, y1 = c.y + Math.sin(a) * R;
    const nx = -Math.sin(a) * w, ny = Math.cos(a) * w;
    lines += `<polygon points="${f(x0)},${f(y0)} ${f(x1 + nx)},${f(y1 + ny)} ${f(x1 - nx)},${f(y1 - ny)}" fill="#fff0d6" opacity="${f(0.1 + r() * 0.2)}"/>`;
  }
  // The striped sun of the app's skyline, cut by bands toward its foot
  const sunR = S(66);
  let cuts = '';
  [[0.18, 2.4], [0.38, 3.4], [0.56, 4.4], [0.72, 5.4]].forEach(([t, h]) => {
    cuts += `<rect x="${f(c.x - sunR)}" y="${f(c.y + sunR * t)}" width="${f(sunR * 2)}" height="${f(S(h))}" fill="#000"/>`;
  });
  const sunMask = `<mask id="cruSunMask" maskUnits="userSpaceOnUse" x="0" y="0" width="${L.W}" height="${L.H}"><rect width="${L.W}" height="${L.H}" fill="#fff"/>${cuts}</mask>`;
  const petal = (x, y, sz, rot, o = 1) => `<g transform="translate(${f(x)},${f(y)}) rotate(${rot}) scale(${f(sz)})" opacity="${o}"><path d="M0,-6 C4,-6 6,-2 5,2 C4,5 1,6 0,4 C-1,6 -4,5 -5,2 C-6,-2 -4,-6 0,-6 Z" fill="#ffc4dc"/><path d="M0,-6 L0,-2" stroke="#ff8fbd" stroke-width="0.9"/></g>`;
  const rp = rng(83);
  let petals = '';
  for (let i = 0; i < 12; i++) petals += petal(rp() * L.W, rp() * L.H * 0.8, S(0.8 + rp() * 0.9), Math.round(rp() * 360), f(0.55 + rp() * 0.4));
  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#cruBg)"/>
  ${lines}
  <rect width="${L.W}" height="${L.H}" fill="url(#cruDots)" opacity="0.26" mask="url(#cruDotMask)"/>
  <circle cx="${f(c.x)}" cy="${f(c.y)}" r="${f(sunR * 1.25)}" fill="#ffb15c" opacity="0.4" filter="url(#b14)"/>
  <circle cx="${f(c.x)}" cy="${f(c.y)}" r="${f(sunR)}" fill="url(#cruSun)" mask="url(#cruSunMask)"/>
  ${petals}`;

  // Seigaiha waves round the bowl
  let waves = '';
  for (let x = -108; x <= 108; x += 13) waves += `<path d="M${x - 6.5},-42 a6.5,6.5 0 0 1 13,0" stroke="#ff8a3d" stroke-width="1.8" fill="none"/><path d="M${x - 3.2},-42 a3.2,3.2 0 0 1 6.4,0" stroke="#ff8a3d" stroke-width="1.3" fill="none"/>`;
  const noodles = [[-70, -62], [-54, -56], [-38, -72], [44, -76], [66, -66], [-12, -52]].map(([x, y], i) => `<path d="M${x},${y} q4,-3 8,0 t8,0 t8,0" stroke="#fff0bf" stroke-width="2.2" fill="none" transform="rotate(${i % 2 ? 8 : -6} ${x} ${y})"/>`).join('');
  const onions = [[-4, -52], [26, -70], [-40, -60], [62, -56], [-64, -70], [14, -52], [80, -64]]
    .map(([x, y]) => `<circle cx="${x}" cy="${y}" r="2.8" fill="#9fe36b" opacity="0.95"/><circle cx="${x}" cy="${y}" r="1.2" fill="#e9ffd4"/>`).join('');
  const steam = [[-36, 3.4, -150], [-2, 3.8, -168], [32, 3.2, -156]].map(([x, w, top]) => {
    const d = `M${x},-92 C${x - 12},-106 ${x + 12},-118 ${x},${f((-92 + top) / 2 - 4)} C${x - 10},${top + 18} ${x + 8},${top + 8} ${x + 2},${top}`;
    return `<path d="${d}" stroke="url(#cruSteamShade)" stroke-width="${w + 3}" fill="none" stroke-linecap="round" filter="url(#b1)"/><path d="${d}" stroke="url(#cruSteam)" stroke-width="${w}" fill="none" stroke-linecap="round"/>`;
  }).join('');
  const fg = `
  ${sunMask}
  <ellipse cx="0" cy="0" rx="108" ry="7" fill="#1a0410" opacity="0.6" filter="url(#b4)"/>
  ${steam}
  <!-- nori at the back -->
  <path d="M-74,-66 L-74,-112 L-48,-116 L-48,-68 Z" fill="#163022"/>
  <path d="M-52,-68 L-52,-108 L-28,-111 L-28,-66 Z" fill="#244a33"/>
  <path d="M-74,-112 L-48,-116 M-52,-108 L-28,-111" stroke="#6f9f7b" stroke-width="1.2" opacity="0.8"/>
  <!-- bowl -->
  <path d="M-32,-5 L32,-5 L28,2 L-28,2 Z" fill="#0c0312"/>
  <path d="M-104,-64 C-100,-22 -62,-4 -30,-2 L30,-2 C62,-4 100,-22 104,-64 Z" fill="url(#cruBowl)"/>
  <g clip-path="url(#cruBowlClip)">${waves}<path d="M-104,-28 C-70,-6 70,-6 104,-28" stroke="#ff8a3d" stroke-width="1.6" fill="none" opacity="0.8"/></g>
  <path d="M-104,-64 C-100,-22 -62,-4 -30,-2 L30,-2 C62,-4 100,-22 104,-64" fill="none" stroke="#ffb27a" stroke-width="1.4" opacity="0.85"/>
  <ellipse cx="0" cy="-64" rx="104" ry="24" fill="#ff5a3d"/>
  <ellipse cx="0" cy="-64" rx="104" ry="24" fill="none" stroke="#ffd0b0" stroke-width="1.2" opacity="0.8"/>
  <ellipse cx="0" cy="-64" rx="96" ry="19" fill="url(#cruBroth)"/>
  <g clip-path="url(#cruBrothClip)">
    ${noodles}
    <ellipse cx="-30" cy="-60" rx="20" ry="10" fill="#b8734a"/><ellipse cx="-30" cy="-60" rx="14" ry="6.4" fill="none" stroke="#f3c79c" stroke-width="1.8"/>
    <ellipse cx="6" cy="-56" rx="15" ry="8" fill="#fffaf4"/>
    <path d="M6,-56 m-1,0 a2.2,1.3 0 1 1 3.4,0.7 a4.6,2.7 0 1 1 -7,-1.4 a7,4.2 0 1 1 10.6,1.8" stroke="#ff4f98" stroke-width="1.6" fill="none"/>
    <ellipse cx="40" cy="-60" rx="16" ry="9.5" fill="#ffffff"/><ellipse cx="41" cy="-60" rx="8.5" ry="5.4" fill="#ffad2e"/>
    <ellipse cx="64" cy="-70" rx="13" ry="8" fill="#fff7ec"/><ellipse cx="65" cy="-70" rx="6.5" ry="4.2" fill="#ff9e1f"/>
    ${onions}
  </g>
  ${petal(-108, -124, 1.5, 20)}${petal(104, -70, 1.3, 140)}${petal(-116, -36, 1.1, 75)}`;
  // The chopsticks lift the noodles and dip them again, steam rises, petals fall
  const sticks = `
  <path d="M8,-108 Q-2,-92 0,-70 M15,-106 Q13,-90 15,-66 M22,-104 Q32,-88 30,-70" stroke="#fff0bf" stroke-width="2.6" fill="none" stroke-linecap="round"/>
  <path d="M6,-110 L110,-168" stroke="url(#cruStick)" stroke-width="4.8" stroke-linecap="round"/>
  <path d="M20,-104 L118,-160" stroke="url(#cruStick)" stroke-width="4.8" stroke-linecap="round"/>
  <path d="M86,-155 L110,-168 M95,-146 L118,-160" stroke="#c2312a" stroke-width="5" stroke-linecap="round"/>`;
  const { halfW, skyTop } = helpers(L);
  return {
    defs, bg, fg,
    parts: [{ name: 'sticks', group: 'subject', svg: sticks, pivot: [114, -164], m: { dy: keys(3.2, [[0, 0], [0.9, -9], [2.2, -9], [3.0, 0]]), rot: keys(3.2, [[0, 0], [0.9, -2.5], [2.2, -2.5], [3.0, 0]]) } }],
    fx: [
      { type: 'rays', layer: 'scene', x: 0, y: -96, n: 30, r0: 70, r1: 420, w: 0.9, spin: 3, color: '#fff0d6', a: 0.12 },
      { type: 'glow', layer: 'scene', x: 0, y: -96, r: 100, color: '#ffb15c', a: wave(0.1, 2.4, 0, 0.3) },
      { type: 'particles', layer: 'top', shape: 'puff', n: 7, seed: 31, life: 3.2, x: -44, w: 80, y: -96, h: 6, vy: -22, dv: [3, 4], sway: [5, 2.2], size: [16, 30], color: '#ffffff', alpha: [0.18, 0.3], fade: [0.25, 0.55] },
      { type: 'particles', layer: 'top', shape: 'petal', n: 9, seed: 37, life: 6, x: -halfW - 10, w: halfW * 2 + 20, y: skyTop - 10, h: 10, vx: 6, vy: (-skyTop + 60) / 6, sway: [14, 2.6], size: [7, 10], spin: 80, color: ['#ffc4dc', '#ff9ac6'], fade: [0.05, 0.15] },
    ],
  };
}

const genres = require('./genres');

module.exports = { scenes: { nfx, hbm, dnp, amp, atp, sst, pmp, cru, ...genres }, commonDefs };
