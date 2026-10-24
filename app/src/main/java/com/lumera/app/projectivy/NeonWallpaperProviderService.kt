package com.lumera.app.projectivy

import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import com.lumera.app.R
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType

/**
 * Wallpaper provider for Projectivy Launcher. Pick "Neon Tokyo" under Projectivy's wallpaper
 * settings: the skyline then shifts colour with the focused card, and while the launcher is
 * idle the grid starts rolling towards you.
 */
class NeonWallpaperProviderService : Service() {

    override fun onBind(intent: Intent): IBinder = binder

    private val binder = object : IWallpaperProviderService.Stub() {
        override fun getWallpapers(event: Event?): List<Wallpaper> {
            val prefs = NeonWallpaperPrefs(applicationContext)
            return when (event) {
                // Projectivy's base list, asked for when its cache runs out; always answer it
                is Event.TimeElapsed -> current(prefs, force = true)

                is Event.CardFocused -> {
                    if (!prefs.followFocus) return emptyList()
                    idle = false
                    lastColour = NeonWallpaper.forCard(event.packageName, event.title, event.lightColor)
                    current(prefs)
                }

                is Event.LauncherIdleModeChanged -> {
                    idle = event.isIdle
                    if (prefs.idleDrive) current(prefs) else emptyList()
                }

                // An empty list leaves the wallpaper on screen as it is
                else -> emptyList()
            }
        }

        override fun getPreferences(): String = NeonWallpaperPrefs(applicationContext).export()

        override fun setPreferences(params: String?) {
            NeonWallpaperPrefs(applicationContext).import(params)
        }
    }

    /** What belongs on screen now. Empty when Projectivy already shows it, so nothing reloads. */
    private fun current(prefs: NeonWallpaperPrefs, force: Boolean = false): List<Wallpaper> {
        val wallpapers = when {
            idle && prefs.idleDrive -> listOf(nightDrive(this))
            prefs.followFocus -> listOf(image(this, lastColour))
            // Without focus colours, Projectivy takes the six in turn on its own timer
            else -> NeonWallpaper.entries.map { image(this, it) }
        }
        val key = wallpapers.joinToString { it.uri }
        if (!force && key == shown) return emptyList()
        shown = key
        return wallpapers
    }

    companion object {
        // Kept for the whole process: Projectivy unbinds between events and the service restarts
        @Volatile private var shown: String? = null
        @Volatile private var lastColour = NeonWallpaper.TOKYO
        @Volatile private var idle = false

        fun image(context: Context, colour: NeonWallpaper) = Wallpaper(
            uri = resourceUri(context, colour.drawable),
            type = WallpaperType.IMAGE,
            displayMode = WallpaperDisplayMode.CROP,
            title = "Neon Tokyo · ${colour.label}",
            author = "Neon Tokyo"
        )

        fun nightDrive(context: Context) = Wallpaper(
            uri = resourceUri(context, R.raw.nt_wall_loop),
            type = WallpaperType.VIDEO,
            displayMode = WallpaperDisplayMode.CROP,
            title = "Neon Tokyo · Night drive",
            author = "Neon Tokyo"
        )

        /**
         * android.resource://<package>/<type>/<name>, the form Projectivy's sample uses. Projectivy
         * looks the name up under our package name; if this build files its resources under
         * another name, the bare resource id is used instead, which resolves either way.
         */
        fun resourceUri(context: Context, resId: Int): String {
            val res = context.resources
            val type = res.getResourceTypeName(resId)
            val name = res.getResourceEntryName(resId)
            val builder = Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(context.packageName)
            if (res.getIdentifier(name, type, context.packageName) == resId) {
                builder.appendPath(type).appendPath(name)
            } else {
                builder.appendPath(resId.toString())
            }
            return builder.build().toString()
        }
    }
}
