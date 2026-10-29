package com.lumera.app.livetv

import java.text.Normalizer

/**
 * The channels people in Sweden watch most, picked out of whatever a provider calls them
 * ("SE: TV4 HD", "SWE | TV4 FHD", "TV4 ᴴᴰ"), one feed each, in roughly the order of how much
 * they are watched.
 */
object PopularChannels {

    private val sweden = listOf(
        "svt1", "tv4", "svt2", "kanal 5", "tv3", "sjuan", "tv6", "svt24", "kanal 9", "tv8",
        "tv10", "kanal 11", "tv12", "svt barn", "kunskapskanalen", "tv4 fakta", "tv4 film",
        "tv4 hits", "tv4 stars", "tv4 guld", "tv4 sport", "tv4 sportkanalen", "tv4 fotboll", "tv4 hockey",
        "v sport 1", "v sport premium", "v sport fotboll", "v sport hockey", "v film premiere", "v film action",
        "c more first", "eurosport 1", "eurosport 2", "discovery channel", "animal planet",
        "national geographic", "history", "cartoon network", "disney channel", "nickelodeon"
    )

    /** Feed and country marks that say nothing about which channel it is. */
    private val noise = Regex("""\b(hd|fhd|uhd|sd|4k|8k|hevc|h265|raw|backup|1080p?|720p?|50fps|se|swe|sweden|sverige|nordic)\b""")
    private val leadingTag = Regex("""^\s*\[?[a-z]{2,4}\]?\s*[:|\-]\s*""")

    // Compiled once: normalize runs for every channel of a list that can be tens of thousands long
    private val marks = Regex("""\p{M}""")
    private val notWord = Regex("""[^a-z0-9 ]""")
    private val letterDigit = Regex("""(\D)(\d)""")
    private val spaces = Regex("""\s+""")
    private val shortAlone = Regex("""^(svt|tv) (\d+)$""")
    private val shortFirst = Regex("""^(svt|tv) (\d+) """)

    fun normalize(name: String): String {
        val plain = Normalizer.normalize(name.lowercase(), Normalizer.Form.NFKD).replace(marks, "")
        return plain
            .replace(leadingTag, "")
            .replace(notWord, " ")
            .replace(noise, " ")
            .replace(letterDigit, "$1 $2")
            .replace(spaces, " ")
            .trim()
            // "svt 1" and "svt1" are the same channel; the list writes the short ones together
            .replace(shortAlone, "$1$2")
            .replace(shortFirst, "$1$2 ")
    }

    fun sweden(channels: List<IptvChannel>): List<IptvChannel> = sweden(channels, channels.map { normalize(it.name) })

    /** The same, with each channel's name already put through [normalize]. */
    fun sweden(channels: List<IptvChannel>, normalized: List<String>): List<IptvChannel> {
        val byName = HashMap<String, IptvChannel>()
        for (i in channels.indices) byName.putIfAbsent(normalized[i], channels[i])
        return sweden.mapNotNull { byName[it] }
    }
}
