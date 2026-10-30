package com.lumera.app.control

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.lumera.app.launcher.BluetoothHelper
import com.lumera.app.launcher.BtConnect
import com.lumera.app.launcher.BtDevice
import com.lumera.app.launcher.BtKind
import com.lumera.app.launcher.BtRules
import com.lumera.app.launcher.HomeBluetooth
import com.lumera.app.launcher.HomeProfile
import com.lumera.app.launcher.HomeProfiles
import com.lumera.app.launcher.HomeSounds
import com.lumera.app.overlay.OverlayPrefs
import com.lumera.app.overlay.OverlayWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** The pages of the Control Center: the buttons, and the lists some of them open. */
enum class CcPage { Main, Audio, Wifi, Profiles, Sleep }

/** A row of a list page. A [header] row only names the rows under it and never takes the focus. */
data class CcRow(
    val key: String,
    val title: String,
    val detail: String? = null,
    val kind: Kind = Kind.Plain,
    val checked: Boolean = false,
    val header: Boolean = false,
    val battery: Int? = null,
    val bars: Int? = null,
    val secure: Boolean = false,
    val profile: HomeProfile? = null
) {
    enum class Kind { Plain, Tv, Headphones, Earbuds, Speaker, Wifi, Profile, Timer, Settings, Note }
}

/** Everything the Control Center shows, as Compose state, so the panel follows it as it changes. */
class CcModel {
    var page by mutableStateOf(CcPage.Main)
    var focus by mutableStateOf(CcLayout.FIRST)
    var row by mutableStateOf(0)
    var clock by mutableStateOf("")
    var net by mutableStateOf(NetInfo(NetInfo.Kind.Offline))
    var canName by mutableStateOf(true)
    var networks by mutableStateOf<List<WifiScan>>(emptyList())
    var devices by mutableStateOf<List<BtDevice>>(emptyList())
    var batteries by mutableStateOf<Map<String, Int>>(emptyMap())
    var btReady by mutableStateOf(true)
    var profiles by mutableStateOf<List<HomeProfile>>(emptyList())
    var profile by mutableStateOf<HomeProfile?>(null)
    var quiet by mutableStateOf(false)
    var sleepLeft by mutableLongStateOf(-1L)
    var sleepMinutes by mutableStateOf(0)
    /** The button just pressed, for its little press, with when, so a second press shows again. */
    var pressed by mutableStateOf<Pair<CcItem, Long>?>(null)
    var poweringOff by mutableStateOf(false)

    val connected: BtDevice? get() = devices.firstOrNull { it.connected }

    /** The rows of the list page showing now. */
    val rows: List<CcRow>
        get() = when (page) {
            CcPage.Main -> emptyList()
            CcPage.Audio -> audioRows()
            CcPage.Wifi -> wifiRows()
            CcPage.Profiles -> profiles.map { p ->
                CcRow("profile:${p.id}", p.name, if (p.id == profile?.id) "Now on the TV" else p.style.title, CcRow.Kind.Profile, checked = p.id == profile?.id, profile = p)
            }
            CcPage.Sleep -> SleepRules.CHOICES.map { m ->
                CcRow("sleep:$m", SleepRules.label(m), if (m == 0) "The TV stays on" else null, CcRow.Kind.Timer, checked = m == sleepMinutes)
            }
        }

    private fun audioRows(): List<CcRow> {
        val now = connected
        val list = mutableListOf(CcRow("tv", "TV Speakers", if (now == null) "Playing now" else null, CcRow.Kind.Tv, checked = now == null))
        val phones = devices.filter { it.kind == BtKind.Headphones || it.kind == BtKind.Earbuds }
        val speakers = devices - phones.toSet()
        fun add(title: String, group: List<BtDevice>) {
            if (group.isEmpty()) return
            list += CcRow("h:$title", title, header = true)
            group.forEach { d ->
                list += CcRow(
                    "bt:${d.address}", d.name, if (d.connected) "Connected" else null,
                    when (d.kind) {
                        BtKind.Earbuds -> CcRow.Kind.Earbuds
                        BtKind.Headphones -> CcRow.Kind.Headphones
                        else -> CcRow.Kind.Speaker
                    },
                    checked = d.connected, battery = batteries[d.address]
                )
            }
        }
        add("HEADPHONES", phones)
        add("SPEAKERS", speakers)
        if (!btReady) list += CcRow("note", "Bluetooth is off, or Neon Home hasn't asked for it yet", kind = CcRow.Kind.Note, header = true)
        list += CcRow("bt-settings", "Pair a new device…", kind = CcRow.Kind.Settings)
        return list
    }

    private fun wifiRows(): List<CcRow> {
        val list = mutableListOf<CcRow>()
        val n = net
        val here = n.ssid
        when (n.kind) {
            NetInfo.Kind.Ethernet -> list += CcRow("eth", "Ethernet", listOfNotNull(if (n.internet) "Internet" else "No internet", n.ip).joinToString(" · "), CcRow.Kind.Settings, checked = true)
            NetInfo.Kind.Wifi -> list += CcRow(
                "wifi:${here.orEmpty()}", here ?: "Wi-Fi", listOfNotNull(if (n.internet) "Connected" else "No internet", n.band, n.mbps?.let { "$it Mbit/s" }, n.ip).joinToString(" · "),
                CcRow.Kind.Wifi, checked = true, bars = n.bars
            )
            NetInfo.Kind.Offline -> list += CcRow("offline", "Not connected", "Pick a network below", CcRow.Kind.Note, header = true)
        }
        val others = networks.filter { it.ssid != here }
        if (others.isNotEmpty()) {
            list += CcRow("h:near", "OTHER NETWORKS", header = true)
            others.take(12).forEach { s ->
                list += CcRow("wifi:${s.ssid}", s.ssid, NetRules.band(s.frequency), CcRow.Kind.Wifi, bars = NetRules.bars(s.rssi), secure = s.secure)
            }
        } else if (!canName) {
            list += CcRow("allow-names", "Show network names", "Android asks once. Nothing is located", CcRow.Kind.Settings)
        }
        list += CcRow("wifi-settings", "Network settings…", kind = CcRow.Kind.Settings)
        return list
    }
}

/**
 * Apple TV's Control Center, for the whole TV: hold Home on any remote, over any app, film or
 * console, and the panel comes down in the top right corner. From there the sound goes to
 * headphones, a speaker or back to the TV, the home profile changes, the network shows and
 * changes, Do Not Disturb and a sleep timer switch, and the TV turns off.
 *
 * It lives in [com.lumera.app.overlay.NeonOverlayService], the one place an app sees the remote
 * before the app in front does. The panel never takes the focus: while it shows, the service
 * keeps the remote's arrows, OK and Back for it, so the film beneath plays on and keeps its place.
 */
class ControlCenter(private val service: AccessibilityService, private val actions: Actions) {
    /** What the panel's round buttons ask of the service it lives in. */
    interface Actions {
        fun diagnostics()
        fun hud()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val hold = HomeHold()
    private val net = NetReader(service)
    private val power = service.getSystemService(PowerManager::class.java)
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sounds: HomeSounds? = null
    private var window: OverlayWindow? = null
    private var warning: OverlayWindow? = null
    private var bluetooth: HomeBluetooth? = null
    private var live: Job? = null
    private var stopWatching: (() -> Unit)? = null
    private var lastKey = 0L
    /** Home pressed for the TV by the service: its own press, which must go straight through. */
    private var replayUntil = 0L
    /** A key that cut the sleep warning short: its release is ours too. */
    private var swallowUp = -1
    /** Made-up devices for a preview, from ADB. */
    private var previewDevices: List<BtDevice>? = null

    val model = CcModel()

    val isOpen: Boolean get() = window?.isShowing == true

    private val sleepEndsAt = longArrayOf(0L)

    private val checkHold = Runnable { if (hold.due(SystemClock.uptimeMillis())) toggle() }
    private val sleepTick = object : Runnable {
        override fun run() = sleepStep()
    }
    private val idle = Runnable { if (SystemClock.uptimeMillis() - lastKey >= IDLE_MS - 50) close() }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // The TV went to sleep: nothing is left up for when it wakes
            hold.reset()
            setSleep(0, quiet = true)
            closeNow()
        }
    }

    fun attach() {
        BtBattery.listen(service)
        ContextCompat.registerReceiver(service, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_EXPORTED)
    }

    fun detach() {
        runCatching { service.unregisterReceiver(screenOff) }
        handler.removeCallbacksAndMessages(null)
        closeNow()
        warning?.close()
        warning = null
        scope.cancel()
    }

    /**
     * The remote's buttons, before the overlays' chords: true keeps a press, false lets it go
     * on to the TV, null when it isn't the Control Center's at all.
     */
    fun onKey(event: KeyEvent): Boolean? {
        val code = event.keyCode
        val action = event.action
        if (swallowUp == code) {
            if (action == KeyEvent.ACTION_UP) swallowUp = -1
            return true
        }
        if (code == KeyEvent.KEYCODE_HOME) return onHome(event)
        if (code == KeyEvent.KEYCODE_SETTINGS) return onSettings(event)
        // The last minute before the sleep timer turns the TV off: any button keeps it on
        if (warning != null && action == KeyEvent.ACTION_DOWN && !isOpen) {
            setSleep(0, quiet = true)
            Toast.makeText(service, "Sleep timer off", Toast.LENGTH_SHORT).show()
            swallowUp = code
            return true
        }
        if (!isOpen) return null
        if (model.poweringOff) return true
        return when (code) {
            // The sound and the power stay the TV's
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_TV_POWER, KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP -> false
            else -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    lastKey = SystemClock.uptimeMillis()
                    handler.removeCallbacks(idle)
                    handler.postDelayed(idle, IDLE_MS)
                    press(code, event.repeatCount)
                } else if (action == KeyEvent.ACTION_UP && (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE)) {
                    back()
                }
                true
            }
        }
    }

    private fun onHome(event: KeyEvent): Boolean? {
        if (!OverlayPrefs.controlCenter(service)) return null
        // A press the service made for the TV, or the screen is off: the TV's own
        if (SystemClock.uptimeMillis() < replayUntil && !hold.holding) return null
        if (power?.isInteractive == false) return null
        return when (hold.onKey(event.action, event.repeatCount, event.eventTime)) {
            HomeStep.Pass -> null
            HomeStep.Swallow -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    handler.removeCallbacks(checkHold)
                    val at = hold.dueAt()
                    if (at >= 0) handler.postAtTime(checkHold, at)
                    // A held Home repeats: the repeat can come before the timer does
                    if (event.repeatCount > 0 && hold.due(event.eventTime)) {
                        handler.removeCallbacks(checkHold)
                        toggle()
                    }
                }
                true
            }
            HomeStep.PressHome -> {
                handler.removeCallbacks(checkHold)
                if (isOpen) closeNow()
                replayUntil = SystemClock.uptimeMillis() + REPLAY_MS
                if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) {
                    Log.w(TAG, "The TV did not take Home")
                }
                true
            }
        }
    }

    /**
     * The remote's Settings button opens and closes the Control Center, a press away: Google TV
     * keeps a held Home for itself on some remotes. The TV's settings are one button on the panel.
     */
    private fun onSettings(event: KeyEvent): Boolean? {
        if (!OverlayPrefs.controlCenter(service)) return null
        if (power?.isInteractive == false) return null
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (model.poweringOff) return true
            lastKey = SystemClock.uptimeMillis()
            toggle()
        }
        return true
    }

    fun toggle() = if (isOpen) close() else open()

    /** Opens on [page], from ADB or the Siri Remote. [devices] fills the sound list for a preview. */
    fun open(page: CcPage = CcPage.Main, devices: List<String>? = null) {
        val preview = devices?.mapIndexed { i, name ->
            BtDevice("00:00:00:00:00:0$i", name, BtRules.previewKind(name), connected = i == 0)
        }
        if (isOpen) {
            preview?.let { previewDevices = it }
            show(page)
            return
        }
        previewDevices = preview
        warning?.hide()
        warning = null
        model.page = CcPage.Main
        model.focus = CcLayout.FIRST
        model.row = 0
        model.poweringOff = false
        model.quiet = Quiet.on(service)
        if (scope.coroutineContext[Job]?.isActive != true) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        refresh()
        startLive()
        if (sounds == null) sounds = runCatching { HomeSounds(service) }.getOrNull()
        val w = OverlayWindow(service, OverlayWindow.FULL, exitMs = 220L, fullScreen = true) { shown ->
            ControlPanel(model, shown)
        }
        window = w
        if (!w.show()) {
            window = null
            stopLive()
            return
        }
        lastKey = SystemClock.uptimeMillis()
        handler.postDelayed(idle, IDLE_MS)
        if (page != CcPage.Main) show(page)
    }

    fun close() {
        val w = window ?: return
        handler.removeCallbacks(idle)
        w.hide {
            if (window === w) window = null
            stopLive()
        }
    }

    private fun closeNow() {
        handler.removeCallbacks(idle)
        window?.close()
        window = null
        stopLive()
    }

    // ---- The remote ----

    private fun press(code: Int, repeat: Int) {
        val dir = when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> Direction.Up
            KeyEvent.KEYCODE_DPAD_DOWN -> Direction.Down
            KeyEvent.KEYCODE_DPAD_LEFT -> Direction.Left
            KeyEvent.KEYCODE_DPAD_RIGHT -> Direction.Right
            else -> null
        }
        if (dir != null) return move(dir)
        if (repeat > 0) return
        when (code) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> select()
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> close()
        }
    }

    private fun move(dir: Direction) {
        if (model.page == CcPage.Main) {
            val next = CcLayout.move(model.focus, dir) ?: return
            model.focus = next
            sounds?.move()
            return
        }
        when (dir) {
            Direction.Left -> back()
            Direction.Right -> Unit
            else -> {
                val rows = model.rows
                val next = nextRow(rows.map { !it.header }, model.row, if (dir == Direction.Down) 1 else -1)
                if (next != model.row) {
                    model.row = next
                    sounds?.move()
                }
            }
        }
    }

    private fun back() {
        if (model.page == CcPage.Main) return close()
        val from = when (model.page) {
            CcPage.Audio -> CcItem.Audio
            CcPage.Wifi -> CcItem.Wifi
            CcPage.Profiles -> CcItem.Profile
            CcPage.Sleep -> CcItem.Sleep
            CcPage.Main -> model.focus
        }
        model.page = CcPage.Main
        model.focus = from
        sounds?.move()
    }

    private fun show(page: CcPage) {
        model.page = page
        if (page == CcPage.Wifi) net.scan()
        refresh()
        val rows = model.rows
        // The list opens on what is in use now
        val now = rows.indexOfFirst { it.checked && !it.header }
        model.row = firstRow(rows.map { !it.header }, if (now >= 0) now else 0)
    }

    private fun select() {
        sounds?.select()
        if (model.page == CcPage.Main) {
            model.pressed = model.focus to SystemClock.uptimeMillis()
            when (model.focus) {
                CcItem.Profile -> show(CcPage.Profiles)
                CcItem.Power -> powerOff()
                CcItem.Wifi -> show(CcPage.Wifi)
                CcItem.Quiet -> {
                    val on = !model.quiet
                    Quiet.set(service, on)
                    model.quiet = on
                }
                CcItem.Audio -> show(CcPage.Audio)
                CcItem.Sleep -> show(CcPage.Sleep)
                CcItem.Screensaver -> { closeNow(); startScreensaver() }
                CcItem.Diagnostics -> { closeNow(); actions.diagnostics() }
                CcItem.Gaming -> { closeNow(); actions.hud() }
                CcItem.Settings -> { closeNow(); start(Intent(Settings.ACTION_SETTINGS)) }
            }
            return
        }
        val row = model.rows.getOrNull(model.row)?.takeIf { !it.header } ?: return
        when {
            row.key == "tv" -> {
                val now = model.connected ?: return back()
                closeNow()
                sendSound(now, disconnect = true)
            }
            row.key.startsWith("bt:") -> {
                val device = model.devices.firstOrNull { "bt:${it.address}" == row.key } ?: return
                if (device.connected) return back()
                closeNow()
                sendSound(device, disconnect = false)
            }
            row.key == "bt-settings" -> { closeNow(); start(BluetoothHelper.settingsIntent(service)) }
            row.key.startsWith("wifi:") -> {
                if (row.checked) return back()
                closeNow()
                val name = row.key.removePrefix("wifi:")
                val helped = BluetoothHelper.request(service, name, BluetoothHelper.Task.Wifi, back = true)
                if (!start(Intent(Settings.ACTION_WIFI_SETTINGS))) BluetoothHelper.cancel()
                else if (!helped) Toast.makeText(service, "Pick $name in the list", Toast.LENGTH_LONG).show()
            }
            row.key == "wifi-settings" || row.key == "eth" -> { closeNow(); start(Intent(Settings.ACTION_WIFI_SETTINGS)) }
            row.key == "allow-names" -> { closeNow(); ControlSetupActivity.open(service, names = true) }
            row.key.startsWith("profile:") -> {
                HomeProfiles.get(service).select(row.key.removePrefix("profile:"))
                refresh()
                back()
            }
            row.key.startsWith("sleep:") -> {
                setSleep(row.key.removePrefix("sleep:").toIntOrNull() ?: 0, quiet = false)
                back()
            }
        }
    }

    private fun sendSound(device: BtDevice, disconnect: Boolean) {
        if (previewDevices != null) return
        val bt = bluetooth ?: HomeBluetooth(service)
        val result = bt.connect(device.address, disconnect = disconnect, back = true)
        val note = when (result) {
            BtConnect.Direct -> "Connecting ${device.name}…"
            BtConnect.Helper -> null
            BtConnect.Settings -> if (disconnect) "Pick ${device.name} and press Disconnect" else "Pick ${device.name} and press Connect"
            BtConnect.Failed -> "Couldn't reach ${device.name}"
        }
        note?.let { Toast.makeText(service, it, Toast.LENGTH_LONG).show() }
    }

    private fun start(intent: Intent): Boolean = runCatching {
        service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Log.w(TAG, "Could not open $intent", it)
        Toast.makeText(service, "Nothing on this TV can open that", Toast.LENGTH_SHORT).show()
    }.isSuccess

    /** The TV's screensaver now: Android's own way in, or Neon Tokyo's when the TV has none. */
    private fun startScreensaver() {
        val system = Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.android.systemui", "com.android.systemui.Somnambulator"))
        val found = runCatching { service.packageManager.resolveActivity(system, 0) != null }.getOrDefault(false)
        if (found && runCatching { service.startActivity(system.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        start(Intent().setClassName(service, "com.lumera.app.dream.NeonDreamPreviewActivity"))
    }

    /** The screen fades to a line as an old TV's did, then the TV goes to standby. */
    private fun powerOff() {
        model.poweringOff = true
        handler.postDelayed({
            val asleep = Build.VERSION.SDK_INT >= 28 && service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            if (!asleep) Toast.makeText(service, "This TV doesn't let apps turn it off", Toast.LENGTH_LONG).show()
            closeNow()
        }, POWER_OFF_MS)
    }

    // ---- The sleep timer ----

    fun setSleep(minutes: Int, quiet: Boolean) {
        handler.removeCallbacks(sleepTick)
        model.sleepMinutes = minutes.coerceAtLeast(0)
        if (minutes <= 0) {
            sleepEndsAt[0] = 0L
            model.sleepLeft = -1L
            warning?.hide()
            warning = null
            return
        }
        sleepEndsAt[0] = SystemClock.elapsedRealtime() + minutes * 60_000L
        model.sleepLeft = minutes * 60_000L
        if (!quiet) Toast.makeText(service, "The TV turns off in ${SleepRules.label(minutes)}", Toast.LENGTH_SHORT).show()
        handler.post(sleepTick)
    }

    private fun sleepStep() {
        val end = sleepEndsAt[0]
        if (end <= 0L) return
        val left = end - SystemClock.elapsedRealtime()
        model.sleepLeft = left.coerceAtLeast(0L)
        if (left <= 0L) {
            setSleep(0, quiet = true)
            closeNow()
            if (Build.VERSION.SDK_INT >= 28) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            return
        }
        if (left <= SleepRules.WARN_MS && warning == null && !isOpen) {
            val w = OverlayWindow(service, OverlayWindow.TOP_RIGHT, exitMs = 200L) { shown -> SleepWarning(model, shown) }
            if (w.show()) warning = w
        }
        // Each second while the number shows, each half minute before
        val next = if (left <= SleepRules.WARN_MS || isOpen) 1_000L - (left % 1_000L) else minOf(30_000L, left - SleepRules.WARN_MS)
        handler.postDelayed(sleepTick, next.coerceAtLeast(50L))
    }

    // ---- What the panel shows ----

    private fun refresh() {
        model.clock = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        model.net = net.read()
        model.canName = net.canName
        model.networks = net.networks(model.net.ssid)
        val profiles = HomeProfiles.get(service)
        model.profiles = profiles.profiles.value
        model.profile = profiles.active()
        model.quiet = Quiet.on(service)
        previewDevices?.let { list ->
            model.devices = list
            model.btReady = true
            model.batteries = list.zip(listOf(98, 90)).associate { (d, level) -> d.address to level }
        }
    }

    private fun startLive() {
        if (live?.isActive == true) return
        val bt = if (previewDevices == null) HomeBluetooth(service).also { it.start() } else null
        bluetooth = bt
        stopWatching = net.watch { handler.post { if (isOpen) refresh() } }
        live = scope.launch {
            if (bt != null) {
                launch {
                    combine(bt.devices, bt.owners, bt.on, bt.permitted) { devices, owners, on, permitted -> listOf(devices, owners, on, permitted) }
                        .collect {
                            val profileId = HomeProfiles.get(service).active().id
                            val list = BtRules.corner(bt.devices.value, bt.owners.value, profileId)
                            model.devices = list
                            model.btReady = bt.on.value && bt.permitted.value
                            model.batteries = withContext(Dispatchers.IO) {
                                list.mapNotNull { d -> BtBattery.of(service, d.address)?.let { d.address to it } }.toMap()
                            }
                        }
                }
            }
            while (isActive) {
                model.clock = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
                val end = sleepEndsAt[0]
                model.sleepLeft = if (end > 0) (end - SystemClock.elapsedRealtime()).coerceAtLeast(0L) else -1L
                delay(1_000L)
                if (model.page != CcPage.Wifi) model.net = net.read()
            }
        }
    }

    private fun stopLive() {
        live?.cancel()
        live = null
        stopWatching?.invoke()
        stopWatching = null
        bluetooth?.stop()
        bluetooth = null
    }

    companion object {
        private const val TAG = "ControlCenter"
        /** Left open and forgotten, it goes by itself. */
        private const val IDLE_MS = 45_000L
        private const val REPLAY_MS = 400L
        private const val POWER_OFF_MS = 560L
    }
}
