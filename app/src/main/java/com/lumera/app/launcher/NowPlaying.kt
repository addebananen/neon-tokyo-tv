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

/** A song or show playing in some app on the TV. */
data class Track(val title: String, val artist: String?, val packageName: String, val playing: Boolean)

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
            val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            Track(title, artist, controller.packageName, playing)
        }
        _track.value = tracks.firstOrNull { it.playing } ?: tracks.firstOrNull()
    }

    companion object {
        private const val TAG = "NowPlaying"

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
