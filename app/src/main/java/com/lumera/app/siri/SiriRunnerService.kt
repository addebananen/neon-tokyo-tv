package com.lumera.app.siri

import android.util.Log

/**
 * Runs inside Shizuku's shell-uid process (Shizuku makes one of these for Neon). All it does is
 * run the one shell command Neon hands it — the bridge's launch loop — and return its output.
 * Shizuku only starts it after the owner has granted Neon access.
 */
class SiriRunnerService : ISiriRunner.Stub() {
    override fun run(command: String): String {
        Log.i("NeonSiriRunner", "running: $command")
        return runCatching {
            val p = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out
        }.getOrElse { it.message ?: it.toString() }
    }

    override fun destroy() {
        // Shizuku stops the process; nothing to clean up (the bridge it started lives on its own)
    }
}
