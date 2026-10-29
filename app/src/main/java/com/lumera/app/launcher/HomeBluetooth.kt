package com.lumera.app.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/** What a paired device is, for the mark beside its name. */
enum class BtKind(val glyph: Glyph) {
    Headphones(Glyph.Headphones),
    Earbuds(Glyph.Earbuds),
    Speaker(Glyph.Speaker),
    Other(Glyph.Bluetooth)
}

/** A device paired with the TV that plays sound: headphones, earbuds, a speaker, a soundbar. */
data class BtDevice(val address: String, val name: String, val kind: BtKind, val connected: Boolean)

/** How a press on Connect went. */
enum class BtConnect {
    /** The TV took it and connects by itself. */
    Direct,

    /** The TV's Bluetooth page opened and Neon Tokyo presses Connect on it. */
    Helper,

    /** The TV's Bluetooth page opened; the device is pressed there by hand. */
    Settings,
    Failed
}

/** The rules of the Bluetooth corner, kept apart from Android so they can be tested. */
internal object BtRules {
    /** How long the corner card stays when nothing is pressed. */
    const val SHOW_MS = 8_000

    /** A card put away with "Not now" stays away this long for that device. */
    const val SNOOZE_MS = 30 * 60_000L

    /** A card that went by itself, without a press, comes back sooner. */
    const val QUIET_MS = 10 * 60_000L

    /** Two presses of Home this close together open the Bluetooth corner. */
    const val DOUBLE_HOME_MS = 700L

    /** How long the Bluetooth corner stays while nothing is pressed. */
    const val CORNER_MS = 12_000

    /** AirPods and other earbuds call themselves headphones; their names tell them apart. */
    private val EARBUDS = Regex("airpods|earpods|earbud|buds|wf-|tws", RegexOption.IGNORE_CASE)

    fun kindOf(name: String, deviceClass: Int, majorClass: Int): BtKind =
        if (EARBUDS.containsMatchIn(name)) BtKind.Earbuds else kindOf(deviceClass, majorClass)

    /** A made-up device for a preview, by its name alone. */
    fun previewKind(name: String): BtKind = if (EARBUDS.containsMatchIn(name)) BtKind.Earbuds else BtKind.Speaker

    /**
     * The devices the Bluetooth corner lists for [profileId]: its own first, then the ones nobody
     * took. Someone else's stay out, as they do from the cards.
     */
    fun corner(devices: List<BtDevice>, owners: Map<String, Set<String>>, profileId: String): List<BtDevice> {
        val mine = devices.filter { profileId in owners[it.address].orEmpty() }
        val free = devices.filter { owners[it.address].isNullOrEmpty() }
        return mine + free
    }

    fun kindOf(deviceClass: Int, majorClass: Int): BtKind = when (deviceClass) {
        BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
        BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
        BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> BtKind.Headphones
        BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
        BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
        BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
        BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> BtKind.Speaker
        else -> if (majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO) BtKind.Speaker else BtKind.Other
    }

    /** Only devices that play sound belong in the list: never the remote or a gamepad. */
    fun playsSound(majorClass: Int): Boolean = majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO

    /** The devices [profileId] took as its own. A device nobody took pops up for nobody. */
    fun mine(owners: Map<String, Set<String>>, profileId: String): Set<String> =
        owners.filterValues { profileId in it }.keys

    /**
     * The device to offer in the corner: the first of [order] that is nearby [now] but was not
     * [before], belongs to this profile, is not connected already and was not put away lately.
     */
    fun arrived(
        order: List<String>,
        before: Set<String>,
        now: Set<String>,
        mine: Set<String>,
        connected: Set<String>,
        snoozedUntil: Map<String, Long>,
        clock: Long
    ): String? = order.firstOrNull {
        it in now && it !in before && it in mine && it !in connected && (snoozedUntil[it] ?: 0L) <= clock
    }

    fun encode(owners: Map<String, Set<String>>): String = JSONObject().apply {
        owners.forEach { (address, profiles) -> if (profiles.isNotEmpty()) put(address, JSONArray(profiles.sorted())) }
    }.toString()

    fun decode(json: String?): Map<String, Set<String>> = runCatching {
        val o = JSONObject(json ?: return emptyMap())
        o.keys().asSequence().associateWith { key ->
            val a = o.getJSONArray(key)
            (0 until a.length()).map { a.getString(it) }.toSet()
        }
    }.getOrDefault(emptyMap())

    fun take(owners: Map<String, Set<String>>, address: String, profileId: String, mine: Boolean): Map<String, Set<String>> {
        val now = owners[address].orEmpty().let { if (mine) it + profileId else it - profileId }
        return if (now.isEmpty()) owners - address else owners + (address to now)
    }
}

/**
 * Bluetooth on the home screen. An app can't change how the TV's Bluetooth works, so this sits on
 * top of it: while the home screen shows it now and then calls on the paired devices of the
 * profile at home (a short page, as the TV does when it looks up a device's services); a device
 * that answers is switched on and nearby, and gets its card in the corner.
 *
 * Connecting is the TV's own job, and Android 13 and later keep it for the system: where the TV
 * lets an app ask, Connect asks; otherwise it opens the TV's Bluetooth page, where
 * [BluetoothHelper] presses Connect when the TV allowed Neon Tokyo to turn it on.
 */
@SuppressLint("MissingPermission")
class HomeBluetooth(private val context: Context) {
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val prefs = context.getSharedPreferences("neon_home_bluetooth", Context.MODE_PRIVATE)

    private val _devices = MutableStateFlow<List<BtDevice>>(emptyList())
    private val _nearby = MutableStateFlow<Set<String>>(emptySet())
    private val _owners = MutableStateFlow(BtRules.decode(prefs.getString(KEY_OWNERS, null)))
    private val _on = MutableStateFlow(false)
    private val _permitted = MutableStateFlow(permittedNow())

    /** The paired devices that play sound, connected or not. */
    val devices: StateFlow<List<BtDevice>> = _devices.asStateFlow()

    /** The addresses of paired devices that answered lately: switched on and in reach. */
    val nearby: StateFlow<Set<String>> = _nearby.asStateFlow()

    /** Which profiles took which device as theirs, by address. */
    val owners: StateFlow<Map<String, Set<String>>> = _owners.asStateFlow()

    /** Bluetooth is switched on. */
    val on: StateFlow<Boolean> = _on.asStateFlow()

    /** Android lets Neon Tokyo see the paired devices (asked once, on Android 12 and later). */
    val permitted: StateFlow<Boolean> = _permitted.asStateFlow()

    val available: Boolean get() = adapter != null

    private val snoozed = HashMap<String, Long>()
    private val aclUp = HashSet<String>()
    private val acl = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private var a2dp: BluetoothProfile? = null
    private var scope: CoroutineScope? = null
    private var watched: Set<String> = emptySet()
    private var wake: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            intent ?: return
            val device = deviceOf(intent)
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> device?.address?.let {
                    aclUp += it
                    // Anything that reaches the TV is on and in reach, probed or not
                    if (it in _devices.value.map { d -> d.address }) _nearby.update { set -> set + it }
                    acl.tryEmit(it)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> device?.address?.let { aclUp -= it }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    _on.value = adapter?.isEnabled == true
                    if (!_on.value) _nearby.value = emptySet()
                    refresh()
                }
                else -> refresh()
            }
        }
    }

    private fun deviceOf(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

    private fun permittedNow(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Starts listening, while the home screen shows. */
    fun start() {
        val adapter = adapter ?: return
        if (scope != null) return
        _permitted.value = permittedNow()
        _on.value = runCatching { adapter.isEnabled }.getOrDefault(false)
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        runCatching {
            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (scope == null) {
                        adapter.closeProfileProxy(profile, proxy)
                        return
                    }
                    a2dp = proxy
                    refresh()
                }

                override fun onServiceDisconnected(profile: Int) {
                    a2dp = null
                }
            }, BluetoothProfile.A2DP)
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { it.launch { probeLoop() } }
        refresh()
    }

    /** Stops listening and calling: the home screen went away. */
    fun stop() {
        scope?.cancel() ?: return
        scope = null
        runCatching { context.unregisterReceiver(receiver) }
        a2dp?.let { proxy -> runCatching { adapter?.closeProfileProxy(BluetoothProfile.A2DP, proxy) } }
        a2dp = null
        aclUp.clear()
        // Back home later, a device still on is news again
        _nearby.value = emptySet()
    }

    /** The devices of the profile at home, the only ones worth calling on. */
    fun watch(addresses: Set<String>) {
        if (addresses == watched) return
        val added = addresses - watched
        watched = addresses
        // A device just taken as one's own is looked for now, not at the next round
        if (added.isNotEmpty()) wake?.cancel()
    }

    /** Android said yes or no to the Bluetooth permission. */
    fun permissionChanged() {
        _permitted.value = permittedNow()
        refresh()
    }

    fun refresh() {
        val adapter = adapter ?: return
        if (!permittedNow()) {
            _devices.value = emptyList()
            return
        }
        val connected = runCatching { a2dp?.connectedDevices?.map { it.address }?.toSet() }.getOrNull().orEmpty()
        val list = runCatching { adapter.bondedDevices.orEmpty() }.getOrDefault(emptySet())
            .mapNotNull { device ->
                val bluetoothClass = runCatching { device.bluetoothClass }.getOrNull() ?: return@mapNotNull null
                if (!BtRules.playsSound(bluetoothClass.majorDeviceClass)) return@mapNotNull null
                BtDevice(
                    address = device.address,
                    name = nameOf(device),
                    kind = BtRules.kindOf(nameOf(device), bluetoothClass.deviceClass, bluetoothClass.majorDeviceClass),
                    connected = device.address in connected
                )
            }
            .sortedWith(compareByDescending<BtDevice> { it.connected }.thenBy { it.name.lowercase() })
        _devices.value = list
    }

    fun take(address: String, profileId: String, mine: Boolean) {
        _owners.update { BtRules.take(it, address, profileId, mine) }
        prefs.edit().putString(KEY_OWNERS, BtRules.encode(_owners.value)).apply()
    }

    fun snooze(address: String, ms: Long) {
        snoozed[address] = SystemClock.elapsedRealtime() + ms
    }

    fun snoozes(): Map<String, Long> = snoozed

    fun clock(): Long = SystemClock.elapsedRealtime()

    /**
     * Connects [address] the best way this TV allows, or with [disconnect] lets it go so the
     * sound comes back to the TV. [back] returns to the app beneath once the helper is done.
     */
    fun connect(address: String, disconnect: Boolean = false, back: Boolean = false): BtConnect {
        val adapter = adapter ?: return BtConnect.Failed
        if (!permittedNow()) return BtConnect.Failed
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return BtConnect.Failed
        if (!disconnect && connectDirect(device)) return BtConnect.Direct
        val task = if (disconnect) BluetoothHelper.Task.Disconnect else BluetoothHelper.Task.Connect
        val helper = BluetoothHelper.request(context, nameOf(device), task, back)
        if (!HomeApps.start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) {
            BluetoothHelper.cancel()
            return BtConnect.Failed
        }
        return if (helper) BtConnect.Helper else BtConnect.Settings
    }

    /** Android 12 and older let an app ask the sound profile to connect; later ones answer no. */
    private fun connectDirect(device: BluetoothDevice): Boolean {
        if (Build.VERSION.SDK_INT >= 33) return false
        val proxy = a2dp ?: return false
        return runCatching {
            proxy.javaClass.getMethod("connect", BluetoothDevice::class.java).invoke(proxy, device) as? Boolean == true
        }.onFailure { Log.i(TAG, "The TV keeps connecting to itself: ${it.javaClass.simpleName}") }.getOrDefault(false)
    }

    private fun nameOf(device: BluetoothDevice): String =
        runCatching { (if (Build.VERSION.SDK_INT >= 30) device.alias else null) ?: device.name }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: device.address

    /**
     * Calls on the watched devices one by one. A device that is on answers the page and the link
     * comes up for a moment, which Android tells as ACL_CONNECTED; one that is off doesn't answer
     * and the page times out. Devices already found are called on less often, only to notice
     * when they go.
     */
    private suspend fun probeLoop() {
        delay(START_DELAY_MS)
        while (scope?.isActive == true) {
            val connected = _devices.value.filter { it.connected }.map { it.address }.toSet()
            val targets = watched.filter { it !in connected }
            if (_on.value && permittedNow() && targets.isNotEmpty()) {
                for (address in targets) {
                    val here = probe(address)
                    if (address in watched) _nearby.update { if (here) it + address else it - address }
                    delay(GAP_MS)
                }
            }
            val pause = if (targets.isNotEmpty() && targets.all { it in _nearby.value }) FOUND_PAUSE_MS else PAUSE_MS
            rest(pause)
        }
    }

    /** A pause that a device just taken as one's own cuts short. */
    private suspend fun rest(ms: Long) {
        val scope = scope ?: return
        val job = scope.launch { delay(ms) }
        wake = job
        job.join()
        wake = null
    }

    private suspend fun probe(address: String): Boolean {
        if (address in aclUp) return true
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return false
        return withTimeoutOrNull(PROBE_MS) {
            acl.onSubscription {
                if (!runCatching { device.fetchUuidsWithSdp() }.getOrDefault(false)) emit("")
            }.first { it == address || it.isEmpty() }
        }.let { it == address }
    }

    companion object {
        private const val TAG = "HomeBluetooth"
        private const val KEY_OWNERS = "owners"

        /** The home screen paints first; Bluetooth waits. */
        private const val START_DELAY_MS = 1_500L

        /** A page gives up after about five seconds; the look-up after it takes a moment more. */
        private const val PROBE_MS = 8_000L
        private const val GAP_MS = 700L
        private const val PAUSE_MS = 12_000L
        private const val FOUND_PAUSE_MS = 45_000L
    }
}
