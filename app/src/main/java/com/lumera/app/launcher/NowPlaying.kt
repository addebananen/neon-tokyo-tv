package com.lumera.app.launcher

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
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
 */
class NowPlaying(private val context: Context) {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, NowPlayingListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val _track = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = _track.asStateFlow()

    private var controllers: List<MediaController> = emptyList()
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }
    private val changed = MediaSessionManager.OnActiveSessionsChangedListener { list -> watch(list.orEmpty()) }
    private var listening = false

    fun start() {
        if (listening || !allowed(context)) return
        runCatching {
            sessions?.addOnActiveSessionsChangedListener(changed, component, handler)
            listening = true
            watch(sessions?.getActiveSessions(component).orEmpty())
        }.onFailure { Log.w(TAG, "No media sessions", it) }
    }

    fun stop() {
        if (listening) runCatching { sessions?.removeOnActiveSessionsChangedListener(changed) }
        listening = false
        watch(emptyList())
    }

    private fun watch(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = list
        list.forEach { runCatching { it.registerCallback(callback, handler) } }
        refresh()
    }

    private fun refresh() {
        val tracks = controllers.mapNotNull { controller ->
            val meta = controller.metadata ?: return@mapNotNull null
            val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
                ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.trim()
            if (title.isNullOrEmpty()) return@mapNotNull null
            val artist = (meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))
                ?.trim()?.takeIf { it.isNotEmpty() }
            val state = controller.playbackState
            val playing = state?.state == PlaybackState.STATE_PLAYING
            val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            val artUri = (meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI))?.takeIf { it.startsWith("http") }
            controller to Track(
                title, artist, controller.packageName, playing,
                album = meta.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim()?.takeIf { it.isNotEmpty() },
                art = art,
                artUri = artUri,
                duration = meta.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L),
                position = state?.position ?: -1L,
                positionAt = state?.lastPositionUpdateTime ?: 0L,
                speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f
            )
        }
        val pick = tracks.firstOrNull { it.second.playing } ?: tracks.firstOrNull()
        current = pick?.first
        _track.value = pick?.second
    }

    /** The app whose track is shown, to steer. */
    private var current: MediaController? = null

    /** Plays or pauses what is shown, as its own app's play button would. */
    fun toggle() {
        val controller = current ?: return
        runCatching {
            if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) controller.transportControls.pause()
            else controller.transportControls.play()
        }
    }

    fun next() {
        runCatching { current?.transportControls?.skipToNext() }
    }

    fun previous() {
        runCatching { current?.transportControls?.skipToPrevious() }
    }

    companion object {
        private const val TAG = "NowPlaying"

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
