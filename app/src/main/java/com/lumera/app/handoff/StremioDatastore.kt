package com.lumera.app.handoff

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** The Stremio account's library ("libraryItem" in Stremio's datastore): read and write items. */
@Singleton
class StremioDatastore @Inject constructor() {
    /** The account signed out elsewhere or the key is no longer good. */
    class AuthException(message: String) : IOException(message)

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = "application/json".toMediaType()

    private suspend fun call(method: String, body: JsonObject): JsonElement? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$API$method")
            .post(body.toString().toRequestBody(json))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val root = runCatching { JsonParser.parseString(text) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
            val error = root?.get("error")
            if (error != null && !error.isJsonNull) {
                val message = if (error.isJsonObject) error.asJsonObject.get("message")?.asString ?: error.toString() else error.toString()
                if (message.contains("session", ignoreCase = true) || message.contains("auth", ignoreCase = true)) {
                    throw AuthException(message)
                }
                throw IOException("Stremio: $message")
            }
            if (!response.isSuccessful) throw IOException("Stremio answered ${response.code}")
            root?.get("result")
        }
    }

    /** These items, or the whole library when [ids] is empty. */
    suspend fun get(authKey: String, ids: List<String>): List<JsonObject> {
        val body = JsonObject().apply {
            addProperty("authKey", authKey)
            addProperty("collection", "libraryItem")
            add("ids", JsonArray().apply { ids.forEach { add(it) } })
            addProperty("all", ids.isEmpty())
        }
        val result = call("datastoreGet", body)
        if (result == null || !result.isJsonArray) return emptyList()
        return result.asJsonArray.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
    }

    suspend fun put(authKey: String, items: List<JsonObject>) {
        if (items.isEmpty()) return
        val body = JsonObject().apply {
            addProperty("authKey", authKey)
            addProperty("collection", "libraryItem")
            add("changes", JsonArray().apply { items.forEach { add(it) } })
        }
        call("datastorePut", body)
    }

    companion object {
        private const val API = "https://api.strem.io/api/"
    }
}
