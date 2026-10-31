package com.lumera.app.control

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumera.app.R
import com.lumera.app.launcher.Glyph
import com.lumera.app.launcher.GlassInk
import com.lumera.app.launcher.HomeGlass
import com.lumera.app.launcher.NeonGlyph
import com.lumera.app.launcher.ProfileFace
import com.lumera.app.launcher.glassPane
import com.lumera.app.ui.components.NeonPalette

private val Numbers = FontFamily(Font(R.font.oswald_medium, FontWeight.Medium))
private val Body = FontFamily(Font(R.font.outfit_medium, FontWeight.Medium))
private val Ink = GlassInk
private val Soft = Color.White.copy(alpha = 0.62f)
private val Calm = Color(0xFF9B8CFF)
/** Inside each pane: just enough shade for white text over a bright film, the glass stays clear. */
private val Clear = Color(0x2E05060A)

// Sized as Apple TV's: a small cluster in the corner, the film all round it
private const val PANEL_W = 212
private const val LIST_W = 264
private const val GAP = 8
private const val UNIT_H = 44
private val UNIT_W = (PANEL_W - 3 * GAP) / 4f

/** 0 to 1 as the panel comes down, back to 0 as it goes. */
@Composable
private fun entrance(shown: Boolean): Float {
    val value by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        // In on a spring that settles without overshoot, out quicker
        animationSpec = if (shown) spring(dampingRatio = 1f, stiffness = 380f) else tween(160, easing = FastOutSlowInEasing),
        label = "CcEntrance"
    )
    return value
}

/** A soft shadow under white text, gone as the button turns white. */
private fun shade(lit: Float) = Shadow(Color.Black.copy(alpha = 0.5f * (1f - lit)), Offset(0f, 1f), 6f)

/**
 * A button: clear liquid glass lit at its rim at rest, a white plate in focus, the way Apple TV's
 * buttons turn.
 */
private fun Modifier.ccPane(shape: Shape, lit: () -> Float): Modifier =
    drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), Clear) }
        .glassPane(HomeGlass.Liquid, shape, lit).drawBehind {
        val l = lit()
        if (l > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), Color.White.copy(alpha = 0.94f), alpha = l)
    }

/**
 * The Control Center over whatever the TV shows: the picture behind dims a touch towards the
 * corner the panel hangs in, and the panel grows out of that corner.
 */
@Composable
internal fun ControlPanel(model: CcModel, shown: Boolean) {
    val enter = entrance(shown)
    Box(Modifier.fillMaxSize()) {
        // Dim towards the top right, so white text reads over a bright film
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = enter }.drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to Color.Black.copy(alpha = 0.42f),
                        0.5f to Color.Black.copy(alpha = 0.16f),
                        1f to Color.Transparent,
                        center = Offset(size.width, 0f),
                        radius = size.width * 0.5f
                    )
                )
            }
        )
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 32.dp)
                .graphicsLayer {
                    alpha = enter.coerceIn(0f, 1f)
                    val s = 0.9f + 0.1f * enter
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(1f, 0f)
                }
        ) {
            AnimatedContent(
                targetState = model.page,
                transitionSpec = {
                    val deeper = targetState != CcPage.Main
                    // A quiet cross-fade, the new page settling in from a touch smaller or larger
                    (fadeIn(tween(180, delayMillis = 60)) + scaleIn(tween(240, delayMillis = 40, easing = LinearOutSlowInEasing), initialScale = if (deeper) 0.96f else 1.03f)) togetherWith
                        fadeOut(tween(110)) using
                        // Unclipped: a button in focus grows past the panel's edge
                        SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 1f, stiffness = 520f) }
                },
                label = "CcPage"
            ) { page ->
                if (page == CcPage.Main) MainPage(model) else ListPage(model, page)
            }
        }
        if (model.poweringOff) PowerOff()
    }
}

@Composable
private fun MainPage(model: CcModel) {
    Column(Modifier.width(PANEL_W.dp)) {
        Header(model, Modifier)
        Spacer(Modifier.height(10.dp))
        val rows = 4
        Box(Modifier.fillMaxWidth().height((rows * UNIT_H + (rows - 1) * GAP).dp)) {
            CcLayout.CELLS.filter { it.y >= 0f }.forEach { cell ->
                val x = cell.x * (UNIT_W + GAP)
                val y = cell.y * (UNIT_H + GAP)
                val w = cell.w * UNIT_W + (cell.w - 1f) * GAP
                val h = cell.h * UNIT_H + (cell.h - 1f) * GAP
                Box(
                    Modifier
                        .offset(x.dp, y.dp)
                        .size(w.dp, h.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(model, cell.item)
                }
            }
        }
        // Room for the name under a round button in focus
        Spacer(Modifier.height(18.dp))
    }
}

/** The time on the left; the sound in use, the network and the profile on the right. */
@Composable
private fun Header(model: CcModel, modifier: Modifier) {
    Row(modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            model.clock,
            style = TextStyle(fontFamily = Numbers, fontSize = 16.sp, color = Color.White, shadow = shade(0f))
        )
        Spacer(Modifier.weight(1f))
        model.connected?.let { d ->
            NeonGlyph(if (d.kind == com.lumera.app.launcher.BtKind.Earbuds) Glyph.Earbuds else Glyph.Headphones, Color.White, Modifier.size(15.dp), plain = true)
            Spacer(Modifier.width(9.dp))
        }
        NeonGlyph(
            when (model.net.kind) {
                NetInfo.Kind.Wifi -> Glyph.Wifi
                NetInfo.Kind.Ethernet -> Glyph.Ethernet
                NetInfo.Kind.Offline -> Glyph.Offline
            },
            Color.White.copy(alpha = if (model.net.internet) 1f else 0.5f), Modifier.size(15.dp), plain = true
        )
        Spacer(Modifier.width(10.dp))
        ProfileChip(model)
    }
}

@Composable
private fun ProfileChip(model: CcModel) {
    val focused = model.page == CcPage.Main && model.focus == CcItem.Profile
    val lit by animateFloatAsState(if (focused) 1f else 0f, tween(120), label = "CcProfileLit")
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "CcProfileScale")
    val profile = model.profile ?: return
    // Just the face in a ring of glass, as Apple TV shows the user
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .ccPane(CircleShape) { lit }
            .padding(2.dp)
    ) {
        ProfileFace(profile, Modifier.size(24.dp), letterSize = 11.sp)
    }
}

/** One button of the front page, drawn for what it is. */
@Composable
private fun Button(model: CcModel, item: CcItem) {
    val focused = model.page == CcPage.Main && model.focus == item
    val lit by animateFloatAsState(if (focused) 1f else 0f, tween(120), label = "CcLit")
    val pressedAt = model.pressed?.takeIf { it.first == item }?.second
    val base = if (item == CcItem.Power) 1.07f else if (round(item)) 1.13f else 1.07f
    // Grows to the focus and settles there, no wobble
    val scale by animateFloatAsState(if (focused) base else 1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "CcScale")
    // A short dip as OK goes down on it
    val dip = remember { Animatable(1f) }
    LaunchedEffect(pressedAt) {
        if (pressedAt == null) return@LaunchedEffect
        dip.snapTo(0.95f)
        dip.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium))
    }
    val shape: Shape = when {
        item == CcItem.Power -> RoundedCornerShape(24.dp)
        round(item) -> CircleShape
        else -> RoundedCornerShape(50)
    }
    val mod = Modifier
        .graphicsLayer {
            val s = scale * dip.value
            scaleX = s
            scaleY = s
        }
    when {
        item == CcItem.Power -> Box(
            mod.fillMaxSize().ccPane(shape) { lit },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NeonGlyph(Glyph.Power, { lerp(Color.White, Ink, lit) }, Modifier.size(34.dp), plain = true)
                Spacer(Modifier.height(6.dp))
                BasicText("Power Off", style = TextStyle(fontFamily = Body, fontSize = 10.sp, color = lerp(Color.White, Ink.copy(alpha = 0.8f), lit), shadow = shade(lit)))
            }
        }
        round(item) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(mod.size(UNIT_H.dp).ccPane(shape) { lit }, contentAlignment = Alignment.Center) {
                NeonGlyph(glyphOf(item), { lerp(Color.White, Ink, lit) }, Modifier.size(19.dp), plain = true)
            }
            // The name of a round button shows under it while it has the focus
            BasicText(
                labelOf(item),
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = 19.dp).graphicsLayer { alpha = lit },
                style = TextStyle(fontFamily = Body, fontSize = 9.sp, color = Color.White, shadow = Shadow(Color.Black, Offset(0f, 1f), 6f))
            )
        }
        else -> Row(
            mod.fillMaxSize().ccPane(shape) { lit }.padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val on = activeOf(model, item)
            val tint = accentOf(item)
            // The mark sits in a disc that fills with its colour while the thing is on
            Box(
                Modifier.size(30.dp).drawBehind {
                    drawCircle(if (on) tint.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.12f + 0.06f * lit))
                },
                contentAlignment = Alignment.Center
            ) {
                NeonGlyph(glyphOf(item), { if (on) Ink else lerp(Color.White, Ink, lit) }, Modifier.size(16.dp), plain = true)
            }
            Spacer(Modifier.width(6.dp))
            // As on Apple TV, a button that is off says only its name, over two lines if it needs them
            val detail = detailOf(model, item)?.takeIf { it != "Off" }
            Column(Modifier.weight(1f)) {
                BasicText(
                    labelOf(item), maxLines = if (detail == null) 2 else 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Body, fontSize = 10.5.sp, lineHeight = 12.sp, color = lerp(Color.White, Ink, lit), shadow = shade(lit))
                )
                detail?.let {
                    BasicText(
                        it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = Body, fontSize = 9.sp, color = lerp(Soft, Ink.copy(alpha = 0.65f), lit), shadow = shade(lit))
                    )
                }
            }
        }
    }
}

private fun round(item: CcItem) = item == CcItem.Screensaver || item == CcItem.Diagnostics || item == CcItem.Gaming || item == CcItem.Settings

private fun glyphOf(item: CcItem): Glyph = when (item) {
    CcItem.Profile -> Glyph.Star
    CcItem.Power -> Glyph.Power
    CcItem.Wifi -> Glyph.Wifi
    CcItem.Quiet -> Glyph.Moon
    CcItem.Audio -> Glyph.Earbuds
    CcItem.Sleep -> Glyph.Timer
    CcItem.Screensaver -> Glyph.Dream
    CcItem.Diagnostics -> Glyph.Pulse
    CcItem.Gaming -> Glyph.Gamepad
    CcItem.Settings -> Glyph.Gear
}

private fun labelOf(item: CcItem): String = when (item) {
    CcItem.Profile -> "Profile"
    CcItem.Power -> "Power Off"
    CcItem.Wifi -> "Wi-Fi"
    CcItem.Quiet -> "Do Not Disturb"
    CcItem.Audio -> "Sound"
    CcItem.Sleep -> "Sleep Timer"
    CcItem.Screensaver -> "Screensaver"
    CcItem.Diagnostics -> "Picture"
    CcItem.Gaming -> "Gaming HUD"
    CcItem.Settings -> "Settings"
}

private fun accentOf(item: CcItem): Color = when (item) {
    CcItem.Quiet -> Calm
    CcItem.Sleep -> NeonPalette.Sun
    else -> NeonPalette.Cyan
}

private fun activeOf(model: CcModel, item: CcItem): Boolean = when (item) {
    CcItem.Wifi -> model.net.kind != NetInfo.Kind.Offline && model.net.internet
    CcItem.Quiet -> model.quiet
    CcItem.Audio -> model.connected != null
    CcItem.Sleep -> model.sleepLeft > 0
    else -> false
}

private fun detailOf(model: CcModel, item: CcItem): String? = when (item) {
    CcItem.Wifi -> when (model.net.kind) {
        NetInfo.Kind.Ethernet -> "Ethernet"
        NetInfo.Kind.Wifi -> model.net.ssid ?: if (model.net.internet) "Connected" else "No internet"
        NetInfo.Kind.Offline -> "Not connected"
    }
    CcItem.Quiet -> if (model.quiet) "On" else "Off"
    CcItem.Audio -> model.connected?.name ?: "TV Speakers"
    CcItem.Sleep -> if (model.sleepLeft > 0) SleepRules.left(model.sleepLeft) else "Off"
    else -> null
}

/** A list the front page opened: the sound's devices, the networks, the profiles, the timer. */
@Composable
private fun ListPage(model: CcModel, page: CcPage) {
    val rows = model.rows
    val list = rememberLazyListState()
    LaunchedEffect(model.row, page) {
        val target = (model.row - 2).coerceAtLeast(0)
        list.animateScrollToItem(target)
    }
    Column(Modifier.width(LIST_W.dp)) {
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("‹", style = TextStyle(fontFamily = Body, fontSize = 18.sp, color = Color.White, shadow = shade(0f)))
            Spacer(Modifier.width(8.dp))
            BasicText(
                when (page) {
                    CcPage.Audio -> "Sound"
                    CcPage.Wifi -> "Wi-Fi"
                    CcPage.Profiles -> "Who's watching?"
                    CcPage.Sleep -> "Sleep Timer"
                    CcPage.Main -> ""
                },
                style = TextStyle(fontFamily = Body, fontSize = 14.sp, color = Color.White, shadow = shade(0f))
            )
            Spacer(Modifier.weight(1f))
            BasicText(model.clock, style = TextStyle(fontFamily = Numbers, fontSize = 16.sp, color = Color.White, shadow = shade(0f)))
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(rows, key = { _, r -> r.key }) { i, row ->
                if (row.header) HeaderRow(row) else ListRow(model, row, focused = i == model.row)
            }
        }
    }
}

@Composable
private fun HeaderRow(row: CcRow) {
    BasicText(
        row.title,
        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 1.dp),
        style = TextStyle(
            fontFamily = Body, fontSize = if (row.kind == CcRow.Kind.Note) 10.sp else 9.sp,
            letterSpacing = if (row.kind == CcRow.Kind.Note) 0.sp else 1.4.sp,
            color = Color.White.copy(alpha = 0.7f), shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 6f)
        )
    )
}

@Composable
private fun ListRow(model: CcModel, row: CcRow, focused: Boolean) {
    val lit by animateFloatAsState(if (focused) 1f else 0f, tween(120), label = "CcRowLit")
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "CcRowScale")
    val shape = RoundedCornerShape(50)
    val ink = lerp(Color.White, Ink, lit)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .height(if (row.detail != null || row.battery != null) 44.dp else 38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .ccPane(shape) { lit }
            .padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (row.profile != null) {
            ProfileFace(row.profile, Modifier.size(28.dp), letterSize = 13.sp)
        } else {
            val glyph = when (row.kind) {
                CcRow.Kind.Tv -> Glyph.Tv
                CcRow.Kind.Headphones -> Glyph.Headphones
                CcRow.Kind.Earbuds -> Glyph.Earbuds
                CcRow.Kind.Speaker -> Glyph.Speaker
                CcRow.Kind.Wifi -> Glyph.Wifi
                CcRow.Kind.Timer -> Glyph.Timer
                CcRow.Kind.Settings -> Glyph.Gear
                else -> Glyph.Ring
            }
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                if (row.kind == CcRow.Kind.Wifi && row.bars != null) Bars(row.bars, ink) else NeonGlyph(glyph, { ink }, Modifier.size(18.dp), plain = true)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            BasicText(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Body, fontSize = 11.5.sp, color = ink, shadow = shade(lit)))
            val small = lerp(Soft, Ink.copy(alpha = 0.65f), lit)
            if (row.battery != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText("${row.battery} %", style = TextStyle(fontFamily = Body, fontSize = 9.sp, color = small))
                    Spacer(Modifier.width(4.dp))
                    Battery(row.battery, small)
                    row.detail?.let { BasicText("  ·  $it", style = TextStyle(fontFamily = Body, fontSize = 9.sp, color = small)) }
                }
            } else {
                row.detail?.let { BasicText(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Body, fontSize = 9.sp, color = small)) }
            }
        }
        if (row.secure) {
            NeonGlyph(Glyph.Lock, { ink.copy(alpha = 0.7f) }, Modifier.size(12.dp), plain = true)
            Spacer(Modifier.width(8.dp))
        }
        if (row.kind != CcRow.Kind.Settings && row.kind != CcRow.Kind.Note) Tick(row.checked, ink, lit)
    }
}

/** The round mark at a row's end: an empty ring, or a filled one with a tick for what is in use. */
@Composable
private fun Tick(checked: Boolean, ink: Color, lit: Float) {
    Canvas(Modifier.size(18.dp)) {
        val r = size.minDimension / 2f
        if (checked) {
            drawCircle(lerp(Color.White, Ink, lit), r)
            val k = size.minDimension / 100f
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(28f * k, 52f * k); lineTo(44f * k, 67f * k); lineTo(73f * k, 34f * k)
            }
            drawPath(p, lerp(Ink, Color.White, lit), style = Stroke(width = 9f * k, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        } else {
            drawCircle(ink.copy(alpha = 0.75f), r - 1.2.dp.toPx(), style = Stroke(1.8.dp.toPx()))
        }
    }
}

/** A battery drawn as iOS draws one, filled to [level]. */
@Composable
private fun Battery(level: Int, color: Color) {
    Canvas(Modifier.size(width = 18.dp, height = 8.dp)) {
        val body = Size(size.width - 3.dp.toPx(), size.height)
        val r = CornerRadius(3.dp.toPx())
        drawRoundRect(color.copy(alpha = 0.5f), size = body, cornerRadius = r, style = Stroke(1.2.dp.toPx()))
        drawRoundRect(color.copy(alpha = 0.5f), topLeft = Offset(body.width + 0.8.dp.toPx(), size.height * 0.3f), size = Size(1.8.dp.toPx(), size.height * 0.4f), cornerRadius = CornerRadius(1.dp.toPx()))
        val inset = 2.dp.toPx()
        val fill = if (level <= 20) Color(0xFFFF5A5F) else color
        drawRoundRect(
            fill, topLeft = Offset(inset, inset),
            size = Size((body.width - 2 * inset) * level.coerceIn(4, 100) / 100f, body.height - 2 * inset),
            cornerRadius = CornerRadius(1.6.dp.toPx())
        )
    }
}

/** A network's strength as the three arcs of the Wi-Fi mark, the ones it reaches lit. */
@Composable
private fun Bars(bars: Int, color: Color) {
    Canvas(Modifier.size(19.dp)) {
        val k = size.minDimension / 100f
        val w = Stroke(7f * k, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val c = Offset(50f * k, 82f * k)
        val radii = listOf(20f, 40f, 60f)
        drawCircle(color, 5f * k, c)
        radii.forEachIndexed { i, rr ->
            val on = bars >= i + 2 || (i == 0 && bars >= 1)
            val r = rr * k
            drawArc(
                color.copy(alpha = if (on) 1f else 0.25f), startAngle = 225f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r), style = w
            )
        }
    }
}

/** The screen draws in to a bright line and then a dot, as an old TV did when it went off. */
@Composable
private fun BoxScope.PowerOff() {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
    Canvas(Modifier.matchParentSize()) {
        val p = anim.value
        drawRect(Color.Black, alpha = (p * 2.2f).coerceAtMost(1f))
        val h = size.height * (1f - p).let { it * it * it } * 0.6f + 2.dp.toPx()
        val w = size.width * (if (p < 0.7f) 1f else ((1f - p) / 0.3f).coerceAtLeast(0.01f))
        val glow = (1f - ((p - 0.85f) / 0.15f).coerceIn(0f, 1f))
        drawRect(Color.White.copy(alpha = 0.9f * glow), topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f), size = Size(w, h))
    }
}

/** The last minute of the sleep timer, in the corner: any button keeps the TV on. */
@Composable
internal fun SleepWarning(model: CcModel, shown: Boolean) {
    val enter = entrance(shown)
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .padding(top = 24.dp, end = 30.dp)
            .graphicsLayer { alpha = enter; translationX = (1f - enter) * 24.dp.toPx() }
            .background(Color(0xE6101018), shape)
            .border(1.dp, Brush.linearGradient(listOf(NeonPalette.Sun.copy(alpha = 0.8f), NeonPalette.Magenta.copy(alpha = 0.5f))), shape)
            .padding(start = 12.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).background(NeonPalette.Sun, CircleShape), contentAlignment = Alignment.Center) {
            NeonGlyph(Glyph.Timer, Ink, Modifier.size(22.dp), plain = true)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            BasicText("Turning off in ${SleepRules.left(model.sleepLeft)}", style = TextStyle(fontFamily = Body, fontSize = 16.sp, color = Color.White))
            BasicText("Press any button to keep watching", style = TextStyle(fontFamily = Body, fontSize = 12.sp, color = Soft))
        }
    }
}
