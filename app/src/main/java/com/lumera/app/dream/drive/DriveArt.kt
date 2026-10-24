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
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Everything the drive draws that is not worked out in a shader: the sprite atlas (signs, glows,
// lamps, other cars, lanterns, palms), the far skylines and a noise tile. Signs and glows are
// drawn white and tinted while drawing, so one sprite serves every colour.

/** The typefaces the art and the clock are drawn in. */
class DriveFonts(
    /** M PLUS Rounded 1c Bold, cut down to the characters of [DriveContent] */
    val jp: Typeface,
    val medium: Typeface,
    val mediumItalic: Typeface,
    val semiboldItalic: Typeface
)

/** Where a sprite sits in its texture, and its size in pixels. */
class Sprite(val u0: Float, val v0: Float, val u1: Float, val v1: Float, val w: Int, val h: Int)

/** A sign's sprite and its size in metres. */
class SignArt(val sprite: Sprite, val wm: Double, val hm: Double)

class DriveArt(
    val atlas: Atlas,
    val vertical: List<SignArt>,
    val horizontal: List<SignArt>,
    val gantry: List<Sprite>,
    val screens: List<Sprite>,
    val car: Bitmap,
    val noise: Bitmap,
    val skylineFar: Bitmap,
    val skylineNear: Bitmap
) {
    fun recycle() {
        atlas.bitmap.recycle()
        car.recycle()
        noise.recycle()
        skylineFar.recycle()
        skylineNear.recycle()
    }

    companion object {
        fun build(fonts: DriveFonts): DriveArt {
            val atlas = Atlas(ATLAS_SIZE)
            atlas.add("solid", 16, 16) { c, w, h -> c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill(0xFFFFFFFF.toInt())) }
            atlas.add("glow", 128, 128, ::drawRoundGlow)
            atlas.add("streak", 48, 256, ::drawStreak)
            atlas.add("flare", 256, 48, ::drawFlare)
            atlas.add("dot", 32, 32, ::drawDot)
            atlas.add("beam", 32, 16, ::drawBeam)
            atlas.add("cone", 64, 128, ::drawCone)
            atlas.add("screen", 128, 72, ::drawScreenGlass)
            atlas.add("rain", 8, 96, ::drawRain)
            atlas.add("lamp", 120, 28, ::drawLampHead)
            atlas.add("lantern", 52, 72, ::drawLantern)
            atlas.add("crosswalk", 64, 16, ::drawCrosswalk)
            atlas.add("trafficRear", 192, 128, ::drawTrafficRear)
            atlas.add("trafficFront", 192, 128, ::drawTrafficFront)
            atlas.add("flyer", 200, 80, ::drawFlyer)
            atlas.add("palm", 200, 350, ::drawPalm)

            // Tallest signs first, so the rows of the atlas pack tight
            atlas.newRow()
            val vertical = DriveContent.vertical.withIndex()
                .sortedByDescending { it.value.length }
                .map { (i, text) ->
                    val w = SIGN_WIDTH + 2 * SIGN_PAD
                    val h = text.length * SIGN_CHAR + 24 + 2 * SIGN_PAD
                    val sprite = atlas.add("v$i", w, h) { c, ww, hh -> drawVerticalSign(c, ww, hh, text, i % 3, fonts) }
                    SignArt(sprite, w / SIGN_PPM, h / SIGN_PPM)
                }
            atlas.newRow()
            val horizontal = DriveContent.horizontal.mapIndexed { i, text ->
                val bh = 80
                val measure = latinPaint(fonts.semiboldItalic, (bh * 0.56f).roundToInt().toFloat(), 3f)
                val w = ceil(measure.measureText(text) + bh * 0.9f).toInt() + 2 * SIGN_PAD
                val h = bh + 2 * SIGN_PAD
                val sprite = atlas.add("h$i", w, h) { c, ww, hh -> drawHorizontalSign(c, ww, hh, text, i % 2, fonts) }
                SignArt(sprite, w / SIGN_PPM, h / SIGN_PPM)
            }
            atlas.newRow()
            val gantry = DriveContent.gantry.mapIndexed { i, (jp, en) ->
                atlas.add("g$i", 500, 125) { c, w, h -> drawGantryBoard(c, w, h, jp, en, fonts) }
            }
            atlas.newRow()
            val screens = DriveContent.screens.mapIndexed { i, (jp, en) ->
                atlas.add("s$i", 256, 128) { c, w, h -> drawScreenWords(c, w, h, jp, en, fonts) }
            }
            return DriveArt(
                atlas, vertical, horizontal, gantry, screens,
                car = buildCarTexture(fonts),
                noise = buildNoise(),
                skylineFar = buildSkyline(11, 2048, 512, near = false),
                skylineNear = buildSkyline(23, 2048, 512, near = true)
            )
        }
    }
}

const val ATLAS_SIZE = 2048
private const val SIGN_PAD = 18
private const val SIGN_CHAR = 80
private const val SIGN_WIDTH = 92
/** Pixels of a sign sprite per metre in the city. */
private const val SIGN_PPM = 92.0

/** Rows of sprites in one big texture. Each sprite keeps a gap around it for the mipmaps. */
class Atlas(val size: Int) {
    val bitmap: Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private var x = 0
    private var y = 0
    private var rowH = 0
    private val gap = 10
    val sprites = HashMap<String, Sprite>()

    operator fun get(name: String): Sprite = sprites.getValue(name)

    fun newRow() {
        if (x > 0) {
            x = 0
            y += rowH + gap
            rowH = 0
        }
    }

    fun add(name: String, w: Int, h: Int, draw: (Canvas, Int, Int) -> Unit): Sprite {
        if (x + w > size) {
            x = 0
            y += rowH + gap
            rowH = 0
        }
        check(y + h <= size) { "atlas full at $name" }
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.clipRect(0, 0, w, h)
        draw(canvas, w, h)
        canvas.restore()
        val s = size.toFloat()
        val sprite = Sprite((x + 0.5f) / s, (y + 0.5f) / s, (x + w - 0.5f) / s, (y + h - 0.5f) / s, w, h)
        sprites[name] = sprite
        x += w + gap
        rowH = max(rowH, h)
        return sprite
    }
}

// --- Canvas helpers ----------------------------------------------------------------------------

fun fill(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    this.color = color
}

fun stroke(color: Int, width: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = width
    this.color = color
}

/** A glow under what the paint draws, as a web canvas's shadowBlur of [blur] pixels would give. */
fun Paint.glow(blur: Float, color: Int): Paint {
    setShadowLayer(max(0.5f, (blur / 2f - 0.5f) / 0.57735f), 0f, 0f, color)
    return this
}

fun textPaint(face: Typeface, size: Float, color: Int = 0xFFFFFFFF.toInt()): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = face
        textSize = size
        textAlign = Paint.Align.CENTER
        this.color = color
    }

/** Latin lettering with [spacing] pixels between the letters. */
fun latinPaint(face: Typeface, size: Float, spacing: Float, color: Int = 0xFFFFFFFF.toInt()): Paint =
    textPaint(face, size, color).apply { letterSpacing = spacing / size }

/** Draws text centred on (x, y), its middle on y as a web canvas's "middle" baseline does. */
fun Canvas.textMiddle(text: String, x: Float, y: Float, paint: Paint) {
    val fm = paint.fontMetrics
    drawText(text, x, y - (fm.ascent + fm.descent) / 2f, paint)
}

fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float): Path =
    Path().apply { addRoundRect(RectF(x, y, x + w, y + h), r, r, Path.Direction.CW) }

/**
 * A radial gradient between circles of radius r0 and r1 round one centre, with canvas-style
 * stops: Android's own starts at the centre, so the stops are moved out to match.
 */
fun radial(cx: Float, cy: Float, r0: Float, r1: Float, colors: IntArray, stops: FloatArray): Shader {
    if (r0 <= 0f) return RadialGradient(cx, cy, r1, colors, stops, Shader.TileMode.CLAMP)
    val c = IntArray(colors.size + 1)
    val s = FloatArray(stops.size + 1)
    c[0] = colors[0]
    s[0] = 0f
    for (i in colors.indices) {
        c[i + 1] = colors[i]
        s[i + 1] = (r0 + stops[i] * (r1 - r0)) / r1
    }
    return RadialGradient(cx, cy, r1, c, s, Shader.TileMode.CLAMP)
}

/** White pixels with the alpha [alpha] gives for each, drawn at the slot's corner. */
private fun Canvas.whitePixels(w: Int, h: Int, alpha: (Int, Int) -> Double) {
    val px = IntArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        val a = (clamp(alpha(x, y), 0.0, 1.0) * 255 + 0.5).toInt()
        px[y * w + x] = (a shl 24) or 0xFFFFFF
    }
    val b = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    drawBitmap(b, 0f, 0f, null)
    b.recycle()
}

private const val WHITE = 0xFFFFFFFF.toInt()

// --- Glows -------------------------------------------------------------------------------------

private fun drawRoundGlow(c: Canvas, w: Int, h: Int) {
    // Close to a gaussian: a hot middle and a long soft tail
    val p = Paint().apply {
        shader = RadialGradient(
            w / 2f, h / 2f, w / 2f,
            intArrayOf(rgba(255, 255, 255, 1.0), rgba(255, 255, 255, 0.72), rgba(255, 255, 255, 0.32), rgba(255, 255, 255, 0.1), rgba(255, 255, 255, 0.025), rgba(255, 255, 255, 0.0)),
            floatArrayOf(0f, 0.12f, 0.3f, 0.55f, 0.8f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
}

/** A reflection on wet tarmac: narrow, bright where it starts, long and fading. */
private fun drawStreak(c: Canvas, w: Int, h: Int) = c.whitePixels(w, h) { x, y ->
    val t = y / (h - 1.0)
    // Brightest a fifth of the way down, where the light itself is mirrored, smeared both ways
    val along = if (t < 0.2) lerp(0.3, 1.0, smooth(t / 0.2)) else (1 - (t - 0.2) / 0.8).pow(1.7)
    val d = (x - (w - 1) / 2.0) / (w / 2.0)
    along * exp(-d * d * 7)
}

/** The long sideways flare of a headlight seen through a lens. */
private fun drawFlare(c: Canvas, w: Int, h: Int) = c.whitePixels(w, h) { x, y ->
    val dy = (y - (h - 1) / 2.0) / (h / 2.0)
    val dx = (x - (w - 1) / 2.0) / (w / 2.0)
    exp(-dy * dy * 30) * (1 - abs(dx)).pow(2.2) + exp(-(dx * dx * 40 + dy * dy * 40)) * 0.8
}

/** Light across a tube: a soft profile side to side, even along its length. */
private fun drawBeam(c: Canvas, w: Int, h: Int) = c.whitePixels(w, h) { x, _ ->
    val d = (x - (w - 1) / 2.0) / (w / 2.0)
    exp(-d * d * 6) - 0.0025
}

/** The light of a street lamp falling through damp air: bright at the lamp, fading to the ground. */
private fun drawCone(c: Canvas, w: Int, h: Int) = c.whitePixels(w, h) { x, y ->
    val t = y / (h - 1.0)
    val down = (1 - t).pow(1.6) * 0.75 + 0.25 * (1 - t)
    val d = abs((x - (w - 1) / 2.0) / (w / 2.0))
    down * (1 - smooth(clamp((d - 0.35) / 0.65, 0.0, 1.0))) * smooth(t / 0.04)
}

/** A big screen's face, drawn white to be tinted: brightest in the middle, with fine scan lines. */
private fun drawScreenGlass(c: Canvas, w: Int, h: Int) {
    val p = Paint().apply {
        shader = radial(w / 2f, h / 2f, 4f, w * 0.62f, intArrayOf(rgba(255, 255, 255), rgba(150, 150, 150)), floatArrayOf(0f, 1f))
    }
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    val line = fill(rgba(0, 0, 0, 0.16))
    var y = 0
    while (y < h) {
        c.drawRect(0f, y.toFloat(), w.toFloat(), y + 1f, line)
        y += 3
    }
}

/** The words of an advert, white: big Japanese, a spaced line of Latin under it. */
private fun drawScreenWords(c: Canvas, w: Int, h: Int, jp: String, en: String, fonts: DriveFonts) {
    var size = 76f
    val big = textPaint(fonts.jp, size).glow(8f, rgba(255, 255, 255, 0.9))
    while (big.measureText(jp) > w - 24 && size > 30f) {
        size -= 2f
        big.textSize = size
    }
    c.textMiddle(jp, w / 2f, h * 0.42f, big)
    val small = latinPaint(fonts.semiboldItalic, 19f, 4f).glow(4f, rgba(255, 255, 255, 0.9))
    c.textMiddle(en, w / 2f, h * 0.84f, small)
}

private fun drawDot(c: Canvas, w: Int, h: Int) {
    val p = Paint().apply {
        shader = RadialGradient(
            w / 2f, h / 2f, w / 2f,
            intArrayOf(rgba(255, 255, 255, 1.0), rgba(255, 255, 255, 0.9), rgba(255, 255, 255, 0.25), rgba(255, 255, 255, 0.0)),
            floatArrayOf(0f, 0.35f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
}

private fun drawRain(c: Canvas, w: Int, h: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(rgba(255, 255, 255, 0.0), rgba(255, 255, 255, 0.8), rgba(255, 255, 255, 1.0)),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    c.drawRect(w / 2f - 1.5f, 0f, w / 2f + 1.5f, h.toFloat(), p)
}

// --- Signs -------------------------------------------------------------------------------------

/**
 * A vertical sign. Three kinds: a light box (a bright board, dark letters), neon tubes on a dark
 * board, and a coloured board with white letters. All drawn white, so a tint gives the colour.
 */
private fun drawVerticalSign(c: Canvas, w: Int, h: Int, text: String, kind: Int, fonts: DriveFonts) {
    val p = SIGN_PAD.toFloat()
    val bw = w - 2 * p
    val bh = h - 2 * p
    val chars = text.map { it.toString() }
    val x = w / 2f
    val top = p + (bh - chars.size * SIGN_CHAR) / 2f
    val board = roundRect(p, p, bw, bh, 7f)
    val letters = textPaint(fonts.jp, (SIGN_CHAR * 0.74f).roundToInt().toFloat())
    fun letters(paint: Paint) = chars.forEachIndexed { i, ch -> c.textMiddle(ch, x, top + (i + 0.5f) * SIGN_CHAR, paint) }
    when (kind) {
        0 -> {
            c.drawPath(board, fill(rgba(222, 222, 222)).glow(22f, rgba(255, 255, 255, 0.75)))
            c.drawPath(board, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    p, 0f, p + bw, 0f,
                    intArrayOf(rgba(0, 0, 0, 0.12), rgba(255, 255, 255, 0.14), rgba(0, 0, 0, 0.16)),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            })
            c.drawPath(board, stroke(rgba(28, 24, 36), 5f))
            letters.color = rgba(24, 20, 30)
            letters(letters)
        }
        1 -> {
            c.drawPath(board, fill(rgba(14, 11, 20)))
            c.drawPath(roundRect(p + 7, p + 7, bw - 14, bh - 14, 5f), stroke(rgba(255, 255, 255, 0.85), 3.5f).glow(12f, rgba(255, 255, 255, 0.9)))
            letters.glow(18f, rgba(255, 255, 255, 0.9))
            repeat(2) { letters(letters) }
        }
        else -> {
            c.drawPath(board, fill(rgba(150, 150, 150)).glow(18f, rgba(255, 255, 255, 0.55)))
            c.drawPath(board, stroke(rgba(210, 210, 210), 4f))
            letters.glow(8f, rgba(255, 255, 255, 0.8))
            letters(letters)
        }
    }
}

/** A horizontal sign in Kanit: neon script on a dark board, or a light box. */
private fun drawHorizontalSign(c: Canvas, w: Int, h: Int, text: String, kind: Int, fonts: DriveFonts) {
    val p = SIGN_PAD.toFloat()
    val bw = w - 2 * p
    val bh = h - 2 * p
    val letters = latinPaint(fonts.semiboldItalic, (bh * 0.56f).roundToInt().toFloat(), 3f)
    val board = roundRect(p, p, bw, bh, 8f)
    if (kind == 0) {
        c.drawPath(board, fill(rgba(14, 11, 20)))
        letters.glow(16f, rgba(255, 255, 255, 0.9))
        repeat(2) { c.textMiddle(text, w / 2f, h / 2f + 2, letters) }
        c.drawPath(roundRect(p + 6, p + 6, bw - 12, bh - 12, 6f), stroke(rgba(255, 255, 255, 0.8), 3f).glow(10f, rgba(255, 255, 255, 0.9)))
    } else {
        c.drawPath(board, fill(rgba(222, 222, 222)).glow(20f, rgba(255, 255, 255, 0.7)))
        c.drawPath(board, stroke(rgba(28, 24, 36), 5f))
        letters.color = rgba(24, 20, 30)
        c.textMiddle(text, w / 2f, h / 2f + 2, letters)
    }
}

/** A dot-matrix board for the gantries over the road: Japanese above, English below. */
private fun drawGantryBoard(c: Canvas, w: Int, h: Int, jp: String, en: String, fonts: DriveFonts) {
    val p = 14f
    val board = roundRect(p, p, w - 2 * p, h - 2 * p, 6f)
    c.drawPath(board, fill(rgba(10, 9, 14)))
    c.drawPath(board, stroke(rgba(44, 40, 54), 6f))
    // The letters on a bitmap of their own, then let through only on the dots of the matrix
    val words = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    Canvas(words).apply {
        textMiddle(jp, w / 2f, h * 0.39f, textPaint(fonts.jp, (h * 0.4f).roundToInt().toFloat()))
        textMiddle(en, w / 2f, h * 0.76f, latinPaint(fonts.medium, (h * 0.2f).roundToInt().toFloat(), 6f))
    }
    val pitch = 5
    fun dotGrid(radius: Float): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val bc = Canvas(b)
        val dot = fill(WHITE)
        var y = p + 4
        while (y < h - p - 2) {
            var x = p + 4
            while (x < w - p - 2) {
                bc.drawCircle(x, y, radius, dot)
                x += pitch
            }
            y += pitch
        }
        return b
    }
    val lit = dotGrid(1.9f)
    Canvas(lit).drawBitmap(words, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
    // Unlit dots faintly, lit ones bright with a little glow
    val all = dotGrid(1.6f)
    c.drawBitmap(all, 0f, 0f, Paint().apply { alpha = (0.07 * 255).roundToInt() })
    c.drawBitmap(lit, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).glow(6f, rgba(255, 255, 255, 0.8)))
    words.recycle()
    lit.recycle()
    all.recycle()
}

// --- Street furniture --------------------------------------------------------------------------

private fun drawLampHead(c: Canvas, w: Int, h: Int) {
    c.drawPath(roundRect(8f, h / 2f - 5, w - 16f, 10f, 5f), fill(WHITE).glow(8f, WHITE))
}

/** A paper lantern, drawn pale so the tint gives its red or amber. */
private fun drawLantern(c: Canvas, w: Int, h: Int) {
    val cx = w / 2f
    val cy = h / 2f
    val rx = w * 0.3f
    val ry = h * 0.32f
    val oval = RectF(cx - rx, cy - ry, cx + rx, cy + ry)
    c.drawOval(oval, fill(rgba(230, 230, 230)).glow(14f, rgba(255, 255, 255, 0.9)))
    c.drawOval(oval, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = radial(cx - rx * 0.1f, cy - ry * 0.1f, 1f, rx * 1.1f, intArrayOf(rgba(255, 255, 255, 0.5), rgba(0, 0, 0, 0.25)), floatArrayOf(0f, 1f))
    })
    val rib = stroke(rgba(40, 30, 30, 0.35), 1.2f)
    for (k in 1 until 6) {
        val yy = cy - ry + (2 * ry * k) / 6
        val half = rx * sqrt(1 - ((yy - cy) / ry).pow(2))
        c.drawLine(cx - half, yy, cx + half, yy, rib)
    }
    val cap = fill(rgba(20, 16, 18))
    c.drawRect(cx - rx * 0.55f, cy - ry - 4, cx + rx * 0.55f, cy - ry + 2, cap)
    c.drawRect(cx - rx * 0.55f, cy + ry - 2, cx + rx * 0.55f, cy + ry + 4, cap)
}

private fun drawPalm(c: Canvas, w: Int, h: Int) {
    // A neon palm: a dark silhouette with a bright edge, the edge drawn white for the tint
    val bx = w * 0.52f
    val by = h - 6f
    val kx = w * 0.46f
    val ky = h * 0.2f
    val trunk = Path().apply {
        moveTo(bx - 9, by)
        quadTo(w * 0.62f, h * 0.55f, kx - 3, ky)
        lineTo(kx + 5, ky)
        quadTo(w * 0.68f, h * 0.55f, bx + 9, by)
        close()
    }
    val fronds = Path()
    val leaves = arrayOf(
        floatArrayOf(-1f, -0.25f), floatArrayOf(-0.85f, 0.15f), floatArrayOf(-0.55f, 0.45f), floatArrayOf(0.55f, 0.45f),
        floatArrayOf(0.85f, 0.12f), floatArrayOf(1f, -0.22f), floatArrayOf(-0.2f, -0.6f), floatArrayOf(0.25f, -0.55f)
    )
    for ((dx, dy) in leaves) {
        val ex = kx + dx * w * 0.46f
        val ey = ky + dy * h * 0.18f + h * 0.08f
        val mx = kx + dx * w * 0.25f
        val my = ky + dy * h * 0.12f - h * 0.06f
        fronds.moveTo(kx, ky)
        fronds.quadTo(mx, my - 8, ex, ey)
        fronds.quadTo(mx, my + 10, kx, ky + 6)
        fronds.close()
    }
    val dark = fill(rgba(8, 6, 14))
    c.drawPath(trunk, dark)
    c.drawPath(fronds, dark)
    val edge = stroke(rgba(255, 255, 255, 0.95), 3f).glow(10f, rgba(255, 255, 255, 0.95))
    edge.strokeJoin = Paint.Join.ROUND
    c.drawPath(fronds, edge)
    edge.strokeWidth = 2.5f
    c.drawPath(trunk, edge)
}

/** The outline of another car seen from behind or ahead, with its glasshouse cut from the top. */
private fun carBody(w: Int, h: Int, front: Boolean): Path = Path().apply {
    val shoulder = if (front) 0.42f else 0.4f
    val roofX = if (front) 0.27f else 0.28f
    val roofY = if (front) 0.14f else 0.12f
    val crown = if (front) 0.06f else 0.04f
    val hip = if (front) 0.18f else 0.2f
    moveTo(w * 0.06f, h * 0.92f)
    lineTo(w * 0.05f, h * 0.55f)
    quadTo(w * 0.07f, h * (shoulder + 0.02f), w * hip, h * shoulder)
    lineTo(w * roofX, h * roofY)
    quadTo(w * 0.5f, h * crown, w * (1 - roofX), h * roofY)
    lineTo(w * (1 - hip), h * shoulder)
    quadTo(w * 0.93f, h * (shoulder + 0.02f), w * 0.95f, h * 0.55f)
    lineTo(w * 0.94f, h * 0.92f)
    close()
}

/** Another car from behind, small: dark body, red lights, a lit plate. Drawn in its own colours. */
private fun drawTrafficRear(c: Canvas, w: Int, h: Int) {
    c.drawPath(carBody(w, h, front = false), fill(rgba(16, 13, 24)))
    c.drawPath(Path().apply {
        moveTo(w * 0.3f, h * 0.16f)
        quadTo(w * 0.5f, h * 0.1f, w * 0.7f, h * 0.16f)
        lineTo(w * 0.76f, h * 0.38f)
        lineTo(w * 0.24f, h * 0.38f)
        close()
    }, fill(rgba(34, 30, 52)))
    val tyre = fill(rgba(8, 6, 12))
    c.drawRect(w * 0.08f, h * 0.86f, w * 0.26f, h * 1.0f, tyre)
    c.drawRect(w * 0.74f, h * 0.86f, w * 0.92f, h * 1.0f, tyre)
    val lamp = fill(rgba(255, 70, 80)).glow(8f, rgba(255, 40, 60))
    c.drawPath(roundRect(w * 0.09f, h * 0.5f, w * 0.2f, h * 0.09f, 3f), lamp)
    c.drawPath(roundRect(w * 0.71f, h * 0.5f, w * 0.2f, h * 0.09f, 3f), lamp)
    c.drawRect(w * 0.42f, h * 0.66f, w * 0.58f, h * 0.76f, fill(rgba(205, 200, 220)))
}

private fun drawTrafficFront(c: Canvas, w: Int, h: Int) {
    c.drawPath(carBody(w, h, front = true), fill(rgba(18, 15, 26)))
    c.drawPath(Path().apply {
        moveTo(w * 0.29f, h * 0.18f)
        quadTo(w * 0.5f, h * 0.12f, w * 0.71f, h * 0.18f)
        lineTo(w * 0.78f, h * 0.4f)
        lineTo(w * 0.22f, h * 0.4f)
        close()
    }, fill(rgba(40, 36, 60)))
    val dark = fill(rgba(8, 6, 12))
    c.drawRect(w * 0.08f, h * 0.86f, w * 0.26f, h * 1.0f, dark)
    c.drawRect(w * 0.74f, h * 0.86f, w * 0.92f, h * 1.0f, dark)
    c.drawRect(w * 0.36f, h * 0.62f, w * 0.64f, h * 0.74f, dark)
    val lamp = fill(rgba(240, 248, 255)).glow(10f, rgba(220, 240, 255))
    c.drawPath(roundRect(w * 0.1f, h * 0.5f, w * 0.19f, h * 0.1f, 4f), lamp)
    c.drawPath(roundRect(w * 0.71f, h * 0.5f, w * 0.19f, h * 0.1f, 4f), lamp)
}

/** A flying car seen from below and behind: a dark wedge with a lit underside. */
private fun drawFlyer(c: Canvas, w: Int, h: Int) {
    c.drawPath(Path().apply {
        moveTo(w * 0.04f, h * 0.6f)
        quadTo(w * 0.2f, h * 0.22f, w * 0.5f, h * 0.18f)
        quadTo(w * 0.8f, h * 0.22f, w * 0.96f, h * 0.6f)
        quadTo(w * 0.5f, h * 0.86f, w * 0.04f, h * 0.6f)
        close()
    }, fill(rgba(14, 12, 22)))
    c.drawPath(Path().apply {
        moveTo(w * 0.12f, h * 0.66f)
        quadTo(w * 0.5f, h * 0.86f, w * 0.88f, h * 0.66f)
    }, stroke(rgba(255, 255, 255, 0.9), 3f).glow(12f, WHITE))
    c.drawPath(Path().apply {
        moveTo(w * 0.32f, h * 0.36f)
        quadTo(w * 0.5f, h * 0.26f, w * 0.68f, h * 0.36f)
        lineTo(w * 0.62f, h * 0.44f)
        lineTo(w * 0.38f, h * 0.44f)
        close()
    }, fill(rgba(40, 36, 64)))
}

private fun drawCrosswalk(c: Canvas, w: Int, h: Int) {
    // One worn white bar of a zebra crossing
    c.drawRect(2f, 2f, w - 2f, h - 2f, fill(rgba(255, 255, 255, 0.9)))
    val rng = Rng(99)
    val wear = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    repeat(140) {
        wear.color = rgba(0, 0, 0, 0.15 + rng.next() * 0.5)
        val x = (rng.next() * w).toFloat()
        val y = (rng.next() * h).toFloat()
        val rw = (2 + rng.next() * 6).toFloat()
        val rh = (2 + rng.next() * 4).toFloat()
        c.drawRect(x, y, x + rw, y + rh, wear)
    }
}

// --- Far skylines ------------------------------------------------------------------------------

/**
 * A strip of far city that wraps around: the outline in the alpha channel, lit windows in red and
 * the red lights on the masts in green, so the shader can colour it for each district.
 */
private fun buildSkyline(seed: Int, w: Int, h: Int, near: Boolean): Bitmap {
    val rng = Rng(seed)
    val red = IntArray(w * h)
    val green = IntArray(w * h)
    val alpha = IntArray(w * h)
    fun set(xIn: Int, y: Int, r: Int, g: Int, a: Int) {
        val x = ((xIn % w) + w) % w
        if (y < 0 || y >= h) return
        val i = y * w + x
        if (r > red[i]) red[i] = r
        if (g > green[i]) green[i] = g
        if (a > alpha[i]) alpha[i] = a
    }
    var x = 0
    while (x < w) {
        val bw = if (near) rng.range(50.0, 170.0).roundToInt() else rng.range(18.0, 70.0).roundToInt()
        val tall = rng.chance(if (near) 0.18 else 0.12)
        val bh = (if (tall) rng.range(h * 0.55, h * 0.95) else rng.range(h * 0.12, h * (if (near) 0.5 else 0.45))).roundToInt()
        val top = h - bh
        val stepped = rng.chance(0.3)
        for (yy in top until h) {
            val inset = if (stepped && yy < top + bh * 0.18) (bw * 0.18).roundToInt() else 0
            for (xx in x + inset until x + bw - inset) set(xx, yy, 0, 0, 255)
        }
        // Windows: a grid, some lit
        val lit = if (near) 0.16 else 0.22
        val pitchX = if (near) 6 else 4
        val pitchY = if (near) 8 else 6
        var yy = top + 6
        while (yy < h - 4) {
            var xx = x + 3
            while (xx < x + bw - 3) {
                if (rng.next() < lit) {
                    val b = rng.range(120.0, 255.0).roundToInt()
                    set(xx, yy, b, 0, 255)
                    if (near) {
                        set(xx + 1, yy, b, 0, 255)
                        set(xx, yy + 1, b, 0, 255)
                        set(xx + 1, yy + 1, b, 0, 255)
                    }
                }
                xx += pitchX
            }
            yy += pitchY
        }
        // Masts and their red lights
        if (tall || rng.chance(0.25)) {
            val mx = x + (bw / 2.0).roundToInt()
            val mh = rng.range(8.0, if (near) 40.0 else 26.0).roundToInt()
            for (my in top - mh until top) set(mx, my, 0, 0, 255)
            set(mx, top - mh - 1, 0, 255, 255)
            set(mx, top - mh - 2, 0, 255, 255)
            set(mx - 1, top - mh - 1, 0, 200, 255)
            set(mx + 1, top - mh - 1, 0, 200, 255)
        }
        x += bw + if (rng.chance(0.3)) rng.range(2.0, 14.0).roundToInt() else 0
    }
    val px = IntArray(w * h) { (alpha[it] shl 24) or (red[it] shl 16) or (green[it] shl 8) }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

// --- Noise -------------------------------------------------------------------------------------

/** A wrapping noise tile: fine grain in red, puddles in green, long streaks in blue. */
private fun buildNoise(): Bitmap {
    val n = 256
    val rng = Rng(4242)
    val white = DoubleArray(n * n) { rng.next() }
    // Value noise on a coarse lattice for the puddles
    fun lattice(cells: Int): (Double, Double) -> Double {
        val g = DoubleArray(cells * cells) { rng.next() }
        fun at(i: Int, j: Int) = g[((j % cells) + cells) % cells * cells + ((i % cells) + cells) % cells]
        return { x, y ->
            val fx = x / n * cells
            val fy = y / n * cells
            val x0 = floor(fx).toInt()
            val y0 = floor(fy).toInt()
            val tx = smooth(fx - x0)
            val ty = smooth(fy - y0)
            lerp(lerp(at(x0, y0), at(x0 + 1, y0), tx), lerp(at(x0, y0 + 1), at(x0 + 1, y0 + 1), tx), ty)
        }
    }
    val n4 = lattice(4)
    val n8 = lattice(8)
    val n16 = lattice(16)
    val n32 = lattice(32)
    val px = IntArray(n * n)
    for (y in 0 until n) for (x in 0 until n) {
        val i = y * n + x
        // Grain: white noise softened with its neighbours
        val g = (white[i] * 2 + white[y * n + (x + 1) % n] + white[((y + 1) % n) * n + x]) / 4
        val p = n4(x.toDouble(), y.toDouble()) * 0.5 + n8(x.toDouble(), y.toDouble()) * 0.3 + n16(x.toDouble(), y.toDouble()) * 0.2
        val streak = n32(x * 0.125, y.toDouble()) * 0.6 + n16(x * 0.25, y.toDouble()) * 0.4
        px[i] = (255 shl 24) or ((g * 255).roundToInt() shl 16) or ((p * 255).roundToInt() shl 8) or (streak * 255).roundToInt()
    }
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
}
