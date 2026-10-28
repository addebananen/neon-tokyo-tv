// Original neon scenes for the genre cards, one per Cinemeta genre, about what makes the
// genre what it is. Same rules as the service scenes (see scenes.js): a background, a subject
// in local units, and the parts and effects that move while the card has focus.
const { f, rng, sparkle, stars, helpers, floorGrid, twinkles, wave, keys } = require('./lib');

// A vertical sky gradient from the top of the frame down to y
const skyGrad = (id, y, stops) => `<linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="${f(y)}">${stops.map(([o, c]) => `<stop offset="${o}" stop-color="${c}"/>`).join('')}</linearGradient>`;

// A rolling ridge of hills across the frame
function ridge(L, y0, amp, seed, fill, step, extra = '') {
  const r = rng(seed);
  let d = `M-4,${f(L.H + 4)} L-4,${f(y0)}`;
  for (let x = -4; x <= L.W + step; x += step) d += ` L${f(x)},${f(y0 - amp * (0.3 + r()))}`;
  return `<path d="${d} L${L.W + 4},${f(L.H + 4)} Z" fill="${fill}" ${extra}/>`;
}

// ---------------------------------------------------------------------------------------
// Horror: a haunted house on a hill under a blood moon. Lightning, bats, someone at the window.
function g_horror(L) {
  const { X, Y, S, halfW, skyTop } = helpers(L);
  const horizon = Y(-22);
  const moon = { x: X(-Math.min(halfW * 0.55, 92)), y: Y(Math.max(skyTop * 0.66, -140)), r: S(40) };
  const defs = `
  ${skyGrad('hrSky', horizon, [[0, '#050107'], [0.5, '#1d0410'], [0.85, '#4a0a1c'], [1, '#7a1228']])}
  <radialGradient id="hrMoon" cx="0.42" cy="0.38" r="0.7"><stop offset="0" stop-color="#ffd2c2"/><stop offset="0.55" stop-color="#ff5a4a"/><stop offset="1" stop-color="#b3122a"/></radialGradient>
  <radialGradient id="hrHalo" gradientUnits="userSpaceOnUse" cx="${f(moon.x)}" cy="${f(moon.y)}" r="${f(S(150))}">
    <stop offset="0.25" stop-color="#ff2e4d" stop-opacity="0.45"/><stop offset="0.6" stop-color="#ff2e4d" stop-opacity="0.1"/><stop offset="1" stop-color="#ff2e4d" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="hrHill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1a0610"/><stop offset="1" stop-color="#070205"/></linearGradient>
  <linearGradient id="hrHouse" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1c0814"/><stop offset="1" stop-color="#0d0309"/></linearGradient>
  <radialGradient id="hrWin" cx="0.5" cy="0.6" r="0.7"><stop offset="0" stop-color="#fff0b8"/><stop offset="0.6" stop-color="#ffb347"/><stop offset="1" stop-color="#e0662a"/></radialGradient>`;

  const craters = [[-14, -10, 8], [12, 6, 10], [-4, 18, 5], [18, -16, 4]]
    .map(([dx, dy, rr]) => `<circle cx="${f(moon.x + S(dx))}" cy="${f(moon.y + S(dy))}" r="${f(S(rr))}" fill="#a3102a" opacity="0.35"/>`).join('');
  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#hrSky)"/>
  ${stars(L, 40, 91, horizon - S(30), '#ffd6de')}
  <rect width="${L.W}" height="${L.H}" fill="url(#hrHalo)"/>
  <circle cx="${f(moon.x)}" cy="${f(moon.y)}" r="${f(moon.r)}" fill="url(#hrMoon)"/>
  ${craters}
  <ellipse cx="${f(moon.x + S(30))}" cy="${f(moon.y + S(6))}" rx="${f(S(70))}" ry="${f(S(5))}" fill="#2a0612" opacity="0.8"/>
  <ellipse cx="${f(moon.x - S(20))}" cy="${f(moon.y + S(20))}" rx="${f(S(50))}" ry="${f(S(4))}" fill="#2a0612" opacity="0.7"/>
  ${ridge(L, horizon + S(2), S(16), 4, '#2a0815', S(26))}
  ${ridge(L, horizon + S(12), S(10), 7, '#16040c', S(18))}
  <rect y="${f(horizon + S(8))}" width="${L.W}" height="${f(S(14))}" fill="#ff5a6e" opacity="0.08" filter="url(#b8)"/>
  <rect y="${f(horizon + S(18))}" width="${L.W}" height="${L.H}" fill="#0a0207"/>`;

  // A gnarled dead tree, drawn as tapering strokes
  const branch = (d, w) => `<path d="${d}" stroke="#0b0207" stroke-width="${w}" fill="none" stroke-linecap="round"/>`;
  const tree = `
    ${branch('M104,-34 C102,-60 108,-84 100,-112', 7)}
    ${branch('M102,-70 C116,-82 126,-84 138,-100', 3.6)}
    ${branch('M126,-86 C132,-84 138,-80 146,-82', 1.8)}
    ${branch('M101,-96 C88,-106 84,-118 74,-124', 3)}
    ${branch('M84,-114 C80,-122 82,-130 78,-138', 1.6)}
    ${branch('M100,-112 C104,-126 100,-134 106,-146', 2.2)}
    ${branch('M106,-56 C96,-62 90,-60 84,-68', 2.4)}`;
  const win = (x, y, w, h, lit = true) => `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="0.8" fill="${lit ? 'url(#hrWin)' : '#24101a'}"/><path d="M${x + w / 2},${y} v${h} M${x},${y + h / 2} h${w}" stroke="#1c0814" stroke-width="0.9"/>`;
  const cross = (x, y, s) => `<path d="M${x},${y} v${-14 * s} M${x - 4 * s},${y - 10 * s} h${8 * s}" stroke="#14050c" stroke-width="${2.2 * s}" stroke-linecap="round"/>`;
  const grave = (x, y, s) => `<path d="M${x - 5 * s},${y} v${-9 * s} a${5 * s},${5 * s} 0 0 1 ${10 * s},0 v${9 * s} Z" fill="#14050c"/>`;

  const fg = `
  <ellipse cx="0" cy="-4" rx="150" ry="10" fill="#000" opacity="0.5" filter="url(#b4)"/>
  <!-- the hill -->
  <path d="M-170,6 C-120,-14 -90,-30 -40,-34 C10,-38 70,-36 110,-24 C140,-14 160,-2 180,6 Z" fill="url(#hrHill)"/>
  <path d="M-150,-4 C-110,-20 -80,-31 -40,-34 C10,-38 70,-36 110,-24 C136,-15 150,-8 166,0" stroke="#ff4d6a" stroke-opacity="0.35" stroke-width="1" fill="none"/>
  ${tree}
  <!-- tower -->
  <rect x="-72" y="-112" width="24" height="76" fill="url(#hrHouse)"/>
  <path d="M-76,-112 L-60,-162 L-44,-112 Z" fill="#12050c"/>
  <path d="M-60,-162 L-60,-172" stroke="#12050c" stroke-width="1.4"/>
  <path d="M-76,-112 L-60,-162 L-44,-112" stroke="#ff4d6a" stroke-opacity="0.45" stroke-width="0.9" fill="none"/>
  ${win(-64, -100, 8, 12)}${win(-64, -76, 8, 12, false)}
  <!-- main house -->
  <rect x="-48" y="-92" width="96" height="58" fill="url(#hrHouse)"/>
  <path d="M-54,-92 L-18,-128 L18,-92 Z" fill="#12050c"/>
  <path d="M10,-92 L30,-112 L54,-92 Z" fill="#12050c"/>
  <path d="M-54,-92 L-18,-128 L18,-92 M10,-92 L30,-112 L54,-92" stroke="#ff4d6a" stroke-opacity="0.45" stroke-width="0.9" fill="none"/>
  <rect x="22" y="-124" width="8" height="18" fill="#12050c"/>
  <circle cx="-18" cy="-104" r="7.5" fill="url(#hrWin)"/>
  <path d="M-18,-111.5 v15 M-25.5,-104 h15" stroke="#1c0814" stroke-width="1"/>
  ${win(-40, -82, 10, 14)}${win(-24, -82, 10, 14, false)}${win(-6, -82, 10, 14)}${win(26, -84, 10, 14)}
  ${win(-40, -58, 10, 14, false)}${win(26, -60, 10, 14)}
  <path d="M-6,-34 v-18 a6,6 0 0 1 12,0 v18 Z" fill="#05010a"/>
  <path d="M-48,-34 h96" stroke="#ff4d6a" stroke-opacity="0.3" stroke-width="1"/>
  <!-- porch steps and fence -->
  <path d="M-10,-34 h20 l3,4 h-26 Z" fill="#180610"/>
  <g stroke="#12050c" stroke-width="1.6">${[-120, -110, -100, -90, -80, 70, 80, 90].map(x => `<line x1="${x}" y1="${-4 - Math.abs(x) * 0.12}" x2="${x}" y2="${-16 - Math.abs(x) * 0.12}"/>`).join('')}</g>
  <path d="M-124,${f(-12 - 124 * 0.12)} L-76,${f(-12 - 76 * 0.12)} M66,${f(-12 - 66 * 0.12)} L94,${f(-12 - 94 * 0.12)}" stroke="#12050c" stroke-width="1.4"/>
  ${cross(-140, 0, 1)}${grave(-128, 2, 0.9)}${cross(136, 2, 0.9)}${grave(148, 4, 1)}`;

  // Lightning far off to the right of the house
  const bx = Math.min(halfW * 0.8, 150), top = Math.max(skyTop, -230);
  const boltPath = `M${bx - 6},${top} L${bx + 4},${f(top * 0.72)} L${bx - 8},${f(top * 0.6)} L${bx + 2},${f(top * 0.36)} L${bx - 10},${f(top * 0.22)} L${bx - 2},-30`;
  const bolt = `
    <path d="${boltPath}" stroke="#d9c6ff" stroke-width="6" fill="none" opacity="0.5" filter="url(#b4)"/>
    <path d="${boltPath}" stroke="#ffffff" stroke-width="1.8" fill="none" stroke-linejoin="bevel"/>
    <path d="M${bx + 2},${f(top * 0.36)} L${bx + 14},${f(top * 0.24)} L${bx + 10},${f(top * 0.12)}" stroke="#ffffff" stroke-width="1" fill="none" opacity="0.8"/>`;
  const strike = [[0, 0], [2.6, 0], [2.64, 1], [2.72, 0.15], [2.8, 0.9], [3.1, 0]];
  // A figure stands in the round attic window now and then
  const ghost = `<path d="M-18,-98 C-18,-104 -22,-106 -22,-110 a4,4 0 0 1 8,0 C-14,-106 -18,-104 -18,-98 Z" fill="#1a0610"/><circle cx="-18" cy="-108.5" r="3" fill="#1a0610"/>`;

  const windows = [[-60, -94], [-35, -75], [-1, -75], [31, -77], [31, -53]].map(([x, y], i) => [x, y, 18, f(i * 0.37)]);
  return {
    defs, bg, fg,
    parts: [
      { name: 'bolt', group: 'scene', hidden: true, svg: bolt, m: { a: keys(6, strike, true) } },
      { name: 'ghost', group: 'subject', hidden: true, svg: ghost, m: { a: keys(9, [[0, 0], [4, 0], [4.6, 0.95], [6.2, 0.95], [6.8, 0]]) } },
    ],
    fx: [
      { type: 'flash', layer: 'scene', color: '#e8dcff', a: keys(6, strike.map(([t, v]) => [t, v * 0.32]), true) },
      { type: 'glow', layer: 'scene', x: f((moon.x - L.cx) / L.s), y: f((moon.y - L.gy) / L.s), r: 95, color: '#ff2e4d', a: wave(0.12, 4, 0, 0.3) },
      { type: 'dots', layer: 'scene', pts: twinkles(L, 40, 91, horizon - S(30)), color: '#ffd6de', a: 0.25, tw: [0.5, 2.2] },
      { type: 'particles', layer: 'scene', shape: 'bat', n: 4, seed: 3, life: 7, x: -halfW - 30, w: 0, y: f((moon.y - L.gy) / L.s) - 30, h: 50, vx: (halfW * 2 + 60) / 7, vy: -4, dv: [6, 4], sway: [5, 1.3], size: [8, 13], flapHz: 6, color: '#120308', fade: [0.02, 0.02] },
      { type: 'dots', layer: 'top', pts: windows, color: '#ffb347', a: 0.32, tw: [0.22, 0.7] },
      { type: 'particles', layer: 'top', shape: 'puff', n: 6, seed: 5, life: 11, x: -halfW - 40, w: halfW * 2, y: -16, h: 22, vx: 7, size: [60, 100], color: '#c58aa6', alpha: [0.1, 0.18], fade: [0.35, 0.35] },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// Thriller: a time bomb on a table under a swinging lamp, its clock counting down.
function g_thriller(L) {
  const { X, Y, S, halfW, skyTop } = helpers(L);
  const top = Math.max(skyTop, -260);
  const lampY = Math.max(skyTop * 0.72, -150);
  const defs = `
  ${skyGrad('thWall', Y(0), [[0, '#07030e'], [0.6, '#170a2a'], [1, '#2a1246']])}
  <linearGradient id="thTable" gradientUnits="userSpaceOnUse" x1="0" y1="${f(Y(0))}" x2="0" y2="${L.H}"><stop offset="0" stop-color="#2c1648"/><stop offset="1" stop-color="#0c0616"/></linearGradient>
  <radialGradient id="thPool" gradientUnits="userSpaceOnUse" cx="${f(X(0))}" cy="${f(Y(-10))}" r="${f(S(180))}">
    <stop offset="0" stop-color="#d9a8ff" stop-opacity="0.45"/><stop offset="0.45" stop-color="#b24bff" stop-opacity="0.16"/><stop offset="1" stop-color="#b24bff" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="thStick" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ff6a6a"/><stop offset="0.35" stop-color="#d42a3a"/><stop offset="1" stop-color="#5a0a18"/></linearGradient>
  <linearGradient id="thBox" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2a2336"/><stop offset="1" stop-color="#0f0b16"/></linearGradient>
  <linearGradient id="thCone" gradientUnits="userSpaceOnUse" x1="0" y1="${f(lampY)}" x2="0" y2="0">
    <stop offset="0" stop-color="#f3e2ff" stop-opacity="0.42"/><stop offset="0.7" stop-color="#c78cff" stop-opacity="0.1"/><stop offset="1" stop-color="#c78cff" stop-opacity="0"/>
  </linearGradient>
  <radialGradient id="thShade" cx="0.5" cy="0.2" r="0.9"><stop offset="0" stop-color="#4a3a66"/><stop offset="1" stop-color="#140c22"/></radialGradient>`;

  // Concrete panels with a purple neon strip along the wall
  const panels = [];
  for (let x = -halfW - 40; x < halfW + 40; x += 64) panels.push(`<line x1="${f(X(x))}" y1="0" x2="${f(X(x))}" y2="${f(Y(0))}"/>`);
  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#thWall)"/>
  <g stroke="#3a2258" stroke-width="${f(S(1))}" opacity="0.6">${panels.join('')}</g>
  <rect x="0" y="${f(Y(-118))}" width="${L.W}" height="${f(S(2))}" fill="#b24bff" opacity="0.55" filter="url(#b2)"/>
  <rect x="0" y="${f(Y(-118))}" width="${L.W}" height="${f(S(0.8))}" fill="#e9c8ff" opacity="0.8"/>
  <rect y="${f(Y(0))}" width="${L.W}" height="${f(L.H - Y(0))}" fill="url(#thTable)"/>
  <line x1="0" y1="${f(Y(0))}" x2="${L.W}" y2="${f(Y(0))}" stroke="#d9a8ff" stroke-width="${f(S(1.2))}" opacity="0.6"/>
  <rect width="${L.W}" height="${L.H}" fill="url(#thPool)"/>`;

  const stick = (y, x0, x1) => `
    <rect x="${x0}" y="${y - 10}" width="${x1 - x0}" height="20" rx="4" fill="url(#thStick)"/>
    <ellipse cx="${x1 - 1}" cy="${y}" rx="4" ry="10" fill="#ff8f8f"/><ellipse cx="${x1 - 1}" cy="${y}" rx="2.4" ry="6.5" fill="#c23040"/>
    <path d="M${x0 + 4},${y - 6.5} H${x1 - 6}" stroke="#ffd0d0" stroke-width="1.6" opacity="0.5" stroke-linecap="round"/>`;
  const tape = x => `<rect x="${x}" y="-58" width="10" height="56" rx="1.5" fill="#1a1622"/><rect x="${x}" y="-58" width="10" height="56" rx="1.5" fill="none" stroke="#6a5a86" stroke-width="0.7" opacity="0.7"/>`;
  const wire = (d, c) => `<path d="${d}" stroke="#000" stroke-width="3.4" fill="none" opacity="0.4" stroke-linecap="round"/><path d="${d}" stroke="${c}" stroke-width="2.2" fill="none" stroke-linecap="round"/>`;
  const fg = `
  <ellipse cx="0" cy="0" rx="96" ry="6" fill="#000" opacity="0.6" filter="url(#b4)"/>
  ${stick(-12, -78, 74)}${stick(-30, -80, 72)}${stick(-48, -76, 76)}
  ${tape(-58)}${tape(46)}
  ${wire('M-34,-66 C-50,-84 -76,-76 -82,-58', '#ff3b4d')}
  ${wire('M-28,-66 C-32,-92 -64,-96 -72,-72', '#3ec8ff')}
  ${wire('M30,-66 C46,-88 72,-82 80,-62', '#ffd36b')}
  ${wire('M24,-66 C34,-96 66,-98 70,-74', '#86ff6a')}
  <!-- the timer -->
  <rect x="-38" y="-70" width="76" height="40" rx="4" fill="url(#thBox)"/>
  <rect x="-38" y="-70" width="76" height="40" rx="4" fill="none" stroke="#b78cff" stroke-width="1" opacity="0.8"/>
  <rect x="-32" y="-63" width="58" height="22" rx="2" fill="#1a0306"/>
  <rect x="-32" y="-63" width="58" height="22" rx="2" fill="none" stroke="#ff3b4d" stroke-opacity="0.35" stroke-width="0.8"/>
  <circle cx="31" cy="-58" r="2.6" fill="#3a0a10"/>
  <circle cx="31" cy="-47" r="2" fill="#2a2336" stroke="#6a5a86" stroke-width="0.6"/>
  <path d="M-32,-36 h58" stroke="#6a5a86" stroke-width="0.8" opacity="0.6"/>
  <circle cx="-34" cy="-66" r="1.6" fill="#6a5a86"/><circle cx="34" cy="-66" r="1.6" fill="#6a5a86"/><circle cx="-34" cy="-34" r="1.6" fill="#6a5a86"/><circle cx="34" cy="-34" r="1.6" fill="#6a5a86"/>`;

  // The lamp hangs from above the frame and swings, its cone of light with it
  const lamp = `
    <path d="M0,${f(top - 10)} L0,${f(lampY - 14)}" stroke="#1a1026" stroke-width="1.6"/>
    <path d="M-60,-6 L-17,${f(lampY + 6)} L17,${f(lampY + 6)} L60,-6 Z" fill="url(#thCone)"/>
    <path d="M-20,${f(lampY + 6)} Q0,${f(lampY - 22)} 20,${f(lampY + 6)} Z" fill="url(#thShade)"/>
    <path d="M-20,${f(lampY + 6)} Q0,${f(lampY - 22)} 20,${f(lampY + 6)}" fill="none" stroke="#b78cff" stroke-width="0.9" opacity="0.8"/>
    <ellipse cx="0" cy="${f(lampY + 6)}" rx="20" ry="3" fill="#f6e8ff"/>
    <ellipse cx="0" cy="${f(lampY + 8)}" rx="14" ry="6" fill="#ffffff" opacity="0.6" filter="url(#b4)"/>`;
  const swing = 3.4, deg = 5;
  const reach = -top;
  return {
    defs, bg, fg,
    parts: [{ name: 'lamp', group: 'subject', svg: lamp, pivot: [0, top - 10], m: { rot: wave(deg, swing) } }],
    fx: [
      // The pool of light on the table follows the lamp
      { type: 'glow', layer: 'scene', x: wave(f(Math.sin(deg * Math.PI / 180) * reach * -1), swing), y: -6, r: 110, sx: 1.8, color: '#d9a8ff', a: 0.28 },
      { type: 'digits', layer: 'top', keep: true, x: -3, y: -52, h: 15, from: 10, hold: 1.6, color: '#ff3b4d' },
      { type: 'glow', layer: 'top', x: 31, y: -58, r: 9, color: '#ff3b4d', a: keys(1, [[0, 1], [0.12, 1], [0.2, 0.15], [0.95, 0.15]], true) },
      { type: 'dots', layer: 'top', keep: true, pts: [[31, -58, 3.4, 0]], color: '#ff3b4d', a: keys(1, [[0, 1], [0.12, 1], [0.2, 0.2], [0.95, 0.2]], true) },
      // At zero the room pulses red, then it starts over
      { type: 'glow', layer: 'top', x: 0, y: -50, r: 150, sx: 1.5, color: '#ff2a3d', a: keys(11.6, [[0, 0], [10, 0], [10.15, 0.55], [10.6, 0.2], [10.9, 0.5], [11.6, 0]], true) },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// Sci-Fi: a flying saucer over a desert of mesas, its beam lifting sparks off the ground.
function g_scifi(L) {
  const { X, Y, S, halfW, skyTop } = helpers(L);
  const horizon = Y(-30);
  const planet = { x: X(-Math.min(halfW * 0.62, 104)), y: Y(Math.max(skyTop * 0.7, -150)), r: S(20) };
  const defs = `
  ${skyGrad('sfSky', horizon, [[0, '#01040c'], [0.55, '#05203a'], [1, '#0b5a72']])}
  <linearGradient id="sfFloor" gradientUnits="userSpaceOnUse" x1="0" y1="${f(horizon)}" x2="0" y2="${L.H}"><stop offset="0" stop-color="#062a3a"/><stop offset="1" stop-color="#020a12"/></linearGradient>
  <radialGradient id="sfPlanet" cx="0.35" cy="0.3" r="0.8"><stop offset="0" stop-color="#ffd2f0"/><stop offset="0.5" stop-color="#d45bd8"/><stop offset="1" stop-color="#4a1a78"/></radialGradient>
  <linearGradient id="sfDisc" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#e8f6ff"/><stop offset="0.45" stop-color="#8fb8cc"/><stop offset="1" stop-color="#2a4a5e"/></linearGradient>
  <radialGradient id="sfDome" cx="0.38" cy="0.3" r="0.8"><stop offset="0" stop-color="#e6ffff"/><stop offset="0.4" stop-color="#3ef0ff" stop-opacity="0.85"/><stop offset="1" stop-color="#0a5a78" stop-opacity="0.9"/></radialGradient>
  <linearGradient id="sfBeam" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#d9fdff" stop-opacity="0.75"/><stop offset="0.6" stop-color="#3ef0ff" stop-opacity="0.32"/><stop offset="1" stop-color="#3ef0ff" stop-opacity="0.12"/></linearGradient>`;

  const r = rng(12);
  let mesas = '';
  [[-1.0, 0.24, 22], [-0.62, 0.18, 34], [0.55, 0.2, 26], [0.9, 0.26, 40]].forEach(([fx, w, h]) => {
    const x = L.W * (0.5 + fx * 0.5), ww = L.W * w, hh = S(h);
    mesas += `<path d="M${f(x - ww / 2)},${f(horizon + 1)} L${f(x - ww / 2 + ww * 0.14)},${f(horizon - hh)} L${f(x + ww / 2 - ww * 0.16)},${f(horizon - hh)} L${f(x + ww / 2)},${f(horizon + 1)} Z" fill="#082636"/>
      <path d="M${f(x - ww / 2 + ww * 0.14)},${f(horizon - hh)} L${f(x + ww / 2 - ww * 0.16)},${f(horizon - hh)}" stroke="#3ef0ff" stroke-opacity="0.45" stroke-width="${f(S(1))}"/>`;
  });
  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#sfSky)"/>
  ${stars(L, 90, 33, horizon, '#d8fbff')}
  <ellipse cx="${f(planet.x)}" cy="${f(planet.y)}" rx="${f(planet.r * 2.1)}" ry="${f(planet.r * 0.5)}" fill="none" stroke="#ffb3f0" stroke-opacity="0.35" stroke-width="${f(S(3))}" transform="rotate(-14 ${f(planet.x)} ${f(planet.y)})"/>
  <circle cx="${f(planet.x)}" cy="${f(planet.y)}" r="${f(planet.r)}" fill="url(#sfPlanet)"/>
  <path d="M${f(planet.x - planet.r * 2.1)},${f(planet.y)} A${f(planet.r * 2.1)},${f(planet.r * 0.5)} 0 0 0 ${f(planet.x + planet.r * 2.1)},${f(planet.y)}" fill="none" stroke="#ffd9f6" stroke-opacity="0.8" stroke-width="${f(S(1.6))}" transform="rotate(-14 ${f(planet.x)} ${f(planet.y)})"/>
  <circle cx="${f(X(Math.min(halfW * 0.7, 120)))}" cy="${f(Y(Math.max(skyTop * 0.82, -170)))}" r="${f(S(5))}" fill="#bfefff" opacity="0.8"/>
  ${mesas}
  <rect y="${f(horizon)}" width="${L.W}" height="${f(L.H - horizon)}" fill="url(#sfFloor)"/>
  ${floorGrid(L, horizon, '#3ef0ff', { opacity: 0.32, rows: 6 })}
  <line x1="0" y1="${f(horizon)}" x2="${L.W}" y2="${f(horizon)}" stroke="#7ff6ff" stroke-width="${f(S(1.2))}" opacity="0.7"/>`;

  const fg = `
  <ellipse cx="0" cy="-2" rx="78" ry="10" fill="#3ef0ff" opacity="0.4" filter="url(#b8)"/>
  <ellipse cx="0" cy="-2" rx="58" ry="6" fill="#d9fdff" opacity="0.5" filter="url(#b2)"/>
  ${[[-96, 0, 9], [104, 2, 12], [-130, 4, 6]].map(([x, y, w]) => `<path d="M${x - w},${y} L${x - w * 0.6},${y - w * 0.9} L${x + w * 0.5},${y - w * 1.1} L${x + w},${y} Z" fill="#06202c"/>`).join('')}`;

  const ufoY = -120;
  const ufo = `
    <ellipse cx="0" cy="${ufoY + 12}" rx="40" ry="7" fill="#3ef0ff" opacity="0.9" filter="url(#b4)"/>
    <ellipse cx="0" cy="${ufoY + 6}" rx="44" ry="10" fill="#1a3444"/>
    <ellipse cx="0" cy="${ufoY + 9}" rx="26" ry="4.5" fill="#bffcff"/>
    <ellipse cx="0" cy="${ufoY}" rx="74" ry="15" fill="url(#sfDisc)"/>
    <ellipse cx="0" cy="${ufoY}" rx="74" ry="15" fill="none" stroke="#e8fbff" stroke-width="0.9"/>
    <path d="M-72,${ufoY - 2} Q0,${ufoY + 10} 72,${ufoY - 2}" stroke="#2a4a5e" stroke-width="1.2" fill="none"/>
    <path d="M-30,${ufoY - 7} C-28,${ufoY - 36} 28,${ufoY - 36} 30,${ufoY - 7} Z" fill="url(#sfDome)"/>
    <path d="M-30,${ufoY - 7} C-28,${ufoY - 36} 28,${ufoY - 36} 30,${ufoY - 7}" fill="none" stroke="#d9fdff" stroke-width="1"/>
    <path d="M-18,${ufoY - 22} Q-10,${ufoY - 31} 2,${ufoY - 31}" stroke="#ffffff" stroke-width="3" stroke-linecap="round" fill="none" opacity="0.7"/>
    <ellipse cx="0" cy="${ufoY - 7}" rx="32" ry="4" fill="#cfe9f5"/>
    ${Array.from({ length: 9 }, (_, i) => { const a = Math.PI * (0.08 + 0.84 * i / 8); return `<circle cx="${f(-Math.cos(a) * 64)}" cy="${f(ufoY + Math.sin(a) * 9)}" r="2.2" fill="#0a5a78"/>`; }).join('')}`;
  const rim = Array.from({ length: 9 }, (_, i) => { const a = Math.PI * (0.08 + 0.84 * i / 8); return [f(-Math.cos(a) * 64), f(ufoY + Math.sin(a) * 9), 5.5, f(-i / 9)]; });
  const beam = `<path d="M-24,${ufoY + 12} L24,${ufoY + 12} L68,-2 L-68,-2 Z" fill="url(#sfBeam)"/>
    <path d="M-24,${ufoY + 12} L-68,-2 M24,${ufoY + 12} L68,-2" stroke="#bffcff" stroke-width="0.8" opacity="0.6"/>`;
  const bob = wave(3.5, 2.8), tilt = wave(2.2, 4.2, 0.2);
  return {
    defs, bg, fg,
    parts: [
      { name: 'beam', group: 'subject', under: true, svg: beam, pivot: [0, ufoY + 12], m: { dy: bob, a: wave(0.14, 0.7, 0, 0.84), sx: wave(0.05, 1.9, 0, 1) } },
      { name: 'ufo', group: 'subject', svg: ufo, pivot: [0, ufoY], m: { dy: bob, rot: tilt } },
    ],
    fx: [
      { type: 'dots', layer: 'scene', pts: twinkles(L, 90, 33, horizon), color: '#d8fbff', a: 0.2, tw: [0.5, 2.6] },
      { type: 'particles', layer: 'top', shape: 'glowdot', n: 12, seed: 7, life: 2.6, x: -46, w: 92, y: -6, h: 4, vy: -34, dv: [4, 6], sway: [3, 1.2], size: [2.2, 4], color: ['#d9fdff', '#3ef0ff'], fade: [0.15, 0.4] },
      { type: 'dots', layer: 'top', dy: bob, pts: rim, color: '#ffe36b', a: 0.15, tw: [0.85, 1.1], shape: 'glowdot' },
      { type: 'glow', layer: 'top', dy: bob, x: 0, y: ufoY + 10, r: 70, sx: 1.4, color: '#3ef0ff', a: wave(0.12, 0.7, 0, 0.35) },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// Action: the hero walks away from the explosion, sparks and embers flying past.
function g_action(L) {
  const { X, Y, S, halfW, skyTop } = helpers(L);
  const horizon = Y(-26);
  const blastY = -92;
  const defs = `
  ${skyGrad('acSky', horizon, [[0, '#0a0410'], [0.45, '#3a0c18'], [0.8, '#a3281a'], [1, '#ff7a2a']])}
  <linearGradient id="acRoad" gradientUnits="userSpaceOnUse" x1="0" y1="${f(horizon)}" x2="0" y2="${L.H}"><stop offset="0" stop-color="#3a1210"/><stop offset="1" stop-color="#0a0306"/></linearGradient>
  <radialGradient id="acFire" cx="0.5" cy="0.62" r="0.6"><stop offset="0" stop-color="#fffbe6"/><stop offset="0.25" stop-color="#ffd36b"/><stop offset="0.55" stop-color="#ff7a1f"/><stop offset="0.85" stop-color="#d4261a"/><stop offset="1" stop-color="#6a0d12"/></radialGradient>
  <radialGradient id="acSmoke" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#5a1a1a"/><stop offset="1" stop-color="#2a0a10" stop-opacity="0"/></radialGradient>
  <radialGradient id="acHalo" gradientUnits="userSpaceOnUse" cx="${f(X(0))}" cy="${f(Y(blastY))}" r="${f(S(230))}"><stop offset="0" stop-color="#ffb347" stop-opacity="0.6"/><stop offset="0.4" stop-color="#ff5a3d" stop-opacity="0.22"/><stop offset="1" stop-color="#ff5a3d" stop-opacity="0"/></radialGradient>`;

  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#acSky)"/>
  ${stars(L, 24, 5, horizon - S(80), '#ffd9c2')}
  <rect width="${L.W}" height="${L.H}" fill="url(#acHalo)"/>
  <rect y="${f(horizon)}" width="${L.W}" height="${f(L.H - horizon)}" fill="url(#acRoad)"/>
  <path d="M${f(X(0))},${f(horizon)} L${f(X(-30))},${L.H} M${f(X(0))},${f(horizon)} L${f(X(30))},${L.H}" stroke="#ffb347" stroke-width="${f(S(1.4))}" opacity="0.25"/>
  ${[0.15, 0.4, 0.75].map(t => { const y = horizon + (L.H - horizon) * t * t; return `<line x1="${f(X(-2 - 26 * t))}" y1="${f(y)}" x2="${f(X(-2 - 26 * t) + S(1))}" y2="${f(y + S(6 * t + 2))}" stroke="#ffe0a0" stroke-width="${f(S(2.4 * t + 0.6))}" opacity="0.5"/>`; }).join('')}`;

  // The fireball: a column of fire rising into a mushroom of billows, smoke round its edge,
  // a white-hot heart low in it
  const rf = rng(17);
  let smoke = '', fire = '';
  const billow = (cx, cy, rx, ry, n, rMin, rMax, fill, out) => {
    for (let i = 0; i < n; i++) {
      const a = rf() * Math.PI * 2, d = Math.sqrt(rf());
      out.push(`<circle cx="${f(cx + Math.cos(a) * d * rx)}" cy="${f(cy + Math.sin(a) * d * ry)}" r="${f(rMin + rf() * (rMax - rMin))}" fill="${fill}"/>`);
    }
  };
  const sm = [], fi = [];
  billow(0, blastY - 48, 110, 40, 16, 26, 40, 'url(#acSmoke)', sm);
  billow(0, blastY - 34, 92, 30, 18, 20, 32, 'url(#acFire)', fi);
  billow(0, blastY + 10, 34, 40, 8, 18, 26, 'url(#acFire)', fi);
  billow(0, blastY + 40, 80, 14, 10, 14, 22, 'url(#acFire)', fi);
  smoke = sm.join(''); fire = fi.join('');
  const blast = `<g opacity="0.95">${smoke}</g><g filter="url(#b2)">${fire}</g>
    <ellipse cx="0" cy="${blastY - 30}" rx="70" ry="22" fill="#fff4cf" opacity="0.55" filter="url(#b8)"/>
    <ellipse cx="0" cy="${blastY + 36}" rx="44" ry="16" fill="#fff4cf" opacity="0.7" filter="url(#b8)"/>`;

  // Rooftops in front of the blast, drawn across the frame
  const rc = rng(23);
  let city = `M-4,${f(L.H)} L-4,${f(horizon)}`;
  for (let x = -4; x <= L.W + 4;) { const w = S(16 + rc() * 26), h = S(14 + rc() * 40) * (Math.abs(x - X(0)) < S(60) ? 0.45 : 1); city += ` L${f(x)},${f(horizon - h)} L${f(x + w)},${f(horizon - h)}`; x += w; }
  city += ` L${L.W + 4},${f(horizon)} L${L.W + 4},${f(L.H)} Z`;
  let lit = '';
  const rw = rng(29);
  for (let i = 0; i < 26; i++) lit += `<rect x="${f(rw() * L.W)}" y="${f(horizon - S(6 + rw() * 30))}" width="${f(S(2.2))}" height="${f(S(3))}" fill="#ffb347" opacity="${f(0.3 + rw() * 0.5)}"/>`;
  const cityPart = `<path d="${city}" fill="#14060c"/><path d="${city}" fill="none" stroke="#ff7a2a" stroke-opacity="0.35" stroke-width="${f(S(0.8))}"/>${lit}<rect y="${f(horizon)}" width="${L.W}" height="${f(L.H - horizon)}" fill="url(#acRoad)"/>`;

  // The hero, a long coat open as he walks toward us, the fire behind lighting his edges
  const body = `M-5,-131 L5,-131 L6,-125 L20,-121 C27,-119 30,-113 31,-106 L35,-70 L36,-58 L31,-56 L28,-68 L25,-84 L27,-40 L17,-38 L15,-30 L13,-4 L17,-2 L17,1 L5,1 L5,-26 L1,-50 L-4,-30 L-5,1 L-18,1 L-18,-2 L-14,-4 L-15,-34 L-18,-38 L-28,-40 L-25,-84 L-28,-68 L-31,-56 L-36,-58 L-35,-70 L-31,-106 C-30,-113 -27,-119 -20,-121 L-6,-125 Z`;
  const head = `M0,-149 C6,-149 9,-145 9,-139 C9,-133 6,-129 0,-129 C-6,-129 -9,-133 -9,-139 C-9,-145 -6,-149 0,-149 Z`;
  const fg = `
  <ellipse cx="0" cy="0" rx="40" ry="4" fill="#000" opacity="0.7" filter="url(#b2)"/>
  <path d="M-30,2 L-50,40 L50,40 L30,2 Z" fill="#ff7a2a" opacity="0.12" filter="url(#b4)"/>
  <g stroke="#ffb347" stroke-width="3.2" fill="none" opacity="0.65" filter="url(#b2)"><path d="${body}"/><path d="${head}"/></g>
  <g stroke="#ffe0a0" stroke-width="1.1" fill="none" opacity="0.9"><path d="${body}"/><path d="${head}"/></g>
  <path d="${body}" fill="#0a0406"/><path d="${head}" fill="#0a0406"/>
  <path d="M-9,-142 C-6,-151 6,-151 9,-142 C6,-146 -6,-146 -9,-142 Z" fill="#1a0a0c"/>
  <path d="M-7.5,-139.5 h15 v2.4 c-1,1.6 -5,1.6 -6,0 h-3 c-1,1.6 -5,1.6 -6,0 Z" fill="#0d1a24"/>
  <path d="M-6,-139 h4 M2,-139 h4" stroke="#7ff3ff" stroke-width="0.9" stroke-linecap="round" opacity="0.9"/>
  <path d="M-6,-125 L0,-104 L6,-125" stroke="#2a1014" stroke-width="1" fill="none"/>
  <path d="M-2,-104 L0,-84 L2,-104" stroke="#2a1014" stroke-width="0.8" fill="none"/>`;

  const center = [0, blastY + 6];
  const pulse = keys(4.2, [[0, 1], [0.08, 1.07], [0.5, 1.02], [1.6, 1.035], [2.4, 1.0], [3.2, 1.03]]);
  return {
    defs, bg, fg,
    parts: [
      { name: 'blast', group: 'scene', svg: blast, pivot: [0, blastY + 40], m: { s: pulse, dy: wave(-1.5, 1.3) } },
      { name: 'city', group: 'scene', frame: true, svg: cityPart },
    ],
    fx: [
      { type: 'glow', layer: 'scene', x: 0, y: blastY + 4, r: 120, color: '#ffd36b', a: keys(4.2, [[0, 0.7], [0.08, 0.95], [0.5, 0.35], [1.2, 0.5], [1.9, 0.3], [2.6, 0.45], [3.4, 0.32]]) },
      { type: 'ring', layer: 'scene', x: 0, y: blastY + 6, r: keys(4.2, [[0, 20, 1], [0.7, 210], [4.2, 210]]), lw: 2.2, color: '#ffe0a0', a: keys(4.2, [[0, 0.7, 1], [0.7, 0], [4.2, 0]]) },
      { type: 'flash', layer: 'top', color: '#ffb347', a: keys(4.2, [[0, 0.26, 1], [0.25, 0], [4.2, 0]]) },
      { type: 'particles', layer: 'top', shape: 'streak', n: 22, seed: 9, life: 1.8, from: center, w: 30, h: 16, arc: [190, 350], speed: [70, 150], g: 60, len: 9, size: [1.2, 2.4], color: ['#fff3c2', '#ffd36b', '#ff9a3d'], fade: [0.05, 0.5] },
      { type: 'particles', layer: 'top', shape: 'glowdot', n: 12, seed: 4, life: 3.4, x: -halfW, w: halfW * 2, y: -20, h: 30, vx: 6, vy: -22, dv: [10, 8], sway: [8, 2], size: [2, 3.6], color: ['#ffb347', '#ff7a2a'], fade: [0.2, 0.4] },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// Comedy: a stand-up mic in the spotlight at a brick-wall club, the laughs lighting up in neon.
function g_comedy(L) {
  const { X, Y, S, halfW, skyTop } = helpers(L);
  const floor = Y(-14);
  const defs = `
  ${skyGrad('coWall', floor, [[0, '#14061a'], [0.6, '#2e0d2c'], [1, '#4a1636']])}
  <linearGradient id="coFloor" gradientUnits="userSpaceOnUse" x1="0" y1="${f(floor)}" x2="0" y2="${L.H}"><stop offset="0" stop-color="#4a1c1c"/><stop offset="1" stop-color="#120608"/></linearGradient>
  <radialGradient id="coSpot" gradientUnits="userSpaceOnUse" cx="${f(X(0))}" cy="${f(Y(-80))}" r="${f(S(130))}"><stop offset="0" stop-color="#ffe7a8" stop-opacity="0.75"/><stop offset="0.5" stop-color="#ffd36b" stop-opacity="0.25"/><stop offset="1" stop-color="#ffd36b" stop-opacity="0"/></radialGradient>
  <linearGradient id="coCurtain" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#5a0a22"/><stop offset="0.3" stop-color="#b3163c"/><stop offset="0.55" stop-color="#6a0c26"/><stop offset="0.8" stop-color="#c41e46"/><stop offset="1" stop-color="#5a0a22"/></linearGradient>
  <radialGradient id="coGrille" cx="0.35" cy="0.3" r="0.8"><stop offset="0" stop-color="#ffffff"/><stop offset="0.5" stop-color="#b9b3c8"/><stop offset="1" stop-color="#4a4458"/></radialGradient>`;

  // Bricks over the whole wall, a little uneven
  const rb = rng(41);
  let bricks = '';
  const bw = S(26), bh = S(11);
  for (let row = 0, y = floor - bh; y > -bh; row++, y -= bh) {
    for (let x = (row % 2 ? -bw / 2 : 0); x < L.W; x += bw) bricks += `<rect x="${f(x + S(0.8))}" y="${f(y + S(0.8))}" width="${f(bw - S(1.6))}" height="${f(bh - S(1.6))}" rx="${f(S(1))}" fill="#3a1030" opacity="${f(0.45 + rb() * 0.4)}"/>`;
  }
  const curtain = side => {
    const x0 = side < 0 ? 0 : L.W - S(46), w = S(46);
    return `<rect x="${f(x0)}" y="0" width="${f(w)}" height="${f(floor + S(2))}" fill="url(#coCurtain)"/>
      <path d="M${f(side < 0 ? x0 + w : x0)},0 v${f(floor)}" stroke="#ff6b8a" stroke-width="${f(S(1))}" opacity="0.4"/>`;
  };
  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#coWall)"/>
  ${bricks}
  <rect width="${L.W}" height="${L.H}" fill="url(#coSpot)"/>
  ${curtain(-1)}${curtain(1)}
  <rect y="${f(floor)}" width="${L.W}" height="${f(L.H - floor)}" fill="url(#coFloor)"/>
  ${Array.from({ length: 5 }, (_, i) => { const t = (i + 1) / 5, y = floor + (L.H - floor) * t * t; return `<line x1="0" y1="${f(y)}" x2="${L.W}" y2="${f(y)}" stroke="#2a0c0c" stroke-width="${f(S(1))}"/>`; }).join('')}
  <line x1="0" y1="${f(floor)}" x2="${L.W}" y2="${f(floor)}" stroke="#ffd36b" stroke-width="${f(S(1.2))}" opacity="0.55"/>
  <ellipse cx="${f(X(0))}" cy="${f(Y(-4))}" rx="${f(S(90))}" ry="${f(S(14))}" fill="#ffe7a8" opacity="0.4" filter="url(#b8)"/>`;

  const fg = `
  <ellipse cx="0" cy="-4" rx="34" ry="5" fill="#000" opacity="0.5" filter="url(#b2)"/>
  <!-- stool -->
  <g transform="translate(46,0)">
    <path d="M-12,-2 L-8,-46 M12,-2 L8,-46 M-10,-22 h20" stroke="#2a1420" stroke-width="2.6" stroke-linecap="round"/>
    <ellipse cx="0" cy="-48" rx="16" ry="4.5" fill="#c41e46"/><ellipse cx="0" cy="-49.5" rx="14" ry="3" fill="#ff5a7a"/>
    <rect x="-5" y="-60" width="10" height="11" rx="2" fill="#7ff3ff" opacity="0.85"/><rect x="-5" y="-60" width="10" height="3" rx="1" fill="#e9ffff"/>
  </g>
  <!-- mic stand -->
  <ellipse cx="-8" cy="-2" rx="20" ry="4" fill="#1c1424"/><ellipse cx="-8" cy="-3" rx="17" ry="2.6" fill="#4a4458"/>
  <path d="M-8,-3 L-8,-98" stroke="#2a2434" stroke-width="3"/><path d="M-9,-3 L-9,-98" stroke="#8a84a0" stroke-width="0.8" opacity="0.8"/>
  <path d="M-8,-98 L0,-110" stroke="#2a2434" stroke-width="2.4"/>
  <path d="M-6,-56 C-30,-50 -36,-30 -24,-16 C-14,-6 -30,-2 -40,-2" stroke="#120a18" stroke-width="1.6" fill="none"/>
  <!-- the mic -->
  <g transform="rotate(28 3 -114)">
    <rect x="-1" y="-112" width="8" height="18" rx="3" fill="#1c1424"/>
    <circle cx="3" cy="-118" r="9" fill="url(#coGrille)"/>
    <path d="M-5,-118 h16 M-3,-123 h12 M-3,-113 h12 M3,-127 v18 M-2,-125 v14 M8,-125 v14" stroke="#4a4458" stroke-width="0.6" opacity="0.6"/>
    <rect x="-3" y="-111" width="12" height="3" rx="1" fill="#8a84a0"/>
  </g>`;

  // The laughs: neon words on the wall that pop one after another
  const top = Math.max(skyTop, -200);
  const word = (x, y, size, rot, color) => `<g transform="translate(${x},${y}) rotate(${rot})">
      <text x="0" y="0" text-anchor="middle" dominant-baseline="central" font-family="Kanit" font-style="italic" font-weight="600" font-size="${size}" fill="none" stroke="${color}" stroke-width="${f(size * 0.16)}" opacity="0.55" filter="url(#b2)">HA!</text>
      <text x="0" y="0" text-anchor="middle" dominant-baseline="central" font-family="Kanit" font-style="italic" font-weight="600" font-size="${size}" fill="#fff6e0" stroke="${color}" stroke-width="${f(size * 0.05)}">HA!</text></g>`;
  const spots = [
    [-Math.min(halfW * 0.56, 86), Math.max(top * 0.66, -132), 26, -12, '#ffd36b'],
    [Math.min(halfW * 0.6, 92), Math.max(top * 0.74, -146), 22, 10, '#ff6fb5'],
    [-Math.min(halfW * 0.2, 30), Math.max(top * 0.86, -168), 18, 6, '#7ff3ff'],
  ];
  const pop = start => keys(2.7, [[0, 1], [start, 1], [start + 0.1, 1.28], [start + 0.32, 0.94], [start + 0.48, 1]]);
  return {
    defs, bg, fg,
    parts: spots.map(([x, y, size, rot, c], i) => ({
      name: `ha${i + 1}`, group: 'scene', svg: word(x, y, size, rot, c), pivot: [x, y],
      m: { s: pop(0.3 + i * 0.55), rot: keys(2.7, [[0, 0], [0.3 + i * 0.55, 0], [0.42 + i * 0.55, i % 2 ? -7 : 7], [0.7 + i * 0.55, 0]]) },
    })),
    fx: [
      { type: 'glow', layer: 'scene', x: 0, y: -80, r: 120, color: '#ffe7a8', a: wave(0.05, 0.23, 0, 0.12) },
      ...spots.map(([x, y, size, rot, c], i) => ({ type: 'glow', layer: 'scene', x, y, r: size * 1.6, color: c, a: keys(2.7, [[0, 0], [0.3 + i * 0.55, 0], [0.4 + i * 0.55, 0.55], [0.9 + i * 0.55, 0.1]]) })),
      { type: 'particles', layer: 'top', shape: 'spark', n: 8, seed: 11, life: 2.2, x: -halfW * 0.8, w: halfW * 1.6, y: Math.max(top * 0.9, -170), h: 90, vy: -8, size: [4, 7], color: ['#ffd36b', '#ffffff', '#ff6fb5'], fade: [0.4, 0.5], spin: 90 },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// Drama: an empty stage after the show, one rose in the spotlight, the velvet curtains breathing.
function g_drama(L) {
  const { X, Y, S, halfW, skyTop } = helpers(L);
  const floor = Y(-18);
  const top = Math.max(skyTop, -260);
  const defs = `
  ${skyGrad('drBack', floor, [[0, '#0c0310'], [0.7, '#2a0828'], [1, '#4a0c3a']])}
  <linearGradient id="drStage" gradientUnits="userSpaceOnUse" x1="0" y1="${f(floor)}" x2="0" y2="${L.H}"><stop offset="0" stop-color="#3a1428"/><stop offset="1" stop-color="#0e040c"/></linearGradient>
  <linearGradient id="drVelvet" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="#4a0626"/><stop offset="0.12" stop-color="#c8155a"/><stop offset="0.24" stop-color="#5a0830"/><stop offset="0.38" stop-color="#e02a6e"/><stop offset="0.5" stop-color="#6a0a36"/>
    <stop offset="0.64" stop-color="#d81f62"/><stop offset="0.78" stop-color="#560830"/><stop offset="0.9" stop-color="#c8155a"/><stop offset="1" stop-color="#3a0420"/>
  </linearGradient>
  <linearGradient id="drShade" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#000" stop-opacity="0.45"/><stop offset="0.3" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity="0.35"/></linearGradient>
  <linearGradient id="drBeam" gradientUnits="userSpaceOnUse" x1="0" y1="${f(Y(top))}" x2="0" y2="${f(Y(0))}"><stop offset="0" stop-color="#ffe6f2" stop-opacity="0.05"/><stop offset="1" stop-color="#ffd0e6" stop-opacity="0.32"/></linearGradient>
  <radialGradient id="drPool" gradientUnits="userSpaceOnUse" cx="${f(X(0))}" cy="${f(Y(-6))}" r="${f(S(100))}"><stop offset="0" stop-color="#fff0f6" stop-opacity="0.75"/><stop offset="0.6" stop-color="#ff9ac6" stop-opacity="0.2"/><stop offset="1" stop-color="#ff6fb5" stop-opacity="0"/></radialGradient>
  <radialGradient id="drRose" cx="0.45" cy="0.4" r="0.6"><stop offset="0" stop-color="#ff7aa8"/><stop offset="0.6" stop-color="#e0144e"/><stop offset="1" stop-color="#7a0626"/></radialGradient>`;

  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#drBack)"/>
  ${Array.from({ length: 12 }, (_, i) => `<rect x="${f(X(-halfW) + i * L.W / 11 - S(4))}" y="0" width="${f(S(8))}" height="${f(floor)}" fill="#1a0418" opacity="0.6"/>`).join('')}
  <path d="M${f(X(-26))},${f(Y(top))} L${f(X(26))},${f(Y(top))} L${f(X(96))},${f(Y(-4))} L${f(X(-96))},${f(Y(-4))} Z" fill="url(#drBeam)"/>
  <rect y="${f(floor)}" width="${L.W}" height="${f(L.H - floor)}" fill="url(#drStage)"/>
  ${Array.from({ length: 9 }, (_, i) => { const x = X(-halfW) + (i - 4) * S(70) + L.W / 2; return `<line x1="${f(X(0) + (x - X(0)) * 0.35)}" y1="${f(floor)}" x2="${f(x)}" y2="${L.H}" stroke="#1c0614" stroke-width="${f(S(1))}"/>`; }).join('')}
  <line x1="0" y1="${f(floor)}" x2="${L.W}" y2="${f(floor)}" stroke="#ff6fb5" stroke-width="${f(S(1.2))}" opacity="0.5"/>
  <rect width="${L.W}" height="${L.H}" fill="url(#drPool)"/>`;

  // Each curtain hangs from the top of the frame and is gathered toward its edge
  const cw = Math.max(halfW * 0.42, 60);
  const curtain = side => {
    const xIn = side * (halfW - cw), xOut = side * (halfW + 6);
    const bottom = 4, sweep = side * 18;
    const d = `M${f(xOut)},${f(top - 4)} L${f(xIn)},${f(top - 4)} C${f(xIn)},${f(top * 0.5)} ${f(xIn + sweep)},${f(-40)} ${f(xIn + sweep * 1.4)},${bottom} L${f(xOut)},${bottom} Z`;
    const id = `drC${side < 0 ? 'L' : 'R'}`;
    return `<clipPath id="${id}"><path d="${d}"/></clipPath>
      <g clip-path="url(#${id})"><rect x="${f(Math.min(xIn, xOut) - 30)}" y="${f(top - 6)}" width="${f(cw + 60)}" height="${f(-top + 12)}" fill="url(#drVelvet)"/>
      <rect x="${f(Math.min(xIn, xOut) - 30)}" y="${f(top - 6)}" width="${f(cw + 60)}" height="${f(-top + 12)}" fill="url(#drShade)"/></g>
      <path d="M${f(xIn)},${f(top - 4)} C${f(xIn)},${f(top * 0.5)} ${f(xIn + sweep)},${f(-40)} ${f(xIn + sweep * 1.4)},${bottom}" stroke="#ff9ac6" stroke-width="1.2" fill="none" opacity="0.6"/>
      <path d="M${f(xIn + sweep * 0.7)},${f(top * 0.32)} q${f(side * 10)},6 ${f(side * 24)},2" stroke="#ffd36b" stroke-width="2.4" fill="none" stroke-linecap="round"/>`;
  };
  // The valance across the top, scalloped
  let scallops = '';
  const vy = top + Math.min(-top * 0.12, 26);
  for (let x = -halfW - 20; x < halfW + 20; x += 24) scallops += ` Q${f(x + 12)},${f(vy + 12)} ${f(x + 24)},${f(vy)}`;
  const valance = `<path d="M${f(-halfW - 20)},${f(top - 6)} L${f(halfW + 44)},${f(top - 6)} L${f(halfW + 44)},${f(vy)} L${f(-halfW - 20)},${f(vy)} Z" fill="#8a0c3c"/>
    <path d="M${f(-halfW - 20)},${f(vy)} ${scallops} L${f(halfW + 20)},${f(vy - 14)} L${f(-halfW - 20)},${f(vy - 14)} Z" fill="#b3124c"/>
    <path d="M${f(-halfW - 20)},${f(vy)} ${scallops}" stroke="#ffd36b" stroke-width="1.6" fill="none" opacity="0.9"/>`;

  const petal = (x, y, r, rot) => `<path d="M0,-5 C3,-5 5,-2 4,2 C3,4.5 1,5 0,3.5 C-1,5 -3,4.5 -4,2 C-5,-2 -3,-5 0,-5 Z" fill="#e0144e" transform="translate(${x},${y}) rotate(${rot}) scale(${r})"/>`;
  const fg = `
  <ellipse cx="0" cy="-2" rx="64" ry="7" fill="#000" opacity="0.35" filter="url(#b4)"/>
  <!-- the rose, lying on the boards -->
  <path d="M-58,-4 C-30,-8 0,-10 26,-14" stroke="#2f8a4a" stroke-width="2.6" fill="none" stroke-linecap="round"/>
  <path d="M-58,-4 C-30,-8 0,-10 26,-14" stroke="#7fe0a0" stroke-width="0.8" fill="none" opacity="0.6"/>
  <path d="M-20,-8 C-26,-20 -14,-24 -8,-14 C-12,-12 -16,-10 -20,-8 Z" fill="#2f8a4a"/>
  <path d="M4,-11 C6,-2 18,-2 18,-8 C12,-8 8,-10 4,-11 Z" fill="#2a7a42"/>
  <path d="M-36,-6 l-3,-4 M-10,-9 l2,-4" stroke="#2f8a4a" stroke-width="1.2" stroke-linecap="round"/>
  <g transform="translate(36,-20) rotate(-14)">
    <path d="M-12,8 C-16,-2 -10,-12 0,-13 C10,-12 16,-2 12,8 C6,13 -6,13 -12,8 Z" fill="url(#drRose)"/>
    <path d="M-9,4 C-11,-4 -6,-9 0,-9 C6,-9 10,-4 8,4" stroke="#7a0626" stroke-width="1.2" fill="none"/>
    <path d="M-5,0 C-5,-5 4,-6 4,0 C4,3 -2,4 -3,1" stroke="#7a0626" stroke-width="1" fill="none"/>
    <path d="M-10,-6 C-6,-12 4,-12 8,-8" stroke="#ffb3cc" stroke-width="1" fill="none" opacity="0.7"/>
    <path d="M-14,10 C-6,6 6,6 14,10 C8,14 -8,14 -14,10 Z" fill="#2a7a42"/>
  </g>
  ${petal(62, -4, 0.9, 30)}${petal(-6, -2, 0.7, 120)}`;

  return {
    defs, bg, fg,
    parts: [
      { name: 'curtainL', group: 'scene', svg: curtain(-1), pivot: [-halfW, top], m: { rot: wave(-0.7, 4.6), sx: wave(0.025, 3.1, 0.25, 1) } },
      { name: 'curtainR', group: 'scene', svg: curtain(1), pivot: [halfW, top], m: { rot: wave(0.7, 4.6, 0.4), sx: wave(0.025, 3.4, 0.6, 1) } },
      { name: 'valance', group: 'scene', svg: valance },
    ],
    fx: [
      { type: 'glow', layer: 'scene', x: 0, y: -8, r: 90, sx: 1.6, color: '#ffd0e6', a: wave(0.06, 0.37, 0, 0.22) },
      { type: 'particles', layer: 'scene', shape: 'glowdot', n: 14, seed: 13, life: 6, x: -60, w: 120, y: Math.max(top * 0.8, -160), h: -Math.max(top * 0.8, -160) - 20, vx: 2, vy: 4, dv: [3, 3], sway: [6, 4], size: [1.4, 2.6], color: '#fff0f6', alpha: [0.35, 0.8], fade: [0.3, 0.3] },
      { type: 'particles', layer: 'top', shape: 'petal', n: 3, seed: 21, life: 5.5, x: -40, w: 80, y: Math.max(top * 0.95, -190), h: 10, vy: (-Math.max(top * 0.95, -190)) / 5.5, sway: [12, 2.2], size: [6, 8], spin: 70, color: ['#e0144e', '#ff4f8a'], fade: [0.1, 0.15] },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// Animation: the classic bouncing-ball exercise on a sheet of animation paper, the ball
// itself bouncing along its pencilled arc with squash and stretch.
function g_animation(L) {
  const { X, Y, S, halfW, skyTop } = helpers(L);
  const top = Math.max(skyTop, -260);
  const defs = `
  ${skyGrad('anWall', L.H, [[0, '#06140c'], [0.6, '#0e2a1a'], [1, '#071a10']])}
  <radialGradient id="anLamp" gradientUnits="userSpaceOnUse" cx="${f(X(0))}" cy="${f(Y(-90))}" r="${f(S(220))}"><stop offset="0" stop-color="#d6ffcc" stop-opacity="0.4"/><stop offset="0.5" stop-color="#86ff6a" stop-opacity="0.12"/><stop offset="1" stop-color="#86ff6a" stop-opacity="0"/></radialGradient>
  <linearGradient id="anPaper" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f6fff0"/><stop offset="1" stop-color="#d4ecc8"/></linearGradient>
  <radialGradient id="anBall" cx="0.35" cy="0.3" r="0.75"><stop offset="0" stop-color="#ffd6ea"/><stop offset="0.45" stop-color="#ff4f9a"/><stop offset="1" stop-color="#a3125a"/></radialGradient>`;

  // Sprocketed film strips along the wall, behind the paper
  const strip = y => {
    let holes = '';
    for (let x = -8; x < L.W + 8; x += S(12)) holes += `<rect x="${f(x)}" y="${f(y + S(2.5))}" width="${f(S(6))}" height="${f(S(4))}" rx="${f(S(1))}" fill="#06140c"/><rect x="${f(x)}" y="${f(y + S(21.5))}" width="${f(S(6))}" height="${f(S(4))}" rx="${f(S(1))}" fill="#06140c"/>`;
    let frames = '';
    for (let x = 0; x < L.W; x += S(30)) frames += `<rect x="${f(x + S(2))}" y="${f(y + S(8))}" width="${f(S(26))}" height="${f(S(12))}" fill="#1f5a34" opacity="0.6"/>`;
    return `<rect x="-8" y="${f(y)}" width="${L.W + 16}" height="${f(S(28))}" fill="#14301f"/>${frames}${holes}`;
  };
  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#anWall)"/>
  ${stars(L, 30, 77, L.H, '#c8ffbf')}
  ${strip(Y(Math.max(top * 0.86, -184)))}
  ${strip(Y(-18))}
  <rect width="${L.W}" height="${L.H}" fill="url(#anLamp)"/>`;

  // The ball's arc: drops from the top left, bounces lower each time toward the right
  const g0 = -36, R = 10;
  const pts = [[-96, -150], [-46, g0], [-6, -112], [30, g0], [58, -82], [82, g0], [100, -60]];
  let arc = `M${pts[0][0]},${pts[0][1] + R}`;
  const ghostPts = [];
  for (let i = 1; i < pts.length; i++) {
    const [x0, y0] = pts[i - 1], [x1, y1] = pts[i];
    // Falling from a top is ease-in, rising to one ease-out: a quadratic from the top's level
    const c = y1 > y0 ? [x1 - (x1 - x0) * 0.42, y0] : [x0 + (x1 - x0) * 0.42, y1];
    arc += ` Q${f(c[0])},${f(c[1] + R)} ${x1},${y1 + R}`;
    // Onion skins along the arc, closer together near the tops where the ball is slow
    for (const t of y1 > y0 ? [0.4, 0.75] : [0.3, 0.65]) {
      const u = 1 - t;
      ghostPts.push([u * u * x0 + 2 * u * t * c[0] + t * t * x1, u * u * y0 + 2 * u * t * c[1] + t * t * y1]);
    }
    if (i % 2 === 0) ghostPts.push([x1, y1]);
  }
  const ghosts = ghostPts.map(([x, y], i) => `<circle cx="${f(x)}" cy="${f(y)}" r="${R}" fill="none" stroke="#3a8a5a" stroke-width="1.1" opacity="${f(0.45 + (i % 3) * 0.1)}"/>`).join('');
  const squashes = [[-46, g0], [30, g0], [82, g0]].map(([x, y]) => `<ellipse cx="${x}" cy="${y + 4}" rx="${R * 1.3}" ry="${R * 0.62}" fill="none" stroke="#3a8a5a" stroke-width="1.1" opacity="0.6"/>`).join('');
  const pw = 128, ph = 92, py = -24;
  const fg = `
  <ellipse cx="0" cy="${py + 6}" rx="${pw}" ry="6" fill="#000" opacity="0.45" filter="url(#b4)"/>
  <!-- animation paper with its peg holes -->
  <path d="M${-pw},${py} L${-pw + 6},${py - ph * 1.6} L${pw - 6},${py - ph * 1.6} L${pw},${py} Z" fill="url(#anPaper)"/>
  <path d="M${-pw},${py} L${-pw + 6},${py - ph * 1.6} L${pw - 6},${py - ph * 1.6} L${pw},${py} Z" fill="none" stroke="#86ff6a" stroke-width="1.2" opacity="0.8"/>
  <g fill="#0e2a1a"><rect x="-26" y="${f(py - ph * 1.6 + 5)}" width="12" height="4" rx="2"/><circle cx="0" cy="${f(py - ph * 1.6 + 7)}" r="3"/><rect x="14" y="${f(py - ph * 1.6 + 5)}" width="12" height="4" rx="2"/></g>
  <path d="${arc}" stroke="#7fbf9a" stroke-width="1" stroke-dasharray="3 3" fill="none"/>
  ${ghosts}${squashes}
  <path d="M${-pw + 10},${g0 + R + 1} H${pw - 10}" stroke="#3a8a5a" stroke-width="1.2" opacity="0.7"/>
  <text x="${pw - 14}" y="${f(py - 8)}" text-anchor="end" font-family="Kanit" font-style="italic" font-weight="600" font-size="9" fill="#3a8a5a" opacity="0.8">SC 01 · 24 fps</text>
  <!-- pencil -->
  <g transform="translate(${-pw + 30},${py - 6}) rotate(-8)">
    <rect x="0" y="-3" width="58" height="6" fill="#ffd36b"/><rect x="0" y="-3" width="58" height="2" fill="#fff0b8"/>
    <rect x="-8" y="-3" width="8" height="6" fill="#ff8fbd"/><rect x="-1" y="-3" width="3" height="6" fill="#c8c8c8"/>
    <path d="M58,-3 L70,0 L58,3 Z" fill="#f2d2a8"/><path d="M66,-1 L70,0 L66,1 Z" fill="#2a2a2a"/>
  </g>`;

  // The bounce: the ball rests at the first top, then runs the arc and starts over
  const T = [0, 0.42, 0.74, 1.02, 1.26, 1.46, 1.64];
  const end = 2.4;
  const dx = keys(end, [...pts.map(([x], i) => [T[i], x - pts[0][0], 1]), [2.0, pts[6][0] - pts[0][0], 0], [2.3, 0, 1]]);
  const dy = keys(end, [...pts.map(([, y], i) => [T[i], y - pts[0][1], i % 2 ? 3 : 2]), [2.0, pts[6][1] - pts[0][1], 0], [2.3, 0, 1]]);
  // Squash on each landing, stretch as it falls
  const hit = (t, k) => [[t - 0.08, 0.86 + 0 * k], [t, 1.32], [t + 0.06, 0.92], [t + 0.12, 1]];
  const sx = keys(end, [[0, 1], [0.3, 0.9], ...hit(T[1]), ...hit(T[3]), ...hit(T[5]), [2.0, 1]]);
  const sy = keys(end, [[0, 1], [0.3, 1.14], [T[1] - 0.08, 1.16], [T[1], 0.64], [T[1] + 0.06, 1.08], [T[1] + 0.12, 1], [T[3] - 0.08, 1.12], [T[3], 0.7], [T[3] + 0.06, 1.05], [T[3] + 0.12, 1], [T[5] - 0.08, 1.08], [T[5], 0.78], [T[5] + 0.06, 1.03], [T[5] + 0.12, 1], [2.0, 1]]);
  const ball = `<circle cx="${pts[0][0]}" cy="${pts[0][1]}" r="${R}" fill="url(#anBall)"/><circle cx="${pts[0][0]}" cy="${pts[0][1]}" r="${R}" fill="none" stroke="#7a0a3c" stroke-width="1"/><ellipse cx="${pts[0][0] - 3}" cy="${pts[0][1] - 4}" rx="3.2" ry="2.2" fill="#fff" opacity="0.8"/>`;
  return {
    defs, bg, fg,
    parts: [{ name: 'ball', group: 'subject', svg: ball, pivot: [pts[0][0], pts[0][1] + R], m: { dx, dy, sx, sy } }],
    fx: [
      { type: 'glow', layer: 'top', x: pts[0][0], y: pts[0][1], dx, dy, r: 26, color: '#ff4f9a', a: 0.28 },
      { type: 'dots', layer: 'scene', pts: twinkles(L, 30, 77, L.H), color: '#c8ffbf', a: 0.2, tw: [0.5, 1.8] },
      { type: 'particles', layer: 'top', shape: 'spark', n: 6, seed: 31, life: 1.8, x: -pw, w: pw * 2, y: py - ph * 1.6 - 20, h: 40, size: [5, 8], color: ['#86ff6a', '#ffffff'], fade: [0.4, 0.5], spin: 120 },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// Crime: a police cruiser in a wet alley behind the tape, its light bar washing the walls red and blue.
function g_crime(L) {
  const { X, Y, S, halfW, skyTop } = helpers(L);
  const horizon = Y(-48);
  const lampX = -Math.min(halfW * 0.7, 120);
  const defs = `
  ${skyGrad('crSky', horizon, [[0, '#04030a'], [0.7, '#140a1e'], [1, '#2a1424']])}
  <linearGradient id="crGround" gradientUnits="userSpaceOnUse" x1="0" y1="${f(horizon)}" x2="0" y2="${L.H}"><stop offset="0" stop-color="#1a0e1a"/><stop offset="1" stop-color="#06040a"/></linearGradient>
  <radialGradient id="crLamp" gradientUnits="userSpaceOnUse" cx="${f(X(lampX))}" cy="${f(Y(-120))}" r="${f(S(150))}"><stop offset="0" stop-color="#ffb35c" stop-opacity="0.6"/><stop offset="0.4" stop-color="#ff8a3d" stop-opacity="0.18"/><stop offset="1" stop-color="#ff8a3d" stop-opacity="0"/></radialGradient>
  <linearGradient id="crBody" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f4f6ff"/><stop offset="0.5" stop-color="#c9cee0"/><stop offset="1" stop-color="#7a809a"/></linearGradient>
  <linearGradient id="crDark" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2a2e44"/><stop offset="1" stop-color="#0c0e18"/></linearGradient>
  <linearGradient id="crGlass" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#5a6a9a"/><stop offset="1" stop-color="#141a30"/></linearGradient>`;

  // Brick walls on both sides, running off toward the back of the alley
  const rb = rng(55);
  const wall = side => {
    const xNear = side < 0 ? 0 : L.W, xFar = X(side * Math.min(halfW * 0.45, 70));
    let bricks = '';
    for (let j = 0; j < 14; j++) {
      const t = j / 14, yN = L.H * 0.04 + t * (Y(0) + S(20)), yF = Y(-150) + t * (horizon - Y(-150));
      bricks += `<path d="M${f(xNear)},${f(yN)} L${f(xFar)},${f(yF)}" stroke="#2a141e" stroke-width="${f(S(0.8))}" opacity="${f(0.5 + rb() * 0.3)}"/>`;
    }
    return `<path d="M${f(xNear)},0 L${f(xFar)},${f(Y(-150))} L${f(xFar)},${f(horizon)} L${f(xNear)},${f(Y(0) + S(30))} Z" fill="#170a14"/>${bricks}
      <path d="M${f(xFar)},${f(Y(-150))} L${f(xFar)},${f(horizon)}" stroke="#ff8a3d" stroke-opacity="0.25" stroke-width="${f(S(1))}"/>`;
  };
  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#crSky)"/>
  ${stars(L, 20, 57, horizon - S(60), '#e0d8ff')}
  <rect y="${f(horizon)}" width="${L.W}" height="${f(L.H - horizon)}" fill="url(#crGround)"/>
  ${wall(-1)}${wall(1)}
  <rect width="${L.W}" height="${L.H}" fill="url(#crLamp)"/>
  <path d="M${f(X(lampX))},${f(Y(-112))} L${f(X(lampX))},${f(L.H)}" stroke="#0c0610" stroke-width="${f(S(3))}"/>
  <path d="M${f(X(lampX))},${f(Y(-112))} q${f(S(10))},${f(-S(10))} ${f(S(22))},${f(-S(6))}" stroke="#0c0610" stroke-width="${f(S(2.4))}" fill="none"/>
  <ellipse cx="${f(X(lampX + 22))}" cy="${f(Y(-117))}" rx="${f(S(7))}" ry="${f(S(3))}" fill="#ffd9a8"/>
  <path d="M${f(X(lampX + 15))},${f(Y(-116))} L${f(X(lampX + 29))},${f(Y(-116))} L${f(X(lampX + 60))},${f(Y(0))} L${f(X(lampX - 16))},${f(Y(0))} Z" fill="#ffb35c" opacity="0.08"/>
  ${[0.12, 0.3, 0.55, 0.8].map((t, i) => `<line x1="${f(X(-halfW * 0.9 + i * 40))}" y1="${f(Y(6 + t * 40))}" x2="${f(X(-halfW * 0.9 + i * 40 + 40))}" y2="${f(Y(6 + t * 40))}" stroke="#ffb35c" stroke-width="${f(S(1))}" opacity="0.18"/>`).join('')}`;

  // An 80s cruiser in profile, nose to the left
  const car = `<g transform="translate(6,0) scale(1.22)">
  <ellipse cx="0" cy="0" rx="96" ry="6" fill="#000" opacity="0.7" filter="url(#b2)"/>
  <path d="M-92,-14 L-90,-30 C-88,-36 -82,-38 -70,-39 L-44,-41 L-30,-58 C-28,-61 -24,-62 -18,-62 L34,-62 C40,-62 44,-60 48,-56 L62,-41 L84,-39 C92,-38 94,-34 94,-28 L94,-14 Z" fill="url(#crBody)"/>
  <path d="M-92,-26 L94,-26 L94,-14 L-92,-14 Z" fill="url(#crDark)"/>
  <path d="M-88,-36 L-44,-41 L62,-41 L90,-37" stroke="#ffffff" stroke-width="0.9" fill="none" opacity="0.8"/>
  <path d="M-26,-56 L-16,-56 L-16,-42 L-38,-42 Z" fill="url(#crGlass)"/>
  <path d="M-12,-58 L14,-58 L14,-42 L-12,-42 Z" fill="url(#crGlass)"/>
  <path d="M18,-58 L34,-58 C38,-58 41,-56 44,-53 L54,-42 L18,-42 Z" fill="url(#crGlass)"/>
  <path d="M-10,-55 L-2,-55 L-10,-46 Z" fill="#9fb0e0" opacity="0.5"/>
  <path d="M-16,-41 V-14 M16,-41 V-14" stroke="#7a809a" stroke-width="0.8"/>
  <rect x="-6" y="-37" width="22" height="9" rx="1" fill="#14182c"/>
  <path d="M-3,-34 h16 M-3,-31 h12" stroke="#f4f6ff" stroke-width="1" opacity="0.8"/>
  <rect x="-94" y="-24" width="7" height="5" rx="1" fill="#fff6d6"/><rect x="88" y="-24" width="6" height="5" rx="1" fill="#ff3b4d"/>
  <path d="M-94,-22 L-130,-26 L-130,-16 Z" fill="#fff6d6" opacity="0.18"/>
  ${[-58, 58].map(x => `<circle cx="${x}" cy="-12" r="13" fill="#0a0a12"/><circle cx="${x}" cy="-12" r="7" fill="#7a809a"/><circle cx="${x}" cy="-12" r="3" fill="#2a2e44"/>`).join('')}
  <!-- light bar -->
  <rect x="-20" y="-68" width="40" height="6" rx="2" fill="#1a1c2c"/>
  <rect x="-18" y="-68" width="16" height="5" rx="2" fill="#7a1020"/><rect x="2" y="-68" width="16" height="5" rx="2" fill="#10307a"/></g>`;
  const tapeY = -20, tx0 = -halfW - 10, tx1 = halfW + 10;
  let marks = '';
  for (let x = tx0 + 8; x < tx1 - 8; x += 26) marks += `<path d="M${f(x)},${f(tapeY - 4 + (x - tx0) * 0.03)} l6,0 l-4,8 l-6,0 Z" fill="#1a1406"/>`;
  const tape = `<path d="M${tx0},${tapeY - 4} L${tx1},${f(tapeY - 4 + (tx1 - tx0) * 0.03)} L${tx1},${f(tapeY + 4 + (tx1 - tx0) * 0.03)} L${tx0},${tapeY + 4} Z" fill="#ffd21e"/>${marks}
    <path d="M${tx0},${tapeY - 4} L${tx1},${f(tapeY - 4 + (tx1 - tx0) * 0.03)}" stroke="#fff3a0" stroke-width="0.8" opacity="0.8"/>`;
  const red = keys(0.9, [[0, 0, 1], [0.02, 1, 1], [0.1, 1, 1], [0.14, 0, 1], [0.2, 0, 1], [0.22, 1, 1], [0.3, 1, 1], [0.34, 0, 1]]);
  const blue = keys(0.9, [[0, 0, 1], [0.45, 0, 1], [0.47, 1, 1], [0.55, 1, 1], [0.59, 0, 1], [0.65, 0, 1], [0.67, 1, 1], [0.75, 1, 1], [0.79, 0, 1]]);
  const scale = (k, m) => ({ k: { ...k.k, v: k.k.v.map(([t, v, e]) => [t, v * m, e]) } });
  return {
    defs, bg, fg: car,
    parts: [{ name: 'tape', group: 'subject', svg: tape, pivot: [tx0, tapeY], m: { rot: wave(0.8, 2.3), sy: wave(0.08, 1.1, 0, 1) } }],
    fx: [
      { type: 'glow', layer: 'scene', x: -halfW * 0.6, y: -90, r: 170, color: '#ff2a3d', a: scale(red, 0.42) },
      { type: 'glow', layer: 'scene', x: halfW * 0.6, y: -90, r: 170, color: '#2a6bff', a: scale(blue, 0.48) },
      { type: 'glow', layer: 'scene', x: lampX + 22, y: -117, r: 40, color: '#ffb35c', a: keys(5, [[0, 0.5], [3.1, 0.5, 1], [3.15, 0.1, 1], [3.25, 0.55, 1], [3.3, 0.15, 1], [3.45, 0.5]]) },
      { type: 'glow', layer: 'top', x: -6, y: -80, r: 30, color: '#ff2a3d', a: red },
      { type: 'glow', layer: 'top', x: 18, y: -80, r: 30, color: '#3a7bff', a: blue },
      { type: 'dots', layer: 'top', pts: [[-6, -80, 11, 0]], shape: 'glowdot', color: '#ff5a6a', a: red },
      { type: 'dots', layer: 'top', pts: [[18, -80, 11, 0]], shape: 'glowdot', color: '#6a9bff', a: blue },
      { type: 'glow', layer: 'top', x: -40, y: 14, r: 120, sx: 2.2, color: '#ff2a3d', a: scale(red, 0.22) },
      { type: 'glow', layer: 'top', x: 40, y: 14, r: 120, sx: 2.2, color: '#2a6bff', a: scale(blue, 0.26) },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// Adventure: a lost temple rising out of the jungle at sunset, torches lit, birds over the canopy.
function g_adventure(L) {
  const { X, Y, S, halfW, skyTop } = helpers(L);
  const horizon = Y(-40);
  const sun = { x: X(0), y: Y(-118) };
  const defs = `
  ${skyGrad('adSky', horizon, [[0, '#04161a'], [0.45, '#0c4a48'], [0.8, '#d48a3a'], [1, '#ffcf7a']])}
  <radialGradient id="adSun" gradientUnits="userSpaceOnUse" cx="${f(sun.x)}" cy="${f(sun.y)}" r="${f(S(170))}"><stop offset="0" stop-color="#fff2c2" stop-opacity="0.9"/><stop offset="0.18" stop-color="#ffd36b" stop-opacity="0.5"/><stop offset="1" stop-color="#ff9a3d" stop-opacity="0"/></radialGradient>
  <linearGradient id="adStone" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#2a3a34"/><stop offset="0.5" stop-color="#3e5248"/><stop offset="1" stop-color="#1a2420"/></linearGradient>
  <linearGradient id="adJungle" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0a2a22"/><stop offset="1" stop-color="#03100c"/></linearGradient>`;

  const canopy = (y0, amp, seed, fill, step) => {
    const r = rng(seed);
    let d = `M-10,${f(L.H)} L-10,${f(y0)}`;
    for (let x = -10; x <= L.W + step; x += step) { const h = amp * (0.4 + r()); d += ` Q${f(x + step / 2)},${f(y0 - h * 1.6)} ${f(x + step)},${f(y0 - h * 0.4)}`; }
    return `<path d="${d} L${L.W + 10},${f(L.H)} Z" fill="${fill}"/>`;
  };
  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#adSky)"/>
  ${stars(L, 30, 61, Y(-140), '#e8fff6')}
  <rect width="${L.W}" height="${L.H}" fill="url(#adSun)"/>
  <circle cx="${f(sun.x)}" cy="${f(sun.y)}" r="${f(S(30))}" fill="#fff0c2" opacity="0.95"/>
  ${ridge(L, horizon - S(10), S(30), 9, '#1c4a44', S(40))}
  ${canopy(horizon + S(4), S(10), 3, '#0e3a30', S(22))}
  <rect y="${f(horizon + S(8))}" width="${L.W}" height="${f(S(10))}" fill="#bfffe0" opacity="0.12" filter="url(#b4)"/>
  ${canopy(horizon + S(22), S(12), 5, 'url(#adJungle)', S(18))}`;

  // A stepped pyramid with a stair up the middle and a shrine on top
  let steps = '';
  const tiers = 6;
  for (let i = 0; i < tiers; i++) {
    const w = 120 - i * 16, y = -8 - i * 14;
    steps += `<rect x="${-w}" y="${y - 14}" width="${w * 2}" height="14" fill="url(#adStone)"/><path d="M${-w},${y - 14} H${w}" stroke="#9fe8c8" stroke-width="0.8" opacity="0.5"/>
      <path d="M${-w + 6},${y - 7} H${-24} M24,${y - 7} H${w - 6}" stroke="#1a2420" stroke-width="0.8" opacity="0.6"/>`;
  }
  let stair = '';
  for (let y = -8; y > -92; y -= 4) stair += `<path d="M-14,${y} H14" stroke="#1a2420" stroke-width="0.8"/>`;
  const vines = `<path d="M-100,-22 C-96,-30 -104,-38 -98,-46 M-70,-50 C-66,-58 -74,-64 -68,-72 M86,-22 C90,-32 84,-40 90,-48 M60,-52 C64,-58 58,-66 62,-74" stroke="#2f8a4a" stroke-width="2" fill="none" stroke-linecap="round"/>`;
  const leaf = (x, y, rot, s, c = '#0c3a2a') => `<path d="M0,0 C8,-10 24,-12 34,-4 C24,2 10,4 0,0 Z" fill="${c}" transform="translate(${x},${y}) rotate(${rot}) scale(${s})"/>`;
  const torch = x => `<path d="M${x},-92 L${x},-108" stroke="#2a1a10" stroke-width="2.4"/><path d="M${x - 3},-108 h6 l-1,-3 h-4 Z" fill="#3a2414"/>`;
  const fg = `
  <ellipse cx="0" cy="-6" rx="130" ry="8" fill="#000" opacity="0.4" filter="url(#b4)"/>
  ${steps}
  <rect x="-16" y="-92" width="32" height="84" fill="#2e403a"/>${stair}
  <path d="M-16,-92 V-8 M16,-92 V-8" stroke="#9fe8c8" stroke-width="0.8" opacity="0.5"/>
  <!-- shrine -->
  <rect x="-26" y="-122" width="52" height="30" fill="url(#adStone)"/>
  <path d="M-30,-122 H30 L24,-130 H-24 Z" fill="#2a3a34"/><path d="M-30,-122 H30" stroke="#9fe8c8" stroke-width="0.9" opacity="0.6"/>
  <path d="M-9,-92 V-108 A9,9 0 0 1 9,-108 V-92 Z" fill="#3dffb0"/>
  <path d="M-6,-92 V-106 A6,6 0 0 1 6,-106 V-92 Z" fill="#e6fff4"/>
  <path d="M-22,-114 h8 M14,-114 h8 M-22,-104 h6 M16,-104 h6" stroke="#1a2420" stroke-width="1.4"/>
  ${torch(-34)}${torch(34)}
  ${vines}
  ${leaf(-150, -4, -30, 1.4)}${leaf(-130, 2, -60, 1.2, '#0a3024')}${leaf(-160, -20, 10, 1.1, '#0e4030')}
  ${leaf(150, -4, 210, 1.4)}${leaf(132, 2, 240, 1.2, '#0a3024')}${leaf(160, -20, 170, 1.1, '#0e4030')}`;

  const flame = (x, ph) => ({ type: 'dots', layer: 'top', keep: true, pts: [[x, -112, 9, ph]], shape: 'glowdot', color: '#ffb347', a: 0.75, tw: [0.25, 0.23] });
  return {
    defs, bg, fg,
    parts: [],
    fx: [
      { type: 'rays', layer: 'scene', upper: true, x: 0, y: -118, n: 18, r0: 30, r1: 260, w: 3.5, spin: 4, color: '#fff0c2', a: 0.16 },
      { type: 'glow', layer: 'scene', x: 0, y: -118, r: 70, color: '#ffd36b', a: wave(0.08, 3.1, 0, 0.25) },
      { type: 'particles', layer: 'scene', shape: 'bird', n: 5, seed: 8, life: 8, x: -halfW - 20, w: 0, y: Math.max(skyTop * 0.75, -170), h: 50, vx: (halfW * 2 + 40) / 8, vy: -2, dv: [4, 3], sway: [3, 2], size: [6, 10], flapHz: 2.6, color: '#06201a', fade: [0.02, 0.02] },
      { type: 'glow', layer: 'top', x: 0, y: -100, r: 34, color: '#3dffb0', a: wave(0.2, 2.2, 0, 0.5) },
      flame(-34, 0), flame(34, 0.4),
      { type: 'glow', layer: 'top', x: -34, y: -112, r: 28, color: '#ff9a3d', a: wave(0.1, 0.17, 0, 0.35) },
      { type: 'glow', layer: 'top', x: 34, y: -112, r: 28, color: '#ff9a3d', a: wave(0.1, 0.19, 0.3, 0.35) },
      { type: 'particles', layer: 'top', shape: 'glowdot', n: 10, seed: 19, life: 4, x: -halfW, w: halfW * 2, y: -60, h: 60, vx: 3, vy: -3, dv: [8, 6], sway: [6, 2.4], size: [1.6, 2.8], color: ['#c8ff6a', '#3dffb0'], fade: [0.3, 0.3] },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// Fantasy: a sword in the stone under an aurora, runes glowing and magic drifting up.
function g_fantasy(L) {
  const { X, Y, S, halfW, skyTop } = helpers(L);
  const horizon = Y(-30);
  const top = Math.max(skyTop, -260);
  const defs = `
  ${skyGrad('faSky', horizon, [[0, '#05031a'], [0.5, '#1a0c46'], [1, '#4a2a8a']])}
  <linearGradient id="faAurora" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3dffd0" stop-opacity="0"/><stop offset="0.5" stop-color="#3dffd0" stop-opacity="0.55"/><stop offset="0.8" stop-color="#c38cff" stop-opacity="0.4"/><stop offset="1" stop-color="#c38cff" stop-opacity="0"/></linearGradient>
  <linearGradient id="faRock" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#2a2048"/><stop offset="0.45" stop-color="#4a3a78"/><stop offset="1" stop-color="#1a1230"/></linearGradient>
  <linearGradient id="faBlade" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#9aa6d8"/><stop offset="0.5" stop-color="#ffffff"/><stop offset="1" stop-color="#7a86b8"/></linearGradient>
  <linearGradient id="faCrystal" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#f0e2ff"/><stop offset="0.5" stop-color="#c38cff"/><stop offset="1" stop-color="#5a2aa8"/></linearGradient>`;

  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#faSky)"/>
  ${stars(L, 90, 71, horizon, '#efe6ff')}
  ${ridge(L, horizon, S(46), 13, '#1c1240', S(34))}
  ${ridge(L, horizon + S(10), S(20), 15, '#120a2c', S(22))}
  <rect y="${f(horizon + S(14))}" width="${L.W}" height="${L.H}" fill="#0c0820"/>`;

  // The aurora: soft ribbons across the sky, on their own layer so they can drift
  const ay = Math.max(top * 0.62, -150);
  const ribbon = (y, amp, w, o, ph) => {
    let d = `M${-halfW - 60},${y}`;
    for (let x = -halfW - 60; x <= halfW + 60; x += 30) d += ` Q${x + 15},${f(y - amp * Math.sin((x / 60) + ph))} ${x + 30},${y}`;
    return `<path d="${d}" stroke="url(#faAurora)" stroke-width="${w}" fill="none" opacity="${o}" filter="url(#b4)"/>`;
  };
  const aurora = `${ribbon(ay, 16, 34, 0.9, 0)}${ribbon(ay - 18, 12, 22, 0.6, 1.4)}${ribbon(ay + 14, 10, 16, 0.5, 2.6)}`;

  const crystal = (x, h, w, rot) => `<g transform="translate(${x},0) rotate(${rot})"><path d="M${-w},0 L${-w * 0.8},${-h * 0.75} L0,${-h} L${w * 0.8},${-h * 0.75} L${w},0 Z" fill="url(#faCrystal)"/><path d="M0,${-h} L0,0" stroke="#ffffff" stroke-width="0.6" opacity="0.5"/></g>`;
  const runes = [[-30, -34], [-14, -44], [6, -42], [22, -36], [-38, -18], [32, -20]];
  const runeGlyph = ([x, y], i) => `<path d="${['M-2,-3 L2,3 M2,-3 L-2,3', 'M0,-3 V3 M0,-1 L2,-3', 'M-2,3 L0,-3 L2,3', 'M-2,-3 H2 L-2,3 H2', 'M0,-3 V3 M-2,0 H2', 'M-2,-3 V3 L2,0'][i % 6]}" transform="translate(${x},${y})" stroke="#e6d2ff" stroke-width="1.1" fill="none" stroke-linecap="round"/>`;
  const fg = `
  <ellipse cx="0" cy="-2" rx="96" ry="7" fill="#000" opacity="0.5" filter="url(#b4)"/>
  ${crystal(-84, 30, 8, -14)}${crystal(-70, 20, 6, 8)}${crystal(78, 36, 9, 12)}${crystal(94, 22, 6, -10)}
  <!-- the stone -->
  <path d="M-58,0 C-62,-16 -54,-38 -36,-50 C-20,-58 18,-58 36,-48 C54,-38 62,-16 58,0 Z" fill="url(#faRock)"/>
  <path d="M-52,-28 C-40,-46 -16,-54 10,-52" stroke="#c38cff" stroke-width="1" fill="none" opacity="0.5"/>
  <path d="M-30,-8 L-20,-24 M24,-10 L34,-30" stroke="#120a24" stroke-width="1" opacity="0.7"/>
  ${runes.map(runeGlyph).join('')}
  <!-- the sword -->
  <path d="M-3.5,-52 L-3.5,-118 L0,-126 L3.5,-118 L3.5,-52 Z" fill="url(#faBlade)"/>
  <path d="M0,-124 L0,-54" stroke="#ffffff" stroke-width="0.7" opacity="0.8"/>
  <path d="M-22,-118 C-14,-116 -6,-118 0,-121 C6,-118 14,-116 22,-118 L20,-114 L-20,-114 Z" fill="#ffd36b"/>
  <circle cx="0" cy="-118" r="3.2" fill="#c38cff"/>
  <rect x="-2.6" y="-142" width="5.2" height="22" rx="1.5" fill="#5a2a6a"/>
  <path d="M-2.6,-138 h5.2 M-2.6,-134 h5.2 M-2.6,-130 h5.2 M-2.6,-126 h5.2" stroke="#2a103a" stroke-width="0.8"/>
  <circle cx="0" cy="-145" r="4.4" fill="#ffd36b"/>
  <path d="M-12,-118 H12" stroke="#fff3c2" stroke-width="0.8" opacity="0.8"/>`;

  const glint = sparkle(0, -126, 14, '#ffffff', 1) + `<circle cx="0" cy="-126" r="6" fill="#e6d2ff" opacity="0.7" filter="url(#b2)"/>`;
  return {
    defs, bg, fg,
    parts: [
      { name: 'aurora', group: 'scene', svg: aurora, pivot: [0, ay], m: { dx: wave(14, 9), a: wave(0.25, 4.4, 0, 0.85), sy: wave(0.12, 3.3, 0, 1) } },
      { name: 'glint', group: 'subject', hidden: true, svg: glint, pivot: [0, -126], m: { a: keys(3.2, [[0, 0], [1.6, 0], [1.85, 1], [2.3, 0]]), rot: keys(3.2, [[0, 0], [1.6, 0], [2.3, 90]]), s: keys(3.2, [[0, 0.4], [1.6, 0.4], [1.85, 1.1], [2.3, 0.4]]) } },
    ],
    fx: [
      { type: 'dots', layer: 'scene', pts: twinkles(L, 90, 71, horizon), color: '#efe6ff', a: 0.2, tw: [0.5, 2.4] },
      { type: 'glow', layer: 'top', x: 0, y: -90, r: 60, color: '#c38cff', a: wave(0.12, 2.6, 0, 0.32) },
      { type: 'dots', layer: 'top', keep: true, pts: runes.map(([x, y], i) => [x, y, 9, f(i / 6)]), shape: 'glowdot', color: '#c38cff', a: 0.35, tw: [0.45, 2] },
      { type: 'particles', layer: 'top', shape: 'glowdot', n: 14, seed: 23, life: 3.2, x: -50, w: 100, y: -40, h: 20, vy: -30, dv: [5, 8], sway: [8, 2.2], size: [1.6, 3.2], color: ['#e6d2ff', '#3dffd0', '#c38cff'], fade: [0.2, 0.45] },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// Romance: a Ferris wheel on the pier at sunset, two people at the rail, hearts drifting up.
function g_romance(L) {
  const { X, Y, S, halfW, skyTop } = helpers(L);
  const horizon = Y(-34);
  const sun = { x: X(-Math.min(halfW * 0.45, 70)), y: horizon };
  const hub = { x: Math.min(halfW * 0.28, 44), y: -96 }, R = 70;
  const defs = `
  ${skyGrad('roSky', horizon, [[0, '#1a0a3a'], [0.45, '#7a2a7a'], [0.8, '#ff7aa8'], [1, '#ffc49a']])}
  <linearGradient id="roSea" gradientUnits="userSpaceOnUse" x1="0" y1="${f(horizon)}" x2="0" y2="${L.H}"><stop offset="0" stop-color="#6a2a6a"/><stop offset="1" stop-color="#140820"/></linearGradient>
  <radialGradient id="roSun" gradientUnits="userSpaceOnUse" cx="${f(sun.x)}" cy="${f(sun.y)}" r="${f(S(160))}"><stop offset="0" stop-color="#fff0d6" stop-opacity="0.8"/><stop offset="0.25" stop-color="#ffb3c6" stop-opacity="0.4"/><stop offset="1" stop-color="#ff7aa8" stop-opacity="0"/></radialGradient>
  <linearGradient id="roSunDisc" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff6d6"/><stop offset="1" stop-color="#ff9a8a"/></linearGradient>`;

  let shimmer = '';
  const rs = rng(66);
  for (let i = 0; i < 16; i++) {
    const t = rs(), y = horizon + S(3) + t * t * (L.H - horizon) * 0.8, w = S(8 + rs() * 26) * (0.5 + t);
    shimmer += `<rect x="${f(sun.x - w / 2 + (rs() - 0.5) * S(30) * (0.4 + t))}" y="${f(y)}" width="${f(w)}" height="${f(S(1.2 + t))}" rx="${f(S(0.6))}" fill="#ffe0c2" opacity="${f(0.25 + rs() * 0.4)}"/>`;
  }
  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#roSky)"/>
  ${stars(L, 30, 17, horizon - S(100), '#ffe6f2')}
  <rect width="${L.W}" height="${L.H}" fill="url(#roSun)"/>
  <circle cx="${f(sun.x)}" cy="${f(sun.y)}" r="${f(S(30))}" fill="url(#roSunDisc)"/>
  ${[0.25, 0.45, 0.62].map((t, i) => `<rect x="${f(sun.x - S(40))}" y="${f(sun.y - S(30) * (1 - t * 2))}" width="${f(S(80))}" height="${f(S(1.5 + i))}" fill="#ff9ac0" opacity="0.9"/>`).join('')}
  <rect y="${f(horizon)}" width="${L.W}" height="${f(L.H - horizon)}" fill="url(#roSea)"/>
  ${shimmer}`;

  // The wheel turns as one piece; its round gondolas look the same at any angle
  const n = 10;
  let spokes = '', cabins = '', lights = '';
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2, x = Math.cos(a) * R, y = Math.sin(a) * R;
    spokes += `<line x1="0" y1="0" x2="${f(x)}" y2="${f(y)}"/>`;
    cabins += `<circle cx="${f(Math.cos(a) * (R + 8))}" cy="${f(Math.sin(a) * (R + 8))}" r="7.5" fill="${['#ff6fb5', '#7ff3ff', '#ffd36b'][i % 3]}"/><circle cx="${f(Math.cos(a) * (R + 8))}" cy="${f(Math.sin(a) * (R + 8))}" r="4" fill="#fff6e6" opacity="0.85"/>`;
  }
  for (let i = 0; i < 30; i++) { const a = (i / 30) * Math.PI * 2; lights += `<circle cx="${f(Math.cos(a) * R)}" cy="${f(Math.sin(a) * R)}" r="1.3"/>`; }
  const wheel = `<g transform="translate(${hub.x},${hub.y})">
    <g stroke="#ffd0e6" stroke-width="1" opacity="0.8">${spokes}</g>
    <circle r="${R}" fill="none" stroke="#ffe0f0" stroke-width="2.2"/><circle r="${R - 6}" fill="none" stroke="#ff9ac6" stroke-width="1"/>
    <g fill="#fff3c2" filter="url(#glow)">${lights}</g>
    ${cabins}
    <circle r="7" fill="#ffd0e6"/><circle r="3" fill="#7a2a5a"/></g>`;
  const fg = `
  <!-- pier -->
  <path d="M-${halfW + 10},-14 H${halfW + 10} V-6 H-${halfW + 10} Z" fill="#2a0c2a"/>
  <path d="M-${halfW + 10},-14 H${halfW + 10}" stroke="#ff9ac6" stroke-width="1" opacity="0.6"/>
  ${Array.from({ length: Math.ceil((halfW * 2 + 20) / 30) }, (_, i) => { const x = -halfW - 10 + i * 30; return `<path d="M${x},-6 V12" stroke="#1a0618" stroke-width="3"/>`; }).join('')}
  <path d="M-${halfW + 10},-26 H${halfW + 10} M-${halfW + 10},-20 H${halfW + 10}" stroke="#1a0618" stroke-width="1.2"/>
  ${Array.from({ length: Math.ceil((halfW * 2 + 20) / 14) }, (_, i) => `<path d="M${-halfW - 10 + i * 14},-26 V-14" stroke="#1a0618" stroke-width="1.2"/>`).join('')}
  <!-- the wheel's legs -->
  <path d="M${hub.x - 40},-14 L${hub.x},${hub.y} L${hub.x + 40},-14 M${hub.x - 30},-40 H${hub.x + 30}" stroke="#2a0c2a" stroke-width="4" fill="none"/>
  <path d="M${hub.x - 40},-14 L${hub.x},${hub.y} L${hub.x + 40},-14" stroke="#ff9ac6" stroke-width="0.8" fill="none" opacity="0.5"/>
  <!-- the two at the rail -->
  <g transform="translate(${-Math.min(halfW * 0.5, 80)},-14)" fill="#1a0618">
    <circle cx="-7" cy="-38" r="4.6"/><path d="M-12,-32 C-12,-34 -2,-34 -2,-32 L-1,-14 L-3,0 L-6,0 L-7,-12 L-8,0 L-11,0 L-13,-14 Z"/>
    <circle cx="7" cy="-36" r="4.2"/><path d="M2,-30 C2,-32 12,-32 12,-30 L15,-6 L12,-6 L11,0 L8,0 L7,-8 L6,0 L3,0 L2,-6 L0,-6 Z"/>
    <path d="M-2,-26 C0,-24 2,-24 3,-26" stroke="#1a0618" stroke-width="2.2" fill="none"/>
  </g>`;
  return {
    defs, bg, fg,
    parts: [{ name: 'wheel', group: 'subject', under: true, svg: wheel, pivot: [hub.x, hub.y], sym: 36, m: { rot: keys(36, [[0, 0, 1], [36, 360, 1]]) } }],
    fx: [
      { type: 'glow', layer: 'scene', x: f((sun.x - L.cx) / L.s), y: -34, r: 120, color: '#ffb3c6', a: wave(0.1, 4, 0, 0.3) },
      { type: 'dots', layer: 'scene', pts: Array.from({ length: 10 }, (_, i) => [f((sun.x - L.cx) / L.s + (i % 2 ? 1 : -1) * (6 + i * 5)), f(-30 + i * 5), 3 + i * 0.4, f(i * 0.37 % 1)]), shape: 'spark', color: '#fff0d6', a: 0.2, tw: [0.6, 1.4] },
      { type: 'particles', layer: 'top', shape: 'heart', n: 7, seed: 29, life: 5, x: -halfW * 0.9, w: halfW * 1.8, y: -30, h: 10, vy: -26, dv: [4, 6], sway: [8, 2.4], size: [7, 12], spin: 20, color: ['#ff6fb5', '#ff9ac6', '#ff3d7a'], alpha: [0.6, 0.95], fade: [0.15, 0.4] },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// Mystery: a foggy cobbled street at night, a magnifying glass drifting over footprints,
// a figure under the far lamp and a neon question mark that will not stay lit.
function g_mystery(L) {
  const { X, Y, S, halfW, skyTop } = helpers(L);
  const horizon = Y(-56);
  const defs = `
  ${skyGrad('myWall', horizon, [[0, '#04041a'], [0.6, '#0e0e3a'], [1, '#1c1c5a']])}
  <linearGradient id="myStreet" gradientUnits="userSpaceOnUse" x1="0" y1="${f(horizon)}" x2="0" y2="${L.H}"><stop offset="0" stop-color="#1a1a48"/><stop offset="1" stop-color="#06061a"/></linearGradient>
  <radialGradient id="myLamp" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#ffe6b0" stop-opacity="0.75"/><stop offset="0.4" stop-color="#ffd38a" stop-opacity="0.22"/><stop offset="1" stop-color="#ffd38a" stop-opacity="0"/></radialGradient>
  <radialGradient id="myLens" cx="0.35" cy="0.3" r="0.8"><stop offset="0" stop-color="#ffffff" stop-opacity="0.55"/><stop offset="0.5" stop-color="#9fb8ff" stop-opacity="0.22"/><stop offset="1" stop-color="#7b7bff" stop-opacity="0.35"/></radialGradient>`;

  // Townhouses along the back, a few windows lit
  const rh = rng(37);
  let houses = '', wins = '';
  for (let x = -6; x < L.W + 6;) {
    const w = S(36 + rh() * 30), h = S(50 + rh() * 50);
    houses += `<rect x="${f(x)}" y="${f(horizon - h)}" width="${f(w)}" height="${f(h + 2)}" fill="${rh() < 0.5 ? '#0c0c30' : '#10103a'}"/><path d="M${f(x)},${f(horizon - h)} h${f(w)}" stroke="#7b7bff" stroke-opacity="0.3" stroke-width="${f(S(0.8))}"/>`;
    for (let j = 0; j < 4; j++) if (rh() < 0.35) wins += `<rect x="${f(x + S(6) + rh() * (w - S(14)))}" y="${f(horizon - h + S(8) + rh() * (h - S(24)))}" width="${f(S(5))}" height="${f(S(7))}" fill="#ffd38a" opacity="${f(0.4 + rh() * 0.5)}"/>`;
    x += w + S(4);
  }
  const lamp = (x, y, sc) => `<circle cx="${f(X(x))}" cy="${f(Y(y))}" r="${f(S(60 * sc))}" fill="url(#myLamp)"/>
    <path d="M${f(X(x))},${f(Y(y + 4 * sc))} V${f(Y(y + 80 * sc))}" stroke="#05051a" stroke-width="${f(S(3 * sc))}"/>
    <path d="M${f(X(x - 4 * sc))},${f(Y(y - 5 * sc))} h${f(S(8 * sc))} l${f(S(2 * sc))},${f(S(9 * sc))} h${f(-S(12 * sc))} Z" fill="#ffe6b0"/>
    <path d="M${f(X(x - 5 * sc))},${f(Y(y - 5 * sc))} h${f(S(10 * sc))} l${f(-S(5 * sc))},${f(-S(5 * sc))} Z" fill="#05051a"/>`;
  const farX = Math.min(halfW * 0.55, 90);
  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#myWall)"/>
  ${stars(L, 30, 39, horizon - S(80), '#d6d6ff')}
  ${houses}${wins}
  <rect y="${f(horizon)}" width="${L.W}" height="${f(L.H - horizon)}" fill="url(#myStreet)"/>
  ${lamp(farX, -100, 0.55)}
  <g fill="#05051a" transform="translate(${f(X(farX - 10))},${f(Y(-56))}) scale(${f(L.s * 0.5)})">
    <path d="M-7,-58 h14 l-1,-3 h-12 Z M-11,-55 h22 v2 h-22 Z"/><circle cx="0" cy="-48" r="5"/>
    <path d="M-8,-42 C-12,-40 -13,-30 -12,-14 L-10,0 L-3,0 L0,-16 L3,0 L10,0 L12,-14 C13,-30 12,-40 8,-42 Z"/>
  </g>
  ${lamp(-Math.min(halfW * 0.75, 126), -134, 1)}`;

  // Cobbles and a trail of glowing footprints leading off
  const rc = rng(43);
  let cobbles = '';
  for (let j = 0; j < 5; j++) {
    const y = -46 + j * 12, w = 12 + j * 3;
    for (let x = -halfW - 20 + (j % 2) * w / 2; x < halfW + 20; x += w) cobbles += `<ellipse cx="${f(x)}" cy="${f(y)}" rx="${f(w * 0.42)}" ry="${f(3 + j * 0.8)}" fill="#14144a" opacity="${f(0.5 + rc() * 0.4)}"/>`;
  }
  const step = (x, y, rot, s) => `<g transform="translate(${x},${y}) rotate(${rot}) scale(${s})"><ellipse cx="0" cy="0" rx="3.2" ry="5.5" fill="#9f9fff"/><ellipse cx="0" cy="7.5" rx="2.6" ry="2.6" fill="#9f9fff"/></g>`;
  const prints = [[-96, -6, -70, 1.1], [-74, -16, -60, 1.05], [-50, -10, -70, 1], [-28, -22, -55, 0.95], [-4, -16, -65, 0.9], [18, -28, -55, 0.85], [40, -22, -62, 0.8], [60, -34, -55, 0.75]]
    .map(([x, y, r, sc], i) => step(x, y + (i % 2 ? -4 : 4), r + 90, sc)).join('');
  const fg = `${cobbles}<g opacity="0.85" filter="url(#glow)">${prints}</g>`;

  const glass = `<g transform="translate(-18,-70) rotate(-24)">
    <rect x="-4.5" y="30" width="9" height="46" rx="3.5" fill="#3a1a12"/><rect x="-4.5" y="30" width="9" height="46" rx="3.5" fill="none" stroke="#c88a5a" stroke-width="0.8" opacity="0.7"/>
    <rect x="-5.5" y="24" width="11" height="9" rx="2" fill="#d4a24a"/>
    <circle r="30" fill="url(#myLens)"/>
    <circle r="30" fill="none" stroke="#ffd36b" stroke-width="5"/><circle r="30" fill="none" stroke="#fff3c2" stroke-width="1" opacity="0.8"/>
    <path d="M-18,-12 A22,22 0 0 1 -4,-22" stroke="#ffffff" stroke-width="4" fill="none" stroke-linecap="round" opacity="0.75"/>
  </g>`;
  const qx = Math.min(halfW * 0.72, 118), qy = Math.max(skyTop * 0.62, -132);
  const qPath = 'M-8,-12 C-8,-22 8,-22 8,-12 C8,-4 0,-4 0,4 V7';
  const qmark = `<g transform="translate(${qx},${qy}) rotate(8)">
    <path d="${qPath}" stroke="#7b7bff" stroke-width="7" fill="none" stroke-linecap="round" opacity="0.6" filter="url(#b2)"/>
    <path d="${qPath}" stroke="#e6e6ff" stroke-width="2.6" fill="none" stroke-linecap="round"/>
    <circle cx="0" cy="15" r="3" fill="#e6e6ff"/><circle cx="0" cy="15" r="5" fill="#7b7bff" opacity="0.6" filter="url(#b2)"/></g>`;
  return {
    defs, bg, fg,
    parts: [
      { name: 'q', group: 'scene', svg: qmark, pivot: [qx, qy], m: { a: keys(3.6, [[0, 1, 1], [1.4, 1, 1], [1.45, 0.2, 1], [1.52, 0.9, 1], [1.6, 0.15, 1], [1.9, 0.15, 1], [1.95, 1, 1]]) } },
      { name: 'glass', group: 'subject', svg: glass, pivot: [-18, -70], m: { dx: wave(34, 7), dy: wave(9, 3.5, 0.25), rot: wave(5, 7, 0.25) } },
    ],
    fx: [
      { type: 'glow', layer: 'scene', x: qx, y: qy, r: 36, color: '#7b7bff', a: keys(3.6, [[0, 0.5, 1], [1.4, 0.5, 1], [1.45, 0, 1], [1.52, 0.45, 1], [1.6, 0, 1], [1.9, 0, 1], [1.95, 0.5, 1]]) },
      { type: 'particles', layer: 'scene', shape: 'puff', n: 6, seed: 47, life: 12, x: -halfW - 60, w: halfW * 2, y: -70, h: 50, vx: 9, size: [90, 140], color: '#8a8acc', alpha: [0.1, 0.16], fade: [0.35, 0.35] },
      { type: 'glow', layer: 'top', x: -18, y: -70, dx: wave(34, 7), dy: wave(9, 3.5, 0.25), r: 40, color: '#ffe6b0', a: 0.16 },
      { type: 'particles', layer: 'top', shape: 'puff', n: 4, seed: 53, life: 10, x: -halfW - 60, w: halfW * 2, y: -20, h: 30, vx: 12, size: [80, 120], color: '#8a8acc', alpha: [0.1, 0.18], fade: [0.35, 0.35] },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// Family: a cosy house at dusk, balloons on the gate, a kite up and smoke from the chimney.
function g_family(L) {
  const { X, Y, S, halfW, skyTop } = helpers(L);
  const horizon = Y(-30);
  const top = Math.max(skyTop, -260);
  const defs = `
  ${skyGrad('faDusk', horizon, [[0, '#0c1c4a'], [0.5, '#3a5aa8'], [0.85, '#ffaab8'], [1, '#ffd2a8']])}
  <linearGradient id="fmHill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2a6a6a"/><stop offset="1" stop-color="#0e2a30"/></linearGradient>
  <linearGradient id="fmWall" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f6e6d6"/><stop offset="1" stop-color="#b8a0a8"/></linearGradient>
  <radialGradient id="fmWin" cx="0.5" cy="0.6" r="0.7"><stop offset="0" stop-color="#fff6c2"/><stop offset="1" stop-color="#ffb35c"/></radialGradient>
  <radialGradient id="fmBalloon" cx="0.35" cy="0.3" r="0.75"><stop offset="0" stop-color="#ffffff" stop-opacity="0.9"/><stop offset="0.3" stop-color="#ffffff" stop-opacity="0"/></radialGradient>`;

  const cloud = (x, y, s) => `<g transform="translate(${f(X(x))},${f(Y(y))}) scale(${f(S(s))})" fill="#ffe0ea" opacity="0.55"><ellipse cx="0" cy="0" rx="22" ry="7"/><ellipse cx="-8" cy="-5" rx="10" ry="7"/><ellipse cx="6" cy="-7" rx="12" ry="9"/></g>`;
  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#faDusk)"/>
  ${stars(L, 30, 83, horizon - S(110), '#ffffff')}
  ${cloud(-halfW * 0.6, top * 0.55, 1.1)}${cloud(halfW * 0.65, top * 0.7, 0.9)}
  <path d="M-4,${f(horizon + S(6))} Q${f(L.W * 0.3)},${f(horizon - S(18))} ${f(L.W * 0.6)},${f(horizon)} T${L.W + 4},${f(horizon - S(8))} V${L.H} H-4 Z" fill="#3a7a7a" opacity="0.6"/>
  <rect y="${f(horizon + S(8))}" width="${L.W}" height="${L.H}" fill="#123a3a"/>`;

  const house = `
  <path d="M-150,6 C-90,-20 -40,-30 0,-30 C60,-30 110,-18 160,6 Z" fill="url(#fmHill)"/>
  <ellipse cx="0" cy="-30" rx="80" ry="6" fill="#000" opacity="0.25" filter="url(#b2)"/>
  <!-- tree -->
  <path d="M-84,-30 V-62" stroke="#3a2420" stroke-width="5"/>
  <circle cx="-84" cy="-78" r="20" fill="#2a8a6a"/><circle cx="-96" cy="-66" r="13" fill="#257a5e"/><circle cx="-72" cy="-66" r="14" fill="#2f9a74"/>
  <!-- house -->
  <rect x="-44" y="-84" width="88" height="56" fill="url(#fmWall)"/>
  <path d="M-54,-84 L0,-122 L54,-84 Z" fill="#c44a6a"/><path d="M-54,-84 L0,-122 L54,-84" stroke="#ff9ac0" stroke-width="1.2" fill="none"/>
  <rect x="22" y="-118" width="10" height="20" fill="#8a3a4a"/>
  <rect x="-34" y="-72" width="18" height="16" rx="1" fill="url(#fmWin)"/><rect x="16" y="-72" width="18" height="16" rx="1" fill="url(#fmWin)"/>
  <path d="M-25,-72 v16 M-34,-64 h18 M25,-72 v16 M16,-64 h18" stroke="#8a5a4a" stroke-width="1.2"/>
  <circle cx="0" cy="-100" r="6" fill="url(#fmWin)"/>
  <path d="M-7,-28 V-50 a7,7 0 0 1 14,0 V-28 Z" fill="#6a3a5a"/><circle cx="4" cy="-38" r="1" fill="#ffd36b"/>
  <!-- fence and gate -->
  <g fill="#f6e6f0">${Array.from({ length: 9 }, (_, i) => { const x = 54 + i * 9; return `<path d="M${x},-14 V-30 l2.5,-3 l2.5,3 V-14 Z"/>`; }).join('')}
  ${Array.from({ length: 9 }, (_, i) => { const x = -134 + i * 9; return `<path d="M${x},-14 V-30 l2.5,-3 l2.5,3 V-14 Z"/>`; }).join('')}</g>
  <path d="M54,-26 H136 M54,-18 H136 M-134,-26 H-54 M-134,-18 H-54" stroke="#f6e6f0" stroke-width="1.6"/>`;

  // Balloons tied to the fence post, swaying round the knot
  const knot = [58, -30];
  const balloon = (x, y, r, c) => `<path d="M${knot[0]},${knot[1]} C${f((knot[0] + x) / 2 - 4)},${f((knot[1] + y) / 2)} ${f(x + 3)},${f(y + r + 10)} ${x},${y + r}" stroke="#ffffff" stroke-width="0.6" fill="none" opacity="0.8"/>
    <ellipse cx="${x}" cy="${y}" rx="${r * 0.86}" ry="${r}" fill="${c}"/><ellipse cx="${x}" cy="${y}" rx="${r * 0.86}" ry="${r}" fill="url(#fmBalloon)"/>
    <path d="M${x - 2},${y + r + 2} l2,-2.4 l2,2.4 Z" fill="${c}"/>`;
  const balloons = balloon(48, -104, 11, '#ff5a8a') + balloon(68, -112, 12, '#3ec8ff') + balloon(84, -96, 10, '#ffd36b') + balloon(60, -88, 9, '#86ff6a');
  // The kite, high up on a long string down to the garden
  const kx = -Math.min(halfW * 0.5, 80), ky = Math.max(top * 0.68, -160), anchor = [-60, -30];
  const kite = `<path d="M${anchor[0]},${anchor[1]} Q${f((anchor[0] + kx) / 2 + 20)},${f((anchor[1] + ky) / 2 + 10)} ${kx},${ky + 16}" stroke="#ffffff" stroke-width="0.6" fill="none" opacity="0.7"/>
    <g transform="translate(${kx},${ky}) rotate(-14)">
      <path d="M0,-16 L11,0 L0,18 L-11,0 Z" fill="#ff5a8a"/><path d="M0,-16 L11,0 L0,0 Z" fill="#ffd36b"/><path d="M0,0 L-11,0 L0,18 Z" fill="#3ec8ff"/>
      <path d="M0,-16 V18 M-11,0 H11" stroke="#ffffff" stroke-width="0.6"/>
      <path d="M0,18 C4,24 -4,28 2,34 C6,38 -2,42 2,46" stroke="#ffffff" stroke-width="0.7" fill="none"/>
      <path d="M2,26 l3,-1 l-1,3 Z M1,36 l-3,-1 l1,3 Z" fill="#ff9ac0"/></g>`;
  return {
    defs, bg, fg: house,
    parts: [
      { name: 'kite', group: 'subject', under: true, svg: kite, pivot: anchor, m: { rot: wave(3.5, 4.2), s: wave(0.02, 2.9, 0, 1) } },
      { name: 'balloons', group: 'subject', svg: balloons, pivot: knot, m: { rot: wave(5, 3.1), dy: wave(-1.5, 1.7) } },
    ],
    fx: [
      { type: 'particles', layer: 'top', shape: 'puff', n: 6, seed: 61, life: 3.6, x: 25, w: 4, y: -122, h: 2, vx: 6, vy: -16, sway: [3, 2], size: [10, 22], color: '#ffffff', alpha: [0.25, 0.4], fade: [0.15, 0.6] },
      { type: 'dots', layer: 'top', keep: true, pts: [[-25, -64, 16, 0], [25, -64, 16, 0.5], [0, -100, 10, 0.2]], color: '#ffd38a', a: 0.3, tw: [0.12, 2.4] },
      { type: 'particles', layer: 'top', shape: 'glowdot', n: 8, seed: 67, life: 4, x: -halfW, w: halfW * 2, y: -40, h: 40, vx: 2, vy: -4, dv: [6, 5], sway: [6, 2.4], size: [1.6, 2.8], color: ['#fff3a0', '#c8ff6a'], fade: [0.3, 0.3] },
      { type: 'dots', layer: 'scene', pts: twinkles(L, 30, 83, horizon - S(110)), color: '#ffffff', a: 0.2, tw: [0.5, 2.4] },
    ],
  };
}

// ---------------------------------------------------------------------------------------
// Documentary: an old film camera on its tripod filming the savanna at sunset, reels turning.
function g_documentary(L) {
  const { X, Y, S, halfW, skyTop } = helpers(L);
  const horizon = Y(-30);
  const sun = { x: X(Math.min(halfW * 0.42, 70)), y: horizon - S(8) };
  const defs = `
  ${skyGrad('doSky', horizon, [[0, '#120a26'], [0.45, '#5a2a5a'], [0.8, '#e0703a'], [1, '#ffc46b']])}
  <radialGradient id="doSun" gradientUnits="userSpaceOnUse" cx="${f(sun.x)}" cy="${f(sun.y)}" r="${f(S(150))}"><stop offset="0" stop-color="#fff2c2" stop-opacity="0.85"/><stop offset="0.2" stop-color="#ffb35c" stop-opacity="0.45"/><stop offset="1" stop-color="#ff7a3d" stop-opacity="0"/></radialGradient>
  <linearGradient id="doGround" gradientUnits="userSpaceOnUse" x1="0" y1="${f(horizon)}" x2="0" y2="${L.H}"><stop offset="0" stop-color="#3a1a1a"/><stop offset="1" stop-color="#0c0608"/></linearGradient>
  <linearGradient id="doBody" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3a3a4a"/><stop offset="1" stop-color="#14141c"/></linearGradient>
  <radialGradient id="doLens" cx="0.4" cy="0.35" r="0.7"><stop offset="0" stop-color="#9fd8ff"/><stop offset="0.5" stop-color="#2a4a7a"/><stop offset="1" stop-color="#0a0a14"/></radialGradient>`;

  const s0 = S(1);
  const acacia = (x, y, sc) => `<g transform="translate(${f(x)},${f(y)}) scale(${f(sc * s0)})" fill="#1a0a10">
    <path d="M-2,0 L-1,-26 L-10,-36 M1,-26 L10,-38" stroke="#1a0a10" stroke-width="2.4" fill="none"/>
    <ellipse cx="0" cy="-40" rx="34" ry="7"/><ellipse cx="-14" cy="-44" rx="16" ry="5"/><ellipse cx="14" cy="-45" rx="18" ry="5"/></g>`;
  const giraffe = (x, y, sc) => `<g transform="translate(${f(x)},${f(y)}) scale(${f(sc * s0)})" fill="#1a0a10">
    <path d="M-10,0 L-9,-14 L-12,-16 C-12,-22 8,-22 10,-18 L12,-34 L13,-44 L18,-46 L20,-43 L16,-42 L15,-30 L12,-14 L11,0 L9,0 L9,-12 L6,-12 L5,0 L3,0 L3,-12 L-6,-12 L-6,0 Z"/></g>`;
  const elephant = (x, y, sc) => `<g transform="translate(${f(x)},${f(y)}) scale(${f(sc * s0)})" fill="#1a0a10">
    <path d="M-16,0 V-10 C-18,-20 -10,-26 0,-26 C10,-26 14,-20 16,-14 C20,-12 22,-6 20,2 L18,2 C19,-4 18,-8 16,-8 L14,0 L10,0 L10,-8 L-4,-8 L-4,0 L-8,0 L-8,-8 L-12,-8 L-12,0 Z"/></g>`;
  const bg = `
  <rect width="${L.W}" height="${L.H}" fill="url(#doSky)"/>
  ${stars(L, 20, 97, horizon - S(110), '#ffe6d6')}
  <rect width="${L.W}" height="${L.H}" fill="url(#doSun)"/>
  <circle cx="${f(sun.x)}" cy="${f(sun.y)}" r="${f(S(34))}" fill="#ffe6a8"/>
  <rect y="${f(horizon)}" width="${L.W}" height="${f(L.H - horizon)}" fill="url(#doGround)"/>
  <path d="M-4,${f(horizon + S(2))} L${L.W + 4},${f(horizon + S(2))}" stroke="#ffb35c" stroke-width="${f(S(1))}" opacity="0.5"/>
  ${acacia(X(Math.min(halfW * 0.82, 140)), horizon + S(1), 1.2)}
  ${giraffe(sun.x - S(20), horizon + S(1), 1)}${giraffe(sun.x + S(8), horizon + S(1), 0.8)}
  ${elephant(X(Math.min(halfW * 0.6, 100)) , horizon + S(1), 0.9)}`;

  // The camera on its tripod, looking toward the sun
  const cam = [-40, -78];
  const fg = `
  <ellipse cx="${cam[0]}" cy="0" rx="40" ry="4" fill="#000" opacity="0.6" filter="url(#b2)"/>
  <path d="M${cam[0]},-58 L${cam[0] - 30},0 M${cam[0]},-58 L${cam[0] + 26},0 M${cam[0]},-58 L${cam[0] - 4},2" stroke="#2a1a14" stroke-width="3" stroke-linecap="round"/>
  <path d="M${cam[0]},-58 L${cam[0] - 30},0 M${cam[0]},-58 L${cam[0] + 26},0" stroke="#c88a5a" stroke-width="0.8" opacity="0.5"/>
  <rect x="${cam[0] - 8}" y="-62" width="16" height="6" rx="1.5" fill="#2a2a34"/>
  <rect x="${cam[0] - 28}" y="${cam[1] - 4}" width="52" height="26" rx="3" fill="url(#doBody)"/>
  <rect x="${cam[0] - 28}" y="${cam[1] - 4}" width="52" height="26" rx="3" fill="none" stroke="#e8f6ff" stroke-width="0.8" opacity="0.5"/>
  <path d="M${cam[0] + 24},${cam[1] + 2} L${cam[0] + 36},${cam[1] - 2} L${cam[0] + 36},${cam[1] + 20} L${cam[0] + 24},${cam[1] + 16} Z" fill="#20202a"/>
  <rect x="${cam[0] + 36}" y="${cam[1] - 4}" width="8" height="28" rx="2" fill="#2a2a34"/>
  <ellipse cx="${cam[0] + 44}" cy="${cam[1] + 10}" rx="3" ry="11" fill="url(#doLens)"/>
  <path d="M${cam[0] + 46},${cam[1] + 10} L${f(X(Math.min(halfW * 0.42, 70)) - L.cx)},${cam[1] + 6}" stroke="#fff2c2" stroke-width="0.6" opacity="0"/>
  <rect x="${cam[0] - 36}" y="${cam[1] + 4}" width="8" height="12" rx="1.5" fill="#2a2a34"/>
  <circle cx="${cam[0] + 16}" cy="${cam[1] + 3}" r="2.2" fill="#3a0a10"/>
  <path d="M${cam[0] - 18},${cam[1] + 14} h20" stroke="#e8f6ff" stroke-width="0.8" opacity="0.4"/>`;

  // Two reels on top; six holes, so they look the same every sixth of a turn
  const reel = (cx, cy, r) => {
    let holes = '';
    for (let i = 0; i < 6; i++) { const a = i * Math.PI / 3; holes += `<circle cx="${f(cx + Math.cos(a) * r * 0.55)}" cy="${f(cy + Math.sin(a) * r * 0.55)}" r="${f(r * 0.2)}" fill="#14141c"/>`; }
    return `<circle cx="${cx}" cy="${cy}" r="${r}" fill="#4a4a5a"/><circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="#e8f6ff" stroke-width="0.8" opacity="0.6"/>${holes}<circle cx="${cx}" cy="${cy}" r="${f(r * 0.16)}" fill="#c8c8d8"/>`;
  };
  const r1 = [cam[0] - 14, cam[1] - 20], r2 = [cam[0] + 10, cam[1] - 18];
  // The viewfinder marks round the frame, and the red recording light in its corner
  const m = 10, inset = 10, xl = X(-halfW) + inset, xr = X(halfW) - inset, yt = inset, yb = L.H - inset - S(26);
  const brackets = `<g stroke="#ffffff" stroke-width="${f(S(1.4))}" fill="none" opacity="0.75">
    <path d="M${f(xl)},${f(yt + S(m))} V${f(yt)} H${f(xl + S(m))} M${f(xr - S(m))},${f(yt)} H${f(xr)} V${f(yt + S(m))} M${f(xr)},${f(yb - S(m))} V${f(yb)} H${f(xr - S(m))} M${f(xl + S(m))},${f(yb)} H${f(xl)} V${f(yb - S(m))}"/></g>`;
  const rec = [(xl - L.cx) / L.s + 12, (yt - L.gy) / L.s + 12];
  return {
    defs, bg, fg, fgGlobal: brackets,
    parts: [
      { name: 'reel1', group: 'subject', svg: reel(r1[0], r1[1], 13), pivot: r1, sym: 60, m: { rot: keys(3, [[0, 0, 1], [3, 360, 1]]) } },
      { name: 'reel2', group: 'subject', svg: reel(r2[0], r2[1], 11), pivot: r2, sym: 60, m: { rot: keys(2.5, [[0, 0, 1], [2.5, 360, 1]]) } },
    ],
    fx: [
      { type: 'glow', layer: 'scene', x: f((sun.x - L.cx) / L.s), y: f((sun.y - L.gy) / L.s), r: 90, color: '#ffd38a', a: wave(0.1, 3.5, 0, 0.35) },
      { type: 'particles', layer: 'scene', shape: 'bird', n: 4, seed: 71, life: 9, x: halfW + 20, w: 0, y: Math.max(skyTop * 0.72, -160), h: 50, vx: -(halfW * 2 + 40) / 9, vy: -1, dv: [3, 2], sway: [3, 2.2], size: [6, 9], flapHz: 2.4, color: '#1a0a10', fade: [0.02, 0.02] },
      { type: 'particles', layer: 'top', shape: 'glowdot', n: 10, seed: 73, life: 5, x: -halfW, w: halfW * 2, y: -60, h: 50, vx: 4, vy: -2, dv: [3, 3], sway: [4, 3], size: [1.2, 2.2], color: '#ffe6b0', alpha: [0.3, 0.7], fade: [0.3, 0.3] },
      { type: 'dots', layer: 'top', keep: true, pts: [[f(rec[0]), f(rec[1]), 9, 0]], shape: 'glowdot', color: '#ff2a3d', a: keys(1.2, [[0, 1, 1], [0.6, 1, 1], [0.62, 0.15, 1], [1.18, 0.15, 1]]) },
      { type: 'glow', layer: 'top', x: cam[0] + 16, y: cam[1] + 3, r: 6, color: '#ff2a3d', a: keys(1.2, [[0, 1, 1], [0.6, 1, 1], [0.62, 0, 1], [1.18, 0, 1]]) },
    ],
  };
}

module.exports = { g_horror, g_thriller, g_scifi, g_action, g_comedy, g_drama, g_animation, g_crime, g_adventure, g_fantasy, g_romance, g_mystery, g_family, g_documentary };
