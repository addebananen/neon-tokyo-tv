// Plays a scene's motion on a canvas the way the app does (SceneMotion.kt), for previews.
// Kept in step with the app by hand: the same channels, parts and effects, the same maths.
(function (root) {
  const TAU = Math.PI * 2;

  function key(k, t) {
    const v = k.v, p = k.p;
    let tt = ((t % p) + p) % p;
    let i = v.length - 1;
    for (let j = 0; j < v.length; j++) if (v[j][0] <= tt) i = j;
    const a = v[i], b = i + 1 < v.length ? v[i + 1] : [v[0][0] + p, v[0][1]];
    if (tt < a[0]) tt += p;
    let u = b[0] === a[0] ? 0 : (tt - a[0]) / (b[0] - a[0]);
    // How the segment from a key eases: 0 in and out, 1 straight, 2 speeding up (a fall), 3 slowing (a rise)
    const ease = a[2] !== undefined ? a[2] : (k.lin ? 1 : 0);
    if (ease === 0) u = u * u * (3 - 2 * u);
    else if (ease === 2) u = u * u;
    else if (ease === 3) u = 1 - (1 - u) * (1 - u);
    return a[1] + (b[1] - a[1]) * u;
  }

  // A channel is a number, or { b: base, k: keyframes { p, v: [[time, value]], lin }, w: [[amp, period, phase]] }
  function ch(c, t, d) {
    if (c === undefined || c === null) return d;
    if (typeof c === 'number') return c;
    let v = c.k ? key(c.k, t) : (c.b !== undefined ? c.b : d);
    if (c.w) for (const [amp, per, ph] of c.w) v += amp * Math.sin(TAU * (t / per + (ph || 0)));
    return v;
  }

  function hash(seed, i, k, j) {
    const x = Math.sin(i * 127.1 + k * 311.7 + j * 74.7 + seed * 17.3) * 43758.5453;
    return x - Math.floor(x);
  }

  function rgba(hex, a) {
    const n = parseInt(hex.slice(1), 16);
    return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${Math.max(0, Math.min(1, a))})`;
  }

  function glowAt(ctx, x, y, r, color, a, sx) {
    if (a <= 0.003 || r <= 0) return;
    ctx.save();
    ctx.translate(x, y);
    ctx.scale(sx || 1, 1);
    const g = ctx.createRadialGradient(0, 0, 0, 0, 0, r);
    g.addColorStop(0, rgba(color, a));
    g.addColorStop(0.35, rgba(color, a * 0.45));
    g.addColorStop(1, rgba(color, 0));
    ctx.fillStyle = g;
    ctx.beginPath(); ctx.arc(0, 0, r, 0, TAU); ctx.fill();
    ctx.restore();
  }

  // Unit shapes, drawn round the origin at size 1
  const SHAPES = {
    heart: 'M0,0.35 C-0.1,0.25 -0.5,0 -0.5,-0.2 C-0.5,-0.45 -0.2,-0.5 0,-0.28 C0.2,-0.5 0.5,-0.45 0.5,-0.2 C0.5,0 0.1,0.25 0,0.35 Z',
    petal: 'M0,-0.5 C0.33,-0.5 0.5,-0.17 0.42,0.17 C0.33,0.42 0.08,0.5 0,0.33 C-0.08,0.5 -0.33,0.42 -0.42,0.17 C-0.5,-0.17 -0.33,-0.5 0,-0.5 Z',
    spark: 'M0,-0.5 Q0.08,-0.08 0.5,0 Q0.08,0.08 0,0.5 Q-0.08,0.08 -0.5,0 Q-0.08,-0.08 0,-0.5 Z',
    leaf: 'M0,-0.5 C0.3,-0.3 0.3,0.3 0,0.5 C-0.3,0.3 -0.3,-0.3 0,-0.5 Z',
  };
  const pathCache = {};
  const unitPath = n => pathCache[n] || (pathCache[n] = new Path2D(SHAPES[n]));

  function drawShape(ctx, shape, x, y, size, rot, color, a, ex) {
    if (a <= 0.003) return;
    ctx.save();
    ctx.translate(x, y);
    switch (shape) {
      case 'dot':
        ctx.fillStyle = rgba(color, a); ctx.beginPath(); ctx.arc(0, 0, size / 2, 0, TAU); ctx.fill(); break;
      case 'glowdot':
        glowAt(ctx, 0, 0, size * 2.2, color, a * 0.55);
        ctx.fillStyle = rgba('#ffffff', a * 0.9); ctx.beginPath(); ctx.arc(0, 0, size * 0.32, 0, TAU); ctx.fill(); break;
      case 'puff':
        glowAt(ctx, 0, 0, size / 2, color, a); break;
      case 'ring':
        ctx.strokeStyle = rgba(color, a); ctx.lineWidth = Math.max(1, size * 0.12); ctx.beginPath(); ctx.arc(0, 0, size / 2, 0, TAU); ctx.stroke(); break;
      case 'streak': {
        // A tapered trail behind the particle, fading toward its tail
        ctx.rotate(rot);
        const len = ex.len;
        const g = ctx.createLinearGradient(-len, 0, 0, 0);
        g.addColorStop(0, rgba(color, 0)); g.addColorStop(1, rgba(color, a));
        ctx.fillStyle = g;
        ctx.beginPath(); ctx.moveTo(-len, 0); ctx.lineTo(0, -size / 2); ctx.lineTo(0, size / 2); ctx.closePath(); ctx.fill();
        ctx.fillStyle = rgba(color, a); ctx.beginPath(); ctx.arc(0, 0, size / 2, 0, TAU); ctx.fill(); break;
      }
      case 'rect':
        ctx.rotate(rot); ctx.scale(1, ex.flip);
        ctx.fillStyle = rgba(color, a); ctx.fillRect(-size / 2, -size / 4, size, size / 2); break;
      case 'bird': {
        const f = ex.flap;
        ctx.strokeStyle = rgba(color, a); ctx.lineWidth = Math.max(1, size * 0.14); ctx.lineCap = 'round'; ctx.lineJoin = 'round';
        ctx.beginPath(); ctx.moveTo(-size / 2, -f * size * 0.3);
        ctx.quadraticCurveTo(-size / 4, -size * 0.18 - f * size * 0.1, 0, 0);
        ctx.quadraticCurveTo(size / 4, -size * 0.18 - f * size * 0.1, size / 2, -f * size * 0.3); ctx.stroke(); break;
      }
      case 'bat': {
        const f = ex.flap, s = size / 2;
        ctx.fillStyle = rgba(color, a); ctx.beginPath();
        ctx.moveTo(0, -s * 0.15);
        ctx.quadraticCurveTo(-s * 0.4, -s * 0.2 - f * s * 0.5, -s, -f * s * 0.6);
        ctx.quadraticCurveTo(-s * 0.7, s * 0.1, -s * 0.45, s * 0.05);
        ctx.quadraticCurveTo(-s * 0.3, s * 0.3, 0, s * 0.2);
        ctx.quadraticCurveTo(s * 0.3, s * 0.3, s * 0.45, s * 0.05);
        ctx.quadraticCurveTo(s * 0.7, s * 0.1, s, -f * s * 0.6);
        ctx.quadraticCurveTo(s * 0.4, -s * 0.2 - f * s * 0.5, 0, -s * 0.15);
        ctx.fill(); break;
      }
      default:
        ctx.rotate(rot); ctx.scale(size, size);
        ctx.fillStyle = rgba(color, a); ctx.fill(unitPath(shape));
    }
    ctx.restore();
  }

  // Seven segments: a b c d e f g, each as x, y, w, h in a 1 x 2 cell
  const SEG = [[0.1, 0, 0.8, 0.14], [0.86, 0.1, 0.14, 0.82], [0.86, 1.08, 0.14, 0.82], [0.1, 1.86, 0.8, 0.14], [0, 1.08, 0.14, 0.82], [0, 0.1, 0.14, 0.82], [0.1, 0.93, 0.8, 0.14]];
  const DIGIT = ['abcdef', 'bc', 'abdeg', 'abcdg', 'bcfg', 'acdfg', 'acdefg', 'abc', 'abcdefg', 'abcdfg'];

  function fx(ctx, f, t, e, M) {
    // An effect that is part of the picture (keep) shows at rest too; the rest fade in with focus
    const ek = f.keep ? 1 : e;
    const a0 = ch(f.a, t, 1) * ek;
    if (a0 <= 0.003) return;
    // Effects that ride on a moving part move with it, by as much as the part does
    const ox = M.S(ch(f.dx, t, 0) * e), oy = M.S(ch(f.dy, t, 0) * e);
    const X = v => M.X(v) + ox, Y = v => M.Y(v) + oy, S = M.S;
    switch (f.type) {
      case 'flash':
        ctx.fillStyle = rgba(f.color, a0); ctx.fillRect(0, 0, M.w, M.h); break;
      case 'glow':
        glowAt(ctx, X(ch(f.x, t, 0)), Y(ch(f.y, t, 0)), S(ch(f.r, t, 10)), f.color, a0, f.sx); break;
      case 'ring': {
        const r = S(ch(f.r, t, 10));
        if (r <= 0) break;
        ctx.strokeStyle = rgba(f.color, a0); ctx.lineWidth = S(f.lw || 1.5);
        ctx.beginPath(); ctx.arc(X(f.x), Y(f.y), r, 0, TAU); ctx.stroke(); break;
      }
      case 'rays': {
        const cx = X(f.x), cy = Y(f.y), r0 = S(f.r0 || 0), r1 = S(f.r1), w = f.w * Math.PI / 180;
        const g = ctx.createRadialGradient(cx, cy, r0, cx, cy, r1);
        g.addColorStop(0, rgba(f.color, a0)); g.addColorStop(1, rgba(f.color, 0));
        ctx.fillStyle = g;
        const base = (f.rot0 || 0) * Math.PI / 180 + (f.spin || 0) * Math.PI / 180 * t;
        for (let i = 0; i < f.n; i++) {
          const ang = base + TAU * i / f.n;
          // upper: rays fade out as they turn toward the ground
          ctx.globalAlpha = f.upper ? Math.max(0, -Math.sin(ang)) : 1;
          if (ctx.globalAlpha <= 0.01) continue;
          ctx.beginPath();
          ctx.moveTo(cx + Math.cos(ang) * r0, cy + Math.sin(ang) * r0);
          ctx.lineTo(cx + Math.cos(ang - w) * r1, cy + Math.sin(ang - w) * r1);
          ctx.lineTo(cx + Math.cos(ang + w) * r1, cy + Math.sin(ang + w) * r1);
          ctx.closePath();
          ctx.fill();
        }
        ctx.globalAlpha = 1; break;
      }
      case 'dots': {
        const [amp, per] = f.tw || [0, 1];
        for (const [x, y, r, ph] of f.pts) {
          const a = Math.max(0, Math.min(1, ch(f.a, t, 1) + amp * e * Math.sin(TAU * (t / per + (ph || 0))))) * ek;
          drawShape(ctx, f.shape || 'glowdot', X(x), Y(y), S(r), 0, f.color, a, {});
        }
        break;
      }
      case 'digits': {
        const per = f.from + (f.hold || 1);
        const tt = ((t % per) + per) % per;
        const left = Math.max(0, f.from - Math.floor(tt));
        const text = `${String(Math.floor(left / 60)).padStart(2, '0')}:${String(left % 60).padStart(2, '0')}`;
        const h = S(f.h), w = h * 0.5, gap = h * 0.16;
        let x = X(f.x) - (4 * w + 3 * gap + w * 0.4) / 2;
        const y = Y(f.y) - h / 2;
        const blink = left === 0 ? (Math.floor(tt * 4) % 2 ? 0.25 : 1) : 1;
        for (const c of text) {
          if (c === ':') {
            const on = tt % 1 < 0.5 ? 1 : 0.2;
            ctx.fillStyle = rgba(f.color, a0 * on * blink);
            ctx.fillRect(x + w * 0.1, y + h * 0.28, w * 0.2, w * 0.2);
            ctx.fillRect(x + w * 0.1, y + h * 0.62, w * 0.2, w * 0.2);
            x += w * 0.4 + gap; continue;
          }
          const lit = DIGIT[+c];
          SEG.forEach((s, i) => {
            const on = lit.includes('abcdefg'[i]);
            const sx = x + s[0] * w, sy = y + s[1] * h / 2, sw = s[2] * w, sh = s[3] * h / 2;
            if (on) { ctx.fillStyle = rgba(f.color, a0 * 0.35 * blink); ctx.fillRect(sx - sw * 0.25, sy - sh * 0.25, sw * 1.5, sh * 1.5); }
            ctx.fillStyle = rgba(f.color, a0 * (on ? blink : 0.1));
            ctx.fillRect(sx, sy, sw, sh);
          });
          x += w + gap;
        }
        break;
      }
      case 'particles': {
        const life = f.life, n = f.n, seed = f.seed || 1;
        const colors = Array.isArray(f.color) ? f.color : [f.color];
        for (let i = 0; i < n; i++) {
          const off = f.sync ? (f.off || 0) : hash(seed, i, 0, 0) * life;
          const tt = t + off, k = Math.floor(tt / life), age = tt - k * life, u = age / life;
          const hk = j => hash(seed, f.sync ? 0 : i, k, j), hi = j => hash(seed, i, k, j);
          let x, y, rot = 0, len = 0;
          const sp = f.speed ? f.speed[0] + (f.speed[1] - f.speed[0]) * hi(4) : 0;
          if (f.from) {
            const arc = f.arc || [0, 360];
            const ang = (f.sync ? (arc[0] + (arc[1] - arc[0]) * (i + 0.5) / n + (hi(3) - 0.5) * 8) : arc[0] + (arc[1] - arc[0]) * hi(3)) * Math.PI / 180;
            const cx = f.from[0] + (f.w ? (hk(1) - 0.5) * f.w : 0), cy = f.from[1] + (f.h ? (hk(2) - 0.5) * f.h : 0);
            const r0 = f.r0 ? f.r0[0] + (f.r0[1] - f.r0[0]) * hi(5) : 0;
            const d = r0 + sp * age + 0.5 * (f.acc || 0) * age * age;
            x = cx + Math.cos(ang) * d; y = cy + Math.sin(ang) * d + 0.5 * (f.g || 0) * age * age;
            const vx = Math.cos(ang) * (sp + (f.acc || 0) * age), vy = Math.sin(ang) * (sp + (f.acc || 0) * age) + (f.g || 0) * age;
            rot = Math.atan2(vy, vx); len = Math.min(f.len || 0, (f.len || 0) * Math.hypot(vx, vy) / Math.max(1, sp || 1));
          } else {
            const vx = (f.vx || 0) + ((f.dv ? f.dv[0] : 0) * (hi(5) - 0.5) * 2), vy = (f.vy || 0) + ((f.dv ? f.dv[1] : 0) * (hi(6) - 0.5) * 2);
            x = f.x + hi(1) * (f.w || 0) + vx * age; y = f.y + hi(2) * (f.h || 0) + vy * age + 0.5 * (f.g || 0) * age * age;
            if (f.sway) x += f.sway[0] * Math.sin(TAU * (age / f.sway[1] + hi(7)));
            rot = Math.atan2(vy + (f.g || 0) * age, vx); len = f.len || 0;
          }
          const size = f.size[0] + (f.size[1] - f.size[0]) * hi(8);
          const color = colors[Math.floor(hi(9) * colors.length) % colors.length];
          const [fi, fo] = f.fade || [0.15, 0.3];
          let a = 1;
          if (u < fi) a = u / fi;
          if (u > 1 - fo) a = Math.min(a, (1 - u) / fo);
          a *= a0 * (f.alpha ? f.alpha[0] + (f.alpha[1] - f.alpha[0]) * hi(10) : 1);
          const shapeRot = (f.shape === 'streak') ? rot : (hi(11) * 360 + (f.spin || 0) * age * (hi(12) < 0.5 ? -1 : 1)) * Math.PI / 180;
          const ex = {
            len: S(len),
            flip: Math.cos(TAU * age * (f.flipHz || 0) + hi(13) * TAU),
            flap: Math.sin(TAU * (age * (f.flapHz || 3) + hi(14))),
          };
          drawShape(ctx, f.shape || 'dot', X(x), Y(y), S(size), shapeRot, color, a, ex);
        }
        break;
      }
    }
  }

  // Draws one part image at its place in the frame, moved by its channels
  function part(ctx, img, p, box, t, e, M) {
    if (!img) return;
    const rest = { dx: 0, dy: 0, rot: 0, s: 1, sx: 1, sy: 1, a: p.hidden ? 0 : 1 };
    const m = p.m || {};
    const v = n => {
      const c = ch(m[n], t, rest[n]);
      // A part that looks the same turned by sym degrees settles on the nearest such turn
      const r = (n === 'rot' && p.sym) ? Math.round(c / p.sym) * p.sym : rest[n];
      return r + (c - r) * e;
    };
    const a = v('a');
    if (a <= 0.003) return;
    const x0 = box[0] * M.w, y0 = box[1] * M.h, w = (box[2] - box[0]) * M.w, h = (box[3] - box[1]) * M.h;
    const pv = p.pivot || [0, 0];
    const px = M.X(pv[0]), py = M.Y(pv[1]);
    ctx.save();
    ctx.globalAlpha = Math.min(1, a);
    ctx.translate(px + M.S(v('dx')), py + M.S(v('dy')));
    ctx.rotate(v('rot') * Math.PI / 180);
    ctx.scale(v('s') * v('sx'), v('s') * v('sy'));
    ctx.translate(-px, -py);
    ctx.drawImage(img, x0, y0, w, h);
    ctx.restore();
  }

  // Maps a frame's local units to pixels on a canvas w x h that shows the whole frame
  function mapper(frame, w, h) {
    const k = w / frame.W;
    return { w, h, X: v => (frame.cx + v * frame.s) * k, Y: v => (frame.gy + v * frame.s) * k, S: v => v * frame.s * k };
  }

  // imgs: { bg, fg, parts: { name: img } }; motion: the frame's entry in motion.json
  function drawScene(ctx, imgs, motion, t, e, lift, w, h) {
    const M = mapper(motion.frame, w, h);
    const scene = motion.parts.filter(p => p.group === 'scene');
    const subject = motion.parts.filter(p => p.group !== 'scene');
    ctx.save();
    ctx.translate(w / 2, h / 2); ctx.scale(1 + 0.04 * lift, 1 + 0.04 * lift); ctx.translate(-w / 2, -h / 2);
    ctx.drawImage(imgs.bg, 0, 0, w, h);
    for (const p of scene) part(ctx, imgs.parts[p.name], p, p.box, t, e, M);
    for (const f of motion.fx) if (f.layer === 'scene') fx(ctx, f, t, e, M);
    ctx.restore();
    ctx.save();
    const s = 1 + 0.09 * lift;
    ctx.translate(w / 2, h * 0.88 - 3 * lift * (w / 190)); ctx.scale(s, s); ctx.translate(-w / 2, -h * 0.88);
    for (const p of subject) if (p.under) part(ctx, imgs.parts[p.name], p, p.box, t, e, M);
    if (motion.fg) part(ctx, imgs.fg, motion.fg, [0, 0, 1, 1], t, e, M); else ctx.drawImage(imgs.fg, 0, 0, w, h);
    for (const p of subject) if (!p.under) part(ctx, imgs.parts[p.name], p, p.box, t, e, M);
    for (const f of motion.fx) if (f.layer !== 'scene') fx(ctx, f, t, e, M);
    ctx.restore();
  }

  root.ScenePlayer = { drawScene, ch };
})(typeof window !== 'undefined' ? window : globalThis);
