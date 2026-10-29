package com.lumera.app.siri

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * A line protocol on 127.0.0.1:[PORT] between the bridge (shell user) and Neon Tokyo's settings
 * page (the app), which cannot share files or binder calls without help.
 *
 *   status                 -> one line of JSON
 *   pair | forget | stop   -> ok
 *   voice                  -> the microphone of the current Siri press, `f <hex>` lines, then `end`
 *   config k=v k=v ...     -> ok
 *
 * Only the TV itself can reach it, and nothing it accepts presses keys.
 */
object SiriStatusServer {
    const val PORT = 47391

    fun start(bridge: SiriBridge): Boolean {
        val server = runCatching { ServerSocket(PORT, 4, InetAddress.getByName("127.0.0.1")) }.getOrNull()
            ?: return false
        thread(name = "siri-status", isDaemon = true) {
            while (true) {
                val socket = runCatching { server.accept() }.getOrNull() ?: continue
                thread(isDaemon = true) { serve(bridge, socket) }
            }
        }
        return true
    }

    private fun serve(bridge: SiriBridge, socket: Socket) = runCatching {
        socket.soTimeout = 30_000
        socket.use { s ->
            val reader = s.getInputStream().bufferedReader()
            val writer = s.getOutputStream().bufferedWriter()
            while (true) {
                val line = reader.readLine()?.trim() ?: break
                val reply = when (val cmd = line.substringBefore(' ')) {
                    "status" -> status(bridge)
                    "pair" -> { bridge.pair(); "ok" }
                    "voice" -> {
                        VoiceRelay.stream { writer.write(it); writer.newLine(); writer.flush() }
                        continue
                    }
                    "forget" -> { bridge.forget(); "ok" }
                    "stop" -> { bridge.stop(); "ok" }
                    "config" -> {
                        val pairs = line.removePrefix(cmd).trim().split(' ')
                            .mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                            .toMap()
                        bridge.configure(pairs)
                        "ok"
                    }
                    else -> "error unknown command"
                }
                writer.write(reply)
                writer.newLine()
                writer.flush()
            }
        }
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun status(b: SiriBridge): String =
        """{"version":${SiriBridge.VERSION},"state":"${esc(b.linkState)}","connected":${b.connected},""" +
            """"remote":"${esc(b.remoteName)}","battery":${b.battery},"keys":${b.keysSent},""" +
            """"problem":"${esc(b.problem)}","config":${b.config.toJson()}}"""
}
