package com.lumera.app.dream

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager

/** Shows the screensaver right away, from Settings › Personalization. Any button ends it. */
class NeonDreamPreviewActivity : Activity() {

    private var openedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedAt = SystemClock.uptimeMillis()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(NeonNightView(this))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
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
