package com.lumera.app.livetv

import java.text.Normalizer

/**
 * Sport categories put together from what a provider has: channels whose name says the sport
 * ("TV4 Hockey", "V Sport Fotboll") and channels whose guide shows a match of it now or later.
 * Channels with a match on right now come first, then the ones with a match coming up soonest,
 * then the ones that are only named after it.
 */
object SportChannels {

    class Sport(val key: String, val title: String, names: List<String>, programmes: List<String>, avoid: List<String> = emptyList()) {
        internal val names = words(names)
        internal val programmes = words(programmes)
        internal val avoid = words(avoid)
    }

    /** Swedish hockey: SHL, HockeyAllsvenskan, Tre Kronor and the hockey channels that carry them. */
    val hockey = Sport(
        "hockey", "Swedish hockey",
        names = listOf("hockey", "shl", "hockeyallsvenskan"),
        programmes = listOf(
            "shl", "hockeyallsvenskan", "hockeyettan", "sdhl", "tre kronor", "ishockey", "hockey vm",
            "hockey-vm", "champions hockey league", "chl", "j20", "svenska hockeyspelen", "hockey"
        ),
        avoid = listOf("nhl", "ahl", "khl", "field hockey", "landhockey", "bandy")
    )

    /** Football: Premier League, the World Cup and the Euros, Nations League, the European cups, Allsvenskan. */
    val football = Sport(
        "football", "Football",
        names = listOf(
            "fotboll", "football", "premier league", "epl", "champions league", "europa league",
            "conference league", "uefa", "fifa", "allsvenskan", "laliga", "la liga", "serie a", "bundesliga"
        ),
        programmes = listOf(
            "fotboll", "football", "premier league", "world cup", "fotbolls vm", "fotbolls-vm", "vm i fotboll",
            "fotbolls em", "fotbolls-em", "em i fotboll", "euro 2028", "euro 2032", "nations league",
            "europa league", "conference league", "champions league", "allsvenskan", "superettan",
            "damallsvenskan", "svenska cupen", "laliga", "la liga", "serie a", "bundesliga", "fa cup"
        ),
        avoid = listOf("american football", "amerikansk fotboll", "nfl", "futsal")
    )

    val all = listOf(hockey, football)

    fun find(sport: Sport, channels: List<IptvChannel>, guide: Map<String, List<Programme>>, now: Long): List<IptvChannel> {
        val ranked = ArrayList<Pair<Long, IptvChannel>>()
        for (channel in channels) {
            val match = guide[channel.epgId].orEmpty().filter { it.stop > now && sport.matches(it.title) }.minOfOrNull { it.start }
            when {
                match != null -> ranked += maxOf(match, now) to channel
                sport.namedIn(channel.name) -> ranked += Long.MAX_VALUE to channel
            }
        }
        // Stable: within the same start time the provider's own order stays
        return ranked.sortedBy { it.first }.map { it.second }
    }

    internal fun Sport.matches(title: String): Boolean {
        val text = plain(title)
        return programmes.any { it.containsMatchIn(text) } && avoid.none { it.containsMatchIn(text) }
    }

    internal fun Sport.namedIn(name: String): Boolean {
        val text = PopularChannels.normalize(name)
        return names.any { it.containsMatchIn(text) } && avoid.none { it.containsMatchIn(text) }
    }

    private fun plain(text: String) =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKD).replace(Regex("""\p{M}"""), "")

    /** Whole words only, so "Hockeyallsvenskan" is not Allsvenskan and "Schlager" is not SHL. */
    private fun words(list: List<String>) = list.map { Regex("""(?<![a-z0-9])""" + Regex.escape(plain(it)) + """(?![a-z0-9])""") }
}
