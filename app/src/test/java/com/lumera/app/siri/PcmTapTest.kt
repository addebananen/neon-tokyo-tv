package com.lumera.app.siri

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class PcmTapTest {
    private class Sink : ByteArrayOutputStream() {
        var closed = false
        override fun close() { closed = true }
    }

    @Test fun aLateRecognizerHearsEverythingSaid() {
        val tap = PcmTap()
        tap.write(byteArrayOf(1, 2))
        val first = Sink()
        tap.attach(first)
        tap.write(byteArrayOf(3))
        // the first recognizer gives up; a second one takes over and gets it all again
        val second = Sink()
        tap.attach(second)
        tap.write(byteArrayOf(4, 5))
        tap.end()
        assertArrayEquals(byteArrayOf(1, 2, 3), first.toByteArray())
        assertTrue(first.closed)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), second.toByteArray())
        assertTrue(second.closed)
    }

    @Test fun attachingAfterTheEndGetsTheWholeUtteranceAndCloses() {
        val tap = PcmTap()
        tap.write(byteArrayOf(9, 8, 7))
        tap.end()
        val late = Sink()
        tap.attach(late)
        assertArrayEquals(byteArrayOf(9, 8, 7), late.toByteArray())
        assertTrue(late.closed)
    }
}
