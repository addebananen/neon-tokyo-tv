package com.lumera.app.data.trailer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Plays a trailer in the TV's YouTube app, which always can, rather than taking the video out
 * of YouTube's pages, which YouTube keeps changing. Back from YouTube comes back to the title.
 */
object TrailerLauncher {
    // YouTube for TV first, then the phone app, then whatever else on the TV opens YouTube links
    private val YouTubeApps = listOf("com.google.android.youtube.tv", "com.google.android.youtube", "com.google.android.youtube.tvkids")

    /** Opens the trailer [youtubeKey] (an id or a link) in YouTube; false when nothing on the TV can. */
    fun open(context: Context, youtubeKey: String): Boolean {
        val id = videoId(youtubeKey) ?: return false
        val link = Uri.parse("https://www.youtube.com/watch?v=$id")
        val tries = YouTubeApps.map { Intent(Intent.ACTION_VIEW, link).setPackage(it) } + Intent(Intent.ACTION_VIEW, link)
        for (intent in tries) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    /** The video's id from an id, a watch link, a youtu.be link or an embed link. */
    fun videoId(input: String): String? {
        val trimmed = input.trim()
        if (Id.matches(trimmed)) return trimmed
        Regex("""(?:v=|youtu\.be/|/embed/|/shorts/|/live/)([A-Za-z0-9_-]{11})""").find(trimmed)?.let { return it.groupValues[1] }
        return null
    }

    private val Id = Regex("[A-Za-z0-9_-]{11}")
}
