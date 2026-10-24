package com.lumera.app.ui.profiles

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.lumera.app.remote_input.AvatarServerManager
import com.lumera.app.remote_input.AvatarUploadServer
import com.lumera.app.remote_input.ServerInfo
import com.lumera.app.ui.addons.VoidButton
import com.lumera.app.ui.components.NeonPalette
import com.lumera.app.ui.components.NeonText
import com.lumera.app.ui.components.NeonType
import com.lumera.app.ui.components.neonPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A photo of yourself as your profile picture. The TV has no camera, so it shows a QR code:
 * the phone opens a page the TV serves, takes a selfie or picks a photo, fits it in the circle
 * and sends it. The photo then lights up here, and OK on the remote keeps it.
 */
@Composable
fun ProfilePhotoDialog(
    profileName: String,
    onDismissRequest: () -> Unit,
    onPhotoChosen: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serverManager = remember { AvatarServerManager() }
    var serverInfo by remember { mutableStateOf<ServerInfo?>(null) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var offline by remember { mutableStateOf(false) }
    var phoneConnected by remember { mutableStateOf(false) }
    var receiving by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<String?>(null) }
    var chosen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val info = serverManager.startServer(context, profileName, object : AvatarUploadServer.Listener {
            override fun onPhoneConnected() {
                phoneConnected = true
            }

            override fun onPhotoReceived(bytes: ByteArray) {
                receiving = true
                note = null
                scope.launch {
                    val ref = withContext(Dispatchers.IO) { ProfilePhotos.save(context, bytes) }
                    receiving = false
                    if (ref == null) {
                        serverManager.state = AvatarUploadServer.STATE_ERROR
                        note = "That photo couldn't be read. Try another one."
                    } else {
                        ProfilePhotos.delete(preview)
                        preview = ref
                        serverManager.state = AvatarUploadServer.STATE_PREVIEW
                    }
                }
            }
        })
        if (info == null) {
            offline = true
        } else {
            serverInfo = info
            qr = withContext(Dispatchers.Default) { qrCode(info.url) }?.asImageBitmap()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (chosen) {
                // A few more seconds for the phone to hear that the photo was taken
                serverManager.stopServer(graceMs = 4_000L)
            } else {
                serverManager.stopServer()
                ProfilePhotos.delete(preview)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(840.dp)
                .neonPanel()
                .padding(horizontal = 40.dp, vertical = 36.dp)
        ) {
            AnimatedContent(
                targetState = preview,
                transitionSpec = {
                    (fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 0.96f)) togetherWith fadeOut(tween(200))
                },
                label = "profile_photo_step"
            ) { shown ->
                if (shown != null) {
                    PhotoPreview(
                        avatarRef = shown,
                        profileName = profileName,
                        onUse = {
                            chosen = true
                            serverManager.state = AvatarUploadServer.STATE_DONE
                            onPhotoChosen(shown)
                        },
                        onRetry = {
                            ProfilePhotos.delete(shown)
                            preview = null
                            serverManager.state = AvatarUploadServer.STATE_RETRY
                        }
                    )
                } else {
                    PhotoWaiting(
                        serverInfo = serverInfo,
                        qr = qr,
                        offline = offline,
                        phoneConnected = phoneConnected,
                        receiving = receiving,
                        note = note,
                        onCancel = onDismissRequest
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoWaiting(
    serverInfo: ServerInfo?,
    qr: ImageBitmap?,
    offline: Boolean,
    phoneConnected: Boolean,
    receiving: Boolean,
    note: String?,
    onCancel: () -> Unit
) {
    val cancelRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { cancelRequester.requestFocus() }
    }

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
            NeonText("PROFILE PHOTO", style = NeonType.eyebrow())
            Spacer(Modifier.height(6.dp))
            NeonText(if (offline) "No network" else "Add your photo", style = NeonType.headline(32.sp))
            Spacer(Modifier.height(20.dp))
            if (offline) {
                Text(
                    "Connect the TV to your home network, then try again.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeonPalette.Lilac
                )
            } else {
                PhotoStepRow(1, "Scan the code with your phone's camera", done = phoneConnected)
                PhotoStepRow(2, "Take a selfie or pick a photo", done = receiving)
                PhotoStepRow(3, "Fit it in the circle and send it", done = receiving)
                Spacer(Modifier.height(14.dp))
                StatusLine(
                    text = when {
                        receiving -> "Getting your photo…"
                        note != null -> note
                        phoneConnected -> "Phone connected. Pick a photo there."
                        else -> "Waiting for your phone…"
                    },
                    color = if (note != null && !receiving) NeonPalette.Sun else NeonPalette.Cyan,
                    pulsing = !receiving
                )
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

@Composable
internal fun PhotoStepRow(number: Int, text: String, done: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        val ring = if (done) NeonPalette.Cyan else NeonPalette.Magenta.copy(alpha = 0.8f)
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (done) NeonPalette.Cyan.copy(alpha = 0.18f) else Color.Transparent)
                .border(1.5.dp, ring, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                Icon(Icons.Default.Check, contentDescription = null, tint = NeonPalette.Cyan, modifier = Modifier.size(18.dp))
            } else {
                Text("$number", style = MaterialTheme.typography.labelLarge, color = NeonPalette.Text)
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (done) NeonPalette.Text else NeonPalette.Lilac)
    }
}

@Composable
internal fun StatusLine(text: String, color: Color, pulsing: Boolean) {
    val transition = rememberInfiniteTransition(label = "photo_status")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "photo_status_pulse"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .graphicsLayer { alpha = if (pulsing) pulse else 1f }
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun PhotoPreview(
    avatarRef: String,
    profileName: String,
    onUse: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val useRequester = remember { FocusRequester() }
    LaunchedEffect(avatarRef) {
        delay(150)
        runCatching { useRequester.requestFocus() }
    }
    // The photo grows into the ring with a little bounce while the ring lights up
    val reveal = remember(avatarRef) { Animatable(0f) }
    LaunchedEffect(avatarRef) { reveal.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 140f)) }
    val spin = rememberInfiniteTransition(label = "photo_ring")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing)),
        label = "photo_ring_angle"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(44.dp)
    ) {
        Box(
            modifier = Modifier
                .size(268.dp)
                .drawBehind {
                    val lit = reveal.value.coerceIn(0f, 1f)
                    val radius = size.minDimension / 2
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.6f to NeonPalette.Magenta.copy(alpha = 0.42f * lit),
                            1f to Color.Transparent,
                            radius = radius
                        ),
                        radius = radius
                    )
                    rotate(angle) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(NeonPalette.Magenta, NeonPalette.Cyan, NeonPalette.Sun, NeonPalette.Magenta)
                            ),
                            radius = 112.dp.toPx(),
                            alpha = lit,
                            style = Stroke(width = 5.dp.toPx())
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(ProfileAssets.getAvatarSource(avatarRef))
                    .size(512, 512)
                    .build(),
                contentDescription = "Your photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(212.dp)
                    .graphicsLayer {
                        val grow = reveal.value
                        scaleX = 0.82f + 0.18f * grow
                        scaleY = 0.82f + 0.18f * grow
                        alpha = grow.coerceIn(0f, 1f)
                    }
                    .clip(CircleShape)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            NeonText("PROFILE PHOTO", style = NeonType.eyebrow())
            Spacer(Modifier.height(6.dp))
            NeonText(
                text = if (profileName.isBlank()) "Looking good!" else "Looking good, $profileName!",
                style = NeonType.headline(32.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "This is you on Neon Tokyo: on the profile screen and in the menu.",
                style = MaterialTheme.typography.bodyLarge,
                color = NeonPalette.Lilac
            )
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                VoidButton(
                    text = "Use This Photo",
                    onClick = onUse,
                    isPrimary = true,
                    modifier = Modifier.width(220.dp),
                    focusRequester = useRequester
                )
                VoidButton(
                    text = "Try Another",
                    onClick = onRetry,
                    modifier = Modifier.width(200.dp)
                )
            }
        }
    }
}

/** Dark modules on white, with a narrow margin: the dialog's white frame is the rest of the quiet zone. */
internal fun qrCode(url: String, size: Int = 480): Bitmap? = try {
    val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val width = matrix.width
    val height = matrix.height
    val dark = 0xFF0D0721.toInt()
    val light = 0xFFFFFFFF.toInt()
    val pixels = IntArray(width * height) { i -> if (matrix[i % width, i / width]) dark else light }
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
} catch (e: Exception) {
    if (com.lumera.app.BuildConfig.DEBUG) android.util.Log.w("ProfilePhotoDialog", "QR generation error", e)
    null
}
