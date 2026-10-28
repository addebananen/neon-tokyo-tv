package com.lumera.app.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumera.app.R
import com.lumera.app.ui.components.NeonPalette
import com.lumera.app.ui.components.NeonText
import com.lumera.app.ui.theme.KanitFamily

private val Numbers = FontFamily(Font(R.font.oswald_medium, FontWeight.Medium))
private val Body = FontFamily(Font(R.font.outfit_medium, FontWeight.Medium))
private val Glass = Color(0xF2140A2A)
private val Card = Color(0x0FFFFFFF)
private val Dim = NeonPalette.Lilac.copy(alpha = 0.78f)
private val Faint = NeonPalette.Lilac.copy(alpha = 0.45f)

/** 0 to 1 as the overlay comes in, back to 0 as it goes. */
@Composable
private fun entrance(shown: Boolean, inMs: Int, outMs: Int): Float {
    val value by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (shown) tween(inMs, easing = LinearOutSlowInEasing) else tween(outMs, easing = FastOutSlowInEasing),
        label = "OverlayEntrance"
    )
    return value
}

/** Each part comes a beat after the one before it, as the panel opens. */
private fun stagger(enter: Float, index: Int): Float = ((enter * 1.35f) - index * 0.08f).coerceIn(0f, 1f)

/**
 * Picture diagnostics: the signal that comes in beside what the panel makes of it, then HDR,
 * gaming, sound and latency. A dot tells how each value is known.
 */
@Composable
internal fun DiagnosticsPanel(report: SignalReport?, shown: Boolean) {
    val enter = entrance(shown, inMs = 260, outMs = 190)
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .padding(horizontal = 22.dp, vertical = 16.dp)
            .graphicsLayer {
                alpha = enter
                translationX = (1f - enter) * 32.dp.toPx()
                val s = 0.97f + 0.03f * enter
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(1f, 0.5f)
            }
    ) {
        Column(
            Modifier
                .width(468.dp)
                .background(Glass, shape)
                .border(
                    1.2.dp,
                    Brush.linearGradient(listOf(NeonPalette.Magenta.copy(alpha = 0.8f), NeonPalette.Cyan.copy(alpha = 0.5f))),
                    shape
                )
                .padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 11.dp)
        ) {
            Header("PICTURE DIAGNOSTICS", report?.source ?: "Reading the TV…", Modifier.graphicsLayer { alpha = stagger(enter, 0) })
            Spacer(Modifier.height(10.dp))
            if (report != null) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).graphicsLayer { alpha = stagger(enter, 1) }) {
                    SectionCard(report.signal, NeonPalette.Magenta)
                    FlowArrow()
                    SectionCard(report.panel, NeonPalette.Cyan)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min).graphicsLayer { alpha = stagger(enter, 2) },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionCard(report.hdr, NeonPalette.Sun)
                    SectionCard(report.game, Color(0xFF86FF6A))
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min).graphicsLayer { alpha = stagger(enter, 3) },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionCard(report.sound, Color(0xFFB98CFF))
                    SectionCard(report.latency, Color(0xFF6AB8FF))
                }
                Spacer(Modifier.height(9.dp))
                Legend(Modifier.graphicsLayer { alpha = stagger(enter, 4) })
            }
        }
    }
}

@Composable
private fun Header(kicker: String, title: String, modifier: Modifier = Modifier, accent: Color = NeonPalette.Cyan) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BasicText(
                kicker,
                style = TextStyle(fontFamily = Body, fontSize = 8.5.sp, letterSpacing = 2.6.sp, color = accent)
            )
            NeonText(
                text = title,
                style = TextStyle(
                    fontFamily = KanitFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontStyle = FontStyle.Italic,
                    fontSize = 18.sp,
                    color = NeonPalette.Text,
                    shadow = Shadow(NeonPalette.Magenta.copy(alpha = 0.75f), blurRadius = 16f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        LivePill()
    }
}

/** A dot that breathes while the readings are live. */
@Composable
private fun LivePill() {
    val pulse by rememberInfiniteTransition(label = "Live").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "LivePulse"
    )
    Row(
        Modifier
            .background(NeonPalette.Cyan.copy(alpha = 0.1f), RoundedCornerShape(50))
            .border(0.6.dp, NeonPalette.Cyan.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(
            Modifier
                .size(5.dp)
                .graphicsLayer { alpha = pulse }
                .background(NeonPalette.Cyan, RoundedCornerShape(50))
        )
        Spacer(Modifier.width(5.dp))
        BasicText("LIVE", style = TextStyle(fontFamily = Body, fontSize = 8.sp, letterSpacing = 1.6.sp, color = NeonPalette.Cyan))
    }
}

@Composable
private fun RowScope.SectionCard(section: Section, accent: Color) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(Card, shape)
            .border(0.8.dp, accent.copy(alpha = 0.28f), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                Modifier
                    .size(width = 10.dp, height = 2.dp)
                    .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.2f))), RoundedCornerShape(50))
            )
            Spacer(Modifier.width(6.dp))
            BasicText(
                section.title.uppercase(),
                style = TextStyle(fontFamily = Body, fontSize = 8.sp, letterSpacing = 2.sp, color = accent)
            )
        }
        section.readings.forEach { ReadingRow(it, accent) }
    }
}

@Composable
private fun ReadingRow(reading: Reading, accent: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HowDot(reading.how, accent)
            Spacer(Modifier.width(5.dp))
            BasicText(
                reading.label,
                maxLines = 1,
                style = TextStyle(fontFamily = Body, fontSize = 10.sp, color = Dim)
            )
            Spacer(Modifier.width(8.dp).weight(1f))
            BasicText(
                reading.value,
                modifier = Modifier.weight(1.6f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontFamily = Numbers,
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.End,
                    color = if (reading.how == How.Unknown) Faint else NeonPalette.Text
                )
            )
        }
        reading.note?.let { note ->
            BasicText(
                note,
                modifier = Modifier.padding(start = 10.dp).fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Body, fontSize = 7.5.sp, color = Faint, textAlign = TextAlign.End)
            )
        }
    }
}

/** Measured: a lit dot. Told by the TV: a ring. Not to be had: a dash. */
@Composable
private fun HowDot(how: How, accent: Color) {
    Spacer(
        Modifier
            .size(5.dp)
            .drawBehind {
                when (how) {
                    How.Measured -> {
                        drawCircle(NeonPalette.Cyan.copy(alpha = 0.3f), radius = size.minDimension)
                        drawCircle(NeonPalette.Cyan, radius = size.minDimension / 2)
                    }
                    How.Reported -> drawCircle(accent.copy(alpha = 0.85f), radius = size.minDimension / 2 - 0.5f, style = Stroke(1.dp.toPx()))
                    How.Unknown -> drawLine(Faint, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx(), StrokeCap.Round)
                }
            }
    )
}

/** Signal in, panel out: a lit arrow between the two. */
@Composable
private fun FlowArrow() {
    Spacer(
        Modifier
            .width(18.dp)
            .fillMaxHeight()
            .drawBehind {
                val y = size.height / 2
                val w = 1.4.dp.toPx()
                val brush = Brush.horizontalGradient(listOf(NeonPalette.Magenta, NeonPalette.Cyan))
                drawLine(brush, Offset(3.dp.toPx(), y), Offset(size.width - 4.dp.toPx(), y), w, StrokeCap.Round)
                val head = Path().apply {
                    moveTo(size.width - 8.dp.toPx(), y - 4.dp.toPx())
                    lineTo(size.width - 3.dp.toPx(), y)
                    lineTo(size.width - 8.dp.toPx(), y + 4.dp.toPx())
                }
                drawPath(head, NeonPalette.Cyan, style = Stroke(w, cap = StrokeCap.Round))
            }
    )
}

@Composable
private fun Legend(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HowDot(How.Measured, NeonPalette.Cyan)
        LegendText("measured")
        HowDot(How.Reported, NeonPalette.Lilac)
        LegendText("reported by the TV")
        HowDot(How.Unknown, NeonPalette.Lilac)
        LegendText("not available")
        Spacer(Modifier.weight(1f))
        BasicText("Back to close", style = TextStyle(fontFamily = Body, fontSize = 8.sp, color = Faint))
    }
}

@Composable
private fun LegendText(text: String) {
    BasicText(text, modifier = Modifier.padding(start = 4.dp, end = 10.dp), style = TextStyle(fontFamily = Body, fontSize = 8.sp, color = Dim))
}

/**
 * The gaming HUD: one slim strip at the top right with what matters while playing. It goes by
 * itself; the lit line under it shows the time left.
 */
@Composable
internal fun GamingHud(report: SignalReport?, shown: Boolean, color: Long?, left: () -> Float) {
    val enter = entrance(shown, inMs = 220, outMs = 170)
    val accent = remember(color) { color?.let { Color(it) } ?: NeonPalette.Magenta }
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .padding(top = 16.dp, end = 20.dp, start = 20.dp, bottom = 16.dp)
            .graphicsLayer {
                alpha = enter
                translationY = (1f - enter) * -14.dp.toPx()
                val s = 0.96f + 0.04f * enter
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(1f, 0f)
            }
    ) {
        Column(
            Modifier
                .background(Glass, shape)
                .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), NeonPalette.Cyan.copy(alpha = 0.4f))), shape)
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 8.dp, top = 9.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(
                    Modifier
                        .size(width = 3.dp, height = 28.dp)
                        .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.25f))), RoundedCornerShape(50))
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.widthIn(max = 150.dp)) {
                    BasicText("GAMING HUD", style = TextStyle(fontFamily = Body, fontSize = 7.5.sp, letterSpacing = 2.2.sp, color = accent))
                    NeonText(
                        text = report?.source ?: "…",
                        style = TextStyle(
                            fontFamily = KanitFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontStyle = FontStyle.Italic,
                            fontSize = 13.sp,
                            color = NeonPalette.Text,
                            shadow = Shadow(accent.copy(alpha = 0.7f), blurRadius = 12f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(6.dp))
                report?.hud.orEmpty().forEachIndexed { index, item ->
                    HudChip(item, accent, Modifier.graphicsLayer {
                        val a = stagger(enter, index + 1)
                        alpha = a
                        translationY = (1f - a) * -6.dp.toPx()
                    })
                }
            }
            // Time left before it goes
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 6.dp)
                    .height(1.5.dp)
                    .graphicsLayer {
                        scaleX = left()
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .drawBehind {
                        drawRoundRect(
                            Brush.horizontalGradient(listOf(accent.copy(alpha = 0.2f), accent, lerp(accent, Color.White, 0.5f))),
                            cornerRadius = CornerRadius(size.height)
                        )
                    }
            )
        }
    }
}

@Composable
private fun HudChip(item: Reading, accent: Color, modifier: Modifier = Modifier, minWidth: Dp = 40.dp) {
    val unknown = item.how == How.Unknown
    Column(
        modifier
            .padding(horizontal = 3.dp)
            .widthIn(min = minWidth)
            .background(if (unknown) Color.Transparent else Card, RoundedCornerShape(9.dp))
            .border(0.6.dp, (if (unknown) Faint else accent).copy(alpha = if (unknown) 0.25f else 0.3f), RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.how == How.Measured) {
                HowDot(How.Measured, accent)
                Spacer(Modifier.width(3.dp))
            }
            BasicText(item.label, style = TextStyle(fontFamily = Body, fontSize = 6.5.sp, letterSpacing = 1.4.sp, color = if (unknown) Faint else Dim))
        }
        BasicText(
            item.value,
            maxLines = 1,
            style = TextStyle(
                fontFamily = Numbers,
                fontSize = 13.sp,
                color = if (unknown) Faint else NeonPalette.Text,
                shadow = if (unknown) null else Shadow(accent.copy(alpha = 0.45f), blurRadius = 8f)
            )
        )
    }
}
