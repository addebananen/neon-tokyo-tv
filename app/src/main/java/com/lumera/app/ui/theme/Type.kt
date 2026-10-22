package com.lumera.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lumera.app.R

// Neon Tokyo display face: Kanit (SIL Open Font License, see third_party/fonts)
val KanitFamily = FontFamily(
    Font(R.font.kanit_medium, FontWeight.Medium),
    Font(R.font.kanit_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.kanit_semibold_italic, FontWeight.SemiBold, FontStyle.Italic)
)

// We define a simple hierarchy:
// Title (Big), Body (Normal), Label (Small)
val LumeraTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = KanitFamily,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = KanitFamily,
        fontWeight = FontWeight.Medium,
        fontStyle = FontStyle.Italic,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 28.sp,
        color = TextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = TextSecondary
    )
)