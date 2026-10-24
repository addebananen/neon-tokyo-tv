package com.lumera.app.dream.drive

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Our car: a low, faceted coupe in the spirit of the late eighties, built from cross sections so
// it turns, pitches and leans in true perspective. Its tail is painted (lights, plate, diffuser),
// the cabin behind the glass is two cards with the driver between them, and the wheels turn.
// Car space: x to the car's right, y up, z forward; the tail sits at z = 0 on the road.

object CarSpec {
    const val LENGTH = 4.22
    const val REAR_AXLE = 0.86f
    const val FRONT_AXLE = 3.36f
    const val WHEEL_X = 0.79f
    const val TYRE_R = 0.32f
    const val TYRE_W = 0.27f
}

private class Station(val z: Float, val p: Array<FloatArray>)

// Right halves of the cross sections, tail first: (x, y) for the sill, the widest point, the
// shoulder, the foot of the glasshouse, the roof's edge and the roof's middle.
private val STATIONS = arrayOf(
    Station(0.00f, pts(0.80f, 0.24f, 0.87f, 0.50f, 0.85f, 0.84f, 0.72f, 0.89f, 0.35f, 0.905f, 0f, 0.91f)),
    Station(0.12f, pts(0.86f, 0.20f, 0.92f, 0.50f, 0.90f, 0.87f, 0.75f, 0.925f, 0.35f, 0.94f, 0f, 0.945f)),
    Station(0.60f, pts(0.90f, 0.18f, 0.95f, 0.52f, 0.92f, 0.89f, 0.76f, 0.95f, 0.35f, 0.962f, 0f, 0.966f)),
    Station(0.98f, pts(0.90f, 0.18f, 0.95f, 0.53f, 0.91f, 0.89f, 0.70f, 0.955f, 0.66f, 0.965f, 0f, 0.972f)),
    Station(1.72f, pts(0.89f, 0.18f, 0.93f, 0.54f, 0.89f, 0.88f, 0.71f, 0.95f, 0.57f, 1.13f, 0f, 1.155f)),
    Station(2.46f, pts(0.88f, 0.18f, 0.92f, 0.54f, 0.88f, 0.87f, 0.72f, 0.93f, 0.58f, 1.13f, 0f, 1.16f)),
    Station(3.12f, pts(0.88f, 0.18f, 0.93f, 0.53f, 0.89f, 0.84f, 0.69f, 0.855f, 0.64f, 0.865f, 0f, 0.875f)),
    Station(3.52f, pts(0.88f, 0.18f, 0.93f, 0.52f, 0.89f, 0.80f, 0.70f, 0.80f, 0.35f, 0.81f, 0f, 0.815f)),
    Station(4.22f, pts(0.80f, 0.21f, 0.85f, 0.43f, 0.80f, 0.62f, 0.62f, 0.655f, 0.30f, 0.665f, 0f, 0.668f))
)

private fun pts(vararg v: Float) = Array(v.size / 2) { floatArrayOf(v[it * 2], v[it * 2 + 1]) }

// What lies between each station and the next: decides which upper faces are glass
private val INTERVALS = arrayOf("tail", "deck", "deck", "rearGlass", "roof", "windscreen", "hood", "nose")

// Midnight purple that turns teal where it curves away, and the pink of the light under the car
val CAR_PAINT = floatArrayOf(0.07f, 0.034f, 0.115f)
val CAR_FLIP = floatArrayOf(0.02f, 0.1f, 0.12f)
val CAR_UNDERGLOW = floatArrayOf(1f, 0.22f, 0.62f)

const val MAT_PAINT = 0
const val MAT_GLASS = 1
const val MAT_TRIM = 2
const val MAT_TYRE = 3
const val MAT_TAIL = 4
const val MAT_CARD = 5
const val MAT_WHEEL = 6

// The car texture, in pixels: the tail at 512 px a metre, its two light layers at half that, the
// cabin cards, the driver and a wheel. Each slot is x, y, width, height.
const val CAR_TEX = 1024
object CarUV {
    val TAIL = intArrayOf(0, 0, 1024, 384)      // x -1..1, y 0.92 down to 0.17
    val LIGHTS = intArrayOf(0, 384, 512, 192)   // what is always lit, in colour
    val MASKS = intArrayOf(512, 384, 512, 192)  // r brake, g left indicator, b right indicator
    val DASH = intArrayOf(0, 576, 512, 256)     // the dashboard and the street through the windscreen
    val SEATS = intArrayOf(512, 576, 512, 256)  // the seat backs
    val DRIVER = intArrayOf(0, 832, 256, 192)
    val WHEEL = intArrayOf(256, 832, 192, 192)
}
const val TAIL_TOP = 0.92f

// Where the cabin cards and the driver stand, in car space: x0, x1, y0, y1, z
val CARD_DASH = floatArrayOf(-0.72f, 0.72f, 0.62f, 1.14f, 2.72f)
val CARD_SEATS = floatArrayOf(-0.72f, 0.72f, 0.55f, 1.07f, 1.88f)
val CARD_DRIVER = floatArrayOf(0.12f, 0.60f, 0.70f, 1.06f, 2.04f)

/**
 * A facet of the car: a quad (a triangle repeats its last corner) with its own normal, or a
 * normal for each corner where the body is rounded. It keeps its middle and normal to skip the
 * ones facing away.
 */
class CarQuad(
    val p: Array<FloatArray>,
    val uv: Array<FloatArray>?,
    var n: FloatArray,
    val mid: FloatArray,
    val mat: Int,
    val gloss: Float,
    val alpha: Float
) {
    /** Normals at the corners, where the body is smoothed */
    var corners: Array<FloatArray>? = null
    /** Where the corners sit on the grid of stations and section points: side, group, (i, k) x 4 */
    var grid: IntArray? = null
    /** For a wheel face: the angles of its rim corners, turned while driving */
    var face: FloatArray? = null
}

private fun sub3(a: FloatArray, b: FloatArray) = floatArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
private fun cross3(a: FloatArray, b: FloatArray) =
    floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
private fun dot3(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
private fun norm3(a: FloatArray): FloatArray {
    val l = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]).let { if (it == 0f) 1f else it }
    return floatArrayOf(a[0] / l, a[1] / l, a[2] / l)
}

private fun faceMaterial(kind: String, seg: Int): Int = when {
    seg <= 2 -> MAT_PAINT
    kind == "rearGlass" -> if (seg == 4) MAT_GLASS else MAT_PAINT
    kind == "roof" -> if (seg == 3) MAT_GLASS else MAT_PAINT
    kind == "windscreen" -> if (seg == 4) MAT_GLASS else MAT_PAINT
    else -> MAT_PAINT
}

/** A section's outline as a loop: across the sill, up the right side, over the top, down the left. */
private fun outline(st: Station): List<FloatArray> {
    val out = ArrayList<FloatArray>()
    val z = st.z
    out += floatArrayOf(-st.p[0][0], st.p[0][1], z)
    for (k in 0 until st.p.size - 1) out += floatArrayOf(st.p[k][0], st.p[k][1], z)
    out += floatArrayOf(0f, st.p[st.p.size - 1][1], z)
    for (k in st.p.size - 2 downTo 1) out += floatArrayOf(-st.p[k][0], st.p[k][1], z)
    return out
}

fun buildCarMesh(): List<CarQuad> {
    val quads = ArrayList<CarQuad>()
    // [inside] is a point within the solid: the normal is turned to point away from it
    fun add(
        pts: List<FloatArray>, mat: Int, gloss: Float, alpha: Float, inside: FloatArray?,
        uvs: List<FloatArray>? = null, normal: FloatArray? = null
    ): CarQuad {
        val p = if (pts.size == 3) arrayOf(pts[0], pts[1], pts[2], pts[2]) else pts.toTypedArray()
        val u = uvs?.let { if (it.size == 3) arrayOf(it[0], it[1], it[2], it[2]) else it.toTypedArray() }
        val mid = FloatArray(3) { a -> (p[0][a] + p[1][a] + p[2][a] + p[3][a]) / 4 }
        var n = normal
        if (n == null) {
            // From the diagonals, which also holds for a quad a little out of plane
            n = if (pts.size == 3) norm3(cross3(sub3(p[1], p[0]), sub3(p[2], p[0])))
            else norm3(cross3(sub3(p[2], p[0]), sub3(p[3], p[1])))
            if (inside != null && dot3(n, sub3(mid, inside)) < 0) n = floatArrayOf(-n[0], -n[1], -n[2])
        }
        return CarQuad(p, u, n, mid, mat, gloss, alpha).also { quads += it }
    }

    // The shell, station to station, both sides
    for (i in 0 until STATIONS.size - 1) {
        val a = STATIONS[i]
        val b = STATIONS[i + 1]
        val kind = INTERVALS[i]
        val inside = floatArrayOf(0f, 0.55f, (a.z + b.z) / 2)
        for (k in 0 until 5) {
            val mat = faceMaterial(kind, k)
            val gloss = if (mat == MAT_GLASS) 1f else 0.85f
            val alpha = if (mat == MAT_GLASS) 0.5f else 1f
            for (sx in intArrayOf(1, -1)) {
                val p0 = floatArrayOf(sx * a.p[k][0], a.p[k][1], a.z)
                val p1 = floatArrayOf(sx * a.p[k + 1][0], a.p[k + 1][1], a.z)
                val p2 = floatArrayOf(sx * b.p[k + 1][0], b.p[k + 1][1], b.z)
                val p3 = floatArrayOf(sx * b.p[k][0], b.p[k][1], b.z)
                val q = add(listOf(p0, p1, p2, p3), mat, gloss, alpha, inside)
                q.grid = intArrayOf(sx, if (k <= 1) 0 else 1, i, k, i, k + 1, i + 1, k + 1, i + 1, k)
            }
        }
        // The floor, dark
        val fl = listOf(
            floatArrayOf(-a.p[0][0], a.p[0][1], a.z), floatArrayOf(a.p[0][0], a.p[0][1], a.z),
            floatArrayOf(b.p[0][0], b.p[0][1], b.z), floatArrayOf(-b.p[0][0], b.p[0][1], b.z)
        )
        add(fl, MAT_TRIM, 0f, 1f, inside)
    }

    // Round the shell: each corner takes the mean normal of the faces around it in its group, the
    // sides as one and the top as another, so the light rolls over the body and breaks at the
    // shoulder line. The roof's middle is shared by both sides.
    val sums = HashMap<Long, FloatArray>()
    fun key(g: IntArray, c: Int): Long {
        val ci = g[2 + c * 2]
        val ck = g[3 + c * 2]
        val sx = if (ck == 5) 0 else g[0]
        return (((sx + 1).toLong() * 4 + g[1]) * 64 + ci) * 64 + ck
    }
    for (q in quads) {
        val g = q.grid ?: continue
        for (c in 0 until 4) {
            val acc = sums.getOrPut(key(g, c)) { FloatArray(3) }
            acc[0] += q.n[0]; acc[1] += q.n[1]; acc[2] += q.n[2]
        }
    }
    for (q in quads) {
        val g = q.grid ?: continue
        q.corners = Array(4) { c -> norm3(sums.getValue(key(g, c))) }
    }

    // The tail: a fan over the first section, painted with the tail texture
    val tailPts = outline(STATIONS[0])
    fun tailUV(p: FloatArray) = floatArrayOf((p[0] + 1) / 2, (TAIL_TOP - p[1]) * 512 / CAR_TEX)
    val centre = floatArrayOf(0f, 0.56f, 0f)
    for (i in tailPts.indices) {
        val p = tailPts[i]
        val q = tailPts[(i + 1) % tailPts.size]
        add(listOf(centre, p, q), MAT_TAIL, 0.75f, 1f, null, listOf(tailUV(centre), tailUV(p), tailUV(q)), floatArrayOf(0f, 0f, -1f))
    }
    // The nose, never really seen: it closes the shell
    val last = STATIONS[STATIONS.size - 1]
    val nosePts = outline(last)
    val nc = floatArrayOf(0f, 0.44f, last.z)
    for (i in nosePts.indices) {
        add(listOf(nc, nosePts[i], nosePts[(i + 1) % nosePts.size]), MAT_PAINT, 0.85f, 1f, null, null, floatArrayOf(0f, 0f, 1f))
    }

    // Mirrors on short stalks by the front of the side windows: the back, the outer side, the top
    for (sx in intArrayOf(1, -1)) {
        val x0 = sx * 0.88f
        val x1 = sx * 0.99f
        val y0 = 0.885f
        val y1 = 0.945f
        val z0 = 2.5f
        val z1 = 2.6f
        val inside = floatArrayOf((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
        add(listOf(floatArrayOf(x0, y0, z0), floatArrayOf(x1, y0, z0), floatArrayOf(x1, y1, z0), floatArrayOf(x0, y1, z0)), MAT_PAINT, 0.85f, 1f, inside)
        add(listOf(floatArrayOf(x1, y0, z0), floatArrayOf(x1, y0, z1), floatArrayOf(x1, y1, z1), floatArrayOf(x1, y1, z0)), MAT_PAINT, 0.85f, 1f, inside)
        add(listOf(floatArrayOf(x0, y1, z0), floatArrayOf(x1, y1, z0), floatArrayOf(x1, y1, z1), floatArrayOf(x0, y1, z1)), MAT_PAINT, 0.85f, 1f, inside)
    }

    for (front in booleanArrayOf(false, true)) for (sx in intArrayOf(1, -1)) quads += buildWheel(front, sx)
    return quads
}

/** The four wheels: a tyre of 18 flats with round normals, and the wheel face on the outer side. */
private fun buildWheel(front: Boolean, sx: Int): List<CarQuad> {
    val n = 18
    val quads = ArrayList<CarQuad>()
    val zc = if (front) CarSpec.FRONT_AXLE else CarSpec.REAR_AXLE
    val xc = sx * CarSpec.WHEEL_X
    val r = CarSpec.TYRE_R
    val w = CarSpec.TYRE_W
    val xo = xc + sx * w / 2
    val xi = xc - sx * w / 2
    for (i in 0 until n) {
        val a0 = (i.toFloat() / n * PI * 2).toFloat()
        val a1 = ((i + 1).toFloat() / n * PI * 2).toFloat()
        val c0 = cos(a0); val s0 = sin(a0); val c1 = cos(a1); val s1 = sin(a1)
        fun p(x: Float, c: Float, s: Float) = floatArrayOf(x, r + r * s, zc + r * c)
        val n0 = floatArrayOf(0f, s0, c0)
        val n1 = floatArrayOf(0f, s1, c1)
        val am = (a0 + a1) / 2
        val tread = CarQuad(
            arrayOf(p(xi, c0, s0), p(xi, c1, s1), p(xo, c1, s1), p(xo, c0, s0)), null,
            floatArrayOf(0f, sin(am), cos(am)), floatArrayOf(xc, r + r * sin(am), zc + r * cos(am)),
            MAT_TYRE, 0.12f, 1f
        )
        tread.corners = arrayOf(n0, n1, n1, n0)
        quads += tread
        // The face: a fan from the hub, its texture turned by the wheel's angle while driving
        val hub = floatArrayOf(xo, r, zc)
        val f0 = floatArrayOf(xo, r + r * s0, zc + r * c0)
        val f1 = floatArrayOf(xo, r + r * s1, zc + r * c1)
        val face = CarQuad(arrayOf(hub, f0, f1, f1), null, floatArrayOf(sx.toFloat(), 0f, 0f), hub, MAT_WHEEL, 0.5f, 1f)
        face.face = floatArrayOf(a0, a1)
        quads += face
    }
    return quads
}

// --- The texture -------------------------------------------------------------------------------

/** The tail in tail-texture pixels: x -1..1 m over 1024 px, y from TAIL_TOP down at 512 px a metre. */
private fun tx(x: Float) = (x + 1) * 512
private fun ty(y: Float) = (TAIL_TOP - y) * 512

private fun tailOutline(scale: Float): Path = Path().apply {
    outline(STATIONS[0]).forEachIndexed { i, q ->
        val x = tx(q[0]) * scale
        val y = ty(q[1]) * scale
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A rectangle in metres on the tail, in pixels at the given scale. */
private fun tailRect(x0: Float, y0: Float, x1: Float, y1: Float, r: Float, s: Float = 1f): Path =
    roundRect(tx(x0) * s, ty(y1) * s, (tx(x1) - tx(x0)) * s, (ty(y0) - ty(y1)) * s, r * s)

private fun paintTail(c: Canvas, fonts: DriveFonts) {
    c.save()
    c.clipPath(tailOutline(1f))
    // The light panel: a band of black glass right across, the lights behind it
    c.drawRect(0f, ty(0.842f), 1024f, ty(0.692f), Paint().apply {
        shader = LinearGradient(
            0f, ty(0.84f), 0f, ty(0.69f),
            intArrayOf(rgba(22, 18, 32), rgba(9, 8, 14), rgba(5, 4, 9)), floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    })
    c.drawRect(0f, ty(0.842f), 1024f, ty(0.842f) + 2, fill(rgba(190, 175, 235, 0.45)))
    c.drawRect(0f, ty(0.692f) - 2, 1024f, ty(0.692f), fill(rgba(255, 255, 255, 0.10)))
    // The bar and the clusters, dark red while unlit
    c.drawPath(tailRect(-0.6f, 0.762f, 0.6f, 0.771f, 2f), fill(rgba(70, 10, 20)))
    for (sx in intArrayOf(-1, 1)) {
        for (k in 0 until 3) {
            val y0 = 0.744f + k * 0.026f
            val xa = if (sx > 0) 0.62f else -0.86f
            val xb = if (sx > 0) 0.86f else -0.62f
            c.drawPath(tailRect(xa, y0, xb, y0 + 0.017f, 3f), fill(rgba(105, 14, 26)))
            c.drawRect(tx(xa), ty(y0 + 0.017f), tx(xb), ty(y0 + 0.017f) + 2, fill(rgba(255, 120, 140, 0.25)))
        }
        // The indicators under them
        c.drawPath(tailRect(if (sx > 0) 0.66f else -0.80f, 0.708f, if (sx > 0) 0.80f else -0.66f, 0.724f, 3f), fill(rgba(96, 58, 16)))
    }
    // Our name, small and silver, in the middle of the panel
    c.textMiddle("NEON TOKYO", 512f, ty(0.716f), latinPaint(fonts.medium, 15f, 7f, rgba(205, 195, 235, 0.6)))

    // A crease across the body, catching a line of light
    c.drawRect(tx(-0.84f), ty(0.625f), tx(0.84f), ty(0.625f) + 2, fill(rgba(255, 255, 255, 0.09)))
    c.drawRect(tx(-0.84f), ty(0.625f) + 2, tx(0.84f), ty(0.625f) + 5, fill(rgba(0, 0, 0, 0.3)))

    // The plate in its recess: white and green as in Japan, with a little joke on it
    c.drawPath(tailRect(-0.195f, 0.43f, 0.195f, 0.622f, 10f), fill(rgba(0, 0, 0, 0.38)))
    c.drawPath(tailRect(-0.165f, 0.452f, 0.165f, 0.602f, 7f), fill(rgba(234, 238, 230)))
    c.drawPath(tailRect(-0.158f, 0.459f, 0.158f, 0.595f, 5f), stroke(rgba(28, 104, 58), 3f))
    val green = rgba(22, 92, 50)
    c.textMiddle("ネオン 東京", 512f, ty(0.578f), textPaint(fonts.jp, 15f, green))
    c.textMiddle("BRB 88", 512f, ty(0.513f), textPaint(fonts.medium, 34f, green))
    // Its lamp above
    c.drawPath(tailRect(-0.06f, 0.607f, 0.06f, 0.616f, 2f), fill(rgba(40, 38, 50)))

    // Reflectors low on the corners
    val reflector = fill(rgba(110, 16, 28))
    c.drawPath(tailRect(0.66f, 0.40f, 0.80f, 0.418f, 3f), reflector)
    c.drawPath(tailRect(-0.80f, 0.40f, -0.66f, 0.418f, 3f), reflector)

    // The diffuser: black, finned, the rain light in its middle
    c.drawPath(tailRect(-0.64f, 0.17f, 0.64f, 0.385f, 18f), fill(rgba(6, 5, 10)))
    c.drawRect(tx(-0.62f), ty(0.385f), tx(0.62f), ty(0.385f) + 2, fill(rgba(255, 255, 255, 0.07)))
    val fin = stroke(rgba(80, 70, 110, 0.55), 3f)
    for (k in -4..4) {
        if (k == 0) continue
        val x = tx(k * 0.12f)
        c.drawLine(x, ty(0.37f), x, ty(0.2f), fin)
    }
    c.drawPath(tailRect(-0.035f, 0.30f, 0.035f, 0.326f, 3f), fill(rgba(90, 12, 22)))
    // Exhausts, wide and oval
    for (sx in intArrayOf(-1, 1)) {
        val cx = tx(sx * 0.46f)
        val cy = ty(0.285f)
        c.drawOval(RectF(cx - 46, cy - 22, cx + 46, cy + 22), fill(rgba(120, 116, 134)))
        c.drawOval(RectF(cx - 40, cy - 17, cx + 40, cy + 17), fill(rgba(48, 46, 58)))
        c.drawOval(RectF(cx - 34, cy + 2 - 13, cx + 34, cy + 2 + 13), fill(rgba(4, 3, 6)))
    }
    c.restore()
}

/** What glows on the tail all the time, at half scale: the bar, the clusters and the plate lamp. */
private fun paintTailLights(c: Canvas) {
    val s = 0.5f
    c.drawRect(0f, 0f, 512f, 192f, fill(0xFF000000.toInt()))
    c.save()
    c.clipPath(tailOutline(s))
    c.drawPath(tailRect(-0.6f, 0.762f, 0.6f, 0.771f, 2f, s), fill(rgba(255, 46, 66)).glow(5f, rgba(255, 30, 60)))
    for (sx in intArrayOf(-1, 1)) {
        for (k in 0 until 3) {
            val y0 = 0.744f + k * 0.026f
            val xa = if (sx > 0) 0.62f else -0.86f
            val xb = if (sx > 0) 0.86f else -0.62f
            val col = if (k == 1) rgba(255, 70, 90) else rgba(235, 26, 48)
            c.drawPath(tailRect(xa, y0, xb, y0 + 0.017f, 3f, s), fill(col).glow(5f, rgba(255, 30, 60)))
        }
    }
    // The plate, lit from above
    c.drawPath(tailRect(-0.165f, 0.452f, 0.165f, 0.602f, 7f, s), Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, ty(0.61f) * s, 0f, ty(0.45f) * s,
            intArrayOf(rgba(255, 255, 255, 0.26), rgba(255, 255, 255, 0.1)), floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    })
    c.drawPath(tailRect(-0.06f, 0.607f, 0.06f, 0.616f, 2f, s), fill(rgba(255, 255, 250)))
    c.restore()
}

/** Masks for what switches: red the brake lights, green and blue the left and right indicators. */
private fun paintTailMasks(c: Canvas) {
    val s = 0.5f
    c.drawRect(0f, 0f, 512f, 192f, fill(0xFF000000.toInt()))
    c.save()
    c.clipPath(tailOutline(s))
    val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    val red = fill(rgba(255, 0, 0)).glow(4f, rgba(255, 0, 0)).apply { xfermode = add }
    for (sx in intArrayOf(-1, 1)) {
        for (k in 0 until 3) {
            val y0 = 0.744f + k * 0.026f
            c.drawPath(tailRect(if (sx > 0) 0.62f else -0.86f, y0, if (sx > 0) 0.86f else -0.62f, y0 + 0.017f, 3f, s), red)
        }
    }
    c.drawPath(tailRect(-0.035f, 0.30f, 0.035f, 0.326f, 3f, s), red)
    c.drawPath(tailRect(-0.6f, 0.762f, 0.6f, 0.771f, 2f, s), red)
    c.drawPath(tailRect(-0.80f, 0.708f, -0.66f, 0.724f, 3f, s), fill(rgba(0, 255, 0)).glow(4f, rgba(0, 255, 0)).apply { xfermode = add })
    c.drawPath(tailRect(0.66f, 0.708f, 0.80f, 0.724f, 3f, s), fill(rgba(0, 0, 255)).glow(4f, rgba(0, 0, 255)).apply { xfermode = add })
    c.restore()
}

/**
 * The far card in the cabin: the dashboard with its glowing clocks, and the street ahead through
 * the windscreen, drawn pale so the district's colour can tint it.
 */
private fun paintDashCard(c: Canvas, w: Int, h: Int) {
    val (x0, x1, y0, y1) = CARD_DASH
    fun cx(x: Float) = (x - x0) / (x1 - x0) * w
    fun cy(y: Float) = (y1 - y) / (y1 - y0) * h
    // Only what the glasshouse would show: narrower at the roof than at the shoulders
    c.clipPath(Path().apply {
        moveTo(cx(-0.54f), cy(1.135f)); lineTo(cx(0.54f), cy(1.135f)); lineTo(cx(0.71f), cy(0.62f)); lineTo(cx(-0.71f), cy(0.62f))
        close()
    })
    // Through the windscreen: the lit street, brightest low down, with soft lights of cars ahead
    c.drawRect(0f, 0f, w.toFloat(), cy(0.84f), Paint().apply {
        shader = LinearGradient(
            0f, cy(1.14f), 0f, cy(0.86f),
            intArrayOf(rgba(70, 64, 92), rgba(150, 138, 176), rgba(210, 196, 226)), floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    })
    // Each light: x, y in metres, radius in pixels, and its colour
    val bokeh = listOf(
        floatArrayOf(-0.42f, 0.9f, 9f) to rgba(255, 90, 110, 0.9),
        floatArrayOf(-0.3f, 0.905f, 9f) to rgba(255, 90, 110, 0.9),
        floatArrayOf(0.18f, 0.93f, 7f) to rgba(255, 255, 255, 0.7),
        floatArrayOf(0.46f, 0.95f, 11f) to rgba(120, 240, 255, 0.8),
        floatArrayOf(-0.05f, 0.99f, 14f) to rgba(255, 160, 90, 0.6)
    )
    for ((xyr, col) in bokeh) {
        val bx = cx(xyr[0])
        val by = cy(xyr[1])
        val r = xyr[2]
        c.drawRect(bx - r * 3, by - r * 3, bx + r * 3, by + r * 3, Paint().apply {
            shader = RadialGradient(bx, by, r * 2.2f, intArrayOf(col, 0), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        })
    }
    // The dash, and the screen pillars either side
    val dark = fill(rgba(8, 7, 13))
    c.drawPath(Path().apply {
        moveTo(0f, cy(0.88f))
        cubicTo(w * 0.3f, cy(0.9f), w * 0.7f, cy(0.9f), w.toFloat(), cy(0.88f))
        lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close()
    }, dark)
    c.drawPath(Path().apply { moveTo(0f, 0f); lineTo(cx(-0.6f), 0f); lineTo(cx(-0.52f), cy(0.88f)); lineTo(0f, cy(0.88f)); close() }, dark)
    c.drawPath(Path().apply { moveTo(w.toFloat(), 0f); lineTo(cx(0.6f), 0f); lineTo(cx(0.52f), cy(0.88f)); lineTo(w.toFloat(), cy(0.88f)); close() }, dark)
    // A soft pink line of light along the top of the dash
    c.drawRect(cx(-0.48f), cy(0.885f), cx(0.48f), cy(0.885f) + 2, fill(rgba(255, 80, 170, 0.7)))
    // The clocks in front of the driver, and the steering wheel's rim against them
    val gx = cx(0.36f)
    val gy = cy(0.84f)
    c.drawRect(gx - 50, gy - 50, gx + 50, gy + 50, Paint().apply {
        shader = radial(gx, gy, 2f, 46f, intArrayOf(rgba(120, 250, 255, 0.95), rgba(60, 200, 255, 0.4), 0), floatArrayOf(0f, 0.5f, 1f))
    })
    val wheelY = cy(0.8f)
    c.drawArc(RectF(gx - 48, wheelY - 40, gx + 48, wheelY + 40), 194.4f, 151.2f, false, stroke(rgba(6, 5, 10), 9f))
    // The mirror hanging from the top of the screen
    c.drawPath(roundRect(cx(-0.09f), cy(1.1f), cx(0.09f) - cx(-0.09f), 14f, 6f), fill(rgba(10, 9, 15)))
}

/** The near card: two bucket seats, the driver's on the right, with a rim of the dash's light. */
private fun paintSeatsCard(c: Canvas, w: Int, h: Int) {
    val (x0, x1, y0, y1) = CARD_SEATS
    fun cx(x: Float) = (x - x0) / (x1 - x0) * w
    fun cy(y: Float) = (y1 - y) / (y1 - y0) * h
    for (sx in intArrayOf(-1, 1)) {
        val mx = cx(sx * 0.36f)
        val top = cy(0.965f)
        val shoulder = cy(0.84f)
        val hw = cx(0.2f) - cx(0f)
        val sw = cx(0.24f) - cx(0f)
        val seat = Path().apply {
            moveTo(mx - hw * 0.62f, top + 16)
            cubicTo(mx - hw * 0.62f, top - 4, mx + hw * 0.62f, top - 4, mx + hw * 0.62f, top + 16)
            lineTo(mx + hw * 0.5f, shoulder - 14)
            cubicTo(mx + sw, shoulder - 8, mx + sw, shoulder + 10, mx + sw * 1.02f, h.toFloat())
            lineTo(mx - sw * 1.02f, h.toFloat())
            cubicTo(mx - sw, shoulder + 10, mx - sw, shoulder - 8, mx - hw * 0.5f, shoulder - 14)
            close()
        }
        c.drawPath(seat, fill(rgba(13, 11, 19)))
        c.drawPath(seat, stroke(rgba(120, 100, 160, 0.55), 2f))
        // The slot for the harness, a racer's touch
        c.drawPath(roundRect(mx - 10, cy(0.9f), 20f, 26f, 8f), fill(rgba(4, 3, 7)))
    }
}

/** The driver from behind: head and shoulders, dark, with a cool rim of light from the dash. */
private fun paintDriver(c: Canvas, w: Int, h: Int) {
    val (x0, x1, y0, y1) = CARD_DRIVER
    fun cx(x: Float) = (x - x0) / (x1 - x0) * w
    fun cy(y: Float) = (y1 - y) / (y1 - y0) * h
    val mx = cx(0.36f)
    val head = Path().apply { addOval(RectF(mx - 40, cy(0.985f) - 52, mx + 40, cy(0.985f) + 52), Path.Direction.CW) }
    val hair = Path().apply {
        arcTo(RectF(mx - 44, cy(1.0f) - 50, mx + 44, cy(1.0f) + 50), 180f, 180f, false)
        arcTo(RectF(mx - 44, cy(0.99f) - 30, mx + 44, cy(0.99f) + 30), 0f, 180f, false)
        close()
    }
    fun shoulders(path: Path, inset: Float, dy: Float) {
        path.moveTo(mx - 118 + inset, h.toFloat())
        path.cubicTo(mx - 112 + inset, cy(0.86f) + dy, mx - 70, cy(0.85f) + dy, mx - 24, cy(0.86f) + dy)
        path.lineTo(mx + 24, cy(0.86f) + dy)
        path.cubicTo(mx + 70, cy(0.85f) + dy, mx + 112 - inset, cy(0.86f) + dy, mx + 118 - inset, h.toFloat())
    }
    val body = Path().apply { shoulders(this, 0f, 0f); close() }
    val dark = fill(rgba(5, 4, 9))
    c.drawPath(body, dark)
    c.drawRect(mx - 17, cy(0.92f), mx + 17, cy(0.85f), dark)
    c.drawPath(head, dark)
    c.drawPath(hair, dark)
    // The rim: drawn white, the shader tints it
    c.save()
    c.clipPath(hair)
    c.drawArc(RectF(mx + 2 - 44, cy(1.0f) + 3 - 50, mx + 2 + 44, cy(1.0f) + 3 + 50), 189f, 162f, false, stroke(rgba(255, 255, 255, 0.6), 5f))
    c.restore()
    c.save()
    c.clipPath(body)
    c.drawPath(Path().apply { shoulders(this, 4f, 2f) }, stroke(rgba(255, 255, 255, 0.35), 4f))
    c.restore()
}

/** A wheel face: mesh spokes in gunmetal, a polished lip, a pink caliper behind. */
private fun paintWheel(c: Canvas, w: Int, @Suppress("UNUSED_PARAMETER") h: Int) {
    val m = w / 2f
    val r = w / 2f - 2
    c.drawCircle(m, m, r, fill(rgba(12, 11, 15)))
    val rim = r * 0.7f
    c.drawCircle(m, m, rim, fill(rgba(30, 28, 36)))
    // The brake disc and its caliper
    c.drawCircle(m, m, rim * 0.82f, fill(rgba(58, 56, 64)))
    val deg = (180 / PI).toFloat()
    c.drawPath(Path().apply {
        arcTo(RectF(m - rim * 0.8f, m - rim * 0.8f, m + rim * 0.8f, m + rim * 0.8f), -2.5f * deg, 0.8f * deg, false)
        arcTo(RectF(m - rim * 0.55f, m - rim * 0.55f, m + rim * 0.55f, m + rim * 0.55f), -1.7f * deg, -0.8f * deg, false)
        close()
    }, fill(rgba(255, 60, 150)))
    // Spokes: a cross-laced mesh
    val spoke = stroke(rgba(84, 82, 96), 4f)
    for (k in 0 until 10) {
        val a = (k / 10.0 * PI * 2).toFloat()
        val ix = m + cos(a) * rim * 0.22f
        val iy = m + sin(a) * rim * 0.22f
        c.drawLine(ix, iy, m + cos(a + 0.5f) * rim * 0.95f, m + sin(a + 0.5f) * rim * 0.95f, spoke)
        c.drawLine(ix, iy, m + cos(a - 0.5f) * rim * 0.95f, m + sin(a - 0.5f) * rim * 0.95f, spoke)
    }
    c.drawCircle(m, m, rim * 0.97f, stroke(rgba(176, 172, 190), 6f))
    c.drawCircle(m, m, rim * 0.2f, fill(rgba(140, 136, 150)))
    c.drawCircle(m, m, rim * 0.1f, fill(rgba(20, 18, 26)))
}

/** Paints [draw] into its slot of the car texture, clipped to it. */
private fun slot(c: Canvas, rect: IntArray, draw: (Canvas, Int, Int) -> Unit) {
    c.save()
    c.translate(rect[0].toFloat(), rect[1].toFloat())
    c.clipRect(0, 0, rect[2], rect[3])
    draw(c, rect[2], rect[3])
    c.restore()
}

fun buildCarTexture(fonts: DriveFonts): Bitmap {
    val bitmap = Bitmap.createBitmap(CAR_TEX, CAR_TEX, Bitmap.Config.ARGB_8888)
    val c = Canvas(bitmap)
    slot(c, CarUV.TAIL) { g, _, _ -> paintTail(g, fonts) }
    slot(c, CarUV.LIGHTS) { g, _, _ -> paintTailLights(g) }
    slot(c, CarUV.MASKS) { g, _, _ -> paintTailMasks(g) }
    slot(c, CarUV.DASH, ::paintDashCard)
    slot(c, CarUV.SEATS, ::paintSeatsCard)
    slot(c, CarUV.DRIVER, ::paintDriver)
    slot(c, CarUV.WHEEL, ::paintWheel)
    return bitmap
}
