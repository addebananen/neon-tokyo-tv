package com.lumera.app.dream.drive

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The clock, the "back soon" sign and the name of each district as the car drives into it, drawn
 * over the drive at the screen's own resolution, in the units of a 1080 line picture.
 */
class DriveHud(context: Context, fonts: DriveFonts) : View(context) {

    /** The drive under it, once the GL thread has made it: for the districts' names */
    @Volatile var world: World? = null

    private val started = SystemClock.uptimeMillis()
    // The clock lights up like a neon sign soon after the screensaver starts, flickers when the
    // minute turns and switches off and on again on the hour
    private var flickerFrom = started + 600
    private var flickerKeys = IGNITE
    private var minuteShown = -1L
    private var hours = ""
    private var minutes = ""
    private var meridiem = ""
    private var date = ""
    private var hoursWidth = 0f
    private var colonWidth = 0f
    private var minutesWidth = 0f

    private val shade = Paint().apply {
        shader = radial(220f, -40f, 20f, 560f, intArrayOf(rgba(6, 3, 16, 0.5), rgba(6, 3, 16, 0.0)), floatArrayOf(0f, 1f))
    }
    private val timePaint = text(fonts.semiboldItalic, 150f, TEXT)
    private val meridiemPaint = text(fonts.mediumItalic, 40f, CYAN)
    private val datePaint = text(fonts.medium, 28f, CYAN, spacing = 9f)
    private val labelPaint = text(fonts.mediumItalic, 30f, TEXT, spacing = 6f)
    private val jpPaint = text(fonts.jp, 24f, CYAN)
    private val signFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rgba(14, 6, 30, 0.55) }
    private val signEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = PINK
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PINK }
    private val divider = Paint().apply { color = rgba(246, 234, 255, 0.25) }
    private val enteringPaint = text(fonts.medium, 18f, CYAN, spacing = 8f)
    private val districtJp = text(fonts.jp, 54f, TEXT)
    private val districtEn = text(fonts.semiboldItalic, 40f, TEXT, spacing = 5f)
    private val underline = Paint().apply { color = PINK }
    private val signRect = RectF()
    private val signLabel = "BACK SOON"
    private val signJp = "すぐ戻ります"
    private val labelWidth = labelPaint.measureText(signLabel)
    private val jpWidth = jpPaint.measureText(signJp)

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val wall = System.currentTimeMillis()
        val t = (now - started) / 1000.0
        updateClock(now, wall)
        val k = height / 1080f
        canvas.save()
        canvas.scale(k, k)
        // Nothing sits on the same pixels all night
        val dx = (26 * sin(2 * PI * t / 420)).toFloat()
        val dy = (16 * sin(2 * PI * t / 290 + 1.3)).toFloat()
        val x = 118 + dx
        val y = 214 + dy

        // A soft shade behind the clock so it reads over the brightest signs
        canvas.save()
        canvas.translate(x, y)
        canvas.drawRect(-x, -y, 1100 - x, 640 - y, shade)
        canvas.restore()

        val lit = flickerAt((now - flickerFrom).toFloat(), flickerKeys)
        neon(timePaint, lit, 34f, PINK)
        canvas.drawText(hours, x, y, timePaint)
        // The colon breathes with the seconds
        val tick = (wall % 1000) / 1000.0
        neon(timePaint, lit * (0.55f + 0.45f * cos(2 * PI * tick).toFloat()), 34f, PINK)
        canvas.drawText(":", x + hoursWidth + 6, y - 10, timePaint)
        neon(timePaint, lit, 34f, PINK)
        val minutesX = x + hoursWidth + 12 + colonWidth
        canvas.drawText(minutes, minutesX, y, timePaint)
        if (meridiem.isNotEmpty()) {
            neon(meridiemPaint, lit, 14f, CYAN)
            canvas.drawText(meridiem, minutesX + minutesWidth + 16, y, meridiemPaint)
        }

        // The date under it
        neon(datePaint, clamp((t - 1.1) / 0.7, 0.0, 1.0).toFloat(), 14f, CYAN)
        canvas.drawText(date, x + 8, y + 58, datePaint)

        // The sign: back soon
        val signLit = flickerAt(((t - 1.5) * 1000).toFloat(), IGNITE)
        if (signLit > 0f) drawSign(canvas, x, y + 104, signLit, t)

        drawEntering(canvas, dx, dy)
        canvas.restore()
        postInvalidateOnAnimation()
    }

    private fun drawSign(canvas: Canvas, x: Float, sy: Float, signLit: Float, t: Double) {
        val pw = 34 + 18 + labelWidth + 26 + jpWidth + 28
        signRect.set(x + 6, sy, x + 6 + pw, sy + 56)
        signFill.alpha = (signLit * 0.55f * 255).toInt()
        canvas.drawRoundRect(signRect, 28f, 28f, signFill)
        neon(signEdge, signLit, 16f, PINK)
        canvas.drawRoundRect(signRect, 28f, 28f, signEdge)
        // The away light breathes
        val breathe = (0.45 + 0.55 * (0.5 + 0.5 * sin(t * 2.2))).toFloat()
        dot.alpha = (signLit * breathe * 255).toInt()
        dot.glow(18f * breathe, PINK)
        canvas.drawCircle(x + 6 + 30, sy + 28, 7f, dot)
        neon(labelPaint, signLit, 12f, PINK)
        canvas.drawText(signLabel, x + 6 + 52, sy + 39, labelPaint)
        divider.alpha = (signLit * 0.25f * 255).toInt()
        canvas.drawRect(x + 6 + 52 + labelWidth + 10, sy + 16, x + 6 + 52 + labelWidth + 12, sy + 40, divider)
        neon(jpPaint, signLit, 10f, CYAN)
        canvas.drawText(signJp, x + 6 + 52 + labelWidth + 26, sy + 37, jpPaint)
    }

    /** Driving into a district: its name lights up low on the left, then fades. */
    private fun drawEntering(canvas: Canvas, dx: Float, dy: Float) {
        val w = world ?: return
        val e = w.entered
        val age = w.time - e.at
        if (age <= 0 || age >= 7.5) return
        val a = (smooth(age / 0.6) * smooth((7.5 - age) / 1.2)).toFloat()
        val bx = 118 - dx * 0.5f
        val by = 940 - dy * 0.5f
        neon(enteringPaint, a, 10f, CYAN)
        canvas.drawText("NOW ENTERING", bx + 4, by - 70, enteringPaint)
        neon(districtJp, a, 22f, PINK)
        canvas.drawText(e.def.jp, bx, by, districtJp)
        val jpw = districtJp.measureText(e.def.jp)
        neon(districtEn, a, 22f, PINK)
        canvas.drawText(e.def.en, bx + jpw + 22, by - 2, districtEn)
        val enw = districtEn.measureText(e.def.en)
        // A neon line draws itself in under the name
        val grow = smoother((age - 0.3) / 0.9).toFloat()
        if (grow > 0f) {
            neon(underline, a, 14f, PINK)
            canvas.drawRect(bx + 2, by + 22, bx + 2 + (jpw + 22 + enw) * grow, by + 25, underline)
        }
    }

    private fun updateClock(now: Long, wall: Long) {
        val minute = wall / 60_000L
        if (minute == minuteShown) return
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
        // After the first ignition the sign flickers as the minute turns, and lights up anew on the hour
        if (minuteShown != -1L && now > flickerFrom + 1000) {
            flickerFrom = now
            flickerKeys = if (calendar.get(Calendar.MINUTE) == 0) IGNITE else MINUTE_FLICKER
        }
        minuteShown = minute
    }

    private companion object {
        val TEXT = rgba(246, 234, 255)
        val PINK = rgba(255, 61, 154)
        val CYAN = rgba(62, 240, 255)

        // (milliseconds, brightness) keyframes of a neon tube catching, and of one that stutters
        val IGNITE = floatArrayOf(0f, 0f, 180f, 0f, 220f, 0.8f, 260f, 0.1f, 340f, 0.9f, 390f, 0.3f, 480f, 1f)
        val MINUTE_FLICKER = floatArrayOf(0f, 1f, 50f, 0.25f, 110f, 1f, 170f, 0.45f, 240f, 1f, 330f, 0.7f, 400f, 1f)

        fun flickerAt(ms: Float, keys: FloatArray): Float {
            if (ms >= keys[keys.size - 2]) return keys[keys.size - 1]
            var i = 0
            while (i + 2 < keys.size && ms >= keys[i + 2]) i += 2
            val span = keys[i + 2] - keys[i]
            val f = if (span <= 0f) 1f else (ms - keys[i]) / span
            return keys[i + 1] + (keys[i + 3] - keys[i + 1]) * f
        }

        fun text(face: Typeface, size: Float, color: Int, spacing: Float = 0f) =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                typeface = face
                textSize = size
                this.color = color
                letterSpacing = spacing / size
            }

        /** How lit a neon line is, its glow included, as a web canvas's alpha and shadowBlur would give. */
        fun neon(paint: Paint, lit: Float, blur: Float, color: Int) {
            paint.alpha = (clamp(lit.toDouble(), 0.0, 1.0) * 255).toInt()
            paint.glow(blur, color)
        }
    }
}
