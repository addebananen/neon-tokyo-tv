package com.lumera.app.siri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class DownsamplerTest {
    /** [hz] at 48 kHz through the downsampler, fed 20 ms at a time as the decoder hands it over. */
    private fun through(hz: Double, amplitude: Double = 10_000.0): ShortArray {
        val d = Downsampler()
        val out = ArrayList<Short>()
        for (frame in 0 until 50) {
            val pcm = ShortArray(960) { i -> (amplitude * sin(2 * PI * hz * (frame * 960 + i) / 48_000)).toInt().toShort() }
            d.push(pcm).forEach { out.add(it) }
        }
        return out.toShortArray()
    }

    private fun rms(s: ShortArray, skip: Int = 200): Double {
        var e = 0.0
        for (i in skip until s.size) e += s[i].toDouble() * s[i]
        return sqrt(e / (s.size - skip))
    }

    @Test fun oneSecondBecomesSixteenThousandSamples() {
        assertEquals(16_000, through(440.0).size)
    }

    @Test fun speechPassesUnchanged() {
        for (hz in listOf(200.0, 440.0, 1_000.0, 3_000.0, 5_000.0)) {
            val gain = rms(through(hz)) / (10_000 / sqrt(2.0))
            assertTrue("$hz Hz came out at $gain", gain in 0.97..1.03)
        }
    }

    @Test fun nothingFoldsBackFromAbove() {
        // above 8 kHz would alias into the speech band at 16 kHz
        for (hz in listOf(9_500.0, 12_000.0, 16_000.0, 20_000.0)) {
            val gain = rms(through(hz)) / (10_000 / sqrt(2.0))
            assertTrue("$hz Hz leaked through at $gain", gain < 0.01)
        }
    }

    @Test fun fullScaleSoundNeverWraps() {
        val out = through(300.0, 32_000.0)
        assertTrue(out.maxOrNull()!! > 30_000)
        // a wrap shows as a jump from one end of the range to the other
        for (i in 201 until out.size) assertTrue("jump at $i", kotlin.math.abs(out[i] - out[i - 1]) < 8_000)
    }
}
