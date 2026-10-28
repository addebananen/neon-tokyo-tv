package com.lumera.app.launcher

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumera.app.R
import com.lumera.app.ui.components.NeonPalette
import com.lumera.app.ui.theme.KanitFamily

// The other faces the styles letter in (SIL Open Font License, see third_party/fonts)
private val OutfitFamily = FontFamily(Font(R.font.outfit_medium, FontWeight.Medium))
private val NunitoFamily = FontFamily(
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_bold_italic, FontWeight.Bold, FontStyle.Italic)
)

/**
 * How a style dresses the home screen: its colours, its type and its tiles. [text] and [dim] are
 * for everything written on the screen and on the tiles; [accent] lights what has the focus and
 * [accent2] the glyphs on the buttons and the rule beside a row's title.
 */
data class HomeLook(
    val accent: Color,
    val accent2: Color,
    val text: Color,
    val dim: Color,
    /** Behind the tabs and the note at the foot of the screen. */
    val pane: Color,
    /** The buttons at the top, resting and in focus. */
    val pill: Color,
    val pillFocused: Color,
    val tileTop: Color,
    val tileBottom: Color,
    val font: FontFamily,
    val weight: FontWeight,
    /** Titles, tabs and the clock lean when the face has an italic. */
    val italic: Boolean,
    val clockSize: TextUnit,
    val clockColor: Color,
    /** The clock's glow, or the soft shadow that keeps it readable on a bright picture. */
    val clockGlow: Color,
    val clockBlur: Float,
    /** A soft shadow under writing that sits right on the picture, or none on a dark one. */
    val shade: Color?,
    val corner: Dp,
    val glow: Dp,
    /** How far an app's own colour is drawn towards [toward], so the tiles sit in the picture. */
    val toward: Color?,
    val pull: Float,
    val letterSpacing: TextUnit = 1.sp,
    val upperTitles: Boolean = true,
    /** Writing lit like neon tubes, glowing in the accents, rather than shaded for readability. */
    val glowingText: Boolean = false
) {
    val tileFill: Brush = Brush.verticalGradient(listOf(tileTop, tileBottom))
    val fontStyle: FontStyle get() = if (italic) FontStyle.Italic else FontStyle.Normal

    /** An app's colour as this style shows it. */
    fun tint(color: Color): Color = if (toward == null) color else lerp(color, toward, pull)
}

/**
 * What moves in a style's picture. Each is a few lights or specks drawn on one layer, and only
 * while the home screen shows.
 */
enum class Ambience { NeonSky, Lake, Calm, Dusk, None }

/**
 * The looks a home profile can wear. Neon is the original: the skyline that takes the colour of
 * the tile in focus. The others each have their own painting, light and type.
 */
enum class HomeStyle(
    val key: String,
    val title: String,
    val blurb: String,
    val backdrop: Int?,
    val ambience: Ambience,
    val look: HomeLook,
    /** Where the light falls in a photo, so the ambience can make it breathe. */
    val light: Int? = null,
    /** A photo drifts slowly closer and back, as a painting would not, towards this point. */
    val drift: Boolean = false,
    val driftX: Float = 0.62f,
    val driftY: Float = 0.3f,
    /** A film that loops behind everything instead of the painting, which shows until it plays. */
    val video: Int? = null
) {
    Neon(
        "neon", "Neon", "Tokyo at night, lit in the colour of what you point at",
        null, Ambience.NeonSky,
        HomeLook(
            accent = NeonPalette.Magenta, accent2 = NeonPalette.Cyan, text = NeonPalette.Text, dim = Color(0xFFB9A6D6),
            pane = Color(0xCC0D0721), pill = Color(0x990D0721), pillFocused = Color(0xCC1A0B36),
            tileTop = Color(0xEB2A0F4F), tileBottom = Color(0xEB0D0721),
            font = KanitFamily, weight = FontWeight.Medium, italic = true,
            clockSize = 60.sp, clockColor = Color(0xFFFFF1F8), clockGlow = NeonPalette.Magenta.copy(alpha = 0.85f), clockBlur = 26f,
            shade = null, corner = 10.dp, glow = 14.dp, toward = null, pull = 0f, glowingText = true
        )
    ),
    Nature(
        "nature", "Nature", "Moraine Lake as the first sun reaches the peaks",
        R.drawable.home_style_nature, Ambience.Lake,
        HomeLook(
            accent = Color(0xFFFFC897), accent2 = Color(0xFF9FE3EC), text = Color(0xFFFFF7EE), dim = Color(0xFFC4D3D6),
            pane = Color(0xB30E1C24), pill = Color(0x990E1C24), pillFocused = Color(0xCC1F3A48),
            tileTop = Color(0xD91A2E3A), tileBottom = Color(0xD90A161E),
            font = OutfitFamily, weight = FontWeight.Medium, italic = false,
            clockSize = 60.sp, clockColor = Color(0xFFFFF8F0), clockGlow = Color(0x99FFB98A), clockBlur = 22f,
            shade = Color(0x660A1020), corner = 12.dp, glow = 12.dp, toward = Color(0xFFFFD2A0), pull = 0.3f,
            letterSpacing = 2.sp
        ),
        light = R.drawable.home_style_nature_glow,
        drift = true
    ),
    Clean(
        "clean", "Clean", "Black, quiet and still",
        R.drawable.home_style_clean, Ambience.Calm,
        HomeLook(
            accent = Color(0xFFFFFFFF), accent2 = Color(0xFF8C8C8C), text = Color(0xFFF2F2F2), dim = Color(0xFF7C7C7C),
            pane = Color(0xCC0B0B0B), pill = Color(0xB30B0B0B), pillFocused = Color(0xFF1E1E1E),
            tileTop = Color(0xF2141414), tileBottom = Color(0xF2080808),
            font = OutfitFamily, weight = FontWeight.Medium, italic = false,
            clockSize = 64.sp, clockColor = Color(0xFFFFFFFF), clockGlow = Color.Transparent, clockBlur = 0f,
            shade = null, corner = 6.dp, glow = 6.dp, toward = Color(0xFFEDEDED), pull = 0.86f,
            letterSpacing = 3.sp
        )
    ),
    Dusk(
        "dusk", "Golden Dusk", "A path under burning evening clouds, from a painting by Angel Ganev",
        R.drawable.home_style_dusk, Ambience.Dusk,
        HomeLook(
            accent = Color(0xFFFFB36B), accent2 = Color(0xFFB78CFF), text = Color(0xFFFFF3EA), dim = Color(0xFFE2BFC8),
            pane = Color(0xB82A1230), pill = Color(0x992A1230), pillFocused = Color(0xD0472250),
            tileTop = Color(0xD93A1A3E), tileBottom = Color(0xD91A0A1E),
            font = NunitoFamily, weight = FontWeight.Bold, italic = false,
            clockSize = 74.sp, clockColor = Color(0xFFFFF3EA), clockGlow = Color(0xAAFF9A70), clockBlur = 22f,
            shade = null, corner = 18.dp, glow = 14.dp, toward = Color(0xFFFFC08A), pull = 0.4f,
            letterSpacing = 1.sp
        ),
        // No slow zoom here: it would push the artist's signature off the corner
        light = R.drawable.home_style_dusk_glow
    ),
    DeepBlue(
        "deepblue", "Deep Blue", "A girl adrift in the light under the sea, fish circling and a whale gliding past, animated by Adrian",
        R.drawable.home_style_deepblue, Ambience.Calm,
        HomeLook(
            accent = Color(0xFF7FF6FF), accent2 = Color(0xFFB9F1FF), text = Color(0xFFF1FDFF), dim = Color(0xFFA9D3E0),
            pane = Color(0xB3041A2E), pill = Color(0x99041A2E), pillFocused = Color(0xCC0B3550),
            tileTop = Color(0xD90A2C46), tileBottom = Color(0xD9031322),
            font = NunitoFamily, weight = FontWeight.Bold, italic = false,
            clockSize = 66.sp, clockColor = Color(0xFFF1FDFF), clockGlow = Color(0xCC00131F), clockBlur = 24f,
            shade = Color(0x99001320), corner = 16.dp, glow = 14.dp, toward = Color(0xFF8FEFFF), pull = 0.35f,
            letterSpacing = 1.5.sp
        ),
        video = R.raw.home_style_deepblue
    ),
    IceLake(
        "icelake", "Ice Lake", "A frozen lake at sundown under streaks of cloud, filmed from Adrian's drone",
        R.drawable.home_style_icelake, Ambience.None,
        HomeLook(
            accent = Color(0xFFFFFFFF), accent2 = Color(0xFFFFC98A), text = Color(0xFFFFFCF7), dim = Color(0xFFD9D2CB),
            pane = Color(0x8C0E131D), pill = Color(0x730E131D), pillFocused = Color(0xB3243046),
            tileTop = Color(0xB31B2333), tileBottom = Color(0xB30B1019),
            font = OutfitFamily, weight = FontWeight.Medium, italic = false,
            clockSize = 64.sp, clockColor = Color(0xFFFFFCF7), clockGlow = Color(0x59000000), clockBlur = 14f,
            shade = Color(0x80000000), corner = 18.dp, glow = 8.dp, toward = Color(0xFFFFE6C8), pull = 0.18f,
            letterSpacing = 0.5.sp, upperTitles = false
        ),
        video = R.raw.home_style_icelake
    );

    companion object {
        fun of(key: String?): HomeStyle = entries.firstOrNull { it.key == key } ?: Neon
    }
}

/** The look of the profile whose home screen shows. */
val LocalHomeLook = staticCompositionLocalOf { HomeStyle.Neon.look }

/**
 * The style's painting as a one frame film, so the TV's video chip shows it at the panel's own
 * resolution (see [PlaneSurface]); the painting itself stands in until then. Neon draws its skyline
 * and Deep Blue and Ice Lake have their films.
 */
val HomeStyle.still: Int?
    get() = when (this) {
        HomeStyle.Nature -> R.raw.home_still_nature
        HomeStyle.Clean -> R.raw.home_still_clean
        HomeStyle.Dusk -> R.raw.home_still_dusk
        else -> null
    }
