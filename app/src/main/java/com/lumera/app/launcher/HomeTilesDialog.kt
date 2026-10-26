package com.lumera.app.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface

/**
 * One of the big tiles on the Home tab: any app on the TV, by its package. [known] is set for the
 * apps with a neon icon of their own; the rest show their first letter in a ring.
 */
internal data class HomeSlot(val key: String, val known: KnownApp?, val label: String) {
    val name: String get() = known?.takeUnless { it.useLabel }?.name ?: label
    val color: Color get() = known?.color ?: HomeCatalog.monogramColor(label)
}

/** The keys of the Home tiles when Adrian hasn't picked his own: the four favourites. */
internal val DefaultHomeKeys = HomeCatalog.favourites.map { it.packages.first() }

/** The Home tiles for the saved [keys], named from the [apps] on the TV where it has them. */
internal fun homeSlots(keys: List<String>, apps: List<InstalledApp>): List<HomeSlot> =
    keys.ifEmpty { DefaultHomeKeys }.distinct().map { key ->
        val app = apps.firstOrNull { it.packageName == key }
        val known = HomeCatalog.favourites.firstOrNull { key in it.packages } ?: app?.known ?: HomeCatalog.known(key, app?.label ?: "")
        HomeSlot(key, known, app?.label ?: known?.name ?: key)
    }

/**
 * Puts [app] from All apps on the Home tab, in place of the tile picked here. An app that is
 * already on Home trades places with the one picked instead.
 */
@Composable
internal fun PinToHomeDialog(app: InstalledApp, slots: List<HomeSlot>, onPick: (Int) -> Unit, onHide: () -> Unit, onDismiss: () -> Unit) {
    val look = LocalHomeLook.current
    val context = LocalContext.current
    val kind = remember(app) { HomeApps.kind(context, app.packageName) }
    val first = remember { FocusRequester() }
    val already = slots.indexOfFirst { it.key == app.packageName }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
        Column(
            modifier = Modifier.width(720.dp).homePanel(look, RoundedCornerShape(18.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PanelNote("HOME", look)
            PanelTitle("Put ${app.name} on Home", look)
            BasicText(
                if (already >= 0) "It is on Home already. Pick a tile to trade places with."
                else "Pick the tile it takes the place of. That app stays under All apps.",
                style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 13.sp, color = look.dim)
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                slots.forEachIndexed { index, slot ->
                    SlotChoice(
                        slot,
                        caption = if (index == already) "Here now" else "Instead of",
                        modifier = Modifier.weight(1f).let { if (index == 0) it.focusRequester(first) else it },
                        onClick = { onPick(index) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // Or away with it: an app of one's own comes off the TV; one that came with the TV can't,
            // but can lose its updates and data, or be switched off, in its app info, or be hidden
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (kind) {
                    HomeApps.Kind.Own -> PanelButton("Uninstall", look) {
                        HomeApps.uninstall(context, app.packageName)
                        onDismiss()
                    }
                    HomeApps.Kind.UpdatedSystem -> PanelButton("Remove updates", look) {
                        HomeApps.uninstall(context, app.packageName)
                        onDismiss()
                    }
                    HomeApps.Kind.System -> Unit
                }
                if (kind != HomeApps.Kind.Own) PanelButton("Hide", look, onClick = onHide)
                PanelButton("App info", look) {
                    HomeApps.appInfo(context, app.packageName)
                    onDismiss()
                }
                PanelButton("Cancel", look, onClick = onDismiss)
            }
            if (kind != HomeApps.Kind.Own) BasicText(
                "It came with the TV, so it can't be uninstalled. App info can clear its data or switch it off.",
                style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 12.sp, color = look.dim)
            )
        }
    }
}

/**
 * Changes one Home tile, [slot], to another app on the TV, or puts all four back to how they
 * came. The apps are in the All apps order.
 */
@Composable
internal fun ChangeHomeTileDialog(
    slot: HomeSlot,
    apps: List<InstalledApp>,
    onPick: (InstalledApp) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val look = LocalHomeLook.current
    val first = remember { FocusRequester() }
    val choices = remember(apps, slot) { apps.filter { it.packageName != slot.key } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
        Column(
            modifier = Modifier.width(760.dp).homePanel(look, RoundedCornerShape(18.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PanelNote("HOME", look)
            PanelTitle("Change ${slot.name}", look)
            BasicText(
                "Pick the app that goes here instead. Hold OK on a tile for three seconds to move it.",
                style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 13.sp, color = look.dim)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PanelButton("Back to the first four", look, onClick = onReset)
                PanelButton("Cancel", look, onClick = onDismiss)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
            ) {
                itemsIndexed(choices, key = { _, app -> app.packageName }) { index, app ->
                    SlotChoice(
                        HomeSlot(app.packageName, app.known, app.label),
                        caption = null,
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                        onClick = { onPick(app) }
                    )
                }
            }
        }
    }
}

/** An app to pick in the dialogs above: its neon icon or letter, its name and a line over it. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SlotChoice(slot: HomeSlot, caption: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val look = LocalHomeLook.current
    val shape = RoundedCornerShape(look.corner * 0.8f)
    val color = look.tint(slot.color)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = look.text.copy(alpha = 0.06f),
            contentColor = look.text,
            focusedContainerColor = color.copy(alpha = 0.22f),
            focusedContentColor = look.text
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(0.5.dp, look.text.copy(alpha = 0.18f)), shape = shape),
            focusedBorder = Border(BorderStroke(1.5.dp, color), shape = shape)
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = color.copy(alpha = 0.45f), elevation = 10.dp))
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (caption != null) {
                BasicText(caption, style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 10.sp, letterSpacing = 0.6.sp, color = look.dim))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val known = slot.known
                if (known != null) NeonGlyph(known.glyph, color, Modifier.size(24.dp)) else Monogram(slot.label, color, Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                BasicText(
                    slot.name,
                    style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 13.sp, color = look.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The apps hidden from All apps; picking one shows it there again. */
@Composable
internal fun HiddenAppsDialog(apps: List<InstalledApp>, onShow: (InstalledApp) -> Unit, onDismiss: () -> Unit) {
    val look = LocalHomeLook.current
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(apps.isEmpty()) {
            if (apps.isEmpty()) {
                onDismiss()
                return@LaunchedEffect
            }
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
        Column(
            modifier = Modifier.width(760.dp).homePanel(look, RoundedCornerShape(18.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PanelNote("ALL APPS", look)
            PanelTitle("Hidden apps", look)
            BasicText(
                "They are still on the TV, only not in the list. Pick one to show it again.",
                style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 13.sp, color = look.dim)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
            ) {
                itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                    SlotChoice(
                        HomeSlot(app.packageName, app.known, app.label),
                        caption = "Show again",
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                        onClick = { onShow(app) }
                    )
                }
            }
            Row { PanelButton("Done", look, primary = true, onClick = onDismiss) }
        }
    }
}
