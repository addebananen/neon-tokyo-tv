package com.lumera.app.data.stream

import com.lumera.app.data.model.StreamQuality
import com.lumera.app.data.model.stremio.Stream
import kotlin.math.ln
import kotlin.math.min

/**
 * What this TV can actually show and decode, so the ranking never prefers a source that
 * would play with wrong colours (Dolby Vision without a DV panel) or stutter (AV1 in software).
 */
data class PlaybackCaps(
    val dolbyVision: Boolean = true,
    val hdr10: Boolean = true,
    val hdr10Plus: Boolean = false,
    val av1Hardware: Boolean = true
) {
    companion object {
        val UNKNOWN = PlaybackCaps()
    }
}

/** Everything the ranking reads from one source's text, also used for its short labels. */
data class SourceFeatures(
    val quality: StreamQuality,
    val dolbyVision: Boolean,
    val hdr10Plus: Boolean,
    val hdr: Boolean,
    val audio: Audio,
    val channels: String?,
    val sizeBytes: Long?,
    val seeds: Int?,
    val hevc: Boolean,
    val av1: Boolean,
    val threeD: Boolean,
    val cached: Boolean,
    val uncachedDebrid: Boolean,
    val direct: Boolean,
    val torrent: Boolean,
    val swedish: Boolean
) {
    enum class Audio(val label: String?, val points: Int) {
        ATMOS("Atmos", 46),
        DTS_X("DTS:X", 42),
        TRUEHD("TrueHD", 38),
        DTS_HD("DTS-HD MA", 34),
        DDP("DD+", 26),
        DTS("DTS", 22),
        DD("Dolby Digital", 18),
        AAC("AAC", 2),
        UNKNOWN(null, 0)
    }
}

/** A source with its score, best first when sorted descending. */
data class RankedSource(val stream: Stream, val score: Double, val features: SourceFeatures)

/**
 * Picks the source a person would pick themselves after reading the whole list: the sharpest
 * picture this TV can show (4K, Dolby Vision or HDR), the richest sound (Atmos, TrueHD, DTS-HD),
 * a file big enough to look good but not so heavy it buffers, and one that starts right away
 * (cached debrid or a direct link before a torrent with few seeders).
 *
 * Pure Kotlin so it can be unit tested; the TV's capabilities come in as [PlaybackCaps].
 */
object SourceRanker {

    private val dvPattern = Regex("""(?i)(?<![a-z0-9])(dolby[\s._-]*vision|dovi|dv)(?![a-z0-9])""")
    private val hdr10PlusPattern = Regex("""(?i)(?<![a-z0-9])hdr[\s._-]?10(\+|[\s._-]?plus)""")
    private val hdrPattern = Regex("""(?i)(?<![a-z0-9])(hdr(10)?|hlg)(?![a-z])""")
    private val atmosPattern = Regex("""(?i)(?<![a-z])atmos(?![a-z])""")
    private val dtsXPattern = Regex("""(?i)(?<![a-z])dts[\s._:-]?x(?![a-z0-9])""")
    private val trueHdPattern = Regex("""(?i)(?<![a-z])true[\s._-]?hd(?![a-z])""")
    private val dtsHdPattern = Regex("""(?i)(?<![a-z])dts[\s._-]?(hd([\s._-]?ma)?|ma)(?![a-z])""")
    private val ddpPattern = Regex("""(?i)(?<![a-z])(dd\+|ddp|e-?ac-?3|dolby[\s._-]*digital[\s._-]*plus)""")
    private val dtsPattern = Regex("""(?i)(?<![a-z])dts(?![a-z])""")
    private val ddPattern = Regex("""(?i)(?<![a-z])(dd[\s._-]?[257][\s._]?[01]|ac-?3|dolby[\s._-]*digital)(?![a-z])""")
    private val aacPattern = Regex("""(?i)(?<![a-z])aac""")
    private val channelsPattern = Regex("""(?<![0-9])([752])\.([01])(?![0-9])""")
    private val hevcPattern = Regex("""(?i)(?<![a-z0-9])(hevc|[hx][\s.]?265)(?![0-9])""")
    private val av1Pattern = Regex("""(?i)(?<![a-z0-9])av1(?![a-z0-9])""")
    private val threeDPattern = Regex("""(?i)(?<![a-z0-9])(3d|h?sbs|half[\s._-]?(sbs|ou)|hou)(?![a-z0-9])""")
    // Debrid addons mark a file their service already holds with a "+" or a lightning bolt
    private val cachedPattern = Regex("""(?i)(\[(rd|ad|pm|tb|dl|ed|oc|pk|en|db)\+]|⚡|(?<![a-z])(instant|cached)(?![a-z]))""")
    private val uncachedPattern = Regex("""(?i)(\[(rd|ad|pm|tb|dl|ed|oc|pk|en|db)(\s?download|⏳)]|⏳|(?<![a-z])uncached(?![a-z]))""")
    private val swedishPattern = Regex("""(?i)((?<![a-z])(swe|swedish|svenska|nordic)(?![a-z])|🇸🇪)""")

    fun features(stream: Stream): SourceFeatures {
        val info = StreamParser.parse(stream)
        val text = StreamParser.combinedText(stream)
        val audio = when {
            atmosPattern.containsMatchIn(text) -> SourceFeatures.Audio.ATMOS
            dtsXPattern.containsMatchIn(text) -> SourceFeatures.Audio.DTS_X
            trueHdPattern.containsMatchIn(text) -> SourceFeatures.Audio.TRUEHD
            dtsHdPattern.containsMatchIn(text) -> SourceFeatures.Audio.DTS_HD
            ddpPattern.containsMatchIn(text) -> SourceFeatures.Audio.DDP
            dtsPattern.containsMatchIn(text) -> SourceFeatures.Audio.DTS
            ddPattern.containsMatchIn(text) -> SourceFeatures.Audio.DD
            aacPattern.containsMatchIn(text) -> SourceFeatures.Audio.AAC
            else -> SourceFeatures.Audio.UNKNOWN
        }
        val channels = channelsPattern.findAll(text)
            .map { "${it.groupValues[1]}.${it.groupValues[2]}" }
            .filter { it in CHANNEL_LAYOUTS }
            .maxByOrNull { it.first().digitToInt() }
        val hdr10Plus = hdr10PlusPattern.containsMatchIn(text)
        val hasUrl = !stream.url.isNullOrBlank()
        val torrent = !hasUrl && !stream.infoHash.isNullOrBlank()
        return SourceFeatures(
            quality = info.quality,
            dolbyVision = dvPattern.containsMatchIn(text),
            hdr10Plus = hdr10Plus,
            hdr = hdr10Plus || hdrPattern.containsMatchIn(text),
            audio = audio,
            channels = channels,
            sizeBytes = info.sizeBytes,
            seeds = info.seeds,
            hevc = hevcPattern.containsMatchIn(text),
            av1 = av1Pattern.containsMatchIn(text),
            threeD = threeDPattern.containsMatchIn(text),
            cached = cachedPattern.containsMatchIn(text),
            uncachedDebrid = uncachedPattern.containsMatchIn(text),
            direct = hasUrl,
            torrent = torrent,
            swedish = swedishPattern.containsMatchIn(text)
        )
    }

    /**
     * @param runtimeMinutes the film's or episode's length, to judge the bitrate from the file
     *   size; without it a feature film or an episode is assumed.
     */
    fun score(f: SourceFeatures, caps: PlaybackCaps, runtimeMinutes: Int?, isEpisode: Boolean): Double {
        var s = when (f.quality) {
            StreamQuality.UHD_4K -> 400.0
            StreamQuality.FHD_1080P -> 300.0
            StreamQuality.HD_720P -> 190.0
            StreamQuality.UNKNOWN -> 150.0
            StreamQuality.SD_480P -> 60.0
            StreamQuality.CAM -> -600.0
        }

        // Picture: only formats the panel shows count; a Dolby Vision file with no HDR10 base
        // layer plays green and purple on a TV without Dolby Vision, so it drops far down
        s += when {
            f.dolbyVision && caps.dolbyVision -> 70.0
            f.dolbyVision && f.hdr -> if (caps.hdr10) 45.0 else 5.0
            f.dolbyVision -> -250.0
            f.hdr10Plus && caps.hdr10Plus -> 55.0
            f.hdr -> if (caps.hdr10) 45.0 else 5.0
            else -> 0.0
        }

        // Sound
        s += f.audio.points
        s += when (f.channels) {
            "7.1" -> 6.0
            "5.1" -> 4.0
            else -> 0.0
        }

        // Codec: AV1 without a hardware decoder stutters at these sizes
        if (f.av1 && !caps.av1Hardware) s -= 300.0
        if (f.hevc) s += 4.0
        if (f.threeD) s -= 500.0

        // Starts right away?
        if (f.cached) s += 60.0
        if (f.uncachedDebrid) s -= 90.0
        if (f.direct && !f.cached && !f.uncachedDebrid) s += 20.0
        if (f.torrent) {
            val seeds = f.seeds
            s += when {
                seeds == null -> -10.0
                seeds == 0 -> -220.0
                else -> min(40.0, 14.0 * ln(1.0 + seeds)) - 22.0
            }
        }

        s += bitrateScore(f, runtimeMinutes ?: if (isEpisode) 45 else 115)

        if (f.swedish) s += 8.0
        return s
    }

    /**
     * Rewards a file that is big enough for its resolution and stops rewarding once it gets so
     * heavy (a 70 GB remux) that it may buffer over Wi-Fi.
     */
    private fun bitrateScore(f: SourceFeatures, runtimeMinutes: Int): Double {
        val size = f.sizeBytes ?: return -5.0
        if (runtimeMinutes <= 0) return 0.0
        val mbps = size * 8.0 / (runtimeMinutes * 60.0) / 1_000_000.0
        val (low, high) = when (f.quality) {
            StreamQuality.UHD_4K -> 12.0 to 65.0
            StreamQuality.FHD_1080P -> 5.0 to 30.0
            StreamQuality.HD_720P -> 2.5 to 12.0
            else -> 1.0 to 8.0
        }
        return when {
            // A 2 GB "4K" film is an upscale or smeared: it should lose to a full 1080p
            mbps < low -> -150.0 * ((low - mbps) / low)
            mbps > high -> -min(80.0, (mbps - high) * 1.5)
            else -> 22.0 * ((mbps - low) / (high - low))
        }
    }

    /** Best first. Ties keep the addon order the person set. */
    fun rank(
        streams: List<Stream>,
        caps: PlaybackCaps,
        runtimeMinutes: Int? = null,
        isEpisode: Boolean = false,
        addonSortOrders: Map<String, Int> = emptyMap()
    ): List<RankedSource> = streams
        .map { stream ->
            val f = features(stream)
            RankedSource(stream, score(f, caps, runtimeMinutes, isEpisode), f)
        }
        .sortedWith(
            compareByDescending<RankedSource> { it.score }
                .thenBy { addonSortOrders[it.stream.addonTransportUrl] ?: Int.MAX_VALUE }
        )

    /** Short labels for the TV: "4K", "Dolby Vision", "Atmos", "18.4 GB", "Instant". */
    fun labels(f: SourceFeatures, caps: PlaybackCaps = PlaybackCaps.UNKNOWN): List<String> = buildList {
        when (f.quality) {
            StreamQuality.UHD_4K -> add("4K")
            StreamQuality.FHD_1080P -> add("1080p")
            StreamQuality.HD_720P -> add("720p")
            StreamQuality.SD_480P -> add("SD")
            StreamQuality.CAM -> add("CAM")
            StreamQuality.UNKNOWN -> Unit
        }
        when {
            f.dolbyVision && (caps.dolbyVision || !f.hdr) -> add("Dolby Vision")
            f.hdr10Plus -> add("HDR10+")
            f.hdr -> add("HDR")
        }
        val audio = f.audio.label
        when {
            audio != null && f.channels != null && f.audio != SourceFeatures.Audio.AAC -> add("$audio ${f.channels}")
            audio != null -> add(audio)
            f.channels != null && f.channels != "2.0" -> add(f.channels)
        }
        f.sizeBytes?.let { add(formatSize(it)) }
        if (f.cached || (f.direct && !f.uncachedDebrid)) add("Instant")
        else if (f.torrent && f.seeds != null) add("${f.seeds} seeds")
    }

    fun formatSize(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        return if (gb >= 1.0) {
            if (gb >= 10) "${gb.toInt()} GB" else String.format(java.util.Locale.US, "%.1f GB", gb)
        } else "${(bytes / 1_048_576.0).toInt()} MB"
    }

    /** The addon's name without debrid tags or the second line: "Torrentio", "Comet". */
    fun addonLabel(stream: Stream): String? = stream.name
        ?.lineSequence()?.firstOrNull()
        ?.replace(Regex("""\[[^]]*]"""), "")
        ?.replace("⚡", "")
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it.length <= 24 }

    /** "148 min", "2h 28min", "2h" or "45" as minutes, for judging a source's bitrate. */
    fun runtimeMinutesIn(text: String): Int? {
        val hours = Regex("""(\d+)\s*h""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minutes = Regex("""(\d+)\s*m""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: if (hours == 0) Regex("""^\s*(\d+)\s*$""").find(text)?.groupValues?.get(1)?.toIntOrNull() else null
        return (hours * 60 + (minutes ?: 0)).takeIf { it in 5..600 }
    }

    private val CHANNEL_LAYOUTS = setOf("7.1", "5.1", "2.0")
}
