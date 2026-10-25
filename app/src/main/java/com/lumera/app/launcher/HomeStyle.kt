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
private val CormorantFamily = FontFamily(
    Font(R.font.cormorant_semibold, FontWeight.SemiBold),
    Font(R.font.cormorant_semibold_italic, FontWeight.SemiBold, FontStyle.Italic)
)
private val OswaldFamily = FontFamily(Font(R.font.oswald_medium, FontWeight.Medium))

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
enum class Ambience { NeonSky, GoogleGlow, Lake, Petals, Calm, CityLights, Glory, Speed, NightRain, LoFi, Meadow, Forest, Cosmos }

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
    val driftY: Float = 0.3f
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
    GoogleTv(
        "google_tv", "Google TV", "Calm charcoal and soft colour, like the TV's own home screen",
        R.drawable.home_style_googletv, Ambience.GoogleGlow,
        HomeLook(
            accent = Color(0xFFE8EAED), accent2 = Color(0xFF8AB4F8), text = Color(0xFFE8EAED), dim = Color(0xFF9AA0A6),
            pane = Color(0xB3303134), pill = Color(0x99303134), pillFocused = Color(0xFF5F6368),
            tileTop = Color(0xE63C4043), tileBottom = Color(0xE6282A2D),
            font = OutfitFamily, weight = FontWeight.Medium, italic = false,
            clockSize = 54.sp, clockColor = Color(0xFFF1F3F4), clockGlow = Color(0x66000000), clockBlur = 12f,
            shade = Color(0x55000000), corner = 14.dp, glow = 10.dp, toward = null, pull = 0f,
            letterSpacing = 0.4.sp, upperTitles = false
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
    Anime(
        "anime", "Anime", "A summer afternoon under towering clouds",
        R.drawable.home_style_anime, Ambience.Petals,
        HomeLook(
            accent = Color(0xFFFFFFFF), accent2 = Color(0xFFFFB7D5), text = Color(0xFFFFFFFF), dim = Color(0xFFD9ECFF),
            pane = Color(0x99082450), pill = Color(0x80082450), pillFocused = Color(0xCC1450A0),
            tileTop = Color(0xD9174A8E), tileBottom = Color(0xD90A2450),
            font = NunitoFamily, weight = FontWeight.Bold, italic = false,
            clockSize = 62.sp, clockColor = Color(0xFFFFFFFF), clockGlow = Color(0xAA0B3D91), clockBlur = 18f,
            shade = Color(0x880B2A60), corner = 16.dp, glow = 14.dp, toward = Color(0xFFBFE4FF), pull = 0.25f,
            letterSpacing = 1.5.sp
        )
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
    City(
        "city", "City", "The Brooklyn Bridge and lower Manhattan at night",
        R.drawable.home_style_city, Ambience.CityLights,
        HomeLook(
            accent = Color(0xFFFFC27A), accent2 = Color(0xFF8FD3FF), text = Color(0xFFF6F8FF), dim = Color(0xFFB7BEDA),
            pane = Color(0xB30A0E2E), pill = Color(0x990A0E2E), pillFocused = Color(0xCC1F2766),
            tileTop = Color(0xD9161B48), tileBottom = Color(0xD9080A22),
            font = OswaldFamily, weight = FontWeight.Medium, italic = false,
            clockSize = 64.sp, clockColor = Color(0xFFFFF4E6), clockGlow = Color(0x99FF9A5A), clockBlur = 20f,
            shade = Color(0x660A0820), corner = 4.dp, glow = 12.dp, toward = Color(0xFFFFC08A), pull = 0.3f,
            letterSpacing = 2.5.sp
        ),
        light = R.drawable.home_style_city_glow,
        drift = true
    ),
    Faith(
        "faith", "Faith", "A cross on the hilltop against the setting sun",
        R.drawable.home_style_faith, Ambience.Glory,
        HomeLook(
            accent = Color(0xFFE8C27A), accent2 = Color(0xFFF3E1B8), text = Color(0xFFFBF1DC), dim = Color(0xFFC9AF88),
            pane = Color(0xB3201208), pill = Color(0x99201208), pillFocused = Color(0xCC3E2812),
            tileTop = Color(0xE02E1D10), tileBottom = Color(0xE0140B05),
            font = CormorantFamily, weight = FontWeight.SemiBold, italic = true,
            clockSize = 72.sp, clockColor = Color(0xFFFFF4DC), clockGlow = Color(0xAAE8B060), clockBlur = 20f,
            shade = Color(0x88140A04), corner = 4.dp, glow = 12.dp, toward = Color(0xFFE8C27A), pull = 0.7f,
            letterSpacing = 1.5.sp
        ),
        light = R.drawable.home_style_faith_glow,
        drift = true
    ),
    Car(
        "car", "Car", "A white racing coupé on the circuit at golden hour",
        R.drawable.home_style_car_track, Ambience.Speed,
        HomeLook(
            accent = Color(0xFFFFB766), accent2 = Color(0xFFFFE2A6), text = Color(0xFFFFFFFF), dim = Color(0xFFE9CFB4),
            pane = Color(0xB3281C22), pill = Color(0x99281C22), pillFocused = Color(0xCC4A3236),
            tileTop = Color(0xD9332428), tileBottom = Color(0xD9140E10),
            font = OswaldFamily, weight = FontWeight.Medium, italic = true,
            clockSize = 66.sp, clockColor = Color(0xFFFFFFFF), clockGlow = Color(0xAAFF9A40), clockBlur = 20f,
            shade = Color(0x77201014), corner = 6.dp, glow = 12.dp, toward = Color(0xFFFFC27A), pull = 0.35f,
            letterSpacing = 2.sp
        )
    ),
    CarNight(
        "car_night", "Car at night", "A dark supercar coming down a wet road in the rain",
        R.drawable.home_style_car_night, Ambience.NightRain,
        HomeLook(
            accent = Color(0xFF9FD8FF), accent2 = Color(0xFFE6F4FF), text = Color(0xFFF4F8FA), dim = Color(0xFFAFBDC2),
            pane = Color(0xB30A0F10), pill = Color(0x990A0F10), pillFocused = Color(0xCC1A2A30),
            tileTop = Color(0xE0141B1C), tileBottom = Color(0xE0050808),
            font = OswaldFamily, weight = FontWeight.Medium, italic = true,
            clockSize = 66.sp, clockColor = Color(0xFFFFFFFF), clockGlow = Color(0xAA8FCBFF), clockBlur = 22f,
            shade = null, corner = 6.dp, glow = 14.dp, toward = Color(0xFFBFE4FF), pull = 0.45f,
            letterSpacing = 2.sp
        ),
        drift = true,
        driftX = 0.475f,
        driftY = 0.33f
    ),
    LoFi(
        "lofi", "Cozy Lo-Fi", "A little room on a rainy night, the cat watching the drops",
        R.drawable.home_style_lofi, Ambience.LoFi,
        HomeLook(
            accent = Color(0xFFFFC874), accent2 = Color(0xFFC3A4FF), text = Color(0xFFFFF1E6), dim = Color(0xFFCBB6C8),
            pane = Color(0xB31D1524), pill = Color(0x991D1524), pillFocused = Color(0xCC3A2638),
            tileTop = Color(0xD92C1E2C), tileBottom = Color(0xD9140E16),
            font = NunitoFamily, weight = FontWeight.Bold, italic = false,
            clockSize = 62.sp, clockColor = Color(0xFFFFF1E6), clockGlow = Color(0x99FF9D5C), clockBlur = 22f,
            shade = null, corner = 14.dp, glow = 12.dp, toward = Color(0xFFFFD2A8), pull = 0.3f,
            letterSpacing = 1.5.sp
        )
    ),
    Cottage(
        "cottage", "Cottagecore", "A turf-roofed cottage above the sea, buttercups in the grass",
        R.drawable.home_style_cottage, Ambience.Meadow,
        HomeLook(
            accent = Color(0xFF4F6B2E), accent2 = Color(0xFFC9981C), text = Color(0xFF23272B), dim = Color(0xFF5E6458),
            pane = Color(0xD9F7F8F4), pill = Color(0xCCF7F8F4), pillFocused = Color(0xFFFFFFFF),
            tileTop = Color(0xEBFDFDFA), tileBottom = Color(0xEBEDEFE6),
            font = CormorantFamily, weight = FontWeight.SemiBold, italic = true,
            clockSize = 74.sp, clockColor = Color(0xFF23272B), clockGlow = Color(0x99FFFFFF), clockBlur = 18f,
            shade = Color(0xAAFFFFFF), corner = 12.dp, glow = 10.dp, toward = Color(0xFF3E5226), pull = 0.45f,
            letterSpacing = 1.5.sp
        ),
        drift = true,
        driftX = 0.5f,
        driftY = 0.35f
    ),
    Naturecore(
        "naturecore", "Naturecore", "An old forest of ferns and moss, the sun coming through the trees",
        R.drawable.home_style_naturecore, Ambience.Forest,
        HomeLook(
            accent = Color(0xFFF2C66D), accent2 = Color(0xFFB9D98A), text = Color(0xFFFBF6E6), dim = Color(0xFFC7C9A8),
            pane = Color(0xB30E1A0E), pill = Color(0x990E1A0E), pillFocused = Color(0xCC24361C),
            tileTop = Color(0xD91C2A16), tileBottom = Color(0xD90A1208),
            font = CormorantFamily, weight = FontWeight.SemiBold, italic = false,
            clockSize = 76.sp, clockColor = Color(0xFFFFF8E6), clockGlow = Color(0x99F2C66D), clockBlur = 20f,
            shade = Color(0x88081006), corner = 14.dp, glow = 12.dp, toward = Color(0xFFE2D08A), pull = 0.35f,
            letterSpacing = 1.5.sp
        ),
        drift = true,
        driftX = 0.45f,
        driftY = 0.38f
    ),
    Space(
        "space", "Space Infinitum", "The Milky Way, nothing else",
        R.drawable.home_style_space, Ambience.Cosmos,
        HomeLook(
            accent = Color(0xFFDCE4FF), accent2 = Color(0xFFE9C9A8), text = Color(0xFFF4F6FF), dim = Color(0xFF9AA3BF),
            pane = Color(0xB3060A14), pill = Color(0x80060A14), pillFocused = Color(0xCC1A2238),
            tileTop = Color(0xC0101626), tileBottom = Color(0xC005070E),
            font = OutfitFamily, weight = FontWeight.Medium, italic = false,
            clockSize = 64.sp, clockColor = Color(0xFFF4F6FF), clockGlow = Color(0x668FA8FF), clockBlur = 26f,
            shade = null, corner = 18.dp, glow = 14.dp, toward = Color(0xFFDCE4FF), pull = 0.55f,
            letterSpacing = 4.sp
        ),
        light = R.drawable.home_style_space_glow,
        drift = true,
        driftX = 0.7f,
        driftY = 0.2f
    );

    companion object {
        fun of(key: String?): HomeStyle = entries.firstOrNull { it.key == key } ?: Neon
    }
}

/** The look of the profile whose home screen shows. */
val LocalHomeLook = staticCompositionLocalOf { HomeStyle.Neon.look }
