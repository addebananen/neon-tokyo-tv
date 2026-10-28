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

    // And with another pack of app icons
    private var previewIcons by mutableStateOf<IconPack?>(null)

    // And in a remix of their colours
    private var previewTint by mutableStateOf<IconTint?>(null)

    // And with other apps on the Home row, without changing the ones Adrian put there
    private var previewTiles by mutableStateOf<List<String>?>(null)

    // A console's card shown as if it had just been switched on, to see how it looks
    private var previewWake by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preview = !intent.hasCategory(Intent.CATEGORY_HOME)
        if (preview) previewStyle = intent.getStringExtra(EXTRA_STYLE)?.let(HomeStyle::of)
        if (preview) previewIcons = intent.getStringExtra(EXTRA_ICONS)?.let(IconPack::of)
        if (preview) previewTint = intent.getStringExtra(EXTRA_TINT)?.let(IconTint::of)
        if (preview) previewTiles = tilesOf(intent)
        if (preview) previewWake = intent.getStringExtra(EXTRA_WAKE)
        setContent {
            NeonHome(
                homePresses = homePresses.intValue,
                onLeave = if (preview) ({ finish() }) else null,
                previewStyle = previewStyle,
                previewIcons = previewIcons,
                previewTint = previewTint,
                previewTiles = previewTiles,
                previewWake = previewWake
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed again while the home screen shows, or a preview that became the home screen
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            preview = false
            previewStyle = null
            previewIcons = null
            previewTint = null
            previewTiles = null
            homePresses.intValue++
        } else {
            // Asked to show a style while already open, as from Settings again: show it
            intent.getStringExtra(EXTRA_STYLE)?.let { previewStyle = HomeStyle.of(it) }
            intent.getStringExtra(EXTRA_ICONS)?.let { previewIcons = IconPack.of(it) }
            intent.getStringExtra(EXTRA_TINT)?.let { previewTint = IconTint.of(it) }
            tilesOf(intent)?.let { previewTiles = it }
            previewWake = intent.getStringExtra(EXTRA_WAKE)
        }
    }

    private fun tilesOf(intent: Intent): List<String>? =
        intent.getStringExtra(EXTRA_TILES)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }

    companion object {
        /** The key of a [HomeStyle] to show a preview in. */
        const val EXTRA_STYLE = "style"

        /** The key of an [IconPack] to show a preview with. */
        const val EXTRA_ICONS = "icons"

        /** The key of an [IconTint] to show the icons of a preview in. */
        const val EXTRA_TINT = "tint"

        /** The packages of the apps to show on the Home row of a preview, separated by commas. */
        const val EXTRA_TILES = "tiles"

        /** The id of a console whose card to show, as if it had just been switched on. */
        const val EXTRA_WAKE = "wake"
    }
}
