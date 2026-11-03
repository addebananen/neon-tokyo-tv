package com.lumera.app.launcher

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Static, resolution-independent depth: no video decoder, blur or per-frame allocations. */
@Composable
internal fun BlueHourBackdrop() {
    Spacer(Modifier.fillMaxSize().drawWithCache {
        val base = Brush.verticalGradient(listOf(Color(0xFF142C49), Color(0xFF080F1C)))
        val center = Offset(size.width * 0.7f, size.height * 0.18f)
        val radius = size.width * 0.65f
        val light = Brush.radialGradient(
            listOf(Color(0x443E7BA8), Color.Transparent), center, radius
        )
        onDrawBehind {
            drawRect(base)
            drawRect(light)
        }
    })
}
