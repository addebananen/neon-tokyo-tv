package com.lumera.app.ui.home

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lumera.app.data.profile.NeonTokyoStarterPack
import com.lumera.app.domain.HubShape
import com.lumera.app.ui.components.ServiceCardArt
import kotlinx.coroutines.delay

/**
 * Every service and genre card in a grid, for checking the scene art: the emulator test films
 * it while focus walks from card to card. Extras: shape (wide, square or tall), width (dp),
 * dwell (ms on each card), codes (a comma list of scenes, all by default).
 */
class SceneArtPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val shape = when (intent.getStringExtra("shape")) {
            "square" -> HubShape.SQUARE
            "tall" -> HubShape.VERTICAL
            else -> HubShape.HORIZONTAL
        }
        val codes = intent.getStringExtra("codes")?.split(',')?.filter { it.isNotBlank() }
            ?: (NeonTokyoStarterPack.SERVICES.map { it.first } + NeonTokyoStarterPack.GENRES.map(NeonTokyoStarterPack::genreArt))
        val width = intent.getIntExtra("width", 260)
        val dwell = intent.getLongExtra("dwell", intent.getIntExtra("dwell", 3200).toLong())
        setContent { Grid(codes, shape, width, dwell) }
    }
}

@Composable
private fun Grid(codes: List<String>, shape: HubShape, width: Int, dwell: Long) {
    val requesters = remember(codes) { codes.map { FocusRequester() } }
    var focused by remember { mutableStateOf(-1) }
    val columns = (900 / (width + 16)).coerceAtLeast(1)
    LaunchedEffect(codes) {
        delay(1500)
        var i = 0
        while (true) {
            runCatching { requesters[i].requestFocus() }
            delay(dwell)
            i = (i + 1) % codes.size
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0618))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        codes.chunked(columns).forEachIndexed { row, chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                chunk.forEachIndexed { col, code ->
                    val index = row * columns + col
                    val isFocused = focused == index
                    Box(
                        modifier = Modifier
                            .width(width.dp)
                            .aspectRatio(shape.aspectRatio)
                            .graphicsLayer {
                                val s = if (isFocused) 1.05f else 1f
                                scaleX = s
                                scaleY = s
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .focusRequester(requesters[index])
                            .onFocusChanged { if (it.isFocused) focused = index }
                            .focusable()
                    ) {
                        val title = NeonTokyoStarterPack.SERVICES.firstOrNull { it.first == code }?.second
                            ?: NeonTokyoStarterPack.GENRES.firstOrNull { NeonTokyoStarterPack.genreArt(it) == code }
                            ?: code
                        ServiceCardArt(code = code, title = title, shape = shape, focused = isFocused)
                    }
                }
            }
        }
    }
}
