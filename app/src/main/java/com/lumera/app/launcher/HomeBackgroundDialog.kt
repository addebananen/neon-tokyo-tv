package com.lumera.app.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumera.app.remote_input.AvatarServerManager
import com.lumera.app.remote_input.AvatarUploadServer
import com.lumera.app.remote_input.ServerInfo
import com.lumera.app.ui.profiles.qrCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A picture of your own behind the home screen, sent from the phone: the TV shows a QR code,
 * the phone picks a photo and chooses the part that fills the screen, and it shows here to keep
 * or send again.
 */
@Composable
fun HomeBackgroundDialog(profileName: String, profiles: HomeProfiles, onDismiss: () -> Unit, onChosen: (String) -> Unit) {
    val context = LocalContext.current
    val look = LocalHomeLook.current
    val scope = rememberCoroutineScope()
    val server = remember { AvatarServerManager() }
    var info by remember { mutableStateOf<ServerInfo?>(null) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var offline by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<File?>(null) }
    var previewImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var chosen by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val started = server.startServer(context, profileName, object : AvatarUploadServer.Listener {
            override fun onPhoneConnected() {
                connected = true
            }

            override fun onPhotoReceived(bytes: ByteArray) {
                scope.launch {
                    val file = profiles.newFile("background")
                    val image = withContext(Dispatchers.IO) {
                        runCatching {
                            file.writeBytes(bytes)
                            // A 4K picture is shown here at the size it takes on the screen
                            decodeSampled(file, 1080)
                        }.getOrNull()
                    }
                    if (image == null) {
                        file.delete()
                        server.state = AvatarUploadServer.STATE_ERROR
                        note = "That picture couldn't be read. Try another one."
                    } else {
                        preview?.delete()
                        preview = file
                        previewImage = image
                        note = null
                        server.state = AvatarUploadServer.STATE_PREVIEW
                    }
                }
            }
        }, wide = true)
        if (started == null) offline = true else {
            info = started
            qr = withContext(Dispatchers.Default) { qrCode(started.url) }?.asImageBitmap()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (chosen) server.stopServer(graceMs = 4_000L) else {
                server.stopServer()
                preview?.delete()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(previewImage != null) {
            withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
        Row(
            modifier = Modifier
                .width(760.dp)
                .homePanel(look, RoundedCornerShape(18.dp))
                .padding(28.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shown = previewImage
            if (shown != null) {
                Image(
                    shown,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(400.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    qr?.let { Image(it, contentDescription = "QR code", modifier = Modifier.fillMaxSize()) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PanelNote("YOUR OWN BACKGROUND", look)
                PanelTitle(if (shown != null) "Like it?" else if (offline) "No network" else "Send a picture", look)
                BasicText(
                    when {
                        shown != null -> "This fills the home screen for $profileName instead of the style's painting."
                        offline -> "Connect the TV to your home network, then try again."
                        note != null -> note!!
                        connected -> "Phone connected. Pick a picture there."
                        else -> "Scan the code with your phone's camera, pick a photo and choose the part that shows."
                    },
                    style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 13.sp, color = look.dim)
                )
                info?.takeIf { shown == null }?.let { PanelNote(it.url, look) }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (shown != null) {
                        PanelButton("Use it", look, primary = true, modifier = Modifier.focusRequester(focus)) {
                            val file = preview ?: return@PanelButton
                            chosen = true
                            server.state = AvatarUploadServer.STATE_DONE
                            preview = null
                            onChosen(file.absolutePath)
                        }
                        PanelButton("Try another", look) {
                            preview?.delete()
                            preview = null
                            previewImage = null
                            server.state = AvatarUploadServer.STATE_RETRY
                        }
                    } else {
                        PanelButton("Cancel", look, modifier = Modifier.focusRequester(focus), onClick = onDismiss)
                    }
                }
            }
        }
    }
}
