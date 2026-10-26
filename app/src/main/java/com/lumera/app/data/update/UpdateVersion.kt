package com.lumera.app.data.update

/**
 * Whether the release [remote] (a tag such as "v0.2.0-nt.75") is newer than the running
 * [current] version ("0.2.0-nt.68"). Their numbers are compared in order, so an older release
 * is never offered: Android would refuse it anyway, and the TV would show a failed update.
 */
internal fun isNewerVersion(remote: String, current: String): Boolean {
    val a = versionNumbers(remote)
    val b = versionNumbers(current)
    if (a.isEmpty()) return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

private fun versionNumbers(version: String): List<Long> =
    Regex("""\d+""").findAll(version).mapNotNull { it.value.toLongOrNull() }.toList()
