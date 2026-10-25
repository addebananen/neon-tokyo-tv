package com.lumera.app.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The Neon Tokyo home screen. The TV opens it for the Home button through the NeonHome alias,
 * which is off until the TV setup script turns it on, so installing the app never makes the TV
 * ask which home screen to use. Settings can open it straight away as a preview.
 */
class NeonHomeActivity : ComponentActivity() {
    private val homePresses = mutableIntStateOf(0)

    // Opened from Settings to have a look, Back leaves it again; as the home screen it stays
    private var preview by mutableStateOf(false)

    // A preview can be opened in a given style to see it, without changing the profile's own
    private var previewStyle by mutableStateOf<HomeStyle?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preview = !intent.hasCategory(Intent.CATEGORY_HOME)
        if (preview) previewStyle = intent.getStringExtra(EXTRA_STYLE)?.let(HomeStyle::of)
        setContent {
            NeonHome(
                homePresses = homePresses.intValue,
                onLeave = if (preview) ({ finish() }) else null,
                previewStyle = previewStyle
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed again while the home screen shows, or a preview that became the home screen
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            preview = false
            previewStyle = null
            homePresses.intValue++
        }
    }

    companion object {
        /** The key of a [HomeStyle] to show a preview in. */
        const val EXTRA_STYLE = "style"
    }
}
