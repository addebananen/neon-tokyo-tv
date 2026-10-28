package com.lumera.app.livetv

import org.junit.Assert.assertEquals
import org.junit.Test

class SportChannelsTest {

    private fun channel(name: String, epg: String = "") = IptvChannel(name, name, "g", "", "u", epg, 0)
    private val hour = 3_600_000L
    private val now = 100 * hour

    @Test
    fun `hockey is found by name and by a match in the guide, match on now first`() {
        val channels = listOf(
            channel("SE: TV4 Hockey HD"), channel("SE: SVT1", "svt1"), channel("Sky NHL", "nhl"),
            channel("SE: Kanal 5", "k5"), channel("Schlager TV")
        )
        val guide = mapOf(
            "svt1" to listOf(Programme(now + hour, now + 3 * hour, "Hockey-VM: Sverige – Finland")),
            "nhl" to listOf(Programme(now - hour, now + hour, "NHL: Rangers – Bruins")),
            "k5" to listOf(Programme(now - hour, now + hour, "SHL: Frölunda – Färjestad"))
        )
        assertEquals(
            listOf("SE: Kanal 5", "SE: SVT1", "SE: TV4 Hockey HD"),
            SportChannels.find(SportChannels.hockey, channels, guide, now).map { it.name }
        )
    }

    @Test
    fun `football takes the leagues and cups but not hockeyallsvenskan or american football`() {
        val channels = listOf(
            channel("V Sport Premier League"), channel("TV4 Fotboll"), channel("SE: TV6", "tv6"),
            channel("SE: TV10", "tv10"), channel("SE: TV12", "tv12"), channel("SE: Sjuan", "sjuan")
        )
        val guide = mapOf(
            "tv6" to listOf(Programme(now - hour, now - 1, "Fotboll: Nations League"), Programme(now + 2 * hour, now + 4 * hour, "UEFA Europa League: Malmö FF – Roma")),
            "tv10" to listOf(Programme(now - hour, now + hour, "Hockeyallsvenskan: AIK – Modo")),
            "tv12" to listOf(Programme(now - hour, now + hour, "NFL: American Football")),
            "sjuan" to listOf(Programme(now + hour, now + 2 * hour, "Premier League: Arsenal – Liverpool"))
        )
        assertEquals(
            listOf("SE: Sjuan", "SE: TV6", "V Sport Premier League", "TV4 Fotboll"),
            SportChannels.find(SportChannels.football, channels, guide, now).map { it.name }
        )
    }
}
