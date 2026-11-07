package com.lumera.app.ui.settings

import com.lumera.app.ui.theme.themed

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumera.app.overlay.NeonOverlayService
import com.lumera.app.remote_input.phone.PhoneRemote
import com.lumera.app.ui.addons.VoidButton
import com.lumera.app.ui.components.NeonPalette
import com.lumera.app.ui.components.NeonText
import com.lumera.app.ui.components.NeonType
import com.lumera.app.ui.components.neonPanel
import com.lumera.app.ui.profiles.PhotoStepRow
import com.lumera.app.ui.profiles.StatusLine
import com.lumera.app.ui.profiles.qrCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The phone remote: on or off, the QR code a phone scans, and forgetting the phones. */
@Composable
fun PhoneRemoteSettings(onGoBack: () -> Unit) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(PhoneRemote.isOn(context)) }
    var pairing by remember { mutableStateOf(false) }
    var forgotten by remember { mutableStateOf(false) }
    val phones by PhoneRemote.phones.collectAsState()

    SettingRow("Phone remote") {
        Column {
            Text(
                buildString {
                    append("Use your iPhone (or any phone) as the remote: swipe and tap to move, type and search from the phone's keyboard, or say what you want. ")
                    append("Scan the code once with the phone's camera; Neon Tokyo Web can open it too.")
                    if (on && phones > 0) append(if (phones == 1) " A phone has the remote open now." else " $phones phones have the remote open now.")
                    if (on && !NeonOverlayService.running) append(" Turn on \"Neon Tokyo overlays\" in Accessibility so the phone can steer other apps than Neon too.")
                    if (forgotten) append(" Done: every phone has to scan the code again.")
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = Color.White.copy(0.6f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            VoidSegmentedControl(
                options = if (on) listOf("Show the code" to 0, "Forget phones" to 1, "Turn off" to 2) else listOf("Turn on" to 3),
                selectedOption = -1,
                onOptionSelected = {
                    when (it) {
                        0 -> pairing = true
                        1 -> {
                            PhoneRemote.forgetPhones(context)
                            forgotten = true
                        }
                        2 -> {
                            PhoneRemote.setOn(context, false)
                            on = false
                        }
                        3 -> {
                            PhoneRemote.setOn(context, true)
                            on = true
                            pairing = true
                        }
                    }
                },
                onBack = onGoBack
            )
        }
    }
    if (pairing) PhoneRemoteDialog(onDismissRequest = { pairing = false })
    Spacer(Modifier.height(15.dp))
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.1f)))
    Spacer(Modifier.height(15.dp))
}

/** The QR code and the four-digit code a phone pairs with; the code only works while this is open. */
@Composable
fun PhoneRemoteDialog(onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var offline by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    val phones by PhoneRemote.phones.collectAsState()
    val startPhones = remember { PhoneRemote.phones.value }

    DisposableEffect(Unit) {
        pin = PhoneRemote.openPairing()
        onDispose { PhoneRemote.closePairing() }
    }
    LaunchedEffect(Unit) {
        PhoneRemote.ensureRunning()
        // The server may still be finding its port
        repeat(20) {
            val address = withContext(Dispatchers.IO) { PhoneRemote.pairUrl(context) }
            if (address != null && PhoneRemote.port > 0) {
                url = address
                qr = withContext(Dispatchers.Default) { qrCode(address) }?.asImageBitmap()
                return@LaunchedEffect
            }
            delay(250)
        }
        offline = true
    }
    // A phone just came: the dialog has done its job
    val joined = phones > startPhones
    LaunchedEffect(joined) {
        if (joined) {
            delay(1_600)
            onDismissRequest()
        }
    }

    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val doneRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(150)
            runCatching { doneRequester.requestFocus() }
        }
        Box(
            modifier = Modifier
                .width(880.dp)
                .neonPanel()
                .padding(horizontal = 40.dp, vertical = 36.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(236.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val code = qr
                        when {
                            code != null -> Image(bitmap = code, contentDescription = "QR code", modifier = Modifier.fillMaxSize())
                            offline -> Icon(Icons.Default.WifiOff, contentDescription = null, tint = NeonPalette.Night, modifier = Modifier.size(64.dp))
                            else -> CircularProgressIndicator(color = NeonPalette.Magenta, modifier = Modifier.size(44.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = url?.substringBefore("/#") ?: " ",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonPalette.Lilac
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    NeonText("PHONE REMOTE", style = NeonType.eyebrow().themed())
                    Spacer(Modifier.height(6.dp))
                    NeonText(if (offline) "No network" else "Your phone is the remote", style = NeonType.headline(30.sp).themed())
                    Spacer(Modifier.height(18.dp))
                    if (offline) {
                        Text("Connect the TV to your home network, then try again.", style = MaterialTheme.typography.bodyLarge, color = NeonPalette.Lilac)
                    } else {
                        PhotoStepRow(1, "Scan the code with the phone's camera", done = joined)
                        PhotoStepRow(2, "In Safari: Share › Add to Home Screen", done = false)
                        PhotoStepRow(3, "Swipe, tap, type or talk", done = false)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Or open the remote in Neon Tokyo Web and type the code ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeonPalette.Lilac.copy(alpha = 0.8f)
                            )
                            Text(
                                pin.chunked(1).joinToString(" "),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                                color = NeonPalette.Cyan
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        StatusLine(
                            text = when {
                                joined -> "Phone connected. Enjoy."
                                phones > 0 -> "A phone has the remote open. Waiting for another one…"
                                else -> "Waiting for your phone… it has to be on the same Wi-Fi as the TV."
                            },
                            color = NeonPalette.Cyan,
                            pulsing = !joined
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    VoidButton(text = "Done", onClick = onDismissRequest, modifier = Modifier.width(200.dp), focusRequester = doneRequester)
                }
            }
        }
    }
}
