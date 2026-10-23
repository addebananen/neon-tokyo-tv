package com.lumera.app.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf

/**
 * The Neon Tokyo home screen. The TV opens it for the Home button through the NeonHome alias,
 * which is off until the TV setup script turns it on, so installing the app never makes the TV
 * ask which home screen to use. Settings can open it straight away as a preview.
 */
class NeonHomeActivity : ComponentActivity() {
    private val homePresses = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opened from Settings to have a look, Back leaves it again; as the home screen it stays
        val preview = !intent.hasCategory(Intent.CATEGORY_HOME)
        setContent {
            NeonHome(homePresses = homePresses.intValue, onLeave = if (preview) ({ finish() }) else null)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed again while the home screen shows
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.intValue++
    }
}
