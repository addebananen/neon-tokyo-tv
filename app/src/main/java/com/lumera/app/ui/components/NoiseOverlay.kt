package com.lumera.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.IntSize
import kotlin.random.Random

private const val NOISE_SIZE = 128

/**
 * The tiled noise bitmap (ALPHA_8, 128x128), made once for the whole app and shared by every
 * screen that uses it.
 */
private val noiseBitmap: Bitmap by lazy {
    val bitmap = Bitmap.createBitmap(NOISE_SIZE, NOISE_SIZE, Bitmap.Config.ALPHA_8)
    val pixels = IntArray(NOISE_SIZE * NOISE_SIZE)
    val random = Random(42) // Fixed seed for deterministic noise
    for (i in pixels.indices) {
        // Random alpha value, packed into ARGB format for setPixels
        pixels[i] = random.nextInt(256) shl 24
    }
    bitmap.setPixels(pixels, 0, NOISE_SIZE, 0, 0, NOISE_SIZE, NOISE_SIZE)
    bitmap
}

private val noiseBrush by lazy {
    androidx.compose.ui.graphics.ShaderBrush(
        androidx.compose.ui.graphics.ImageShader(
            noiseBitmap.asImageBitmap(),
            androidx.compose.ui.graphics.TileMode.Repeated,
            androidx.compose.ui.graphics.TileMode.Repeated
        )
    )
}

@Composable
fun rememberNoiseBitmap(): Bitmap = noiseBitmap

/**
 * Draws a subtle tiled noise overlay to break up gradient banding.
 * Uses ~1.5-2% opacity so it's invisible on good panels but smooths
 * out banding on 6-bit and 8-bit displays.
 *
 * The tile repeats through a shader, so the whole screen is one rectangle for the graphics chip
 * instead of a hundred and more small pictures each time the screen above it is drawn. It sits
 * in a layer of its own, so what changes beneath it does not draw it again.
 */
@Composable
fun NoiseOverlay(modifier: Modifier = Modifier, alpha: Float = 0.018f) {
    val brush = noiseBrush

    Canvas(modifier = modifier.fillMaxSize().graphicsLayer()) {
        drawRect(brush = brush, alpha = alpha)
    }
}
