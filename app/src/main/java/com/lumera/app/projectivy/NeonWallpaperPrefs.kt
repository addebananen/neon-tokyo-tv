package com.lumera.app.projectivy

import android.content.Context
import org.json.JSONObject

/** The two choices on the Projectivy wallpaper settings page. Projectivy backs them up as JSON. */
class NeonWallpaperPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("neon_wallpaper", Context.MODE_PRIVATE)

    /** Wallpaper colour follows the focused card. Off: the colours take turns over time. */
    var followFocus: Boolean
        get() = prefs.getBoolean(KEY_FOLLOW_FOCUS, true)
        set(value) = prefs.edit().putBoolean(KEY_FOLLOW_FOCUS, value).apply()

    /** The skyline starts moving (the night drive loop) while Projectivy is idle. */
    var idleDrive: Boolean
        get() = prefs.getBoolean(KEY_IDLE_DRIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_IDLE_DRIVE, value).apply()

    fun export(): String = JSONObject()
        .put(KEY_FOLLOW_FOCUS, followFocus)
        .put(KEY_IDLE_DRIVE, idleDrive)
        .toString()

    fun import(json: String?) {
        if (json.isNullOrBlank()) return
        runCatching {
            val saved = JSONObject(json)
            if (saved.has(KEY_FOLLOW_FOCUS)) followFocus = saved.getBoolean(KEY_FOLLOW_FOCUS)
            if (saved.has(KEY_IDLE_DRIVE)) idleDrive = saved.getBoolean(KEY_IDLE_DRIVE)
        }
    }

    private companion object {
        const val KEY_FOLLOW_FOCUS = "followFocus"
        const val KEY_IDLE_DRIVE = "idleDrive"
    }
}
