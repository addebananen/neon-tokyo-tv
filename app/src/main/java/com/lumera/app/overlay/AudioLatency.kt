package com.lumera.app.overlay

import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Measures how long sound takes from Android's mixer to the TV's output (speakers, ARC or
 * Bluetooth) by playing a moment of silence and asking the output when each frame left it.
 * The soundbar's own processing comes after that and no app can see it.
 */
internal object AudioLatency {
    private const val RATE = 48_000
    private const val RUN_MS = 900L

    @Volatile
    private var kept: Pair<Long, Int>? = null

    /** The last measure if it is under [freshMs] old. */
    fun recent(freshMs: Long = 60_000L): Int? =
        kept?.takeIf { SystemClock.uptimeMillis() - it.first < freshMs }?.second

    suspend fun measure(): Int? = withContext(Dispatchers.IO) {
        recent(20_000L)?.let { return@withContext it }
        val track = runCatching {
            val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            AudioTrack.Builder()
                .setAudioAttributes(SignalProbe.MEDIA)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(min.coerceAtLeast(4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return@withContext null
        try {
            track.setVolume(0f)
            track.play()
            val chunk = ShortArray(RATE / 100 * 2) // 10 ms of silence
            val stamp = AudioTimestamp()
            var written = 0L
            val samples = ArrayList<Float>()
            val until = SystemClock.uptimeMillis() + RUN_MS
            while (SystemClock.uptimeMillis() < until) {
                val wrote = track.write(chunk, 0, chunk.size)
                if (wrote <= 0) break
                written += wrote / 2
                if (track.getTimestamp(stamp) && stamp.framePosition > 0) {
                    val now = System.nanoTime()
                    val outNow = stamp.framePosition + (now - stamp.nanoTime) * RATE / 1_000_000_000.0
                    // What is queued past the output, less what still waits in this app's own buffer
                    val queuedMs = ((written - outNow) * 1000.0 / RATE).toFloat()
                    samples += queuedMs
                }
            }
            val bufferMs = track.bufferSizeInFrames * 1000f / RATE
            val latest = samples.takeLast(20).sorted()
            if (latest.size < 5) return@withContext null
            val median = latest[latest.size / 2]
            val ms = (median - bufferMs).roundToInt()
            if (ms !in 1..1_000) return@withContext null
            kept = SystemClock.uptimeMillis() to ms
            ms
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }
}
