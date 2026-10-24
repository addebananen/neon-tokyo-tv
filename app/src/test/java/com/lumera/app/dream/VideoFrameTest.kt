package com.lumera.app.dream

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFrameTest {

    @Test
    fun `a 16 by 9 video fills a 16 by 9 screen exactly`() {
        assertEquals(1920 to 1080, videoFrame(1920, 1080, 16f / 9f))
    }

    @Test
    fun `4 by 3 and cinema widths fill the screen, trimming the edges`() {
        assertEquals(1920 to 1440, videoFrame(1920, 1080, 4f / 3f))
        assertEquals(2520 to 1080, videoFrame(1920, 1080, 21f / 9f))
    }

    @Test
    fun `an upright phone video is shown whole, not cropped to a strip`() {
        assertEquals(608 to 1080, videoFrame(1920, 1080, 9f / 16f))
    }

    @Test
    fun `a very wide video is shown whole`() {
        assertEquals(1920 to 480, videoFrame(1920, 1080, 4f))
    }
}
