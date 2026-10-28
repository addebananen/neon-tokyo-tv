package com.lumera.app.overlay

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayTest {
    private val down = KeyEvent.ACTION_DOWN
    private val up = KeyEvent.ACTION_UP
    private val upKey = KeyEvent.KEYCODE_DPAD_UP
    private val downKey = KeyEvent.KEYCODE_DPAD_DOWN
    private val left = KeyEvent.KEYCODE_DPAD_LEFT
    private val right = KeyEvent.KEYCODE_DPAD_RIGHT

    @Test
    fun `holding up and down together fires diagnostics once after two seconds`() {
        val chords = ChordDetector(ChordDetector.DEFAULT)
        // The first button goes to the app, the second belongs to the chord
        assertFalse(chords.onKey(upKey, down, 0, 0))
        assertTrue(chords.onKey(downKey, down, 0, 40))
        assertNull(chords.due(1_000))
        // Repeats while held stay with the chord, so the app doesn't scroll
        assertTrue(chords.onKey(upKey, down, 5, 600))
        assertEquals(ChordAction.Diagnostics, chords.due(2_040)?.action)
        assertNull(chords.due(3_000))
        // The first button's release reaches the app, the second's doesn't
        assertFalse(chords.onKey(upKey, up, 0, 3_100))
        assertTrue(chords.onKey(downKey, up, 0, 3_120))
    }

    @Test
    fun `letting go early fires nothing`() {
        val chords = ChordDetector(ChordDetector.DEFAULT)
        chords.onKey(left, down, 0, 0)
        chords.onKey(right, down, 0, 10)
        chords.onKey(right, up, 0, 900)
        assertNull(chords.due(2_500))
        // The left button's release still reaches the app
        assertFalse(chords.onKey(left, up, 0, 2_600))
    }

    @Test
    fun `left and right is the gaming hud`() {
        val chords = ChordDetector(ChordDetector.DEFAULT)
        chords.onKey(right, down, 0, 0)
        chords.onKey(left, down, 0, 5)
        assertEquals(ChordAction.GamingHud, chords.due(2_005)?.action)
    }

    @Test
    fun `other buttons and a single held button pass straight through`() {
        val chords = ChordDetector(ChordDetector.DEFAULT)
        assertFalse(chords.onKey(KeyEvent.KEYCODE_DPAD_CENTER, down, 0, 0))
        assertFalse(chords.onKey(upKey, down, 0, 0))
        assertFalse(chords.onKey(upKey, down, 30, 3_000))
        assertNull(chords.due(5_000))
        assertFalse(chords.onKey(upKey, up, 0, 5_000))
    }

    @Test
    fun `film frames on the panel's refresh`() {
        assertEquals("5× the film, smooth", SignalProbe.cadence(24f, 120f))
        assertEquals("3:2 pulldown, faint judder", SignalProbe.cadence(23.976f, 60f))
        assertEquals("Matches the film", SignalProbe.cadence(50f, 50f))
        assertEquals("Not a multiple of the film", SignalProbe.cadence(25f, 60f))
    }

    @Test
    fun `frame rates read the way they are known`() {
        assertEquals("23.976", SignalProbe.fpsText(23.9761f))
        assertEquals("59.94", SignalProbe.fpsText(59.94f))
        assertEquals("120", SignalProbe.fpsText(120f))
        assertEquals("57", SignalProbe.fpsText(57.3f))
        assertEquals("4K", SignalProbe.resolutionShort(3840, 2160))
        assertEquals("1080p", SignalProbe.resolutionShort(1920, 1080))
        assertEquals("5.1", SignalProbe.channelsText(6))
    }

    @Test
    fun `surfaceflinger latency gives the frame rate shown`() {
        val rows = (0 until 48).joinToString("\n") { i -> "0 ${1_000_000_000L + i * 41_708_333L} 0" }
        val text = "16666666\n$rows\n0 0 0\n0 9223372036854775807 0"
        assertEquals(23.976f, Dumps.latencyFps(text)!!, 0.01f)
        assertNull(Dumps.latencyFps("16666666\n0 0 0"))
    }

    @Test
    fun `surfaceflinger layer gives size and hdr`() {
        val dump = """
            * Layer 0x1 (SurfaceView[com.netflix.ninja/MainActivity](BLAST)#120)
                  layerStack=0, z=1, dataspace=BT2020_PQ, defaultPixelFormat=RGBA
                  activeBuffer=[3840x2160:3840,ycbcr], queued-frames=0
            * Layer 0x2 (StatusBar#3)
                  activeBuffer=[1920x1080:1920,rgba]
        """.trimIndent()
        val (w, h, hdr) = Dumps.layerFacts(dump, "SurfaceView[com.netflix.ninja/MainActivity](BLAST)#120")
        assertEquals(3840, w)
        assertEquals(2160, h)
        assertEquals("HDR10", hdr)
    }
}
