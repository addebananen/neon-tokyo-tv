package com.lumera.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lumera.app.ui.theme.DefaultThemes
import com.lumera.app.ui.theme.LocalThemeId

@Composable
fun LumeraBackground(
    content: @Composable () -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val isNeonTokyo = LocalThemeId.current == DefaultThemes.NEON_TOKYO.id

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .then(if (isNeonTokyo) Modifier.neonTokyoBackdrop() else Modifier)
    ) {
        content()
    }
}

/**
 * Static sunset glow and perspective grid for the Neon Tokyo theme.
 * Built once per size with drawWithCache, so it costs nothing per frame.
 */
private fun Modifier.neonTokyoBackdrop(): Modifier = drawWithCache {
    val magenta = Color(0xFFFF3D9A)
    val cyan = Color(0xFF3EF0FF)
    val horizonY = size.height * 0.68f

    val sunGlow = Brush.radialGradient(
        colors = listOf(Color(0x40FF7A45), Color(0x1AFF3D9A), Color.Transparent),
        center = Offset(size.width * 0.78f, horizonY),
        radius = size.width * 0.45f
    )
    val floorFade = Brush.verticalGradient(
        colors = listOf(Color(0x33FF3D9A), Color.Transparent),
        startY = horizonY,
        endY = size.height
    )

    // Horizontal grid lines get closer together towards the horizon
    val horizontalLines = (1..9).map { i ->
        val t = i / 9f
        horizonY + (size.height - horizonY) * t * t
    }
    // Vertical lines fan out from a vanishing point on the horizon
    val vanishX = size.width / 2f
    val verticalLines = (-12..12).map { i -> vanishX + i * size.width / 10f }

    onDrawBehind {
        drawRect(sunGlow)
        drawRect(floorFade, topLeft = Offset(0f, horizonY))
        drawLine(magenta.copy(alpha = 0.45f), Offset(0f, horizonY), Offset(size.width, horizonY), strokeWidth = 2f)
        horizontalLines.forEach { y ->
            drawLine(magenta.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
        }
        verticalLines.forEach { x ->
            drawLine(cyan.copy(alpha = 0.12f), Offset(vanishX, horizonY), Offset(x, size.height), strokeWidth = 1.5f)
        }
    }
}
