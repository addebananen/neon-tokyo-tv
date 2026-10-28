package com.lumera.app.dream

import android.service.dreams.DreamService
import com.lumera.app.launcher.DreamWidgetsView

/**
 * The screensaver: the night drive through the city, the Neon Tokyo skyline, or your own video.
 * Google TV hides third-party screensavers from its settings, so the TV setup script switches
 * to it over ADB.
 */
class NeonDreamService : DreamService() {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        // The home profile's widgets ride along when it wants them on the screensaver
        setContentView(DreamWidgetsView.wrap(this, NeonDreamStyle.view(this)))
    }

    companion object {
        /** The value for the TV's screensaver_components setting. */
        fun component(packageName: String) = "$packageName/${NeonDreamService::class.java.name}"
    }
}
