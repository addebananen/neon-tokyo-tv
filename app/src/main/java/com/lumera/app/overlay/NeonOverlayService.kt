package com.lumera.app.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.lumera.app.control.CcPage
import com.lumera.app.control.ControlCenter
import com.lumera.app.launcher.BluetoothHelper
import com.lumera.app.launcher.SettingsHand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Picture diagnostics and the gaming HUD, over whatever the TV shows.
 *
 * It is an accessibility service because that is the one way Android lets an app see the
 * remote's buttons while another app (a film, a console's input) is in front, and draw over it
 * without asking for the "draw over other apps" switch Google TV hides. It reads no screens:
 * it only watches the buttons for its two chords and which app is in front.
 *
 * Hold Up and Down together for 2 s: picture diagnostics. Hold Left and Right: the gaming HUD.
 * The same chord again, or Back for diagnostics, puts them away. Hold Home: the Control Center
 * ([ControlCenter]), which also holds the sleep timer.
 */
class NeonOverlayService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val chords = ChordDetector(ChordDetector.DEFAULT)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var probe: SignalProbe
    private var control: ControlCenter? = null
    /** The hand that presses on the TV's settings pages for the Control Center, lent while this runs. */
    private val hand = SettingsHand(this) { handOff() }

    private var foreground: String? = null
    private var keyLagMs: Int? = null
    private var gestures = true

    private val report = mutableStateOf<SignalReport?>(null)
    private val hudLeft = mutableStateOf(1f)
    private var diagnostics: OverlayWindow? = null
    private var hud: OverlayWindow? = null
    private var live: Job? = null
    private var hudTimer: Job? = null
    /** Back went down while diagnostics showed: its release is ours too. */
    private var backHeld = false
    /** A console was just started from Neon Home: the HUD comes when its input shows. */
    private var hudWhenConsole = 0L

    /**
     * From a PC: `adb shell am broadcast -a com.neontokyo.tv.OVERLAY --es show diagnostics|hud|off|report`.
     * `--es show control` opens the Control Center (the Siri Remote's held TV button sends it), and
     * `control:audio|wifi|profiles|sleep` opens it on that page; `--es devices "AirPods,JBL"`
     * fills its sound list for a preview.
     */
    private val fromAdb = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getStringExtra("show")) {
                "diagnostics" -> showDiagnostics()
                "hud" -> showHud()
                "off" -> { hideDiagnostics(); hideHud(); control?.close() }
                "control" -> control?.toggle()
                // What diagnostics read now, printed by am broadcast
                "control:audio", "control:wifi", "control:profiles", "control:sleep", "control:main" -> {
                    val page = when (intent.getStringExtra("show")) {
                        "control:audio" -> CcPage.Audio
                        "control:wifi" -> CcPage.Wifi
                        "control:profiles" -> CcPage.Profiles
                        "control:sleep" -> CcPage.Sleep
                        else -> CcPage.Main
                    }
                    val devices = intent.getStringExtra("devices")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                    control?.open(page, devices)
                }
                "report" -> resultData = Dumps.readings(build(layer = null, audioMs = AudioLatency.recent()))
            }
        }
    }

    private val checkChord = Runnable {
        chords.due(SystemClock.uptimeMillis())?.let { fire(it.action) }
    }

    override fun onServiceConnected() {
        probe = SignalProbe(this)
        gestures = OverlayPrefs.gestures(this)
        instance = this
        control?.detach()
        control = ControlCenter(this, object : ControlCenter.Actions {
            override fun diagnostics() = showDiagnostics()
            override fun hud() = showHud()
        }).also { it.attach() }
        BluetoothHelper.lent = hand
        // Only the shell and the system hold DUMP, so no app can open the overlays this way
        runCatching { unregisterReceiver(fromAdb) }
        ContextCompat.registerReceiver(this, fromAdb, IntentFilter(ACTION_ADB), "android.permission.DUMP", handler, ContextCompat.RECEIVER_EXPORTED)
        Log.i(TAG, "Connected, chords ${if (gestures) "on" else "off"}")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            keyLagMs = (SystemClock.uptimeMillis() - event.eventTime).toInt().takeIf { it in 0..2_000 }
        }
        // The Control Center first: while it shows, the remote is its own
        control?.onKey(event)?.let { return it }
        // Back puts diagnostics away and goes no further
        if (event.keyCode == KeyEvent.KEYCODE_BACK && (diagnostics?.isShowing == true || backHeld)) {
            if (event.action == KeyEvent.ACTION_DOWN) backHeld = true
            if (event.action == KeyEvent.ACTION_UP) {
                backHeld = false
                hideDiagnostics()
            }
            return true
        }
        if (!gestures) return false
        val consume = chords.onKey(event.keyCode, event.action, event.repeatCount, event.eventTime)
        val armed = chords.armed
        handler.removeCallbacks(checkChord)
        if (armed != null) {
            val wait = (chords.armedAt + armed.holdMs - SystemClock.uptimeMillis()).coerceAtLeast(0L)
            handler.postDelayed(checkChord, wait)
        }
        return consume
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (SettingsHand.pending != null) {
            handOn()
            hand.step()
        }
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // The TV's volume bar and other system panels sit over the app, which stays in front
        if (pkg == "com.android.systemui" || pkg == "android") return
        // The overlays' own windows and toasts don't change what is in front
        if (pkg == packageName && (diagnostics != null || hud != null || event.className?.contains("Toast") == true)) return
        foreground = pkg
        val console = ConsoleSession.last
        if (hudWhenConsole > 0 && console?.tvApp == pkg) {
            if (SystemClock.uptimeMillis() - hudWhenConsole < CONSOLE_WAIT_MS) {
                // The input has a moment to lock onto the signal first
                handler.postDelayed({ showHud() }, 1_200L)
            }
            hudWhenConsole = 0L
        }
    }

    override fun onInterrupt() = Unit

    private var handWatching = false

    /** While the hand works it follows the page as it changes; the rest of the time it doesn't look. */
    private fun handOn() {
        if (handWatching) return
        handWatching = true
        serviceInfo?.let { info ->
            info.eventTypes = info.eventTypes or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            serviceInfo = info
        }
    }

    private fun handOff() {
        if (!handWatching) return
        handWatching = false
        serviceInfo?.let { info ->
            info.eventTypes = info.eventTypes and AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED.inv()
            info.flags = info.flags and AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.inv()
            serviceInfo = info
        }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        runCatching { unregisterReceiver(fromAdb) }
        handler.removeCallbacksAndMessages(null)
        diagnostics?.close()
        hud?.close()
        control?.detach()
        control = null
        if (BluetoothHelper.lent === hand) BluetoothHelper.lent = null
        hand.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun fire(action: ChordAction) {
        Log.i(TAG, "Chord: $action")
        when (action) {
            ChordAction.Diagnostics -> if (diagnostics?.isShowing == true) hideDiagnostics() else showDiagnostics()
            ChordAction.GamingHud -> if (hud?.isShowing == true) hideHud() else showHud()
        }
    }

    fun showDiagnostics() {
        if (diagnostics?.isShowing == true) return
        hideHud()
        val window = OverlayWindow(this, OverlayWindow.RIGHT, exitMs = 200L) { shown ->
            DiagnosticsPanel(report.value, shown)
        }
        diagnostics = window
        startLive()
        if (!window.show()) diagnostics = null
        // A copy for later, once the readings have settled
        scope.launch {
            delay(3_500L)
            val now = report.value ?: return@launch
            if (diagnostics === window) withContext(Dispatchers.IO) { Dumps.save(this@NeonOverlayService, now, probe.hasDump()) }
        }
        // Not left up for ever if forgotten
        scope.launch {
            delay(DIAGNOSTICS_MAX_MS)
            if (diagnostics === window) hideDiagnostics()
        }
    }

    fun hideDiagnostics() {
        val window = diagnostics ?: return
        window.hide {
            if (diagnostics === window) diagnostics = null
            stopLiveIfIdle()
        }
    }

    fun showHud() {
        if (diagnostics?.isShowing == true) return
        hud?.takeIf { it.isShowing }?.let { restartHudTimer(); return }
        val window = OverlayWindow(this, OverlayWindow.TOP_RIGHT, exitMs = 180L) { shown ->
            GamingHud(report.value, shown, consoleColor(), left = { hudLeft.value })
        }
        hud = window
        startLive()
        if (!window.show()) hud = null else restartHudTimer()
    }

    fun hideHud() {
        hudTimer?.cancel()
        val window = hud ?: return
        window.hide {
            if (hud === window) hud = null
            stopLiveIfIdle()
        }
    }

    private fun restartHudTimer() {
        hudTimer?.cancel()
        val seconds = OverlayPrefs.hudSeconds(this)
        hudLeft.value = 1f
        if (seconds <= 0) return
        hudTimer = scope.launch {
            val total = seconds * 1000L
            val start = SystemClock.uptimeMillis()
            while (isActive) {
                val gone = SystemClock.uptimeMillis() - start
                hudLeft.value = (1f - gone.toFloat() / total).coerceIn(0f, 1f)
                if (gone >= total) break
                delay(100L)
            }
            hideHud()
        }
    }

    /** The readings, fresh every second while anything shows. */
    private fun startLive() {
        if (live?.isActive == true) return
        report.value = build(layer = null, audioMs = AudioLatency.recent())
        live = scope.launch {
            var layer: VideoLayer? = null
            var audioMs = AudioLatency.recent()
            launch { audioMs = AudioLatency.measure() ?: audioMs }
            var tick = 0
            while (isActive) {
                probe.sampleFrames()
                // Only for diagnostics: asking the compositor costs it a little, and a game deserves every frame
                if (diagnostics != null && probe.hasDump() && tick % 3 == 0 && PlaybackSignal.current == null) {
                    val skip = packageName
                    layer = withContext(Dispatchers.IO) { Dumps.videoLayer(skip) }
                }
                report.value = build(layer, audioMs)
                tick++
                delay(1_000L)
            }
        }
    }

    private fun stopLiveIfIdle() {
        if (diagnostics == null && hud == null) {
            live?.cancel()
            live = null
        }
    }

    private fun build(layer: VideoLayer?, audioMs: Int?): SignalReport =
        probe.report(consoleNow(), foreground, layer, audioMs, keyLagMs)

    /** The console in front, when the TV's input app is. */
    private fun consoleNow(): ConsoleNow? {
        val session = ConsoleSession.last ?: return null
        if (session.tvApp == null || foreground != session.tvApp) return null
        val tv = getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        val inputs = runCatching { tv?.tvInputList }.getOrNull().orEmpty()
        // A device the TV found on that port over HDMI-CEC, by the name it gives itself
        val device = inputs.firstOrNull { it.parentId == session.inputId }
            ?.let { runCatching { it.loadLabel(this)?.toString() }.getOrNull() }
            ?.takeIf { it.isNotBlank() && !it.equals(session.inputLabel, ignoreCase = true) }
        val state = runCatching { tv?.getInputState(session.inputId) }.getOrNull()
        return ConsoleNow(
            title = "${session.name} · ${session.inputLabel}",
            input = session.inputLabel,
            device = device,
            connected = state == TvInputManager.INPUT_STATE_CONNECTED,
            color = session.color
        )
    }

    private fun consoleColor(): Long? = ConsoleSession.last?.takeIf { foreground == it.tvApp }?.color

    companion object {
        private const val TAG = "NeonOverlay"
        private const val ACTION_ADB = "com.neontokyo.tv.OVERLAY"
        private const val CONSOLE_WAIT_MS = 8_000L
        private const val DIAGNOSTICS_MAX_MS = 5 * 60_000L

        @Volatile
        private var instance: NeonOverlayService? = null

        val running: Boolean get() = instance != null

        /** For Settings' button: opens the Control Center now, false when the service is off. */
        fun openControlCenter(): Boolean {
            val service = instance ?: return false
            service.handler.post { service.control?.open() }
            return true
        }

        /** For Settings' buttons: shows it now, false when the service is off. */
        fun show(action: ChordAction): Boolean {
            val service = instance ?: return false
            service.handler.post {
                when (action) {
                    ChordAction.Diagnostics -> service.showDiagnostics()
                    ChordAction.GamingHud -> service.showHud()
                }
            }
            return true
        }

        /** Settings changed: the service reads them again. */
        fun reload() {
            val service = instance ?: return
            service.handler.post { service.gestures = OverlayPrefs.gestures(service) }
        }

        internal fun consoleStarted() {
            val service = instance ?: return
            service.handler.post {
                if (OverlayPrefs.hudOnConsole(service)) service.hudWhenConsole = SystemClock.uptimeMillis()
            }
        }
    }
}
