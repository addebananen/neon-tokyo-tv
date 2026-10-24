package com.lumera.app.intro

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Leaving the app the way an old TV switches off, in well under half a second: the picture folds
 * into a glowing line across the middle, the same neon line the intro starts with, the line runs
 * back into a dot and the dot goes out. [picture] is the app's own content, folded where it is, so
 * nothing has to be copied. [onDone] runs once the screen is black.
 */
class NeonPowerOffView(
    context: Context,
    private val picture: View,
    private val onDone: () -> Unit
) : View(context) {

    private var t = 0.0
    private var k = 1f
    private val wash = Paint().apply { color = Color.rgb(255, 236, 246) }
    private val glow = Paint()
    private val core = Paint().apply { color = Color.rgb(251, 66, 158) }
    private val hot = Paint().apply { color = Color.argb(242, 255, 220, 238) }
    private val spark = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = (TOTAL * 1000).roundToInt().toLong()
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedFraction * TOTAL
            fold()
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) = onDone()
        })
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun start() {
        picture.pivotX = picture.width / 2f
        picture.pivotY = picture.height / 2f
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.removeAllListeners()
        animator.removeAllUpdateListeners()
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        k = h / 1080f
        val cy = h / 2f
        val clear = Color.argb(0, 255, 61, 154)
        glow.shader = LinearGradient(
            0f, cy - 40 * k, 0f, cy + 40 * k, intArrayOf(clear, Color.argb(128, 255, 61, 154), clear),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        spark.shader = RadialGradient(
            0f, 0f, 34 * k, intArrayOf(Color.WHITE, Color.argb(204, 255, 150, 205), clear),
            floatArrayOf(0f, 0.25f, 1f), Shader.TileMode.CLAMP
        )
        dot.shader = RadialGradient(
            0f, 0f, 46 * k, intArrayOf(Color.WHITE, Color.argb(230, 255, 170, 215), clear),
            floatArrayOf(0f, 0.18f, 1f), Shader.TileMode.CLAMP
        )
    }

    // The picture flattens slowly at first, then snaps into the line
    private fun fold() {
        val f = seg(t, 0.0, FOLD)
        picture.scaleY = (1 - (1 - FLAT) * f * f * f).toFloat()
        picture.alpha = if (t < FOLD) 1f else 0f
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        // The picture burns brighter as it folds
        val f = seg(t, 0.0, FOLD)
        if (f < 1) {
            val band = h * picture.scaleY / 2f
            wash.alpha = a255(0.92 * f.pow(1.6))
            canvas.drawRect(0f, cy - band, w, cy + band, wash)
        }
        // The line takes over from the flat picture and runs back into the middle
        val lineA = smooth(seg(t, 0.125, 0.165))
        val shrink = seg(t, 0.14, 0.30).let { it * it * it }
        val half = (cx * (1 - shrink)).toFloat()
        if (lineA > 0 && half > 0.5f) {
            val a = a255(lineA)
            glow.alpha = a
            core.alpha = a
            hot.alpha = (a * 0.95f).roundToInt()
            canvas.drawRect(cx - half, cy - 40 * k, cx + half, cy + 40 * k, glow)
            canvas.drawRect(cx - half, cy - 2 * k, cx + half, cy + 2 * k, core)
            canvas.drawRect(cx - half, cy - k, cx + half, cy + k, hot)
            spark.alpha = a
            drawSpark(canvas, cx - half, cy)
            drawSpark(canvas, cx + half, cy)
        }
        // The dot holds a moment, then goes out
        val out = 1 - smooth(seg(t, 0.27, TOTAL))
        val d = smooth(seg(t, 0.24, 0.29)) * out
        if (d > 0) {
            val size = (0.6 + 0.4 * out).toFloat()
            dot.alpha = a255(d)
            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(size, size)
            canvas.drawCircle(0f, 0f, 46 * k, dot)
            canvas.restore()
        }
    }

    private fun drawSpark(canvas: Canvas, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.drawCircle(0f, 0f, 34 * k, spark)
        canvas.restore()
    }

    private companion object {
        const val TOTAL = 0.42
        const val FOLD = 0.16
        const val FLAT = 0.012

        fun seg(t: Double, from: Double, to: Double): Double = ((t - from) / (to - from)).coerceIn(0.0, 1.0)
        fun smooth(x: Double): Double = x.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
        fun a255(a: Double): Int = (a.coerceIn(0.0, 1.0) * 255).roundToInt()
    }
}
