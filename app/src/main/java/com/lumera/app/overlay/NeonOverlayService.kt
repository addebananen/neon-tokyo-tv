package com.lumera.app.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Picture diagnostics and the gaming HUD, over whatever the TV shows.
 *
 * It is an accessibility service because that is the one way Android lets an app see the
 * remote's buttons while another app (a film, a console's input) is in front, and draw over it
 * without asking for the "draw over other apps" switch Google TV hides. It reads no screens:
 * it only watches the buttons for its two chords and which app is in front.
 *
 * Hold Up and Down together for 2 s: picture diagnostics. Hold Left and Right: the gaming HUD.
 * The same chord again, or Back for diagnostics, puts them away. Settings (or a held Home): the Control Center
 * ([ControlCenter]), which also holds the sleep timer.
 */
class NeonOverlayService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val chords = ChordDetector(ChordDetector.DEFAULT)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var probe: SignalProbe
    @Volatile
    private var control: ControlCenter? = null
    /** The hand that presses on the TV's settings pages for the Control Center, lent while this runs. */
    private val hand = SettingsHand(this) { handOff() }

    @Volatile
    private var foreground: String? = null
    @Volatile
    private var keyLagMs: Int? = null
    @Volatile
    private var gestures = true

    /*
     * The remote's buttons on a thread of their own. A service that filters keys holds up every
     * press on the TV until it answers, and Android hands it each press on the app's main thread,
     * the same one Neon Home and the Neon Tokyo app draw on. While those were busy, as just after
     * coming back from an app, every press anywhere on the TV waited for them, up to half a
     * second each. So the presses come here instead and are answered at once; only the few that
     * are the Control Center's or diagnostics' go over to the main thread, ahead of anything
     * queued there.
     */
    private var keyThread: HandlerThread? = null
    @Volatile
    private var keysOwnThread = false
    private var bindFound = false

    private fun keyLooper(): Looper =
        keyThread?.looper ?: HandlerThread("NeonKeys", Process.THREAD_PRIORITY_URGENT_DISPLAY).also {
            it.start()
            keyThread = it
        }.looper

    /** True only for the call AccessibilityService.onBind makes for the looper its callbacks run on. */
    private fun forOnBind(): Boolean {
        if (bindFound) return false
        val bind = Throwable().stackTrace.any {
            it.methodName == "onBind" && it.className == AccessibilityService::class.java.name
        }
        if (bind) {
            bindFound = true
            keysOwnThread = true
        }
        return bind
    }

    // Android 14 asks for an executor, Android 11 to 13 for a looper; anything else asking still gets the main one
    @androidx.annotation.RequiresApi(28)
    override fun getMainExecutor(): Executor {
        if (!forOnBind()) return super.getMainExecutor()
        val keys = Handler(keyLooper())
        return Executor { keys.post(it) }
    }

    override fun getMainLooper(): Looper = if (forOnBind()) keyLooper() else super.getMainLooper()

    /** Where the chord timer runs: wherever the presses come. */
    private val keyHandler: Handler by lazy { if (keysOwnThread) Handler(keyLooper()) else handler }

    /** Runs [block] on the main thread now and waits for it, for no longer than [timeoutMs]. */
    private fun <T> onMainNow(timeoutMs: Long, fallback: T, block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val state = AtomicInteger(0) // 0 waiting, 1 taken by the main thread, 2 given up
        val done = CountDownLatch(1)
        val result = arrayOfNulls<Any?>(1)
        result[0] = fallback
        handler.postAtFrontOfQueue {
            if (!state.compareAndSet(0, 1)) return@postAtFrontOfQueue
            try {
                result[0] = block()
            } finally {
                done.countDown()
            }
        }
        if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            // Too busy: the press goes on to the TV as if nothing here wanted it
            if (state.compareAndSet(0, 2)) return fallback
            done.await()
        }
        @Suppress("UNCHECKED_CAST")
        return result[0] as T
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }

    /** Diagnostics are up, or Back went down on them: Back is theirs. Read on the key thread. */
    @Volatile
    private var backIsOurs = false

    private fun publishBack() {
        backIsOurs = diagnostics != null || backHeld
    }

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
        chords.due(SystemClock.uptimeMillis())?.let { onMain { fire(it.action) } }
    }

    override fun onServiceConnected() = onMain { connected() }

    private fun connected() {
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
        Log.i(TAG, "Connected, chords ${if (gestures) "on" else "off"}, keys on ${if (keysOwnThread) "their own thread" else "the main thread"}")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            keyLagMs = (SystemClock.uptimeMillis() - event.eventTime).toInt().takeIf { it in 0..2_000 }
        }
        val code = event.keyCode
        // Only a press the Control Center or diagnostics may want waits for the main thread
        val theirs = code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_SETTINGS ||
            control?.engaged == true || (code == KeyEvent.KEYCODE_BACK && backIsOurs)
        if (theirs) onMainNow<Boolean?>(MAIN_WAIT_MS, null) { panelKey(event) }?.let { return it }
        if (!gestures) return false
        val consume = chords.onKey(code, event.action, event.repeatCount, event.eventTime)
        val armed = chords.armed
        keyHandler.removeCallbacks(checkChord)
        if (armed != null) {
            val wait = (chords.armedAt + armed.holdMs - SystemClock.uptimeMillis()).coerceAtLeast(0L)
            keyHandler.postDelayed(checkChord, wait)
        }
        return consume
    }

    /** On the main thread: the Control Center's and diagnostics' share of the remote, null when neither wants [event]. */
    private fun panelKey(event: KeyEvent): Boolean? {
        // The Control Center first: while it shows, the remote is its own
        control?.onKey(event)?.let { return it }
        // Back puts diagnostics away and goes no further
        if (event.keyCode == KeyEvent.KEYCODE_BACK && (diagnostics?.isShowing == true || backHeld)) {
            if (event.action == KeyEvent.ACTION_DOWN) backHeld = true
            if (event.action == KeyEvent.ACTION_UP) {
                backHeld = false
                hideDiagnostics()
            }
            publishBack()
            return true
        }
        return null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Read now: the event is recycled once this returns
        val type = event?.eventType
        val pkg = event?.packageName?.toString()
        val className = event?.className?.toString()
        onMain { windowChanged(type, pkg, className) }
    }

    private fun windowChanged(type: Int?, pkg: String?, className: String?) {
        if (SettingsHand.pending != null) {
            handOn()
            hand.step()
        }
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        pkg ?: return
        // The TV's volume bar and other system panels sit over the app, which stays in front
        if (pkg == "com.android.systemui" || pkg == "android") return
        // The overlays' own windows and toasts don't change what is in front
        if (pkg == packageName && (diagnostics != null || hud != null || className?.contains("Toast") == true)) return
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
        keyThread?.quitSafely()
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
        publishBack()
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
            publishBack()
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

        /** How long a press waits for a busy main thread; Android itself waits half a second. */
        private const val MAIN_WAIT_MS = 300L

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
