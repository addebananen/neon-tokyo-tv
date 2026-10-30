package com.lumera.app.siri

import java.net.InetSocketAddress
import java.net.Socket

/**
 * A blocking one-line request to the bridge's status server ([SiriStatusServer]) on localhost,
 * for the boot and Shizuku paths that run off the main thread. [SiriRemoteClient] is the
 * coroutine version the settings page uses.
 */
object StatusLink {
    fun ask(command: String, timeoutMs: Int): String? = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress("127.0.0.1", SiriStatusServer.PORT), 800)
            s.soTimeout = timeoutMs
            s.getOutputStream().apply { write("$command\n".toByteArray()); flush() }
            s.getInputStream().bufferedReader().readLine()
        }
    }.getOrNull()
}
