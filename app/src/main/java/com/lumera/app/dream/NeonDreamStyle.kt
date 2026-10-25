package com.lumera.app.dream

import android.content.Context
import android.view.View
import com.lumera.app.dream.drive.CityDriveView
import com.lumera.app.launcher.HomeDream

/**
 * Which screensaver runs: the style of the profile on Neon Home, the night drive through the
 * city, the Neon Tokyo skyline, or your own video.
 */
object NeonDreamStyle {
    const val HOME = "home"
    const val DRIVE = "drive"
    const val SKYLINE = "skyline"
    const val VIDEO = "video"
    private const val PREFS = "neon_dream"
    private const val KEY = "style"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, HOME) ?: HOME

    fun set(context: Context, style: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, style).apply()
    }

    /** The screensaver's view in the style chosen in Settings; the city drive while no video was sent. */
    fun view(context: Context): View = when {
        get(context) == HOME -> HomeDream.view(context)
        get(context) == SKYLINE -> NeonNightView(context)
        get(context) == VIDEO && DreamVideo.exists(context) -> VideoDreamView(context, DreamVideo.file(context))
        else -> CityDriveView(context)
    }
}
