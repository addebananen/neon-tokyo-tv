package com.lumera.app.siri

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

/** What Neon Tokyo knows about the Siri Remote bridge, read over [SiriStatusServer]. */
data class SiriStatus(
    val running: Boolean,
    val state: String = "",
    val connected: Boolean = false,
    val remote: String = "",
    val battery: Int = -1,
    val problem: String = "",
    val scrollSpeed: Float = 1f,
    val momentum: Boolean = true,
    val wakeFromRemote: Boolean = true
)

/** The app's side of the line protocol to the bridge. */
object SiriRemoteClient {
    private suspend fun ask(command: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", SiriStatusServer.PORT), 400)
                s.soTimeout = 1_500
                s.getOutputStream().apply { write("$command\n".toByteArray()); flush() }
                s.getInputStream().bufferedReader().readLine()
            }
        }.getOrNull()
    }

    suspend fun status(): SiriStatus {
        val line = ask("status") ?: return SiriStatus(running = false)
        return runCatching {
            val j = JSONObject(line)
            val c = j.optJSONObject("config") ?: JSONObject()
            SiriStatus(
                running = true,
                state = j.optString("state"),
                connected = j.optBoolean("connected"),
                remote = j.optString("remote"),
                battery = j.optInt("battery", -1),
                problem = j.optString("problem"),
                scrollSpeed = c.optDouble("scrollSpeed", 1.0).toFloat(),
                momentum = c.optBoolean("momentum", true),
                wakeFromRemote = c.optBoolean("wakeFromRemote", true)
            )
        }.getOrDefault(SiriStatus(running = true))
    }

    suspend fun pair() = ask("pair") == "ok"
    suspend fun forget() = ask("forget") == "ok"
    suspend fun configure(vararg pairs: Pair<String, Any>) =
        ask("config " + pairs.joinToString(" ") { "${it.first}=${it.second}" }) == "ok"
}
