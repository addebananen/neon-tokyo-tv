package com.lumera.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.sqrt

/**
 * A title's logo, cropped to its lettering so it lines up with the text under it however much
 * empty margin the image came with. Logos drawn for light backgrounds (black or navy lettering)
 * disappear on the night backdrop, so those are lit in the text colour instead. [fallback] shows
 * when the logo can't be loaded.
 */
@Composable
fun TitleLogo(
    url: String,
    name: String,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit
) {
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) {
        fallback()
        return
    }
    val context = LocalContext.current
    // The logo arrives cropped and lit, so it never shows first as it was and then changes
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .transformations(LogoTransformation)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = name,
        contentScale = ContentScale.Fit,
        alignment = Alignment.BottomStart,
        onError = { failed = true },
        modifier = modifier
    )
}

/** Crops a logo to its visible pixels, and lights it in the text colour when its lettering is dark. */
internal object LogoTransformation : Transformation {
    private val lit = NeonPalette.Text.toArgb()

    override val cacheKey = "neon-logo-1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = input.width
        val height = input.height
        val pixels = IntArray(width * height)
        input.getPixels(pixels, 0, width, 0, 0, width, height)
        val box = visibleBounds(pixels, width, height) ?: return input
        val dark = isDarkLogo(sample(pixels, width, box))
        if (!dark && box.width == width && box.height == height) return input
        val output = Bitmap.createBitmap(box.width, box.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        if (dark) paint.colorFilter = PorterDuffColorFilter(lit, PorterDuff.Mode.SRC_IN)
        Canvas(output).drawBitmap(input, -box.left.toFloat(), -box.top.toFloat(), paint)
        return output
    }

    /** About 4000 pixels spread evenly over [box], plenty to judge the colours by. */
    private fun sample(pixels: IntArray, width: Int, box: PixelBounds): IntArray {
        val step = sqrt(box.width * box.height / 4096.0).toInt().coerceAtLeast(1)
        val out = IntArray(((box.width + step - 1) / step) * ((box.height + step - 1) / step))
        var i = 0
        for (y in box.top until box.bottom step step) {
            for (x in box.left until box.right step step) out[i++] = pixels[y * width + x]
        }
        return if (i == out.size) out else out.copyOf(i)
    }
}

/** A box of pixels, [right] and [bottom] exclusive. */
internal data class PixelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/** The smallest box holding every pixel that is more than faintly visible, or null if none is. */
internal fun visibleBounds(argb: IntArray, width: Int, height: Int, minAlpha: Int = 16): PixelBounds? {
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            if ((argb[row + x] ushr 24) < minAlpha) continue
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            bottom = y
        }
    }
    return if (right < 0) null else PixelBounds(left, top, right + 1, bottom + 1)
}

/** Dark when the visible pixels are dark on average and even the bright ones stay dim. */
internal fun isDarkLogo(argb: IntArray): Boolean {
    val luminance = FloatArray(argb.size)
    var count = 0
    for (pixel in argb) {
        if ((pixel ushr 24) < 128) continue
        val r = pixel shr 16 and 0xFF
        val g = pixel shr 8 and 0xFF
        val b = pixel and 0xFF
        luminance[count++] = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
    }
    // Too little to judge, or a solid block (a logo on its own background) a tint would flatten
    if (count < 24 || count > argb.size * 0.95f) return false
    val values = luminance.copyOf(count).also { it.sort() }
    val mean = values.average()
    val bright = values[(count * 0.9f).toInt().coerceAtMost(count - 1)]
    return mean < 0.3 && bright < 0.5f
}

/**
 * Image request for a hero backdrop. Without a real backdrop the poster stands in: it is
 * decoded tiny, so scaled up it becomes a soft wash of its colours instead of a crop through
 * its title text. [Backdrop.wash] tells the caller to dim it a little.
 */
fun backdropRequest(context: Context, background: String?, poster: String?): Backdrop? {
    val url = background ?: poster ?: return null
    val wash = background == null
    val request = ImageRequest.Builder(context)
        .data(url)
        .crossfade(false)
        .apply { if (wash) size(36, 54) else size(1920, 1080) }
        .build()
    return Backdrop(request, wash)
}

class Backdrop(val request: ImageRequest, val wash: Boolean)
