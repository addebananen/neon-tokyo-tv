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

    // The rest of the apps on Adrian's TVs, each drawn for what the app is about, not its logo
    Satellite("M20 34 A38 38 0 0 0 66 80 Z M43 57 L70 30" + circle(73, 27, 5) + "M36 70 L28 88 H56"),
    Angel("M36 18 A14 5 0 1 0 64 18 A14 5 0 1 0 36 18 Z " + circle(50, 38, 9) + "M44 52 Q22 40 12 56 Q24 58 26 70 Q36 64 44 64 M56 52 Q78 40 88 56 Q76 58 74 70 Q64 64 56 64 M44 52 L40 86 H60 L56 52"),
    Clapper(rect(16, 42, 68, 42, 6) + "M16 42 L80 28 L77 15 L13 29 Z M31 26 L37 37 M49 22 L55 33 M67 18 L73 29 M44 54 L60 63 L44 72 Z"),
    Lantern("M40 10 H60 M50 10 V18 M24 34 Q24 18 50 18 Q76 18 76 34 V66 Q76 82 50 82 Q24 82 24 66 Z M38 19 Q30 50 38 81 M62 19 Q70 50 62 81 M24 34 H76 M24 66 H76 M50 82 V92"),
    Tube("M28 30 H70 A10 20 0 0 1 70 70 H28 A10 20 0 0 1 28 30 Z M70 30 A10 20 0 0 0 70 70 M36 40 L54 50 L36 60 Z M86 14 L82 24 M92 28 L84 30"),
    Ticket("M14 32 H86 V42 A8 8 0 0 0 86 58 V68 H14 V58 A8 8 0 0 0 14 42 Z M66 34 V40 M66 47 V53 M66 60 V66 M32 42 L48 50 L32 58 Z"),
    Fish("M18 48 Q42 22 68 48 Q42 74 18 48 Z M68 48 L86 34 V62 Z" + circle(32, 44, 2.5) + "M22 82 Q30 77 38 82 Q46 87 54 82 Q62 77 70 82"),
    Tower("M50 34 L36 88 M50 34 L64 88 M42 64 H58 M39 76 H61 M45 50 H55" + circle(50, 26, 5) + "M38 14 A16 16 0 0 0 38 38 M62 14 A16 16 0 0 1 62 38 M28 6 A26 26 0 0 0 28 46 M72 6 A26 26 0 0 1 72 46"),
    Mic(rect(38, 12, 24, 40, 12) + "M28 40 A22 22 0 0 0 72 40 M50 62 V80 M36 84 H64 M44 24 H56 M44 32 H56"),
    Arcade(rect(14, 62, 72, 24, 6) + "M44 62 L36 32" + circle(34, 24, 8) + circle(62, 74, 4) + circle(75, 74, 4)),
    Popcorn("M28 46 L36 88 H64 L72 46 Z M44 46 L46 88 M56 46 L54 88 M28 46 A10 10 0 0 1 38 30 A12 12 0 0 1 62 30 A10 10 0 0 1 72 46"),
    Gamepad("M27 34 H73 A16 16 0 0 1 88 52 L85 70 A10 10 0 0 1 67 74 L61 64 H39 L33 74 A10 10 0 0 1 15 70 L12 52 A16 16 0 0 1 27 34 Z M30 46 V58 M24 52 H36" + circle(66, 48, 3) + circle(74, 56, 3)),
    Ball(circle(58, 52, 24) + "M48 58 Q58 66 68 58" + circle(50, 46, 2) + circle(64, 46, 2) + "M10 40 H24 M8 52 H22 M12 64 H26"),
    Download("M50 14 V60 M32 44 L50 62 L68 44 M18 66 V84 H82 V66"),
    LiveTv(rect(12, 22, 76, 50, 6) + "M38 86 H62 M50 72 V86" + circle(50, 47, 5) + "M39 36 A15 15 0 0 0 39 58 M61 36 A15 15 0 0 1 61 58"),
    Globe(circle(50, 50, 34) + "M16 50 H84 M50 16 A17 34 0 0 1 50 84 A17 34 0 0 1 50 16 M22 32 H78 M22 68 H78"),
    Sliders("M16 30 H84 M16 50 H84 M16 70 H84" + circle(36, 30, 6) + circle(64, 50, 6) + circle(44, 70, 6)),
    Sparkle(rect(18, 18, 64, 64, 8) + "M50 30 L55 45 L70 50 L55 55 L50 70 L45 55 L30 50 L45 45 Z"),
    Picture(rect(12, 22, 76, 56, 6) + "M12 70 L36 46 L52 62 L62 52 L88 74" + circle(64, 38, 6)),
    Shield("M50 12 L82 24 V48 Q82 76 50 90 Q18 76 18 48 V24 Z M36 50 L46 60 L66 40"),
    Cast("M14 40 V26 A6 6 0 0 1 20 20 H80 A6 6 0 0 1 86 26 V66 A6 6 0 0 1 80 72 H52 M14 54 A18 18 0 0 1 32 72 M14 64 A8 8 0 0 1 22 72" + circle(15, 71, 1.5)),
    Book("M50 26 Q34 16 14 20 V80 Q34 76 50 86 Q66 76 86 80 V20 Q66 16 50 26 Z M50 26 V86 M24 36 H40 M24 48 H40 M60 36 H76 M60 48 H76"),
    Key(circle(30, 50, 16) + circle(30, 50, 5) + "M46 50 H88 M76 50 V62 M86 50 V58"),
    Layers("M50 16 L86 34 L50 52 L14 34 Z M14 50 L50 68 L86 50 M14 66 L50 84 L86 66"),
    Headphones("M18 62 V50 A32 32 0 0 1 82 50 V62" + rect(12, 56, 18, 28, 7) + rect(70, 56, 18, 28, 7)),
    Channel(rect(20, 32, 60, 44, 6) + "M28 24 H72 M36 16 H64 M44 44 L60 54 L44 64 Z"),
    Speaker(rect(28, 10, 44, 80, 8) + circle(50, 62, 15) + circle(50, 62, 4) + circle(50, 29, 7)),
    Projector(circle(32, 30, 12) + circle(60, 26, 14) + rect(16, 46, 56, 32, 6) + "M72 56 L88 48 V76 L72 68 M30 78 L24 90 M58 78 L64 90" + circle(32, 30, 3) + circle(60, 26, 3)),
    Lock(rect(24, 44, 52, 42, 8) + "M34 44 V32 A16 16 0 0 1 66 32 V44" + circle(50, 62, 5) + "M50 67 V74"),
    Server(rect(18, 16, 64, 20, 5) + rect(18, 40, 64, 20, 5) + rect(18, 64, 64, 20, 5) + circle(29, 26, 2.5) + circle(29, 50, 2.5) + circle(29, 74, 2.5) + "M58 26 H72 M58 50 H72 M58 74 H72"),
    Cone("M36 82 L46 18 H54 L64 82 M40 60 H60 M43 40 H57 M22 84 H78"),
    Grid(rect(14, 18, 32, 28, 5) + rect(54, 18, 32, 28, 5) + rect(14, 54, 32, 28, 5) + rect(54, 54, 32, 28, 5)),
    Kite("M50 10 L72 36 L50 64 L28 36 Z M28 36 H72 M50 10 V64 M50 64 Q40 72 48 78 Q56 84 46 90"),
    Onigiri("M50 16 Q60 16 80 62 Q86 82 66 82 H34 Q14 82 20 62 Q40 16 50 16 Z M38 82 V60 H62 V82"),
    Star("M50 12 L60 38 L88 39 L66 56 L74 84 L50 68 L26 84 L34 56 L12 39 L40 38 Z"),

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
    Gear(gear(50f, 50f, 38f, 28f, 8) + circle(50, 50, 11)),
    Update("M50 16 V60 M33 44 L50 61 L67 44 M20 68 V82 H80 V68"),
    Bluetooth("M30 34 L68 66 L50 84 V16 L68 34 L30 66"),

    // Earbuds, as AirPods look: two rounded buds, each with its stem hanging down
    Earbuds(
        "M30 20 A13 13 0 1 1 30 46 A13 13 0 0 1 30 20 Z M37 40 V80 A5 5 0 0 1 27 80 V44 " +
            "M70 20 A13 13 0 1 0 70 46 A13 13 0 0 0 70 20 Z M63 40 V80 A5 5 0 0 0 73 80 V44"
    );

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
