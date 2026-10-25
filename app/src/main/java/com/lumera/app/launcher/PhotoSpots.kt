package com.lumera.app.launcher

import androidx.compose.ui.graphics.Color

/**
 * Where things are in the style photos, as fractions of the 16:9 picture: found in the photo
 * itself, so the lights that move sit exactly on the lights in it.
 */
internal object PhotoSpots {
    /** City: the lamps strung along the Brooklyn Bridge cables, x, y. */
    val bridgeLamps = floatArrayOf(
        0.0281f, 0.1472f, 0.0708f, 0.1954f, 0.1115f, 0.2398f, 0.0286f, 0.2861f, 0.1057f, 0.3083f,
        0.1141f, 0.338f, 0.1844f, 0.3148f, 0.2948f, 0.4204f, 0.3823f, 0.45f, 0.574f, 0.5167f, 0.5849f,
        0.5102f, 0.6052f, 0.4981f, 0.6833f, 0.5037f, 0.6932f, 0.4972f, 0.7469f, 0.4546f, 0.7828f,
        0.4676f, 0.8078f, 0.5639f
    )

    /** City: lit windows spread over the towers that show around the tiles, x, y. */
    val windows = floatArrayOf(
        0.0005f, 0.7213f, 0.9708f, 0.7694f, 0.5453f, 0.8519f, 0.2167f, 0.5167f, 0.4219f, 0.8343f,
        0.063f, 0.2833f, 0.0182f, 0.2694f, 0.0359f, 0.6519f, 0.5307f, 0.4213f, 0.199f, 0.3889f,
        0.8521f, 0.7046f, 0.2427f, 0.4028f, 0.212f, 0.7333f, 0.0312f, 0.7389f, 0.3464f, 0.7111f,
        0.4333f, 0.8139f, 0.0036f, 0.6213f, 0.3745f, 0.7157f, 0.3927f, 0.7787f, 0.0078f, 0.5139f,
        0.0167f, 0.6481f, 0.1917f, 0.8213f, 0.5333f, 0.7306f, 0.1974f, 0.3981f, 0.313f, 0.7657f,
        0.625f, 0.863f, 0.4104f, 0.4046f, 0.4479f, 0.7343f, 0.2917f, 0.7556f, 0.337f, 0.7435f, 0.4094f,
        0.3898f, 0.4896f, 0.7019f, 0.5312f, 0.4065f, 0.1057f, 0.3083f, 0.1333f, 0.85f, 0.7964f,
        0.3204f, 0.0651f, 0.7037f, 0.4698f, 0.7019f, 0.4917f, 0.5056f, 0.1203f, 0.7019f, 0.3818f,
        0.7481f, 0.2188f, 0.3546f, 0.5318f, 0.3778f, 0.0766f, 0.7361f, 0.9953f, 0.7269f, 0.2391f,
        0.3833f, 0.4286f, 0.4204f, 0.0562f, 0.8074f, 0.1698f, 0.5093f, 0.574f, 0.5167f, 0.6328f,
        0.7954f, 0.6057f, 0.85f, 0.7729f, 0.3046f, 0.5182f, 0.4639f, 0.2656f, 0.375f, 0.8052f, 0.4259f,
        0.0042f, 0.8296f, 0.2479f, 0.3639f, 0.9646f, 0.8028f, 0.9448f, 0.7907f, 0.1188f, 0.725f,
        0.3599f, 0.7787f, 0.7406f, 0.2843f, 0.7667f, 0.2704f, 0.5182f, 0.3991f, 0.4531f, 0.5009f,
        0.5172f, 0.4852f, 0.5182f, 0.4343f, 0.625f, 0.3778f, 0.6479f, 0.4111f, 0.5318f, 0.3722f,
        0.9781f, 0.7769f, 0.0635f, 0.3565f, 0.5333f, 0.7565f, 0.4562f, 0.513f, 0.0005f, 0.7f, 0.6479f,
        0.4426f, 0.7953f, 0.4954f, 0.2276f, 0.7204f, 0.7896f, 0.5148f, 0.3484f, 0.7981f, 0.3214f,
        0.787f, 0.5182f, 0.3269f, 0.2125f, 0.7676f, 0.6354f, 0.5046f, 0.4969f, 0.4639f, 0.624f,
        0.2435f, 0.8531f, 0.7296f, 0.6615f, 0.487f, 0.0286f, 0.6824f
    )

    /** City: each window's light, a little lifted. */
    /** City: each window's light, a little lifted. */
    val windowColours = longArrayOf(
        0xFFE3DCC9, 0xFFFBFEFF, 0xFFFFFFF8, 0xFFFFFFFC, 0xFFFEFCFF, 0xFFEDFFFF, 0xFFC5E9E1, 0xFFFCFEFE,
        0xFFFFFFF9, 0xFFD7DDDC, 0xFFFCFDFA, 0xFFF9FCFB, 0xFFFCF7F2, 0xFFFAF9E5, 0xFFF1F4F0, 0xFFFFF8EF,
        0xFFE2E2D3, 0xFFFDFEFE, 0xFFC6E3E6, 0xFFC5C9CB, 0xFFC9C9C9, 0xFFE2E0CA, 0xFFFCFFFF, 0xFFD1D6D0,
        0xFFF6F8E8, 0xFFEBCCAA, 0xFFFAFFFE, 0xFFFAF5D3, 0xFFFDFDFA, 0xFFF0F7DA, 0xFFCDF9FF, 0xFFFFFDFF,
        0xFFFFFEFB, 0xFFFFFFFE, 0xFFFFFFF9, 0xFFDADCD2, 0xFFEAEEE2, 0xFFFFEEDA, 0xFFFFFFFF, 0xFFEBF4E4,
        0xFFE9FBF8, 0xFFFFFDFF, 0xFFFFFDFF, 0xFFF9FFFD, 0xFFFFFFFD, 0xFFFEFBFD, 0xFFFEFFFF, 0xFFFFFFFD,
        0xFFFFFFFF, 0xFFFEFDFA, 0xFFFAFEFF, 0xFFF7F5FE, 0xFFFFFEFB, 0xFFFDFEFF, 0xFFFEFEFD, 0xFFFFFDEF,
        0xFFFCFFFB, 0xFFFCFEFC, 0xFFFFFFFA, 0xFFFFFFF3, 0xFFF5FBF4, 0xFFD6E4D4, 0xFFFEFEFE, 0xFFFDF6E4,
        0xFFFDFDFD, 0xFFF9F9FF, 0xFFFFFDF9, 0xFFFFFEFD, 0xFFFFFDFA, 0xFFF9FFFF, 0xFFFFFDFF, 0xFFFBFDFD,
        0xFFFCDAA8, 0xFFFFFFFE, 0xFFEFFFFF, 0xFFD6D0C0, 0xFFFAFEFF, 0xFFFDFBE4, 0xFFFBFFFD, 0xFFFDFDFC,
        0xFFFAFBFC, 0xFFFBF3FB, 0xFFFEFEFC, 0xFFFBFBFC, 0xFFFDFCFA, 0xFFFCFFFF, 0xFFFEFDFE, 0xFFFFFBF6,
        0xFFEAFEFF, 0xFFCDCAC6
    )

    /** City: the colour the river takes from the lights above it, left to right in twelve bands. */
    val riverColours = listOf(
        Color(0xFF93D8EB), Color(0xFFAAD4EB), Color(0xFFA7D6EB), Color(0xFFEBEBCB), Color(0xFFDEEBE6), Color(0xFFCADFEB),
        Color(0xFFD3E2EB), Color(0xFF85C7EB), Color(0xFF94D5EB), Color(0xFFCCEBEB), Color(0xFF91BFEB), Color(0xFFEBD0EB)
    )

    /** City: the beacon on top of One World Trade Center. */
    const val SPIRE_X = 0.6458f
    const val SPIRE_Y = 0.0528f

    /** Cottagecore: buttercups in the meadow below the turf-roofed cottage, x, y. */
    val buttercups = floatArrayOf(
        0.4109f, 0.7935f, 0.7875f, 0.8722f, 0.1568f, 0.8176f, 0.1229f, 0.7667f, 0.3812f, 0.8185f,
        0.7802f, 0.7102f, 0.5146f, 0.8065f, 0.4188f, 0.9463f, 0.5771f, 0.8833f, 0.5708f, 0.7537f,
        0.6406f, 0.9963f, 0.9422f, 0.7074f, 0.3073f, 0.9194f, 0.7578f, 0.7806f, 0.4594f, 0.7241f,
        0.0422f, 0.7676f, 0.7922f, 0.7139f, 0.8646f, 0.7306f, 0.9167f, 0.7185f, 0.7776f, 0.7278f,
        0.6812f, 0.7222f, 0.0193f, 0.8176f, 0.7974f, 0.9028f, 0.7198f, 0.7213f, 0.0948f, 0.8306f,
        0.4089f, 0.9296f, 0.0333f, 0.8315f, 0.0495f, 0.8259f, 0.6953f, 0.9093f, 0.8417f, 0.8861f,
        0.0953f, 0.8028f, 0.849f, 0.7194f, 0.487f, 0.787f, 0.0115f, 0.8611f, 0.7802f, 0.9731f,
        0.9109f, 0.9481f, 0.6661f, 0.7306f, 0.5516f, 0.7389f, 0.2453f, 0.738f, 0.601f, 0.7667f,
        0.5479f, 0.762f, 0.2271f, 0.7991f, 0.8901f, 0.8833f, 0.8182f, 0.9296f, 0.2125f, 0.8463f,
        0.5724f, 0.737f, 0.174f, 0.8213f, 0.5f, 0.7204f, 0.6568f, 0.762f, 0.0224f, 0.8398f,
        0.7578f, 0.937f, 0.9365f, 0.9926f, 0.0797f, 0.8222f, 0.8401f, 0.7352f, 0.963f, 0.7028f,
        0.8161f, 0.9944f, 0.3115f, 0.7565f, 0.2828f, 0.7296f, 0.4219f, 0.7111f, 0.8339f, 0.9139f
    )

    /** Cottagecore: the top of the cottage's chimney. */
    const val CHIMNEY_X = 0.8362f
    const val CHIMNEY_Y = 0.3222f

    /** Space: the brightest stars around the Milky Way, x, y. */
    val stars = floatArrayOf(
        0.7146f, 0.0657f, 0.6089f, 0.4324f, 0.8823f, 0.8815f, 0.5708f, 0.2944f, 0.8036f, 0.7463f,
        0.726f, 0.2324f, 0.6734f, 0.1222f, 0.9302f, 0.4139f, 0.7729f, 0.463f, 0.6214f, 0.0583f,
        0.5536f, 0.0315f, 0.6292f, 0.2352f, 0.9005f, 0.2398f, 0.7573f, 0.1204f, 0.9245f, 0.838f,
        0.6448f, 0.3176f, 0.9682f, 0.3074f, 0.1526f, 0.3454f, 0.9396f, 0.1731f, 0.5865f, 0.7454f,
        0.8219f, 0.7843f, 0.6661f, 0.2769f, 0.4635f, 0.9593f, 0.8297f, 0.9046f, 0.7792f, 0.4296f,
        0.7901f, 0.0639f, 0.6333f, 0.725f, 0.1776f, 0.0769f, 0.7729f, 0.7269f, 0.925f, 0.7981f,
        0.9052f, 0.8324f, 0.7932f, 0.3028f, 0.6781f, 0.0463f, 0.4005f, 0.1639f, 0.9292f, 0.7028f,
        0.7578f, 0.5148f, 0.6755f, 0.3269f, 0.6542f, 0.2278f, 0.4547f, 0.1657f, 0.3208f, 0.075f,
        0.987f, 0.8935f, 0.9568f, 0.663f, 0.8505f, 0.7472f, 0.8307f, 0.4204f, 0.7448f, 0.0417f,
        0.6688f, 0.2037f, 0.5536f, 0.3593f, 0.701f, 0.4926f, 0.6812f, 0.7046f, 0.5875f, 0.4769f,
        0.4396f, 0.1917f, 0.776f, 0.4f, 0.6865f, 0.175f, 0.6557f, 0.4574f, 0.9057f, 0.7315f,
        0.7229f, 0.1787f, 0.3677f, 0.2454f, 0.8078f, 0.2028f, 0.5708f, 0.2287f, 0.6578f, 0.1463f,
        0.5781f, 0.438f, 0.7854f, 0.9269f, 0.7026f, 0.3685f, 0.662f, 0.2509f, 0.6562f, 0.3472f,
        0.6182f, 0.2019f, 0.4438f, 0.4583f, 0.7646f, 0.0991f, 0.6807f, 0.2917f, 0.6448f, 0.2074f
    )
}
