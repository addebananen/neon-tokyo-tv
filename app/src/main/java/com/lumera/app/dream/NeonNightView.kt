package com.lumera.app.dream

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.View
import com.lumera.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Neon Tokyo night for the screensaver: the wallpaper skyline with its grid rolling towards
 * you, twinkling stars, a shooting star now and then, a blinking light on the tower and a neon
 * clock that lights up like a sign and flickers when the minute turns. Everything is drawn in
 * the 1920x1080 units of the wallpaper scene and scaled to fill the screen.
 */
class NeonNightView(context: Context) : View(context) {

    private val sky: Bitmap? = decode(R.drawable.nt_dream_sky)
    private val horizon: Bitmap? = decode(R.drawable.nt_dream_horizon)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val skyRect = RectF(0f, 0f, W, H)
    private val horizonRect = RectF(0f, HY - 80f, W, HY + 80f)
    private val started = SystemClock.uptimeMillis()

    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private val gridLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = PINK
        alpha = 204
    }
    private val gridGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = PINK
        alpha = 40
    }

    private val stars = makeStars()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.2f }

    private val random = Random(started)
    private var shot: Shot? = null
    // The first one comes soon, so a quick look at the preview shows one
    private var nextShotAt = started + 5_000L + random.nextLong(4_000L)
    private val tailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2.6f
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private val beaconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BEACON }

    private val vignette = Paint().apply {
        shader = RadialGradient(
            W / 2f, H * 0.55f, H * 1.05f,
            intArrayOf(Color.TRANSPARENT, 0x6B000000),
            floatArrayOf(1f / 3f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = font(R.font.kanit_semibold_italic, Typeface.DEFAULT_BOLD)
        textSize = 176f
        color = TEXT
    }
    private val meridiemPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = font(R.font.kanit_medium_italic, Typeface.DEFAULT)
        textSize = 46f
        color = CYAN
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = font(R.font.kanit_medium, Typeface.DEFAULT)
        textSize = 30f
        letterSpacing = 0.32f
        color = CYAN
    }

    private var minuteShown = -1L
    private var hours = ""
    private var minutes = ""
    private var meridiem = ""
    private var date = ""
    private var hoursWidth = 0f
    private var colonWidth = 0f
    private var minutesWidth = 0f
    // The clock lights up like a neon sign when the screensaver starts
    private var flickerFrom = started
    private var flickerKeys = IGNITION

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scale = max(w / W, h / H)
        offsetX = (w - W * scale) / 2f
        offsetY = (h - H * scale) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val seconds = (now - started) / 1000f

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)

        if (sky != null) canvas.drawBitmap(sky, null, skyRect, bitmapPaint) else canvas.drawColor(NIGHT)
        drawGrid(canvas, now)
        if (horizon != null) canvas.drawBitmap(horizon, null, horizonRect, bitmapPaint)
        drawStars(canvas, seconds)
        drawShootingStar(canvas, now)
        drawBeacon(canvas, seconds)
        canvas.drawRect(0f, 0f, W, H, vignette)
        drawClock(canvas, now, seconds)

        canvas.restore()
        postInvalidateOnAnimation()
    }

    // Twelve lines that roll from the horizon to the bottom edge every four seconds, like the video
    private fun drawGrid(canvas: Canvas, now: Long) {
        val phase = ((now - started) % GRID_LOOP_MS) / GRID_LOOP_MS.toFloat()
        for (k in 0 until 12) {
            val t = (k + phase) / 12f
            val y = HY + (H - HY) * t.pow(2.1f)
            val width = 1f + 2.5f * t
            gridGlow.strokeWidth = width + 3f + 8f * t
            canvas.drawLine(0f, y, W, y, gridGlow)
            gridLine.strokeWidth = width
            canvas.drawLine(0f, y, W, y, gridLine)
        }
    }

    private fun drawStars(canvas: Canvas, seconds: Float) {
        for (star in stars) {
            val wave = 0.5f + 0.5f * sin(seconds * star.speed + star.phase)
            val a = star.brightness * (0.1f + 0.9f * wave * wave)
            starPaint.color = star.color
            starPaint.alpha = (a * 70).toInt()
            canvas.drawCircle(star.x, star.y, star.radius * 3f, starPaint)
            starPaint.alpha = (a * 255).toInt()
            canvas.drawCircle(star.x, star.y, star.radius, starPaint)
            if (star.flare && a > 0.55f) {
                val k = (a - 0.55f) / 0.45f
                val arm = 4f + 10f * k
                flarePaint.color = star.color
                flarePaint.alpha = (k * 170).toInt()
                canvas.drawLine(star.x - arm, star.y, star.x + arm, star.y, flarePaint)
                canvas.drawLine(star.x, star.y - arm, star.x, star.y + arm, flarePaint)
            }
        }
    }

    private fun drawShootingStar(canvas: Canvas, now: Long) {
        val current = shot ?: run {
            if (now < nextShotAt) return
            newShot(now).also { shot = it }
        }
        val p = (now - current.start) / current.duration.toFloat()
        if (p >= 1f) {
            shot = null
            tailPaint.shader = null
            nextShotAt = now + 16_000L + random.nextLong(14_000L)
            return
        }
        val travelled = current.speed * (now - current.start) / 1000f
        val headX = current.x + current.dirX * travelled
        val headY = current.y + current.dirY * travelled
        val alpha = minOf(1f, p / 0.15f) * minOf(1f, (1f - p) / 0.4f)
        val length = current.length * minOf(1f, p / 0.3f)
        val tailX = headX - current.dirX * length
        val tailY = headY - current.dirY * length

        tailPaint.shader = LinearGradient(
            headX, headY, tailX, tailY,
            intArrayOf(withAlpha(Color.WHITE, alpha), withAlpha(STAR_CYAN, alpha * 0.4f), Color.TRANSPARENT),
            floatArrayOf(0f, 0.3f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawLine(headX, headY, tailX, tailY, tailPaint)
        headPaint.alpha = (alpha * 80).toInt()
        canvas.drawCircle(headX, headY, 7f, headPaint)
        headPaint.alpha = (alpha * 255).toInt()
        canvas.drawCircle(headX, headY, 2.6f, headPaint)
    }

    // Starts in the open sky left of the sun and falls away to the left, never across the sun
    private fun newShot(now: Long): Shot {
        val angle = (10f + random.nextFloat() * 8f) * PI.toFloat() / 180f
        return Shot(
            start = now,
            x = 650f + random.nextFloat() * 400f,
            y = 40f + random.nextFloat() * 100f,
            dirX = -cos(angle),
            dirY = sin(angle),
            speed = 700f + random.nextFloat() * 150f,
            duration = 700L + random.nextLong(200L),
            length = 200f + random.nextFloat() * 60f
        )
    }

    // The red light on top of the tower, one short blink every 1.8 seconds
    private fun drawBeacon(canvas: Canvas, seconds: Float) {
        val phase = (seconds % 1.8f) / 1.8f
        val blink = if (phase < 0.16f) sin(PI.toFloat() * phase / 0.16f) else 0f
        val a = 0.15f + 0.85f * blink
        beaconPaint.alpha = (a * 90).toInt()
        canvas.drawCircle(BEACON_X, BEACON_Y, 11f, beaconPaint)
        beaconPaint.alpha = (a * 255).toInt()
        canvas.drawCircle(BEACON_X, BEACON_Y, 3f, beaconPaint)
    }

    private fun drawClock(canvas: Canvas, now: Long, seconds: Float) {
        val wall = System.currentTimeMillis()
        val minute = wall / 60_000L
        if (minute != minuteShown) {
            if (minuteShown != -1L) {
                flickerFrom = now
                flickerKeys = MINUTE_FLICKER
            }
            minuteShown = minute
            formatTime(wall)
        }

        // A slow drift so nothing sits on the same pixels all night
        val x = CLOCK_X + 28f * sin(2f * PI.toFloat() * seconds / 420f)
        val y = CLOCK_Y + 18f * sin(2f * PI.toFloat() * seconds / 290f + 1.3f)

        val lit = flicker(now - flickerFrom, flickerKeys)
        glow(timePaint, lit, 28f, PINK)
        canvas.drawText(hours, x, y, timePaint)
        // The colon breathes with the seconds
        val tick = (wall % 1000L) / 1000f
        glow(timePaint, lit * (0.55f + 0.45f * cos(2f * PI.toFloat() * tick)), 28f, PINK)
        canvas.drawText(":", x + hoursWidth + COLON_GAP, y - 10f, timePaint)
        glow(timePaint, lit, 28f, PINK)
        val minutesX = x + hoursWidth + COLON_GAP * 2 + colonWidth
        canvas.drawText(minutes, minutesX, y, timePaint)
        if (meridiem.isNotEmpty()) {
            glow(meridiemPaint, lit, 14f, CYAN)
            canvas.drawText(meridiem, minutesX + minutesWidth + 16f, y, meridiemPaint)
        }

        val dateLit = ((now - started - 500L) / 700f).coerceIn(0f, 1f)
        glow(datePaint, dateLit, 14f, CYAN)
        canvas.drawText(date, x + 10f, y + 64f, datePaint)
    }

    private fun formatTime(wall: Long) {
        val calendar = Calendar.getInstance().apply { timeInMillis = wall }
        val locale = Locale.getDefault()
        if (DateFormat.is24HourFormat(context)) {
            hours = String.format(locale, "%02d", calendar.get(Calendar.HOUR_OF_DAY))
            meridiem = ""
        } else {
            val hour = calendar.get(Calendar.HOUR)
            hours = (if (hour == 0) 12 else hour).toString()
            meridiem = SimpleDateFormat("a", locale).format(calendar.time)
        }
        minutes = String.format(locale, "%02d", calendar.get(Calendar.MINUTE))
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        date = SimpleDateFormat(pattern, locale).format(calendar.time).uppercase(locale)
        hoursWidth = timePaint.measureText(hours)
        colonWidth = timePaint.measureText(":")
        minutesWidth = timePaint.measureText(minutes)
    }

    private fun decode(resId: Int): Bitmap? =
        runCatching { BitmapFactory.decodeResource(resources, resId) }.getOrNull()

    private fun font(resId: Int, fallback: Typeface): Typeface =
        runCatching { resources.getFont(resId) }.getOrNull() ?: fallback

    private data class Star(
        val x: Float,
        val y: Float,
        val radius: Float,
        val brightness: Float,
        val speed: Float,
        val phase: Float,
        val color: Int,
        val flare: Boolean
    )

    private class Shot(
        val start: Long,
        val x: Float,
        val y: Float,
        val dirX: Float,
        val dirY: Float,
        val speed: Float,
        val duration: Long,
        val length: Float
    )

    private companion object {
        const val W = 1920f
        const val H = 1080f
        const val HY = 735f
        const val GRID_LOOP_MS = 4_000L
        const val CLOCK_X = 150f
        const val CLOCK_Y = 330f
        const val COLON_GAP = 6f
        // Tip of the tower's antenna in the wallpaper
        const val BEACON_X = 840f
        const val BEACON_Y = 365f

        const val NIGHT = 0xFF0D0721.toInt()
        const val TEXT = 0xFFF6EAFF.toInt()
        const val PINK = 0xFFFF3D9A.toInt()
        const val CYAN = 0xFF3EF0FF.toInt()
        const val BEACON = 0xFFFF4D4D.toInt()
        const val STAR_PINK = 0xFFFFD6EC.toInt()
        const val STAR_CYAN = 0xFFD6FBFF.toInt()

        // (milliseconds, brightness) keyframes
        val IGNITION = floatArrayOf(0f, 0f, 260f, 0f, 300f, 0.8f, 340f, 0.1f, 420f, 0.9f, 470f, 0.3f, 560f, 1f)
        val MINUTE_FLICKER = floatArrayOf(0f, 1f, 50f, 0.25f, 110f, 1f, 170f, 0.45f, 240f, 1f, 330f, 0.7f, 400f, 1f)

        fun flicker(elapsed: Long, keys: FloatArray): Float {
            val t = elapsed.toFloat()
            if (t >= keys[keys.size - 2]) return keys[keys.size - 1]
            var i = 0
            while (i + 2 < keys.size && t >= keys[i + 2]) i += 2
            val span = keys[i + 2] - keys[i]
            val f = if (span <= 0f) 1f else (t - keys[i]) / span
            return keys[i + 1] + (keys[i + 3] - keys[i + 1]) * f
        }

        fun withAlpha(color: Int, alpha: Float): Int =
            (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)

        /** Sets how lit a neon text is, glow included. */
        fun glow(paint: Paint, lit: Float, radius: Float, color: Int) {
            paint.alpha = (lit.coerceIn(0f, 1f) * 255).toInt()
            paint.setShadowLayer(radius, 0f, 0f, withAlpha(color, 0.85f * lit))
        }

        // Fixed seed: the same sky every night. Kept above the rooftops and clear of the sun.
        fun makeStars(): List<Star> {
            val r = Random(7)
            val stars = ArrayList<Star>()
            while (stars.size < 46) {
                val x = r.nextFloat() * W
                val y = 20f + r.nextFloat() * 340f
                if (hypot(x - 1390f, y - 565f) < 330f) continue
                val tint = r.nextFloat()
                stars += Star(
                    x = x,
                    y = y,
                    radius = 0.9f + r.nextFloat() * 1.2f,
                    brightness = 0.5f + r.nextFloat() * 0.5f,
                    speed = 0.5f + r.nextFloat() * 1.3f,
                    phase = r.nextFloat() * 2f * PI.toFloat(),
                    color = when {
                        tint < 0.15f -> STAR_PINK
                        tint < 0.3f -> STAR_CYAN
                        else -> Color.WHITE
                    },
                    flare = false
                )
            }
            // The six biggest get a little cross of light when they peak
            val biggest = stars.sortedByDescending { it.radius }.take(6).toSet()
            return stars.map { if (it in biggest) it.copy(flare = true) else it }
        }
    }
}
