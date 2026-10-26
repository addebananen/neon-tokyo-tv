package com.lumera.app.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailerLauncherTest {
    @Test
    fun theVideoIdComesOutOfIdsAndLinks() {
        assertEquals("dQw4w9WgXcQ", TrailerLauncher.videoId("dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", TrailerLauncher.videoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=3"))
        assertEquals("dQw4w9WgXcQ", TrailerLauncher.videoId("youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", TrailerLauncher.videoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
    }

    @Test
    fun somethingElseIsNoTrailer() {
        assertNull(TrailerLauncher.videoId("not a trailer"))
        assertNull(TrailerLauncher.videoId(""))
    }
}
