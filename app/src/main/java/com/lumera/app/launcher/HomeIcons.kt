package com.lumera.app.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.sin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * How the apps on the home screen look. Neon draws each as a lit tube; the others show the app's
 * own icon or TV banner, as the app itself has it on the TV, so nothing of anyone's logo is kept
 * in Neon Tokyo.
 */
enum class IconPack(val key: String, val title: String) {
    /** The neon line icons. */
    Neon("neon", "Neon"),

    /** The app's own icon, clean and sharp, with its name. */
    Original("original", "Original"),

    /** The app's wide TV banner across the whole tile, as on Google TV. */
    Banner("banner", "Banner"),

    /** The app's own icon, large, on a soft pane of frosted glass. */
    Glass("glass", "Glass");

    companion object {
        fun of(key: String?): IconPack = values().firstOrNull { it.key == key } ?: Neon
    }
}

internal val LocalIconPack = compositionLocalOf { IconPack.Neon }

/** The apps' own icons and banners, read from the TV once and kept while the home screen runs. */
internal object AppArt {
    private val cache = LruCache<String, ImageBitmap>(96)

    fun cached(packageName: String, banner: Boolean): ImageBitmap? = cache.get(key(packageName, banner))

    /** The icon or banner of [packageName], or null when the app has none (or is not on the TV). */
    suspend fun load(context: Context, packageName: String, banner: Boolean): ImageBitmap? {
        val key = key(packageName, banner)
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val drawable = runCatching {
                if (banner) pm.getApplicationBanner(packageName) ?: pm.getLeanbackLaunchIntentForPackage(packageName)?.let { pm.getActivityBanner(it) }
                else pm.getApplicationIcon(packageName)
            }.getOrNull()
            val image = drawable?.let { runCatching { toBitmap(it, if (banner) 640 else 192, if (banner) 360 else 192) }.getOrNull() }
            image?.asImageBitmap()?.also { cache.put(key, it) }
        }
    }

    private fun key(packageName: String, banner: Boolean) = if (banner) "b:$packageName" else "i:$packageName"

    /** [drawable] as a bitmap about the size a tile shows it at, keeping its shape. */
    private fun toBitmap(drawable: Drawable, maxWidth: Int, maxHeight: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val b = drawable.bitmap
            if (b.width <= maxWidth * 2 && b.height <= maxHeight * 2) return b
        }
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxWidth
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxHeight
        // Drawn at the size the tile wants, so an adaptive or vector icon stays sharp
        val scale = minOf(maxWidth.toFloat() / w, maxHeight.toFloat() / h)
        val bw = (w * scale).toInt().coerceAtLeast(1)
        val bh = (h * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, bw, bh)
        drawable.draw(canvas)
        return bitmap
    }
}

/** An app's art as far as it has been read: [done] once the TV has answered, with or without it. */
private class Art(val image: ImageBitmap?, val done: Boolean)

/** The first of [packages] that has art of that kind, read off the main thread. */
@Composable
private fun rememberAppArt(packages: List<String>, banner: Boolean): Art {
    val context = LocalContext.current
    val ready = packages.firstNotNullOfOrNull { AppArt.cached(it, banner) }
    val art by produceState(Art(ready, ready != null || packages.isEmpty()), packages, banner) {
        if (!value.done) value = Art(packages.firstNotNullOfOrNull { AppArt.load(context, it, banner) }, true)
    }
    return art
}

/**
 * An app's face on its tile in the [IconPack] the profile wears: its own icon with its name, its
 * banner, or its icon on glass. [packages] are the app's packages, the one on the TV among them.
 * For Neon, and for an app the TV has no art for, it is [fallback], the neon face; while the art
 * is still being read the tile stays empty for a moment rather than flicker from one to the other.
 */
@Composable
internal fun BoxScope.AppArtFace(
    pack: IconPack,
    packages: List<String>,
    name: String,
    shape: RoundedCornerShape,
    color: Color,
    focused: Boolean,
    big: Boolean,
    nameSize: TextUnit,
    fallback: @Composable BoxScope.() -> Unit
) {
    val look = LocalHomeLook.current
    val shine = rememberShine(focused, pack != IconPack.Neon)
    when (pack) {
        IconPack.Neon -> fallback()
        IconPack.Banner -> {
            val banner = rememberAppArt(packages, banner = true)
            when {
                banner.image != null -> Image(
                    bitmap = banner.image,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(shape).shine(shine)
                )
                // Not every app has a TV banner: its icon, as Original shows it
                banner.done -> OriginalFace(packages, name, big, nameSize, color, shine, fallback)
            }
        }
        IconPack.Original -> OriginalFace(packages, name, big, nameSize, color, shine, fallback)
        IconPack.Glass -> {
            val icon = rememberAppArt(packages, banner = false)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f))), shape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.22f), shape)
            )
            when {
                icon.image != null -> Column(
                    modifier = Modifier.fillMaxSize().padding(vertical = if (big) 8.dp else 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AppIcon(icon.image, name, if (big) 44.dp else 34.dp, color, shine)
                    if (big) {
                        Spacer(Modifier.size(5.dp))
                        Name(name, nameSize, look.text.copy(alpha = 0.9f))
                    }
                }
                icon.done -> fallback()
            }
        }
    }
}

@Composable
private fun BoxScope.OriginalFace(
    packages: List<String>,
    name: String,
    big: Boolean,
    nameSize: TextUnit,
    color: Color,
    shine: Shine,
    fallback: @Composable BoxScope.() -> Unit
) {
    val look = LocalHomeLook.current
    val art = rememberAppArt(packages, banner = false)
    val icon = art.image
    if (icon == null) {
        if (art.done) fallback()
        return
    }
    if (big) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(icon, name, 44.dp, color, shine)
            Spacer(Modifier.width(11.dp))
            Name(name, nameSize, look.text)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 8.dp)) {
            AppIcon(icon, name, 28.dp, color, shine)
            Spacer(Modifier.weight(1f))
            Name(name, nameSize, look.text)
        }
    }
}

/**
 * The light on an app's own icon or banner in focus: it glows in the app's colour, breathing a
 * little, and every few seconds a gleam runs across it as over glass. Only the drawing reads it.
 */
internal class Shine(val time: State<Float>, val lit: State<Float>)

@Composable
private fun rememberShine(focused: Boolean, wanted: Boolean): Shine {
    val time = rememberFocusClock(focused && wanted)
    val lit = animateFloatAsState(if (focused) 1f else 0f, tween(320, easing = FastOutSlowInEasing), label = "IconShine")
    return remember(time, lit) { Shine(time, lit) }
}

/** The glow around an icon, drawn behind it and past its edges. */
private fun Modifier.halo(shine: Shine, color: Color): Modifier = drawBehind {
    val lit = shine.lit.value
    if (lit <= 0f) return@drawBehind
    val breathe = 0.85f + 0.15f * sin(shine.time.value * 2.2f)
    val radius = size.maxDimension * 0.95f
    drawCircle(
        Brush.radialGradient(0f to color.copy(alpha = 0.55f), 1f to Color.Transparent, center = center, radius = radius),
        radius = radius,
        alpha = lit * breathe
    )
}

/** The gleam running across an icon or banner, over what it shows. */
private fun Modifier.shine(shine: Shine): Modifier = drawWithContent {
    drawContent()
    val lit = shine.lit.value
    if (lit <= 0f) return@drawWithContent
    // Across in 0.7 s, then a rest, once on landing and every 3.2 s after
    val run = (shine.time.value % 3.2f) / 0.7f
    if (run < 1f) {
        val band = size.width * 0.35f
        val x = -band + (size.width + band * 2f) * run
        drawRect(
            Brush.linearGradient(
                0f to Color.Transparent,
                0.5f to Color.White.copy(alpha = 0.42f),
                1f to Color.Transparent,
                start = Offset(x - band, 0f),
                end = Offset(x + band * 0.4f, size.height)
            ),
            alpha = lit
        )
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap, name: String, size: Dp, color: Color, shine: Shine) {
    val corner = RoundedCornerShape(size * 0.22f)
    Image(
        bitmap = icon,
        contentDescription = name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(size)
            .halo(shine, color)
            .graphicsLayer {
                // It rises a touch towards the viewer in focus
                val s = 1f + 0.08f * shine.lit.value
                scaleX = s
                scaleY = s
            }
            .clip(corner)
            .shine(shine)
    )
}

@Composable
private fun Name(text: String, size: TextUnit, color: Color) {
    val look = LocalHomeLook.current
    BasicText(
        text = text,
        style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = size, color = color),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
