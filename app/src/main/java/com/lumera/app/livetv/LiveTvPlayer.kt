package com.lumera.app.livetv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.lumera.app.ui.components.NeonPalette
import com.lumera.app.ui.components.NeonText
import com.lumera.app.ui.components.NeonType
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private const val BANNER_MS = 5_000L
private const val RETRIES = 3

private val streamHttp by lazy {
    OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

/**
 * A channel on the whole screen. Up and down (and CH+/CH-) zap through the list it was opened
 * from, OK shows what is on, holding OK makes the channel a favourite, Back goes back to the list.
 * It keeps the screen on, so the screensaver never starts in the middle of a match.
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveTvPlayer(
    channels: List<IptvChannel>,
    startIndex: Int,
    guide: Map<String, List<Programme>>,
    isFavourite: (IptvChannel) -> Boolean,
    onToggleFavourite: (IptvChannel) -> Boolean,
    onChannel: (IptvChannel) -> Unit,
    onClose: () -> Unit
) {
    if (channels.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))) }
    var banner by remember { mutableStateOf(true) }
    var poke by remember { mutableLongStateOf(0L) }
    var buffering by remember { mutableStateOf(true) }
    var problem by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val focus = remember { FocusRequester() }
    val channel = channels[index.coerceIn(0, channels.size - 1)]

    // How many times this channel has been retried, and whether it was tried as HLS
    val attempt = remember { intArrayOf(0) }
    val triedHls = remember { booleanArrayOf(false) }

    val player = remember {
        val renderers = DefaultRenderersFactory(context)
            // Broadcast audio (MP2, AC-3) plays through FFmpeg where the TV has no decoder for it
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        val sources = DefaultMediaSourceFactory(OkHttpDataSource.Factory(streamHttp).setUserAgent("NeonTokyoTV"))
        ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(sources)
            .build()
            .apply {
                setWakeMode(C.WAKE_MODE_NETWORK)
                playWhenReady = true
            }
    }

    fun tune(target: IptvChannel, asHls: Boolean = false) {
        problem = null
        buffering = true
        val item = MediaItem.Builder().setUri(target.url).apply { if (asHls) setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                if (state == Player.STATE_READY) attempt[0] = 0
            }

            override fun onPlayerError(error: PlaybackException) {
                val current = channels.getOrNull(index) ?: return
                when {
                    // Fell behind what the live stream still has: jump back to the live edge
                    error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                        player.seekToDefaultPosition()
                        player.prepare()
                    }
                    // A playlist link with nothing in its name to say it is HLS
                    !triedHls[0] && generateSequence<Throwable>(error) { it.cause }.any { it is UnrecognizedInputFormatException } -> {
                        triedHls[0] = true
                        tune(current, asHls = true)
                    }
                    // The provider said no (a channel not in the package, a login used up): retrying will not help
                    refusal(error) != null -> {
                        buffering = false
                        problem = "Your provider refused this channel (${refusal(error)})"
                        banner = true
                    }
                    attempt[0] < RETRIES -> {
                        attempt[0]++
                        problem = null
                        buffering = true
                        player.prepare()
                    }
                    else -> {
                        buffering = false
                        problem = "This channel is not playing right now"
                        banner = true
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(index) {
        attempt[0] = 0
        triedHls[0] = false
        tune(channel)
        onChannel(channel)
        banner = true
        poke = System.currentTimeMillis()
    }
    // A pause that outlasts a few seconds of the stream is retried quietly
    LaunchedEffect(index, problem) {
        if (problem == null) return@LaunchedEffect
        delay(8_000)
        if (problem != null && problem?.startsWith("Your provider refused") != true) { attempt[0] = 0; tune(channel, triedHls[0]) }
    }
    LaunchedEffect(banner, poke) {
        if (!banner) return@LaunchedEffect
        delay(BANNER_MS)
        if (problem == null) banner = false
    }
    LaunchedEffect(toast) {
        if (toast == null) return@LaunchedEffect
        delay(2_000)
        toast = null
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    fun zap(step: Int) {
        if (channels.size < 2) return
        index = (index + step + channels.size) % channels.size
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(-1); true }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> { zap(1); true }
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_INFO -> {
                            banner = true
                            poke = System.currentTimeMillis()
                            true
                        }
                        else -> false
                    }
                }
                .okPress(
                    onClick = {
                        banner = !banner
                        poke = System.currentTimeMillis()
                    },
                    onHold = {
                        val on = onToggleFavourite(channel)
                        toast = if (on) "★  Added to favourites" else "Removed from favourites"
                        banner = true
                        poke = System.currentTimeMillis()
                    }
                )
                .focusable()
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        keepScreenOn = true
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (buffering && problem == null) {
                CircularProgressIndicator(
                    color = NeonPalette.Magenta,
                    modifier = Modifier.align(Alignment.Center).size(48.dp)
                )
            }

            toast?.let {
                Text(
                    it,
                    color = NeonPalette.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(NeonPalette.Night.copy(alpha = 0.85f))
                        .border(1.dp, NeonPalette.Magenta.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }

            AnimatedVisibility(
                visible = banner,
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut() + slideOutVertically { it / 3 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Banner(channel, guide[channel.epgId], now, isFavourite(channel), problem, index + 1, channels.size)
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(60)
        runCatching { focus.requestFocus() }
    }
}

@Composable
private fun Banner(
    channel: IptvChannel,
    programmes: List<Programme>?,
    now: Long,
    favourite: Boolean,
    problem: String?,
    position: Int,
    count: Int
) {
    val nowNext = remember(programmes, now) { programmes?.nowNext(now) }
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, NeonPalette.Night.copy(alpha = 0.92f))))
            .padding(start = 64.dp, end = 64.dp, top = 80.dp, bottom = 44.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 150.dp, height = 84.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, NeonPalette.Magenta.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logo.isNotBlank()) {
                    AsyncImage(channel.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(10.dp))
                } else {
                    Text(channel.name.take(3).uppercase(), color = NeonPalette.Lilac, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${channel.number}", color = NeonPalette.Cyan, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(12.dp))
                    NeonText(channel.name, style = NeonType.headline(30.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (favourite) Text("  ★", color = NeonPalette.Sun, fontSize = 22.sp)
                }
                if (problem != null) {
                    Text(problem, color = Color(0xFFFF8FB8), fontSize = 16.sp)
                } else {
                    val on = nowNext?.now
                    if (on != null) {
                        Text(
                            "${hhmm(on.start)} – ${hhmm(on.stop)}   ${on.title}",
                            color = NeonPalette.Text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Progress(on.progress(now), Modifier.width(520.dp).padding(vertical = 4.dp))
                        if (on.description.isNotBlank()) {
                            Text(on.description, color = NeonPalette.Lilac, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        Text(channel.group, color = NeonPalette.Lilac, fontSize = 16.sp)
                    }
                    nowNext?.next?.let { next ->
                        Text("NEXT ${hhmm(next.start)}  ${next.title}", color = NeonPalette.Cyan.copy(alpha = 0.8f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("$position / $count", color = NeonPalette.Lilac, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text("▲ ▼  change channel", color = NeonPalette.Lilac.copy(alpha = 0.7f), fontSize = 12.sp)
                Text("hold OK  favourite", color = NeonPalette.Lilac.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
    }
}

/** The HTTP status when the provider refused the stream outright (4xx), else null. */
private fun refusal(error: PlaybackException): Int? =
    generateSequence<Throwable>(error) { it.cause }
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()
        ?.responseCode
        ?.takeIf { it in 400..499 }
