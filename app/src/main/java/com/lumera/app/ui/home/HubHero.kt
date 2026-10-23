package com.lumera.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lumera.app.data.profile.NeonTokyoStarterPack
import com.lumera.app.ui.components.NeonAccents
import com.lumera.app.ui.components.NeonPalette
import com.lumera.app.ui.components.NeonText
import com.lumera.app.ui.components.NeonType

/**
 * What the top of the home screen shows while a folder card (a streaming service or a
 * genre) has focus: the folder's name as a neon sign and a line on what is inside,
 * so the hero never sits empty.
 */
@Composable
fun HubHero(title: String, rowTitle: String, rowId: String, modifier: Modifier = Modifier) {
    val accent = remember(title) { NeonAccents.forTitle(title) }
    Column(modifier = modifier.padding(top = 28.dp)) {
        NeonText(rowTitle.uppercase(), style = NeonType.eyebrow(13.sp, color = accent))
        Spacer(Modifier.height(6.dp))
        NeonText(
            text = title,
            style = NeonType.display(56.sp, glow = accent),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = NeonTokyoStarterPack.hubBlurb(title, rowId),
            style = MaterialTheme.typography.bodyLarge,
            color = NeonPalette.Lilac,
            maxLines = 2,
            modifier = Modifier.widthIn(max = 640.dp)
        )
    }
}

/** Soft glow in the folder's colour behind the hero, in place of a backdrop image. */
@Composable
fun HubHeroGlow(title: String, modifier: Modifier = Modifier) {
    val accent = remember(title) { NeonAccents.forTitle(title) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val glow = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.74f, size.height * 0.18f),
                    radius = size.width * 0.55f
                )
                onDrawBehind { drawRect(glow) }
            }
    )
}
