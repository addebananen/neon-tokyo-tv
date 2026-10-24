package com.lumera.app.dream

import android.content.Context
import android.view.View
import com.lumera.app.dream.drive.CityDriveView

/** Which screensaver runs: the night drive through the city, or the Neon Tokyo skyline. */
object NeonDreamStyle {
    const val DRIVE = "drive"
    const val SKYLINE = "skyline"
    private const val PREFS = "neon_dream"
    private const val KEY = "style"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, DRIVE) ?: DRIVE

    fun set(context: Context, style: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, style).apply()
    }

    /** The screensaver's view in the style chosen in Settings. */
    fun view(context: Context): View =
        if (get(context) == SKYLINE) NeonNightView(context) else CityDriveView(context)
}
