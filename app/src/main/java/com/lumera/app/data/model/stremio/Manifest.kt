package com.lumera.app.data.model.stremio

import com.google.gson.JsonElement

// Tells us what the addon can do (stream, catalog, meta)
data class Manifest(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val description: String? = null,

    // List<JsonElement> accepts both Cinemeta (Strings) and Torrentio (Objects)
    val resources: List<JsonElement>? = null,

    val types: List<String>? = null,
    val catalogs: List<CatalogManifest>? = null,
    val logo: String? = null,
    val idPrefixes: List<String>? = null
)

data class CatalogManifest(
    val type: String = "",
    val id: String = "",
    val name: String = "",
    val extra: List<CatalogExtra>? = null,
    // Older addons list required extras here instead of flagging them in `extra`
    val extraRequired: List<String>? = null
) {
    /** A catalog that only answers with an extra (search box, mandatory genre) can't be a plain row. */
    fun needsExtraToBrowse(): Boolean =
        extra.orEmpty().any { it.isRequired } || !extraRequired.isNullOrEmpty()

    fun supportsSearch(): Boolean =
        extra.orEmpty().any { it.name == "search" } || extraRequired.orEmpty().contains("search")
}

data class CatalogExtra(
    val name: String = "",
    val isRequired: Boolean = false,
    val options: List<String>? = null
)