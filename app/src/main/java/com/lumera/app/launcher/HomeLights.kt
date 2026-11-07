package com.lumera.app.launcher

import android.content.Context
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** Where a light is steered from. */
enum class LightHub { Hue, HomeAssistant }

/**
 * A light, or a Hue room of lights, as the Today row shows it: on or off, how bright (0..1), and
 * its colour as it glows, or null for plain white.
 */
data class HomeLight(
    val hub: LightHub,
    val id: String,
    val name: String,
    val on: Boolean,
    val brightness: Float,
    val color: Int?,
    val reachable: Boolean = true,
    val dimmable: Boolean = true,
    val colorful: Boolean = false,
    /** A Hue room holds several lights; it is switched as one. */
    val room: Boolean = false
) {
    val key: String get() = "${hub.name}:$id"
}

/** A colour the light panel offers: a warm or cool white (a colour temperature) or a colour. */
enum class LightTone(val title: String, val argb: Int, val kelvin: Int? = null) {
    Warm("Warm", 0xFFFFB46B.toInt(), kelvin = 2400),
    Soft("Soft", 0xFFFFD9A8.toInt(), kelvin = 3000),
    Day("Daylight", 0xFFF4F6FF.toInt(), kelvin = 5000),
    Magenta("Neon pink", 0xFFFF2E9A.toInt()),
    Cyan("Neon cyan", 0xFF2EE6FF.toInt()),
    Violet("Violet", 0xFF8A4DFF.toInt()),
    Sunset("Sunset", 0xFFFF6A2E.toInt())
}

/** How a Hue bridge answered the link: paired, its button not pressed yet, or not there. */
enum class HuePairing { Paired, PressButton, Unreachable }

/**
 * Smart lights for the Today row, without any cloud of ours: a Philips Hue bridge on the home's
 * network (found by itself, paired with its round button) and Home Assistant (its address and a
 * long-lived token, typed on the phone), which brings IKEA, Shelly, Tuya and the rest along.
 * The keys are kept encrypted on the TV and only ever sent to the hub they belong to.
 */
internal object HomeLights {
    private const val TAG = "HomeLights"
    private const val SECURE = "neon_home_lights"
    private const val HUE_IP = "hue_ip"
    private const val HUE_USER = "hue_user"
    private const val HA_URL = "ha_url"
    private const val HA_TOKEN = "ha_token"
    private const val SHOWN = "shown"
    const val MAX_SHOWN = 8

    @Volatile private var store: SharedPreferences? = null

    private val _version = MutableStateFlow(0)
    /** Goes up when a hub is linked or forgotten, or the shown lights change. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun prefs(context: Context): SharedPreferences = store ?: synchronized(this) {
        store ?: run {
            val app = context.applicationContext
            runCatching {
                EncryptedSharedPreferences.create(
                    app, SECURE,
                    MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }.getOrElse {
                Log.w(TAG, "Secure storage unavailable, keeping the light keys in private app storage")
                app.getSharedPreferences(SECURE + "_fallback", Context.MODE_PRIVATE)
            }.also { store = it }
        }
    }

    fun hueLinked(context: Context): Boolean = prefs(context).getString(HUE_USER, null) != null
    fun homeAssistantLinked(context: Context): Boolean = prefs(context).getString(HA_TOKEN, null) != null
    fun linked(context: Context): Boolean = hueLinked(context) || homeAssistantLinked(context)
    fun homeAssistantUrl(context: Context): String? = prefs(context).getString(HA_URL, null)
    fun hueBridge(context: Context): String? = prefs(context).getString(HUE_IP, null)

    fun forget(context: Context, hub: LightHub) {
        prefs(context).edit().apply {
            when (hub) {
                LightHub.Hue -> { remove(HUE_IP); remove(HUE_USER) }
                LightHub.HomeAssistant -> { remove(HA_URL); remove(HA_TOKEN) }
            }
        }.apply()
        _version.update { it + 1 }
    }

    /** The lights picked for the row, by [HomeLight.key]; empty means the first ones there are. */
    fun shown(context: Context): List<String> =
        prefs(context).getString(SHOWN, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun setShown(context: Context, keys: List<String>) {
        prefs(context).edit().putString(SHOWN, keys.take(MAX_SHOWN).joinToString("\n")).apply()
        _version.update { it + 1 }
    }

    /** The lights for the row: the picked ones in their order, or else the first few of each hub. */
    fun forRow(context: Context, all: List<HomeLight>): List<HomeLight> {
        val picked = shown(context)
        if (picked.isEmpty()) return all.filter { it.reachable }.take(MAX_SHOWN)
        val byKey = all.associateBy { it.key }
        return picked.mapNotNull { byKey[it] }
    }

    // ---- Every light there is ----

    suspend fun all(context: Context): List<HomeLight> = coroutineScope {
        val hue = async(Dispatchers.IO) { if (hueLinked(context)) hueRooms(context) else emptyList() }
        val ha = async(Dispatchers.IO) { if (homeAssistantLinked(context)) haLights(context) else emptyList() }
        listOf(hue, ha).awaitAll().flatten()
    }

    /** Turns [light] on or off, sets how bright it is (0..1) or its [tone]; true when the hub took it. */
    suspend fun set(context: Context, light: HomeLight, on: Boolean? = null, brightness: Float? = null, tone: LightTone? = null): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                when (light.hub) {
                    LightHub.Hue -> hueSet(context, light, on, brightness, tone)
                    LightHub.HomeAssistant -> haSet(context, light, on, brightness, tone)
                }
            }.onFailure { Log.w(TAG, "Light change failed", it) }.getOrDefault(false)
        }

    // ---- Philips Hue ----

    /** The bridge on this network: from its own announcement, else from Philips' finder. */
    suspend fun findHueBridge(context: Context): String? =
        withTimeoutOrNull(6_000) { nsdFind(context, "_hue._tcp.") }
            ?: withContext(Dispatchers.IO) {
                runCatching {
                    val list = JSONArray(get("https://discovery.meethue.com") ?: return@runCatching null)
                    list.optJSONObject(0)?.optString("internalipaddress")?.takeIf { it.isNotBlank() }
                }.getOrNull()
            }

    /** Asks the bridge at [ip] for a key; it gives one in the half minute after its button is pressed. */
    suspend fun pairHue(context: Context, ip: String): HuePairing = withContext(Dispatchers.IO) {
        val answer = runCatching { send("http://$ip/api", "POST", JSONObject().put("devicetype", "neon_tokyo#tv").toString()) }.getOrNull()
            ?: return@withContext HuePairing.Unreachable
        val first = runCatching { JSONArray(answer).getJSONObject(0) }.getOrNull() ?: return@withContext HuePairing.Unreachable
        val user = first.optJSONObject("success")?.optString("username")
        if (!user.isNullOrBlank()) {
            prefs(context).edit().putString(HUE_IP, ip).putString(HUE_USER, user).apply()
            _version.update { it + 1 }
            HuePairing.Paired
        } else if (first.optJSONObject("error")?.optInt("type") == 101) HuePairing.PressButton
        else HuePairing.Unreachable
    }

    private fun hueBase(context: Context): String? {
        val ip = prefs(context).getString(HUE_IP, null) ?: return null
        val user = prefs(context).getString(HUE_USER, null) ?: return null
        return "http://$ip/api/$user"
    }

    /** The bridge's rooms and zones, each with its lights' state taken together. */
    private fun hueRooms(context: Context): List<HomeLight> {
        val base = hueBase(context) ?: return emptyList()
        val groups = JSONObject(get("$base/groups") ?: return emptyList())
        val lights = runCatching { JSONObject(get("$base/lights") ?: "{}") }.getOrDefault(JSONObject())
        val rooms = ArrayList<HomeLight>()
        for (id in groups.keys()) {
            val g = groups.optJSONObject(id) ?: continue
            val type = g.optString("type")
            if (type != "Room" && type != "Zone") continue
            val members = g.optJSONArray("lights")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            if (members.isEmpty()) continue
            val state = g.optJSONObject("state")
            val action = g.optJSONObject("action") ?: JSONObject()
            val reachable = members.any { lights.optJSONObject(it)?.optJSONObject("state")?.optBoolean("reachable", true) != false }
            val colorful = members.any { lights.optJSONObject(it)?.optJSONObject("state")?.has("xy") == true }
            val on = state?.optBoolean("any_on") ?: action.optBoolean("on")
            rooms += HomeLight(
                hub = LightHub.Hue,
                id = id,
                name = g.optString("name").ifBlank { "Room $id" },
                on = on,
                brightness = (action.optInt("bri", 254) / 254f).coerceIn(0.01f, 1f),
                color = hueColor(action),
                reachable = reachable,
                colorful = colorful,
                room = true
            )
        }
        // Rooms first, as the Hue app lists them
        return rooms.sortedWith(compareBy({ groups.optJSONObject(it.id)?.optString("type") != "Room" }, { it.id.toIntOrNull() ?: 0 }))
    }

    private fun hueSet(context: Context, light: HomeLight, on: Boolean?, brightness: Float?, tone: LightTone?): Boolean {
        val base = hueBase(context) ?: return false
        val body = JSONObject()
        if (on != null) body.put("on", on)
        if (brightness != null) body.put("on", true).put("bri", (brightness * 254).roundToInt().coerceIn(1, 254))
        if (tone != null) {
            body.put("on", true)
            if (tone.kelvin != null) body.put("ct", (1_000_000 / tone.kelvin).coerceIn(153, 500))
            else rgbToXy(tone.argb).let { (x, y) -> body.put("xy", JSONArray().put(x).put(y)) }
        }
        // A soft fade, as the Hue app's own switch does
        body.put("transitiontime", 4)
        val answer = send("$base/groups/${light.id}/action", "PUT", body.toString()) ?: return false
        return answer.contains("success")
    }

    /** The glow of a Hue state, from its colour (xy) or white (ct); null for a plain bulb. */
    private fun hueColor(action: JSONObject): Int? {
        val mode = action.optString("colormode")
        val xy = action.optJSONArray("xy")
        return when {
            mode == "xy" && xy != null -> xyToRgb(xy.optDouble(0).toFloat(), xy.optDouble(1).toFloat())
            mode == "ct" || action.has("ct") -> kelvinToRgb(1_000_000 / action.optInt("ct", 366).coerceAtLeast(1))
            else -> null
        }
    }

    // ---- Home Assistant ----

    /** Keeps Home Assistant at [url] with [token] once it answered; null, or why not. */
    suspend fun linkHomeAssistant(context: Context, url: String, token: String): String? = withContext(Dispatchers.IO) {
        val base = url.trim().trimEnd('/').let { if (it.startsWith("http")) it else "http://$it" }
        val answer = runCatching {
            val connection = open("$base/api/", "GET", token.trim())
            try {
                when (connection.responseCode) {
                    200 -> null
                    401 -> "Home Assistant did not take that token"
                    else -> "Home Assistant answered ${connection.responseCode}"
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { "The TV could not reach $base" }
        if (answer == null) {
            prefs(context).edit().putString(HA_URL, base).putString(HA_TOKEN, token.trim()).apply()
            _version.update { it + 1 }
        }
        answer
    }

    private fun haLights(context: Context): List<HomeLight> {
        val base = prefs(context).getString(HA_URL, null) ?: return emptyList()
        val token = prefs(context).getString(HA_TOKEN, null) ?: return emptyList()
        val states = JSONArray(get("$base/api/states", token) ?: return emptyList())
        val list = ArrayList<HomeLight>()
        for (i in 0 until states.length()) {
            val s = states.optJSONObject(i) ?: continue
            val id = s.optString("entity_id")
            if (!id.startsWith("light.")) continue
            val attrs = s.optJSONObject("attributes") ?: JSONObject()
            // Hue rooms in Home Assistant are light groups too; keep what it calls a group
            val state = s.optString("state")
            val modes = attrs.optJSONArray("supported_color_modes")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            val rgb = attrs.optJSONArray("rgb_color")
            list += HomeLight(
                hub = LightHub.HomeAssistant,
                id = id,
                name = attrs.optString("friendly_name").ifBlank { id.removePrefix("light.").replace('_', ' ') },
                on = state == "on",
                brightness = (attrs.optInt("brightness", 255) / 255f).coerceIn(0.01f, 1f),
                color = if (rgb != null && rgb.length() == 3) (0xFF shl 24) or (rgb.optInt(0) shl 16) or (rgb.optInt(1) shl 8) or rgb.optInt(2) else null,
                reachable = state != "unavailable",
                dimmable = modes.any { it != "onoff" },
                colorful = modes.any { it in setOf("hs", "xy", "rgb", "rgbw", "rgbww") },
                room = attrs.has("entity_id")
            )
        }
        return list.sortedBy { it.name.lowercase() }
    }

    private fun haSet(context: Context, light: HomeLight, on: Boolean?, brightness: Float?, tone: LightTone?): Boolean {
        val base = prefs(context).getString(HA_URL, null) ?: return false
        val token = prefs(context).getString(HA_TOKEN, null) ?: return false
        val off = on == false && brightness == null && tone == null
        val body = JSONObject().put("entity_id", light.id)
        if (!off) {
            body.put("transition", 0.4)
            if (brightness != null) body.put("brightness_pct", (brightness * 100).roundToInt().coerceIn(1, 100))
            if (tone != null) {
                if (tone.kelvin != null) body.put("color_temp_kelvin", tone.kelvin)
                else body.put("rgb_color", JSONArray().put((tone.argb shr 16) and 0xFF).put((tone.argb shr 8) and 0xFF).put(tone.argb and 0xFF))
            }
        }
        val connection = open("$base/api/services/light/${if (off) "turn_off" else "turn_on"}", "POST", token)
        return try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }

    // ---- The network ----

    private fun open(url: String, method: String, token: String? = null): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 4_000
            readTimeout = 6_000
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
        }

    private fun get(url: String, token: String? = null): String? {
        val connection = open(url, "GET", token)
        return try {
            if (connection.responseCode != 200) null else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun send(url: String, method: String, body: String): String? {
        val connection = open(url, method)
        return try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
            if (connection.responseCode !in 200..299) null else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** The first service of [type] announced on the network, by its address, or null. */
    private suspend fun nsdFind(context: Context, type: String): String? {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return null
        return suspendCancellableCoroutine { cont ->
            val done = java.util.concurrent.atomic.AtomicBoolean(false)
            val self = arrayOfNulls<NsdManager.DiscoveryListener>(1)
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    if (done.compareAndSet(false, true)) cont.resume(null)
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onServiceLost(service: NsdServiceInfo) = Unit
                override fun onServiceFound(service: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    nsd.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val host = serviceInfo.host?.hostAddress ?: return
                            if (!host.contains(':') && done.compareAndSet(false, true)) {
                                self[0]?.let { runCatching { nsd.stopServiceDiscovery(it) } }
                                cont.resume(host)
                            }
                        }
                    })
                }
            }
            self[0] = listener
            runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
                .onFailure { if (done.compareAndSet(false, true)) cont.resume(null) }
            cont.invokeOnCancellation { runCatching { nsd.stopServiceDiscovery(listener) } }
        }
    }

    // ---- Colour ----

    /** A colour as Hue's CIE xy (Wide gamut, as Philips' own guide does it). */
    internal fun rgbToXy(argb: Int): Pair<Double, Double> {
        fun lin(c: Int): Double = (c / 255.0).let { if (it > 0.04045) ((it + 0.055) / 1.055).pow(2.4) else it / 12.92 }
        val r = lin((argb shr 16) and 0xFF)
        val g = lin((argb shr 8) and 0xFF)
        val b = lin(argb and 0xFF)
        val x = r * 0.664511 + g * 0.154324 + b * 0.162028
        val y = r * 0.283881 + g * 0.668433 + b * 0.047685
        val z = r * 0.000088 + g * 0.072310 + b * 0.986039
        val sum = x + y + z
        if (sum <= 0.0) return 0.3227 to 0.329
        return (x / sum).round4() to (y / sum).round4()
    }

    private fun Double.round4() = (this * 10_000).roundToInt() / 10_000.0

    /** Hue's xy back to a bright colour for the glow on the card. */
    internal fun xyToRgb(x: Float, y: Float): Int {
        if (y <= 0f) return 0xFFFFFFFF.toInt()
        val z = 1f - x - y
        val bigY = 1f
        val bigX = bigY / y * x
        val bigZ = bigY / y * z
        var r = bigX * 1.656492f - bigY * 0.354851f - bigZ * 0.255038f
        var g = -bigX * 0.707196f + bigY * 1.655397f + bigZ * 0.036152f
        var b = bigX * 0.051713f - bigY * 0.121364f + bigZ * 1.011530f
        fun gamma(c: Float) = if (c <= 0.0031308f) 12.92f * c else 1.055f * c.toDouble().pow(1 / 2.4).toFloat() - 0.055f
        r = gamma(r.coerceAtLeast(0f)); g = gamma(g.coerceAtLeast(0f)); b = gamma(b.coerceAtLeast(0f))
        val top = max(r, max(g, b)).coerceAtLeast(1e-4f)
        fun byte(c: Float) = (c / top * 255).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
    }

    /** The colour of white light at [kelvin], for the glow (Tanner Helland's fit). */
    internal fun kelvinToRgb(kelvin: Int): Int {
        val t = kelvin.coerceIn(1000, 40000) / 100.0
        val r = if (t <= 66) 255.0 else 329.698727446 * (t - 60).pow(-0.1332047592)
        val g = if (t <= 66) 99.4708025861 * Math.log(t) - 161.1195681661 else 288.1221695283 * (t - 60).pow(-0.0755148492)
        val b = when {
            t >= 66 -> 255.0
            t <= 19 -> 0.0
            else -> 138.5177312231 * Math.log(t - 10) - 305.0447927307
        }
        fun byte(c: Double) = c.roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
    }
}
