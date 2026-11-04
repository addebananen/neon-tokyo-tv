package com.neontokyo.desktop.player

import java.awt.Desktop
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.URI
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import kotlin.concurrent.thread

/**
 * Plays a source in mpv (bundled with the Windows installer; it opens MKV, HEVC and ASS
 * subtitles like the Android player), else in VLC, else in the default app for the address.
 *
 * While mpv plays, the app listens on mpv's control pipe and saves where you are every ten
 * seconds and when mpv closes, so Continue watching and "watched" work as on the TV.
 */
object Player {
    private val windows = System.getProperty("os.name").startsWith("Windows")

    /** The installer puts mpv here (compose.application.resources.dir/mpv). */
    private val bundled: File? = System.getProperty("compose.application.resources.dir")?.let { File(it, "mpv") }

    private fun find(vararg names: String): File? {
        val dirs = listOfNotNull(bundled?.path) + (System.getenv("PATH") ?: "").split(File.pathSeparator) + listOfNotNull(
            System.getenv("ProgramFiles")?.let { "$it\\mpv" },
            System.getenv("LOCALAPPDATA")?.let { "$it\\Programs\\mpv" },
            System.getenv("ProgramFiles")?.let { "$it\\VideoLAN\\VLC" },
            System.getenv("ProgramFiles(x86)")?.let { "$it\\VideoLAN\\VLC" },
        )
        for (dir in dirs) for (n in names) {
            val f = File(dir, n)
            if (f.isFile) return f
        }
        return null
    }

    private val mpv by lazy { find("mpv.exe", "mpv") }
    private val vlc by lazy { find("vlc.exe", "vlc") }

    val playerName: String
        get() = when {
            mpv != null -> "mpv"
            vlc != null -> "VLC"
            else -> "your default player"
        }

    /**
     * Opens [url]. [progress] gets (position, duration) in milliseconds from a background thread,
     * every ten seconds and once more when the player closes; only mpv reports it.
     */
    fun play(url: String, title: String, startMs: ULong, headers: Map<String, String>, progress: (ULong, ULong) -> Unit = { _, _ -> }) {
        val start = (startMs / 1000uL).toLong()
        mpv?.let { exe ->
            val pipe = if (windows) "\\\\.\\pipe\\neon-mpv-${System.nanoTime()}"
            else File(System.getProperty("java.io.tmpdir"), "neon-mpv-${System.nanoTime()}.sock").path
            val args = mutableListOf(
                exe.path, "--force-media-title=$title", "--fullscreen", "--keep-open=no", "--force-window=immediate",
                "--input-ipc-server=$pipe", "--osd-font=Kanit", "--osd-color=#F6EAFF", "--osd-border-color=#160A2E",
            )
            if (start > 0) args += "--start=$start"
            if (headers.isNotEmpty()) args += "--http-header-fields=" + headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
            args += url
            val proc = ProcessBuilder(args).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            thread(isDaemon = true, name = "mpv-progress") { watch(pipe, proc, progress) }
            return
        }
        vlc?.let { exe ->
            val args = mutableListOf(exe.path, "--fullscreen", "--meta-title=$title")
            if (start > 0) args += "--start-time=$start"
            args += url
            ProcessBuilder(args).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            return
        }
        openWeb(url)
    }

    fun openWeb(url: String) {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
    }

    private val name = Regex("\"name\"\\s*:\\s*\"(time-pos|duration)\"")
    private val data = Regex("\"data\"\\s*:\\s*([0-9.eE+-]+)")

    /** Follows mpv's time-pos and duration over its control pipe until mpv closes. */
    private fun watch(pipe: String, proc: Process, progress: (ULong, ULong) -> Unit) {
        val conn = connect(pipe, proc) ?: return
        var pos = 0.0
        var dur = 0.0
        var lastSent = 0L
        fun send() {
            if (dur > 0) progress((pos * 1000).toULong(), (dur * 1000).toULong())
        }
        try {
            conn.out.write("{\"command\":[\"observe_property\",1,\"time-pos\"]}\n{\"command\":[\"observe_property\",2,\"duration\"]}\n".toByteArray())
            conn.out.flush()
            val reader = BufferedReader(InputStreamReader(conn.input, Charsets.UTF_8))
            while (true) {
                val line = reader.readLine() ?: break
                val n = name.find(line)?.groupValues?.get(1) ?: continue
                val v = data.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
                if (n == "time-pos") pos = v else dur = v
                val now = System.currentTimeMillis()
                if (now - lastSent >= 10_000) {
                    lastSent = now
                    send()
                }
            }
        } catch (_: Exception) {
            // mpv closed the pipe
        } finally {
            send()
            runCatching { conn.close() }
            if (!windows) File(pipe).delete()
        }
    }

    private class Conn(val input: InputStream, val out: OutputStream, val close: () -> Unit)

    /** mpv makes the pipe a moment after it starts; try for up to fifteen seconds. */
    private fun connect(pipe: String, proc: Process): Conn? {
        repeat(75) {
            if (!proc.isAlive) return null
            try {
                return if (windows) {
                    val f = RandomAccessFile(pipe, "rw")
                    Conn(
                        object : InputStream() {
                            override fun read() = f.read()
                            override fun read(b: ByteArray, off: Int, len: Int) = f.read(b, off, len)
                        },
                        object : OutputStream() {
                            override fun write(b: Int) = f.write(b)
                            override fun write(b: ByteArray, off: Int, len: Int) = f.write(b, off, len)
                        },
                    ) { f.close() }
                } else {
                    val ch = SocketChannel.open(StandardProtocolFamily.UNIX)
                    ch.connect(UnixDomainSocketAddress.of(pipe))
                    Conn(Channels.newInputStream(ch), Channels.newOutputStream(ch)) { ch.close() }
                }
            } catch (_: Exception) {
                Thread.sleep(200)
            }
        }
        return null
    }
}
