package com.lumera.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A title's logo. Logos drawn for light backgrounds (black or navy lettering) disappear on the
 * night backdrop, so those are lit in the text colour instead. [fallback] shows when the logo
 * can't be loaded.
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
    // null until the logo is checked; it stays hidden that long so a dark logo never flashes
    var dark by remember(url) { mutableStateOf(LogoTone.cached(url)) }
    val scope = rememberCoroutineScope()

    AsyncImage(
        model = url,
        contentDescription = name,
        contentScale = ContentScale.Fit,
        alignment = Alignment.BottomStart,
        colorFilter = if (dark == true) ColorFilter.tint(NeonPalette.Text, BlendMode.SrcIn) else null,
        onSuccess = { state ->
            if (dark == null) {
                val drawable = state.result.drawable
                scope.launch {
                    val isDark = withContext(Dispatchers.Default) {
                        runCatching { LogoTone.isDark(drawable) }.getOrDefault(false)
                    }
                    LogoTone.store(url, isDark)
                    dark = isDark
                }
            }
        },
        onError = { failed = true },
        modifier = modifier.alpha(if (dark == null) 0f else 1f)
    )
}

internal object LogoTone {
    private val checked = LruCache<String, Boolean>(256)

    fun cached(url: String): Boolean? = checked.get(url)

    fun store(url: String, dark: Boolean) {
        checked.put(url, dark)
    }

    fun isDark(drawable: Drawable): Boolean {
        val source = (drawable as? BitmapDrawable)?.bitmap ?: return false
        // Coil hands out GPU bitmaps, which can't be read; a software copy can
        val soft = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: return false
        } else {
            source
        }
        val width = 48
        val height = (width * soft.height / soft.width.coerceAtLeast(1)).coerceIn(1, 48)
        val small = Bitmap.createScaledBitmap(soft, width, height, true)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== soft && small !== source) small.recycle()
        if (soft !== source) soft.recycle()
        return isDarkLogo(pixels)
    }
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
