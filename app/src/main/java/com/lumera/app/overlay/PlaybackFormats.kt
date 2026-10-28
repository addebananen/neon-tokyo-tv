package com.lumera.app.overlay

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes

/** The video [format] the decoder now gets. */
fun PlayingSignal.withVideo(format: Format): PlayingSignal = copy(
    width = format.width.coerceAtLeast(0),
    height = format.height.coerceAtLeast(0),
    frameRate = format.frameRate.takeIf { it > 0f } ?: frameRate,
    videoCodec = videoCodecName(format.sampleMimeType, format.codecs),
    hdr = hdrName(format),
    bitDepth = format.colorInfo?.lumaBitdepth?.takeIf { it != Format.NO_VALUE },
    videoBitrate = format.bitrate.takeIf { it > 0 } ?: 0
)

/** The sound [format] the decoder now gets. */
fun PlayingSignal.withAudio(format: Format): PlayingSignal = copy(
    audioCodec = audioCodecName(format.sampleMimeType, format.codecs),
    audioChannels = format.channelCount.coerceAtLeast(0),
    audioSampleRate = format.sampleRate.coerceAtLeast(0)
)

internal fun hdrName(format: Format): String? {
    val mime = format.sampleMimeType
    val codecs = format.codecs.orEmpty().lowercase()
    if (mime == MimeTypes.VIDEO_DOLBY_VISION || codecs.startsWith("dvh") || codecs.startsWith("dva") || codecs.startsWith("dav1")) {
        return "Dolby Vision"
    }
    return when (format.colorInfo?.colorTransfer) {
        C.COLOR_TRANSFER_ST2084 -> "HDR10"
        C.COLOR_TRANSFER_HLG -> "HLG"
        else -> null
    }
}

internal fun videoCodecName(mime: String?, codecs: String?): String? = when (mime) {
    MimeTypes.VIDEO_H265 -> "HEVC"
    MimeTypes.VIDEO_H264 -> "H.264"
    MimeTypes.VIDEO_AV1 -> "AV1"
    MimeTypes.VIDEO_VP9 -> "VP9"
    MimeTypes.VIDEO_DOLBY_VISION -> if (codecs?.lowercase()?.startsWith("dav1") == true) "AV1" else "HEVC"
    MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
    MimeTypes.VIDEO_VC1 -> "VC-1"
    null -> null
    else -> mime.substringAfter('/').uppercase()
}

internal fun audioCodecName(mime: String?, codecs: String?): String? = when (mime) {
    MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Atmos (DD+)"
    MimeTypes.AUDIO_E_AC3 -> if (codecs?.contains("ec+3", ignoreCase = true) == true) "Dolby Atmos (DD+)" else "Dolby Digital Plus"
    MimeTypes.AUDIO_AC3 -> "Dolby Digital"
    MimeTypes.AUDIO_AC4 -> "Dolby AC-4"
    MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
    MimeTypes.AUDIO_DTS -> "DTS"
    MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
    MimeTypes.AUDIO_DTS_EXPRESS -> "DTS Express"
    MimeTypes.AUDIO_DTS_X -> "DTS:X"
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_OPUS -> "Opus"
    MimeTypes.AUDIO_FLAC -> "FLAC"
    MimeTypes.AUDIO_MPEG -> "MP3"
    MimeTypes.AUDIO_VORBIS -> "Vorbis"
    MimeTypes.AUDIO_RAW -> "PCM"
    null -> null
    else -> mime.substringAfter('/').uppercase()
}
