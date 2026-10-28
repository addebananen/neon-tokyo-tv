package com.lumera.app.dream

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import com.lumera.app.dream.drive.CityDriveView
import com.lumera.app.launcher.DreamWidgetsView
import com.lumera.app.launcher.HomeDream
import com.lumera.app.launcher.StyleDreamView

/** Shows the screensaver right away, from Settings › Personalization. Any button ends it. */
class NeonDreamPreviewActivity : Activity() {

    private var openedAt = 0L
    private lateinit var scene: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedAt = SystemClock.uptimeMillis()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // A home style's own screensaver can be asked for by its key, as the emulator test does
        scene = intent.getStringExtra("style")?.let { HomeDream.preview(this, it) } ?: NeonDreamStyle.view(this)
        setContentView(DreamWidgetsView.wrap(this, scene))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onPause() {
        super.onPause()
        (scene as? CityDriveView)?.onPause()
        (scene as? VideoDreamView)?.onPause()
        (scene as? StyleDreamView)?.onPause()
    }

    override fun onResume() {
        super.onResume()
        (scene as? CityDriveView)?.onResume()
        (scene as? VideoDreamView)?.onResume()
        (scene as? StyleDreamView)?.onResume()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // A button still held from opening the preview doesn't count
        if (SystemClock.uptimeMillis() - openedAt < 800L) return true
        finish()
        return true
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
