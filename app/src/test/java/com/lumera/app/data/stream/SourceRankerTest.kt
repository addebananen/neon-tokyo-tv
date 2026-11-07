package com.lumera.app.data.stream

import com.lumera.app.data.model.StreamQuality
import com.lumera.app.data.model.stremio.Stream
import com.lumera.app.data.model.stremio.StreamBehaviorHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRankerTest {

    private val tcl = PlaybackCaps(dolbyVision = true, hdr10 = true, hdr10Plus = true, av1Hardware = true)
    private val noDv = PlaybackCaps(dolbyVision = false, hdr10 = true, hdr10Plus = false, av1Hardware = true)

    private fun stream(text: String, url: String? = "https://example.invalid/x", infoHash: String? = null, size: Long? = null) =
        Stream(name = "Addon", title = text, url = url, infoHash = infoHash, behaviorHints = size?.let { StreamBehaviorHints(videoSize = it) })

    private fun best(caps: PlaybackCaps, vararg streams: Stream, runtime: Int? = 120) =
        SourceRanker.rank(streams.toList(), caps, runtime).first().stream

    @Test
    fun reads_picture_sound_and_size() {
        val f = SourceRanker.features(stream("Dune.Part.Two.2024.2160p.UHD.BluRay.REMUX.DV.HDR10.HEVC.TrueHD.Atmos.7.1 💾 61.3 GB"))
        assertEquals(StreamQuality.UHD_4K, f.quality)
        assertTrue(f.dolbyVision)
        assertTrue(f.hdr)
        assertEquals(SourceFeatures.Audio.ATMOS, f.audio)
        assertEquals("7.1", f.channels)
        assertTrue(f.hevc)
        assertEquals(listOf("4K", "Dolby Vision", "Atmos 7.1", "61 GB", "Instant"), SourceRanker.labels(f, tcl))
    }

    @Test
    fun ddp_and_dts_hd_are_told_apart() {
        assertEquals(SourceFeatures.Audio.DDP, SourceRanker.features(stream("Show.S01E01.1080p.WEB-DL.DDP5.1.H.264")).audio)
        assertEquals("5.1", SourceRanker.features(stream("Show.S01E01.1080p.WEB-DL.DDP5.1.H.264")).channels)
        assertEquals(SourceFeatures.Audio.DTS_HD, SourceRanker.features(stream("Film.1080p.BluRay.DTS-HD.MA.5.1.x264")).audio)
        assertEquals(SourceFeatures.Audio.DD, SourceRanker.features(stream("Film.720p.HDTV.DD5.1")).audio)
        assertEquals(SourceFeatures.Audio.DTS, SourceRanker.features(stream("Film.1080p.DTS.x264")).audio)
    }

    @Test
    fun dvdrip_is_not_dolby_vision() {
        assertFalse(SourceRanker.features(stream("Film.DVDRip.XviD")).dolbyVision)
    }

    @Test
    fun good_4k_hdr_beats_1080p() {
        val uhd = stream("Film.2160p.WEB-DL.HDR10.DDP5.1.Atmos.HEVC", size = 20L shl 30)
        val fhd = stream("Film.1080p.BluRay.DTS-HD.MA.5.1", size = 14L shl 30)
        assertEquals(uhd, best(tcl, fhd, uhd))
    }

    @Test
    fun dolby_vision_only_drops_on_a_tv_without_it() {
        val dvOnly = stream("Film.2160p.WEB-DL.DV.DDP5.1.Atmos", size = 18L shl 30)
        val hdr = stream("Film.2160p.WEB-DL.HDR10.DDP5.1.Atmos", size = 18L shl 30)
        assertEquals(dvOnly, best(tcl, hdr, dvOnly))
        assertEquals(hdr, best(noDv, dvOnly, hdr))
    }

    @Test
    fun a_huge_remux_loses_to_a_sensible_encode_of_the_same_kind() {
        val remux = stream("Film.2160p.BluRay.REMUX.HDR10.TrueHD.Atmos.7.1", size = 85L shl 30)
        val encode = stream("Film.2160p.BluRay.x265.HDR10.TrueHD.Atmos.7.1", size = 30L shl 30)
        assertEquals(encode, best(tcl, remux, encode))
    }

    @Test
    fun a_starved_4k_file_loses_to_a_full_1080p() {
        val starved = stream("Film.2160p.WEB.x265", size = 2L shl 30)
        val full = stream("Film.1080p.BluRay.x264.DTS", size = 12L shl 30)
        assertEquals(full, best(tcl, starved, full))
    }

    @Test
    fun cached_debrid_beats_uncached_and_dead_torrents() {
        val cached = Stream(name = "[RD+] Torrentio\n1080p", title = "Film.1080p.WEB-DL.DDP5.1\n💾 6 GB", url = "https://example.invalid/a")
        val uncached = Stream(name = "[RD download] Torrentio\n4k", title = "Film.2160p.WEB-DL.HDR.DDP5.1\n💾 18 GB", url = "https://example.invalid/b")
        val dead = Stream(name = "Torrentio\n4k", title = "Film.2160p.WEB-DL.HDR.DDP5.1\n👤 0 💾 18 GB", infoHash = "abc")
        assertEquals(cached, best(tcl, uncached, dead, cached))
    }

    @Test
    fun cam_and_3d_go_last() {
        val cam = stream("Film.2024.HDCAM.x264")
        val threeD = stream("Film.1080p.3D.HSBS.BluRay")
        val sd = stream("Film.480p.DVDRip")
        val ranked = SourceRanker.rank(listOf(cam, threeD, sd), tcl).map { it.stream }
        assertEquals(sd, ranked.first())
        assertEquals(cam, ranked.last())
    }

    @Test
    fun av1_without_hardware_is_avoided() {
        val av1 = stream("Film.2160p.WEB.AV1.HDR10.DDP5.1", size = 10L shl 30)
        val hevc = stream("Film.1080p.WEB.HEVC.DDP5.1", size = 6L shl 30)
        assertEquals(av1, best(tcl, hevc, av1))
        assertEquals(hevc, best(tcl.copy(av1Hardware = false), av1, hevc))
    }

    @Test
    fun ties_keep_the_addon_order() {
        val a = Stream(name = "A", title = "Film.1080p", url = "u1", addonTransportUrl = "a")
        val b = Stream(name = "B", title = "Film.1080p", url = "u2", addonTransportUrl = "b")
        val ranked = SourceRanker.rank(listOf(a, b), tcl, addonSortOrders = mapOf("a" to 2, "b" to 1)).map { it.stream }
        assertEquals(listOf(b, a), ranked)
    }

    @Test
    fun addon_label_drops_debrid_tags() {
        assertEquals("Torrentio", SourceRanker.addonLabel(Stream(name = "[RD+] Torrentio\n4k DV")))
        assertEquals("Comet", SourceRanker.addonLabel(Stream(name = "[RD⚡] Comet")))
    }

    @Test
    fun size_reads_well() {
        assertEquals("1.5 GB", SourceRanker.formatSize(1_610_612_736L))
        assertEquals("18 GB", SourceRanker.formatSize(18L shl 30))
        assertEquals("700 MB", SourceRanker.formatSize(700L shl 20))
    }

    @Test
    fun runtime_reads_common_shapes() {
        assertEquals(148, SourceRanker.runtimeMinutesIn("148 min"))
        assertEquals(148, SourceRanker.runtimeMinutesIn("2h 28min"))
        assertEquals(120, SourceRanker.runtimeMinutesIn("2h"))
        assertEquals(45, SourceRanker.runtimeMinutesIn("45"))
        assertEquals(null, SourceRanker.runtimeMinutesIn("N/A"))
    }
}
