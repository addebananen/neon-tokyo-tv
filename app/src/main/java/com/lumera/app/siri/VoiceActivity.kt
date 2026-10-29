package com.lumera.app.siri

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.lumera.app.launcher.HomeApps
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Talking to the TV through the Siri Remote. Holding Siri opens this over whatever is on screen;
 * the bridge streams the remote's microphone (Opus, 16 kHz) here, MediaCodec decodes it, the TV's
 * speech recognizer turns it into text, and [VoiceCommands] decides what to do with it.
 */
class VoiceActivity : ComponentActivity() {
    private val heard = mutableStateOf("")
    private val hint = mutableStateOf("Lyssnar …")
    private val level = mutableFloatStateOf(0f)
    private var recognizer: SpeechRecognizer? = null
    /**
     * The recognizer's end of the sound pipe. startListening only queues the request, so this
     * stays open until the recognizer has it (closing it at once crashed with a bad descriptor).
     */
    private var sourceForRecognizer: ParcelFileDescriptor? = null
    private var tap = PcmTap()
    @Volatile private var socket: Socket? = null
    @Volatile private var done = false
    /** Which Siri press this is: a stream from an earlier one leaves the screen alone. */
    @Volatile private var press = 0
    /** Recognizers tried for this press, so a failing one hands over to the next. */
    private var attempt = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VoiceOverlay(heard.value, hint.value, level.floatValue) }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Android asks once, on the TV; OK on the Siri Remote answers it
            hint.value = "Tillåt mikrofonen en gång, så kan du prata med TV:n"
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        begin()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) begin() else finishSoon(2500)
    }

    /** A new Siri press while this is still up starts over. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        stopAll()
        done = false
        heard.value = ""
        hint.value = "Lyssnar …"
        begin()
    }

    private fun begin() {
        if (Build.VERSION.SDK_INT < 33) {
            remember("Android ${Build.VERSION.RELEASE} cannot take sound from the remote")
            hint.value = "Röststyrning med Siri Remote kräver Android 13 eller senare"
            finishSoon(3000)
            return
        }
        // the sound is kept, so a recognizer that fails can hand the same words to another
        tap = PcmTap()
        attempt = 0
        val mine = ++press
        val t = tap
        thread(name = "siri-voice") { pump(t, mine) }
        if (!listen(0)) {
            remember("no speech recognizer on this TV")
            Log.w(TAG, "voice: no speech recognizer on this TV")
            done = true
            hint.value = "TV:n har ingen taligenkänning, öppnar sök"
            window.decorView.postDelayed({ openSearch(); finish() }, 1200)
        }
    }

    /**
     * Starts recognizer number [n]: the TV's own in Swedish, then the TV's own in its own
     * language (Swedish may not be installed), then the on-device one.
     */
    private fun listen(n: Int): Boolean {
        attempt = n
        recognizer?.destroy()
        recognizer = null
        closeSource()
        val rec = when (n) {
            0, 1 -> if (SpeechRecognizer.isRecognitionAvailable(this)) SpeechRecognizer.createSpeechRecognizer(this) else null
            2 -> if (SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) SpeechRecognizer.createOnDeviceSpeechRecognizer(this) else null
            else -> null
        } ?: return if (n < 2) listen(n + 1) else false
        recognizer = rec
        val (read, write) = ParcelFileDescriptor.createPipe()
        rec.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, OUT_RATE)
        if (n != 1) intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sv-SE")
        Log.i(TAG, "voice: recognizer ${listOf("TV, Swedish", "TV, its own language", "on-device")[n]}")
        rec.startListening(intent)
        sourceForRecognizer = read
        val out = ParcelFileDescriptor.AutoCloseOutputStream(write)
        val t = tap
        // what was said so far goes in first; a full pipe must not hold up the screen
        thread(name = "siri-voice-feed") { t.attach(out) }
        return true
    }

    /** Reads the utterance from the bridge, decodes it and writes 16 kHz PCM into [tap]. */
    private fun pump(tap: PcmTap, mine: Int) {
        val decoder = OpusDecoder()
        var frames = 0
        var samples = 0L
        var energy = 0.0
        try {
            Socket("127.0.0.1", SiriStatusServer.PORT).also { socket = it }.use { s ->
                s.soTimeout = 35_000
                s.getOutputStream().apply { write("voice\n".toByteArray()); flush() }
                val lines = s.getInputStream().bufferedReader()
                var last = -1
                while (!done && press == mine) {
                    val line = lines.readLine() ?: break
                    if (line == "end") break
                    if (!line.startsWith("f ")) continue
                    val frame = hex(line, 2)
                    if (frame.size <= 2) continue
                    // a frame or two lost over the air: keep the timing with silence
                    val seq = (frame[0].toInt() and 0xFF) or ((frame[1].toInt() and 0xFF) shl 8)
                    val gap = if (last < 0) 0 else (seq - last - 1) and 0xFFFF
                    if (gap in 1..4) tap.write(ByteArray(gap * FRAME_OUT * 2))
                    last = seq
                    val pcm = decoder.decode(frame, 2)
                    frames++
                    if (pcm.isEmpty()) continue
                    samples += pcm.size
                    for (v in pcm) energy += v.toDouble() * v
                    showLevel(pcm, mine)
                    tap.write(toBytes(pcm))
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "voice stream ended", e)
        } finally {
            val rms = if (samples > 0) sqrt(energy / samples).toInt() else 0
            Log.i(TAG, "voice: $frames frames, $samples samples at $OUT_RATE Hz, rms $rms, decoder ${decoder.name}")
            if (frames == 0) remember("no sound came from the remote")
            else if (decoder.name == "none") remember("this TV cannot decode the remote's sound (no Opus decoder)")
            decoder.release()
            tap.end()
            runOnUiThread { if (!done && press == mine) { hint.value = "Tänker …"; level.floatValue = 0f } }
        }
    }

    private fun showLevel(pcm: ShortArray, mine: Int) {
        var sum = 0.0
        for (v in pcm) sum += v.toDouble() * v
        val rms = sqrt(sum / pcm.size) / 32768.0
        val l = (rms * 9).coerceIn(0.0, 1.0).toFloat()
        runOnUiThread { if (press == mine) level.floatValue = l }
    }

    private val listener = object : RecognitionListener {
        // the recognizer holds its own copy of the pipe now
        override fun onReadyForSpeech(params: Bundle?) = closeSource()
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { hint.value = "Tänker …" }
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }?.let { heard.value = it }
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (text.isBlank()) return onError(SpeechRecognizer.ERROR_NO_MATCH)
            Log.i(TAG, "voice heard: $text")
            remember("heard \"$text\"")
            heard.value = text
            act(text)
        }

        override fun onError(error: Int) {
            closeSource()
            if (done) return
            Log.w(TAG, "voice recognizer error $error (attempt $attempt)")
            val next = when (error) {
                // nothing said, or no internet: another recognizer would not do better
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> null
                // Swedish missing: the same recognizer in the TV's language
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    if (attempt == 0) 1 else if (attempt == 1) 2 else null
                // it would not take sound from us (or broke): the on-device one
                else -> if (attempt < 2) 2 else null
            }
            if (next != null && listen(next)) return
            done = true
            remember("recognizer error $error")
            hint.value = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Hörde inget, håll Siri och prata"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Ingen internetanslutning"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofonbehörighet saknas"
                else -> "TV:n kunde inte tolka rösten, öppnar sök"
            }
            val searchInstead = error !in listOf(
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT
            )
            // a Siri press always leads somewhere: when the voice cannot be understood, to search
            window.decorView.postDelayed({ if (searchInstead) openSearch(); if (!isFinishing) finish() }, 1800)
        }
    }

    private fun closeSource() {
        runCatching { sourceForRecognizer?.close() }
        sourceForRecognizer = null
    }

    /** The last voice search's outcome, for the Siri Remote settings page. */
    private fun remember(what: String) {
        runCatching {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(LAST, "$what (${java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT).format(java.util.Date())})")
                .apply()
        }
    }

    private fun openSearch() {
        listOf(
            Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH),
            Intent(Intent.ACTION_ASSIST),
            Intent(Intent.ACTION_WEB_SEARCH)
        ).firstOrNull { start(it) }
    }

    private fun act(text: String) {
        done = true
        val apps = HomeApps.load(this).map { it.packageName to it.label }
        val command = VoiceCommands.parse(text, apps)
        Log.i(TAG, "voice action: $command")
        hint.value = when (command) {
            is VoiceCommand.OpenApp -> "Öppnar ${command.label}"
            is VoiceCommand.PlayIn -> "Söker i ${command.app.replaceFirstChar { it.uppercase() }}"
            is VoiceCommand.Search -> "Söker"
        }
        window.decorView.postDelayed({
            when (command) {
                is VoiceCommand.OpenApp -> HomeApps.open(this, command.packageName)
                is VoiceCommand.PlayIn -> playIn(command.app, command.query)
                is VoiceCommand.Search -> search(command.query)
            }
            finish()
        }, 450)
    }

    private fun playIn(app: String, query: String) {
        val q = Uri.encode(query)
        val tries = when (app) {
            "spotify" -> listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$q")).setPackage("com.spotify.tv.android"),
                Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$q"))
            )
            "youtube music" -> listOf(Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=$q")))
            else -> listOf(
                Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube.tv").putExtra(SearchManager.QUERY, query),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$q")).setPackage("com.google.android.youtube.tv")
            )
        }
        if (tries.none { start(it) }) search(query)
    }

    private fun search(query: String) {
        listOf(
            Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH).putExtra(SearchManager.QUERY, query),
            Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        ).firstOrNull { start(it) }
    }

    private fun start(intent: Intent) = runCatching {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)

    /** Back or Home while listening just closes; Siri itself is handled by the bridge. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }

    private fun finishSoon(ms: Long) = window.decorView.postDelayed({ if (!isFinishing) finish() }, ms)

    private fun stopAll() {
        done = true
        runCatching { socket?.close() }
        tap.end()
        recognizer?.destroy()
        recognizer = null
        closeSource()
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    companion object {
        private const val TAG = "NeonSiri"
        const val OUT_RATE = 16_000
        const val PREFS = "siri_voice"
        const val LAST = "last"
        /** 20 ms at [OUT_RATE]. */
        const val FRAME_OUT = 320

        fun hex(line: String, from: Int): ByteArray {
            val n = (line.length - from) / 2
            return ByteArray(n) { i ->
                ((Character.digit(line[from + 2 * i], 16) shl 4) or Character.digit(line[from + 2 * i + 1], 16)).toByte()
            }
        }

        fun toBytes(pcm: ShortArray): ByteArray {
            val b = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            b.asShortBuffer().put(pcm)
            return b.array()
        }
    }
}

/**
 * The decoded sound of one Siri press: kept whole, and passed on to whichever recognizer is
 * listening. A recognizer attached late (a second try) gets everything said so far first.
 */
internal class PcmTap {
    private val kept = java.io.ByteArrayOutputStream()
    private var sink: OutputStream? = null
    private var ended = false
    private val lock = Object()

    fun write(b: ByteArray) {
        val out = synchronized(lock) {
            if (kept.size() < MAX) kept.write(b)
            sink
        } ?: return
        // outside the lock: a slow recognizer must not stop the next one from attaching
        runCatching { out.write(b) }.onFailure { synchronized(lock) { if (sink === out) sink = null } }
    }

    fun end() {
        val out = synchronized(lock) { ended = true; sink.also { sink = null } }
        runCatching { out?.close() }
    }

    /** Hands [out] what was said so far, then everything after; closes it at the end. */
    fun attach(out: OutputStream) {
        val old: OutputStream?
        val backlog: ByteArray
        synchronized(lock) {
            old = sink
            sink = null
            backlog = kept.toByteArray()
        }
        runCatching { old?.close() }
        val ok = runCatching { out.write(backlog) }.isSuccess
        synchronized(lock) {
            // anything written while the backlog went out
            val more = kept.toByteArray()
            if (ok && more.size > backlog.size) runCatching { out.write(more, backlog.size, more.size - backlog.size) }
            if (ended || !ok) runCatching { out.close() } else sink = out
        }
    }

    companion object {
        /** 30 s at 16 kHz. */
        const val MAX = 30 * 16_000 * 2
    }
}

/**
 * The Siri Remote's Opus frames through the TV's own decoder. MediaCodec always gives 48 kHz;
 * [Downsampler] brings it to the 16 kHz speech recognizers expect.
 */
private class OpusDecoder {
    private val codec: MediaCodec? = runCatching {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, 48_000, 1)
        format.setByteBuffer("csd-0", ByteBuffer.wrap(opusHead()))
        format.setByteBuffer("csd-1", nanos(0))
        format.setByteBuffer("csd-2", nanos(80_000_000))
        MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).apply { configure(format, null, null, 0); start() }
    }.onFailure { Log.w("NeonSiri", "no opus decoder", it) }.getOrNull()
    val name: String get() = runCatching { codec?.name }.getOrNull() ?: "none"
    private val down = Downsampler()
    private val info = MediaCodec.BufferInfo()
    private var pts = 0L

    fun decode(frame: ByteArray, offset: Int): ShortArray {
        val c = codec ?: return ShortArray(0)
        val inIndex = c.dequeueInputBuffer(20_000)
        if (inIndex >= 0) {
            val buf = c.getInputBuffer(inIndex)!!
            buf.clear()
            buf.put(frame, offset, frame.size - offset)
            c.queueInputBuffer(inIndex, 0, frame.size - offset, pts, 0)
            pts += 20_000
        }
        val out = ArrayList<Short>(320)
        while (true) {
            val outIndex = c.dequeueOutputBuffer(info, if (out.isEmpty()) 20_000 else 0)
            if (outIndex < 0) break
            val buf = c.getOutputBuffer(outIndex)!!.order(ByteOrder.LITTLE_ENDIAN)
            buf.position(info.offset)
            buf.limit(info.offset + info.size)
            val shorts = buf.asShortBuffer()
            val pcm = ShortArray(shorts.remaining()).also { shorts.get(it) }
            c.releaseOutputBuffer(outIndex, false)
            for (s in down.push(pcm)) out.add(s)
        }
        return out.toShortArray()
    }

    fun release() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
    }

    private fun opusHead(): ByteArray = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("OpusHead".toByteArray())
        put(1)                  // version
        put(1)                  // mono
        putShort(0)             // pre-skip
        putInt(16_000)          // what the remote recorded at
        putShort(0)             // gain
        put(0)                  // mono/stereo mapping
    }.array()

    private fun nanos(n: Long): ByteBuffer = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(n).apply { flip() }
}

/**
 * 48 kHz to 16 kHz: a windowed-sinc low-pass (flat to 5 kHz, gone above 9 kHz, so nothing folds
 * back into the speech band), then every third sample.
 */
internal class Downsampler {
    private val taps = FloatArray(TAPS) { i ->
        val m = i - (TAPS - 1) / 2.0
        val sinc = if (m == 0.0) 2 * CUTOFF else sin(2 * PI * CUTOFF * m) / (PI * m)
        val blackman = 0.42 - 0.5 * cos(2 * PI * i / (TAPS - 1)) + 0.08 * cos(4 * PI * i / (TAPS - 1))
        (sinc * blackman).toFloat()
    }.let { t -> val sum = t.sum(); FloatArray(t.size) { t[it] / sum } }
    /** The last [TAPS] samples, as a ring; [pos] is where the next one goes. */
    private val history = FloatArray(TAPS)
    private var pos = 0
    private var phase = 0

    fun push(input: ShortArray): ShortArray {
        val out = ShortArray((input.size + phase + 2) / 3)
        var n = 0
        for (x in input) {
            history[pos] = x.toFloat()
            pos = (pos + 1) % TAPS
            if (phase == 0) {
                var acc = 0f
                var j = pos // oldest sample
                for (i in 0 until TAPS) {
                    acc += taps[i] * history[j]
                    j = if (j + 1 == TAPS) 0 else j + 1
                }
                if (n < out.size) out[n++] = acc.coerceIn(-32768f, 32767f).toInt().toShort()
            }
            phase = (phase + 1) % 3
        }
        return if (n == out.size) out else out.copyOf(n)
    }

    companion object {
        const val TAPS = 63
        /** 7.2 kHz, as a fraction of 48 kHz. */
        const val CUTOFF = 7_200.0 / 48_000.0
    }
}

@Composable
private fun VoiceOverlay(heard: String, hint: String, level: Float) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Transparent, Color(0xB0060312), Color(0xF0060312)))
        ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = 72.dp, start = 96.dp, end = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LevelBars(level)
            Spacer(Modifier.height(22.dp))
            Text(
                heard.ifEmpty { " " }, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 2
            )
            Spacer(Modifier.height(8.dp))
            Text(hint, color = Color(0xFFB8A9FF), fontSize = 18.sp, textAlign = TextAlign.Center)
        }
    }
}

/** Siri-like bars that breathe with the voice, in Neon Tokyo's pink and cyan. */
@Composable
private fun LevelBars(level: Float) {
    val shown by animateFloatAsState(level, spring(dampingRatio = 0.6f, stiffness = 500f), label = "level")
    val shape = listOf(0.35f, 0.6f, 0.85f, 1f, 0.85f, 0.6f, 0.35f)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        shape.forEachIndexed { i, s ->
            val h = 10f + 70f * s * (0.15f + 0.85f * shown) * (if (i % 2 == 0) 1f else 0.92f)
            Box(
                Modifier.width(10.dp).height(h.dp).clip(RoundedCornerShape(5.dp)).background(
                    Brush.verticalGradient(listOf(Color(0xFFFF4FD8), Color(0xFF4FE3FF)))
                )
            )
        }
    }
}
