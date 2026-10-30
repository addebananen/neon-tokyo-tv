package com.lumera.app.livetv

/**
 * Everything the Live TV screen looks up in a channel list, worked out once off the main thread.
 * A big provider sends tens of thousands of channels, and matching their names on the main
 * thread froze the TV on "Tuning in the channels" for as long as it took.
 */
class ChannelIndex private constructor(
    val channels: List<IptvChannel>,
    val byId: Map<String, IptvChannel>,
    val groups: List<String>,
    val counts: Map<String, Int>,
    val popular: List<IptvChannel>,
    /** Each channel's name as matched and as written, lower case, for search; by position. */
    val searchable: List<String>,
    /** Each channel's name through [PopularChannels.normalize], by position. */
    val normalized: List<String>
) {
    /** The sport groups as the guide has them at [now]; slow for a big list, so off the main thread too. */
    fun sports(guide: Map<String, List<Programme>>, now: Long): Map<String, List<IptvChannel>> =
        SportChannels.all.associate { it.key to SportChannels.find(it, channels, normalized, guide, now) }

    companion object {
        val EMPTY = build(emptyList())

        fun build(channels: List<IptvChannel>): ChannelIndex {
            val normalized = channels.map { PopularChannels.normalize(it.name) }
            return ChannelIndex(
                channels = channels,
                byId = channels.associateBy { it.id },
                groups = channels.mapTo(LinkedHashSet()) { it.group }.toList(),
                counts = channels.groupingBy { it.group }.eachCount(),
                popular = PopularChannels.sweden(channels, normalized),
                searchable = channels.mapIndexed { i, it -> normalized[i] + " " + it.name.lowercase() },
                normalized = normalized
            )
        }
    }
}
