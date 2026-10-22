package com.lumera.app.data.profile

import com.google.gson.Gson
import com.lumera.app.data.model.AddonEntity
import com.lumera.app.data.model.CatalogConfigEntity
import com.lumera.app.data.model.HubRowEntity
import com.lumera.app.data.model.HubRowItemEntity
import com.lumera.app.data.model.stremio.CatalogManifest
import com.lumera.app.data.model.stremio.Manifest
import com.lumera.app.data.repository.CatalogUrls
import com.lumera.app.domain.HubShape

/**
 * The catalog a new Neon Tokyo profile starts with: Cinemeta for films and series,
 * Streaming Catalogs for what is on each service (set up for Sweden), and Anime Catalogs.
 * All three are metadata-only addons. Rows, genre cards and service cards are laid out
 * here so the home screen is complete on first launch and stays current on its own,
 * because every row is a live addon catalog.
 */
object NeonTokyoStarterPack {

    const val CINEMETA_URL = "https://v3-cinemeta.strem.io"
    private const val STREAMING_HOST = "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club"
    const val ANIME_URL = "https://1fe84bc728af-stremio-anime-catalogs.baby-beamup.club/" +
        "%7B%22cinemeta%22%3A%22on%22%2C%22myanimelist_top-all-time%22%3A%22on%22%2C" +
        "%22myanimelist_top-airing%22%3A%22on%22%2C%22anilist_trending-now%22%3A%22on%22%2C" +
        "%22anilist_top-anime%22%3A%22on%22%2C%22kitsu_most-popular%22%3A%22on%22%7D"

    /** Streaming Catalogs provider codes for services sold in Sweden, in card order. */
    val SERVICES = listOf(
        "nfx" to "Netflix",
        "hbm" to "HBO Max",
        "dnp" to "Disney+",
        "amp" to "Prime Video",
        "atp" to "Apple TV+",
        "sst" to "SkyShowtime",
        "pmp" to "Paramount+",
        "cru" to "Crunchyroll"
    )

    const val COUNTRY = "SE"

    /** Cinemeta genre names, which double as the addon's genre filter values. */
    val GENRES = listOf(
        "Action", "Comedy", "Sci-Fi", "Horror", "Thriller", "Drama", "Animation",
        "Crime", "Adventure", "Fantasy", "Romance", "Mystery", "Family", "Documentary"
    )

    /**
     * Streaming Catalogs reads its settings from a base64 path segment:
     * providers:rpdbKey:country:installedAt:top10Global:top10Country:top10CountryCode
     */
    val STREAMING_URL: String by lazy {
        val config = listOf(
            SERVICES.joinToString(",") { it.first }, "", COUNTRY, "1792700000000", "0", "1", COUNTRY
        ).joinToString(":")
        val encoded = java.util.Base64.getEncoder().encodeToString(config.toByteArray(Charsets.US_ASCII))
        "$STREAMING_HOST/${java.net.URLEncoder.encode(encoded, "UTF-8")}"
    }

    val MANIFEST_URLS: List<String> get() = listOf(CINEMETA_URL, STREAMING_URL, ANIME_URL).map { "$it/manifest.json" }

    private const val TOP10 = "netflix-top10-$COUNTRY"
    private const val COMBO = "combo:"

    /** Hub cards can point at a movie and a series catalog at once; the grid shows both. */
    fun comboId(vararg configIds: String) = COMBO + configIds.joinToString("|")
    fun parseCombo(categoryId: String): List<String>? =
        if (categoryId.startsWith(COMBO)) categoryId.removePrefix(COMBO).split('|').filter { it.isNotBlank() } else null

    fun configId(transportUrl: String, type: String, catalogId: String, extraArgs: String? = null) =
        "$transportUrl/$type/$catalogId" + (extraArgs?.let { "#$it" } ?: "")

    fun build(manifests: Map<String, Manifest?>, gson: Gson = Gson()): ProfileRuntimeSnapshot {
        val cinemeta = manifests[CINEMETA_URL]
        val streaming = manifests[STREAMING_URL]
        val anime = manifests[ANIME_URL]

        val cinemetaCatalogs = cinemeta?.catalogs?.filter { it.type == "movie" || it.type == "series" }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(
                CatalogManifest(type = "movie", id = "top", name = "Popular"),
                CatalogManifest(type = "series", id = "top", name = "Popular"),
                CatalogManifest(type = "movie", id = "imdbRating", name = "Featured"),
                CatalogManifest(type = "series", id = "imdbRating", name = "Featured")
            )
        // Its manifest is fixed by the config above, so the catalogs are known even offline
        val streamingCatalogs = streaming?.catalogs?.takeIf { it.isNotEmpty() }
            ?: SERVICES.flatMap { (code, name) ->
                listOf(CatalogManifest(type = "movie", id = code, name = name), CatalogManifest(type = "series", id = code, name = name))
            } + listOf(
                CatalogManifest(type = "movie", id = TOP10, name = "Netflix Top 10 Movies ($COUNTRY)"),
                CatalogManifest(type = "series", id = TOP10, name = "Netflix Top 10 Shows ($COUNTRY)")
            )
        val animeCatalogs = anime?.catalogs.orEmpty().filter { !it.needsExtraToBrowse() }

        val addons = buildList {
            add(addon(CINEMETA_URL, cinemeta, "org.stremio.cinemeta", "Cinemeta", cinemetaCatalogs, gson,
                types = listOf("movie", "series"), idPrefixes = listOf("tt"), supportsMeta = true))
            add(addon(STREAMING_URL, streaming, "pw.ers.netflix-catalog", "Streaming Catalogs", streamingCatalogs, gson,
                types = listOf("movie", "series"), idPrefixes = listOf("tt"), supportsMeta = false))
            if (anime != null) {
                add(addon(ANIME_URL, anime, anime.id, anime.name, anime.catalogs.orEmpty(), gson,
                    types = anime.types.orEmpty(), idPrefixes = anime.idPrefixes.orEmpty(),
                    supportsMeta = anime.hasResource("meta")))
            }
        }

        val configs = linkedMapOf<String, CatalogConfigEntity>()
        fun config(
            transportUrl: String, addonName: String, catalog: CatalogManifest, title: String? = null,
            extraArgs: String? = null, home: Int? = null, movies: Int? = null, series: Int? = null
        ): String {
            val id = configId(transportUrl, catalog.type, catalog.id, extraArgs)
            configs[id] = CatalogConfigEntity(
                uniqueId = id, transportUrl = transportUrl, addonName = addonName,
                catalogType = catalog.type, catalogId = catalog.id, catalogName = catalog.name,
                customTitle = title, extraArgs = extraArgs,
                showInHome = home != null, showInMovies = movies != null, showInSeries = series != null,
                homeOrder = home ?: 999, moviesOrder = movies ?: 999, seriesOrder = series ?: 999
            )
            return id
        }

        val hubRows = mutableListOf<HubRowEntity>()
        val hubItems = mutableListOf<HubRowItemEntity>()
        fun hub(id: String, title: String, home: Int? = null, movies: Int? = null, series: Int? = null, items: List<Pair<String, String>>) {
            if (items.isEmpty()) return
            hubRows += HubRowEntity(
                id = id, title = title, shape = HubShape.HORIZONTAL.name,
                showInHome = home != null, showInMovies = movies != null, showInSeries = series != null,
                homeOrder = home ?: 999, moviesOrder = movies ?: 999, seriesOrder = series ?: 999,
                createdAt = 0L
            )
            items.forEachIndexed { index, (configId, itemTitle) ->
                hubItems += HubRowItemEntity(hubRowId = id, configUniqueId = configId, title = itemTitle, itemOrder = index)
            }
        }

        fun cine(type: String, id: String) = cinemetaCatalogs.firstOrNull { it.type == type && it.id == id }
        fun stream(type: String, id: String) = streamingCatalogs.firstOrNull { it.type == type && it.id == id }

        // --- Cinemeta rows -------------------------------------------------------------
        cine("movie", "top")?.let { config(CINEMETA_URL, "Cinemeta", it, "Popular movies", home = 30, movies = 20) }
        cine("series", "top")?.let { config(CINEMETA_URL, "Cinemeta", it, "Popular series", home = 40, series = 20) }
        cine("movie", "imdbRating")?.let { config(CINEMETA_URL, "Cinemeta", it, "Highly rated movies", home = 80, movies = 60) }
        cine("series", "imdbRating")?.let { config(CINEMETA_URL, "Cinemeta", it, "Highly rated series", home = 90, series = 60) }
        // Remaining Cinemeta catalogs stay available in the dashboard editor
        cinemetaCatalogs.filter { !it.needsExtraToBrowse() }.forEach { catalog ->
            val id = configId(CINEMETA_URL, catalog.type, catalog.id)
            if (id !in configs) config(CINEMETA_URL, "Cinemeta", catalog)
        }

        // --- What's hot in Sweden ------------------------------------------------------
        stream("movie", TOP10)?.let { config(STREAMING_URL, "Streaming Catalogs", it, "Top 10 films on Netflix in Sweden", home = 10, movies = 10) }
        stream("series", TOP10)?.let { config(STREAMING_URL, "Streaming Catalogs", it, "Top 10 series on Netflix in Sweden", home = 20, series = 10) }

        // --- Streaming services: one card per service, rows on the Films and Series tabs ---
        val serviceMovieIds = mutableListOf<Pair<String, String>>()
        val serviceSeriesIds = mutableListOf<Pair<String, String>>()
        val serviceHomeIds = mutableListOf<Pair<String, String>>()
        SERVICES.forEachIndexed { index, (code, name) ->
            val movieId = stream("movie", code)?.let { config(STREAMING_URL, "Streaming Catalogs", it, "$name films", movies = 100 + index) }
            val seriesId = stream("series", code)?.let { config(STREAMING_URL, "Streaming Catalogs", it, "$name series", series = 100 + index) }
            movieId?.let { serviceMovieIds += it to name }
            seriesId?.let { serviceSeriesIds += it to name }
            listOfNotNull(seriesId, movieId).takeIf { it.isNotEmpty() }?.let { serviceHomeIds += comboId(*it.toTypedArray()) to name }
        }
        hub("nt_services_home", "Streaming services", home = 5, items = serviceHomeIds)
        hub("nt_services_movies", "Streaming services", movies = 5, items = serviceMovieIds)
        hub("nt_services_series", "Streaming services", series = 5, items = serviceSeriesIds)

        // --- Genres: Cinemeta's popular list filtered by genre ---------------------------
        val genreMovieIds = mutableListOf<Pair<String, String>>()
        val genreSeriesIds = mutableListOf<Pair<String, String>>()
        val genreHomeIds = mutableListOf<Pair<String, String>>()
        GENRES.forEach { genre ->
            val extra = CatalogUrls.genreExtra(genre)
            val movieId = cine("movie", "top")?.let { config(CINEMETA_URL, "Cinemeta", it, "$genre films", extraArgs = extra) }
            val seriesId = cine("series", "top")?.let { config(CINEMETA_URL, "Cinemeta", it, "$genre series", extraArgs = extra) }
            movieId?.let { genreMovieIds += it to genre }
            seriesId?.let { genreSeriesIds += it to genre }
            listOfNotNull(movieId, seriesId).takeIf { it.isNotEmpty() }?.let { genreHomeIds += comboId(*it.toTypedArray()) to genre }
        }
        hub("nt_genres_home", "Genres", home = 50, items = genreHomeIds)
        hub("nt_genres_movies", "Genres", movies = 40, items = genreMovieIds)
        hub("nt_genres_series", "Genres", series = 40, items = genreSeriesIds)

        // --- Anime: every browsable catalog from the Anime Catalogs addon ----------------
        animeCatalogs.forEachIndexed { index, catalog ->
            config(ANIME_URL, anime?.name ?: "Anime Catalogs", catalog, animeTitle(catalog), home = 60 + index,
                series = if (catalog.type == "series") 70 + index else null,
                movies = if (catalog.type == "movie") 70 + index else null)
        }

        return ProfileRuntimeSnapshot(
            addons = addons,
            catalogConfigs = configs.values.toList(),
            hubRows = hubRows,
            hubRowItems = hubItems,
            watchHistory = emptyList()
        )
    }

    /** One line under a folder's name on the home screen. */
    fun hubBlurb(title: String): String {
        val service = SERVICES.firstOrNull { it.second.equals(title, ignoreCase = true) }?.second
        if (service != null) return "Everything on $service in Sweden, films and series together. Updates by itself."
        val genre = GENRES.firstOrNull { it.equals(title, ignoreCase = true) }
        if (genre != null) return "Popular $genre films and series, kept up to date from Cinemeta."
        return "Open the folder to browse everything in it."
    }

    private val ANIME_TITLES = listOf(
        "top-airing" to "Airing now: top anime",
        "trending" to "Trending anime",
        "top-all-time" to "Best anime of all time",
        "top-anime" to "Top rated anime",
        "most-popular" to "Most popular anime"
    )

    /** Friendly row titles for the Anime Catalogs lists, falling back to the addon's own name. */
    fun animeTitle(catalog: CatalogManifest): String {
        val id = catalog.id.lowercase()
        ANIME_TITLES.firstOrNull { (key, _) -> key in id }?.let { return it.second }
        val name = catalog.name.trim().ifBlank { catalog.id }
        return if (name.contains("anime", ignoreCase = true)) name else "$name anime"
    }

    private fun addon(
        transportUrl: String, manifest: Manifest?, fallbackId: String, fallbackName: String,
        catalogs: List<CatalogManifest>, gson: Gson, types: List<String>, idPrefixes: List<String>, supportsMeta: Boolean
    ) = AddonEntity(
        transportUrl = transportUrl,
        id = manifest?.id?.ifBlank { null } ?: fallbackId,
        name = manifest?.name?.ifBlank { null } ?: fallbackName,
        version = manifest?.version?.ifBlank { null } ?: "1.0.0",
        description = manifest?.description,
        iconUrl = manifest?.logo,
        isTrusted = false,
        isEnabled = true,
        nickname = null,
        catalogsJson = gson.toJson(catalogs),
        supportsMeta = manifest?.hasResource("meta") ?: supportsMeta,
        supportsStream = manifest?.hasResource("stream") ?: false,
        typesJson = gson.toJson(manifest?.types ?: types),
        idPrefixesJson = gson.toJson(manifest?.idPrefixes ?: idPrefixes)
    )

    private fun Manifest.hasResource(name: String): Boolean = resources.orEmpty().any { element ->
        when {
            element.isJsonPrimitive -> element.asString == name
            element.isJsonObject -> element.asJsonObject.get("name")?.asString == name
            else -> false
        }
    }
}
