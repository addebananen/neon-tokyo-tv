package com.lumera.app.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** How brightly Neon Tokyo's tile glows while the focus is elsewhere. */
private const val AURA_AT_REST = 0.3f

/**
 * The neon round Neon Tokyo's own tile, whichever icons the profile wears: a soft pink glow
 * while it rests, and in focus a tube that stutters on as a neon sign does, runs through pink,
 * violet and cyan, and has two sparks chasing each other round it. [outer] is the glow spilling
 * out past the tile, drawn behind it; the tube and the sparks are drawn over its edge.
 * The tube is one outline path made once; each frame only strokes it again.
 */
@Composable
internal fun BoxScope.NeonAura(shape: RoundedCornerShape, focused: Boolean, outer: Boolean) {
    val clock = rememberFocusClock(focused)
    val lit = animateFloatAsState(if (focused) 1f else 0f, tween(260), label = "NeonAura")
    val landedAt = remember { FloatArray(1) }
    LaunchedEffect(focused) { if (focused) landedAt[0] = clock.value }
    Spacer(
        Modifier
            .matchParentSize()
            .drawWithCache {
                val edge = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
                val measure = PathMeasure().apply { setPath(edge, false) }
                val length = measure.length
                val trail = Path()
                val trailLength = length * 0.14f
                val wide = Stroke(22.dp.toPx())
                val middle = Stroke(14.dp.toPx())
                val narrow = Stroke(7.dp.toPx())
                val tube = Stroke(2.5.dp.toPx())
                val core = Stroke(1.dp.toPx())
                val sparkGlow = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
                val sparkCore = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round)
                val head = 5.dp.toPx()

                // The stretch of the edge from [from] to [to], going on past the start if it has to
                fun stretch(from: Float, to: Float) {
                    trail.reset()
                    val a = ((from % length) + length) % length
                    val b = a + (to - from)
                    if (b <= length) {
                        measure.getSegment(a, b, trail, true)
                    } else {
                        measure.getSegment(a, length, trail, true)
                        measure.getSegment(0f, b - length, trail, true)
                    }
                }

                onDrawBehind {
                    val l = lit.value
                    val t = clock.value
                    val on = if (l > 0f) neonFlicker(t - landedAt[0]) else 1f
                    val strength = AURA_AT_REST + (1f - AURA_AT_REST) * l * on
                    val color = lerp(NeonTubes[0], neonAt(t), l)
                    if (outer) {
                        // Wide and faint out to narrow and bright, like light in a little haze
                        drawPath(edge, color, alpha = 0.07f * strength, style = wide)
                        drawPath(edge, color, alpha = 0.13f * strength, style = middle)
                        drawPath(edge, color, alpha = 0.24f * strength, style = narrow)
                        return@onDrawBehind
                    }
                    drawPath(edge, color, alpha = 0.9f * strength, style = tube)
                    if (l <= 0f) return@onDrawBehind
                    drawPath(edge, Color.White, alpha = 0.7f * l * on, style = core)
                    // Pink one way round, cyan the other, once round in about three seconds
                    for (k in 0..1) {
                        val run = ((t * 0.32f + k * 0.5f) % 1f) * length
                        val spark = NeonTubes[if (k == 0) 0 else 2]
                        val at: Float
                        if (k == 0) {
                            stretch(run - trailLength, run)
                            at = run
                        } else {
                            val back = length - run
                            stretch(back, back + trailLength)
                            at = back
                        }
                        drawPath(trail, spark, alpha = 0.55f * l * on, style = sparkGlow)
                        drawPath(trail, Color.White, alpha = 0.9f * l * on, style = sparkCore)
                        val point = measure.getPosition(at)
                        drawCircle(spark, radius = head * 1.6f, center = point, alpha = 0.35f * l * on)
                        drawCircle(Color.White, radius = head * 0.45f, center = point, alpha = l * on)
                    }
                }
            }
    )
}
