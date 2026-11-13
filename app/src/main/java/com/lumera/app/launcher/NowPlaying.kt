package com.lumera.app.launcher

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.graphics.Bitmap
import android.os.HandlerThread
import android.os.Handler
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A song or show playing in some app on the TV. [position] was at [positionAt] (uptime) and runs
 * on at [speed] while it plays; [duration] is 0 when the app does not say.
 */
data class Track(
    val title: String,
    val artist: String?,
    val packageName: String,
    val playing: Boolean,
    val album: String? = null,
    val art: android.graphics.Bitmap? = null,
    val artUri: String? = null,
    val duration: Long = 0L,
    val position: Long = -1L,
    val positionAt: Long = 0L,
    val speed: Float = 1f
) {
    /** Where it is now, worked out from the last position the app gave. */
    fun positionNow(uptime: Long = android.os.SystemClock.elapsedRealtime()): Long {
        if (position < 0) return -1L
        val run = if (playing) ((uptime - positionAt) * speed).toLong() else 0L
        return (position + run).let { if (duration > 0) it.coerceIn(0L, duration) else it }
    }
}

/**
 * Android only tells an app what other apps play once it is a notification listener, the
 * switch Google TV keeps for the system. It is flipped over ADB (or by Neon Tokyo itself
 * after WRITE_SECURE_SETTINGS was granted); this service then does nothing but exist.
 */
class NowPlayingListener : NotificationListenerService()

/**
 * What plays now, for the music widget: the first app playing, or else the last one paused.
 * It only listens while [start]ed, which the home screen does while it shows.
 *
 * Every widget and card that shows the music holds one of these, and they all share one
 * [NowPlayingHub]: one set of media callbacks on a thread of its own, so a music app telling the
 * TV about its playback many times a second never reaches the main thread, the home screen.
 */
class NowPlaying(context: Context) {
    private val hub = NowPlayingHub.get(context)
    private var holding = false
    val track: StateFlow<Track?> get() = hub.track

    /** Starts listening, or looks again whether the switch was flipped since. */
    fun start() {
        if (!holding) {
            holding = true
            hub.acquire()
        } else {
            hub.retry()
        }
    }

    fun stop() {
        if (!holding) return
        holding = false
        hub.release()
    }

    /** Plays or pauses what is shown, as its own app's play button would. */
    fun toggle() {
        hub.toggle()
    }

    fun next() {
        hub.next()
    }

    fun previous() {
        hub.previous()
    }

    companion object {
        /** Opens the TV's page for this switch, where the TV has one; false where it has none. */
        fun openSettings(context: Context): Boolean {
            val me = ComponentName(context, NowPlayingListener::class.java)
            val tries = listOfNotNull(
                if (android.os.Build.VERSION.SDK_INT >= 30) android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, me.flattenToString()) else null,
                android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            )
            for (intent in tries) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) == null) continue
                if (runCatching { context.startActivity(intent) }.isSuccess) return true
            }
            return false
        }

        fun allowed(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        /** Turns the listener on without ADB, once WRITE_SECURE_SETTINGS was granted. */
        fun allow(context: Context): Boolean = runCatching {
            val me = ComponentName(context, NowPlayingListener::class.java).flattenToString()
            val resolver = context.contentResolver
            val list = Settings.Secure.getString(resolver, "enabled_notification_listeners").orEmpty()
                .split(':').filter { it.isNotBlank() && !it.equals(me, ignoreCase = true) }
            Settings.Secure.putString(resolver, "enabled_notification_listeners", (list + me).joinToString(":"))
        }.isSuccess
    }
}

/**
 * The one listener behind every [NowPlaying]. Music apps like Spotify report their playback state
 * often and send their cover with every metadata change, a bitmap of a megabyte or more. So the
 * callbacks run on their own thread, what each app last said is kept from the callback itself
 * (asking a controller again is a call across processes that copies the cover every time), the
 * cover is shrunk once per track to the size it is shown at, and a new [Track] goes out only when
 * something you can see changed. A position that only moves on as expected changes nothing.
 */
internal class NowPlayingHub private constructor(private val context: Context) {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, NowPlayingListener::class.java)
    private val thread = HandlerThread("NowPlaying", android.os.Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
    private val handler = Handler(thread.looper)
    private val _track = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = _track.asStateFlow()

    private var users = 0
    private var listening = false

    /** One app's session and what it last said. */
    private inner class Watched(val controller: MediaController) : MediaController.Callback() {
        var meta: MediaMetadata? = runCatching { controller.metadata }.getOrNull()
        var state: PlaybackState? = runCatching { controller.playbackState }.getOrNull()

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            meta = metadata
            refresh()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            this.state = state
            refresh()
        }

        override fun onSessionDestroyed() {
            meta = null
            state = null
            refresh()
        }
    }

    private var watched: List<Watched> = emptyList()
    private val changed = MediaSessionManager.OnActiveSessionsChangedListener { list -> watch(list.orEmpty()) }

    /** The cover last shrunk, and what it was shrunk for. */
    private var artKey: String? = null
    private var artSmall: Bitmap? = null

    /** The controller whose track shows, for the buttons. */
    @Volatile private var current: MediaController? = null

    fun acquire() {
        handler.post {
            users++
            listen()
        }
    }

    fun retry() {
        handler.post { if (users > 0) listen() }
    }

    fun release() {
        handler.post {
            users = (users - 1).coerceAtLeast(0)
            if (users > 0) return@post
            if (listening) runCatching { sessions?.removeOnActiveSessionsChangedListener(changed) }
            listening = false
            watch(emptyList())
            // Nothing shows the music any more: let go of the cover too
            artKey = null
            artSmall = null
            _track.value = null
        }
    }

    private fun listen() {
        if (listening || !NowPlaying.allowed(context)) return
        runCatching {
            sessions?.addOnActiveSessionsChangedListener(changed, component, handler)
            listening = true
            watch(sessions?.getActiveSessions(component).orEmpty())
        }.onFailure { Log.w(TAG, "No media sessions", it) }
    }

    private fun watch(list: List<MediaController>) {
        val old = watched
        // A session still there keeps its callback; only new ones are asked what they play
        val next = list.map { controller ->
            old.firstOrNull { it.controller.sessionToken == controller.sessionToken } ?: Watched(controller).also {
                runCatching { controller.registerCallback(it, handler) }
            }
        }
        old.filter { o -> next.none { it === o } }.forEach { runCatching { it.controller.unregisterCallback(it) } }
        watched = next
        refresh()
    }

    private fun refresh() {
        var pick: Pair<Watched, MediaMetadata>? = null
        var pickPlaying = false
        for (w in watched) {
            val meta = w.meta ?: continue
            if (title(meta).isNullOrEmpty()) continue
            val playing = w.state?.state == PlaybackState.STATE_PLAYING
            if (pick == null || (playing && !pickPlaying)) {
                pick = w to meta
                pickPlaying = playing
            }
            if (pickPlaying) break
        }
        if (pick == null) {
            current = null
            if (_track.value != null) _track.value = null
            return
        }
        val (w, meta) = pick
        current = w.controller
        val state = w.state
        val title = title(meta)!!
        val artist = (meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))
            ?.trim()?.takeIf { it.isNotEmpty() }
        val album = meta.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim()?.takeIf { it.isNotEmpty() }
        val artUri = (meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI))?.takeIf { it.startsWith("http") }
        val next = Track(
            title, artist, w.controller.packageName, state?.state == PlaybackState.STATE_PLAYING,
            album = album,
            art = art(meta, "${w.controller.packageName}|$title|$artist|$album"),
            artUri = artUri,
            duration = meta.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L),
            position = state?.position ?: -1L,
            positionAt = state?.lastPositionUpdateTime ?: 0L,
            speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f
        )
        val before = _track.value
        if (before != null && same(before, next)) return
        _track.value = next
    }

    /** Whether [next] shows just as [before] does: the same song, playing or not, where it was bound to be. */
    private fun same(before: Track, next: Track): Boolean {
        if (before.copy(position = next.position, positionAt = next.positionAt) != next) return false
        if ((before.position < 0) != (next.position < 0)) return false
        if (next.position < 0) return true
        return kotlin.math.abs(before.positionNow(next.positionAt) - next.position) < POSITION_SLACK_MS
    }

    /** The track's cover at the size it is shown, shrunk once per track rather than on every report. */
    private fun art(meta: MediaMetadata, key: String): Bitmap? {
        val raw = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        val full = "$key|${raw.width}x${raw.height}"
        if (full == artKey) return artSmall
        val small = runCatching { shrink(raw) }.getOrNull()
        artKey = full
        artSmall = small
        return small
    }

    private fun shrink(raw: Bitmap): Bitmap {
        val side = maxOf(raw.width, raw.height)
        val bitmap = if (side <= ART_PX) raw.copy(Bitmap.Config.ARGB_8888, false) ?: raw else {
            val scale = ART_PX.toFloat() / side
            Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt().coerceAtLeast(1), (raw.height * scale).toInt().coerceAtLeast(1), true)
        }
        bitmap.prepareToDraw()
        return bitmap
    }

    private fun title(meta: MediaMetadata): String? =
        (meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))?.trim()

    fun toggle() {
        handler.post {
            val controller = current ?: return@post
            val playing = watched.firstOrNull { it.controller === controller }?.state?.state == PlaybackState.STATE_PLAYING
            runCatching {
                if (playing) controller.transportControls.pause() else controller.transportControls.play()
            }
        }
    }

    fun next() {
        handler.post { runCatching { current?.transportControls?.skipToNext() } }
    }

    fun previous() {
        handler.post { runCatching { current?.transportControls?.skipToPrevious() } }
    }

    companion object {
        private const val TAG = "NowPlaying"

        /** The cover's longest side: the panel shows it at 150 dp, 300 pixels on the TV. */
        private const val ART_PX = 320

        /** How far a reported position may differ from where it was bound to be and change nothing. */
        private const val POSITION_SLACK_MS = 1_500L

        @Volatile private var instance: NowPlayingHub? = null

        fun get(context: Context): NowPlayingHub =
            instance ?: synchronized(this) { instance ?: NowPlayingHub(context.applicationContext).also { instance = it } }
    }
}
