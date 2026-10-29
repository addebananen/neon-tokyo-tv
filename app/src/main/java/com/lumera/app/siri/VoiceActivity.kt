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
    private var pipeOut: OutputStream? = null
    @Volatile private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VoiceOverlay(heard.value, hint.value, level.floatValue) }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            hint.value = "Neon Tokyo behöver mikrofonbehörighet en gång (kör TV-skriptets siri-steg)"
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
            hint.value = "Röststyrning med Siri Remote kräver Android 13 eller senare"
            finishSoon(3000)
            return
        }
        val rec = when {
            SpeechRecognizer.isRecognitionAvailable(this) -> SpeechRecognizer.createSpeechRecognizer(this)
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this) -> SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            else -> null
        }
        if (rec == null) {
            hint.value = "TV:n har ingen taligenkänning"
            finishSoon(3000)
            return
        }
        recognizer = rec
        val (read, write) = ParcelFileDescriptor.createPipe()
        pipeOut = ParcelFileDescriptor.AutoCloseOutputStream(write)
        rec.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sv-SE")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, OUT_RATE)
        rec.startListening(intent)
        read.close()
        val out = pipeOut!!
        thread(name = "siri-voice") { pump(out) }
    }

    /** Reads the utterance from the bridge, decodes it and writes 16 kHz PCM into the recognizer. */
    private fun pump(out: OutputStream) {
        val decoder = OpusDecoder()
        try {
            Socket("127.0.0.1", SiriStatusServer.PORT).use { s ->
                s.soTimeout = 35_000
                s.getOutputStream().apply { write("voice\n".toByteArray()); flush() }
                val lines = s.getInputStream().bufferedReader()
                var last = -1
                while (!done) {
                    val line = lines.readLine() ?: break
                    if (line == "end") break
                    if (!line.startsWith("f ")) continue
                    val frame = hex(line, 2)
                    if (frame.size <= 2) continue
                    // a frame or two lost over the air: keep the timing with silence
                    val seq = (frame[0].toInt() and 0xFF) or ((frame[1].toInt() and 0xFF) shl 8)
                    val gap = if (last < 0) 0 else (seq - last - 1) and 0xFFFF
                    if (gap in 1..4) out.write(ByteArray(gap * FRAME_OUT * 2))
                    last = seq
                    val pcm = decoder.decode(frame, 2)
                    if (pcm.isEmpty()) continue
                    showLevel(pcm)
                    out.write(toBytes(pcm))
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "voice stream ended", e)
        } finally {
            decoder.release()
            runCatching { out.close() }
            runOnUiThread { if (!done) { hint.value = "Tänker …"; level.floatValue = 0f } }
        }
    }

    private fun showLevel(pcm: ShortArray) {
        var sum = 0.0
        for (v in pcm) sum += v.toDouble() * v
        val rms = sqrt(sum / pcm.size) / 32768.0
        val l = (rms * 9).coerceIn(0.0, 1.0).toFloat()
        runOnUiThread { level.floatValue = l }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
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
            heard.value = text
            act(text)
        }

        override fun onError(error: Int) {
            if (done) return
            done = true
            hint.value = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Hörde inget, håll Siri och prata"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Ingen internetanslutning"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofonbehörighet saknas"
                else -> "Taligenkänningen svarade inte ($error)"
            }
            Log.w(TAG, "recognizer error $error")
            finishSoon(1800)
        }
    }

    private fun act(text: String) {
        done = true
        val apps = HomeApps.load(this).map { it.packageName to it.label }
        val command = VoiceCommands.parse(text, apps)
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
        runCatching { pipeOut?.close() }
        recognizer?.destroy()
        recognizer = null
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

/** 48 kHz to 16 kHz: a short low-pass, then every third sample. */
internal class Downsampler {
    private val taps = floatArrayOf(
        -0.0027f, -0.0060f, 0f, 0.0230f, 0.0500f, 0.0350f, -0.0400f, -0.1100f,
        0.2400f, 0.5414f, 0.2400f, -0.1100f, -0.0400f, 0.0350f, 0.0500f, 0.0230f, 0f, -0.0060f, -0.0027f
    ).let { t -> val s = t.sum(); FloatArray(t.size) { t[it] / s } }
    private val history = FloatArray(taps.size)
    private var phase = 0

    fun push(input: ShortArray): ShortArray {
        val out = ShortArray((input.size + phase + 2) / 3)
        var n = 0
        for (x in input) {
            System.arraycopy(history, 1, history, 0, history.size - 1)
            history[history.size - 1] = x.toFloat()
            if (phase == 0) {
                var acc = 0f
                for (i in taps.indices) acc += taps[i] * history[i]
                if (n < out.size) out[n++] = acc.coerceIn(-32768f, 32767f).toInt().toShort()
            }
            phase = (phase + 1) % 3
        }
        return if (n == out.size) out else out.copyOf(n)
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
