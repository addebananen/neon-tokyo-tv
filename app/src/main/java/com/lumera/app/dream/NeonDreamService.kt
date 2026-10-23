package com.lumera.app.dream

import android.service.dreams.DreamService

/**
 * Screensaver with the Neon Tokyo night scene and clock. Google TV hides third-party
 * screensavers from its settings, so the TV setup script switches to it over ADB.
 */
class NeonDreamService : DreamService() {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(NeonNightView(this))
    }

    companion object {
        /** The value for the TV's screensaver_components setting. */
        fun component(packageName: String) = "$packageName/${NeonDreamService::class.java.name}"
    }
}
