package com.lumera.app.launcher

import android.widget.Toast
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.lumera.app.ui.components.NeonText

/** "AirPods turned on": the name, cut short when it is long. */
internal fun btTitle(name: String): String = "${name.take(24).trimEnd()} turned on"

/** What Connect told the person, when it did not just connect. */
internal fun btConnectNote(result: BtConnect, name: String, disconnect: Boolean = false): String? = when (result) {
    BtConnect.Direct -> if (disconnect) "Disconnecting $name…" else "Connecting $name…"
    BtConnect.Helper -> null
    BtConnect.Settings -> if (disconnect) "Pick $name and press Disconnect" else "Pick $name and press Connect"
    BtConnect.Failed -> "Couldn't reach $name"
}

/**
 * The card of a paired device that was just switched on near the TV, in the corner. It takes the
 * focus, so OK connects at once; Back, "Not now" or the time running out put it away.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun BluetoothCard(
    device: BtDevice,
    onConnect: () -> Unit,
    onDismiss: (hadFocus: Boolean, pressed: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val look = LocalHomeLook.current
    val color = look.accent2
    val shape = RoundedCornerShape(16.dp)
    val connect = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)

    // The mark flickers on like a neon tube while its waves start going out
    val mark = remember(device.address) { Animatable(0f) }
    val left = remember(device.address) { Animatable(1f) }
    LaunchedEffect(device.address) {
        mark.animateTo(1f, keyframes {
            durationMillis = 900
            0f at 120
            0.9f at 200
            0.2f at 270
            1f at 390
            0.6f at 500
            1f at 900
        })
    }
    LaunchedEffect(device.address) {
        for (attempt in 0 until 10) {
            withFrameNanos { }
            if (focused || runCatching { connect.requestFocus() }.isSuccess) break
        }
        left.snapTo(1f)
        left.animateTo(0f, tween(BtRules.SHOW_MS, easing = LinearEasing))
        dismiss(focused, false)
    }
    // Waves of signal going out from the device, three at a time, as it calls for the TV
    val wave by rememberInfiniteTransition(label = "BtWave").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2100, easing = LinearEasing), RepeatMode.Restart),
        label = "BtWaveValue"
    )
    BackHandler(enabled = focused) { dismiss(true, true) }

    Column(
        modifier = modifier
            .width(320.dp)
            .homePanel(look, shape)
            .onFocusChanged { focused = it.hasFocus }
            .focusGroup()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                SignalWaves(color, progress = { wave }, lit = { mark.value })
                NeonGlyph(device.kind.glyph, color, Modifier.size(30.dp).graphicsLayer { alpha = mark.value })
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                NeonText(
                    text = btTitle(device.name),
                    style = TextStyle(
                        fontFamily = look.font,
                        fontWeight = if (look.glowingText) FontWeight.SemiBold else look.weight,
                        fontStyle = look.fontStyle,
                        fontSize = 16.sp,
                        color = look.text,
                        shadow = if (look.glowingText) Shadow(color.copy(alpha = 0.7f), blurRadius = 16f) else null
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    glow = { mark.value }
                )
                BasicText(
                    text = "Bluetooth · nearby",
                    maxLines = 1,
                    style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 11.sp, color = look.dim)
                )
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    WakeButton("Connect", color, primary = true, onClick = onConnect, modifier = Modifier.focusRequester(connect))
                    WakeButton("Not now", color, primary = false, onClick = { dismiss(true, true) })
                }
            }
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 9.dp)
                .height(2.dp)
                .graphicsLayer {
                    scaleX = left.value
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .drawBehind {
                    drawRect(Brush.horizontalGradient(listOf(color.copy(alpha = 0.15f), color, lerp(color, Color.White, 0.5f))))
                }
        )
    }
}

/** Rings going out from the middle and fading as they go: drawn only, no layout per frame. */
@Composable
private fun SignalWaves(color: Color, progress: () -> Float, lit: () -> Float) {
    Spacer(
        Modifier
            .size(64.dp)
            .drawWithCache {
                val line = Stroke(2.dp.toPx())
                val inner = 15.dp.toPx()
                val outer = size.minDimension / 2 - 1.dp.toPx()
                val soft = Brush.radialGradient(
                    0f to color.copy(alpha = 0.3f),
                    1f to Color.Transparent,
                    center = Offset(size.width / 2, size.height / 2),
                    radius = size.minDimension / 2
                )
                onDrawBehind {
                    val l = lit()
                    if (l <= 0f) return@onDrawBehind
                    drawCircle(soft, alpha = l)
                    for (k in 0 until 3) {
                        val t = (progress() + k / 3f) % 1f
                        drawCircle(color, radius = inner + (outer - inner) * t, alpha = (1f - t) * 0.8f * l, style = line)
                    }
                }
            }
    )
}

/**
 * The Bluetooth button's panel: the paired devices that play sound, which of them are on, and
 * which belong to the profile at home. OK on a device connects it; the second button takes it as
 * this profile's, so its card shows here and on no one else's home screen.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun HomeBluetoothDialog(
    bt: HomeBluetooth,
    profile: HomeProfile,
    profiles: List<HomeProfile>,
    onConnect: (BtDevice) -> Unit,
    onDismiss: () -> Unit
) {
    val look = LocalHomeLook.current
    val context = LocalContext.current
    val devices by bt.devices.collectAsState()
    val nearby by bt.nearby.collectAsState()
    val owners by bt.owners.collectAsState()
    val on by bt.on.collectAsState()
    val permitted by bt.permitted.collectAsState()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { bt.permissionChanged() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { bt.refresh() }
    LaunchedEffect(permitted, on, devices.isEmpty()) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    val names = profiles.associate { it.id to it.name }
    var showOthers by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(460.dp)
                    .panelIn().homePanel(look, RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeonGlyph(Glyph.Bluetooth, look.accent2, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    NeonText(
                        text = "Bluetooth",
                        style = TextStyle(
                            fontFamily = look.font,
                            fontWeight = if (look.glowingText) FontWeight.SemiBold else look.weight,
                            fontStyle = look.fontStyle,
                            fontSize = 18.sp,
                            color = look.text,
                            shadow = if (look.glowingText) Shadow(look.accent.copy(alpha = 0.6f), blurRadius = 16f) else null
                        )
                    )
                    Spacer(Modifier.weight(1f))
                    BasicText(
                        "${profile.name}'s devices",
                        style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 11.sp, color = look.dim)
                    )
                }
                val note = when {
                    !bt.available -> "This TV has no Bluetooth."
                    !permitted -> "Neon Tokyo needs Android's OK to see the TV's Bluetooth devices."
                    !on -> "Bluetooth is off on the TV."
                    devices.isEmpty() -> "No headphones or speakers are paired yet. Pair one in the TV's settings, then it shows here."
                    else -> null
                }
                if (note != null) {
                    BasicText(note, style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 12.5.sp, color = look.text))
                }
                // Someone else's headphones stay out of the way unless asked for
                val theirs = devices.filter { d -> owners[d.address].orEmpty().let { it.isNotEmpty() && profile.id !in it } }
                val shown = if (showOthers) devices else devices - theirs.toSet()
                if (bt.available && permitted && on) {
                    shown.forEachIndexed { index, device ->
                        val who = owners[device.address].orEmpty()
                        val mine = profile.id in who
                        val others = who.filter { it != profile.id }.mapNotNull { names[it] }.joinToString(", ")
                        DeviceRow(
                            device = device,
                            status = when {
                                device.connected -> "Connected · press to disconnect"
                                device.address in nearby -> "On, nearby"
                                mine -> "Off or away"
                                else -> null
                            },
                            lit = device.connected || device.address in nearby,
                            owners = when {
                                others.isEmpty() -> null
                                mine -> "also $others's"
                                else -> "$others's"
                            },
                            mine = mine,
                            onClick = {
                                if (!mine) bt.take(device.address, profile.id, true)
                                // A device that is connected lets go, and the sound comes back to the TV
                                if (device.connected) {
                                    btConnectNote(bt.connect(device.address, disconnect = true), device.name, disconnect = true)
                                        ?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                                } else {
                                    onConnect(device)
                                }
                            },
                            onMine = { bt.take(device.address, profile.id, !mine) },
                            modifier = if (index == 0) Modifier.focusRequester(first) else Modifier
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (bt.available && permitted && on && theirs.isNotEmpty()) {
                        UpdateButton(
                            if (showOthers) "Hide others' devices" else "Others' devices (${theirs.size})",
                            if (shown.isEmpty()) Modifier.focusRequester(first) else Modifier
                        ) { showOthers = !showOthers }
                    }
                    if (bt.available && !permitted) {
                        UpdateButton("Allow", Modifier.focusRequester(first)) {
                            if (Build.VERSION.SDK_INT >= 31) ask.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    }
                    if (bt.available) {
                        UpdateButton(
                            if (on && permitted) "Pair a new device" else "Bluetooth settings",
                            if (permitted && (!on || devices.isEmpty())) Modifier.focusRequester(first) else Modifier
                        ) { HomeApps.start(context, BluetoothHelper.settingsIntent(context)) }
                    }
                    UpdateButton("Close", if (!bt.available) Modifier.focusRequester(first) else Modifier, onClick = onDismiss)
                }
                if (bt.available && permitted && devices.isNotEmpty()) {
                    BasicText(
                        "Devices marked as yours pop up in the corner when they're switched on, on your home screen only." +
                            if (Build.VERSION.SDK_INT >= 33 && !BluetoothHelper.available(context)) " This TV connects from its own Bluetooth page; Connect opens it." else "",
                        style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 10.5.sp, color = look.dim),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DeviceRow(
    device: BtDevice,
    status: String?,
    lit: Boolean,
    owners: String?,
    mine: Boolean,
    onClick: () -> Unit,
    onMine: () -> Unit,
    modifier: Modifier = Modifier
) {
    val look = LocalHomeLook.current
    val shape = RoundedCornerShape(9.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onClick,
            modifier = modifier.weight(1f).heightIn(min = 42.dp),
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = look.text.copy(alpha = 0.08f),
                contentColor = look.text,
                focusedContainerColor = look.accent.copy(alpha = 0.2f),
                focusedContentColor = look.text
            ),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(1.5.dp, look.accent), shape = shape))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeonGlyph(device.kind.glyph, if (lit) look.accent2 else look.dim, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(
                        device.name,
                        style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 13.sp, color = look.text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val detail = listOfNotNull(status, owners).joinToString(" · ")
                    if (detail.isNotEmpty()) {
                        BasicText(
                            detail,
                            style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 10.5.sp, color = if (lit) look.accent2 else look.dim),
                            maxLines = 1
                        )
                    }
                }
                if (lit) {
                    Box(Modifier.size(7.dp).background(look.accent2, CircleShape))
                }
            }
        }
        UpdateButton(if (mine) "Mine ✓" else "Make mine", onClick = onMine)
    }
}

/**
 * The Bluetooth corner, opened with Down in the app switcher (Home twice): this profile's devices,
 * each with its mark and Connect (Disconnect once connected), for the times a device was on but its card never came. The first device
 * takes the focus; Back or a while without a press put it away.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun BluetoothCorner(
    devices: List<BtDevice>,
    nearby: Set<String>,
    note: String?,
    onConnect: (BtDevice) -> Unit,
    onAll: () -> Unit,
    onDismiss: (hadFocus: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val look = LocalHomeLook.current
    val color = look.accent2
    val shape = RoundedCornerShape(16.dp)
    val first = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)
    // Each press starts the time again
    var presses by remember { mutableStateOf(0) }
    val left = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        for (attempt in 0 until 10) {
            withFrameNanos { }
            if (focused || runCatching { first.requestFocus() }.isSuccess) break
        }
    }
    LaunchedEffect(presses) {
        left.snapTo(1f)
        left.animateTo(0f, tween(BtRules.CORNER_MS, easing = LinearEasing))
        dismiss(focused)
    }
    BackHandler(enabled = focused) { dismiss(true) }

    Column(
        modifier = modifier
            .width(330.dp)
            .homePanel(look, shape)
            .onFocusChanged { focused = it.hasFocus }
            .onPreviewKeyEvent {
                presses++
                false
            }
            .focusGroup()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) {
            NeonGlyph(Glyph.Bluetooth, color, Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            NeonText(
                text = "Bluetooth",
                style = TextStyle(
                    fontFamily = look.font,
                    fontWeight = if (look.glowingText) FontWeight.SemiBold else look.weight,
                    fontStyle = look.fontStyle,
                    fontSize = 15.sp,
                    color = look.text,
                    shadow = if (look.glowingText) Shadow(color.copy(alpha = 0.6f), blurRadius = 14f) else null
                )
            )
        }
        if (note != null) {
            BasicText(
                note,
                style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 11.5.sp, color = look.dim),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        devices.take(4).forEachIndexed { index, device ->
            val lit = device.connected || device.address in nearby
            CornerRow(
                device = device,
                status = when {
                    device.connected -> "Connected"
                    device.address in nearby -> "On, nearby"
                    else -> null
                },
                lit = lit,
                onClick = {
                    presses++
                    onConnect(device)
                },
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier
            )
        }
        Row(Modifier.padding(top = 2.dp, start = 2.dp)) {
            WakeButton(
                "All devices",
                color,
                primary = false,
                onClick = onAll,
                modifier = if (devices.isEmpty()) Modifier.focusRequester(first) else Modifier
            )
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .padding(top = 2.dp, bottom = 9.dp)
                .height(2.dp)
                .graphicsLayer {
                    scaleX = left.value
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .drawBehind {
                    drawRect(Brush.horizontalGradient(listOf(color.copy(alpha = 0.15f), color, lerp(color, Color.White, 0.5f))))
                }
        )
    }
}

/** A device in the corner: its mark, its name and what it is doing, and Connect. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CornerRow(device: BtDevice, status: String?, lit: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val look = LocalHomeLook.current
    val color = look.accent2
    val shape = RoundedCornerShape(11.dp)
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = look.text.copy(alpha = 0.06f),
            contentColor = look.text,
            focusedContainerColor = color.copy(alpha = 0.2f),
            focusedContentColor = look.text
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(1.5.dp, color), shape = shape))
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonGlyph(device.kind.glyph, if (lit || focused) color else look.dim, Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    device.name,
                    style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = look.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (status != null) {
                    BasicText(status, style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 10.5.sp, color = color), maxLines = 1)
                }
            }
            if (!device.connected || focused) {
                // A connected device in focus offers to let go: OK disconnects it
                Box(
                    Modifier
                        .background(if (focused) color.copy(alpha = 0.45f) else color.copy(alpha = 0.16f), RoundedCornerShape(50))
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    BasicText(if (device.connected) "Disconnect" else "Connect", style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = look.text))
                }
            } else {
                Box(Modifier.size(7.dp).background(color, CircleShape))
            }
        }
    }
}
