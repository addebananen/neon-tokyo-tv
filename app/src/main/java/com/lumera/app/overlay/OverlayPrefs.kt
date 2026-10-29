package com.lumera.app.overlay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings

/** The overlays' choices in Settings. */
object OverlayPrefs {
    private const val PREFS = "neon_overlay"
    private const val GESTURES = "gestures"
    private const val HUD_SECONDS = "hud_seconds"
    private const val HUD_ON_CONSOLE = "hud_on_console"
    private const val CONTROL_CENTER = "control_center"

    /** How long the gaming HUD stays; 0 keeps it until it is put away. */
    val HUD_CHOICES = listOf(4, 6, 10, 0)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun gestures(context: Context): Boolean = prefs(context).getBoolean(GESTURES, true)
    fun setGestures(context: Context, on: Boolean) = prefs(context).edit().putBoolean(GESTURES, on).apply()

    fun hudSeconds(context: Context): Int = prefs(context).getInt(HUD_SECONDS, 6)
    fun setHudSeconds(context: Context, seconds: Int) = prefs(context).edit().putInt(HUD_SECONDS, seconds).apply()

    fun hudOnConsole(context: Context): Boolean = prefs(context).getBoolean(HUD_ON_CONSOLE, false)
    fun setHudOnConsole(context: Context, on: Boolean) = prefs(context).edit().putBoolean(HUD_ON_CONSOLE, on).apply()

    /** Holding Home opens the Control Center. */
    fun controlCenter(context: Context): Boolean = prefs(context).getBoolean(CONTROL_CENTER, true)
    fun setControlCenter(context: Context, on: Boolean) = prefs(context).edit().putBoolean(CONTROL_CENTER, on).apply()

    /** True while the overlay service runs: the TV turned it on under Accessibility. */
    fun serviceOn(context: Context): Boolean {
        val me = ComponentName(context, NeonOverlayService::class.java).flattenToString()
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any { it.equals(me, ignoreCase = true) }
    }

    /** WRITE_SECURE_SETTINGS was granted over ADB, so Neon Tokyo can switch the service itself. */
    fun canSwitch(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Turns the service on or off, keeping every other accessibility service as it is. */
    fun switchService(context: Context, on: Boolean): Boolean = runCatching {
        val resolver = context.contentResolver
        val me = ComponentName(context, NeonOverlayService::class.java).flattenToString()
        val list = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').filter { it.isNotBlank() && !it.equals(me, ignoreCase = true) }
        val next = if (on) list + me else list
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next.joinToString(":"))
        if (on) Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
    }.isSuccess

    /** The TV's Accessibility page, where the service is switched on by hand. */
    fun openAccessibility(context: Context): Boolean = runCatching {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

/**
 * The console Neon Home just switched the TV to, so the overlays can say which one shows.
 * The TV's own input app shows it; [tvApp] is that app, to know when the console is in front.
 */
object ConsoleSession {
    data class Started(
        val name: String,
        val color: Long,
        val inputId: String,
        val inputLabel: String,
        val tvApp: String?,
        val at: Long
    )

    @Volatile
    var last: Started? = null
        private set

    fun started(context: Context, name: String, color: Long, inputId: String, inputLabel: String, open: Intent) {
        val tvApp = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(open, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()
        last = Started(name, color, inputId, inputLabel, tvApp, SystemClock.uptimeMillis())
        NeonOverlayService.consoleStarted()
    }
}
