package com.lumera.app.ui.settings

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumera.app.dream.DreamVideo
import com.lumera.app.dream.DreamVideoShrink
import com.lumera.app.dream.NeonDreamPreviewActivity
import com.lumera.app.dream.NeonDreamStyle
import com.lumera.app.remote_input.DreamVideoServerManager
import com.lumera.app.remote_input.DreamVideoUploadServer
import com.lumera.app.remote_input.ServerInfo
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Your own video as the screensaver. Like the profile photo, the phone does the picking: it
 * opens the page behind the QR code and sends the video, which arrives here with a progress bar.
 * Once the TV has checked it plays, a frame of it lights up and it is the screensaver.
 */
@Composable
fun DreamVideoDialog(
    onDismissRequest: () -> Unit,
    onVideoSet: () -> Unit,
    /** Where the video goes: the one screensaver video, or a home profile's own. */
    target: File? = null,
    /** A home profile's background rather than a screensaver: the words follow, the screensaver stays. */
    background: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serverManager = remember { DreamVideoServerManager() }
    var serverInfo by remember { mutableStateOf<ServerInfo?>(null) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var offline by remember { mutableStateOf(false) }
    var phoneConnected by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var checking by remember { mutableStateOf(false) }
    var shrinking by remember { mutableStateOf<Float?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(Unit) {
        val info = serverManager.startServer(context, object : DreamVideoUploadServer.Listener {
            override fun onPhoneConnected() {
                phoneConnected = true
            }

            override fun onProgress(received: Long, total: Long) {
                progress = received.toFloat() / total
                note = null
            }

            override fun onVideoReceived(file: File) {
                checking = true
                scope.launch {
                    // A phone's film is made lighter first, so it doesn't fill the TV's storage
                    var upload = file
                    if (withContext(Dispatchers.IO) { DreamVideoShrink.needed(file) }) {
                        shrinking = 0f
                        val small = File(file.parentFile, "shrunk.mp4")
                        if (DreamVideoShrink.shrink(context, file, small) { shrinking = it }) {
                            file.delete()
                            upload = small
                        }
                        shrinking = null
                    }
                    val shown: Bitmap? = withContext(Dispatchers.IO) { DreamVideo.install(context, upload, target ?: DreamVideo.file(context)) }
                    checking = false
                    progress = null
                    if (shown == null) {
                        serverManager.state = DreamVideoUploadServer.STATE_ERROR
                        note = "The TV can't play that video. Try another one."
                    } else {
                        // A profile's own video plays when the screensaver follows the home style
                        if (!background) NeonDreamStyle.set(context, if (target == null) NeonDreamStyle.VIDEO else NeonDreamStyle.HOME)
                        serverManager.state = DreamVideoUploadServer.STATE_DONE
                        frame = shown.asImageBitmap()
                        onVideoSet()
                    }
                }
            }

            override fun onUploadFailed() {
                progress = null
                note = "The video stopped arriving. Send it again from your phone."
            }
        }, background)
        if (info == null) {
            offline = true
        } else {
            serverInfo = info
            qr = withContext(Dispatchers.Default) { qrCode(info.url) }?.asImageBitmap()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // A few more seconds for the phone to hear that the video is on
            serverManager.stopServer(graceMs = if (frame != null) 4_000L else 0L)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(860.dp)
                .neonPanel()
                .padding(horizontal = 40.dp, vertical = 36.dp)
        ) {
            AnimatedContent(
                targetState = frame,
                transitionSpec = {
                    (fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 0.96f)) togetherWith fadeOut(tween(200))
                },
                label = "dream_video_step"
            ) { shown ->
                if (shown != null) {
                    VideoReady(
                        frame = shown,
                        background = background,
                        onPreview = {
                            context.startActivity(Intent(context, NeonDreamPreviewActivity::class.java))
                        },
                        onDone = onDismissRequest
                    )
                } else {
                    VideoWaiting(
                        background = background,
                        serverInfo = serverInfo,
                        qr = qr,
                        offline = offline,
                        phoneConnected = phoneConnected,
                        progress = progress,
                        checking = checking,
                        shrinking = shrinking,
                        note = note,
                        onCancel = onDismissRequest
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoWaiting(
    background: Boolean,
    serverInfo: ServerInfo?,
    qr: ImageBitmap?,
    offline: Boolean,
    phoneConnected: Boolean,
    progress: Float?,
    checking: Boolean,
    shrinking: Float?,
    note: String?,
    onCancel: () -> Unit
) {
    val cancelRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { cancelRequester.requestFocus() }
    }
    val arriving = progress != null || checking

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(40.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(236.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    qr != null -> Image(bitmap = qr, contentDescription = "QR code", modifier = Modifier.fillMaxSize())
                    offline -> Icon(Icons.Default.WifiOff, contentDescription = null, tint = NeonPalette.Night, modifier = Modifier.size(64.dp))
                    else -> CircularProgressIndicator(color = NeonPalette.Magenta, modifier = Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = serverInfo?.url ?: " ",
                style = MaterialTheme.typography.bodySmall,
                color = NeonPalette.Lilac
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            NeonText(if (background) "HOME BACKGROUND" else "SCREENSAVER", style = NeonType.eyebrow())
            Spacer(Modifier.height(6.dp))
            NeonText(if (offline) "No network" else "Your own video", style = NeonType.headline(32.sp))
            Spacer(Modifier.height(20.dp))
            if (offline) {
                Text(
                    "Connect the TV to your home network, then try again.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeonPalette.Lilac
                )
            } else {
                PhotoStepRow(1, "Scan the code with your phone's camera", done = phoneConnected)
                PhotoStepRow(2, "Choose a video", done = arriving)
                PhotoStepRow(3, "Send it to the TV", done = checking)
                Spacer(Modifier.height(14.dp))
                if (progress != null && !checking) {
                    UploadBar(progress)
                } else {
                    StatusLine(
                        text = when {
                            shrinking != null -> "Making it lighter for the TV…  ${(shrinking * 100).toInt()}%"
                            checking -> "Checking that it plays…"
                            note != null -> note
                            phoneConnected -> "Phone connected. Choose a video there."
                            else -> "Waiting for your phone…"
                        },
                        color = if (note != null && !checking) NeonPalette.Sun else NeonPalette.Cyan,
                        pulsing = true
                    )
                }
            }
            Spacer(Modifier.height(26.dp))
            VoidButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.width(200.dp),
                focusRequester = cancelRequester
            )
        }
    }
}

/** The video arriving: a neon tube filling from magenta to cyan, with the share that is here. */
@Composable
private fun UploadBar(progress: Float) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(250), label = "dream_video_upload")
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(NeonPalette.Lilac.copy(alpha = 0.25f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(shown)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(NeonPalette.Magenta, NeonPalette.Cyan)))
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Getting your video… ${(shown * 100).toInt()} %",
            style = MaterialTheme.typography.bodyMedium,
            color = NeonPalette.Cyan
        )
    }
}

@Composable
private fun VideoReady(
    frame: ImageBitmap,
    background: Boolean,
    onPreview: () -> Unit,
    onDone: () -> Unit
) {
    val previewRequester = remember { FocusRequester() }
    LaunchedEffect(frame) {
        delay(150)
        runCatching { previewRequester.requestFocus() }
    }
    // The frame settles into its screen with a little bounce while the screen's glow comes up
    val reveal = remember(frame) { Animatable(0f) }
    LaunchedEffect(frame) { reveal.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 140f)) }
    val screen = RoundedCornerShape(14.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(40.dp)
    ) {
        Box(
            modifier = Modifier
                .width(340.dp)
                .height(191.dp)
                .drawBehind {
                    val lit = reveal.value.coerceIn(0f, 1f)
                    drawRect(
                        brush = Brush.radialGradient(
                            0.55f to NeonPalette.Cyan.copy(alpha = 0.32f * lit),
                            1f to Color.Transparent,
                            radius = size.maxDimension * 0.62f
                        ),
                        topLeft = Offset(-size.width * 0.1f, -size.height * 0.3f),
                        size = Size(size.width * 1.2f, size.height * 1.6f)
                    )
                }
                .graphicsLayer {
                    val grow = reveal.value
                    scaleX = 0.86f + 0.14f * grow
                    scaleY = 0.86f + 0.14f * grow
                    alpha = grow.coerceIn(0f, 1f)
                }
                .clip(screen)
                .background(Color.Black)
                .border(1.5.dp, NeonPalette.Cyan.copy(alpha = 0.8f), screen)
        ) {
            Image(
                bitmap = frame,
                contentDescription = "Your video",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            NeonText(if (background) "HOME BACKGROUND" else "SCREENSAVER", style = NeonType.eyebrow())
            Spacer(Modifier.height(6.dp))
            NeonText("Your video is on", style = NeonType.headline(32.sp))
            Spacer(Modifier.height(12.dp))
            Text(
                if (background) "It loops without sound behind your tiles, and rests while an app is open."
                else "It plays on a loop, without sound, whenever the TV rests.",
                style = MaterialTheme.typography.bodyLarge,
                color = NeonPalette.Lilac
            )
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // A background shows as soon as the dialog closes, so it has nothing to preview
                if (!background) {
                    VoidButton(
                        text = "Preview",
                        onClick = onPreview,
                        isPrimary = true,
                        modifier = Modifier.width(200.dp),
                        focusRequester = previewRequester
                    )
                }
                VoidButton(
                    text = "Done",
                    onClick = onDone,
                    isPrimary = background,
                    modifier = Modifier.width(160.dp),
                    focusRequester = if (background) previewRequester else null
                )
            }
        }
    }
}
