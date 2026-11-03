package com.lumera.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

val LocalCinemaStyle = staticCompositionLocalOf { false }

/** Retargets from the current position/velocity on rapid D-pad reversals, without relayout. */
@Composable
fun Modifier.cinemaFocus(focused: Boolean, shape: Shape): Modifier {
    if (!LocalCinemaStyle.current) return this
    val focus by animateFloatAsState(
        if (focused) 1f else 0f,
        spring(dampingRatio = 1f, stiffness = 380f), label = "CinemaCardFocus"
    )
    return graphicsLayer {
        scaleX = 1f + focus * 0.045f
        scaleY = scaleX
        translationY = -3.dp.toPx() * focus
        shadowElevation = 10.dp.toPx() * focus
        this.shape = shape
        clip = false
    }
}
