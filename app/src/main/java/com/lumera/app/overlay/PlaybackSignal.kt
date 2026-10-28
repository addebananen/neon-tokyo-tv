package com.lumera.app.overlay

import android.os.SystemClock

/**
 * What Neon Tokyo's own player is playing right now, as the decoder gets it: the video and
 * sound inside the file, not what the TV makes of them. Picture diagnostics reads it, set
 * by the player as formats arrive and cleared when it stops.
 */
data class PlayingSignal(
    val width: Int = 0,
    val height: Int = 0,
    /** The frame rate the file says it has, 23.976 for most films. */
    val frameRate: Float = 0f,
    val videoCodec: String? = null,
    /** "Dolby Vision", "HDR10", "HLG" or null for SDR. */
    val hdr: String? = null,
    val bitDepth: Int? = null,
    val videoBitrate: Int = 0,
    val videoDecoder: String? = null,
    val audioCodec: String? = null,
    val audioChannels: Int = 0,
    val audioSampleRate: Int = 0,
    /** True when the sound goes out still packed (Dolby/DTS to the soundbar), false when decoded to PCM. */
    val audioPassthrough: Boolean? = null,
    val tunneled: Boolean = false,
    val droppedFrames: Int = 0,
    val playing: Boolean = false,
    val since: Long = SystemClock.uptimeMillis()
)

object PlaybackSignal {
    @Volatile
    var current: PlayingSignal? = null
        private set

    /**
     * How many frames the player has put on screen so far, read on the main thread where the
     * player lives. Two readings a second apart are the frame rate that really reaches the TV.
     */
    @Volatile
    var renderedFrames: (() -> Int?)? = null

    @Volatile
    private var owner: Any? = null

    /** A player starts: what an earlier one left is gone. */
    fun start(player: Any, frames: () -> Int?) {
        owner = player
        current = null
        renderedFrames = frames
    }

    fun update(change: (PlayingSignal) -> PlayingSignal) {
        current = change(current ?: PlayingSignal())
    }

    /** [player] stopped; a newer player keeps what it set. */
    fun stop(player: Any) {
        if (owner !== player) return
        owner = null
        current = null
        renderedFrames = null
    }
}
