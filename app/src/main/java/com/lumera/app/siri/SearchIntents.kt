package com.lumera.app.siri

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Searching in the TV's apps, for the Siri Remote's voice and the phone remote. */
object SearchIntents {
    /** [query] searched in [app]: "youtube", "spotify" or "youtube music"; Google TV's search if that fails. */
    fun playIn(context: Context, app: String, query: String): Boolean {
        val q = Uri.encode(query)
        val tries = when (app) {
            "spotify" -> listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$q")).setPackage("com.spotify.tv.android"),
                Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$q"))
            )
            "youtube music" -> listOf(Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=$q")))
            else -> listOf(
                Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube.tv").putExtra(SearchManager.QUERY, query),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$q")).setPackage("com.google.android.youtube.tv")
            )
        }
        return tries.any { start(context, it) } || global(context, query)
    }

    /** Google TV's own search, across every app. */
    fun global(context: Context, query: String): Boolean =
        listOf(
            Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH).putExtra(SearchManager.QUERY, query),
            Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        ).any { start(context, it) }

    private fun start(context: Context, intent: Intent) = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)
}
