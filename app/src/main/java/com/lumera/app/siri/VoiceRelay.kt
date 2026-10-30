package com.lumera.app.siri

import android.util.Log
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/**
 * Carries the Siri Remote's microphone from the bridge to Neon Tokyo's [VoiceActivity], which
 * decodes and understands it. Holding Siri opens the activity at once; the frames that arrive
 * before it has connected wait here, so the first word is never lost.
 */
object VoiceRelay {
    private const val TAG = "NeonSiri"
    /** Frames: 2-byte sequence number + Opus. An empty array ends the utterance. */
    private val queue = LinkedBlockingDeque<ByteArray>()
    @Volatile private var active = false
    /** Each Siri press is a new session; a stream from an earlier press stops taking frames. */
    @Volatile private var session = 0
    @Volatile var packageName = "com.neontokyo.tv"
    private val END = ByteArray(0)

    fun start() {
        session++
        queue.clear()
        active = true
        // `am` runs as the shell user, which may start any app's activity
        runCatching {
            Runtime.getRuntime().exec(
                arrayOf("am", "start", "--activity-no-animation", "-n", "$packageName/com.lumera.app.siri.VoiceActivity")
            )
        }.onFailure { Log.w(TAG, "could not open voice search", it) }
    }

    fun frame(frame: ByteArray) {
        if (active) queue.offer(frame)
    }

    /** Siri released: the remote sends a short tail, then the utterance ends. */
    fun stop() {
        if (!active) return
        active = false
        Thread {
            Thread.sleep(350)
            queue.offer(END)
        }.start()
    }

    /** Streams one utterance to [write], as `f <hex>` lines and a final `end`. */
    fun stream(write: (String) -> Unit) {
        val mine = session
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline && session == mine) {
            val f = queue.poll(2, TimeUnit.SECONDS) ?: if (active) continue else break
            if (session != mine) {
                // a new press began while this waited: the frame is the new stream's
                queue.offerFirst(f)
                break
            }
            if (f.isEmpty()) break
            write("f " + f.joinToString("") { "%02x".format(it) })
        }
        write("end")
    }
}
