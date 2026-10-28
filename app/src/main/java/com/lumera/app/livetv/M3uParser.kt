package com.lumera.app.livetv

import java.io.BufferedReader

/**
 * Reads an M3U playlist line by line, so even a provider's list of tens of thousands of entries
 * never sits in memory as one string. Films and series that an Xtream "m3u_plus" list mixes in
 * are left out: this is for live channels.
 */
object M3uParser {

    class Result(val channels: List<IptvChannel>, val epgUrl: String)

    private val attribute = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(reader: BufferedReader): Result {
        val channels = ArrayList<IptvChannel>()
        var epgUrl = ""
        var pending: Map<String, String>? = null
        var pendingName = ""
        var pendingGroup = ""
        val seen = HashMap<String, Int>()
        while (true) {
            val raw = reader.readLine() ?: break
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val attrs = attributes(line)
                    epgUrl = attrs["url-tvg"] ?: attrs["x-tvg-url"] ?: epgUrl
                    // Some lists name several guides; the first is the one to use
                    epgUrl = epgUrl.split(',').first().trim()
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pending = attributes(line.substringBefore(',', line))
                    pendingName = line.substringAfterLast(',', "").trim()
                    pendingGroup = ""
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> pendingGroup = line.substringAfter(':').trim()
                line.startsWith("#") -> Unit
                else -> {
                    val attrs = pending ?: emptyMap()
                    pending = null
                    if (isVod(line)) continue
                    val name = pendingName.ifBlank { attrs["tvg-name"].orEmpty() }.ifBlank { "Channel ${channels.size + 1}" }
                    val group = attrs["group-title"].orEmpty().ifBlank { pendingGroup }.ifBlank { "Other" }
                    // The same name can be listed twice (an HD and an SD feed under one name)
                    val baseId = attrs["tvg-id"].orEmpty().ifBlank { name } + "|" + group
                    val copy = seen.merge(baseId, 1, Int::plus)!!
                    channels += IptvChannel(
                        id = if (copy == 1) baseId else "$baseId|$copy",
                        name = name,
                        group = group,
                        logo = attrs["tvg-logo"].orEmpty(),
                        url = line,
                        epgId = attrs["tvg-id"].orEmpty(),
                        number = attrs["tvg-chno"]?.toIntOrNull() ?: (channels.size + 1)
                    )
                    pendingName = ""
                    pendingGroup = ""
                }
            }
        }
        return Result(channels, epgUrl)
    }

    private fun attributes(line: String): Map<String, String> =
        attribute.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }

    private fun isVod(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return "/movie/" in path || "/series/" in path ||
            path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".avi")
    }
}
