package com.lumera.app.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.lumera.app.intro.NeonIntroView
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

    // And in another glass, and laid out otherwise
    private var previewGlass by mutableStateOf<HomeGlass?>(null)
    private var previewLayout by mutableStateOf<Set<String>?>(null)

    // And with other apps on the Home row, without changing the ones Adrian put there
    private var previewTiles by mutableStateOf<List<String>?>(null)

    // A console's card shown as if it had just been switched on, to see how it looks
    private var previewWake by mutableStateOf<String?>(null)

    // A Bluetooth device's card shown as if it had just been switched on, by a made-up name
    private var previewBluetooth by mutableStateOf<String?>(null)

    // The Bluetooth corner, as Home pressed twice opens it, listing made-up devices by name
    private var previewBluetoothList by mutableStateOf<String?>(null)

    // The night the screen goes to as the TV is switched off, so it comes on to the intro rather
    // than to the home screen for a moment first
    private var sleepCover: View? = null
    private var intro: NeonIntroView? = null

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> coverForSleep()
                Intent.ACTION_SCREEN_ON -> playWakeIntro()
            }
        }
    }

    private var stoppedAt = 0L

    override fun onStop() {
        super.onStop()
        stoppedAt = android.os.SystemClock.uptimeMillis()
    }

    private fun coverForSleep() {
        if (sleepCover != null || preview) return
        // Only when the home screen is what was showing: the TV may stop it just before it says it went off
        val showing = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) ||
            android.os.SystemClock.uptimeMillis() - stoppedAt < 1500
        if (!showing) return
        HomeMotion.load(this)
        if (!HomeMotion.wakeIntro || !NeonIntroView.animationsOn(this)) return
        val cover = View(this).apply { setBackgroundColor(android.graphics.Color.rgb(13, 7, 33)) }
        addContentView(cover, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        sleepCover = cover
    }

    /** The TV has come on: the intro the app opens with plays, then fades into the home screen. */
    private fun playWakeIntro() {
        val cover = sleepCover ?: return
        sleepCover = null
        if (intro == null) {
            val view = NeonIntroView(this) {
                intro?.let { (it.parent as? ViewGroup)?.removeView(it) }
                intro = null
            }
            addContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            intro = view
            // The home screen is all there under it, so it goes as soon as it has played
            view.appReady()
        }
        // Under the intro now, which has the same night until its first picture
        (cover.parent as? ViewGroup)?.removeView(cover)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screen) }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this,
            screen,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        preview = !intent.hasCategory(Intent.CATEGORY_HOME)
        if (preview) previewStyle = intent.getStringExtra(EXTRA_STYLE)?.let(HomeStyle::of)
        if (preview) previewIcons = intent.getStringExtra(EXTRA_ICONS)?.let(IconPack::of)
        if (preview) previewTint = intent.getStringExtra(EXTRA_TINT)?.let(IconTint::of)
        if (preview) previewTiles = tilesOf(intent)
        if (preview) previewGlass = HomeGlass.of(intent.getStringExtra(EXTRA_GLASS))
        if (preview) previewLayout = layoutOf(intent)
        if (preview) previewWake = intent.getStringExtra(EXTRA_WAKE)
        HomeWidgets.preview(if (preview) widgetsOf(intent) else null)
        if (preview) previewBluetooth = intent.getStringExtra(EXTRA_BLUETOOTH)
        if (preview) previewBluetoothList = intent.getStringExtra(EXTRA_BLUETOOTH_LIST)
        setContent {
            NeonHome(
                homePresses = homePresses.intValue,
                onLeave = if (preview) ({ finish() }) else null,
                previewStyle = previewStyle,
                previewIcons = previewIcons,
                previewTint = previewTint,
                previewTiles = previewTiles,
                previewGlass = previewGlass,
                previewLayout = previewLayout,
                previewWake = previewWake,
                previewBluetooth = previewBluetooth,
                previewBluetoothList = previewBluetoothList
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
            previewGlass = null
            previewLayout = null
            HomeWidgets.preview(null)
            homePresses.intValue++
        } else {
            // Asked to show a style while already open, as from Settings again: show it
            intent.getStringExtra(EXTRA_STYLE)?.let { previewStyle = HomeStyle.of(it) }
            intent.getStringExtra(EXTRA_ICONS)?.let { previewIcons = IconPack.of(it) }
            intent.getStringExtra(EXTRA_TINT)?.let { previewTint = IconTint.of(it) }
            tilesOf(intent)?.let { previewTiles = it }
            HomeGlass.of(intent.getStringExtra(EXTRA_GLASS))?.let { previewGlass = it }
            layoutOf(intent)?.let { previewLayout = it }
            previewWake = intent.getStringExtra(EXTRA_WAKE)
            widgetsOf(intent)?.let { HomeWidgets.preview(it) }
            previewBluetooth = intent.getStringExtra(EXTRA_BLUETOOTH)
            previewBluetoothList = intent.getStringExtra(EXTRA_BLUETOOTH_LIST)
        }
    }

    /** "Weather,Calendar,Network" shows those widgets, as the emulator test asks. */
    private fun widgetsOf(intent: Intent): WidgetSetup? =
        intent.getStringExtra(EXTRA_WIDGETS)?.split(',')
            ?.mapNotNull { name -> WidgetKind.values().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } }
            ?.let { WidgetSetup(kinds = it.toSet()) }

    private fun tilesOf(intent: Intent): List<String>? =
        intent.getStringExtra(EXTRA_TILES)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }

    private fun layoutOf(intent: Intent): Set<String>? =
        intent.getStringExtra(EXTRA_LAYOUT)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()

    companion object {
        /** The key of a [HomeStyle] to show a preview in. */
        const val EXTRA_STYLE = "style"

        /** The key of an [IconPack] to show a preview with. */
        const val EXTRA_ICONS = "icons"

        /** The key of an [IconTint] to show the icons of a preview in. */
        const val EXTRA_TINT = "tint"

        /** The key of a [HomeGlass] to show a preview in. */
        const val EXTRA_GLASS = "glass"

        /**
         * How to lay a preview out, separated by commas: down (Watch & listen at the foot),
         * consoles (on Home too), square (icons) and names (all the time); a word left out is off.
         */
        const val EXTRA_LAYOUT = "layout"

        /** The packages of the apps to show on the Home row of a preview, separated by commas. */
        const val EXTRA_TILES = "tiles"

        /** The id of a console whose card to show, as if it had just been switched on. */
        const val EXTRA_WAKE = "wake"

        /** Widgets to show in a preview, by name, separated by commas. */
        const val EXTRA_WIDGETS = "widgets"
        /** The name of a Bluetooth device whose card to show, as if it had just been switched on. */
        const val EXTRA_BLUETOOTH = "bluetooth"

        /** Names of made-up Bluetooth devices, separated by commas, for the Bluetooth corner. */
        const val EXTRA_BLUETOOTH_LIST = "bluetoothlist"
    }
}
