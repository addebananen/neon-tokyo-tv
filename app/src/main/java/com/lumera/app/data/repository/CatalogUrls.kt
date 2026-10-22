package com.lumera.app.data.repository

/**
 * Builds Stremio catalog URLs. Extras (genre, skip, search) share one path segment
 * joined by '&', so `top` + `genre=Action` + skip 100 becomes `top/genre=Action&skip=100.json`.
 */
object CatalogUrls {

    fun catalog(transportUrl: String, type: String, catalogId: String, extraArgs: String? = null): String {
        val base = "${transportUrl.trimEnd('/')}/catalog/$type/$catalogId"
        return if (extraArgs.isNullOrBlank()) "$base.json" else "$base/$extraArgs.json"
    }

    /** Adds a skip extra to a catalog URL built by [catalog], keeping any extras it already has. */
    fun withSkip(catalogUrl: String, skip: Int): String {
        if (skip <= 0) return catalogUrl
        val withoutJson = catalogUrl.removeSuffix(".json")
        val lastSegment = withoutJson.substringAfterLast('/')
        return if ('=' in lastSegment) "$withoutJson&skip=$skip.json" else "$withoutJson/skip=$skip.json"
    }

    /** `genre=Sci-Fi` style extra with the value URL-encoded the way Stremio addons expect. */
    fun genreExtra(genre: String): String =
        "genre=" + java.net.URLEncoder.encode(genre, "UTF-8").replace("+", "%20")
}
