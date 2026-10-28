package com.lumera.app.livetv

/**
 * Where the channels come from: the household's own IPTV service, either as a playlist link or
 * as an Xtream login. It is the same for every profile, like the addons; what each profile keeps
 * for itself is its favourites and the groups it hides.
 */
sealed class IptvSource {
    data class M3u(val url: String, val epgUrl: String = "") : IptvSource()
    data class Xtream(val server: String, val username: String, val password: String) : IptvSource() {
        /** The server as typed on a phone, made into a base address: a scheme, no slash at the end. */
        val base: String
            get() {
                val trimmed = server.trim().trimEnd('/')
                return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) trimmed else "http://$trimmed"
            }
    }
}

/** One channel. [id] stays the same across reloads of the same service, so favourites survive them. */
data class IptvChannel(
    val id: String,
    val name: String,
    val group: String,
    val logo: String,
    val url: String,
    val epgId: String,
    val number: Int
)

/** One programme in the guide, times in epoch milliseconds. */
data class Programme(val start: Long, val stop: Long, val title: String, val description: String = "") {
    fun isOn(now: Long) = now in start until stop
    fun progress(now: Long) = if (stop > start) ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f) else 0f
}

/** What is on now on a channel, and what comes after it. */
data class NowNext(val now: Programme?, val next: Programme?)

fun List<Programme>.nowNext(time: Long): NowNext {
    val index = indexOfFirst { it.isOn(time) }
    return if (index >= 0) NowNext(this[index], getOrNull(index + 1))
    else NowNext(null, firstOrNull { it.start > time })
}
