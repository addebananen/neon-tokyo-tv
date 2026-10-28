package com.lumera.app.overlay

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.view.Display
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** How a value is known: measured here and now, told by the TV, or not to be had. */
enum class How { Measured, Reported, Unknown }

data class Reading(val label: String, val value: String, val how: How, val note: String? = null)

data class Section(val title: String, val readings: List<Reading>)

/**
 * What the TV can tell about the picture and the sound, split as the eye needs it: the signal
 * that comes in (the film, the console) and what the panel does with it. Only what Android
 * really reports is shown; what it keeps to itself says so instead of being guessed.
 */
data class SignalReport(
    /** Where the signal comes from: "Neon Tokyo player", "Switch 2 · HDMI 2", an app's name. */
    val source: String,
    val signal: Section,
    val panel: Section,
    val hdr: Section,
    val game: Section,
    val sound: Section,
    val latency: Section,
    /** The gaming HUD's short line, in order. */
    val hud: List<Reading> = emptyList(),
    val at: Long = SystemClock.uptimeMillis()
)

/** The panel's mode, for the HUD's short line. */
data class PanelMode(val width: Int, val height: Int, val hz: Float, val uiWidth: Int, val uiHeight: Int)

/** Where the TV sends media sound now. */
data class SoundRoute(val name: String, val formats: List<String>, val maxChannels: Int)

internal class SignalProbe(private val context: Context) {
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)

    private var lastFrames: Int? = null
    private var lastFramesAt = 0L
    /** The frame rate measured on the player's own counter, eased over the last readings. */
    var measuredFps: Float? = null
        private set

    private val display: Display? get() = displays?.getDisplay(Display.DEFAULT_DISPLAY)

    fun panelMode(): PanelMode? {
        val d = display ?: return null
        val mode = d.mode
        val ui = android.graphics.Point()
        @Suppress("DEPRECATION")
        d.getRealSize(ui)
        return PanelMode(mode.physicalWidth, mode.physicalHeight, mode.refreshRate, ui.x, ui.y)
    }

    /** The refresh rates the panel can switch to at its current size. */
    fun panelRates(): List<Float> {
        val d = display ?: return emptyList()
        val mode = d.mode
        return d.supportedModes
            .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            .map { it.refreshRate }
            .distinctBy { (it * 100).roundToInt() }
            .sorted()
    }

    /** The HDR formats the panel shows, best known first. */
    fun panelHdr(): List<String> {
        val d = display ?: return emptyList()
        val types = runCatching {
            if (Build.VERSION.SDK_INT >= 34) d.mode.supportedHdrTypes else d.hdrCapabilities?.supportedHdrTypes
        }.getOrNull() ?: IntArray(0)
        return types.toList().distinct().sortedBy { HDR_ORDER.indexOf(it).let { i -> if (i < 0) 99 else i } }.mapNotNull { hdrTypeName(it) }
    }

    /** The low-latency mode the panel offers apps and consoles (ALLM's partner on the TV side). */
    fun gameModeSupported(): Boolean? =
        if (Build.VERSION.SDK_INT >= 30) runCatching { display?.isMinimalPostProcessingSupported }.getOrNull() else null

    fun soundRoute(): SoundRoute? {
        val audio = audio ?: return null
        val device = mediaDevice(audio) ?: return null
        val formats = buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                runCatching {
                    audio.getDirectProfilesForAttributes(MEDIA).map { it.format }.forEach { add(it) }
                }
            }
            addAll(device.encodings.toList())
        }.distinct().filter { it !in PCM }.sortedBy { ENCODING_ORDER.indexOf(it).let { i -> if (i < 0) 99 else i } }
            .mapNotNull { encodingName(it) }
            .distinct()
        val channels = device.channelCounts.maxOrNull() ?: 0
        return SoundRoute(deviceName(device), formats, channels)
    }

    /** Frames the Neon Tokyo player put on screen per second, from two readings of its counter. Main thread. */
    fun sampleFrames() {
        val frames = PlaybackSignal.renderedFrames?.invoke()
        val now = SystemClock.uptimeMillis()
        val before = lastFrames
        if (frames == null) {
            lastFrames = null
            measuredFps = null
            return
        }
        if (before != null && now > lastFramesAt && frames >= before) {
            val fps = (frames - before) * 1000f / (now - lastFramesAt)
            measuredFps = measuredFps?.let { it * 0.6f + fps * 0.4f } ?: fps
        }
        lastFrames = frames
        lastFramesAt = now
    }

    fun hasDump(): Boolean =
        context.checkSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED

    /**
     * The whole report. [layer] is what SurfaceFlinger says about the video on screen, when the
     * TV lets Neon Tokyo ask; [console] the console showing; [audioMs] the measured sound delay.
     */
    fun report(
        console: ConsoleNow?,
        foreground: String?,
        layer: VideoLayer?,
        audioMs: Int?,
        keyMs: Int?
    ): SignalReport {
        val playing = PlaybackSignal.current?.takeIf { foreground == null || foreground == context.packageName }
        val panel = panelMode()
        val rates = panelRates()
        val hdrCaps = panelHdr()
        val route = soundRoute()

        val source = when {
            playing != null -> "Neon Tokyo player"
            console != null -> console.title
            foreground != null && foreground != context.packageName -> appName(foreground)
            else -> "Neon Tokyo"
        }

        // The signal: what comes in, before the TV does anything with it
        val signal = buildList {
            when {
                playing != null -> {
                    if (playing.width > 0) add(Reading("Resolution", "${playing.width} × ${playing.height}", How.Reported, "In the file"))
                    if (playing.frameRate > 0f) add(Reading("Frame rate", fpsText(playing.frameRate) + " fps", How.Reported, "In the file"))
                    measuredFps?.takeIf { it > 0.5f }?.let {
                        add(Reading("Shown", fpsText(it) + " fps", How.Measured, if (playing.droppedFrames > 0) "${playing.droppedFrames} frames dropped" else "No dropped frames"))
                    }
                    playing.videoCodec?.let { codec ->
                        val bits = playing.bitDepth?.let { " · $it-bit" }.orEmpty()
                        val rate = playing.videoBitrate.takeIf { it > 0 }?.let { " · " + mbps(it) }.orEmpty()
                        add(Reading("Codec", codec + bits + rate, How.Reported, playing.videoDecoder?.let { decoderNote(it) }))
                    }
                    add(Reading("HDR", playing.hdr ?: "SDR", How.Reported))
                }
                layer != null -> {
                    if (layer.width > 0) add(Reading("Resolution", "${layer.width} × ${layer.height}", How.Reported, "Video layer"))
                    layer.fps?.let { add(Reading("Frame rate", fpsText(it) + " fps", How.Measured, "Frames reaching the screen")) }
                    layer.hdr?.let { add(Reading("HDR", it, How.Reported, "Colour space of the video")) }
                }
                console != null -> {
                    add(Reading("Input", console.input ?: "HDMI", How.Reported, if (console.connected) "Signal present" else "No signal"))
                    console.device?.let { add(Reading("Device", it, How.Reported, "Over HDMI-CEC")) }
                    add(Reading("Resolution", "—", How.Unknown, "The TV keeps the HDMI signal to itself"))
                    add(Reading("Frame rate", "—", How.Unknown, "The TV keeps the HDMI signal to itself"))
                }
                else -> add(Reading("Video", "Nothing playing", How.Reported, "Start a film or a console"))
            }
        }

        // The panel: what the screen is doing
        val panelReadings = buildList {
            if (panel != null) {
                add(Reading("Mode", "${panel.width} × ${panel.height}", How.Reported))
                add(Reading("Refresh", hzText(panel.hz), How.Reported, playing?.let { cadence(it.frameRate, panel.hz) }))
                if (panel.uiWidth > 0 && (panel.uiWidth != panel.width || panel.uiHeight != panel.height)) {
                    add(Reading("Menus drawn at", "${panel.uiWidth} × ${panel.uiHeight}", How.Reported, "Scaled up to the panel; video keeps its own sharpness"))
                }
                if (rates.size > 1) add(Reading("Can switch to", rates.joinToString(" · ") { hzText(it) }, How.Reported))
            } else {
                add(Reading("Mode", "—", How.Unknown))
            }
        }

        val hdrReadings = buildList {
            add(Reading("Panel shows", hdrCaps.joinToString(" · ").ifBlank { "SDR only" }, How.Reported))
            val now = playing?.hdr ?: layer?.hdr
            when {
                now != null -> add(Reading("Now", now, How.Reported, if (now in hdrCaps || now == "HDR10" && "HDR10+" in hdrCaps) "Shown as made" else "Tone-mapped by the TV"))
                playing != null || layer != null -> add(Reading("Now", "SDR", How.Reported))
                console != null -> add(Reading("Now", "—", How.Unknown, "Not reported for HDMI"))
            }
        }

        val gameReadings = buildList {
            when (gameModeSupported()) {
                true -> add(Reading("Low-latency mode", "Supported", How.Reported, "ALLM switches it on from a console"))
                false -> add(Reading("Low-latency mode", "No", How.Reported, "The TV offers no game mode to apps"))
                null -> add(Reading("Low-latency mode", "—", How.Unknown))
            }
            add(Reading("ALLM now", "—", How.Unknown, "The TV doesn't tell apps"))
            add(Reading("VRR now", "—", How.Unknown, "Android gives apps no way to ask"))
        }

        val soundReadings = buildList {
            if (route != null) {
                add(Reading("Output", route.name, How.Reported, route.maxChannels.takeIf { it > 2 }?.let { "Up to ${channelsText(it)}" }))
                add(Reading("Takes", route.formats.joinToString(" · ").ifBlank { "PCM only" }, How.Reported))
            }
            if (playing?.audioCodec != null) {
                val layout = playing.audioChannels.takeIf { it > 0 }?.let { " " + channelsText(it) }.orEmpty()
                add(Reading("Playing", playing.audioCodec + layout, How.Reported))
                playing.audioPassthrough?.let { packed ->
                    add(Reading("Sent as", if (packed) "Bitstream" else "PCM", How.Reported, if (packed) "Your speakers decode it" else "Decoded on the TV"))
                }
            }
            audioMs?.let { add(Reading("Sound delay", "$it ms", How.Measured, "From the TV's mixer to the output")) }
                ?: add(Reading("Sound delay", "Measuring…", How.Unknown))
        }

        val latencyReadings = buildList {
            keyMs?.let { add(Reading("Remote to TV", "$it ms", How.Measured, "Last press, as the system got it")) }
                ?: add(Reading("Remote to TV", "—", How.Unknown, "Shows after the next press"))
            pipelineMs()?.let { add(Reading("Picture pipeline", "$it ms", How.Reported, "The TV's own figure")) }
            add(Reading("Console to screen", "—", How.Unknown, "Needs a camera to measure"))
        }

        val hud = buildList {
            val res = playing?.takeIf { it.width > 0 }?.let { it.width to it.height } ?: layer?.takeIf { it.width > 0 }?.let { it.width to it.height }
            when {
                res != null -> add(Reading("RES", resolutionShort(res.first, res.second), How.Reported))
                panel != null -> add(Reading("PANEL", resolutionShort(panel.width, panel.height), How.Reported))
            }
            val fps = measuredFps?.takeIf { playing != null && it > 0.5f } ?: layer?.fps
            if (fps != null) add(Reading("FPS", fpsText(fps), How.Measured))
            else playing?.frameRate?.takeIf { it > 0f }?.let { add(Reading("FPS", fpsText(it), How.Reported)) }
            panel?.let { add(Reading("REFRESH", hzText(it.hz), How.Reported)) }
            val hdrNow = playing?.hdr ?: layer?.hdr
            when {
                hdrNow != null -> add(Reading("HDR", hdrNow, How.Reported))
                playing != null || layer != null -> add(Reading("HDR", "SDR", How.Reported))
                else -> add(Reading("HDR", "n/a", How.Unknown))
            }
            add(Reading("VRR", "n/a", How.Unknown))
            add(Reading("ALLM", "n/a", How.Unknown))
            route?.let { r ->
                val best = playing?.audioCodec?.let { shortCodec(it) } ?: r.formats.firstOrNull()?.let { shortCodec(it) }
                add(Reading("SOUND", listOfNotNull(shortRoute(r.name), best).joinToString(" · "), How.Reported))
            }
            add(audioMs?.let { Reading("DELAY", "$it ms", How.Measured) } ?: Reading("DELAY", "…", How.Unknown))
            keyMs?.let { add(Reading("INPUT", "$it ms", How.Measured)) }
        }

        return SignalReport(
            source = source,
            hud = hud,
            signal = Section("Signal in", signal),
            panel = Section("Panel", panelReadings),
            hdr = Section("HDR", hdrReadings),
            game = Section("Gaming", gameReadings),
            sound = Section("Sound", soundReadings),
            latency = Section("Latency", latencyReadings)
        )
    }

    /** The display's own estimate of how long a frame takes from the app to the glass. */
    private fun pipelineMs(): Int? {
        val d = display ?: return null
        val ns = runCatching { d.presentationDeadlineNanos + d.appVsyncOffsetNanos }.getOrNull() ?: return null
        return (ns / 1_000_000L).toInt().takeIf { it in 1..500 }
    }

    private fun appName(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        val MEDIA: AudioAttributes by lazy {
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
        }

        private val HDR_ORDER = listOf(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION, Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS, Display.HdrCapabilities.HDR_TYPE_HDR10, Display.HdrCapabilities.HDR_TYPE_HLG)

        fun hdrTypeName(type: Int): String? = when (type) {
            Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
            Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
            Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
            Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
            else -> null
        }

        private val PCM = setOf(
            AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_FLOAT,
            AudioFormat.ENCODING_PCM_24BIT_PACKED, AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_DEFAULT,
            AudioFormat.ENCODING_INVALID
        )

        private val ENCODING_ORDER = listOf(
            AudioFormat.ENCODING_DOLBY_MAT, AudioFormat.ENCODING_E_AC3_JOC, AudioFormat.ENCODING_DOLBY_TRUEHD,
            AudioFormat.ENCODING_E_AC3, AudioFormat.ENCODING_AC4, AudioFormat.ENCODING_AC3,
            AudioFormat.ENCODING_DTS_UHD_P2, AudioFormat.ENCODING_DTS_UHD_P1, AudioFormat.ENCODING_DTS_HD, AudioFormat.ENCODING_DTS
        )

        fun encodingName(encoding: Int): String? = when (encoding) {
            AudioFormat.ENCODING_DOLBY_MAT -> "Atmos (MAT)"
            AudioFormat.ENCODING_E_AC3_JOC -> "Atmos"
            AudioFormat.ENCODING_DOLBY_TRUEHD -> "TrueHD"
            AudioFormat.ENCODING_E_AC3 -> "DD+"
            AudioFormat.ENCODING_AC4 -> "AC-4"
            AudioFormat.ENCODING_AC3 -> "Dolby Digital"
            AudioFormat.ENCODING_DTS_UHD_P2 -> "DTS:X"
            AudioFormat.ENCODING_DTS_UHD_P1 -> "DTS:X"
            AudioFormat.ENCODING_DTS_HD -> "DTS-HD"
            AudioFormat.ENCODING_DTS -> "DTS"
            else -> null
        }

        /** The device media sound plays on now. */
        fun mediaDevice(audio: AudioManager): AudioDeviceInfo? {
            if (Build.VERSION.SDK_INT >= 33) {
                runCatching { audio.getAudioDevicesForAttributes(MEDIA).firstOrNull() }.getOrNull()?.let { routed ->
                    // The routing answer has no formats; the full device from the list has them
                    return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == routed.id }
                        ?: audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == routed.type }
                        ?: routed
                }
            }
            val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            return outputs.minByOrNull { ROUTE_ORDER.indexOf(it.type).let { i -> if (i < 0) 99 else i } }
        }

        private val ROUTE_ORDER = listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_HDMI_EARC,
            AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        )

        fun deviceName(device: AudioDeviceInfo): String {
            val product = device.productName?.toString()?.trim().orEmpty()
            return when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "TV speakers"
                AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI eARC"
                AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
                AudioDeviceInfo.TYPE_HDMI -> "HDMI"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER ->
                    product.ifBlank { "Bluetooth" }
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> product.ifBlank { "USB audio" }
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Headphones"
                AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Optical"
                else -> product.ifBlank { "Speakers" }
            }
        }

        fun fpsText(fps: Float): String {
            val known = listOf(23.976f, 24f, 25f, 29.97f, 30f, 47.952f, 48f, 50f, 59.94f, 60f, 100f, 119.88f, 120f, 144f)
            known.firstOrNull { abs(it - fps) < 0.02f }?.let { return trim(it) }
            return if (fps >= 10f) fps.roundToInt().toString() else String.format(Locale.ROOT, "%.1f", fps)
        }

        fun resolutionShort(width: Int, height: Int): String {
            val h = minOf(width, height)
            return when {
                h >= 4320 -> "8K"
                h >= 2160 -> if (maxOf(width, height) >= 4096) "4K DCI" else "4K"
                h >= 1440 -> "1440p"
                h >= 1080 -> "1080p"
                h >= 720 -> "720p"
                h >= 576 -> "576p"
                h >= 480 -> "480p"
                else -> "${width}×$height"
            }
        }

        private fun shortRoute(name: String): String = when (name) {
            "HDMI eARC" -> "eARC"
            "HDMI ARC" -> "ARC"
            "TV speakers" -> "TV"
            else -> name.take(14)
        }

        private fun shortCodec(codec: String): String = when {
            "Atmos" in codec -> "Atmos"
            codec == "Dolby Digital Plus" -> "DD+"
            codec == "Dolby Digital" -> "DD"
            codec == "Dolby TrueHD" -> "TrueHD"
            else -> codec
        }

        fun hzText(hz: Float): String = fpsText(hz) + " Hz"

        private fun trim(value: Float): String =
            String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.')

        fun channelsText(count: Int): String = when (count) {
            1 -> "mono"
            2 -> "stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> "$count ch"
        }

        private fun mbps(bits: Int): String = String.format(Locale.ROOT, "%.1f Mb/s", bits / 1_000_000f)

        /** A hardware decoder's name says so; a software one is worth knowing about. */
        private fun decoderNote(name: String): String {
            val soft = name.startsWith("c2.android", ignoreCase = true) || name.startsWith("OMX.google", ignoreCase = true) ||
                name.contains("ffmpeg", ignoreCase = true) || name.contains("libgav1", ignoreCase = true) || name.contains("dav1d", ignoreCase = true)
            return if (soft) "Software decoder" else "Hardware decoder"
        }

        /**
         * How film frames sit on the panel's refresh: 24 fps on 120 Hz is 5 refreshes each,
         * smooth; 24 on 60 is 3:2 pulldown, a faint judder.
         */
        fun cadence(fps: Float, hz: Float): String? {
            if (fps <= 1f || hz <= 1f) return null
            val ratio = hz / fps
            val whole = ratio.roundToInt()
            return when {
                whole >= 1 && abs(ratio - whole) < 0.01f * whole -> if (whole == 1) "Matches the film" else "$whole× the film, smooth"
                abs(ratio - 2.5f) < 0.03f -> "3:2 pulldown, faint judder"
                else -> "Not a multiple of the film"
            }
        }
    }
}

/** The console the TV shows, as far as the home screen knows. */
data class ConsoleNow(val title: String, val input: String?, val device: String?, val connected: Boolean, val color: Long)
