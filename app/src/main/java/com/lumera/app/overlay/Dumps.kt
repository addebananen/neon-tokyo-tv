package com.lumera.app.overlay

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

/** The video on screen as SurfaceFlinger, the screen's compositor, sees it. */
data class VideoLayer(val name: String, val width: Int, val height: Int, val hdr: String?, val fps: Float?)

/**
 * Asks the TV's system services what they know, the way `adb shell dumpsys` does. Android only
 * answers apps that were given the DUMP permission over ADB, so all of this is a bonus: without
 * it these return null and the diagnostics show what the public APIs know.
 */
internal object Dumps {
    private const val TAG = "Dumps"
    private const val TIMEOUT_MS = 1_500L

    fun run(vararg args: String, timeoutMs: Long = TIMEOUT_MS): String? = runCatching {
        val process = ProcessBuilder(listOf("dumpsys") + args).redirectErrorStream(true).start()
        val text = StringBuilder()
        val reader = Thread {
            runCatching { process.inputStream.bufferedReader().use { r -> r.lineSequence().take(20_000).forEach { text.appendLine(it) } } }
        }.apply { isDaemon = true; start() }
        if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) process.destroy()
        reader.join(200)
        text.toString().takeIf { it.isNotBlank() && !it.contains("Permission Denial") }
    }.onFailure { Log.w(TAG, "dumpsys ${args.firstOrNull()} failed", it) }.getOrNull()

    /** The biggest video surface of an app other than [skip], with its measured frame rate. */
    fun videoLayer(skip: String?): VideoLayer? {
        val names = run("SurfaceFlinger", "--list")?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() }?.toList() ?: return null
        val candidates = names.filter { name ->
            name.contains("SurfaceView", ignoreCase = true) && !name.contains("Background", ignoreCase = true) &&
                (skip == null || !name.contains(skip))
        }
        if (candidates.isEmpty()) return null
        val full = run("SurfaceFlinger", timeoutMs = 2_500L).orEmpty()
        val layers = candidates.map { name -> name to layerFacts(full, name) }
        val (name, facts) = layers.maxByOrNull { (_, f) -> f.first * f.second } ?: return null
        val fps = run("SurfaceFlinger", "--latency", name)?.let { latencyFps(it) }
        if (facts.first <= 0 && fps == null) return null
        return VideoLayer(name, facts.first, facts.second, facts.third, fps)
    }

    /** The buffer size and HDR of [name]'s layer in a full SurfaceFlinger dump. */
    internal fun layerFacts(dump: String, name: String): Triple<Int, Int, String?> {
        val start = dump.indexOf(name).takeIf { it >= 0 } ?: return Triple(0, 0, null)
        // The layer's own lines, up to the next layer
        val end = Regex("""\n[+*-] (Layer|Output Layer)""").find(dump, start + name.length)?.range?.first ?: (start + 4_000).coerceAtMost(dump.length)
        val block = dump.substring(start, end.coerceAtMost(dump.length))
        val size = Regex("""(?:activeBuffer|buffer|bufferSize|w/h)[^\n]*?(\d{3,4})\s*x\s*(\d{3,4})""", RegexOption.IGNORE_CASE).find(block)
        val width = size?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val height = size?.groupValues?.get(2)?.toIntOrNull() ?: 0
        val dataspace = Regex("""dataspace\s*=\s*([A-Za-z0-9_ ()]+)""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1).orEmpty()
        return Triple(width, height, hdrFromDataspace(dataspace))
    }

    fun hdrFromDataspace(text: String): String? {
        val t = text.uppercase()
        return when {
            "PQ" in t || "ST2084" in t -> "HDR10"
            "HLG" in t -> "HLG"
            else -> null
        }
    }

    /**
     * The frame rate in `dumpsys SurfaceFlinger --latency`: a refresh period, then one line per
     * recent frame with the time it was shown in the middle. Unset rows are 0 or Long.MAX_VALUE.
     */
    internal fun latencyFps(text: String): Float? {
        val shown = text.lineSequence().drop(1).mapNotNull { line ->
            val parts = line.trim().split(Regex("""\s+"""))
            parts.getOrNull(1)?.toLongOrNull()?.takeIf { it in 1 until Long.MAX_VALUE }
        }.toList().sorted()
        if (shown.size < 8) return null
        val span = shown.last() - shown.first()
        if (span <= 0) return null
        val fps = (shown.size - 1) * 1e9f / span
        return fps.takeIf { it in 1f..240f }
    }

    /** The readings as text, as saved and as sent back to `am broadcast ... --es show report`. */
    fun readings(report: SignalReport): String = buildString {
        appendLine("Neon Tokyo picture diagnostics, ${java.util.Date()}")
        appendLine("Source: ${report.source}")
        listOf(report.signal, report.panel, report.hdr, report.game, report.sound, report.latency).forEach { section ->
            appendLine()
            appendLine("[${section.title}]")
            section.readings.forEach { appendLine("${it.label}: ${it.value} (${it.how}${it.note?.let { n -> ", $n" }.orEmpty()})") }
        }
    }

    /**
     * Everything diagnostics saw, written next to the app's files so it can be fetched with
     * `adb pull /sdcard/Android/data/com.neontokyo.tv/files/diagnostics/latest.txt`.
     */
    fun save(context: Context, report: SignalReport, withSystem: Boolean) {
        runCatching {
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "diagnostics").apply { mkdirs() }
            val readings = readings(report)
            val file = File(dir, "latest.txt")
            // The readings first, as asking the system can take seconds
            file.writeText(readings + "\n===== properties =====\n" + props())
            Log.i(TAG, "Saved the report to $file")
            if (!withSystem) return@runCatching
            val text = buildString {
                append(readings)
                for (service in listOf("display", "hdmi_control", "tv_input", "media.audio_flinger", "audio")) {
                    appendLine()
                    appendLine("===== dumpsys $service =====")
                    appendLine(run(service, timeoutMs = 2_500L)?.take(60_000) ?: "(no answer)")
                }
                appendLine()
                appendLine("===== SurfaceFlinger --list =====")
                appendLine(run("SurfaceFlinger", "--list") ?: "(no answer)")
                appendLine()
                appendLine("===== properties =====")
                appendLine(props())
            }
            file.writeText(text)
        }.onFailure { Log.w(TAG, "Could not save the report", it) }
    }

    /** The TV's readable system properties about the picture and sound, to learn what the TV tells. */
    private fun props(): String = runCatching {
        val process = ProcessBuilder("getprop").redirectErrorStream(true).start()
        val lines = process.inputStream.bufferedReader().use { it.readLines() }
        process.waitFor(1, TimeUnit.SECONDS)
        val wanted = Regex("""hdmi|hdr|vrr|allm|game|frc|memc|frame|refresh|timing|dolby|atmos|arc|avsync|lipsync|latency|panel|display""", RegexOption.IGNORE_CASE)
        lines.filter { wanted.containsMatchIn(it) }.take(300).joinToString("\n")
    }.getOrDefault("(none)")
}
