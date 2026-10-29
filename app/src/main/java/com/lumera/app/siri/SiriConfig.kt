package com.lumera.app.siri

import android.view.KeyEvent
import java.io.File
import java.util.Properties

/**
 * The bridge's settings, kept where the shell user can write: /data/local/tmp/neon-siri.
 * Neon Tokyo's settings page changes them through [SiriStatusServer].
 */
data class SiriConfig(
    /** 1.0 = Apple TV; 0.5 to 2.0. */
    val scrollSpeed: Float = 1.0f,
    val momentum: Boolean = true,
    val ringScrub: Boolean = true,
    /** Light taps on the pad's edges step (always on for the gen 1 remote, which has no ring). */
    val edgeTaps: Boolean = false,
    val backHoldHome: Boolean = true,
    /** A key for the Siri button besides voice search; 0 = voice search only. */
    val siriKey: Int = 0,
    val tvDoubleKey: Int = KeyEvent.KEYCODE_APP_SWITCH,
    val tvHoldKey: Int = KeyEvent.KEYCODE_SETTINGS,
    /** Keep the TV from sleeping deeply, so the Power button can wake it again. */
    val wakeFromRemote: Boolean = true,
    /** The paired remote, once found. */
    val address: String = ""
) {
    fun save() = runCatching {
        DIR.mkdirs()
        val p = Properties()
        p["scrollSpeed"] = scrollSpeed.toString()
        p["momentum"] = momentum.toString()
        p["ringScrub"] = ringScrub.toString()
        p["edgeTaps"] = edgeTaps.toString()
        p["backHoldHome"] = backHoldHome.toString()
        p["siriKey"] = siriKey.toString()
        p["tvDoubleKey"] = tvDoubleKey.toString()
        p["tvHoldKey"] = tvHoldKey.toString()
        p["wakeFromRemote"] = wakeFromRemote.toString()
        p["address"] = address
        FILE.outputStream().use { p.store(it, "Neon Tokyo Siri Remote bridge") }
    }

    /** Applies `key=value` pairs, as sent by the settings page. Unknown keys are ignored. */
    fun with(pairs: Map<String, String>): SiriConfig {
        var c = this
        pairs.forEach { (k, v) ->
            c = when (k) {
                "scrollSpeed" -> v.toFloatOrNull()?.coerceIn(0.5f, 2.0f)?.let { c.copy(scrollSpeed = it) } ?: c
                "momentum" -> c.copy(momentum = v.toBoolean())
                "ringScrub" -> c.copy(ringScrub = v.toBoolean())
                "edgeTaps" -> c.copy(edgeTaps = v.toBoolean())
                "backHoldHome" -> c.copy(backHoldHome = v.toBoolean())
                "siriKey" -> v.toIntOrNull()?.let { c.copy(siriKey = it) } ?: c
                "tvDoubleKey" -> v.toIntOrNull()?.let { c.copy(tvDoubleKey = it) } ?: c
                "tvHoldKey" -> v.toIntOrNull()?.let { c.copy(tvHoldKey = it) } ?: c
                "wakeFromRemote" -> c.copy(wakeFromRemote = v.toBoolean())
                else -> c
            }
        }
        return c
    }

    fun toJson(): String =
        """{"scrollSpeed":$scrollSpeed,"momentum":$momentum,"ringScrub":$ringScrub,"edgeTaps":$edgeTaps,""" +
            """"backHoldHome":$backHoldHome,"siriKey":$siriKey,"tvDoubleKey":$tvDoubleKey,"tvHoldKey":$tvHoldKey,""" +
            """"wakeFromRemote":$wakeFromRemote}"""

    companion object {
        val DIR = File("/data/local/tmp/neon-siri")
        private val FILE = File(DIR, "config.properties")

        fun load(): SiriConfig = runCatching {
            val p = Properties().apply { FILE.inputStream().use { load(it) } }
            SiriConfig().with(p.stringPropertyNames().associateWith { p.getProperty(it) })
                .copy(address = p.getProperty("address").orEmpty())
        }.getOrDefault(SiriConfig())
    }
}
