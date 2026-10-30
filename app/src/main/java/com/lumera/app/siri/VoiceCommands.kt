package com.lumera.app.siri

import java.text.Normalizer

/** What a spoken sentence asks for. */
sealed class VoiceCommand {
    data class OpenApp(val packageName: String, val label: String) : VoiceCommand()
    data class PlayIn(val app: String, val query: String) : VoiceCommand()
    data class Search(val query: String) : VoiceCommand()
}

/**
 * Turns what was said into something to do, in Swedish or English: "öppna YouTube",
 * "spela Akira på YouTube", "play lofi on Spotify", anything else is a search.
 */
object VoiceCommands {
    private val openWords = listOf("öppna", "starta", "kör", "gå till", "open", "launch", "start", "go to")
    private val playWords = listOf("spela upp", "spela", "visa", "play", "show")
    private val onWords = listOf(" på ", " i ", " on ", " in ")

    /** Apps that [PlayIn] can search in directly, by what people call them. */
    val playApps = mapOf(
        "youtube" to "youtube",
        "spotify" to "spotify",
        "youtube music" to "youtube music",
        "yt music" to "youtube music"
    )

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFKC)
            .replace(Regex("[.!?,]"), "")
            .replace(Regex("\\s+"), " ")

    fun parse(text: String, apps: List<Pair<String, String>>): VoiceCommand {
        val said = normalize(text)
        for (w in openWords) {
            if (said.startsWith("$w ")) {
                val name = said.removePrefix("$w ").removePrefix("appen ").removePrefix("the ").removeSuffix(" appen").trim()
                findApp(name, apps)?.let { return VoiceCommand.OpenApp(it.first, it.second) }
            }
        }
        for (w in playWords) {
            if (!said.startsWith("$w ")) continue
            val rest = said.removePrefix("$w ")
            for (on in onWords) {
                val i = rest.lastIndexOf(on)
                if (i <= 0) continue
                val where = rest.substring(i + on.length).trim()
                val app = playApps[where] ?: continue
                return VoiceCommand.PlayIn(app, rest.substring(0, i).trim())
            }
        }
        // a bare app name opens the app too
        findApp(said, apps, exactOnly = true)?.let { return VoiceCommand.OpenApp(it.first, it.second) }
        return VoiceCommand.Search(text.trim())
    }

    /** The app whose label best matches [name]: exact, then contained, then one letter off per five. */
    fun findApp(name: String, apps: List<Pair<String, String>>, exactOnly: Boolean = false): Pair<String, String>? {
        if (name.isBlank()) return null
        val n = squash(name)
        apps.firstOrNull { squash(it.second) == n }?.let { return it }
        if (exactOnly) return null
        apps.filter { squash(it.second).contains(n) || n.contains(squash(it.second)) }
            .minByOrNull { kotlin.math.abs(squash(it.second).length - n.length) }?.let { return it }
        return apps.map { it to distance(squash(it.second), n) }
            .filter { (_, d) -> d <= maxOf(1, n.length / 5) }
            .minByOrNull { it.second }?.first
    }

    private fun squash(s: String) = normalize(s).replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun distance(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }
}
