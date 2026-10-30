package com.lumera.app.livetv

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Live TV for the whole app: the household's IPTV service, its channels and its guide, kept on
 * the TV between visits so the list is there at once and only refreshed now and then.
 *
 * The login or link is kept encrypted, never logged, and never leaves the TV except to the
 * provider itself.
 */
object LiveTv {

    sealed class State {
        /** No service added yet. */
        object Empty : State()
        object Loading : State()
        class Ready(val index: ChannelIndex, val refreshing: Boolean = false) : State() {
            val channels get() = index.channels
        }
        class Failed(val message: String, val index: ChannelIndex = ChannelIndex.EMPTY) : State() {
            val channels get() = index.channels
        }
    }

    /** The type Neon Home puts on the request to open a channel in the app. */
    const val OPEN_TYPE = "livetv"

    private const val TAG = "LiveTv"
    private const val SECURE_PREFS = "livetv_secure"
    private const val PREFS = "livetv"
    private const val CHANNELS_FRESH_MS = 24 * 60 * 60_000L
    private const val GUIDE_FRESH_MS = 6 * 60 * 60_000L
    private const val GUIDE_BEHIND_MS = 3 * 60 * 60_000L
    private const val GUIDE_AHEAD_MS = 24 * 60 * 60_000L
    /** Programmes this soon keep their description; later ones only need a title and a time. */
    private const val GUIDE_DETAIL_MS = 3 * 60 * 60_000L

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _guide = MutableStateFlow<Map<String, List<Programme>>>(emptyMap())
    val guide: StateFlow<Map<String, List<Programme>>> = _guide.asStateFlow()

    private val _progress = MutableStateFlow<String?>(null)
    /** What the first load of a service is doing, for the screen to show under its spinner. */
    val progress: StateFlow<String?> = _progress.asStateFlow()

    private val _wanted = MutableStateFlow<String?>(null)
    /** A channel Neon Home asked to play; Live TV opens and plays it, then clears this. */
    val wanted: StateFlow<String?> = _wanted.asStateFlow()

    fun want(channelId: String?) {
        _wanted.value = channelId
    }

    private val gson = Gson()
    private val loadLock = Mutex()
    private var secure: SharedPreferences? = null
    private var prefs: SharedPreferences? = null
    private lateinit var dir: File

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    // Once only: two threads making the encrypted store at the same time can spoil its key
    @Synchronized
    private fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        secure = runCatching {
            EncryptedSharedPreferences.create(
                app, SECURE_PREFS,
                MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            Log.w(TAG, "Secure storage unavailable, keeping the service in private app storage")
            app.getSharedPreferences(SECURE_PREFS + "_fallback", Context.MODE_PRIVATE)
        }
        dir = File(app.filesDir, "livetv").apply { mkdirs() }
    }

    // ---- The service ----

    fun source(context: Context): IptvSource? {
        init(context)
        val p = secure ?: return null
        return when (p.getString("kind", null)) {
            "xtream" -> IptvSource.Xtream(p.getString("server", "").orEmpty(), p.getString("user", "").orEmpty(), p.getString("pass", "").orEmpty())
            "m3u" -> IptvSource.M3u(p.getString("url", "").orEmpty(), p.getString("epg", "").orEmpty())
            else -> null
        }
    }

    /**
     * Checks a service the phone sent and keeps it if it answers. Returns null when it is saved,
     * or what went wrong in words for the phone and the TV to show.
     */
    suspend fun connect(context: Context, source: IptvSource): String? = withContext(Dispatchers.IO) {
        init(context)
        val problem = try {
            when (source) {
                is IptvSource.Xtream -> {
                    val account = get(XtreamApi.apiUrl(source)) { XtreamApi.readAccount(it.reader()) }
                    if (account.ok) null else if (account.status.isNotBlank()) "The account is ${account.status.lowercase()}" else "Wrong username or password"
                }
                is IptvSource.M3u -> get(source.url) { stream ->
                    val head = stream.reader().buffered().use { r -> CharArray(4096).let { buf -> String(buf, 0, r.read(buf).coerceAtLeast(0)) } }
                    if ("#EXTINF" in head || "#EXTM3U" in head) null else "That link is not a playlist"
                }
            }
        } catch (e: Exception) {
            describe(e)
        }
        if (problem != null) return@withContext problem
        secure?.edit()?.clear()?.apply {
            when (source) {
                is IptvSource.Xtream -> putString("kind", "xtream").putString("server", source.server.trim())
                    .putString("user", source.username.trim()).putString("pass", source.password)
                is IptvSource.M3u -> putString("kind", "m3u").putString("url", source.url.trim()).putString("epg", source.epgUrl.trim())
            }
        }?.commit()
        dir.listFiles()?.forEach { it.delete() }
        VodLibrary.forget()
        prefs?.edit()?.remove("channels.at")?.remove("guide.at")?.apply()
        _guide.value = emptyMap()
        // A new service: its own channels are tuned in, not the old ones kept on screen meanwhile
        _state.value = State.Loading
        null
    }

    /** Forgets the service and everything fetched from it; favourites stay for a service added later. */
    suspend fun remove(context: Context) {
        // The keystore and a big service's files are slow, so they are cleared off the main thread
        withContext(NonCancellable + Dispatchers.IO) {
            init(context)
            secure?.edit()?.clear()?.commit()
            dir.listFiles()?.forEach { it.delete() }
            VodLibrary.forget()
            prefs?.edit()?.remove("channels.at")?.remove("guide.at")?.apply()
        }
        _guide.value = emptyMap()
        _state.value = State.Empty
    }

    // ---- Channels and guide ----

    /** Shows what is kept on the TV at once, then fetches anew when it is old or [force] is set. */
    suspend fun load(context: Context, force: Boolean = false) = loadLock.withLock {
        withContext(Dispatchers.IO) {
            init(context)
            val source = source(context)
            if (source == null) {
                _state.value = State.Empty
                return@withContext
            }
            val keptIndex = (state.value as? State.Ready)?.index?.takeIf { it.channels.isNotEmpty() }
                ?: readChannels().takeIf { it.isNotEmpty() }?.let { ChannelIndex.build(it) }
                ?: ChannelIndex.EMPTY
            val kept = keptIndex.channels
            if (_guide.value.isEmpty()) readGuide()?.let { _guide.value = it }
            val now = System.currentTimeMillis()
            val channelsAt = prefs?.getLong("channels.at", 0L) ?: 0L
            val stale = force || kept.isEmpty() || now - channelsAt > CHANNELS_FRESH_MS
            var index = keptIndex
            if (stale) {
                _state.value = if (kept.isEmpty()) State.Loading else State.Ready(keptIndex, refreshing = true)
                try {
                    val started = System.currentTimeMillis()
                    val fetched = fetchChannels(source)
                    if (fetched.channels.isEmpty()) {
                        _state.value = State.Failed("The service sent no live channels", keptIndex)
                        return@withContext
                    }
                    _progress.value = "Sorting ${count(fetched.channels.size)} channels"
                    index = ChannelIndex.build(fetched.channels)
                    writeChannels(fetched.channels)
                    prefs?.edit()?.putLong("channels.at", now)?.putString("guide.url", fetched.guideUrl)?.apply()
                    Log.i(TAG, "${fetched.channels.size} channels in ${index.groups.size} groups, ready in ${System.currentTimeMillis() - started} ms")
                } catch (e: Throwable) {
                    // An OutOfMemoryError too: a list too big for the TV must not take the app down
                    Log.w(TAG, "Channels could not be fetched: ${e.javaClass.simpleName}")
                    _state.value = if (kept.isNotEmpty()) State.Ready(keptIndex)
                    else State.Failed(if (e is Exception) describe(e) else "The channel list is too big for this TV")
                    return@withContext
                } finally {
                    _progress.value = null
                }
            }
            val channels = index.channels
            _state.value = State.Ready(index)
            val guideAt = prefs?.getLong("guide.at", 0L) ?: 0L
            if (stale || _guide.value.isEmpty() || now - guideAt > GUIDE_FRESH_MS) refreshGuide(source, channels)
        }
    }

    private class Fetched(val channels: List<IptvChannel>, val guideUrl: String)

    private fun fetchChannels(source: IptvSource): Fetched = when (source) {
        is IptvSource.Xtream -> {
            _progress.value = "Signing in"
            val account = get(XtreamApi.apiUrl(source)) { XtreamApi.readAccount(it.reader()) }
            if (!account.ok) throw IOException("login")
            val format = XtreamApi.pickFormat(account)
            _progress.value = "Reading the channel groups"
            val categories = get(XtreamApi.apiUrl(source, "get_live_categories")) { XtreamApi.readCategories(it.reader().buffered()) }
            _progress.value = "Fetching the channels"
            val streams = get(XtreamApi.apiUrl(source, "get_live_streams")) { stream ->
                XtreamApi.readStreams(stream.reader().buffered(), categories, onProgress = { _progress.value = "${count(it)} channels so far" }) {
                    XtreamApi.streamUrl(source, it, format)
                }
            }
            // In the order the provider lists its groups, and by number within each
            val order = categories.values.withIndex().associate { it.value to it.index }
            Fetched(streams.sortedWith(compareBy<IptvChannel>({ order[it.group] ?: Int.MAX_VALUE }, { it.number })), XtreamApi.guideUrl(source))
        }
        is IptvSource.M3u -> {
            _progress.value = "Fetching the playlist"
            val result = get(source.url) { M3uParser.parse(it.reader().buffered()) }
            Fetched(result.channels, source.epgUrl.ifBlank { result.epgUrl })
        }
    }

    private fun refreshGuide(source: IptvSource, channels: List<IptvChannel>) {
        val url = prefs?.getString("guide.url", null)?.takeIf { it.isNotBlank() }
            ?: (source as? IptvSource.Xtream)?.let { XtreamApi.guideUrl(it) }
            ?: return
        val wanted = channels.mapNotNullTo(HashSet()) { it.epgId.takeIf(String::isNotBlank) }
        if (wanted.isEmpty()) return
        val now = System.currentTimeMillis()
        try {
            val started = System.currentTimeMillis()
            val guide = get(url) { stream ->
                XmlTvParser.parse(unzipped(stream), wanted, now - GUIDE_BEHIND_MS, now + GUIDE_AHEAD_MS, detailUntil = now + GUIDE_DETAIL_MS)
            }
            if (guide.isEmpty()) return
            _guide.value = guide
            writeGuide(guide)
            prefs?.edit()?.putLong("guide.at", now)?.apply()
            Log.i(TAG, "Guide for ${guide.size} channels, ${guide.values.sumOf { it.size }} programmes, in ${System.currentTimeMillis() - started} ms")
        } catch (e: Throwable) {
            // A guide too big for the TV leaves the channels without one rather than closing the app
            Log.w(TAG, "Guide could not be fetched: ${e.javaClass.simpleName}")
        }
    }

    // ---- Each profile's own ----

    fun favourites(context: Context, profileId: Int): List<String> {
        init(context)
        return prefs?.getString("fav.$profileId", null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
    }

    fun setFavourite(context: Context, profileId: Int, channelId: String, on: Boolean) {
        val now = favourites(context, profileId).filter { it != channelId }
        val next = if (on) now + channelId else now
        prefs?.edit()?.putString("fav.$profileId", next.joinToString("\n"))?.apply()
    }

    /** A profile's own channel lists ("Sport", "Kids"), by name in the order they were made. */
    fun lists(context: Context, profileId: Int): LinkedHashMap<String, List<String>> {
        init(context)
        val json = prefs?.getString("lists.$profileId", null) ?: return LinkedHashMap()
        return runCatching {
            gson.fromJson<LinkedHashMap<String, List<String>>>(json, object : TypeToken<LinkedHashMap<String, List<String>>>() {}.type)
        }.getOrNull() ?: LinkedHashMap()
    }

    fun setLists(context: Context, profileId: Int, lists: Map<String, List<String>>) {
        init(context)
        prefs?.edit()?.putString("lists.$profileId", gson.toJson(LinkedHashMap(lists)))?.apply()
    }

    fun hiddenGroups(context: Context, profileId: Int): Set<String> {
        init(context)
        return prefs?.getString("hidden.$profileId", null)?.split('\n')?.filterTo(HashSet()) { it.isNotBlank() } ?: emptySet()
    }

    fun setGroupHidden(context: Context, profileId: Int, group: String, hidden: Boolean) {
        val now = hiddenGroups(context, profileId)
        val next = if (hidden) now + group else now - group
        prefs?.edit()?.putString("hidden.$profileId", next.joinToString("\n"))?.apply()
    }

    fun lastChannel(context: Context, profileId: Int): String? {
        init(context)
        return prefs?.getString("last.$profileId", null)
    }

    fun setLastChannel(context: Context, profileId: Int, channelId: String) {
        init(context)
        prefs?.edit()?.putString("last.$profileId", channelId)?.apply()
    }

    /**
     * A profile's favourite channels as kept on the TV, with what is on each now, for the row on
     * Neon Home; nothing is fetched for it.
     */
    suspend fun homeFavourites(context: Context, profileId: Int, max: Int): List<Pair<IptvChannel, Programme?>> = withContext(Dispatchers.IO) {
        init(context)
        if (source(context) == null) return@withContext emptyList()
        val favourites = favourites(context, profileId)
        if (favourites.isEmpty()) return@withContext emptyList()
        val byId = (state.value as? State.Ready)?.index?.byId?.takeIf { it.isNotEmpty() } ?: readChannels().associateBy { it.id }
        val guide = _guide.value.ifEmpty { readGuide().orEmpty() }
        val now = System.currentTimeMillis()
        favourites.mapNotNull { byId[it] }.take(max).map { it to guide[it.epgId]?.nowNext(now)?.now }
    }

    // ---- Plumbing ----

    private fun <T> get(url: String, read: (InputStream) -> T): T {
        val request = Request.Builder().url(url).header("User-Agent", "NeonTokyoTV").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("empty")
            return body.byteStream().use(read)
        }
    }

    /** Guides often come gzipped whatever their name says; tell by the first two bytes. */
    private fun unzipped(stream: InputStream): InputStream {
        val buffered = BufferedInputStream(stream, 64 * 1024)
        buffered.mark(2)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()
        return if (first == 0x1f && second == 0x8b) GZIPInputStream(buffered, 64 * 1024) else buffered
    }

    /** 12 400, as the TV shows a big number. */
    private fun count(n: Int) = String.format(java.util.Locale.US, "%,d", n).replace(',', ' ')

    private fun describe(e: Exception): String = when {
        e.message == "login" -> "Wrong username or password"
        e is java.net.UnknownHostException -> "The server could not be found"
        e is java.net.SocketTimeoutException -> "The server did not answer in time"
        e is java.net.ConnectException -> "The server refused the connection"
        e is IOException && e.message?.startsWith("HTTP ") == true -> "The server answered ${e.message}"
        e is IllegalArgumentException -> "That address is not valid"
        else -> "Could not reach the service"
    }

    private fun readChannels(): List<IptvChannel> = runCatching {
        File(dir, "channels.json").takeIf { it.exists() }?.bufferedReader()?.use {
            gson.fromJson<List<IptvChannel>>(it, object : TypeToken<List<IptvChannel>>() {}.type)
        }
    }.getOrNull().orEmpty()

    private fun writeChannels(channels: List<IptvChannel>) {
        runCatching { File(dir, "channels.json").bufferedWriter().use { gson.toJson(channels, it) } }
    }

    private fun readGuide(): Map<String, List<Programme>>? = runCatching {
        File(dir, "guide.json").takeIf { it.exists() }?.bufferedReader()?.use {
            gson.fromJson<Map<String, List<Programme>>>(it, object : TypeToken<Map<String, List<Programme>>>() {}.type)
        }
    }.getOrNull()

    private fun writeGuide(guide: Map<String, List<Programme>>) {
        runCatching { File(dir, "guide.json").bufferedWriter().use { gson.toJson(guide, it) } }
    }
}
