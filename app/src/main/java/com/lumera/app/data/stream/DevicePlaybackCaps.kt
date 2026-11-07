package com.lumera.app.data.stream

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.Display

/**
 * Reads once what this TV's panel shows and its chips decode, for [SourceRanker].
 * Anything that cannot be read is assumed supported, so a ranking never hides a format
 * just because a vendor's firmware was shy about it.
 */
object DevicePlaybackCaps {

    @Volatile private var cached: PlaybackCaps? = null

    fun get(context: Context): PlaybackCaps {
        cached?.let { return it }
        val caps = runCatching { read(context.applicationContext) }.getOrDefault(PlaybackCaps.UNKNOWN)
        cached = caps
        return caps
    }

    private fun read(context: Context): PlaybackCaps {
        val display = (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        @Suppress("DEPRECATION")
        val hdrTypes: IntArray? = display?.let { d ->
            runCatching {
                if (Build.VERSION.SDK_INT >= 34) d.mode.supportedHdrTypes else d.hdrCapabilities?.supportedHdrTypes
            }.getOrNull()
        }
        val decoders = runCatching { MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { !it.isEncoder } }
            .getOrDefault(emptyList())

        fun hasDecoder(mime: String, hardwareOnly: Boolean): Boolean = decoders.any { info ->
            info.supportedTypes.any { it.equals(mime, ignoreCase = true) } && (!hardwareOnly || isHardware(info))
        }

        val known = hdrTypes != null
        val types = hdrTypes?.toSet().orEmpty()
        val dvPanel = Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in types
        return PlaybackCaps(
            // Dolby Vision needs both the panel and the decoder
            dolbyVision = if (known) dvPanel && hasDecoder("video/dolby-vision", hardwareOnly = false) else true,
            hdr10 = if (known) Display.HdrCapabilities.HDR_TYPE_HDR10 in types || dvPanel else true,
            hdr10Plus = Build.VERSION.SDK_INT >= 29 && Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS in types,
            av1Hardware = if (decoders.isEmpty()) true else hasDecoder("video/av01", hardwareOnly = true)
        )
    }

    private fun isHardware(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= 29) return info.isHardwareAccelerated
        val name = info.name.lowercase()
        return !name.startsWith("omx.google.") && !name.startsWith("c2.android.") && !name.contains("ffmpeg")
    }
}
