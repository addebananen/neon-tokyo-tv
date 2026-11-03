package com.lumera.app.launcher

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Static light, cached by size: no video decoder, bitmap allocation or idle animation loop. */
@Composable
internal fun AuroraBackdrop() {
    Spacer(Modifier.fillMaxSize().drawWithCache {
        val base = Brush.verticalGradient(listOf(Color(0xFF132941), Color(0xFF070E1C)))
        val blue = Brush.radialGradient(
            listOf(Color(0x663B749F), Color.Transparent),
            center = Offset(size.width * 0.72f, size.height * 0.24f),
            radius = size.width * 0.62f
        )
        val violet = Brush.radialGradient(
            listOf(Color(0x263D407B), Color.Transparent),
            center = Offset(size.width * 0.12f, size.height * 0.85f),
            radius = size.width * 0.5f
        )
        onDrawBehind {
            drawRect(base)
            drawRect(blue)
            drawRect(violet)
        }
    })
}
