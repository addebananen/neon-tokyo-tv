package com.neontokyo.desktop.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uniffi.neon_core.UpdateInfo
import uniffi.neon_core.pickUpdate
import uniffi.neon_core.releasesUrl
import java.io.File
import kotlin.system.exitProcess

/**
 * Updates from the app itself: looks at the GitHub releases when the app starts and every
 * six hours, and when a newer Windows installer is out, downloads it and installs it over this
 * one (the installer keeps the same upgrade id, so settings and history stay), then opens the
 * new version.
 */
class Updater(private val scope: CoroutineScope) {
    var available by mutableStateOf<UpdateInfo?>(null)
        private set
    /** 0..1 while downloading, null otherwise. */
    var progress by mutableStateOf<Float?>(null)
        private set
    var status by mutableStateOf("")
        private set
    var checking by mutableStateOf(false)
        private set

    private val windows = System.getProperty("os.name").startsWith("Windows")

    fun start() {
        scope.launch {
            delay(4_000)
            while (isActive) {
                check()
                delay(6 * 3600_000L)
            }
        }
    }

    suspend fun check() {
        if (checking) return
        checking = true
        try {
            val a = Net.call(releasesUrl(), headers = mapOf("Accept" to "application/vnd.github+json"))
            if (!a.ok) {
                status = "GitHub answered ${a.status}"
                return
            }
            available = pickUpdate(a.body, listOf(".msi"), AppInfo.version, AppInfo.builtAtMs)
            status = if (available == null) "You have the newest version" else ""
        } catch (e: Exception) {
            status = "Could not reach GitHub"
        } finally {
            checking = false
        }
    }

    fun install() {
        val u = available ?: return
        if (progress != null) return
        scope.launch {
            try {
                progress = 0f
                val file = File(System.getProperty("java.io.tmpdir"), u.assetName)
                Net.download(u.assetUrl, file) { p -> scope.launch { progress = p } }
                progress = 1f
                status = "Installing…"
                if (!windows) {
                    status = "Downloaded to ${file.path}"
                    progress = null
                    return@launch
                }
                // msiexec installs over this version; then the new one opens
                val app = System.getProperty("jpackage.app-path")
                val reopen = if (app != null) " && start \"\" \"$app\"" else ""
                ProcessBuilder("cmd", "/c", "msiexec /i \"${file.path}\" /passive /norestart$reopen").start()
                delay(600)
                exitProcess(0)
            } catch (e: Exception) {
                progress = null
                status = "Download failed: ${e.message ?: "try again"}"
            }
        }
    }
}
