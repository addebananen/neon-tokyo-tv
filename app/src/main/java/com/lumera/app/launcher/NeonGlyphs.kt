package com.lumera.app.launcher

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import java.util.Locale

/**
 * The neon line icons of the home screen, the same ones as on the Projectivy banners: drawn
 * on a 100 by 100 grid, lit as a thin tube with a soft glow around it. None of them copies
 * an app's own logo.
 */
enum class Glyph(pathData: String) {
    YouTube(rect(14, 26, 72, 48, 16) + "M44 39 L62 50 L44 61 Z"),
    YtMusic(circle(50, 50, 32) + circle(50, 50, 19) + "M45 42 L58 50 L45 58 Z"),
    Spotify(circle(50, 50, 33) + "M31 40 Q50 33 70 42 M34 51 Q50 45 66 53 M37 61 Q50 57 62 63"),
    Stremio("M50 15 L85 50 L50 85 L15 50 Z M44 38 L61 50 L44 62 Z"),
    NeonTokyo("M24 60 A26 26 0 0 1 76 60 M29 49 H71 M26 55 H74 M12 66 H88 M44 66 L34 88 M56 66 L66 88 M24 80 H76"),
    Tv(rect(16, 34, 68, 48, 12) + rect(24, 42, 40, 32, 7) + "M40 34 L30 16 M60 34 L70 16 M71 62 H77" + circle(74, 50, 2.5f)),
    Parcel("M20 50 L50 62 L80 50 V76 L50 88 L20 76 Z M50 62 V88 M20 50 L12 40 M80 50 L88 40" + circle(50, 28, 14) + "M46 21 L57 28 L46 35 Z"),
    Castle(
        "M18 86 V56 H28 V42 L33 32 L38 42 V56 H62 V42 L67 32 L72 42 V56 H82 V86 Z M44 86 V72 A6 6 0 0 1 56 72 V86 " +
            "M42 56 V44 L50 30 L58 44 V56 M50 30 V18 L58 21 L50 24"
    ),
    Moon("M58 16 A34 34 0 1 0 86 66 A27 27 0 0 1 58 16 Z M74 26 L76 32 L82 34 L76 36 L74 42 L72 36 L66 34 L72 32 Z"),
    Bag("M22 36 H78 L74 86 H26 Z M38 36 V30 A12 12 0 0 1 62 30 V36 M44 52 L61 61 L44 70 Z"),
    Folder("M14 28 H40 L47 36 H86 V80 H14 Z M44 50 L61 59 L44 68 Z"),
    Pc(rect(15, 18, 70, 46, 4) + "M38 82 h24 M50 64 v18 M44 33 L59 41 L44 49 Z"),
    Switch2(rect(12, 24, 18, 52, 9) + rect(70, 24, 18, 52, 9) + rect(33, 26, 34, 48, 3) + circle(21, 38, 3) + circle(79, 60, 3)),
    Xbox360(circle(50, 50, 32) + "M34 31 Q50 47 66 69 M66 31 Q50 47 34 69 M50 9 v8"),
    Xbox(rect(18, 18, 64, 64, 12) + "M33 33 L67 67 M67 33 L33 67" + circle(50, 50, 7)),
    Wii(rect(38, 10, 24, 80, 11) + "M50 22 v14 M43 29 h14" + circle(50, 49, 4.5f) + circle(50, 70, 2) + circle(50, 78, 2)),
    WiiU(rect(8, 27, 84, 46, 16) + rect(31, 33, 38, 30, 2) + circle(19, 41, 4) + circle(81, 59, 4)),
    /** The ring around the first letter of an app the home screen has no icon of its own for. */
    Ring(circle(50, 50, 36)),

    // The buttons at the top of the home screen
    Search(circle(43, 43, 25) + "M62 62 L84 84"),
    Hdmi("M14 34 H86 V54 L74 70 H26 L14 54 Z M30 47 H70"),
    Gear(gear(50f, 50f, 38f, 28f, 8) + circle(50, 50, 11));

    /** The icon's outline on the 100 by 100 grid, made once. */
    val path: Path by lazy { PathParser().parsePathString(pathData).toPath() }
}

private fun rect(x: Number, y: Number, w: Number, h: Number, r: Number): String {
    val l = x.toFloat(); val t = y.toFloat(); val rx = r.toFloat()
    val right = l + w.toFloat(); val bottom = t + h.toFloat()
    return "M${l + rx} $t H${right - rx} A$rx $rx 0 0 1 $right ${t + rx} V${bottom - rx} " +
        "A$rx $rx 0 0 1 ${right - rx} $bottom H${l + rx} A$rx $rx 0 0 1 $l ${bottom - rx} V${t + rx} " +
        "A$rx $rx 0 0 1 ${l + rx} $t Z "
}

/** A cog wheel with [teeth] square teeth between radius [inner] and [outer]. */
internal fun gear(cx: Float, cy: Float, outer: Float, inner: Float, teeth: Int): String {
    val corners = teeth * 4
    return (0 until corners).joinToString(" ", postfix = " Z ") { k ->
        // Two corners out on a tooth, two in between, turned so a tooth points straight up
        val angle = 2.0 * Math.PI * (k - 0.5) / corners - Math.PI / 2
        val r = if (k % 4 < 2) outer else inner
        val x = cx + r * Math.cos(angle)
        val y = cy + r * Math.sin(angle)
        (if (k == 0) "M" else "L") + String.format(Locale.ROOT, "%.2f %.2f", x, y)
    }
}

private fun circle(cx: Number, cy: Number, r: Number): String {
    val x = cx.toFloat(); val y = cy.toFloat(); val radius = r.toFloat()
    return "M${x - radius} $y A$radius $radius 0 1 0 ${x + radius} $y A$radius $radius 0 1 0 ${x - radius} $y Z "
}

/** A neon icon filling its size: a 4 unit tube with a 10 unit glow at a fifth of its strength. */
@Composable
fun NeonGlyph(glyph: Glyph, color: Color, modifier: Modifier = Modifier) {
    Spacer(
        modifier.drawWithCache {
            val k = size.minDimension / 100f
            val left = (size.width - 100f * k) / 2f
            val top = (size.height - 100f * k) / 2f
            val glow = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val tube = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind {
                scale(k, k, pivot = Offset.Zero) {
                    translate(left / k, top / k) {
                        drawPath(glyph.path, color, alpha = 0.22f, style = glow)
                        drawPath(glyph.path, color, style = tube)
                    }
                }
            }
        }
    )
}
