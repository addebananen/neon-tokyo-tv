package com.lumera.app.launcher

import android.app.ActivityOptions
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** An app on a card of the switcher: its package and its name. */
private data class SwitcherEntry(val packageName: String, val label: String)

/** How the cards come and go: quick to start, long and soft to settle, as Apple TV's do. */
private val Glide = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)

/**
 * The app switcher, opened by pressing Home twice, made like Apple TV's: the apps opened lately as
 * big cards, the newest in front with the older ones stacked behind it on the left. Left and right
 * glide through them, OK jumps into the one in front, Up throws it off the top and closes the app
 * for good, Down goes to the Bluetooth corner and Back puts the switcher away.
 *
 * Each card shows the app as it was left when the overlay service could take a picture of it,
 * and otherwise the app's own banner.
 */
@Composable
internal fun AppSwitcher(
    onDone: () -> Unit,
    onBluetooth: () -> Unit,
    onMove: () -> Unit,
    preview: List<String>? = null
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val look = LocalHomeLook.current
    val scope = rememberCoroutineScope()
    val done by rememberUpdatedState(onDone)

    val recent by remember { RecentApps.list(appContext) }.collectAsState()
    // Read off the main thread: which of them are still on the TV, and their names
    val entries by produceState<List<SwitcherEntry>?>(null, recent, preview) {
        val pkgs = preview ?: SwitcherRules.onScreen(recent.orEmpty()).map { it.packageName }
        value = withContext(Dispatchers.IO) {
            val pm = appContext.packageManager
            pkgs.mapNotNull { pkg ->
                val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                when {
                    info != null -> SwitcherEntry(pkg, runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(pkg))
                    preview != null -> SwitcherEntry(pkg, pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })
                    else -> null
                }
            }
        }
    }
    // Closed ones go at once, before the list read again from the TV comes back without them
    var gone by remember { mutableStateOf(emptySet<String>()) }
    val apps = entries?.filter { it.packageName !in gone }
    var focusKey by remember { mutableStateOf<String?>(null) }
    var closing by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf<String?>(null) }
    // The newest app has the focus as the switcher opens
    LaunchedEffect(apps) {
        if (apps != null && apps.none { it.packageName == focusKey }) focusKey = apps.lastOrNull()?.packageName
    }
    val focus = apps?.indexOfFirst { it.packageName == focusKey }?.takeIf { it >= 0 } ?: ((apps?.size ?: 1) - 1)

    // The whole switcher's way in and out
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(420, easing = Glide)) }
    var leaving by remember { mutableStateOf(false) }
    fun leave(then: () -> Unit = {}) {
        if (leaving) return
        leaving = true
        scope.launch {
            shown.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            then()
            done()
        }
    }

    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(10) {
            withFrameNanos { }
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    BackHandler(enabled = !leaving) { leave() }

    // A card thrown off the top, and the one grown to the whole screen as its app opens
    val lift = remember { Animatable(0f) }
    val zoom = remember { Animatable(0f) }

    fun close() {
        val list = apps ?: return
        val app = list.getOrNull(focus) ?: return
        if (closing != null || opening != null) return
        closing = app.packageName
        scope.launch {
            lift.snapTo(0f)
            lift.animateTo(1f, tween(300, easing = CubicBezierEasing(0.5f, 0f, 0.9f, 0.6f)))
            // The neighbour on the left slides out of the stack into its place
            focusKey = list.getOrNull(SwitcherRules.afterClose(focus, list.size).let { if (it >= focus) focus + 1 else it })?.packageName
            gone = gone + app.packageName
            if (preview == null) RecentApps.close(context, app.packageName)
            closing = null
            lift.snapTo(0f)
        }
    }

    fun open() {
        val app = apps?.getOrNull(focus) ?: return
        if (closing != null || opening != null) return
        opening = app.packageName
        scope.launch {
            zoom.animateTo(1f, tween(340, easing = Glide))
            if (preview == null) {
                HomeApps.growFrom = { ActivityOptions.makeCustomAnimation(context, android.R.anim.fade_in, android.R.anim.fade_out).toBundle() }
                try {
                    HomeApps.open(context, app.packageName)
                } finally {
                    HomeApps.growFrom = null
                }
            }
            // The app's window comes over the card; the switcher goes once it is hidden under it
            delay(900)
            done()
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .focusRequester(requester)
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val ours = native.keyCode in SwitcherKeys
                if (!ours) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown || leaving) return@onPreviewKeyEvent true
                val size = apps?.size ?: 0
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> if (focus > 0 && closing == null && opening == null) {
                        focusKey = apps?.getOrNull(focus - 1)?.packageName
                        onMove()
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> if (focus < size - 1 && closing == null && opening == null) {
                        focusKey = apps?.getOrNull(focus + 1)?.packageName
                        onMove()
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> if (native.repeatCount == 0) close()
                    KeyEvent.KEYCODE_DPAD_DOWN -> if (native.repeatCount == 0 && opening == null) leave(onBluetooth)
                    else -> if (native.repeatCount == 0) open()
                }
                true
            }
            .focusable()
    ) {
        val density = LocalDensity.current
        val screenW = maxWidth
        val screenH = maxHeight
        val cardW = screenW * CARD_WIDTH
        val cardH = cardW * 9f / 16f
        val cardLeft = (screenW - cardW) / 2
        val cardTop = (screenH - cardH) / 2 + 18.dp
        val cardWPx = with(density) { cardW.toPx() }
        val fullScale = screenW / cardW
        val toCentre = with(density) { (screenH / 2 - (cardTop + cardH / 2)).toPx() }
        val aboveTop = with(density) { (cardTop - HEADER).toPx() } + 40f

        // The home screen sinks back behind the cards
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown.value }
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.78f),
                        0.5f to Color.Black.copy(alpha = 0.62f),
                        1f to Color.Black.copy(alpha = 0.82f)
                    )
                )
                .drawBehind {
                    // A breath of the style's colour under the cards
                    drawRect(
                        Brush.radialGradient(
                            listOf(look.accent.copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(size.width / 2, size.height * 0.55f),
                            radius = size.width * 0.55f
                        )
                    )
                }
        )

        if (apps != null && apps.isEmpty()) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        alpha = shown.value
                        val s = 0.94f + 0.06f * shown.value
                        scaleX = s
                        scaleY = s
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BasicText("No apps open", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = Color.White))
                Spacer(Modifier.height(8.dp))
                BasicText("The apps you open show up here", style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 15.sp, color = Color.White.copy(alpha = 0.6f)))
            }
        }

        apps?.forEachIndexed { index, app ->
            key(app.packageName) {
                val target = (index - focus).toFloat()
                // Read only where the card is drawn, so a glide redraws the cards without composing them again
                val dState = animateFloatAsState(
                    target,
                    spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessLow * 1.6f),
                    label = "card"
                )
                val isClosing = closing == app.packageName
                val isOpening = opening == app.packageName
                val anyOpening = opening != null
                val grown = { if (isOpening) zoom.value else 0f }
                Column(
                    Modifier
                        .zIndex(if (isOpening) 10f else SwitcherRules.pose(target).z)
                        .offset(x = cardLeft, y = cardTop - HEADER)
                        .width(cardW)
                        .graphicsLayer {
                            val d = dState.value
                            val pose = SwitcherRules.pose(d)
                            val enter = shown.value
                            val z = grown()
                            val l = if (isClosing) lift.value else 0f
                            // Coming in, the cards glide in from the right and settle
                            translationX = (pose.x + (1f - enter) * (0.5f + 0.25f * abs(d))) * cardWPx
                            translationY = -l * (size.height + aboveTop) + z * toCentre
                            val s = pose.scale * (0.94f + 0.06f * enter)
                            scaleX = s + (fullScale - s) * z
                            scaleY = s + (fullScale - s) * z
                            alpha = (pose.alpha * enter * (1f - 0.6f * l)).coerceIn(0f, 1f) *
                                if (anyOpening && !isOpening) 1f - zoom.value else 1f
                            transformOrigin = TransformOrigin(0.5f, (HEADER + cardH / 2) / (HEADER + cardH))
                        }
                ) {
                    CardHeader(
                        app,
                        front = { (1f - abs(dState.value) * 1.6f).coerceIn(0f, 1f) * (1f - grown()) },
                        stacked = { dState.value < -0.5f }
                    )
                    CardFace(
                        app = app,
                        height = cardH,
                        front = { 1f - abs(dState.value).coerceAtMost(1f) },
                        shade = { SwitcherRules.pose(dState.value).shade },
                        flat = grown
                    )
                }
            }
        }

        // What the remote does, quietly under the cards
        if (apps != null) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = cardTop + cardH + 34.dp)
                    .graphicsLayer { alpha = shown.value * (1f - zoom.value) * 0.85f },
                horizontalArrangement = Arrangement.spacedBy(30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (apps.isNotEmpty()) {
                    Hint(up = true, "Close app")
                    Hint(up = null, "Open")
                }
                Hint(up = false, "Bluetooth")
            }
        }
    }
}

/** The app's icon over its card, and its name over the card in front. */
@Composable
private fun CardHeader(app: SwitcherEntry, front: () -> Float, stacked: () -> Boolean) {
    val context = LocalContext.current
    val look = LocalHomeLook.current
    val icon by produceState<ImageBitmap?>(AppArt.cached(app.packageName, false), app.packageName) {
        if (value == null) value = AppArt.load(context, app.packageName, banner = false)
    }
    Row(
        Modifier.height(HEADER).padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .graphicsLayer {
                    alpha = if (stacked()) 0.9f else 1f
                    shape = RoundedCornerShape(10.dp)
                    clip = true
                }
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            icon?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(14.dp))
        BasicText(
            app.label,
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Color.White),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer { alpha = front() }
        )
    }
}

/**
 * The card itself: the picture of the app as it was left, or its banner on the style's own
 * colours. [front] is how near it is to having the focus, [flat] how far it has grown to the
 * whole screen as its app opens, when its corners and edge go.
 */
@Composable
private fun CardFace(app: SwitcherEntry, height: Dp, front: () -> Float, shade: () -> Float, flat: () -> Float) {
    val context = LocalContext.current
    val look = LocalHomeLook.current
    val shotsVersion by RecentApps.shots.collectAsState()
    val shot by produceState<ImageBitmap?>(null, app.packageName, shotsVersion) {
        value = RecentApps.shot(context, app.packageName) ?: value
    }
    val banner by produceState<ImageBitmap?>(AppArt.cached(app.packageName, true), app.packageName, shot == null) {
        if (shot == null && value == null) value = AppArt.load(context, app.packageName, banner = true)
    }
    val icon = AppArt.cached(app.packageName, false)
    val edge = look.accent2
    val top = lerp(look.tileTop, Color.Black, 0.35f)
    val bottom = lerp(look.tileBottom, Color.Black, 0.55f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            // A soft dark halo under the card
            .drawBehind {
                val f = flat()
                val r = CORNER.toPx() * (1f - f)
                for (i in 1..3) {
                    val grow = i * 9f
                    drawRoundRect(
                        Color.Black.copy(alpha = 0.16f * (1f - f)),
                        topLeft = Offset(-grow, -grow + 10f),
                        size = Size(size.width + 2 * grow, size.height + 2 * grow),
                        cornerRadius = CornerRadius(r + grow)
                    )
                }
            }
            .graphicsLayer {
                shape = RoundedCornerShape(CORNER * (1f - flat()))
                clip = true
            }
            .drawBehind { drawRect(Brush.verticalGradient(listOf(top, bottom))) }
            .drawWithContent {
                drawContent()
                val f = flat()
                val dark = shade()
                if (dark > 0f) drawRect(Color.Black.copy(alpha = dark))
                // Light along the top as on glass, and the edge of the card in front in the style's colour
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f * (1f - f)), 0.35f to Color.Transparent))
                val lit = front() * (1f - f)
                if (lit > 0f) {
                    val w = 2.dp.toPx()
                    drawRoundRect(
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), edge.copy(alpha = 0.7f), Color.White.copy(alpha = 0.25f))),
                        topLeft = Offset(w / 2, w / 2),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(CORNER.toPx() * (1f - f) - w / 2),
                        alpha = lit,
                        style = Stroke(w)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val picture = shot
        val art = banner
        when {
            picture != null -> Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            art != null -> Image(
                art,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.46f).clip(RoundedCornerShape(14.dp))
            )
            icon != null -> Image(icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(height * 0.3f).clip(RoundedCornerShape(18.dp)))
            else -> BasicText(app.label, style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, color = Color.White))
        }
    }
}

/** A quiet hint under the cards: an arrow ([up], down, or OK when null) and what it does. */
@Composable
private fun Hint(up: Boolean?, text: String) {
    val look = LocalHomeLook.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(26.dp)
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                .drawBehind {
                    val c = Color.White.copy(alpha = 0.85f)
                    val w = size.width
                    if (up == null) {
                        drawCircle(c, radius = w * 0.13f)
                    } else {
                        val path = Path().apply {
                            val y0 = if (up) w * 0.58f else w * 0.42f
                            val y1 = if (up) w * 0.40f else w * 0.60f
                            moveTo(w * 0.33f, y0)
                            lineTo(w * 0.5f, y1)
                            lineTo(w * 0.67f, y0)
                        }
                        drawPath(path, c, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
        )
        Spacer(Modifier.width(9.dp))
        BasicText(text, style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f)))
    }
}

/** How wide the card in front is, of the screen. */
private const val CARD_WIDTH = 0.6f

/** The rounding of a card's corners, gone once it has grown to the whole screen. */
private val CORNER = 22.dp

/** The room over each card for its icon and name. */
private val HEADER = 64.dp

/** The remote's keys the switcher answers itself; Back goes to its back handler. */
private val SwitcherKeys = setOf(
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER
)
