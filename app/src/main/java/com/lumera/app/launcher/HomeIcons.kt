package com.lumera.app.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
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

    fun cachedSign(packageName: String): ImageBitmap? = cache.get("s:$packageName")

    /**
     * The sign of [packageName] alone, to lay on glass: the front layer of an adaptive icon, cut to
     * the part a launcher shows, without the plate it stands on. An icon of the older kind is all
     * one picture, and comes whole.
     */
    suspend fun loadSign(context: Context, packageName: String): ImageBitmap? {
        cache.get("s:$packageName")?.let { return it }
        return withContext(Dispatchers.IO) {
            val drawable = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
            val front = (drawable as? AdaptiveIconDrawable)?.foreground
            val image = if (front == null) {
                drawable?.let { runCatching { toBitmap(it, 192, 192) }.getOrNull() }
            } else {
                runCatching {
                    // The layer is 108 units across, of which a launcher shows the middle 72
                    val whole = Bitmap.createBitmap(288, 288, Bitmap.Config.ARGB_8888)
                    front.setBounds(0, 0, 288, 288)
                    front.draw(Canvas(whole))
                    Bitmap.createBitmap(whole, 48, 48, 192, 192)
                }.getOrNull()
            }
            image?.asImageBitmap()?.also { cache.put("s:$packageName", it) }
        }
    }

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

/** The first of [packages] with a sign to lay on glass, read off the main thread. */
@Composable
private fun rememberAppSign(packages: List<String>): Art {
    val context = LocalContext.current
    val ready = packages.firstNotNullOfOrNull { AppArt.cachedSign(it) }
    val art by produceState(Art(ready, ready != null || packages.isEmpty()), packages) {
        if (!value.done) value = Art(packages.firstNotNullOfOrNull { AppArt.loadSign(context, it) }, true)
    }
    return art
}

/**
 * An app on a glass tile, in the middle of it. Liquid glass, and the icon packs of the apps' own
 * icons, show the app's own sign; Frost and Soft with the neon pack show [glyph], a white sign that
 * takes [brand], the app's own colour, in focus when the look says so. A banner fills the tile.
 * [lit] runs from 0 to 1 as the focus lands.
 */
@Composable
internal fun BoxScope.GlassFace(
    pack: IconPack,
    packages: List<String>,
    name: String,
    shape: RoundedCornerShape,
    brand: Color,
    size: Dp,
    lit: () -> Float,
    glyph: @Composable BoxScope.(ink: () -> Color) -> Unit
) {
    val look = LocalHomeLook.current
    val tint = LocalIconTint.current
    val filter = remember(tint, brand) { tint.filter(brand) }
    val ink = { lerp(Color.White.copy(alpha = 0.94f), brand, if (look.brandInFocus) lit() else 0f) }
    @Composable
    fun Sign(art: Art) {
        val image = art.image
        when {
            image != null -> Image(
                bitmap = image,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                colorFilter = filter,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(size)
                    // The sign rises a touch as the glass comes forward
                    .graphicsLayer { translationY = -1.5f * density * lit() }
                    .clip(RoundedCornerShape(size * 0.22f))
            )
            art.done -> glyph(ink)
        }
    }
    when {
        pack == IconPack.Banner -> {
            val banner = rememberAppArt(packages, banner = true)
            when {
                banner.image != null -> Image(
                    bitmap = banner.image,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    colorFilter = filter,
                    modifier = Modifier.fillMaxSize().clip(shape)
                )
                banner.done -> Sign(rememberAppArt(packages, banner = false))
            }
        }
        pack == IconPack.Neon && look.glassMode == HomeGlass.Liquid -> Sign(rememberAppSign(packages))
        pack == IconPack.Neon -> glyph(ink)
        else -> Sign(rememberAppArt(packages, banner = false))
    }
}

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
    motion: IconMotion = IconMotion.Rise,
    fallback: @Composable BoxScope.() -> Unit
) {
    val look = LocalHomeLook.current
    val tint = LocalIconTint.current
    val shine = rememberShine(focused, pack != IconPack.Neon)
    // The remix's colours on the icon, and its glow in the remix's light
    val filter = remember(tint, color) { tint.filter(color) }
    val glow = tint.glow(color)
    val face = IconFace(filter, glow, motion)
    when (pack) {
        IconPack.Neon -> fallback()
        IconPack.Banner -> {
            val banner = rememberAppArt(packages, banner = true)
            when {
                banner.image != null -> Image(
                    bitmap = banner.image,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    colorFilter = filter,
                    modifier = Modifier.fillMaxSize().clip(shape).shine(shine)
                )
                // Not every app has a TV banner: its icon, as Original shows it
                banner.done -> OriginalFace(packages, name, big, nameSize, face, shine, fallback)
            }
        }
        IconPack.Original -> OriginalFace(packages, name, big, nameSize, face, shine, fallback)
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
                    AppIcon(icon.image, name, if (big) 44.dp else 34.dp, face, shine)
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
    face: IconFace,
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
            AppIcon(icon, name, 44.dp, face, shine)
            Spacer(Modifier.width(11.dp))
            Name(name, nameSize, look.text)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 8.dp)) {
            AppIcon(icon, name, 28.dp, face, shine)
            Spacer(Modifier.weight(1f))
            Name(name, nameSize, look.text)
        }
    }
}

/**
 * The light on an app's own icon or banner in focus: it glows in the app's colour, breathing a
 * little, and every few seconds a gleam runs across it as over glass. Only the drawing reads it.
 */
internal class Shine(val time: State<Float>, val lit: State<Float>, private val landedAt: FloatArray) {
    /** Seconds since the focus last landed. */
    val since: Float get() = time.value - landedAt[0]
}

@Composable
private fun rememberShine(focused: Boolean, wanted: Boolean): Shine {
    val time = rememberFocusClock(focused && wanted)
    val lit = animateFloatAsState(if (focused) 1f else 0f, tween(320, easing = FastOutSlowInEasing), label = "IconShine")
    // Kept out of state: only the drawing reads it, and the clock it is read with redraws anyway
    val landedAt = remember { FloatArray(1) }
    LaunchedEffect(focused) { if (focused) landedAt[0] = time.value }
    return remember(time, lit) { Shine(time, lit, landedAt) }
}

/** How an app's icon is drawn: in its remix's colours, glowing in [glow], doing its [motion]. */
internal class IconFace(val filter: ColorFilter?, val glow: Color, val motion: IconMotion)

/** The glow around an icon, drawn behind it and past its edges. */
private fun Modifier.halo(shine: Shine, face: IconFace): Modifier = drawBehind {
    val lit = shine.lit.value
    if (lit <= 0f) return@drawBehind
    val t = shine.since
    val color = if (face.motion == IconMotion.Neon) neonAt(shine.time.value) else face.glow
    val breathe = 0.85f + 0.15f * sin(shine.time.value * 2.2f)
    val radius = size.maxDimension * if (face.motion == IconMotion.Neon) 1.25f else 0.95f
    val strength = if (face.motion == IconMotion.Neon) neonFlicker(t) else 1f
    drawCircle(
        Brush.radialGradient(0f to color.copy(alpha = 0.55f), 1f to Color.Transparent, center = center, radius = radius),
        radius = radius,
        alpha = lit * breathe * strength
    )
    val edge = size.minDimension / 2f
    if (HomeMotion.icons == HomeMotion.Icons.Off) return@drawBehind
    when (face.motion) {
        // Two rings go out with each heartbeat, as from a live broadcast
        IconMotion.Beat -> for (k in 0..1) {
            val age = ((t % 1.2f) - k * 0.22f) / 0.9f
            if (age in 0f..1f) {
                drawCircle(color, radius = edge * (1f + 0.75f * age), center = center, style = Stroke(1.5.dp.toPx() * (1f - age) + 0.5f), alpha = (1f - age) * 0.8f * lit)
            }
        }
        // Four stars round the icon, twinkling in turn
        IconMotion.Drift -> for (k in 0 until 4) {
            val a = k * 1.7f + 0.6f
            val at = Offset(center.x + cos(a) * edge * 1.35f, center.y + sin(a) * edge * 1.2f)
            val twinkle = (0.5f + 0.5f * sin(shine.time.value * 3f + k * 1.9f)).let { it * it }
            val r = 1.6.dp.toPx() * (0.7f + 0.6f * twinkle)
            drawLine(Color.White, Offset(at.x - r * 2f, at.y), Offset(at.x + r * 2f, at.y), 0.8.dp.toPx(), alpha = twinkle * lit)
            drawLine(Color.White, Offset(at.x, at.y - r * 2f), Offset(at.x, at.y + r * 2f), 0.8.dp.toPx(), alpha = twinkle * lit)
            drawCircle(color, radius = r * 2.2f, center = at, alpha = 0.35f * twinkle * lit)
        }
        // A ring of tube light right round the icon, in the colours it runs through
        IconMotion.Neon -> {
            drawCircle(NeonTubes[0], radius = edge * 1.18f, center = center, style = Stroke(2.dp.toPx()), alpha = 0.55f * lit * strength)
            drawCircle(NeonTubes[2], radius = edge * 1.32f, center = center, style = Stroke(1.dp.toPx()), alpha = 0.4f * lit * strength * breathe)
        }
        else -> {}
    }
}

/** The gleam running across an icon or banner, over what it shows. */
private fun Modifier.shine(shine: Shine): Modifier = drawWithContent {
    drawContent()
    val lit = shine.lit.value
    if (lit <= 0f) return@drawWithContent
    // Across in 0.7 s, then a rest, once on landing and every 3.2 s after
    val run = (shine.since % 3.2f) / 0.7f
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
private fun AppIcon(icon: ImageBitmap, name: String, size: Dp, face: IconFace, shine: Shine) {
    val corner = RoundedCornerShape(size * 0.22f)
    val pose = remember { Pose() }
    // Read in the layer, so the motion only redraws the icon
    fun GraphicsLayerScope.posed(): Pose {
        // As lively as the Motion panel says
        val p = face.motion.pose(shine.since, shine.lit.value * HomeMotion.icons.amount, density, pose)
        scaleX = p.scale
        scaleY = p.scale
        translationX = p.dx
        translationY = p.dy
        rotationZ = p.turn
        rotationY = p.flip
        cameraDistance = 14f * density
        // A swinging sign hangs from its top
        if (face.motion == IconMotion.Swing) transformOrigin = TransformOrigin(0.5f, 0f)
        return p
    }
    Box(Modifier.size(size).halo(shine, face)) {
        if (face.motion == IconMotion.Neon) {
            // The pink and cyan ghosts of the icon a glitch splits off it, at rest exactly under it
            NeonTubes.forEachIndexed { i, tube ->
                if (i == 1) return@forEachIndexed
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(tube, BlendMode.SrcIn),
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val p = posed()
                            val side = if (i == 0) -1f else 1f
                            translationX += side * 3.5f * density * p.glitch
                            translationY += side * -1f * density * p.glitch
                            alpha = (p.glitch * 0.9f).coerceIn(0f, 1f)
                        }
                        .clip(corner)
                )
            }
        }
        Image(
            bitmap = icon,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            colorFilter = face.filter,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = posed().alpha.let { 0.35f + 0.65f * it } }
                .clip(corner)
                .shine(shine)
        )
    }
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
