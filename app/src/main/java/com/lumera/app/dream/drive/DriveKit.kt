package com.lumera.app.dream.drive

import kotlin.math.floor
import kotlin.math.sin

// Small helpers shared by the art, the world and the renderer of the night drive.

/** Xorshift on 32 bits: the same numbers on every device for a given seed. */
class Rng(seed: Int) {
    private var s = if (seed != 0) seed else 0x2F6B3A1

    fun nextInt(): Int {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        s = x
        return x
    }

    /** In [0, 1), from 24 bits. */
    fun next(): Double = (nextInt() ushr 8) / 16777216.0
    fun range(a: Double, b: Double): Double = a + (b - a) * next()
    fun int(n: Int): Int = floor(next() * n).toInt()
    fun chance(p: Double): Boolean = next() < p
    fun <T> pick(list: List<T>): T = list[int(list.size)]
}

fun clamp(x: Double, a: Double, b: Double): Double = if (x < a) a else if (x > b) b else x
fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
fun smooth(t: Double): Double {
    val c = clamp(t, 0.0, 1.0)
    return c * c * (3 - 2 * c)
}
fun smoother(t: Double): Double {
    val c = clamp(t, 0.0, 1.0)
    return c * c * c * (c * (c * 6 - 15) + 10)
}

/** A smooth minimum of a and b, rounding the corner over k. */
fun smin(a: Double, b: Double, k: Double): Double {
    val h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0)
    return lerp(b, a, h) - k * h * (1 - h)
}

/** Cheap hash of a pair of numbers to 0..1, the same one the shaders use. */
fun hash2(x: Double, y: Double): Double {
    val h = sin(x * 127.1 + y * 311.7) * 43758.5453
    return h - floor(h)
}

/** '#rrggbb' to (r, g, b) in 0..1 */
fun rgb(hex: String): FloatArray {
    val v = hex.substring(1).toInt(16)
    return floatArrayOf(((v shr 16) and 255) / 255f, ((v shr 8) and 255) / 255f, (v and 255) / 255f)
}

fun mixRgb(a: FloatArray, b: FloatArray, t: Double): FloatArray {
    val k = t.toFloat()
    return floatArrayOf(a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k)
}

fun scaleRgb(a: FloatArray, k: Double): FloatArray {
    val f = k.toFloat()
    return floatArrayOf(a[0] * f, a[1] * f, a[2] * f)
}

/** (r, g, b) in 0..1 to an Android colour with the given alpha. */
fun argb(c: FloatArray, alpha: Double = 1.0): Int {
    fun byte(v: Double) = (clamp(v, 0.0, 1.0) * 255 + 0.5).toInt()
    return (byte(alpha) shl 24) or (byte(c[0].toDouble()) shl 16) or (byte(c[1].toDouble()) shl 8) or byte(c[2].toDouble())
}

/** An Android colour from 0..255 channels and an alpha in 0..1, as a canvas would take rgba(). */
fun rgba(r: Int, g: Int, b: Int, a: Double = 1.0): Int =
    ((clamp(a, 0.0, 1.0) * 255 + 0.5).toInt() shl 24) or (r shl 16) or (g shl 8) or b
