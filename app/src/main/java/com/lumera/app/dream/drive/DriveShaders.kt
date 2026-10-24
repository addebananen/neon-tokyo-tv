package com.lumera.app.dream.drive

// GLSL ES 1.00 for the night drive, word for word the shaders of the browser prototype.

private const val FRAG_PRECISION = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
"""

private const val FOG = """
uniform vec3 uFogColor;
uniform float uFogDensity;
float fogAmount(float depth) {
  return clamp(1.0 - exp(-uFogDensity * max(depth - 12.0, 0.0)), 0.0, 1.0);
}
"""

private const val HASH = """
float hash(vec2 p) {
  return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}
"""

object DriveShaders {
    // Sprites: signs, glows, lamps, other cars, decals on the road. Premultiplied alpha. A sprite
    // with blend 0 adds light; one drawn white with "hot" set keeps a white core whatever its tint.
    const val SPRITE_VS = """
attribute vec3 aPos;
attribute vec2 aUV;
attribute vec4 aColor;
attribute vec3 aParams;
uniform mat4 uProj;
varying vec2 vUV;
varying vec4 vColor;
varying vec3 vParams;
varying float vDepth;
void main() {
  gl_Position = uProj * vec4(aPos, 1.0);
  vUV = aUV;
  vColor = aColor;
  vParams = aParams;
  vDepth = aPos.z;
}"""
    const val SPRITE_FS = FRAG_PRECISION + FOG + """
uniform sampler2D uTex;
varying vec2 vUV;
varying vec4 vColor;
varying vec3 vParams;
varying float vDepth;
void main() {
  vec4 c = texture2D(uTex, vUV);
  float hot = smoothstep(0.72, 1.0, max(c.r, max(c.g, c.b))) * vParams.y;
  vec3 rgb = mix(c.rgb * vColor.rgb, c.rgb, hot) * vColor.a;
  float a = c.a * c.a * vColor.a * vParams.x;
  float f = fogAmount(vDepth) * vParams.z;
  rgb = rgb * (1.0 - f) + uFogColor * a * f;
  gl_FragColor = vec4(rgb, a);
}"""

    // Building fronts, worked out per pixel: windows that light up and go dark, floors of glass,
    // neon bands, and shops along the pavement. Crisp up close, an even glow far away.
    const val FACADE_VS = """
attribute vec3 aPos;
attribute vec2 aUV;
attribute vec4 aWall;
attribute vec4 aGrid;
attribute vec4 aLook;
attribute vec3 aAccent;
uniform mat4 uProj;
varying vec2 vUV;
varying vec4 vWall;
varying vec4 vGrid;
varying vec4 vLook;
varying vec4 vAccent;
void main() {
  gl_Position = uProj * vec4(aPos, 1.0);
  vUV = aUV;
  vWall = aWall;
  vGrid = aGrid;
  vLook = aLook;
  vAccent = vec4(aAccent, aPos.z);
}"""
    const val FACADE_FS = """
#extension GL_OES_standard_derivatives : enable
""" + FRAG_PRECISION + FOG + HASH + """
uniform float uTime;
uniform float uPixel;
uniform vec3 uWarm;
uniform vec3 uCool;
uniform vec3 uSky;
varying vec2 vUV;
varying vec4 vWall;
varying vec4 vGrid;
varying vec4 vLook;
varying vec4 vAccent;

float band(float x, float a, float b, float w) {
  return clamp(smoothstep(a - w, a + w, x) - smoothstep(b - w, b + w, x), 0.0, 1.0);
}

vec3 shop(vec2 uv, vec2 fw, vec3 wall, float seed, vec3 accent) {
  float sid = floor(uv.x / 6.0);
  float hs = hash(vec2(sid, seed * 37.0));
  float h2 = hash(vec2(seed * 11.0, sid));
  float x = uv.x - sid * 6.0;
  float y = uv.y;
  vec3 light = mix(uWarm, uCool, step(0.5, h2)) * (0.75 + 0.25 * fract(h2 * 7.0));
  vec3 col = wall * 0.8;
  float w = max(fw.x, fw.y);
  // The sign board over the shop: a lit panel with blocks of bright lettering
  float board = band(y, 3.25, 4.02, fw.y) * band(x, 0.25, 5.75, fw.x);
  float glyph = step(0.35, hash(vec2(floor(x / 0.42), sid + seed))) * band(fract(x / 0.42), 0.18, 0.82, fw.x / 0.42) * band(y, 3.42, 3.86, fw.y);
  float boardLit = step(0.18, hs);
  col = mix(col, accent * (0.22 + 0.12 * boardLit), board);
  col += mix(accent, vec3(1.0), 0.45) * glyph * board * boardLit * 0.95;
  if (hs < 0.44) {
    // A lit shop: glass with mullions, brighter under the ceiling lights
    float glass = band(y, 0.32, 2.85, fw.y) * band(x, 0.3, 5.7, fw.x);
    float mull = band(fract(x / 1.45), 0.0, 0.05, fw.x / 1.45);
    vec3 inside = light * (0.55 + 0.45 * smoothstep(0.4, 2.7, y));
    inside *= 1.0 - 0.35 * band(y, 0.32, 1.05, fw.y) * step(0.5, fract(hs * 9.0));
    col = mix(col, inside, glass * (1.0 - mull * 0.85));
    float awning = band(y, 2.9, 3.18, fw.y) * band(x, 0.15, 5.85, fw.x) * step(0.55, fract(hs * 5.0));
    vec3 stripes = mix(accent * 0.55, vec3(0.9), step(0.5, fract(x / 0.5)) * 0.5);
    col = mix(col, stripes, awning);
  } else if (hs < 0.62) {
    // Shutters down
    float sh = band(y, 0.05, 2.95, fw.y) * band(x, 0.3, 5.7, fw.x);
    float ribs = 0.75 + 0.25 * smoothstep(0.3, 0.7, fract(y / 0.09));
    col = mix(col, vec3(0.13, 0.12, 0.17) * ribs, sh);
  } else if (hs < 0.76) {
    // A row of vending machines glowing on the pavement
    float unit = floor(x / 1.05);
    float ux = fract(x / 1.05);
    float on = step(unit, 2.0 + floor(fract(hs * 13.0) * 3.0)) * step(0.5, unit + 0.5);
    float body = band(ux, 0.06, 0.94, fw.x / 1.05) * band(y, 0.0, 1.85, fw.y) * on;
    vec3 front = mix(vec3(0.95, 0.97, 1.0), accent, 0.25) * 0.95;
    float rows = band(fract(y / 0.3), 0.15, 0.55, fw.y / 0.3) * band(y, 0.75, 1.65, fw.y) * band(ux, 0.16, 0.84, fw.x);
    vec3 cans = mix(accent, vec3(1.0, 0.4, 0.3), step(0.5, fract(floor(ux * 6.0) * 0.37 + unit * 0.13)));
    col = mix(col, mix(front, cans, rows * 0.8), body);
    col = mix(col, vec3(0.04), band(ux, 0.2, 0.8, fw.x) * band(y, 0.15, 0.45, fw.y) * on);
  } else if (hs < 0.88) {
    // A narrow lit doorway and stairs up
    float door = band(x, 2.4, 3.6, fw.x) * band(y, 0.0, 2.5, fw.y);
    col = mix(col, light * (0.4 + 0.5 * smoothstep(0.0, 2.4, y)), door);
  } else {
    // Closed for the night, a dim light at the back
    float glass = band(y, 0.32, 2.85, fw.y) * band(x, 0.3, 5.7, fw.x);
    col = mix(col, light * 0.12, glass);
  }
  // Trim along the top of the shops
  col = mix(col, wall * 0.5, band(y, 4.05, 4.25, fw.y));
  // Far away it becomes its average
  vec3 avg = mix(wall * 0.8, light * 0.6, 0.4) + accent * 0.12;
  return mix(avg, col, clamp(1.4 - w * 3.0, 0.0, 1.0));
}

void main() {
  vec2 uv = vUV;
  float depth = vAccent.w;
#ifdef GL_OES_standard_derivatives
  vec2 fw = fwidth(uv);
#else
  vec2 fw = vec2(depth * uPixel * 2.0);
#endif
  vec3 wall = vWall.rgb;
  float storeH = vWall.a;
  vec3 accent = vAccent.rgb;
  // Whole numbers on every corner: rounded, so interpolation can't nudge the hashes
  float seed = floor(vLook.w + 0.5);
  vec3 col;
  if (uv.y < storeH) {
    col = shop(uv, fw, wall, seed, accent);
  } else {
    vec2 cell = vGrid.xy;
    vec2 margin = vGrid.zw;
    float glassy = vLook.y;
    vec2 g = vec2(uv.x, uv.y - storeH) / cell;
    vec2 id = floor(g);
    vec2 f = fract(g);
    vec2 fwc = fw / cell;
    float wx = band(f.x, margin.x, 1.0 - margin.x, fwc.x);
    float wy = band(f.y, margin.y, 1.0 - margin.y * 0.55, fwc.y);
    float win = wx * wy;
    // A glass tower lights whole floors; elsewhere each window has its own light
    vec2 lid = vec2(id.x * (1.0 - step(0.9, glassy)), id.y);
    float h = hash(lid + seed * 91.7);
    float h2 = hash(lid.yx + seed * 13.3);
    float slot = floor(uTime / 45.0 + h2 * 9.0);
    float lit = step(hash(lid + vec2(slot * 1.37, seed)), vLook.x);
    vec3 lc = mix(uWarm, uCool, step(0.6, h)) * (0.5 + 0.5 * fract(h * 17.3));
    // A few windows flicker blue with a television
    float tv = step(0.986, h2) * (0.55 + 0.45 * sin(uTime * 6.0 + h * 40.0)) * (1.0 - lit);
    vec3 dark = wall * 0.5 + uSky * (0.05 + 0.12 * glassy);
    // Inside a lit room: brighter under the ceiling, and curtains drawn part way in some
    float wy01 = clamp((f.y - margin.y) / (1.0 - 1.55 * margin.y), 0.0, 1.0);
    float wx01 = clamp((f.x - margin.x) / (1.0 - 2.0 * margin.x), 0.0, 1.0);
    lc *= 0.72 + 0.28 * smoothstep(0.05, 0.95, wy01);
    float cover = 0.18 + 0.3 * fract(h * 7.7);
    float cx = step(0.5, fract(h * 3.3)) > 0.5 ? wx01 : 1.0 - wx01;
    float soft = max(0.02, fwc.x / max(1.0 - 2.0 * margin.x, 0.1));
    lc *= 1.0 - 0.5 * step(0.58, fract(h * 23.7)) * (1.0 - smoothstep(cover - soft, cover + soft, cx));
    vec3 wc = mix(dark, lc, lit) + vec3(0.12, 0.25, 0.65) * tv;
    // Blinds in some of the lit windows
    wc *= 1.0 - 0.28 * step(0.72, fract(h * 5.1)) * step(0.5, fract(f.y * 8.0)) * lit;
    col = mix(wall * (0.92 + 0.08 * hash(vec2(id.y, seed))), wc, win);
    // Neon bands every few floors
    if (vLook.z > 0.5) {
      float on = step(mod(id.y, vLook.z), 0.5);
      float tube = band(f.y, 0.02, 0.07, fwc.y + 0.004);
      col += mix(accent, vec3(1.0), 0.3) * on * tube * 1.3;
      col += accent * on * exp(-pow((f.y - 0.045) / 0.12, 2.0)) * 0.12;
    }
    float winArea = (1.0 - 2.0 * margin.x) * (1.0 - 1.55 * margin.y);
    vec3 avg = mix(wall, mix(dark, mix(uWarm, uCool, 0.4) * 0.72, vLook.x), winArea);
    float detail = clamp(1.5 - max(fwc.x, fwc.y) * 2.6, 0.0, 1.0);
    col = mix(avg, col, detail);
  }
  col = mix(col, uFogColor, fogAmount(depth));
  gl_FragColor = vec4(col, 1.0);
}"""

    // The road: wet tarmac that mirrors the glow of the sky, paint, pavements, a neon strip in
    // each kerb, and water on the bay side of the boulevard.
    const val ROAD_VS = """
attribute vec3 aPos;
attribute vec3 aRoad;
uniform mat4 uProj;
varying vec3 vRoad;
varying float vDepth;
void main() {
  gl_Position = uProj * vec4(aPos, 1.0);
  vRoad = aRoad;
  vDepth = aPos.z;
}"""
    const val ROAD_FS = """
#extension GL_OES_standard_derivatives : enable
""" + FRAG_PRECISION + FOG + """
uniform sampler2D uNoise;
uniform vec3 uRoad;
uniform vec3 uReflect;
uniform vec3 uEdge;
uniform float uPixel;
uniform float uTime;
uniform vec3 uSunGlint;
varying vec3 vRoad;
varying float vDepth;

float band(float x, float a, float b, float w) {
  return clamp(smoothstep(a - w, a + w, x) - smoothstep(b - w, b + w, x), 0.0, 1.0);
}

void main() {
  float u = vRoad.x;
  float s = vRoad.y;
  float kind = vRoad.z;
  float au = abs(u);
#ifdef GL_OES_standard_derivatives
  vec2 fw = fwidth(vec2(u, s));
#else
  vec2 fw = vec2(vDepth * uPixel * 2.0);
#endif
  vec4 n = texture2D(uNoise, vec2(u, s) * 0.25);
  vec4 big = texture2D(uNoise, vec2(u, s) * 0.03125);
  vec3 col;
  float wet;
  if (kind < 0.5) {
    col = uRoad * (0.7 + 0.6 * n.r);
    float paint = band(au, 6.5, 6.64, fw.x);
    paint += band(au, 0.07, 0.19, fw.x);
    float f12 = mod(s, 12.0);
    float dash = smoothstep(-fw.y, fw.y, f12) - smoothstep(5.0 - fw.y, 5.0 + fw.y, f12) + smoothstep(12.0 - fw.y, 12.0 + fw.y, f12);
    paint += band(au, 3.33, 3.47, fw.x) * dash;
    paint = clamp(paint, 0.0, 1.0) * (0.7 + 0.3 * n.r);
    col = mix(col, vec3(0.74, 0.7, 0.84), paint * 0.85);
    wet = 0.35 + 0.65 * smoothstep(0.42, 0.7, big.g);
    // Tyre tracks are a little darker and wetter
    float track = band(mod(au, 3.4), 0.7, 1.15, fw.x) + band(mod(au, 3.4), 2.25, 2.7, fw.x);
    col *= 1.0 - 0.15 * track;
    wet += 0.15 * track;
  } else if (kind < 1.5) {
    vec2 t = fract(vec2(u, s) / 0.6);
    float grout = 1.0 - band(t.x, 0.03, 0.97, fw.x / 0.6) * band(t.y, 0.03, 0.97, fw.y / 0.6);
    col = (uRoad * 1.5 + vec3(0.03, 0.025, 0.04)) * (0.85 + 0.3 * n.r) * (1.0 - 0.35 * grout);
    col = mix(col, uRoad * 2.4 + vec3(0.04), band(au, 6.8, 7.05, fw.x));
    wet = 0.3 + 0.3 * smoothstep(0.5, 0.75, big.g);
  } else if (kind < 2.5) {
    col = uRoad * (0.55 + 0.4 * n.r);
    wet = 0.45;
  } else {
    // The bay: dark water in long ripples that mirror the sky
    float ripple = texture2D(uNoise, vec2(u * 0.05 + uTime * 0.01, s * 0.4)).b;
    col = uRoad * 0.35;
    wet = 0.8 + 0.4 * ripple;
    // The sun laid on the water in broken bands, drifting
    float dx = (gl_FragCoord.x - uSunGlint.x) / uSunGlint.z;
    float bands = smoothstep(0.42, 0.72, texture2D(uNoise, vec2(u * 0.004 + 0.13, s * 0.05 + uTime * 0.012)).b);
    col += vec3(1.0, 0.45, 0.5) * exp(-dx * dx) * bands * uSunGlint.y * 0.85;
    col += vec3(1.0, 0.3, 0.55) * exp(-dx * dx * 0.08) * uSunGlint.y * 0.06;
  }
  float fres = mix(0.05, 0.62, smoothstep(4.0, 170.0, vDepth));
  col += uReflect * fres * wet * (0.65 + 0.35 * n.b);
  // The neon strip in the kerb, and its light on the wet road
  float d = abs(au - 6.9);
  float strip = 1.0 - smoothstep(0.035, 0.035 + fw.x * 1.5, d);
  float spill = exp(-d * d * 1.6) * 0.22 * (0.4 + wet);
  col += uEdge * (strip * 1.6 + spill) * step(kind, 1.5);
  col = mix(col, uFogColor, fogAmount(vDepth));
  gl_FragColor = vec4(col, 1.0);
}"""

    // The sky: the night gradient with the city's glow along the horizon, a few stars, thin lit
    // clouds, and in some districts a great striped sun or a pale moon.
    const val SKY_VS = """
attribute vec2 aPos;
void main() {
  gl_Position = vec4(aPos, 0.999, 1.0);
}"""
    const val SKY_FS = FRAG_PRECISION + HASH + """
uniform sampler2D uNoise;
uniform vec2 uRes;
uniform float uFocal;
uniform float uHorizonY;
uniform float uYaw;
uniform float uTime;
uniform vec3 uTop;
uniform vec3 uMid;
uniform vec3 uHorizon;
uniform vec3 uFog;
uniform vec4 uSun;
uniform vec4 uMoon;
uniform float uStars;
uniform float uFlash;
uniform float uCloud;

void main() {
  vec2 px = gl_FragCoord.xy;
  float fromTop = uRes.y - px.y;
  float el = (uHorizonY - fromTop) / uFocal;
  float az = (px.x - uRes.x * 0.5) / uFocal + uYaw;
  float t = clamp(el / 0.7, 0.0, 1.0);
  vec3 col = mix(uHorizon, uMid, smoothstep(0.0, 0.32, t));
  col = mix(col, uTop, smoothstep(0.28, 1.0, t));
  col += uHorizon * 0.3 * exp(-abs(el) * 16.0);
  if (el < 0.0) col = mix(uHorizon * 0.7 + uFog * 0.3, uFog, smoothstep(0.0, 0.06, -el));
  // Thin clouds lit from below by the city
  vec4 cl = texture2D(uNoise, vec2(az * 0.35 + uCloud, el * 3.2));
  float cloud = smoothstep(0.45, 0.8, cl.g) * smoothstep(0.03, 0.12, el) * (1.0 - smoothstep(0.22, 0.45, el));
  col = mix(col, uHorizon * 0.45 + uMid * 0.5, cloud * 0.45);
  // Stars, fewer near the glow of the city
  vec2 sc = vec2(az, el) * 140.0;
  vec2 id = floor(sc);
  vec2 f = fract(sc) - 0.5;
  float h = hash(id);
  vec2 off = vec2(hash(id + 3.1), hash(id + 7.7)) - 0.5;
  float star = step(1.0 - 0.02 * uStars, h) * smoothstep(0.09, 0.0, length(f - off * 0.6));
  star *= (0.55 + 0.45 * sin(uTime * (1.0 + h * 3.0) + h * 50.0)) * smoothstep(0.1, 0.35, el) * (1.0 - cloud);
  col += vec3(0.9, 0.9, 1.0) * star;
  // The sun: a striped disc low over the far end of the road, and its glow
  if (uSun.w > 0.0) {
    vec2 sp = vec2(az - uSun.x, el - uSun.y);
    float r = uSun.z;
    float d = length(sp);
    float v = clamp((uSun.y + r - el) / (2.0 * r), 0.0, 1.0);
    vec3 sunCol = mix(vec3(1.0, 0.8, 0.34), vec3(1.0, 0.22, 0.52), smoothstep(0.08, 0.95, v));
    float cut = step(0.5, v) * step(fract(v * 11.0 - 0.15), (v - 0.5) * 1.15);
    float disc = smoothstep(r, r - 1.5 / uFocal, d) * (1.0 - cut);
    // The glow goes round the disc, not over it, so the top keeps its gold instead of washing out
    col += sunCol * exp(-max(d - r, 0.0) / (r * 0.45)) * 0.32 * uSun.w * (1.0 - disc);
    col = mix(col, sunCol, disc * uSun.w);
  }
  if (uMoon.w > 0.0) {
    vec2 mp = vec2(az - uMoon.x, el - uMoon.y);
    float d = length(mp);
    float disc = smoothstep(uMoon.z, uMoon.z - 1.5 / uFocal, d);
    vec3 moon = vec3(0.92, 0.93, 1.0) * (0.85 + 0.15 * texture2D(uNoise, mp * 9.0).g);
    col = mix(col, moon, disc * uMoon.w);
    col += vec3(0.6, 0.7, 1.0) * exp(-max(d - uMoon.z, 0.0) / (uMoon.z * 1.6)) * 0.18 * uMoon.w;
  }
  col += vec3(0.75, 0.8, 1.0) * uFlash * (0.4 + 0.6 * smoothstep(0.0, 0.5, el));
  gl_FragColor = vec4(col, 1.0);
}"""

    // A far strip of city at the horizon: the outline in alpha, windows in red, mast lights in green
    const val SKYLINE_VS = """
attribute vec2 aPos;
attribute vec2 aUV;
varying vec2 vUV;
void main() {
  gl_Position = vec4(aPos, 0.998, 1.0);
  vUV = aUV;
}"""
    const val SKYLINE_FS = FRAG_PRECISION + """
uniform sampler2D uTex;
uniform vec3 uSil;
uniform vec3 uWin;
uniform vec3 uHaze;
uniform float uTime;
varying vec2 vUV;
void main() {
  vec4 t = texture2D(uTex, vUV);
  float haze = smoothstep(0.45, 1.0, vUV.y);
  vec3 col = mix(uSil, uHaze, haze * 0.8) * t.a + uWin * t.r * (1.0 - haze * 0.5);
  float blink = step(0.55, fract(uTime * 0.55 + floor(vUV.x * 160.0) * 0.618));
  col += vec3(1.0, 0.16, 0.12) * t.g * blink;
  gl_FragColor = vec4(col, t.a);
}"""

    // Our car: flip-flop paint, glass, rubber and the painted tail, lit by the district, the neon
    // either side, our underglow and the street lamps, whose highlights slide over it as we pass.
    // The street it mirrors slides past too. Cabin cards (material 5) are unlit and carry their tint
    // in the normal.
    const val CAR_VS = """
attribute vec3 aPos;
attribute vec3 aNormal;
attribute vec2 aUV;
attribute vec4 aMat;
uniform mat4 uProj;
varying vec3 vPos;
varying vec3 vNormal;
varying vec2 vUV;
varying vec4 vMat;
void main() {
  vPos = aPos;
  vNormal = aNormal;
  vUV = aUV;
  vMat = aMat;
  gl_Position = uProj * vec4(aPos, 1.0);
}"""
    const val CAR_FS = FRAG_PRECISION + """
uniform sampler2D uTex;
uniform sampler2D uNoise;
uniform vec3 uPaint;
uniform vec3 uFlip;
uniform vec3 uAmbient;
uniform vec3 uLeftLight;
uniform vec3 uRightLight;
uniform vec3 uSkyTop;
uniform vec3 uHorizon;
uniform vec3 uGlow;
uniform vec3 uRight;
uniform vec3 uForward;
uniform vec3 uLampPos[4];
uniform vec3 uLampCol[4];
uniform float uTravel;
uniform vec2 uTail;
uniform vec2 uIndicator;
varying vec3 vPos;
varying vec3 vNormal;
varying vec2 vUV;
varying vec4 vMat;

// What the body mirrors: the sky above, the street's lights sliding past either side, the wet road
vec3 environment(vec3 r) {
  float side = dot(r, uRight);
  vec3 sky = mix(uHorizon * 0.8, uSkyTop, smoothstep(0.0, 0.5, r.y));
  float reach = 7.0 / max(abs(side), 0.12);
  float along = uTravel + dot(r, uForward) * reach;
  float lights = texture2D(uNoise, vec2(0.5 + side * 0.3, along / 96.0)).b;
  vec3 neon = mix(uLeftLight, uRightLight, smoothstep(-0.35, 0.35, side));
  vec3 street = neon * smoothstep(0.5, 0.8, lights) * 1.3 + uAmbient * 0.5;
  vec3 above = mix(street, sky, smoothstep(0.1, 0.45, r.y + (1.0 - abs(side)) * 0.3));
  vec3 below = uAmbient * 0.3 + neon * 0.06;
  return mix(below, above, smoothstep(-0.06, 0.04, r.y));
}

void main() {
  float mat = vMat.x;
  if (mat > 4.5 && mat < 5.5) {
    vec4 c = texture2D(uTex, vUV);
    gl_FragColor = vec4(c.rgb * vNormal, c.a);
    return;
  }
  vec3 n = normalize(vNormal);
  vec3 v = normalize(-vPos);
  float ndv = clamp(dot(n, v), 0.0, 1.0);
  vec3 r = reflect(-v, n);
  float side = dot(n, uRight);
  float gloss = vMat.y;
  float alpha = vMat.z;

  vec3 base = uPaint;
  if (mat < 0.5) base = mix(uPaint, uFlip, pow(1.0 - ndv, 2.0));
  else if (mat < 1.5) base = vec3(0.01, 0.01, 0.016);
  else if (mat < 2.5) base = vec3(0.018, 0.016, 0.024);
  else if (mat < 3.5) base = vec3(0.016, 0.015, 0.02);
  vec4 decal = vec4(0.0);
  if (mat > 3.5) decal = texture2D(uTex, vUV);

  vec3 light = uAmbient
    + uLeftLight * clamp(-side, 0.0, 1.0) * 0.5
    + uRightLight * clamp(side, 0.0, 1.0) * 0.5
    + uHorizon * clamp(n.y, 0.0, 1.0) * 0.16
    + uGlow * smoothstep(0.42, 0.1, vMat.w) * (0.3 + 0.7 * clamp(-n.y, 0.0, 1.0));
  vec3 spec = vec3(0.0);
  for (int i = 0; i < 4; i++) {
    vec3 l = uLampPos[i] - vPos;
    float d2 = dot(l, l);
    l *= inversesqrt(d2);
    float fall = 1.0 / (1.0 + d2 * 0.012);
    light += uLampCol[i] * clamp(dot(n, l), 0.0, 1.0) * fall * 0.8;
    float s = clamp(dot(r, l), 0.0, 1.0);
    spec += uLampCol[i] * (pow(s, 300.0) * 2.4 + pow(s, 24.0) * 0.08) * fall * 2.0;
  }
  // Two lamps lining up in one flat panel shouldn't burn it out white
  spec = min(spec, vec3(1.8));
  vec3 diffuse = base * light;
  diffuse = diffuse * (1.0 - decal.a) + decal.rgb * light * 1.4;

  float fres = 0.04 + 0.96 * pow(1.0 - ndv, 5.0);
  vec3 shine = (environment(r) * mix(0.18, 1.0, fres) * 0.5 + spec) * gloss;
  shine += mix(uLeftLight, uRightLight, smoothstep(-0.35, 0.35, side)) * pow(1.0 - ndv, 3.0) * (mat < 0.5 ? 0.45 : 0.15);

  vec3 glow = vec3(0.0);
  if (mat > 3.5 && mat < 4.5) {
    vec2 e = vec2(vUV.x * 0.5, 0.375 + vUV.y * 0.5);
    vec3 lit = texture2D(uTex, e).rgb;
    vec3 sw = texture2D(uTex, e + vec2(0.5, 0.0)).rgb;
    glow = lit * uTail.x * 1.6 + vec3(1.0, 0.1, 0.15) * sw.r * uTail.y * 1.8
      + vec3(1.0, 0.56, 0.1) * (sw.g * uIndicator.x + sw.b * uIndicator.y) * 2.2;
  }
  if (mat > 0.5 && mat < 1.5) alpha = mix(alpha, 1.0, fres);
  gl_FragColor = vec4(diffuse * alpha + shine + glow, alpha);
}"""

    // Full screen: the vignette, the fade in, a lightning flash
    const val SCREEN_VS = """
attribute vec2 aPos;
varying vec2 vPos;
void main() {
  gl_Position = vec4(aPos, 0.0, 1.0);
  vPos = aPos;
}"""
    const val SCREEN_FS = FRAG_PRECISION + """
uniform vec4 uColor;
uniform float uShape;
uniform vec2 uAspect;
varying vec2 vPos;
void main() {
  float v = smoothstep(0.5, 1.5, length(vPos * uAspect));
  gl_FragColor = uColor * mix(1.0, v, uShape);
}"""
}
