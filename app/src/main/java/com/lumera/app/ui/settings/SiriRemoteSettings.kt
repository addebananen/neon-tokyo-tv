package com.lumera.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumera.app.siri.SiriRemoteClient
import com.lumera.app.siri.SiriStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The Apple TV Siri Remote: whether the bridge runs, pairing, and how scrolling feels. */
@Composable
fun SiriRemoteSettings(onGoBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(SiriStatus(running = false)) }
    var pairing by remember { mutableStateOf(false) }
    var voiceLast by remember { mutableStateOf<String?>(null) }
    // self-start after a reboot via Shizuku: (turned on, Shizuku running, Shizuku granted)
    var selfStart by remember { mutableStateOf(Triple(false, false, false)) }
    LaunchedEffect(Unit) {
        val voicePrefs = context.getSharedPreferences(com.lumera.app.siri.VoiceActivity.PREFS, android.content.Context.MODE_PRIVATE)
        while (true) {
            status = SiriRemoteClient.status()
            voiceLast = voicePrefs.getString(com.lumera.app.siri.VoiceActivity.LAST, null)
            selfStart = Triple(
                com.lumera.app.siri.ShizukuBridge.isEnabled(context),
                com.lumera.app.siri.ShizukuBridge.available(),
                com.lumera.app.siri.ShizukuBridge.granted()
            )
            if (status.connected) pairing = false
            delay(1_500L)
        }
    }

    SettingRow("Siri Remote") {
        Column {
            val text = when {
                !status.running ->
                    "Run the whole TV with an Apple TV Siri Remote: swipe to scroll like on an Apple TV, click to pick, Power turns the TV off and on. Start it from your PC with the TV setup script's siri step (once, and again after the TV restarts)."
                status.connected ->
                    "Connected to ${status.remote.ifEmpty { "your Siri Remote" }}" +
                        (if (status.battery >= 0) ", battery ${status.battery}%." else ".") +
                        " Swipe on the clickpad to move, click to pick. TV button: Home, twice for recent apps, hold for Control Center. Hold Back for Home. Hold Siri and talk: \"open YouTube\", \"play city pop on Spotify\" or just what to search for."
                pairing ->
                    "Hold Back and Volume Up on the remote for 5 seconds, close to the TV. It pairs by itself."
                status.problem.isNotEmpty() && status.state != "waiting" -> status.problem + "."
                else ->
                    "Press any button on the Siri Remote to wake it."
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = Color.White.copy(0.6f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            // what the last Siri press led to, so a voice search that went wrong can be read here
            val lastVoice = voiceLast
            if (lastVoice != null) {
                Text(
                    "Last voice search: $lastVoice",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = Color.White.copy(0.45f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            if (status.running) {
                VoidSegmentedControl(
                    options = listOf("Pair a remote" to 0, "TV Bluetooth settings" to 1, "Forget remote" to 2),
                    selectedOption = -1,
                    onOptionSelected = {
                        when (it) {
                            0 -> scope.launch { pairing = SiriRemoteClient.pair() }
                            1 -> runCatching {
                                context.startActivity(
                                    Intent("com.google.android.intent.action.CONNECT_INPUT")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                            2 -> scope.launch { SiriRemoteClient.forget() }
                        }
                    },
                    onBack = onGoBack
                )
                Spacer(Modifier.height(12.dp))
                VoidSegmentedControl(
                    options = listOf("Calmer scrolling" to 0.75f, "Apple TV" to 1.0f, "Quicker scrolling" to 1.35f),
                    selectedOption = listOf(0.75f, 1.0f, 1.35f).minByOrNull { kotlin.math.abs(it - status.scrollSpeed) } ?: 1.0f,
                    onOptionSelected = {
                        status = status.copy(scrollSpeed = it)
                        scope.launch { SiriRemoteClient.configure("scrollSpeed" to it) }
                    },
                    onBack = onGoBack
                )
                Spacer(Modifier.height(12.dp))
                VoidSegmentedControl(
                    options = listOf("Glide after a swipe" to true, "Stop on lift" to false),
                    selectedOption = status.momentum,
                    onOptionSelected = {
                        status = status.copy(momentum = it)
                        scope.launch { SiriRemoteClient.configure("momentum" to it) }
                    },
                    onBack = onGoBack
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Power on from the remote keeps the TV in a lighter sleep, so it can hear the remote. It uses a little more power while off.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = Color.White.copy(0.6f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                VoidSegmentedControl(
                    options = listOf("Power on from the remote" to true, "Off only" to false),
                    selectedOption = status.wakeFromRemote,
                    onOptionSelected = {
                        status = status.copy(wakeFromRemote = it)
                        scope.launch { SiriRemoteClient.configure("wakeFromRemote" to it) }
                    },
                    onBack = onGoBack
                )
                Spacer(Modifier.height(12.dp))
                val (selfOn, shizukuUp, shizukuOk) = selfStart
                Text(
                    "Start the remote again after the TV restarts, without a PC. This needs the free Shizuku app: install it, start it once on the TV, then turn this on and allow Neon when Shizuku asks." +
                        when {
                            !shizukuUp -> " Shizuku is not running yet."
                            !shizukuOk && selfOn -> " Waiting for you to allow Neon in Shizuku."
                            selfOn && shizukuOk -> " On: Neon will start the remote by itself."
                            else -> ""
                        },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = Color.White.copy(0.6f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                VoidSegmentedControl(
                    options = listOf("Start at restart (Shizuku)" to true, "Off" to false),
                    selectedOption = selfOn,
                    onOptionSelected = { on ->
                        com.lumera.app.siri.ShizukuBridge.setEnabled(context, on)
                        selfStart = selfStart.copy(first = on)
                        if (on) {
                            if (!com.lumera.app.siri.ShizukuBridge.granted()) com.lumera.app.siri.ShizukuBridge.requestPermission()
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) { com.lumera.app.siri.ShizukuBridge.startBridge(context) }
                        }
                    },
                    onBack = onGoBack
                )
            }
        }
    }
    Spacer(Modifier.height(15.dp))
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.1f)))
    Spacer(Modifier.height(15.dp))
}
