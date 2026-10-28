package com.lumera.app.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How the scene art moves while its card has focus, read from service_art/motion.json, which
 * tools/service-art writes next to the pictures. A scene has parts (pictures of their own that
 * move, turn, grow and fade) and effects drawn in code (glows, sparks, rain of hearts, a
 * countdown). Everything is placed in the scene's local units, round where its subject stands,
 * so one description fits every card shape.
 *
 * tools/service-art/player.js plays the same file in the browser for previews; the two are
 * kept to the same maths by hand.
 */
object SceneMotion {
    private const val FILE = "service_art/motion.json"
    private val lock = Mutex()
    @Volatile private var scenes: Map<String, Map<String, FrameMotion>>? = null

    /** The scene's motion in one frame (wide, square, tall or hero), once it has been read. */
    fun cached(code: String, frame: String): FrameMotion? = scenes?.get(code)?.get(frame)

    suspend fun load(context: Context, code: String, frame: String): FrameMotion? {
        scenes?.let { return it[code]?.get(frame) }
        return lock.withLock {
            scenes ?: withContext(Dispatchers.IO) {
                runCatching {
                    val json = context.assets.open(FILE).bufferedReader().use { it.readText() }
                    parse(JSONObject(json))
                }.getOrElse {
                    Log.w("SceneMotion", "motion.json unreadable", it)
                    emptyMap()
                }
            }.also { scenes = it }
        }[code]?.get(frame)
    }

    private fun parse(root: JSONObject): Map<String, Map<String, FrameMotion>> =
        root.keys().asSequence().associateWith { code ->
            val frames = root.getJSONObject(code)
            // A scene that does not parse stays still; the others still move
            frames.keys().asSequence().mapNotNull { frame ->
                runCatching { frame to FrameMotion.parse(frames.getJSONObject(frame)) }
                    .onFailure { Log.w("SceneMotion", "$code $frame: bad motion", it) }
                    .getOrNull()
            }.toMap()
        }
}

/** A value that changes over time: a base or keyframes, plus sine waves on top. */
class Channel(
    private val base: Float,
    private val period: Float,
    private val times: FloatArray?,
    private val values: FloatArray?,
    private val eases: IntArray?,
    private val waves: FloatArray?
) {
    fun at(t: Float): Float {
        var v = if (times != null) key(t) else base
        if (waves != null) {
            var i = 0
            while (i < waves.size) {
                v += waves[i] * sin(TAU * (t / waves[i + 1] + waves[i + 2]))
                i += 3
            }
        }
        return v
    }

    private fun key(t: Float): Float {
        val ts = times!!
        val vs = values!!
        var tt = ((t % period) + period) % period
        var i = ts.size - 1
        for (j in ts.indices) if (ts[j] <= tt) i = j
        val t0 = ts[i]
        val v0 = vs[i]
        val t1: Float
        val v1: Float
        if (i + 1 < ts.size) {
            t1 = ts[i + 1]; v1 = vs[i + 1]
        } else {
            t1 = ts[0] + period; v1 = vs[0]
        }
        if (tt < t0) tt += period
        var u = if (t1 == t0) 0f else (tt - t0) / (t1 - t0)
        // How the segment from a key eases: 0 in and out, 1 straight, 2 speeding up, 3 slowing
        u = when (eases!![i]) {
            0 -> u * u * (3 - 2 * u)
            2 -> u * u
            3 -> 1 - (1 - u) * (1 - u)
            else -> u
        }
        return v0 + (v1 - v0) * u
    }

    companion object {
        fun of(value: Float) = Channel(value, 1f, null, null, null, null)

        fun parse(any: Any?, default: Float): Channel {
            if (any == null || any == JSONObject.NULL) return of(default)
            if (any is Number) return of(any.toFloat())
            val o = any as JSONObject
            val base = o.optDouble("b", default.toDouble()).toFloat()
            var period = 1f
            var times: FloatArray? = null
            var values: FloatArray? = null
            var eases: IntArray? = null
            o.optJSONObject("k")?.let { k ->
                period = k.getDouble("p").toFloat()
                val lin = k.optBoolean("lin")
                val v = k.getJSONArray("v")
                times = FloatArray(v.length()) { v.getJSONArray(it).getDouble(0).toFloat() }
                values = FloatArray(v.length()) { v.getJSONArray(it).getDouble(1).toFloat() }
                eases = IntArray(v.length()) { v.getJSONArray(it).optInt(2, if (lin) 1 else 0) }
            }
            val waves = o.optJSONArray("w")?.let { w ->
                FloatArray(w.length() * 3) { idx ->
                    val one = w.getJSONArray(idx / 3)
                    one.optDouble(idx % 3, 0.0).toFloat()
                }
            }
            return Channel(base, period, times, values, eases, waves)
        }
    }
}

/** Where a frame's subject stands: frame size W x H, its ground centre (cx, gy) and scale s. */
class SceneFrame(val w: Float, val h: Float, val cx: Float, val gy: Float, val s: Float) {
    /** Local units to pixels on a canvas [width] wide that shows the whole frame. */
    fun x(v: Float, width: Float) = (cx + v * s) * width / w
    fun y(v: Float, width: Float) = (gy + v * s) * width / w
    fun len(v: Float, width: Float) = v * s * width / w
}

/** A picture of its own in a scene, and how it moves. */
class ScenePart(
    val name: String,
    val inScene: Boolean,
    val under: Boolean,
    val hidden: Boolean,
    val pivotX: Float,
    val pivotY: Float,
    val sym: Float,
    val box: FloatArray,
    val dx: Channel?, val dy: Channel?, val rot: Channel?,
    val s: Channel?, val sx: Channel?, val sy: Channel?, val a: Channel?
) {
    val moves get() = dx != null || dy != null || rot != null || s != null || sx != null || sy != null || a != null

    companion object {
        fun parse(o: JSONObject, box: FloatArray): ScenePart {
            val m = o.optJSONObject("m") ?: JSONObject()
            val pivot = o.optJSONArray("pivot")
            fun ch(n: String, d: Float) = if (m.has(n)) Channel.parse(m.get(n), d) else null
            return ScenePart(
                name = o.optString("name"),
                inScene = o.optString("group") == "scene",
                under = o.optBoolean("under"),
                hidden = o.optBoolean("hidden"),
                pivotX = pivot?.getDouble(0)?.toFloat() ?: 0f,
                pivotY = pivot?.getDouble(1)?.toFloat() ?: 0f,
                sym = o.optDouble("sym", 0.0).toFloat(),
                box = box,
                dx = ch("dx", 0f), dy = ch("dy", 0f), rot = ch("rot", 0f),
                s = ch("s", 1f), sx = ch("sx", 1f), sy = ch("sy", 1f), a = ch("a", 1f)
            )
        }
    }
}

/** Where a part is and how it is turned at a moment, ready for a graphics layer. */
class PartPose {
    var dx = 0f; var dy = 0f; var rot = 0f; var scaleX = 1f; var scaleY = 1f; var alpha = 1f

    /**
     * The part's pose at time [t], [e] of the way from its resting pose (0) to its moving one
     * (1). A part that looks the same turned by sym degrees settles on the nearest such turn.
     */
    fun of(p: ScenePart, t: Float, e: Float): PartPose {
        fun lerp(c: Channel?, rest: Float) = if (c == null) rest else rest + (c.at(t) - rest) * e
        dx = lerp(p.dx, 0f)
        dy = lerp(p.dy, 0f)
        rot = if (p.rot == null) 0f else {
            val r = p.rot.at(t)
            val rest = if (p.sym > 0f) (r / p.sym).roundToInt() * p.sym else 0f
            rest + (r - rest) * e
        }
        val s = lerp(p.s, 1f)
        scaleX = s * lerp(p.sx, 1f)
        scaleY = s * lerp(p.sy, 1f)
        alpha = lerp(p.a, if (p.hidden) 0f else 1f).coerceIn(0f, 1f)
        return this
    }
}

/** One frame of a scene: its parts, its effects, and for the hero, where the subject was cut from. */
class FrameMotion(
    val frame: SceneFrame,
    val fg: ScenePart?,
    val parts: List<ScenePart>,
    val sceneFx: List<SceneFx>,
    val topFx: List<SceneFx>,
    val fgBox: FloatArray?
) {
    val animated = fg != null || parts.any { it.moves } || sceneFx.isNotEmpty() || topFx.isNotEmpty()

    companion object {
        fun parse(o: JSONObject): FrameMotion {
            val f = o.getJSONObject("frame")
            val frame = SceneFrame(
                f.getDouble("W").toFloat(), f.getDouble("H").toFloat(),
                f.getDouble("cx").toFloat(), f.getDouble("gy").toFloat(), f.getDouble("s").toFloat()
            )
            val parts = o.optJSONArray("parts").objects().map { ScenePart.parse(it, it.optJSONArray("box")?.floats(4) ?: floatArrayOf(0f, 0f, 1f, 1f)) }
            val fx = o.optJSONArray("fx").objects().mapNotNull { SceneFx.parse(it) }
            val fg = o.optJSONObject("fg")?.let { ScenePart.parse(it, floatArrayOf(0f, 0f, 1f, 1f)) }
            return FrameMotion(
                frame, fg, parts,
                sceneFx = fx.filter { it.inScene },
                topFx = fx.filter { !it.inScene },
                fgBox = o.optJSONArray("fgBox")?.floats(4)
            )
        }
    }
}

/** An effect drawn in code over the scene's pictures. */
class SceneFx(
    val type: String,
    val inScene: Boolean,
    val keep: Boolean,
    val color: List<Color>,
    val a: Channel,
    val dx: Channel?,
    val dy: Channel?,
    private val o: JSONObject
) {
    // Everything else the effect needs, read once
    val x = Channel.parse(o.opt("x"), 0f)
    val y = Channel.parse(o.opt("y"), 0f)
    val r = Channel.parse(o.opt("r"), 10f)
    val sx = o.optDouble("sx", 1.0).toFloat()
    val lw = o.optDouble("lw", 1.5).toFloat()
    val shape = o.optString("shape", if (type == "dots") "glowdot" else "dot")
    val pts: Array<FloatArray> = o.optJSONArray("pts")?.let { a -> Array(a.length()) { a.getJSONArray(it).floats(4) } } ?: emptyArray()
    val tw = o.optJSONArray("tw")?.floats(2) ?: floatArrayOf(0f, 1f)
    // rays
    val n = o.optInt("n", 0)
    val r0 = o.optDouble("r0", 0.0).toFloat()
    val r1 = o.optDouble("r1", 0.0).toFloat()
    val halfAngle = (o.optDouble("w", 0.0) * PI / 180).toFloat()
    val spin = o.optDouble("spin", 0.0).toFloat()
    val rot0 = o.optDouble("rot0", 0.0).toFloat()
    val upper = o.optBoolean("upper")
    // digits
    val from = o.optInt("from", 10)
    val hold = o.optDouble("hold", 1.0).toFloat()
    val digitH = o.optDouble("h", 12.0).toFloat()
    // particles
    val life = o.optDouble("life", 1.0).toFloat()
    val seed = o.optDouble("seed", 1.0)
    val sync = o.optBoolean("sync")
    val off = o.optDouble("off", 0.0).toFloat()
    val px = o.optDouble("x", 0.0).toFloat()
    val py = o.optDouble("y", 0.0).toFloat()
    val pw = o.optDouble("w", 0.0).toFloat()
    val ph = o.optDouble("h", 0.0).toFloat()
    val vx = o.optDouble("vx", 0.0).toFloat()
    val vy = o.optDouble("vy", 0.0).toFloat()
    val dv = o.optJSONArray("dv")?.floats(2)
    val g = o.optDouble("g", 0.0).toFloat()
    val acc = o.optDouble("acc", 0.0).toFloat()
    val sway = o.optJSONArray("sway")?.floats(2)
    val size = o.optJSONArray("size")?.floats(2) ?: floatArrayOf(2f, 2f)
    val alpha = o.optJSONArray("alpha")?.floats(2)
    val fade = o.optJSONArray("fade")?.floats(2) ?: floatArrayOf(0.15f, 0.3f)
    val origin = o.optJSONArray("from")?.floats(2)
    val arc = o.optJSONArray("arc")?.floats(2) ?: floatArrayOf(0f, 360f)
    val speed = o.optJSONArray("speed")?.floats(2)
    val startR = o.optJSONArray("r0")?.floats(2)
    val len = o.optDouble("len", 0.0).toFloat()
    val partSpin = o.optDouble("spin", 0.0).toFloat()
    val flipHz = o.optDouble("flipHz", 0.0).toFloat()
    val flapHz = o.optDouble("flapHz", 3.0).toFloat()

    companion object {
        private val TYPES = setOf("flash", "glow", "ring", "rays", "dots", "digits", "particles")

        fun parse(o: JSONObject): SceneFx? {
            val type = o.optString("type")
            if (type !in TYPES) return null
            val colors = when (val c = o.opt("color")) {
                is JSONArray -> List(c.length()) { parseColor(c.getString(it)) }
                is String -> listOf(parseColor(c))
                else -> listOf(Color.White)
            }
            return SceneFx(
                type = type,
                inScene = o.optString("layer") == "scene",
                keep = o.optBoolean("keep"),
                color = colors,
                a = Channel.parse(o.opt("a"), 1f),
                dx = if (o.has("dx")) Channel.parse(o.get("dx"), 0f) else null,
                dy = if (o.has("dy")) Channel.parse(o.get("dy"), 0f) else null,
                o = o
            )
        }

        private fun parseColor(hex: String) = Color(android.graphics.Color.parseColor(hex))
    }
}

/**
 * Draws a scene's effects at time [t] and focus [e] (0 at rest, 1 in full motion). Brushes
 * and shapes are made once per size and kept, so a frame allocates next to nothing.
 */
class SceneFxPainter(private val motion: FrameMotion, private val size: Size) {
    private val width = size.width
    private val f = motion.frame
    private val glows = HashMap<Color, Brush>()
    private val trails = HashMap<Color, Brush>()
    private val rays = HashMap<SceneFx, Brush>()
    private val wing = Path()
    private val trailPath = Path()

    private fun X(v: Float) = f.x(v, width)
    private fun Y(v: Float) = f.y(v, width)
    private fun S(v: Float) = f.len(v, width)

    // A soft round glow of radius 1 at the origin, scaled into place when drawn
    private fun glowBrush(c: Color) = glows.getOrPut(c) {
        Brush.radialGradient(
            0f to c, 0.35f to c.copy(alpha = c.alpha * 0.45f), 1f to c.copy(alpha = 0f),
            center = Offset.Zero, radius = 1f
        )
    }

    // A trail from x = -1 (clear) to x = 0 (solid), scaled along the particle's path
    private fun trailBrush(c: Color) = trails.getOrPut(c) {
        Brush.horizontalGradient(0f to c.copy(alpha = 0f), 1f to c, startX = -1f, endX = 0f)
    }

    private fun DrawScope.glowAt(x: Float, y: Float, r: Float, c: Color, a: Float, sx: Float = 1f) {
        if (a <= 0.003f || r <= 0f) return
        translate(x, y) {
            scale(r * sx, r, pivot = Offset.Zero) {
                drawCircle(glowBrush(c), radius = 1f, center = Offset.Zero, alpha = a.coerceAtMost(1f))
            }
        }
    }

    fun DrawScope.draw(list: List<SceneFx>, t: Float, e: Float) {
        for (fx in list) drawFx(fx, t, e)
    }

    private fun DrawScope.drawFx(fx: SceneFx, t: Float, e: Float) {
        // An effect that is part of the picture (keep) shows at rest too; the rest fade in with focus
        val ek = if (fx.keep) 1f else e
        val a0 = fx.a.at(t) * ek
        if (a0 <= 0.003f) return
        val ox = fx.dx?.let { S(it.at(t) * e) } ?: 0f
        val oy = fx.dy?.let { S(it.at(t) * e) } ?: 0f
        val c = fx.color[0]
        when (fx.type) {
            "flash" -> drawRect(c, alpha = a0.coerceAtMost(1f))
            "glow" -> glowAt(X(fx.x.at(t)) + ox, Y(fx.y.at(t)) + oy, S(fx.r.at(t)), c, a0, fx.sx)
            "ring" -> {
                val r = S(fx.r.at(t))
                if (r > 0f) drawCircle(c, r, Offset(X(fx.px) + ox, Y(fx.py) + oy), alpha = a0.coerceAtMost(1f), style = Stroke(S(fx.lw)))
            }
            "rays" -> drawRays(fx, t, a0, ox, oy)
            "dots" -> for (p in fx.pts) {
                val a = (fx.a.at(t) + fx.tw[0] * e * sin(TAU * (t / fx.tw[1] + p[3]))).coerceIn(0f, 1f) * ek
                shape(fx.shape, X(p[0]) + ox, Y(p[1]) + oy, S(p[2]), 0f, c, a, 0f, 0f, 0f)
            }
            "digits" -> drawDigits(fx, t, a0, c, ox, oy)
            "particles" -> drawParticles(fx, t, a0, ox, oy)
        }
    }

    private fun DrawScope.drawRays(fx: SceneFx, t: Float, a0: Float, ox: Float, oy: Float) {
        val cx = X(fx.px) + ox
        val cy = Y(fx.py) + oy
        val r0 = S(fx.r0)
        val r1 = S(fx.r1)
        val brush = rays.getOrPut(fx) {
            Brush.radialGradient(0f to fx.color[0], 1f to fx.color[0].copy(alpha = 0f), center = Offset(cx, cy), radius = r1)
        }
        val base = (fx.rot0 + fx.spin * t) * PI.toFloat() / 180f
        val path = wing
        for (i in 0 until fx.n) {
            val ang = base + TAU * i / fx.n
            // upper: rays fade out as they turn toward the ground
            val k = if (fx.upper) max(0f, -sin(ang)) else 1f
            if (k <= 0.01f) continue
            path.reset()
            path.moveTo(cx + cos(ang) * r0, cy + sin(ang) * r0)
            path.lineTo(cx + cos(ang - fx.halfAngle) * r1, cy + sin(ang - fx.halfAngle) * r1)
            path.lineTo(cx + cos(ang + fx.halfAngle) * r1, cy + sin(ang + fx.halfAngle) * r1)
            path.close()
            drawPath(path, brush, alpha = (a0 * k).coerceAtMost(1f))
        }
    }

    private fun DrawScope.drawDigits(fx: SceneFx, t: Float, a0: Float, c: Color, ox: Float, oy: Float) {
        val per = fx.from + fx.hold
        val tt = ((t % per) + per) % per
        val left = max(0, fx.from - floor(tt).toInt())
        val text = "%02d:%02d".format(left / 60, left % 60)
        val h = S(fx.digitH)
        val w = h * 0.5f
        val gap = h * 0.16f
        var x = X(fx.px) + ox - (4 * w + 3 * gap + w * 0.4f) / 2
        val y = Y(fx.py) + oy - h / 2
        val blink = if (left == 0) (if (floor(tt * 4).toInt() % 2 == 1) 0.25f else 1f) else 1f
        for (ch in text) {
            if (ch == ':') {
                val on = if (tt % 1f < 0.5f) 1f else 0.2f
                val al = (a0 * on * blink).coerceAtMost(1f)
                drawRect(c, Offset(x + w * 0.1f, y + h * 0.28f), Size(w * 0.2f, w * 0.2f), alpha = al)
                drawRect(c, Offset(x + w * 0.1f, y + h * 0.62f), Size(w * 0.2f, w * 0.2f), alpha = al)
                x += w * 0.4f + gap
                continue
            }
            val lit = DIGITS[ch - '0']
            for (i in 0 until 7) {
                val s = SEGMENTS[i]
                val on = lit and (1 shl i) != 0
                val sx = x + s[0] * w
                val sy = y + s[1] * h / 2
                val sw = s[2] * w
                val sh = s[3] * h / 2
                if (on) drawRect(c, Offset(sx - sw * 0.25f, sy - sh * 0.25f), Size(sw * 1.5f, sh * 1.5f), alpha = (a0 * 0.35f * blink).coerceAtMost(1f))
                drawRect(c, Offset(sx, sy), Size(sw, sh), alpha = (a0 * if (on) blink else 0.1f).coerceAtMost(1f))
            }
            x += w + gap
        }
    }

    private fun DrawScope.drawParticles(fx: SceneFx, t: Float, a0: Float, ox: Float, oy: Float) {
        val life = fx.life
        for (i in 0 until fx.n) {
            val start = if (fx.sync) fx.off else hash(fx.seed, i, 0, 0) * life
            val tt = t + start
            val k = floor(tt / life).toInt()
            val age = tt - k * life
            val u = age / life
            fun hi(j: Int) = hash(fx.seed, i, k, j)
            fun hk(j: Int) = hash(fx.seed, if (fx.sync) 0 else i, k, j)
            var x: Float
            var y: Float
            var heading: Float
            var trail: Float
            val sp = fx.speed?.let { it[0] + (it[1] - it[0]) * hi(4) } ?: 0f
            val origin = fx.origin
            if (origin != null) {
                val arc = fx.arc
                val deg = if (fx.sync) arc[0] + (arc[1] - arc[0]) * (i + 0.5f) / fx.n + (hi(3) - 0.5f) * 8f
                else arc[0] + (arc[1] - arc[0]) * hi(3)
                val ang = deg * PI.toFloat() / 180f
                val cx = origin[0] + if (fx.pw != 0f) (hk(1) - 0.5f) * fx.pw else 0f
                val cy = origin[1] + if (fx.ph != 0f) (hk(2) - 0.5f) * fx.ph else 0f
                val r0 = fx.startR?.let { it[0] + (it[1] - it[0]) * hi(5) } ?: 0f
                val d = r0 + sp * age + 0.5f * fx.acc * age * age
                x = cx + cos(ang) * d
                y = cy + sin(ang) * d + 0.5f * fx.g * age * age
                val v = sp + fx.acc * age
                val vx = cos(ang) * v
                val vy = sin(ang) * v + fx.g * age
                heading = atan2(vy, vx)
                trail = min(fx.len, fx.len * hypot(vx, vy) / max(1f, if (sp == 0f) 1f else sp))
            } else {
                val vx = fx.vx + (fx.dv?.get(0) ?: 0f) * (hi(5) - 0.5f) * 2f
                val vy = fx.vy + (fx.dv?.get(1) ?: 0f) * (hi(6) - 0.5f) * 2f
                x = fx.px + hi(1) * fx.pw + vx * age
                y = fx.py + hi(2) * fx.ph + vy * age + 0.5f * fx.g * age * age
                fx.sway?.let { x += it[0] * sin(TAU * (age / it[1] + hi(7))) }
                heading = atan2(vy + fx.g * age, vx)
                trail = fx.len
            }
            val size = fx.size[0] + (fx.size[1] - fx.size[0]) * hi(8)
            val color = fx.color[(hi(9) * fx.color.size).toInt() % fx.color.size]
            var a = 1f
            if (u < fx.fade[0]) a = u / fx.fade[0]
            if (u > 1f - fx.fade[1]) a = min(a, (1f - u) / fx.fade[1])
            a *= a0 * (fx.alpha?.let { it[0] + (it[1] - it[0]) * hi(10) } ?: 1f)
            val rot = if (fx.shape == "streak") heading
            else (hi(11) * 360f + fx.partSpin * age * if (hi(12) < 0.5f) -1f else 1f) * PI.toFloat() / 180f
            val flip = cos(TAU * age * fx.flipHz + hi(13) * TAU)
            val flap = sin(TAU * (age * fx.flapHz + hi(14)))
            shape(fx.shape, X(x) + ox, Y(y) + oy, S(size), rot, color, a, S(trail), flip, flap)
        }
    }

    private fun DrawScope.shape(name: String, x: Float, y: Float, size: Float, rot: Float, c: Color, a: Float, trail: Float, flip: Float, flap: Float) {
        if (a <= 0.003f) return
        val al = a.coerceAtMost(1f)
        when (name) {
            "dot" -> drawCircle(c, size / 2, Offset(x, y), alpha = al)
            "glowdot" -> {
                glowAt(x, y, size * 2.2f, c, al * 0.55f)
                drawCircle(Color.White, size * 0.32f, Offset(x, y), alpha = (al * 0.9f).coerceAtMost(1f))
            }
            "puff" -> glowAt(x, y, size / 2, c, al)
            "ring" -> drawCircle(c, size / 2, Offset(x, y), alpha = al, style = Stroke(max(1f, size * 0.12f)))
            "streak" -> translate(x, y) {
                rotateRad(rot, pivot = Offset.Zero) {
                    // A tapered trail behind the particle, fading toward its tail
                    if (trail > 0f) scale(trail, 1f, pivot = Offset.Zero) {
                        trailPath.reset()
                        trailPath.moveTo(-1f, 0f); trailPath.lineTo(0f, -size / 2); trailPath.lineTo(0f, size / 2); trailPath.close()
                        drawPath(trailPath, trailBrush(c), alpha = al)
                    }
                    drawCircle(c, size / 2, Offset.Zero, alpha = al)
                }
            }
            "rect" -> translate(x, y) {
                rotateRad(rot, pivot = Offset.Zero) {
                    scale(1f, flip, pivot = Offset.Zero) {
                        drawRect(c, Offset(-size / 2, -size / 4), Size(size, size / 2), alpha = al)
                    }
                }
            }
            "bird" -> {
                val s = size
                wing.reset()
                wing.moveTo(x - s / 2, y - flap * s * 0.3f)
                wing.quadraticBezierTo(x - s / 4, y - s * 0.18f - flap * s * 0.1f, x, y)
                wing.quadraticBezierTo(x + s / 4, y - s * 0.18f - flap * s * 0.1f, x + s / 2, y - flap * s * 0.3f)
                drawPath(wing, c, alpha = al, style = Stroke(max(1f, s * 0.14f)))
            }
            "bat" -> {
                val s = size / 2
                wing.reset()
                wing.moveTo(x, y - s * 0.15f)
                wing.quadraticBezierTo(x - s * 0.4f, y - s * 0.2f - flap * s * 0.5f, x - s, y - flap * s * 0.6f)
                wing.quadraticBezierTo(x - s * 0.7f, y + s * 0.1f, x - s * 0.45f, y + s * 0.05f)
                wing.quadraticBezierTo(x - s * 0.3f, y + s * 0.3f, x, y + s * 0.2f)
                wing.quadraticBezierTo(x + s * 0.3f, y + s * 0.3f, x + s * 0.45f, y + s * 0.05f)
                wing.quadraticBezierTo(x + s * 0.7f, y + s * 0.1f, x + s, y - flap * s * 0.6f)
                wing.quadraticBezierTo(x + s * 0.4f, y - s * 0.2f - flap * s * 0.5f, x, y - s * 0.15f)
                drawPath(wing, c, alpha = al)
            }
            else -> {
                val path = UNIT_SHAPES[name] ?: return
                translate(x, y) {
                    rotateRad(rot, pivot = Offset.Zero) {
                        scale(size, size, pivot = Offset.Zero) { drawPath(path, c, alpha = al) }
                    }
                }
            }
        }
    }

    private companion object {
        // Unit shapes, drawn round the origin at size 1 (the same as player.js)
        val UNIT_SHAPES: Map<String, Path> = mapOf(
            "heart" to "M0,0.35 C-0.1,0.25 -0.5,0 -0.5,-0.2 C-0.5,-0.45 -0.2,-0.5 0,-0.28 C0.2,-0.5 0.5,-0.45 0.5,-0.2 C0.5,0 0.1,0.25 0,0.35 Z",
            "petal" to "M0,-0.5 C0.33,-0.5 0.5,-0.17 0.42,0.17 C0.33,0.42 0.08,0.5 0,0.33 C-0.08,0.5 -0.33,0.42 -0.42,0.17 C-0.5,-0.17 -0.33,-0.5 0,-0.5 Z",
            "spark" to "M0,-0.5 Q0.08,-0.08 0.5,0 Q0.08,0.08 0,0.5 Q-0.08,0.08 -0.5,0 Q-0.08,-0.08 0,-0.5 Z",
            "leaf" to "M0,-0.5 C0.3,-0.3 0.3,0.3 0,0.5 C-0.3,0.3 -0.3,-0.3 0,-0.5 Z"
        ).mapValues { PathParser().parsePathString(it.value).toPath() }

        // Seven segments a to g as x, y, w, h in a 1 x 2 cell, and which are lit for each digit
        val SEGMENTS = arrayOf(
            floatArrayOf(0.1f, 0f, 0.8f, 0.14f), floatArrayOf(0.86f, 0.1f, 0.14f, 0.82f), floatArrayOf(0.86f, 1.08f, 0.14f, 0.82f),
            floatArrayOf(0.1f, 1.86f, 0.8f, 0.14f), floatArrayOf(0f, 1.08f, 0.14f, 0.82f), floatArrayOf(0f, 0.1f, 0.14f, 0.82f),
            floatArrayOf(0.1f, 0.93f, 0.8f, 0.14f)
        )
        val DIGITS = listOf("abcdef", "bc", "abdeg", "abcdg", "bcfg", "acdfg", "acdefg", "abc", "abcdefg", "abcdfg")
            .map { lit -> lit.fold(0) { m, ch -> m or (1 shl (ch - 'a')) } }
    }
}

private const val TAU = (2 * PI).toFloat()

/** The same random numbers as player.js: one per particle, cycle and use. */
private fun hash(seed: Double, i: Int, k: Int, j: Int): Float {
    val x = sin(i * 127.1 + k * 311.7 + j * 74.7 + seed * 17.3) * 43758.5453
    return (x - floor(x)).toFloat()
}

private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }

private fun JSONArray.floats(n: Int) = FloatArray(n) { optDouble(it, 0.0).toFloat() }
