package com.lumera.app.control

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap

/** How the TV reaches the internet right now. */
data class NetInfo(
    val kind: Kind,
    /** The network's name, or null when Android keeps it from Neon Tokyo (no location permission). */
    val ssid: String? = null,
    val bars: Int = 0,
    val mbps: Int? = null,
    val band: String? = null,
    val ip: String? = null,
    /** The TV checked that the internet answers through it. */
    val internet: Boolean = false
) {
    enum class Kind { Wifi, Ethernet, Offline }
}

/**
 * The TV's network, read for the Control Center. Android only names Wi-Fi networks to an app
 * allowed to see the TV's location (the names give a home away), which neon-tv.ps1 grants.
 */
internal class NetReader(private val context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    val canName: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun read(): NetInfo {
        val network = runCatching { connectivity?.activeNetwork }.getOrNull()
        val caps = network?.let { runCatching { connectivity?.getNetworkCapabilities(it) }.getOrNull() }
            ?: return NetInfo(NetInfo.Kind.Offline)
        val internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val ip = runCatching {
            connectivity?.getLinkProperties(network)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress
        }.getOrNull()
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetInfo(NetInfo.Kind.Ethernet, ip = ip, internet = internet)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                // The old call still names the network to an app allowed to; the new one hides it
                @Suppress("DEPRECATION")
                val info = runCatching { wifi?.connectionInfo }.getOrNull()
                    ?: (if (Build.VERSION.SDK_INT >= 29) caps.transportInfo as? WifiInfo else null)
                NetInfo(
                    NetInfo.Kind.Wifi,
                    ssid = NetRules.ssid(info?.ssid),
                    bars = info?.rssi?.let { NetRules.bars(it) } ?: 0,
                    mbps = info?.linkSpeed?.takeIf { it > 0 },
                    band = info?.frequency?.let { NetRules.band(it) },
                    ip = ip,
                    internet = internet
                )
            }
            else -> NetInfo(NetInfo.Kind.Offline, ip = ip, internet = internet)
        }
    }

    /** Asks for a fresh look round. Android allows a few a minute; a refused one keeps the last. */
    @Suppress("DEPRECATION")
    fun scan() {
        if (canName) runCatching { wifi?.startScan() }
    }

    @SuppressLint("MissingPermission")
    fun networks(current: String?): List<WifiScan> {
        if (!canName) return emptyList()
        val scans = runCatching { wifi?.scanResults.orEmpty() }.getOrDefault(emptyList()).map {
            @Suppress("DEPRECATION")
            val name = if (Build.VERSION.SDK_INT >= 33) it.wifiSsid?.toString() else it.SSID
            val caps = it.capabilities.orEmpty()
            WifiScan(
                ssid = NetRules.ssid(name).orEmpty(),
                rssi = it.level,
                secure = listOf("WEP", "PSK", "SAE", "EAP", "OWE").any { k -> k in caps },
                frequency = it.frequency
            )
        }
        return NetRules.networks(scans, current)
    }

    /** Tells [onChange] of fresh scans and of the TV moving between networks, until [stop]. */
    fun watch(onChange: () -> Unit): () -> Unit {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) = onChange()
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            @Suppress("DEPRECATION")
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.RSSI_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        return { runCatching { context.unregisterReceiver(receiver) } }
    }
}

/**
 * How full the batteries of Bluetooth headphones are. Android hears it from AirPods and most
 * headphones and tells it to apps only as it changes, so it is kept from the moment Neon Tokyo
 * starts; until then it is asked of the device itself, where the TV allows that.
 */
internal object BtBattery {
    private const val ACTION = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    private const val EXTRA = "android.bluetooth.device.extra.BATTERY_LEVEL"
    private val levels = ConcurrentHashMap<String, Int>()
    @Volatile
    private var listening = false

    fun listen(context: Context) {
        if (listening) return
        listening = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent ?: return
                val device: BluetoothDevice? =
                    if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val level = intent.getIntExtra(EXTRA, -1)
                device?.address?.let { if (level in 0..100) levels[it] = level else levels.remove(it) }
            }
        }
        runCatching {
            ContextCompat.registerReceiver(context.applicationContext, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        }.onFailure { listening = false }
    }

    /** 0 to 100, or null when the device doesn't say. */
    @SuppressLint("MissingPermission")
    fun of(context: Context, address: String): Int? {
        levels[address]?.let { return it }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        return runCatching {
            val device = adapter.getRemoteDevice(address)
            device.javaClass.getMethod("getBatteryLevel").invoke(device) as? Int
        }.getOrNull()?.takeIf { it in 0..100 }?.also { levels[address] = it }
    }
}

/**
 * Do Not Disturb: the TV's own, where it let Neon Tokyo switch it (neon-tv.ps1 asks), and Neon
 * Tokyo's popups (the Bluetooth card, the console chime) either way.
 */
object Quiet {
    private const val PREFS = "neon_control"
    private const val KEY = "quiet"

    fun on(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        val notifications = context.getSystemService(NotificationManager::class.java) ?: return
        if (runCatching { notifications.isNotificationPolicyAccessGranted }.getOrDefault(false)) {
            runCatching {
                notifications.setInterruptionFilter(
                    if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
                )
            }
        }
    }

    /** The TV lets Neon Tokyo switch its own Do Not Disturb too. */
    fun system(context: Context): Boolean =
        runCatching { context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true }.getOrDefault(false)
}
