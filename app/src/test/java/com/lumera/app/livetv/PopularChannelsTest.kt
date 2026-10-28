package com.lumera.app.livetv

import org.junit.Assert.assertEquals
import org.junit.Test

class PopularChannelsTest {

    private fun channel(name: String) = IptvChannel(name, name, "g", "", "u", "", 0)

    @Test
    fun `provider names are matched to the channels they are`() {
        assertEquals("svt1", PopularChannels.normalize("SE: SVT1 HD"))
        assertEquals("svt1", PopularChannels.normalize("SWE | SVT 1 FHD"))
        assertEquals("tv4 sport", PopularChannels.normalize("[SE] TV4 Sport ᴴᴰ"))
        assertEquals("kanal 5", PopularChannels.normalize("Kanal5 HD"))
        assertEquals("eurosport 1", PopularChannels.normalize("SE - Eurosport1 1080p"))
    }

    @Test
    fun `popular in sweden keeps one feed each, most watched first`() {
        val list = listOf("SE: TV4 HD", "SE: SVT1 HD", "SE: SVT1 SD", "Neon One", "SE: Kanal 5 FHD").map(::channel)
        assertEquals(listOf("SE: SVT1 HD", "SE: TV4 HD", "SE: Kanal 5 FHD"), PopularChannels.sweden(list).map { it.name })
    }
}
