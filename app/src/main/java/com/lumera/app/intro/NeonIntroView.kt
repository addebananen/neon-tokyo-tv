package com.lumera.app.intro

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import com.lumera.app.R
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Neon Tokyo intro, in well under a second: the horizon line draws itself out from the middle,
 * the sky comes up, the floor rolls out and the sun rises to the place the skyline behind the
 * profile screen has it, and the sign flickers on like a neon tube. Then the picture fades into the
 * app, whose skyline it matches line for line, so the city seems to light up around the sun.
 *
 * It is drawn on a thread of its own onto a surface over the window, so the app starting up under
 * it can't make it stutter, and it only waits for the app once it has played.
 */
class NeonIntroView(
    context: Context,
    /** Holds the picture this many seconds in for a while, for the emulator test to photograph. */
    private val holdAt: Double = -1.0,
    private val onDone: () -> Unit
) : FrameLayout(context), SurfaceHolder.Callback {

    private val surface = SurfaceView(context)
    // The intro keeps to the system's animation speed, like any other animation
    private val speed = 1.0 / animatorScale(context).coerceAtLeast(0.01f)
    @Volatile private var appReady = false
    @Volatile private var drawing = false
    private var painter: Painter? = null
    private var done = false

    init {
        // The window's own night until the surface has its first picture, so the app never shows early
        setBackgroundColor(NIGHT)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        @Suppress("DEPRECATION")
        surface.setZOrderOnTop(true)
        surface.holder.setFormat(PixelFormat.TRANSLUCENT)
        surface.holder.addCallback(this)
        // At most 1080 lines; a 4K screen's display hardware scales it up
        val metrics = resources.displayMetrics
        if (metrics.heightPixels > SCENE_H && metrics.widthPixels > metrics.heightPixels) {
            val w = (SCENE_H.toDouble() * metrics.widthPixels / metrics.heightPixels).roundToInt()
            surface.holder.setFixedSize(w, SCENE_H.toInt())
        }
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** The app has its first screen ready: the intro leaves as soon as it has played. */
    fun appReady() {
        appReady = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // A surface that never comes, or a painter that stops, must not keep the app covered
        postDelayed({ if (!drawing) finish() }, FIRST_FRAME_DEADLINE_MS)
        postDelayed({ finish() }, LAST_RESORT_MS + (if (holdAt >= 0) (HOLD_FOR * 1000).toLong() else 0L))
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (done || painter != null) return
        painter = Painter(holder).also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // The surface stays valid until this returns, so the painter finishes its frame first
        painter?.let {
            it.running = false
            it.join(500)
        }
        painter = null
        // Leaving the app while the intro plays: it is over by the time the app is back
        post { finish() }
    }

    private fun finish() {
        if (done) return
        done = true
        painter?.running = false
        onDone()
    }

    private inner class Painter(private val holder: SurfaceHolder) : Thread("NeonIntro") {
        @Volatile var running = true

        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
            val scene = IntroScene(resources)
            var start = 0L
            var leaveAt = -1.0
            var frames = 0
            try {
                while (running) {
                    val canvas = holder.lockHardwareCanvas()
                    val now = System.nanoTime()
                    if (start == 0L) start = now
                    val t = clock((now - start) / 1e9 * speed)
                    if (leaveAt < 0 && t >= scene.earliestExit(t) && (appReady || t >= MAX_WAIT)) leaveAt = t
                    val leave = if (leaveAt < 0) -1.0 else (t - leaveAt) / LEAVE
                    try {
                        scene.draw(canvas, t, leave)
                    } finally {
                        holder.unlockCanvasAndPost(canvas)
                    }
                    frames++
                    drawing = true
                    // The surface covers the app by now, and fades into it at the end
                    if (frames == 3) post { if (!done) background = null }
                    if (leave >= 1.0) break
                }
            } catch (e: Exception) {
                Log.w(TAG, "the intro stopped early", e)
            }
            if (start != 0L) Log.i(TAG, "played $frames frames in ${(System.nanoTime() - start) / 1_000_000} ms")
            post { finish() }
        }

        private fun clock(real: Double): Double = when {
            holdAt < 0 || real < holdAt -> real
            real < holdAt + HOLD_FOR -> holdAt
            else -> real - HOLD_FOR
        }
    }

    companion object {
        private const val FIRST_FRAME_DEADLINE_MS = 2000L
        private const val LAST_RESORT_MS = 8000L
        private const val HOLD_FOR = 8.0

        /** False when the system's animations are switched off, and the app opens without the intro. */
        fun animationsOn(context: Context): Boolean = animatorScale(context) > 0f

        private fun animatorScale(context: Context): Float =
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
}

// --- The picture ----------------------------------------------------------------------------------

private const val TAG = "NeonIntro"

// The scene is laid out on a 1080 line picture, cropped to the screen like the skyline behind the
// profile screen, whose horizon and sun it shares
private const val SCENE_W = 1920f
private const val SCENE_H = 1080f
private const val HY = 692f
private const val FLOOR_TOP = 694
private const val SUN_X = 1329.5f
private const val SUN_Y = 459.6f
private const val SUN_R = 259.5f

// Seconds
private const val LINE_LEN = 0.20
private const val SKY_FROM = 0.03
private const val SKY_TO = 0.30
private const val FLOOR_FROM = 0.08
private const val FLOOR_LEN = 0.28
private const val SUN_FROM = 0.10
private const val SUN_TO = 0.42
private const val SIGN_FROM = 0.17
private const val JP_DELAY = 0.07
private const val JP_STEP = 0.02
// The sign and the last Japanese letter have caught by this long after the sign starts
private const val SIGN_SETTLE = 0.28
private const val SIGN_DEADLINE = 0.8
private const val MIN_EXIT = 0.44
private const val MAX_WAIT = 3.0
private const val LEAVE = 0.28
private const val PULSE_FROM = 0.6
private const val PULSE_PERIOD = 1.4

private const val LINE1 = "NEON"
private const val LINE2 = "TOKYO"
private const val JP = "ネオン東京"
private const val SIGN_X = 150f
private const val SIGN_B2 = HY - 30f
private const val SIGN_B1 = SIGN_B2 - 208f
private const val JP_X = 166f
private const val JP_BASE = SIGN_B1 - 186f

private val NIGHT = Color.rgb(13, 7, 33)
private val PINK = Color.rgb(255, 61, 154)
private val CYAN = Color.rgb(62, 240, 255)

// A neon tube catching: (seconds after it starts, brightness) steps
private val IGNITE = doubleArrayOf(0.0, 1.0, 0.035, 0.25, 0.07, 1.0, 0.10, 0.5, 0.13, 1.0)

// The stripes across the sun, as parts of its height from the top
private val BANDS = floatArrayOf(0.497f, 0.530f, 0.613f, 0.651f, 0.728f, 0.772f, 0.842f, 0.896f)

private fun withAlpha(color: Int, a: Double): Int = Color.argb((a * 255).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))
private fun a255(a: Double): Int = (a.coerceIn(0.0, 1.0) * 255).roundToInt()
private fun seg(t: Double, from: Double, to: Double): Double = ((t - from) / (to - from)).coerceIn(0.0, 1.0)
private fun outCubic(x: Double): Double = 1 - (1 - x) * (1 - x) * (1 - x)
private fun smooth(x: Double): Double = x.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }

private fun stepAt(keys: DoubleArray, s: Double): Double {
    if (s < 0) return 0.0
    var v = 0.0
    var i = 0
    while (i < keys.size && s >= keys[i]) {
        v = keys[i + 1]
        i += 2
    }
    return v
}

/** A glow under what the paint draws, as a web canvas's shadowBlur of [blur] pixels would give. */
private fun Paint.glow(blur: Float, color: Int) {
    setShadowLayer(max(0.5f, (blur / 2f - 0.5f) / 0.57735f), 0f, 0f, color)
}

/**
 * Draws the intro on the painter thread. The wordmark and the skyline's floor are made on threads
 * of their own while the line draws, and come in as they are ready.
 */
private class IntroScene(res: Resources) {
    @Volatile private var sign: Sign? = null
    @Volatile private var signFailed = false
    @Volatile private var floor: Bitmap? = null
    private var signFrom = -1.0
    private var floorFrom = -1.0

    private val sky = Paint().apply {
        val stops = floatArrayOf(0f, 120 / HY, 240 / HY, 360 / HY, 480 / HY, 560 / HY, 1f)
        val colors = intArrayOf(
            Color.rgb(10, 7, 31), Color.rgb(18, 8, 43), Color.rgb(23, 10, 53), Color.rgb(32, 13, 65),
            Color.rgb(58, 18, 88), Color.rgb(72, 22, 98), Color.rgb(128, 30, 112)
        )
        shader = LinearGradient(0f, 0f, 0f, HY, colors, stops, Shader.TileMode.CLAMP)
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val r = SUN_R + 170f
        shader = RadialGradient(
            SUN_X, SUN_Y, r, intArrayOf(withAlpha(PINK, 0.3), withAlpha(PINK, 0.3), withAlpha(PINK, 0.0)),
            floatArrayOf(0f, SUN_R * 0.92f / r, 1f), Shader.TileMode.CLAMP
        )
    }
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, SUN_Y - SUN_R, 0f, SUN_Y + SUN_R,
            intArrayOf(Color.rgb(255, 211, 107), Color.rgb(255, 159, 74), PINK),
            floatArrayOf(0f, 0.45f, 0.85f), Shader.TileMode.CLAMP
        )
    }
    // What shows through the stripes: the glow behind the sun
    private val bandFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val edge = Color.rgb(88, 33, 86)
        shader = LinearGradient(
            SUN_X - SUN_R, 0f, SUN_X + SUN_R, 0f, intArrayOf(edge, Color.rgb(126, 50, 94), edge),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
    }
    private val bandRects = Array(BANDS.size / 2) { i ->
        val top = SUN_Y - SUN_R
        RectF(SUN_X - SUN_R - 2f, top + BANDS[2 * i] * 2 * SUN_R, SUN_X + SUN_R + 2f, top + BANDS[2 * i + 1] * 2 * SUN_R)
    }
    private val discPath = Path().apply {
        addCircle(SUN_X, SUN_Y, SUN_R, Path.Direction.CW)
        val stripes = Path()
        for (r in bandRects) stripes.addRect(r, Path.Direction.CW)
        op(stripes, Path.Op.DIFFERENCE)
    }
    private val floorPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val floorDst = RectF(0f, 0f, SCENE_W, SCENE_H - FLOOR_TOP)
    private val lineGlow = Paint().apply {
        shader = LinearGradient(
            0f, HY - 40f, 0f, HY + 40f, intArrayOf(withAlpha(PINK, 0.0), withAlpha(PINK, 0.42), withAlpha(PINK, 0.0)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
    }
    private val lineCore = Paint().apply { color = Color.rgb(251, 66, 158) }
    private val lineHot = Paint().apply { color = Color.rgb(255, 220, 238) }
    private val spark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, SPARK_R, intArrayOf(Color.WHITE, Color.argb(204, 255, 150, 205), withAlpha(PINK, 0.0)),
            floatArrayOf(0f, 0.25f, 1f), Shader.TileMode.CLAMP
        )
    }
    private val signPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        art("NeonIntroFloor") { floor = decodeFloor(res)?.also { it.prepareToDraw() } }
        art("NeonIntroSign", onFail = { signFailed = true }) { sign = Sign(res) }
    }

    /** When the intro may leave: once the sign has caught, or without it if it can't come. */
    fun earliestExit(t: Double): Double = when {
        signFrom >= 0 -> max(MIN_EXIT, signFrom + SIGN_SETTLE)
        signFailed || t > SIGN_DEADLINE -> MIN_EXIT
        else -> Double.MAX_VALUE
    }

    /** [leave] runs from 0 to 1 while the picture fades into the app, and is below 0 before. */
    fun draw(c: Canvas, t: Double, leave: Double) {
        if (leave < 0) {
            if (signFrom < 0 && sign != null) signFrom = max(SIGN_FROM, t)
            if (floorFrom < 0 && floor != null) floorFrom = max(FLOOR_FROM, t)
        }
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        val fade = if (leave < 0) 1.0 else 1 - smooth((leave - 0.15) / 0.85)
        if (fade < 1) {
            c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            if (fade <= 0) return
            c.saveLayerAlpha(0f, 0f, w, h, a255(fade))
        }
        c.drawColor(NIGHT)
        val k = max(w / SCENE_W, h / SCENE_H)
        c.save()
        c.translate((w - SCENE_W * k) / 2f, (h - SCENE_H * k) / 2f)
        c.scale(k, k)
        drawSky(c, t)
        drawSun(c, t)
        drawFloor(c, t)
        drawLine(c, t, leave)
        drawSign(c, t, leave)
        c.restore()
        if (fade < 1) c.restore()
    }

    // The sky comes up like dawn over the horizon
    private fun drawSky(c: Canvas, t: Double) {
        val a = smooth(seg(t, SKY_FROM, SKY_TO))
        if (a <= 0) return
        sky.alpha = a255(a)
        c.drawRect(0f, 0f, SCENE_W, HY, sky)
    }

    // The sun rises from behind the horizon to where the skyline has it
    private fun drawSun(c: Canvas, t: Double) {
        val rise = outCubic(seg(t, SUN_FROM, SUN_TO))
        if (rise <= 0) return
        val below = HY + SUN_R + 8f
        val cy = below - (below - SUN_Y) * rise
        c.save()
        c.clipRect(0f, 0f, SCENE_W, HY - 1f)
        c.translate(0f, (cy - SUN_Y).toFloat())
        halo.alpha = a255(rise)
        c.drawCircle(SUN_X, SUN_Y, SUN_R + 170f, halo)
        for (r in bandRects) {
            c.save()
            c.clipRect(r)
            c.drawCircle(SUN_X, SUN_Y, SUN_R - 0.5f, bandFill)
            c.restore()
        }
        c.drawPath(discPath, disc)
        c.restore()
    }

    // The floor rolls out from the horizon towards you: the skyline's own grid
    private fun drawFloor(c: Canvas, t: Double) {
        val bitmap = floor ?: return
        if (floorFrom < 0) return
        val unroll = outCubic(seg(t, floorFrom, floorFrom + FLOOR_LEN))
        if (unroll <= 0) return
        floorPaint.alpha = a255(smooth(seg(t, floorFrom, floorFrom + 0.14)))
        c.save()
        c.clipRect(0f, FLOOR_TOP.toFloat(), SCENE_W, SCENE_H)
        c.translate(0f, FLOOR_TOP.toFloat())
        c.scale(1f, unroll.toFloat())
        c.drawBitmap(bitmap, null, floorDst, floorPaint)
        c.restore()
    }

    // The horizon line draws itself out from the middle, white hot, and settles into pink
    private fun drawLine(c: Canvas, t: Double, leave: Double) {
        val grow = outCubic(seg(t, 0.0, LINE_LEN))
        if (grow <= 0) return
        val half = (SCENE_W / 2 * grow).toFloat()
        val x0 = SCENE_W / 2 - half
        val x1 = SCENE_W / 2 + half
        c.drawRect(x0, HY - 40f, x1, HY + 40f, lineGlow)
        c.drawRect(x0, HY - 2f, x1, HY + 2f, lineCore)
        val hot = 1 - smooth(seg(t, 0.14, 0.40))
        if (hot > 0) {
            lineHot.alpha = a255(hot)
            c.drawRect(x0, HY - 1f, x1, HY + 1f, lineHot)
        }
        // Sparks at the ends while it draws
        val tips = 1 - smooth(seg(t, 0.15, 0.25))
        if (tips > 0) {
            drawSpark(c, x0, 1f, tips)
            drawSpark(c, x1, 1f, tips)
        }
        // A light runs out along the line while the app is still getting ready
        if (t > PULSE_FROM) {
            val phase = ((t - PULSE_FROM) % PULSE_PERIOD) / PULSE_PERIOD
            val a = 0.7 * sin(PI * phase) * (if (leave < 0) 1.0 else 1 - smooth(leave / 0.3))
            if (a > 0) {
                val off = (920 * phase).toFloat()
                drawSpark(c, SCENE_W / 2 - off, 0.65f, a)
                drawSpark(c, SCENE_W / 2 + off, 0.65f, a)
            }
        }
    }

    private fun drawSpark(c: Canvas, x: Float, size: Float, a: Double) {
        spark.alpha = a255(a)
        c.save()
        c.translate(x, HY)
        c.scale(size, size)
        c.drawCircle(0f, 0f, SPARK_R, spark)
        c.restore()
    }

    // The sign flickers on like a neon tube, the Japanese letters one after another, and steps
    // forward out of the way as the app comes in
    private fun drawSign(c: Canvas, t: Double, leave: Double) {
        val s = sign ?: return
        if (signFrom < 0) return
        val gone = if (leave < 0) 0.0 else leave
        val a = stepAt(IGNITE, t - signFrom) * (1 - smooth(gone / 0.4))
        if (a <= 0) return
        val zoom = (1 + 0.05 * outCubic(min(1.0, gone / 0.45))).toFloat()
        c.save()
        c.scale(zoom, zoom, 560f, HY - 200f)
        signPaint.alpha = a255(a)
        c.drawBitmap(s.bitmap, s.left, s.top, signPaint)
        for (i in JP.indices) {
            val ja = a * stepAt(IGNITE, t - signFrom - JP_DELAY - i * JP_STEP)
            if (ja <= 0) continue
            s.jp.alpha = a255(ja)
            c.drawText(JP, i, i + 1, s.jpX[i], JP_BASE, s.jp)
        }
        c.restore()
    }

    private fun art(name: String, onFail: () -> Unit = {}, make: () -> Unit) {
        Thread({
            try {
                make()
            } catch (e: Throwable) {
                Log.w(TAG, "$name failed", e)
                onFail()
            }
        }, name).start()
    }

    private companion object {
        const val SPARK_R = 34f
    }
}

/** NEON TOKYO with its glow, painted once, and the Japanese line drawn letter by letter. */
private class Sign(res: Resources) {
    val bitmap: Bitmap
    val left: Float
    val top: Float
    val jp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    val jpX = FloatArray(JP.length)

    init {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = res.getFont(R.font.kanit_semibold_italic)
            textSize = 236f
            color = Color.rgb(255, 241, 248)
        }
        val a = Rect()
        val b = Rect()
        paint.getTextBounds(LINE1, 0, LINE1.length, a)
        paint.getTextBounds(LINE2, 0, LINE2.length, b)
        // Room for the widest glow on every side
        val pad = 150f
        left = SIGN_X + min(a.left, b.left) - pad
        top = SIGN_B1 + a.top - pad
        val right = SIGN_X + max(a.right, b.right) + pad
        val bottom = SIGN_B2 + b.bottom + pad
        bitmap = Bitmap.createBitmap((right - left).roundToInt(), (bottom - top).roundToInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.translate(-left, -top)
        // A wide soft glow and a tight bright one, like a lit tube
        for (pass in 0..1) {
            if (pass == 0) paint.glow(80f, withAlpha(PINK, 0.6)) else paint.glow(28f, withAlpha(PINK, 0.95))
            c.drawText(LINE1, SIGN_X, SIGN_B1, paint)
            c.drawText(LINE2, SIGN_X, SIGN_B2, paint)
        }
        bitmap.prepareToDraw()
        jp.typeface = res.getFont(R.font.neon_jp)
        jp.textSize = 46f
        jp.color = CYAN
        jp.glow(18f, CYAN)
        var x = JP_X
        for (i in JP.indices) {
            jpX[i] = x
            x += jp.measureText(JP, i, i + 1) + 16f
        }
    }
}

/** The skyline's floor, from just under its horizon line to the bottom. */
private fun decodeFloor(res: Resources): Bitmap? {
    val area = Rect(0, FLOOR_TOP, SCENE_W.toInt(), SCENE_H.toInt())
    val region = runCatching {
        res.openRawResource(R.drawable.nt_skyline).use { input ->
            val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(input)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(input, false)
            }
            try {
                decoder?.decodeRegion(area, null)
            } finally {
                decoder?.recycle()
            }
        }
    }.getOrNull()
    if (region != null) return region
    val whole = BitmapFactory.decodeResource(res, R.drawable.nt_skyline, BitmapFactory.Options().apply { inScaled = false })
        ?: return null
    if (whole.width < area.right || whole.height < area.bottom) return null
    return Bitmap.createBitmap(whole, area.left, area.top, area.width(), area.height())
}
