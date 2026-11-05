package com.neontokyo.desktop.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

/**
 * Neon Home's motion, number for number (app/.../launcher/NeonHomeScreen.kt NeonTile and
 * FocusBackdrop), so a card on the PC lands exactly like an app tile on the TV:
 * the focus pops it to 107 % on a spring that overshoots a touch, its neon tube lights in
 * 150 ms, and the light on the floor glides after it.
 */
object NeonMotion {
    /** Bouncy pop: the TV's default Motion setting. */
    const val POP = 1.07f
    const val POP_DAMPING = 0.55f
    const val POP_STIFFNESS = 650f
    /** Letting go settles without a wobble. */
    const val SETTLE_STIFFNESS = 900f
    const val TUBE_ON_MS = 150
    const val TUBE_OFF_MS = 240
    const val SHEEN_MS = 900
    /** The floor light and the row's slide. */
    const val GLIDE_DAMPING = 0.86f
    const val GLIDE_STIFFNESS = 240f
    const val LEAN_MS = 1100

    fun <T> pop() = spring<T>(dampingRatio = POP_DAMPING, stiffness = POP_STIFFNESS)
    fun <T> settle() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = SETTLE_STIFFNESS)
    fun <T> glide() = spring<T>(dampingRatio = GLIDE_DAMPING, stiffness = GLIDE_STIFFNESS)
    fun <T> lean() = tween<T>(LEAN_MS, easing = FastOutSlowIn)

    val FastOutSlowIn = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    /** Light crossing glass: quick start, long soft tail. */
    val Sheen = CubicBezierEasing(0.3f, 0f, 0.15f, 1f)
}

/**
 * Where the focus is, in window coordinates, and the colour of what is focused. The floor
 * light and the world's lean read it; cards write it. Kept in one small state holder so a
 * moving focus redraws those layers only.
 */
class Spot {
    var bounds by mutableStateOf<Rect?>(null)
    var color by mutableStateOf(Color(0xFFFF3D9A))
    var on by mutableStateOf(false)
    /** The mouse, 0..1 across the window, for the world's parallax. */
    var pointer by mutableStateOf(Offset(0.5f, 0.5f))

    fun land(rect: Rect, c: Color) {
        bounds = rect
        color = c
        on = true
    }
}

val LocalSpot = compositionLocalOf { Spot() }

/** How much the app moves: Full (the PC's default), Calm, or Still for those who want none. */
enum class MotionLevel(val key: String, val title: String, val amount: Float) {
    Full("full", "Full", 1f),
    Calm("calm", "Calm", 0.45f),
    Still("still", "Still", 0f),
}

val LocalMotion = compositionLocalOf { MotionLevel.Full }
