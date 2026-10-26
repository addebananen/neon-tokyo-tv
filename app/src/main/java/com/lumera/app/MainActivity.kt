package com.lumera.app

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.hilt.navigation.compose.hiltViewModel
import com.lumera.app.data.update.AppUpdateManager
import com.lumera.app.intro.NeonIntroView
import com.lumera.app.intro.NeonPowerOffView
import com.lumera.app.data.update.UpdateInfo
import com.lumera.app.data.update.UpdateState
import com.lumera.app.data.player.PlaybackTrackSelectionStore
import com.lumera.app.data.torrent.TorrentProgress
import com.lumera.app.data.torrent.TorrentService
import com.lumera.app.data.player.SourceSelectionStore
import com.lumera.app.ui.MainViewModel
import com.lumera.app.ui.components.LumeraBackground
import com.lumera.app.ui.details.DetailsScreen
import com.lumera.app.ui.home.GridViewScreen
import com.lumera.app.ui.home.HomeScreen
import com.lumera.app.ui.watchlist.WatchlistScreen
import com.lumera.app.ui.home.HomeViewModel
import com.lumera.app.data.model.stremio.MetaItem
import com.lumera.app.data.model.stremio.Stream
import com.lumera.app.data.model.stremio.StreamSubtitle
import com.lumera.app.data.model.stremio.MetaVideo
import com.lumera.app.data.repository.AddonRepository
import com.lumera.app.data.repository.IntroRepository
import com.lumera.app.data.repository.SubtitleRepository
import com.lumera.app.data.stream.StreamSortingService
import com.lumera.app.domain.AddonSubtitle
import com.lumera.app.domain.DashboardTab
import com.lumera.app.domain.episodeDisplayTitle
import com.lumera.app.domain.episodePlaybackId
import com.lumera.app.domain.episodeStreamId
import com.lumera.app.domain.findNextEpisode
import com.lumera.app.ui.navigation.NavDestination
import com.lumera.app.ui.navigation.NavDrawer
import com.lumera.app.ui.navigation.TopNavigationBar
import com.lumera.app.ui.player.PlayerScreen
import com.lumera.app.ui.player.PlayerSessionResult
import com.lumera.app.ui.player.base.PlayerSourceOption
import com.lumera.app.ui.player.base.NextEpisodeInfo
import com.lumera.app.ui.player.base.PlaybackSettings
import com.lumera.app.ui.player.base.PlayerSubtitleSource
import com.lumera.app.ui.player.base.SkipSegmentInfo
import com.lumera.app.ui.profiles.ProfileScreen
import com.lumera.app.ui.profiles.ProfileViewModel
import com.lumera.app.ui.search.SearchScreen
import com.lumera.app.ui.settings.SettingsScreen
import com.lumera.app.ui.addons.VoidButton
import com.lumera.app.ui.addons.VoidDialog
import com.lumera.app.ui.theme.DefaultThemes
import com.lumera.app.ui.theme.LocalRoundCorners
import com.lumera.app.ui.theme.LocalHubRoundCorners
import com.lumera.app.ui.theme.LumeraTheme
import com.lumera.app.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.lumera.app.data.local.AddonDao
import com.lumera.app.data.profile.ProfileConfigurationManager
import kotlinx.coroutines.runBlocking

import java.util.Locale
import javax.inject.Inject

private const val DOUBLE_BACK_EXIT_WINDOW_MS = 700L

// Coming back to the app after this long away plays the intro again, as switching a TV on would
private const val INTRO_AGAIN_AFTER_MS = 2 * 60_000L
private const val SOURCE_SELECTION_COMMIT_MIN_POSITION_MS = 5_000L
private const val SOURCE_SELECTION_FAILURE_RESET_MAX_POSITION_MS = 1_000L

private data class PlayerSubtitlePayload(
    val id: String,
    val url: String,
    val name: String,
    val language: String?
)

private data class PendingSourceSelection(
    val playbackId: String,
    val launchedStream: Stream,
    val candidateStreams: List<Stream>
)

private data class PendingEpisodeSwitch(
    val playbackId: String,
    val playbackTitle: String,
    val streams: List<Stream>?,
    val addonSubs: List<AddonSubtitle>,
    val playerCurrentSourceUrl: String?
)

@Stable
private class PlayerState {
    var selectedPlayerSubtitles by mutableStateOf<List<PlayerSubtitlePayload>>(emptyList())
    var selectedPlayerSources by mutableStateOf<List<PlayerSourceOption>>(emptyList())
    var pendingSourceSelection by mutableStateOf<PendingSourceSelection?>(null)
    var showPlayerChoiceDialog by mutableStateOf(false)
    var currentEpisodeList by mutableStateOf<List<MetaVideo>>(emptyList())
    var currentStream by mutableStateOf<Stream?>(null)
    var pendingEpisodeSwitch by mutableStateOf<PendingEpisodeSwitch?>(null)
    var isEpisodeSwitchLoading by mutableStateOf(false)
    // Looking up the sources of the next episode; stopped when the player closes or another switch starts
    var episodeSwitchJob: Job? = null
}

private fun resolveSubtitleUrl(rawUrl: String, addonTransportUrl: String?): String? {
    val value = rawUrl.trim()
    if (value.isEmpty()) return null

    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
    if (uri.isAbsolute) {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        return value
    }
    if (addonTransportUrl.isNullOrBlank()) return null

    val base = addonTransportUrl.trimEnd('/')
    val path = value.trimStart('/')
    if (path.isEmpty()) return null
    return "$base/$path"
}

private fun sanitizeSubtitleSourceName(rawName: String?, fallback: String): String {
    val cleaned = rawName
        ?.replace("[", "")
        ?.replace("]", "")
        ?.trim()
        .orEmpty()
    return cleaned.ifEmpty { fallback }
}

private fun subtitleNameFromUrl(rawUrl: String): String? {
    val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return null
    val path = uri.path?.substringBefore('?').orEmpty()
    val rawName = path.substringAfterLast('/').ifEmpty { return null }
    val decoded = runCatching { Uri.decode(rawName) }.getOrDefault(rawName)
    val withoutExtension = decoded.substringBeforeLast('.', decoded).trim()
    return withoutExtension.ifEmpty { null }
}

private fun normalizeSubtitleLanguageTag(rawLang: String?): String? {
    val value = rawLang?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return value.replace('_', '-').lowercase(Locale.ROOT)
}

private val TORRENT_TRACKERS = listOf(
    // HTTP trackers (TCP — work even when UDP is blocked)
    "http://tracker.opentrackr.org:1337/announce",
    "http://tracker.openbittorrent.com:80/announce",
    "http://tracker1.bt.moack.co.kr:80/announce",
    "http://tracker.gbitt.info:80/announce",
    // UDP trackers (fallback)
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://open.stealth.si:80/announce",
    "udp://tracker.openbittorrent.com:6969/announce",
    "udp://exodus.desync.com:6969/announce"
)

private fun resolvePlayableSourceUrl(stream: Stream): String? {
    val directUrl = stream.url?.trim()?.takeIf { it.isNotEmpty() }
    if (directUrl != null) return directUrl

    val infoHash = stream.infoHash?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    // Combine hardcoded trackers with addon-provided tracker URLs
    val addonTrackers = stream.sources
        ?.filter { it.startsWith("tracker:") }
        ?.map { it.removePrefix("tracker:") }
        ?: emptyList()
    val allTrackers = (addonTrackers + TORRENT_TRACKERS).distinct()
    val trackerParams = allTrackers.joinToString("") {
        "&tr=${java.net.URLEncoder.encode(it, "UTF-8")}"
    }
    return "magnet:?xt=urn:btih:${infoHash}&dn=Video${trackerParams}"
}

private fun sourceDisplayLabel(stream: Stream): String {
    val primary = stream.description
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: stream.title
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        ?: stream.name
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        ?: "Source"
    return primary.replace('\n', ' ')
}

private fun launchExternalPlayer(context: android.content.Context, url: String) {
    try {
        val scheme = Uri.parse(url).scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            Toast.makeText(context, "Unsupported URL scheme", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "No external player found", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun PlayerChoiceDialog(
    onInternal: () -> Unit,
    onExternal: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Choose Player",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VoidButton(
                        text = "Internal Player",
                        onClick = onInternal,
                        isPrimary = true,
                        modifier = Modifier.weight(1f)
                    )
                    VoidButton(
                        text = "External Player",
                        onClick = onExternal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateAvailableDialog(
    info: UpdateInfo,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onDontShowAgain: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Update Available",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "v${info.versionName}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (info.changelog.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        info.changelog,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(0.7f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VoidButton(
                        text = "Later",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    VoidButton(
                        text = "Update",
                        onClick = onUpdate,
                        isPrimary = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Don't show again",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onDontShowAgain() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun UpdateDownloadingDialog(progress: Float, downloadedMb: Float, totalMb: Float) {
    Dialog(onDismissRequest = {}) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Downloading Update",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(0.1f),
                    drawStopIndicator = {}
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (totalMb > 0f) "%.1f MB / %.1f MB".format(downloadedMb, totalMb)
                    else "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun UpdateErrorDialog(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Update Failed",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VoidButton(
                        text = "Dismiss",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    VoidButton(
                        text = "Retry",
                        onClick = onRetry,
                        isPrimary = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun buildSourcePayload(
    streams: List<Stream>,
    selectedStream: Stream
): List<PlayerSourceOption> {
    val selectedUrl = resolvePlayableSourceUrl(selectedStream)
    return streams.mapNotNull { stream ->
        val url = resolvePlayableSourceUrl(stream) ?: return@mapNotNull null
        PlayerSourceOption(
            id = url,
            url = url,
            label = sourceDisplayLabel(stream),
            name = stream.name,
            title = stream.title,
            description = stream.description,
            fileIdx = stream.fileIdx ?: -1,
            fileName = stream.behaviorHints?.filename ?: ""
        )
    }
        .distinctBy { it.url }
        .sortedByDescending { option -> option.url == selectedUrl }
}

private fun canonicalSubtitleUrlForId(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return rawUrl

    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return trimmed
    val noQuery = trimmed.substringBefore('?').substringBefore('#')
    if (!uri.isAbsolute) return noQuery

    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    val host = uri.host?.lowercase(Locale.ROOT)
    val path = uri.encodedPath ?: uri.path
    if (scheme.isNullOrBlank() || host.isNullOrBlank() || path.isNullOrBlank()) {
        return noQuery
    }
    val port = if (uri.port != -1) ":${uri.port}" else ""
    return "$scheme://$host$port$path"
}

private fun buildSubtitleFallbackId(
    resolvedUrl: String,
    language: String?,
    name: String
): String {
    val canonicalUrl = canonicalSubtitleUrlForId(resolvedUrl)
    val canonicalLanguage = language.orEmpty().trim().lowercase(Locale.ROOT)
    val canonicalName = name.trim().lowercase(Locale.ROOT)
    return "lumera-sub:$canonicalLanguage|$canonicalName|$canonicalUrl"
}

private fun buildEmbeddedSubtitlePayload(stream: Stream): List<PlayerSubtitlePayload> {
    return stream.subtitles
        .orEmpty()
        .mapNotNull { subtitle ->
            buildEmbeddedSubtitlePayloadItem(stream, subtitle)
        }
}

private fun buildEmbeddedSubtitlePayloadItem(
    stream: Stream,
    subtitle: StreamSubtitle
): PlayerSubtitlePayload? {
    val rawUrl = subtitle.url?.trim().orEmpty()
    if (rawUrl.isEmpty()) return null

    val resolvedUrl = resolveSubtitleUrl(
        rawUrl = rawUrl,
        addonTransportUrl = subtitle.transportUrl ?: stream.addonTransportUrl
    ) ?: return null

    val fallbackName = subtitleNameFromUrl(resolvedUrl) ?: "Embedded subtitle"
    val name = sanitizeSubtitleSourceName(subtitle.name, fallbackName)
    val language = normalizeSubtitleLanguageTag(subtitle.lang)
    val subtitleId = subtitle.id
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: buildSubtitleFallbackId(
            resolvedUrl = resolvedUrl,
            language = language,
            name = name
        )
    return PlayerSubtitlePayload(
        id = subtitleId,
        url = resolvedUrl,
        name = name,
        language = language
    )
}

private fun buildAddonSubtitlePayload(addonSubtitles: List<AddonSubtitle>): List<PlayerSubtitlePayload> {
    return addonSubtitles.mapNotNull { subtitle ->
        val resolvedUrl = resolveSubtitleUrl(subtitle.url, addonTransportUrl = null) ?: return@mapNotNull null
        val name = sanitizeSubtitleSourceName(subtitle.addonName, "Addon subtitle")
        val language = normalizeSubtitleLanguageTag(subtitle.lang)
        val subtitleId = subtitle.id
            .trim()
            .takeIf { it.isNotEmpty() }
            ?: buildSubtitleFallbackId(
                resolvedUrl = resolvedUrl,
                language = language,
                name = name
            )
        PlayerSubtitlePayload(
            id = subtitleId,
            url = resolvedUrl,
            name = name,
            language = language
        )
    }
}

private fun buildSubtitlePayload(stream: Stream, addonSubtitles: List<AddonSubtitle>): List<PlayerSubtitlePayload> {
    return (buildEmbeddedSubtitlePayload(stream) + buildAddonSubtitlePayload(addonSubtitles))
        .distinctBy { payload ->
            val url = payload.url.lowercase(Locale.ROOT)
            val lang = payload.language.orEmpty().lowercase(Locale.ROOT)
            "$url|$lang"
        }
}

private fun handlePlayerSessionEnd(
    sessionResult: PlayerSessionResult,
    selectedPlaybackId: String,
    playbackTrackSelectionStore: PlaybackTrackSelectionStore,
    sourceSelectionStore: SourceSelectionStore,
    pendingSourceSelection: PendingSourceSelection?,
    onConsumePendingSelection: () -> Unit,
    onResumeHintResolved: (String?) -> Unit,
    rememberSourceSelection: Boolean = true,
    // Left for another episode on purpose: the source worked, so it isn't forgotten as a failed one
    switchedAway: Boolean = false
) {
    val playbackId = selectedPlaybackId.trim()
    if (playbackId.isBlank()) {
        onConsumePendingSelection()
        onResumeHintResolved(null)
        return
    }

    onResumeHintResolved(
        if (!sessionResult.isCompleted && sessionResult.positionMs >= SOURCE_SELECTION_COMMIT_MIN_POSITION_MS) {
            playbackId
        } else {
            null
        }
    )

    val hasAudioTrackSelection = !sessionResult.selectedAudioTrackId.isNullOrBlank()
    val hasSubtitleTrackSelection = !sessionResult.selectedSubtitleTrackId.isNullOrBlank()
    val hasSubtitleDelayChange = sessionResult.subtitleDelayMs != 0L
    if (hasAudioTrackSelection || hasSubtitleTrackSelection || hasSubtitleDelayChange) {
        playbackTrackSelectionStore.updateSelection(
            playbackId = playbackId,
            audioTrackId = sessionResult.selectedAudioTrackId,
            subtitleTrackId = sessionResult.selectedSubtitleTrackId,
            subtitleDelayMs = sessionResult.subtitleDelayMs,
            updateAudio = hasAudioTrackSelection,
            updateSubtitle = hasSubtitleTrackSelection,
            updateSubtitleDelay = true
        )
    }

    pendingSourceSelection?.let { pendingSelection ->
        val pendingPlaybackId = pendingSelection.playbackId.trim()
        if (pendingPlaybackId.isNotEmpty()) {
            val selectedStream = sessionResult.selectedSourceUrl
                ?.let { selectedSourceUrl ->
                    pendingSelection.candidateStreams.firstOrNull { candidate ->
                        resolvePlayableSourceUrl(candidate) == selectedSourceUrl
                    }
                }
                ?: pendingSelection.launchedStream

            val shouldCommitSource = rememberSourceSelection && (sessionResult.isCompleted ||
                sessionResult.positionMs >= SOURCE_SELECTION_COMMIT_MIN_POSITION_MS)
            if (shouldCommitSource) {
                sourceSelectionStore.rememberSelection(pendingPlaybackId, selectedStream)
            } else if (!switchedAway && sessionResult.positionMs <= SOURCE_SELECTION_FAILURE_RESET_MAX_POSITION_MS) {
                val wasPreferred = sourceSelectionStore.findPreferredStream(
                    playbackId = pendingPlaybackId,
                    streams = listOf(selectedStream)
                ) != null
                if (wasPreferred) {
                    sourceSelectionStore.clearSelection(pendingPlaybackId)
                }
            }
        }
    }

    onConsumePendingSelection()
}

private class GridRestoreState {
    var focusedIndex: Int? = null
    var scrollIndex: Int = 0
    var scrollOffset: Int = 0
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var sourceSelectionStore: SourceSelectionStore
    @Inject
    lateinit var playbackTrackSelectionStore: PlaybackTrackSelectionStore
    @Inject
    lateinit var addonRepository: AddonRepository
    @Inject
    lateinit var subtitleRepository: SubtitleRepository
    @Inject
    lateinit var introRepository: IntroRepository
    @Inject
    lateinit var profileConfigurationManager: ProfileConfigurationManager
    @Inject
    lateinit var appUpdateManager: AppUpdateManager
    @Inject
    lateinit var addonDao: AddonDao
    @Inject
    lateinit var streamSortingService: StreamSortingService

    private var splashOverlay: NeonIntroView? = null
    private val _splashFinished = mutableStateOf(false)
    private var poweringOff = false
    // A title Neon Home asked to open, until the right profile is logged in and its page shows
    private val pendingOpen = mutableStateOf<OpenRequest?>(null)
    // When the app last went out of sight, by the clock that keeps counting while the TV sleeps
    private var stoppedAt = 0L

    override fun onDestroy() {
        dismissSplash()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (splashOverlay == null) {
            outState.putBoolean(KEY_SPLASH_SHOWN, true)
            outState.putLong(KEY_SPLASH_SHOWN_AT, SystemClock.elapsedRealtime())
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) stoppedAt = SystemClock.elapsedRealtime()
    }

    // Back after a while away (Home pressed, the TV asleep), the app switches on with its intro
    override fun onRestart() {
        super.onRestart()
        val away = SystemClock.elapsedRealtime() - stoppedAt
        if (stoppedAt == 0L || away < INTRO_AGAIN_AFTER_MS || splashOverlay != null || poweringOff) return
        if (!NeonIntroView.animationsOn(this) || !splashEnabled()) return
        attachSplashOverlay()
        // Everything under it is already there, so it leaves as soon as it has played
        onSplashAppReady()
    }

    private fun splashEnabled(): Boolean =
        profileConfigurationManager.getLastActiveProfileId()?.let { id ->
            runBlocking(Dispatchers.IO) { addonDao.getProfileById(id) }
        }?.splashEnabled ?: true

    /** The first screen has something to show: the intro leaves as soon as it has played. */
    private fun onSplashAppReady() {
        splashOverlay?.appReady()
    }

    private fun dismissSplash() {
        splashOverlay?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        splashOverlay = null
        _splashFinished.value = true
    }

    private fun attachSplashOverlay() {
        // The emulator test holds the intro still for a moment to photograph it
        val holdAt = intent?.getFloatExtra("nt_intro_hold", -1f) ?: -1f
        val intro = NeonIntroView(this, holdAt.toDouble()) { dismissSplash() }
        addContentView(intro, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))
        splashOverlay = intro
    }

    /**
     * Leaves the app the way an old TV switches off: the picture folds into a neon line, which
     * runs back into a dot that goes out. Keys are ignored meanwhile.
     */
    private fun powerOff() {
        if (poweringOff || isFinishing) return
        poweringOff = true
        // An intro still playing just goes; the update popup it holds back stays away too
        splashOverlay?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        splashOverlay = null
        val content = findViewById<android.view.ViewGroup>(android.R.id.content)
        if (content == null || content.width == 0 || !NeonIntroView.animationsOn(this)) {
            closeApp()
            return
        }
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
        val powerOff = NeonPowerOffView(this, content) { closeApp() }
        (window.decorView as android.view.ViewGroup).addView(powerOff, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))
        powerOff.start()
    }

    /** Closes the app, with the black it ends on fading into the home screen. */
    private fun closeApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
            finishAffinity()
        } else {
            finishAffinity()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, android.R.anim.fade_out)
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
        poweringOff || super.dispatchKeyEvent(event)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Sanitize saved state: R8 can obfuscate Parcelable class names, causing
        // BadParcelableException on process-death restore. Clear the bundle if corrupt.
        val safeState = savedInstanceState?.let { bundle ->
            try {
                bundle.keySet() // forces unparcel — throws if any class is missing
                bundle
            } catch (_: android.os.BadParcelableException) {
                null
            }
        }
        super.onCreate(safeState)
        OpenRequest.from(intent)?.let { pendingOpen.value = it }
        window.setFormat(android.graphics.PixelFormat.RGBA_8888)

        // Fix sideload launch bug: pressing Home and returning re-creates the activity
        // instead of resuming it when the APK was installed via adb/sideload.
        if (!isTaskRoot && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
            && Intent.ACTION_MAIN == intent.action) {
            finish()
            return
        }

        val splashEnabledInProfile = splashEnabled()
        // Brought back after the TV slept and the app was put away: the intro plays again when
        // it has been a while, not when the screen was only turned or rebuilt a moment ago
        val shownAgo = safeState?.getLong(KEY_SPLASH_SHOWN_AT, 0L)?.takeIf { it > 0L }?.let { SystemClock.elapsedRealtime() - it }
        val shownLately = safeState?.getBoolean(KEY_SPLASH_SHOWN) == true && (shownAgo == null || shownAgo in 0 until INTRO_AGAIN_AFTER_MS)
        // No intro when the system's animations are switched off
        val showSplash = splashEnabledInProfile && !shownLately && NeonIntroView.animationsOn(this)
        if (!showSplash) _splashFinished.value = true

        setContent {

            val mainViewModel = hiltViewModel<MainViewModel>()
            val themeManager = hiltViewModel<ThemeManager>()
            val currentProfile by mainViewModel.activeProfile.collectAsState()
            var sessionProfileId by rememberSaveable { mutableStateOf<Int?>(null) }
            var sessionRestoreAttemptedProfileId by rememberSaveable { mutableStateOf<Int?>(null) }
            var activeView by rememberSaveable { mutableStateOf("menu") }
            var selectedMovieId by rememberSaveable { mutableStateOf("") }
            var selectedMovieType by rememberSaveable { mutableStateOf("movie") }
            var selectedVideoUrl by rememberSaveable { mutableStateOf("") }
            var selectedTrailerAudioUrl by rememberSaveable { mutableStateOf("") }
            var torrentProgress by remember { mutableStateOf<TorrentProgress?>(null) }
            var selectedMovieTitle by rememberSaveable { mutableStateOf("") }
            var selectedMoviePoster by rememberSaveable { mutableStateOf("") }
            var selectedMovieBackground by rememberSaveable { mutableStateOf("") }
            var selectedMovieLogo by rememberSaveable { mutableStateOf("") }
            var selectedAddonBaseUrl by rememberSaveable { mutableStateOf<String?>(null) }
            var detailsResumePlaybackHint by rememberSaveable { mutableStateOf<String?>(null) }
            var trailerReturnToken by rememberSaveable { mutableStateOf(0) }
            var isTrailerLoading by remember { mutableStateOf(false) }
            var showTrailerError by remember { mutableStateOf(false) }
            var selectedPlaybackId by rememberSaveable { mutableStateOf("") }
            var selectedPlaybackType by rememberSaveable { mutableStateOf("movie") }
            var selectedPlaybackTitle by rememberSaveable { mutableStateOf("") }
            var selectedPlaybackPoster by rememberSaveable { mutableStateOf("") }
            var previousView by rememberSaveable { mutableStateOf("menu") }
            val playerState = remember { PlayerState() }


            LaunchedEffect(currentProfile?.id) {
                val profileId = currentProfile?.id
                if (profileId != null) {
                    sessionProfileId = profileId
                    sessionRestoreAttemptedProfileId = null
                }
            }

            // Continue watching on Neon Home: log in as the profile whose row it was, then open the title
            val openRequest by pendingOpen
            LaunchedEffect(openRequest, currentProfile?.id) {
                val open = openRequest ?: return@LaunchedEffect
                if (currentProfile?.id != open.profileId) {
                    if (sessionProfileId != open.profileId || sessionRestoreAttemptedProfileId != open.profileId) {
                        activeView = "menu"
                        sessionProfileId = open.profileId
                        sessionRestoreAttemptedProfileId = open.profileId
                        mainViewModel.login(open.profileId)
                    }
                    return@LaunchedEffect
                }
                selectedMovieId = open.id
                selectedMovieType = open.type
                selectedMovieTitle = open.name
                selectedMoviePoster = open.poster
                selectedMovieBackground = open.background
                selectedMovieLogo = open.logo
                selectedAddonBaseUrl = null
                detailsResumePlaybackHint = null
                selectedPlaybackId = open.id
                selectedPlaybackType = open.type
                selectedPlaybackTitle = open.name
                selectedPlaybackPoster = open.poster
                previousView = "menu"
                activeView = "details"
                pendingOpen.value = null
            }

            LaunchedEffect(currentProfile, sessionProfileId, sessionRestoreAttemptedProfileId) {
                if (currentProfile != null) return@LaunchedEffect
                val profileIdToRestore = sessionProfileId ?: return@LaunchedEffect
                if (sessionRestoreAttemptedProfileId == profileIdToRestore) return@LaunchedEffect

                sessionRestoreAttemptedProfileId = profileIdToRestore
                mainViewModel.login(profileIdToRestore)
            }

            // Resolve theme from profile's themeId
            val currentTheme by themeManager.currentTheme.collectAsState()

            // Get round corners setting from profile (default true)
            val roundCorners = currentProfile?.roundCorners ?: true
            val hubRoundCorners = currentProfile?.hubRoundCorners ?: true
            
            // Update theme when profile changes
            LaunchedEffect(currentProfile) {
                currentProfile?.let { profile ->
                    themeManager.setCurrentProfile(profile.id, profile.themeId)
                }
            }

            // Auto-check for updates on launch
            val updateState by appUpdateManager.state.collectAsState()
            var updateDismissed by rememberSaveable { mutableStateOf(false) }
            val updateScope = rememberCoroutineScope()
            // The emulator test passes nt_ci so the popup does not cover the screens under test
            val ciRun = remember { intent?.getBooleanExtra("nt_ci", false) == true }
            var updateDownloadStarted by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) { if (!ciRun) appUpdateManager.checkForUpdate() }

            LumeraTheme(theme = currentTheme) {
                CompositionLocalProvider(
                    LocalRoundCorners provides roundCorners,
                    LocalHubRoundCorners provides hubRoundCorners
                ) {
                LumeraBackground {
                    if (currentProfile == null) {
                        // Double-back-to-exit on profile selection
                        var lastBackPressMs by remember { mutableStateOf(0L) }
                        BackHandler {
                            val now = SystemClock.uptimeMillis()
                            if (now - lastBackPressMs < DOUBLE_BACK_EXIT_WINDOW_MS) {
                                powerOff()
                            } else {
                                lastBackPressMs = now
                            }
                        }

                        val isRestoringSession = sessionProfileId != null
                        if (!isRestoringSession) {
                            // PROFILE SELECTION / CREATION
                            // Profile selection always uses the house Neon Tokyo look
                            LumeraTheme(theme = DefaultThemes.NEON_TOKYO) {
                                val profileViewModel = hiltViewModel<ProfileViewModel>()
                                val profiles by profileViewModel.profiles.collectAsState()
                                // The intro fades into the profiles, so it waits for them to be there
                                val profilesLoading by profileViewModel.isLoading.collectAsState()
                                LaunchedEffect(profilesLoading) { if (!profilesLoading) onSplashAppReady() }

                                ProfileScreen(
                                    profiles = profiles,
                                    onProfileSelected = {
                                        sessionProfileId = it.id
                                        sessionRestoreAttemptedProfileId = null
                                        mainViewModel.login(it.id)
                                    }
                                )
                            }
                        }
                    } else {
                        // MAIN APP CONTENT
                        LaunchedEffect(Unit) { onSplashAppReady() }
                        var currentNav by remember { mutableStateOf(NavDestination.Home) }
                        
                        // Grid view state
                        var gridViewTitle by rememberSaveable { mutableStateOf("") }
                        var gridViewItems by remember { mutableStateOf<List<MetaItem>>(emptyList()) }
                        var gridViewConfigId by rememberSaveable { mutableStateOf("") }
                        val gridRestoreState = remember { GridRestoreState() }

                        // Search focus restoration
                        val searchMoviesViewMoreRequester = remember { FocusRequester() }
                        val searchSeriesViewMoreRequester = remember { FocusRequester() }
                        val searchResultsRequester = remember { FocusRequester() }
                        val searchDiscoverRequester = remember { FocusRequester() }
                        var searchFocusTarget by remember { mutableStateOf<String?>(null) }
                        var searchLastFocusedId by remember { mutableStateOf<String?>(null) }

                        // Track where we came from for proper back navigation
                        val uiScope = rememberCoroutineScope()

                        // Focus Traffic Control
                        val drawerRequesters = remember { NavDestination.values().associateWith { FocusRequester() } }
                        val homeEntryRequester = remember { FocusRequester() }
                        val searchEntryRequester = remember { FocusRequester() }
                        val settingsEntryRequester = remember { FocusRequester() }
                        val watchlistEntryRequester = remember { FocusRequester() }

                        // STATE CHANGE TRIGGER:
                        LaunchedEffect(currentNav, activeView) {
                            if (activeView != "menu") return@LaunchedEffect
                            when(currentNav) {
                                // HomeScreen requests focus itself once data is ready.
                                // Avoid requesting early into the loading placeholder, which can
                                // cause a brief nav -> content -> nav -> content flicker.
                                NavDestination.Home, NavDestination.Movies, NavDestination.Series -> Unit
                                NavDestination.Search -> {
                                    delay(200) // Increased for stability
                                    val target = searchFocusTarget
                                    if (target != null) {
                                        searchFocusTarget = null
                                        when (target) {
                                            "movies" -> searchMoviesViewMoreRequester.requestFocus()
                                            "series" -> searchSeriesViewMoreRequester.requestFocus()
                                            "poster" -> searchResultsRequester.requestFocus()
                                            "discover" -> searchDiscoverRequester.requestFocus()
                                        }
                                    } else {
                                        searchEntryRequester.requestFocus()
                                    }
                                }
                                NavDestination.Settings -> {
                                    delay(200) // Increased for stability
                                    settingsEntryRequester.requestFocus()
                                }
                                NavDestination.Watchlist -> {
                                    delay(200)
                                    watchlistEntryRequester.requestFocus()
                                }
                                else -> Unit
                            }
                        }

                        // Focus restoration after navPosition change (Crossfade animation)
                        val navPosition = currentProfile?.navPosition ?: "left"
                        LaunchedEffect(navPosition) {
                            if (activeView == "menu" && currentNav == NavDestination.Settings) {
                                delay(450) // Wait for Crossfade (400ms) + buffer
                                settingsEntryRequester.requestFocus()
                            }
                        }


                            // CONDITIONAL NAVIGATION RENDERING (no animation)
                            val view = activeView
                            if (view == "menu") {
                                // Double-back-to-exit: two rapid back presses exit the app
                                var lastBackPressMs by remember { mutableStateOf(0L) }
                                var settingsContentFocused by remember { mutableStateOf(false) }

                                // Shared content composable
                                // Shared navigation handler
                                val handleNavigate: (NavDestination) -> Unit = { destination ->
                                    if (destination == NavDestination.Exit) {
                                        powerOff()
                                    } else if (currentNav == destination) {
                                        // Already here - just focus content
                                        when(destination) {
                                            NavDestination.Home, NavDestination.Movies, NavDestination.Series -> homeEntryRequester.requestFocus()
                                            NavDestination.Search -> searchEntryRequester.requestFocus()
                                            NavDestination.Settings -> settingsEntryRequester.requestFocus()
                                            NavDestination.Watchlist -> watchlistEntryRequester.requestFocus()
                                            else -> {}
                                        }
                                    } else {
                                        if (currentNav == NavDestination.Search) searchFocusTarget = null
                                        if (currentNav == NavDestination.Settings) settingsContentFocused = false
                                        currentNav = destination
                                    }
                                }

                                // Shared enter content handler
                                val handleEnterContent: () -> Unit = {
                                    when(currentNav) {
                                        NavDestination.Home, NavDestination.Movies, NavDestination.Series -> homeEntryRequester.requestFocus()
                                        NavDestination.Search -> searchEntryRequester.requestFocus()
                                        NavDestination.Settings -> settingsEntryRequester.requestFocus()
                                        NavDestination.Watchlist -> watchlistEntryRequester.requestFocus()
                                        else -> {}
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .onPreviewKeyEvent { event ->
                                            if (settingsContentFocused) return@onPreviewKeyEvent false
                                            if (event.key == Key.Back && event.type == KeyEventType.KeyDown) {
                                                // When the key was pressed rather than when it got here, so a slow
                                                // frame between two quick presses can't split them
                                                val now = event.nativeKeyEvent.eventTime
                                                if (now - lastBackPressMs < DOUBLE_BACK_EXIT_WINDOW_MS) {
                                                    powerOff()
                                                    true
                                                } else {
                                                    lastBackPressMs = now
                                                    false
                                                }
                                            } else {
                                                false
                                            }
                                        }
                                ) {
                                Crossfade(targetState = navPosition, animationSpec = tween(400), label = "NavSwitcher") { position ->
                                if (position == "top") {
                                    TopNavigationBar(
                                        currentDestination = currentNav,
                                        currentProfile = currentProfile,
                                        topNavRequesters = drawerRequesters,
                                        onNavigate = handleNavigate,
                                        onEnterContent = handleEnterContent,
                                        onLogout = {
                                            sessionProfileId = null
                                            sessionRestoreAttemptedProfileId = null

                                            activeView = "menu"
                                            themeManager.resetTheme()
                                            mainViewModel.logout()
                                        },
                                        onExit = { powerOff() },
                                        content = {
                                            when (currentNav) {
                                                NavDestination.Home, NavDestination.Movies, NavDestination.Series -> {
                                                    val vm = hiltViewModel<HomeViewModel>()
                                                    val tab = if(currentNav == NavDestination.Home) "home" else if(currentNav == NavDestination.Movies) "movies" else "series"
                                                    val dashboardTab = DashboardTab.fromString(tab)

                                                    key(tab) {
                                                        LaunchedEffect(tab, currentProfile?.id) { vm.loadScreen(tab, currentProfile) }
                                                        HomeScreen(
                                                            tab = dashboardTab,
                                                            viewModel = vm,
                                                            currentProfile = currentProfile,
                                                            entryRequester = homeEntryRequester,
                                                            drawerRequester = drawerRequesters[currentNav]!!,
                                                            onMovieClick = { movie ->
                                                                selectedMovieId = movie.id
                                                                selectedMovieType = movie.type
                                                                selectedMovieTitle = movie.name
                                                                selectedMoviePoster = movie.poster ?: ""
                                                                selectedMovieBackground = movie.background ?: ""
                                                                selectedMovieLogo = movie.logo ?: ""
                                                                selectedAddonBaseUrl = movie.addonBaseUrl
                                                                detailsResumePlaybackHint = null
                                                                selectedPlaybackId = movie.id
                                                                selectedPlaybackType = movie.type
                                                                selectedPlaybackTitle = movie.name
                                                                selectedPlaybackPoster = movie.poster ?: ""
                                                                previousView = "menu"
                                                                activeView = "details"
                                                            },
                                                            onViewMore = { title, items, configId ->
                                                                gridViewTitle = title
                                                                gridViewItems = items
                                                                gridViewConfigId = configId
                                                                activeView = "grid"
                                                            }
                                                        )
                                                    }
                                                }
                                                NavDestination.Search -> {
                                                    val searchHomeVm = hiltViewModel<HomeViewModel>()
                                                    SearchScreen(
                                                        currentProfile = currentProfile,
                                                        watchedIds = searchHomeVm.state.collectAsState().value.watchedIds,
                                                        onMovieClick = { movie ->
                                                            selectedMovieId = movie.id
                                                            selectedMovieType = movie.type
                                                            selectedMovieTitle = movie.name
                                                            selectedMoviePoster = movie.poster ?: ""
                                                            selectedMovieBackground = movie.background ?: ""
                                                            selectedMovieLogo = movie.logo ?: ""
                                                            selectedAddonBaseUrl = movie.addonBaseUrl
                                                            detailsResumePlaybackHint = null
                                                            selectedPlaybackId = movie.id
                                                            selectedPlaybackType = movie.type
                                                            selectedPlaybackTitle = movie.name
                                                            selectedPlaybackPoster = movie.poster ?: ""
                                                            searchFocusTarget = "poster"
                                                            previousView = "menu"
                                                            activeView = "details"
                                                        },
                                                        onViewMore = { title, items ->
                                                            searchFocusTarget = if (title == "Movies") "movies" else "series"
                                                            gridViewTitle = title
                                                            gridViewItems = items
                                                            gridViewConfigId = ""
                                                            activeView = "grid"
                                                        },
                                                        moviesViewMoreRequester = searchMoviesViewMoreRequester,
                                                        seriesViewMoreRequester = searchSeriesViewMoreRequester,
                                                        resultsRequester = searchResultsRequester,
                                                        discoverRequester = searchDiscoverRequester,
                                                        lastFocusedId = searchLastFocusedId,
                                                        onFocusedIdChange = { searchLastFocusedId = it },
                                                        onDiscoverClick = { movie ->
                                                            selectedMovieId = movie.id
                                                            selectedMovieType = movie.type
                                                            selectedMovieTitle = movie.name
                                                            selectedMoviePoster = movie.poster ?: ""
                                                            selectedMovieBackground = movie.background ?: ""
                                                            selectedMovieLogo = movie.logo ?: ""
                                                            selectedAddonBaseUrl = movie.addonBaseUrl
                                                            detailsResumePlaybackHint = null
                                                            selectedPlaybackId = movie.id
                                                            selectedPlaybackType = movie.type
                                                            selectedPlaybackTitle = movie.name
                                                            selectedPlaybackPoster = movie.poster ?: ""
                                                            searchFocusTarget = "discover"
                                                            previousView = "menu"
                                                            activeView = "details"
                                                        },
                                                        entryRequester = searchEntryRequester,
                                                        drawerRequester = drawerRequesters[NavDestination.Search]!!
                                                    )
                                                }
                                                NavDestination.Profile -> {
                                                    sessionProfileId = null
                                                    sessionRestoreAttemptedProfileId = null
                                                    activeView = "menu"
                                                    themeManager.resetTheme()
                                                    mainViewModel.logout()
                                                }
                                                NavDestination.Watchlist -> {
                                                    val watchlistHomeVm = hiltViewModel<HomeViewModel>()
                                                    WatchlistScreen(
                                                        currentProfile = currentProfile,
                                                        entryRequester = watchlistEntryRequester,
                                                        drawerRequester = drawerRequesters[NavDestination.Watchlist]!!,
                                                        watchedIds = watchlistHomeVm.state.collectAsState().value.watchedIds,
                                                        onMovieClick = { movie ->
                                                            selectedMovieId = movie.id
                                                            selectedMovieType = movie.type
                                                            selectedMovieTitle = movie.name
                                                            selectedMoviePoster = movie.poster ?: ""
                                                            selectedMovieBackground = movie.background ?: ""
                                                            selectedMovieLogo = movie.logo ?: ""
                                                            selectedAddonBaseUrl = movie.addonBaseUrl
                                                            detailsResumePlaybackHint = null
                                                            selectedPlaybackId = movie.id
                                                            selectedPlaybackType = movie.type
                                                            selectedPlaybackTitle = movie.name
                                                            selectedPlaybackPoster = movie.poster ?: ""
                                                            previousView = "menu"
                                                            activeView = "details"
                                                        }
                                                    )
                                                }
                                                NavDestination.Settings -> {
                                                    val homeVm = hiltViewModel<HomeViewModel>()
                                                    SettingsScreen(
                                                        currentProfile = currentProfile,
                                                        onBack = {
                                                            currentNav = NavDestination.Home
                                                            drawerRequesters[NavDestination.Home]?.requestFocus()
                                                        },
                                                        entryRequester = settingsEntryRequester,
                                                        drawerRequester = drawerRequesters[NavDestination.Settings]!!,
                                                        onDashboardChanged = { homeVm.invalidate() },
                                                        onContentFocusChanged = { settingsContentFocused = it }
                                                    )
                                                }
                                                NavDestination.Exit -> { /* App closes */ }
                                            }
                                        }
                                    )
                                } else { // position == "left"
                                    NavDrawer(
                                        currentDestination = currentNav,
                                        currentProfile = currentProfile,
                                        drawerRequesters = drawerRequesters,
                                        onNavigate = handleNavigate,
                                        onClose = handleEnterContent,
                                        content = {
                                            when (currentNav) {
                                                NavDestination.Home, NavDestination.Movies, NavDestination.Series -> {
                                                    val vm = hiltViewModel<HomeViewModel>()
                                                    val tab = if(currentNav == NavDestination.Home) "home" else if(currentNav == NavDestination.Movies) "movies" else "series"
                                                    val dashboardTab = DashboardTab.fromString(tab)

                                                    key(tab) {
                                                        LaunchedEffect(tab, currentProfile?.id) { vm.loadScreen(tab, currentProfile) }
                                                        HomeScreen(
                                                            tab = dashboardTab,
                                                            viewModel = vm,
                                                            currentProfile = currentProfile,
                                                            entryRequester = homeEntryRequester,
                                                            drawerRequester = drawerRequesters[currentNav]!!,
                                                            onMovieClick = { movie ->
                                                                selectedMovieId = movie.id
                                                                selectedMovieType = movie.type
                                                                selectedMovieTitle = movie.name
                                                                selectedMoviePoster = movie.poster ?: ""
                                                                selectedMovieBackground = movie.background ?: ""
                                                                selectedMovieLogo = movie.logo ?: ""
                                                                selectedAddonBaseUrl = movie.addonBaseUrl
                                                                detailsResumePlaybackHint = null
                                                                selectedPlaybackId = movie.id
                                                                selectedPlaybackType = movie.type
                                                                selectedPlaybackTitle = movie.name
                                                                selectedPlaybackPoster = movie.poster ?: ""
                                                                previousView = "menu"
                                                                activeView = "details"
                                                            },
                                                            onViewMore = { title, items, configId ->
                                                                gridViewTitle = title
                                                                gridViewItems = items
                                                                gridViewConfigId = configId
                                                                activeView = "grid"
                                                            }
                                                        )
                                                    }
                                                }
                                                NavDestination.Search -> {
                                                    val searchHomeVm = hiltViewModel<HomeViewModel>()
                                                    SearchScreen(
                                                        currentProfile = currentProfile,
                                                        watchedIds = searchHomeVm.state.collectAsState().value.watchedIds,
                                                        onMovieClick = { movie ->
                                                            selectedMovieId = movie.id
                                                            selectedMovieType = movie.type
                                                            selectedMovieTitle = movie.name
                                                            selectedMoviePoster = movie.poster ?: ""
                                                            selectedMovieBackground = movie.background ?: ""
                                                            selectedMovieLogo = movie.logo ?: ""
                                                            selectedAddonBaseUrl = movie.addonBaseUrl
                                                            detailsResumePlaybackHint = null
                                                            selectedPlaybackId = movie.id
                                                            selectedPlaybackType = movie.type
                                                            selectedPlaybackTitle = movie.name
                                                            selectedPlaybackPoster = movie.poster ?: ""
                                                            searchFocusTarget = "poster"
                                                            previousView = "menu"
                                                            activeView = "details"
                                                        },
                                                        onViewMore = { title, items ->
                                                            searchFocusTarget = if (title == "Movies") "movies" else "series"
                                                            gridViewTitle = title
                                                            gridViewItems = items
                                                            gridViewConfigId = ""
                                                            activeView = "grid"
                                                        },
                                                        moviesViewMoreRequester = searchMoviesViewMoreRequester,
                                                        seriesViewMoreRequester = searchSeriesViewMoreRequester,
                                                        resultsRequester = searchResultsRequester,
                                                        discoverRequester = searchDiscoverRequester,
                                                        lastFocusedId = searchLastFocusedId,
                                                        onFocusedIdChange = { searchLastFocusedId = it },
                                                        onDiscoverClick = { movie ->
                                                            selectedMovieId = movie.id
                                                            selectedMovieType = movie.type
                                                            selectedMovieTitle = movie.name
                                                            selectedMoviePoster = movie.poster ?: ""
                                                            selectedMovieBackground = movie.background ?: ""
                                                            selectedMovieLogo = movie.logo ?: ""
                                                            selectedAddonBaseUrl = movie.addonBaseUrl
                                                            detailsResumePlaybackHint = null
                                                            selectedPlaybackId = movie.id
                                                            selectedPlaybackType = movie.type
                                                            selectedPlaybackTitle = movie.name
                                                            selectedPlaybackPoster = movie.poster ?: ""
                                                            searchFocusTarget = "discover"
                                                            previousView = "menu"
                                                            activeView = "details"
                                                        },
                                                        entryRequester = searchEntryRequester,
                                                        drawerRequester = drawerRequesters[NavDestination.Search]!!
                                                    )
                                                }
                                                NavDestination.Profile -> {
                                                    sessionProfileId = null
                                                    sessionRestoreAttemptedProfileId = null
                                                    activeView = "menu"
                                                    themeManager.resetTheme()
                                                    mainViewModel.logout()
                                                }
                                                NavDestination.Watchlist -> {
                                                    val watchlistHomeVm = hiltViewModel<HomeViewModel>()
                                                    WatchlistScreen(
                                                        currentProfile = currentProfile,
                                                        entryRequester = watchlistEntryRequester,
                                                        drawerRequester = drawerRequesters[NavDestination.Watchlist]!!,
                                                        watchedIds = watchlistHomeVm.state.collectAsState().value.watchedIds,
                                                        onMovieClick = { movie ->
                                                            selectedMovieId = movie.id
                                                            selectedMovieType = movie.type
                                                            selectedMovieTitle = movie.name
                                                            selectedMoviePoster = movie.poster ?: ""
                                                            selectedMovieBackground = movie.background ?: ""
                                                            selectedMovieLogo = movie.logo ?: ""
                                                            selectedAddonBaseUrl = movie.addonBaseUrl
                                                            detailsResumePlaybackHint = null
                                                            selectedPlaybackId = movie.id
                                                            selectedPlaybackType = movie.type
                                                            selectedPlaybackTitle = movie.name
                                                            selectedPlaybackPoster = movie.poster ?: ""
                                                            previousView = "menu"
                                                            activeView = "details"
                                                        }
                                                    )
                                                }
                                                NavDestination.Settings -> {
                                                    val homeVm = hiltViewModel<HomeViewModel>()
                                                    SettingsScreen(
                                                        currentProfile = currentProfile,
                                                        onBack = {
                                                            currentNav = NavDestination.Home
                                                            drawerRequesters[NavDestination.Home]?.requestFocus()
                                                        },
                                                        entryRequester = settingsEntryRequester,
                                                        drawerRequester = drawerRequesters[NavDestination.Settings]!!,
                                                        onDashboardChanged = { homeVm.invalidate() },
                                                        onContentFocusChanged = { settingsContentFocused = it }
                                                    )
                                                }
                                                NavDestination.Exit -> { /* App closes */ }
                                            }
                                        }
                                    )
                                }
                                } // Crossfade end
                                } // Double-back Box end
                        } else if (view == "grid") {
                            val gridVm = hiltViewModel<HomeViewModel>()
                            GridViewScreen(
                                title = gridViewTitle,
                                items = gridViewItems,
                                lastFocusedIndex = gridRestoreState.focusedIndex,
                                onFocusChange = { gridRestoreState.focusedIndex = it },
                                onMovieClick = { movie ->
                                    selectedMovieId = movie.id
                                    selectedMovieType = movie.type
                                    selectedMovieTitle = movie.name
                                    selectedMoviePoster = movie.poster ?: ""
                                    selectedMovieBackground = movie.background ?: ""
                                    selectedMovieLogo = movie.logo ?: ""
                                    selectedAddonBaseUrl = movie.addonBaseUrl
                                    detailsResumePlaybackHint = null
                                    selectedPlaybackId = movie.id
                                    selectedPlaybackType = movie.type
                                    selectedPlaybackTitle = movie.name
                                    selectedPlaybackPoster = movie.poster ?: ""
                                    previousView = "grid"
                                    activeView = "details"
                                },
                                onBack = { 
                                    gridRestoreState.focusedIndex = null  // Reset for next time
                                    gridRestoreState.scrollIndex = 0  // Reset scroll position
                                    gridRestoreState.scrollOffset = 0
                                    activeView = "menu"
                                },
                                onLoadMore = {
                                    if (gridViewConfigId.isNotEmpty()) {
                                        gridVm.loadMoreItems(gridViewConfigId)
                                    }
                                },
                                initialScrollIndex = gridRestoreState.scrollIndex,
                                initialScrollOffset = gridRestoreState.scrollOffset,
                                onScrollPositionChange = { index, offset ->
                                    gridRestoreState.scrollIndex = index
                                    gridRestoreState.scrollOffset = offset
                                },
                                watchedIds = gridVm.state.collectAsState().value.watchedIds
                            )
                            // Sync gridViewItems when ViewModel state updates (after loadMoreItems)
                            val vmState by gridVm.state.collectAsState()
                            LaunchedEffect(vmState.rows) {
                                if (gridViewConfigId.isNotEmpty()) {
                                    val updatedRow = vmState.rows.find { it.configId == gridViewConfigId }
                                    if (updatedRow != null && updatedRow.items.size > gridViewItems.size) {
                                        gridViewItems = updatedRow.items
                                    }
                                }
                            }
                        } else if (view == "details" || (view == "player" && selectedPlaybackId.startsWith("trailer_"))) {
                            val detailsNavController = rememberNavController()
                            val startRoute = "detail/${java.net.URLEncoder.encode(selectedMovieType, "UTF-8")}/${java.net.URLEncoder.encode(selectedMovieId, "UTF-8")}?addon=${java.net.URLEncoder.encode(selectedAddonBaseUrl ?: "", "UTF-8")}&resume=${java.net.URLEncoder.encode(detailsResumePlaybackHint ?: "", "UTF-8")}"

                            // Navigate to initial details when first entering
                            LaunchedEffect(selectedMovieType, selectedMovieId) {
                                val currentRoute = detailsNavController.currentBackStackEntry?.destination?.route
                                if (currentRoute == null || currentRoute == "detail_start") {
                                    detailsNavController.navigate(startRoute) {
                                        popUpTo("detail_start") { inclusive = true }
                                    }
                                }
                            }

                            BackHandler {
                                if (!detailsNavController.popBackStack()) {
                                    activeView = previousView
                                }
                            }

                            // Shared onPlayClick lambda for all detail screens
                            val onPlayClick: (String, String, String, String, String, String, com.lumera.app.data.model.stremio.Stream, List<com.lumera.app.domain.AddonSubtitle>, List<com.lumera.app.data.model.stremio.Stream>, List<com.lumera.app.data.model.stremio.MetaVideo>) -> Unit = { url, playbackId, playbackType, playbackTitle, seriesTitle, logo, stream, addonSubtitles, availableStreams, episodes ->
                                val resolvedPlaybackTitle = playbackTitle.ifBlank { selectedMovieTitle }
                                val resolvedSeriesTitle = seriesTitle.ifBlank { selectedMovieTitle }
                                val isSeriesPlayback = playbackType.equals("series", ignoreCase = true) ||
                                    playbackType.equals("tv", ignoreCase = true)
                                if (isSeriesPlayback && resolvedSeriesTitle.isNotBlank()) {
                                    selectedMovieTitle = resolvedSeriesTitle
                                }
                                if (logo.isNotBlank()) selectedMovieLogo = logo
                                playerState.currentEpisodeList = episodes
                                playerState.currentStream = stream
                                val subtitlePayload = buildSubtitlePayload(stream, addonSubtitles)
                                val sourcePayloadInput = if (availableStreams.isNotEmpty()) availableStreams else listOf(stream)
                                val sourcePayload = buildSourcePayload(streams = sourcePayloadInput, selectedStream = stream)
                                playerState.pendingSourceSelection = PendingSourceSelection(
                                    playbackId = playbackId,
                                    launchedStream = stream,
                                    candidateStreams = sourcePayloadInput
                                )
                                if (url.startsWith("magnet:")) {
                                    uiScope.launch {
                                        selectedPlaybackId = playbackId
                                        selectedPlaybackType = playbackType
                                        selectedPlaybackTitle = resolvedPlaybackTitle
                                        selectedPlaybackPoster = selectedMoviePoster
                                        selectedTrailerAudioUrl = ""
                                        playerState.selectedPlayerSubtitles = subtitlePayload
                                        playerState.selectedPlayerSources = sourcePayload
                                        selectedVideoUrl = ""
                                        torrentProgress = TorrentProgress("Connecting to peers...")
                                        activeView = "player"
                                        TorrentService.onStreamReady = { localUrl ->
                                            torrentProgress = null
                                            selectedVideoUrl = localUrl
                                        }
                                        TorrentService.onStreamError = { error ->
                                            torrentProgress = null
                                            if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Stream error: $error")
                                        }
                                        TorrentService.onStreamProgress = { progress ->
                                            torrentProgress = progress
                                        }
                                        val intent = Intent(this@MainActivity, TorrentService::class.java).apply {
                                            putExtra("MAGNET_LINK", url)
                                            putExtra("FILE_IDX", stream.fileIdx ?: -1)
                                            putExtra("FILE_NAME", stream.behaviorHints?.filename ?: "")
                                        }
                                        startService(intent)
                                    }
                                } else {
                                    stopService(Intent(this@MainActivity, TorrentService::class.java))
                                    uiScope.launch {
                                        selectedPlaybackId = playbackId
                                        selectedPlaybackType = playbackType
                                        selectedPlaybackTitle = resolvedPlaybackTitle
                                        selectedPlaybackPoster = selectedMoviePoster
                                        selectedTrailerAudioUrl = ""
                                        playerState.selectedPlayerSubtitles = subtitlePayload
                                        playerState.selectedPlayerSources = sourcePayload
                                        selectedVideoUrl = url
                                        when (currentProfile?.playerPreference) {
                                            "external" -> launchExternalPlayer(this@MainActivity, url)
                                            "ask" -> playerState.showPlayerChoiceDialog = true
                                            else -> activeView = "player"
                                        }
                                    }
                                }
                            }

                            NavHost(
                                navController = detailsNavController,
                                startDestination = "detail_start",
                            ) {
                                composable("detail_start") { }
                                composable(
                                    "detail/{type}/{id}?addon={addon}&resume={resume}",
                                    arguments = listOf(
                                        navArgument("type") { type = NavType.StringType },
                                        navArgument("id") { type = NavType.StringType },
                                        navArgument("addon") { type = NavType.StringType; defaultValue = "" },
                                        navArgument("resume") { type = NavType.StringType; defaultValue = "" }
                                    )
                                ) { backStackEntry ->
                                    val detailType = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("type") ?: "movie", "UTF-8")
                                    val detailId = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("id") ?: "", "UTF-8")
                                    val detailAddon = backStackEntry.arguments?.getString("addon")?.takeIf { it.isNotEmpty() }
                                    val detailResume = backStackEntry.arguments?.getString("resume")?.takeIf { it.isNotEmpty() }

                                    DetailsScreen(
                                        type = detailType,
                                        id = detailId,
                                        addonBaseUrl = detailAddon,
                                        resumePlaybackHint = detailResume,
                                        autoSelectSource = currentProfile?.autoSelectSource ?: false,
                                        rememberSourceSelection = currentProfile?.rememberSourceSelection ?: true,
                                        onPosterResolved = { selectedMoviePoster = it },
                                        onPlayClick = onPlayClick,
                                        onNavigateToDetails = { navType, navId ->
                                            val route = "detail/${java.net.URLEncoder.encode(navType, "UTF-8")}/${java.net.URLEncoder.encode(navId, "UTF-8")}"
                                            detailsNavController.navigate(route)
                                        },
                                        onNavigateToCastDetail = { castPersonId, castPersonName ->
                                            val route = "cast_detail/$castPersonId/${java.net.URLEncoder.encode(castPersonName, "UTF-8")}"
                                            detailsNavController.navigate(route)
                                        },
                                        onNavigateToStudioDetail = { entityId, entityKind, entityName, sourceType ->
                                            val route = "studio_detail/$entityId/$entityKind/${java.net.URLEncoder.encode(entityName, "UTF-8")}/$sourceType"
                                            detailsNavController.navigate(route)
                                        },
                                        trailerReturnToken = trailerReturnToken,
                                        isTrailerLoading = isTrailerLoading,
                                        onTrailerClick = { youtubeKey, trailerName ->
                                            // The YouTube app plays it; taking the video out of YouTube's pages is the
                                            // stand-in for a TV without YouTube
                                            if (!com.lumera.app.data.trailer.TrailerLauncher.open(this@MainActivity, youtubeKey)) {
                                                isTrailerLoading = true
                                                uiScope.launch {
                                                    val extractor = com.lumera.app.data.trailer.YouTubeExtractor()
                                                    val source = extractor.extractPlaybackSource(youtubeKey)
                                                    isTrailerLoading = false
                                                    if (source != null) {
                                                        selectedVideoUrl = source.videoUrl
                                                        selectedTrailerAudioUrl = source.audioUrl ?: ""
                                                        selectedPlaybackId = "trailer_$youtubeKey"
                                                        selectedPlaybackType = selectedMovieType
                                                        selectedPlaybackTitle = trailerName
                                                        selectedPlaybackPoster = selectedMoviePoster
                                                        playerState.selectedPlayerSubtitles = emptyList()
                                                        playerState.selectedPlayerSources = emptyList()
                                                        activeView = "player"
                                                    } else {
                                                        showTrailerError = true
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                                composable(
                                    "cast_detail/{personId}/{personName}",
                                    arguments = listOf(
                                        navArgument("personId") { type = NavType.StringType },
                                        navArgument("personName") { type = NavType.StringType }
                                    )
                                ) { backStackEntry ->
                                    val castPersonId = (backStackEntry.arguments?.getString("personId") ?: "0").toIntOrNull() ?: 0
                                    val castPersonName = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("personName") ?: "", "UTF-8")

                                    com.lumera.app.ui.cast.CastDetailScreen(
                                        personId = castPersonId,
                                        personName = castPersonName,
                                        onBackPress = { detailsNavController.popBackStack() },
                                        onNavigateToDetails = { navType, navId ->
                                            val route = "detail/${java.net.URLEncoder.encode(navType, "UTF-8")}/${java.net.URLEncoder.encode(navId, "UTF-8")}"
                                            detailsNavController.navigate(route)
                                        }
                                    )
                                }
                                composable(
                                    "studio_detail/{entityId}/{entityKind}/{entityName}/{sourceType}",
                                    arguments = listOf(
                                        navArgument("entityId") { type = NavType.StringType },
                                        navArgument("entityKind") { type = NavType.StringType },
                                        navArgument("entityName") { type = NavType.StringType },
                                        navArgument("sourceType") { type = NavType.StringType }
                                    )
                                ) { backStackEntry ->
                                    val studioEntityId = (backStackEntry.arguments?.getString("entityId") ?: "0").toIntOrNull() ?: 0
                                    val studioEntityKind = backStackEntry.arguments?.getString("entityKind") ?: "company"
                                    val studioEntityName = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("entityName") ?: "", "UTF-8")
                                    val studioSourceType = backStackEntry.arguments?.getString("sourceType") ?: "movie"

                                    com.lumera.app.ui.studio.StudioDetailScreen(
                                        entityId = studioEntityId,
                                        entityKind = studioEntityKind,
                                        entityName = studioEntityName,
                                        sourceType = studioSourceType,
                                        onBackPress = { detailsNavController.popBackStack() },
                                        onNavigateToDetails = { navType, navId ->
                                            val route = "detail/${java.net.URLEncoder.encode(navType, "UTF-8")}/${java.net.URLEncoder.encode(navId, "UTF-8")}"
                                            detailsNavController.navigate(route)
                                        }
                                    )
                                }
                            }
                        }
                        // Who is watching stays in the top left corner, where the side menu has it
                        if (view == "grid" || view == "details") {
                            com.lumera.app.ui.navigation.ProfileBadge(currentProfile)
                        }
                        if (view == "player") {
                            if (selectedVideoUrl.isBlank() && torrentProgress == null) {
                                LaunchedEffect(Unit) { activeView = "details" }
                            } else {
                            val rememberedTrackSelection = remember(selectedPlaybackId) {
                                playbackTrackSelectionStore.getSelection(selectedPlaybackId)
                            }
                            val playerSources = remember(playerState.selectedPlayerSources) { playerState.selectedPlayerSources }
                            val playerSubtitles = remember(playerState.selectedPlayerSubtitles) {
                                playerState.selectedPlayerSubtitles.map { subtitle ->
                                    PlayerSubtitleSource(
                                        id = subtitle.id,
                                        url = subtitle.url,
                                        label = subtitle.name,
                                        language = subtitle.language
                                    )
                                }
                            }

                            // Compute next episode
                            val isSeries = selectedPlaybackType.equals("series", ignoreCase = true)
                            val shouldAutoplay = currentProfile?.autoplayNextEpisode == true && isSeries
                            val nextEpisode = remember(selectedPlaybackId, selectedMovieId, playerState.currentEpisodeList, isSeries) {
                                if (isSeries && playerState.currentEpisodeList.isNotEmpty()) {
                                    findNextEpisode(selectedMovieId, selectedPlaybackId, playerState.currentEpisodeList)
                                } else null
                            }
                            val nextEpisodeInfo = remember(nextEpisode) {
                                nextEpisode?.let { ep ->
                                    NextEpisodeInfo(
                                        title = episodeDisplayTitle(ep),
                                        thumbnail = ep.thumbnail,
                                        seasonNumber = ep.season,
                                        episodeNumber = ep.episode
                                    )
                                }
                            }

                            // Fetch skip intro/outro segments from IntroDB
                            val skipIntroEnabled = currentProfile?.skipIntro == true
                            val autoplayEnabled = currentProfile?.autoplayNextEpisode == true
                            val needIntroDB = skipIntroEnabled || autoplayEnabled
                            var skipSegmentInfo by remember { mutableStateOf<SkipSegmentInfo?>(null) }
                            LaunchedEffect(selectedPlaybackId, needIntroDB, skipIntroEnabled) {
                                skipSegmentInfo = null
                                if (!needIntroDB) return@LaunchedEffect
                                if (!isSeries || selectedPlaybackId.isBlank()) return@LaunchedEffect
                                val parts = selectedPlaybackId.split(":")
                                if (parts.size < 3) return@LaunchedEffect
                                val imdbId = parts.dropLast(2).joinToString(":")
                                val season = parts[parts.lastIndex - 1].toIntOrNull() ?: return@LaunchedEffect
                                val episode = parts.last().toIntOrNull() ?: return@LaunchedEffect
                                val response = introRepository.getSegments(imdbId, season, episode)
                                if (response != null) {
                                    skipSegmentInfo = SkipSegmentInfo(
                                        introStartMs = if (skipIntroEnabled) response.intro?.start_ms else null,
                                        introEndMs = if (skipIntroEnabled) response.intro?.end_ms else null,
                                        outroStartMs = response.outro?.start_ms,
                                        outroEndMs = response.outro?.end_ms
                                    )
                                }
                            }

                            PlayerScreen(
                                videoUrl = selectedVideoUrl,
                                trailerAudioUrl = selectedTrailerAudioUrl.takeIf { it.isNotBlank() },
                                title = selectedPlaybackTitle.ifBlank { selectedMovieTitle },
                                seriesTitle = selectedMovieTitle.takeIf {
                                    selectedPlaybackType.equals("series", ignoreCase = true)
                                },
                                logoUrl = selectedMovieLogo.takeIf { it.isNotBlank() },
                                poster = selectedPlaybackPoster,
                                movieId = selectedPlaybackId,
                                mediaType = selectedPlaybackType,
                                sources = playerSources,
                                subtitles = playerSubtitles,
                                preferredAudioTrackId = rememberedTrackSelection?.audioTrackId,
                                preferredSubtitleTrackId = rememberedTrackSelection?.subtitleTrackId,
                                initialSubtitleDelayMs = rememberedTrackSelection?.subtitleDelayMs ?: 0L,
                                playbackSettings = PlaybackSettings(
                                    tunnelingEnabled = currentProfile?.tunnelingEnabled ?: false,
                                    mapDV7ToHevc = currentProfile?.mapDV7ToHevc ?: false,
                                    decoderPriority = currentProfile?.decoderPriority ?: 1,
                                    frameRateMatching = currentProfile?.frameRateMatching ?: false,
                                    autoplayNextEpisode = currentProfile?.autoplayNextEpisode ?: false,
                                    autoSelectSource = currentProfile?.autoSelectSource ?: false,
                                    autoplayThresholdMode = currentProfile?.autoplayThresholdMode ?: "percentage",
                                    autoplayThresholdPercent = currentProfile?.autoplayThresholdPercent ?: 95,
                                    autoplayThresholdSeconds = currentProfile?.autoplayThresholdSeconds ?: 30,
                                    preferredAudioLanguage = currentProfile?.preferredAudioLanguage ?: "",
                                    preferredAudioLanguageSecondary = currentProfile?.preferredAudioLanguageSecondary ?: "",
                                    preferredSubtitleLanguage = currentProfile?.preferredSubtitleLanguage ?: "",
                                    preferredSubtitleLanguageSecondary = currentProfile?.preferredSubtitleLanguageSecondary ?: "",
                                    subtitleSize = currentProfile?.subtitleSize ?: 100,
                                    subtitleOffset = currentProfile?.subtitleOffset ?: 0,
                                    subtitleTextColor = currentProfile?.subtitleTextColor?.toInt() ?: 0xFFFFFFFF.toInt(),
                                    subtitleBackgroundColor = currentProfile?.subtitleBackgroundColor?.toInt() ?: 0x00000000,
                                    assRendererEnabled = currentProfile?.assRendererEnabled ?: false
                                ),
                                skipSegmentInfo = skipSegmentInfo,
                                nextEpisodeInfo = if (nextEpisode != null) nextEpisodeInfo else null,
                                onAutoplayNextEpisode = if (nextEpisode != null) {
                                    { playerCurrentSourceUrl ->
                                        // Mark current episode as completed
                                        handlePlayerSessionEnd(
                                            sessionResult = PlayerSessionResult(
                                                positionMs = 0L,
                                                durationMs = null,
                                                isCompleted = true,
                                                selectedSourceUrl = playerCurrentSourceUrl ?: selectedVideoUrl,
                                                selectedAudioTrackId = null,
                                                selectedSubtitleTrackId = null
                                            ),
                                            selectedPlaybackId = selectedPlaybackId,
                                            playbackTrackSelectionStore = playbackTrackSelectionStore,
                                            sourceSelectionStore = sourceSelectionStore,
                                            pendingSourceSelection = playerState.pendingSourceSelection,
                                            onConsumePendingSelection = { playerState.pendingSourceSelection = null },
                                            onResumeHintResolved = { detailsResumePlaybackHint = it },
                                            rememberSourceSelection = currentProfile?.rememberSourceSelection ?: true
                                        )

                                        val nextPlaybackId = episodePlaybackId(selectedMovieId, nextEpisode)
                                        val nextStreamId = episodeStreamId(selectedMovieId, nextEpisode)
                                        val nextPlaybackTitle = episodeDisplayTitle(nextEpisode)

                                        playerState.episodeSwitchJob?.cancel()
                                        playerState.episodeSwitchJob = uiScope.launch {
                                            // Show loading feedback immediately
                                            val autoplay = currentProfile?.autoplayNextEpisode == true
                                            val autoSelect = currentProfile?.autoSelectSource == true
                                            val willAutoResolve = autoplay || autoSelect

                                            if (willAutoResolve) {
                                                playerState.isEpisodeSwitchLoading = true
                                            } else {
                                                playerState.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = nextPlaybackId,
                                                    playbackTitle = nextPlaybackTitle,
                                                    streams = null,
                                                    addonSubs = emptyList(),
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                            }

                                            val streamsDeferred = async { try { addonRepository.getStreams("series", nextStreamId) } catch (_: Exception) { emptyList() } }
                                            val subtitlesDeferred = async { try { subtitleRepository.getSubtitles("series", nextStreamId) } catch (_: Exception) { emptyList() } }

                                            val rawStreams = streamsDeferred.await()
                                            val addonSubs = subtitlesDeferred.await()

                                            val streams = if (currentProfile?.sourceSortingEnabled == true) {
                                                val enabledQ = StreamSortingService.parseEnabledQualities(currentProfile?.sourceEnabledQualities ?: "4k,1080p,720p,unknown")
                                                val excludeP = StreamSortingService.parseExcludePhrases(currentProfile?.sourceExcludePhrases ?: "")
                                                val addonOrders = addonRepository.getAddonSortOrders()
                                                val excludedF = StreamSortingService.parseExcludedFormats(currentProfile?.sourceExcludedFormats ?: "")
                                                streamSortingService.sortAndFilter(rawStreams, enabledQ, excludeP, addonOrders, currentProfile?.sourceSortPrimary ?: "quality", currentProfile?.sourceMaxSizeGb ?: 0, excludedF)
                                            } else rawStreams

                                            if (streams.isEmpty()) {
                                                playerState.isEpisodeSwitchLoading = false
                                                playerState.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = nextPlaybackId,
                                                    playbackTitle = nextPlaybackTitle,
                                                    streams = emptyList(),
                                                    addonSubs = emptyList(),
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                                return@launch
                                            }

                                            // Resolve the actual stream the user was watching (may differ from initial if they switched sources)
                                            val actualStream = if (playerCurrentSourceUrl != null) {
                                                playerState.pendingSourceSelection?.candidateStreams?.firstOrNull { candidate ->
                                                    resolvePlayableSourceUrl(candidate) == playerCurrentSourceUrl
                                                } ?: playerState.currentStream
                                            } else playerState.currentStream

                                            // Priority 1: Same bingeGroup + same addon as current stream (when autoplay or autoselect is on)
                                            val currentBingeGroup = actualStream?.behaviorHints?.bingeGroup
                                            val currentAddonUrl = actualStream?.addonTransportUrl
                                            val bingeMatch = if ((autoplay || autoSelect) && !currentBingeGroup.isNullOrBlank()) {
                                                streams.firstOrNull {
                                                    it.behaviorHints?.bingeGroup == currentBingeGroup &&
                                                        it.addonTransportUrl == currentAddonUrl &&
                                                        (!it.url.isNullOrBlank() || !it.infoHash.isNullOrBlank())
                                                }
                                            } else null
                                            // Priority 2: Remembered source
                                            val rememberSource = currentProfile?.rememberSourceSelection ?: true
                                            val preferred = if (rememberSource) sourceSelectionStore.findPreferredStream(nextPlaybackId, streams) else null
                                            // Priority 3: First playable (only when autoSelectSource is on)
                                            val streamToPlay = bingeMatch
                                                ?: preferred
                                                ?: if (autoSelect) streams.firstOrNull { !it.url.isNullOrBlank() || !it.infoHash.isNullOrBlank() } else null

                                            if (streamToPlay == null) {
                                                playerState.isEpisodeSwitchLoading = false
                                                playerState.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = nextPlaybackId,
                                                    playbackTitle = nextPlaybackTitle,
                                                    streams = streams,
                                                    addonSubs = addonSubs,
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                                return@launch
                                            }

                                            val nextUrl = resolvePlayableSourceUrl(streamToPlay)
                                            if (nextUrl == null) {
                                                playerState.isEpisodeSwitchLoading = false
                                                playerState.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = nextPlaybackId,
                                                    playbackTitle = nextPlaybackTitle,
                                                    streams = streams,
                                                    addonSubs = addonSubs,
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                                return@launch
                                            }

                                            // Auto-resolved: clear loading + switch
                                            playerState.isEpisodeSwitchLoading = false
                                            playerState.pendingEpisodeSwitch = null

                                            val subtitlePayload = buildSubtitlePayload(streamToPlay, addonSubs)
                                            val sourcePayload = buildSourcePayload(streams, streamToPlay)

                                            playerState.pendingSourceSelection = PendingSourceSelection(
                                                playbackId = nextPlaybackId,
                                                launchedStream = streamToPlay,
                                                candidateStreams = streams
                                            )
                                            playerState.currentStream = streamToPlay

                                            if (nextUrl.startsWith("magnet:")) {
                                                selectedPlaybackId = nextPlaybackId
                                                selectedPlaybackType = "series"
                                                selectedPlaybackTitle = nextPlaybackTitle
                                                playerState.selectedPlayerSubtitles = subtitlePayload
                                                playerState.selectedPlayerSources = sourcePayload
                                                torrentProgress = TorrentProgress("Connecting to peers...")
                                                TorrentService.onStreamReady = { localUrl ->
                                                    torrentProgress = null
                                                    selectedVideoUrl = localUrl
                                                }
                                                TorrentService.onStreamError = { error ->
                                                    torrentProgress = null
                                                    if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Stream error: $error")
                                                }
                                                TorrentService.onStreamProgress = { progress ->
                                                    torrentProgress = progress
                                                }
                                                val intent = Intent(this@MainActivity, TorrentService::class.java).apply {
                                                    putExtra("MAGNET_LINK", nextUrl)
                                                    putExtra("FILE_IDX", streamToPlay.fileIdx ?: -1)
                                                    putExtra("FILE_NAME", streamToPlay.behaviorHints?.filename ?: "")
                                                }
                                                startService(intent)
                                            } else {
                                                stopService(Intent(this@MainActivity, TorrentService::class.java))
                                                selectedPlaybackId = nextPlaybackId
                                                selectedPlaybackType = "series"
                                                selectedPlaybackTitle = nextPlaybackTitle
                                                playerState.selectedPlayerSubtitles = subtitlePayload
                                                playerState.selectedPlayerSources = sourcePayload
                                                selectedVideoUrl = nextUrl
                                                // PlayerScreen will recompose due to movieId/videoUrl key change
                                            }
                                        }
                                    }
                                } else null,
                                episodes = playerState.currentEpisodeList,
                                currentPlaybackId = selectedPlaybackId,
                                onEpisodeSelected = if (playerState.currentEpisodeList.isNotEmpty()) {
                                    { episode, playerCurrentSourceUrl ->
                                        val epPlaybackId = episodePlaybackId(selectedMovieId, episode)
                                        val epStreamId = episodeStreamId(selectedMovieId, episode)
                                        val epTitle = episodeDisplayTitle(episode)

                                        playerState.episodeSwitchJob?.cancel()
                                        playerState.episodeSwitchJob = uiScope.launch {
                                            // Show loading feedback immediately
                                            val autoplay = currentProfile?.autoplayNextEpisode == true
                                            val autoSelect = currentProfile?.autoSelectSource == true
                                            val willAutoResolve = autoplay || autoSelect

                                            if (willAutoResolve) {
                                                playerState.isEpisodeSwitchLoading = true
                                            } else {
                                                playerState.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = epPlaybackId,
                                                    playbackTitle = epTitle,
                                                    streams = null,
                                                    addonSubs = emptyList(),
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                            }

                                            val streamsDeferred = async { try { addonRepository.getStreams("series", epStreamId) } catch (_: Exception) { emptyList() } }
                                            val subtitlesDeferred = async { try { subtitleRepository.getSubtitles("series", epStreamId) } catch (_: Exception) { emptyList() } }

                                            val rawStreams2 = streamsDeferred.await()
                                            val addonSubs = subtitlesDeferred.await()

                                            val streams = if (currentProfile?.sourceSortingEnabled == true) {
                                                val enabledQ = StreamSortingService.parseEnabledQualities(currentProfile?.sourceEnabledQualities ?: "4k,1080p,720p,unknown")
                                                val excludeP = StreamSortingService.parseExcludePhrases(currentProfile?.sourceExcludePhrases ?: "")
                                                val addonOrders = addonRepository.getAddonSortOrders()
                                                val excludedF = StreamSortingService.parseExcludedFormats(currentProfile?.sourceExcludedFormats ?: "")
                                                streamSortingService.sortAndFilter(rawStreams2, enabledQ, excludeP, addonOrders, currentProfile?.sourceSortPrimary ?: "quality", currentProfile?.sourceMaxSizeGb ?: 0, excludedF)
                                            } else rawStreams2

                                            if (streams.isEmpty()) {
                                                playerState.isEpisodeSwitchLoading = false
                                                playerState.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = epPlaybackId,
                                                    playbackTitle = epTitle,
                                                    streams = emptyList(),
                                                    addonSubs = emptyList(),
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                                return@launch
                                            }

                                            // Resolve the actual stream the user was watching
                                            val actualStream = if (playerCurrentSourceUrl != null) {
                                                playerState.pendingSourceSelection?.candidateStreams?.firstOrNull { candidate ->
                                                    resolvePlayableSourceUrl(candidate) == playerCurrentSourceUrl
                                                } ?: playerState.currentStream
                                            } else playerState.currentStream

                                            // Priority 1: Same bingeGroup + same addon as current stream (when autoplay or autoselect is on)
                                            val currentBingeGroup = actualStream?.behaviorHints?.bingeGroup
                                            val currentAddonUrl = actualStream?.addonTransportUrl
                                            val bingeMatch = if ((autoplay || autoSelect) && !currentBingeGroup.isNullOrBlank()) {
                                                streams.firstOrNull {
                                                    it.behaviorHints?.bingeGroup == currentBingeGroup &&
                                                        it.addonTransportUrl == currentAddonUrl &&
                                                        (!it.url.isNullOrBlank() || !it.infoHash.isNullOrBlank())
                                                }
                                            } else null

                                            // Priority 2: Auto-select first available (only when autoSelectSource is on)
                                            val streamToPlay = bingeMatch
                                                ?: if (autoSelect) streams.firstOrNull { !it.url.isNullOrBlank() || !it.infoHash.isNullOrBlank() } else null

                                            if (streamToPlay == null) {
                                                playerState.isEpisodeSwitchLoading = false
                                                playerState.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = epPlaybackId,
                                                    playbackTitle = epTitle,
                                                    streams = streams,
                                                    addonSubs = addonSubs,
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                                return@launch
                                            }

                                            val epUrl = resolvePlayableSourceUrl(streamToPlay)
                                            if (epUrl == null) {
                                                playerState.isEpisodeSwitchLoading = false
                                                playerState.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = epPlaybackId,
                                                    playbackTitle = epTitle,
                                                    streams = streams,
                                                    addonSubs = addonSubs,
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                                return@launch
                                            }

                                            // Auto-resolved: clear loading + switch
                                            playerState.isEpisodeSwitchLoading = false
                                            playerState.pendingEpisodeSwitch = null
                                            handlePlayerSessionEnd(
                                                sessionResult = PlayerSessionResult(
                                                    positionMs = 0L,
                                                    durationMs = null,
                                                    isCompleted = false,
                                                    selectedSourceUrl = playerCurrentSourceUrl ?: selectedVideoUrl,
                                                    selectedAudioTrackId = null,
                                                    selectedSubtitleTrackId = null
                                                ),
                                                selectedPlaybackId = selectedPlaybackId,
                                                playbackTrackSelectionStore = playbackTrackSelectionStore,
                                                sourceSelectionStore = sourceSelectionStore,
                                                pendingSourceSelection = playerState.pendingSourceSelection,
                                                onConsumePendingSelection = { playerState.pendingSourceSelection = null },
                                                onResumeHintResolved = { detailsResumePlaybackHint = it },
                                                rememberSourceSelection = currentProfile?.rememberSourceSelection ?: true,
                                                switchedAway = true
                                            )

                                            val subtitlePayload = buildSubtitlePayload(streamToPlay, addonSubs)
                                            val sourcePayload = buildSourcePayload(streams, streamToPlay)

                                            playerState.pendingSourceSelection = PendingSourceSelection(
                                                playbackId = epPlaybackId,
                                                launchedStream = streamToPlay,
                                                candidateStreams = streams
                                            )
                                            playerState.currentStream = streamToPlay

                                            if (epUrl.startsWith("magnet:")) {
                                                selectedPlaybackId = epPlaybackId
                                                selectedPlaybackType = "series"
                                                selectedPlaybackTitle = epTitle
                                                playerState.selectedPlayerSubtitles = subtitlePayload
                                                playerState.selectedPlayerSources = sourcePayload
                                                torrentProgress = TorrentProgress("Connecting to peers...")
                                                TorrentService.onStreamReady = { localUrl ->
                                                    torrentProgress = null
                                                    selectedVideoUrl = localUrl
                                                }
                                                TorrentService.onStreamError = { error ->
                                                    torrentProgress = null
                                                    if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Stream error: $error")
                                                }
                                                TorrentService.onStreamProgress = { progress ->
                                                    torrentProgress = progress
                                                }
                                                val intent = Intent(this@MainActivity, TorrentService::class.java).apply {
                                                    putExtra("MAGNET_LINK", epUrl)
                                                    putExtra("FILE_IDX", streamToPlay.fileIdx ?: -1)
                                                    putExtra("FILE_NAME", streamToPlay.behaviorHints?.filename ?: "")
                                                }
                                                startService(intent)
                                            } else {
                                                stopService(Intent(this@MainActivity, TorrentService::class.java))
                                                selectedPlaybackId = epPlaybackId
                                                selectedPlaybackType = "series"
                                                selectedPlaybackTitle = epTitle
                                                playerState.selectedPlayerSubtitles = subtitlePayload
                                                playerState.selectedPlayerSources = sourcePayload
                                                selectedVideoUrl = epUrl
                                            }
                                        }
                                    }
                                } else null,
                                episodeSwitchSources = playerState.pendingEpisodeSwitch?.let { pending ->
                                    pending.streams?.mapNotNull { stream ->
                                        val url = resolvePlayableSourceUrl(stream) ?: return@mapNotNull null
                                        PlayerSourceOption(
                                            id = url,
                                            url = url,
                                            label = sourceDisplayLabel(stream),
                                            name = stream.name,
                                            title = stream.title,
                                            description = stream.description,
                                            fileIdx = stream.fileIdx ?: -1,
                                            fileName = stream.behaviorHints?.filename ?: ""
                                        )
                                    }?.distinctBy { it.url }
                                },
                                isEpisodeSwitchLoading = playerState.isEpisodeSwitchLoading,
                                episodeSwitchTitle = playerState.pendingEpisodeSwitch?.playbackTitle,
                                onEpisodeSwitchSourceSelected = playerState.pendingEpisodeSwitch?.let { pending ->
                                    { sourceUrl: String ->
                                        val streamToPlay = pending.streams?.firstOrNull { resolvePlayableSourceUrl(it) == sourceUrl }
                                        if (streamToPlay == null) {
                                            playerState.pendingEpisodeSwitch = null
                                            return@let
                                        }

                                        // Now save progress for current episode
                                        handlePlayerSessionEnd(
                                            sessionResult = PlayerSessionResult(
                                                positionMs = 0L,
                                                durationMs = null,
                                                isCompleted = false,
                                                selectedSourceUrl = pending.playerCurrentSourceUrl ?: selectedVideoUrl,
                                                selectedAudioTrackId = null,
                                                selectedSubtitleTrackId = null
                                            ),
                                            selectedPlaybackId = selectedPlaybackId,
                                            playbackTrackSelectionStore = playbackTrackSelectionStore,
                                            sourceSelectionStore = sourceSelectionStore,
                                            pendingSourceSelection = playerState.pendingSourceSelection,
                                            onConsumePendingSelection = { playerState.pendingSourceSelection = null },
                                            onResumeHintResolved = { detailsResumePlaybackHint = it },
                                            rememberSourceSelection = currentProfile?.rememberSourceSelection ?: true,
                                            switchedAway = true
                                        )

                                        val subtitlePayload = buildSubtitlePayload(streamToPlay, pending.addonSubs)
                                        val sourcePayload = buildSourcePayload(pending.streams, streamToPlay)

                                        playerState.pendingSourceSelection = PendingSourceSelection(
                                            playbackId = pending.playbackId,
                                            launchedStream = streamToPlay,
                                            candidateStreams = pending.streams
                                        )
                                        playerState.currentStream = streamToPlay
                                        playerState.pendingEpisodeSwitch = null

                                        if (sourceUrl.startsWith("magnet:")) {
                                            selectedPlaybackId = pending.playbackId
                                            selectedPlaybackType = "series"
                                            selectedPlaybackTitle = pending.playbackTitle
                                            playerState.selectedPlayerSubtitles = subtitlePayload
                                            playerState.selectedPlayerSources = sourcePayload
                                            torrentProgress = TorrentProgress("Connecting to peers...")
                                            TorrentService.onStreamReady = { localUrl ->
                                                torrentProgress = null
                                                selectedVideoUrl = localUrl
                                            }
                                            TorrentService.onStreamError = { error ->
                                                torrentProgress = null
                                                if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Stream error: $error")
                                            }
                                            TorrentService.onStreamProgress = { progress ->
                                                torrentProgress = progress
                                            }
                                            val intent = Intent(this@MainActivity, TorrentService::class.java).apply {
                                                putExtra("MAGNET_LINK", sourceUrl)
                                                putExtra("FILE_IDX", streamToPlay.fileIdx ?: -1)
                                                putExtra("FILE_NAME", streamToPlay.behaviorHints?.filename ?: "")
                                            }
                                            startService(intent)
                                        } else {
                                            stopService(Intent(this@MainActivity, TorrentService::class.java))
                                            selectedPlaybackId = pending.playbackId
                                            selectedPlaybackType = "series"
                                            selectedPlaybackTitle = pending.playbackTitle
                                            playerState.selectedPlayerSubtitles = subtitlePayload
                                            playerState.selectedPlayerSources = sourcePayload
                                            selectedVideoUrl = sourceUrl
                                        }
                                    }
                                },
                                onEpisodeSwitchDismissed = { playerState.pendingEpisodeSwitch = null; playerState.isEpisodeSwitchLoading = false },
                                onMagnetSourceSelected = { magnetUrl, sourceFileIdx, sourceFileName, onReady ->
                                    torrentProgress = TorrentProgress("Connecting to peers...")
                                    TorrentService.onStreamReady = { localUrl ->
                                        torrentProgress = null
                                        onReady(localUrl)
                                    }
                                    TorrentService.onStreamError = { error ->
                                        torrentProgress = null
                                        if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Source switch error: $error")
                                    }
                                    TorrentService.onStreamProgress = { progress ->
                                        torrentProgress = progress
                                    }
                                    val intent = Intent(this@MainActivity, TorrentService::class.java).apply {
                                        putExtra("MAGNET_LINK", magnetUrl)
                                        putExtra("FILE_IDX", sourceFileIdx)
                                        putExtra("FILE_NAME", sourceFileName)
                                    }
                                    startService(intent)
                                },
                                torrentProgress = torrentProgress,
                                onBack = { sessionResult ->
                                    torrentProgress = null
                                    // A next episode still being looked up must not start after the player is gone
                                    playerState.episodeSwitchJob?.cancel()
                                    playerState.episodeSwitchJob = null
                                    playerState.isEpisodeSwitchLoading = false
                                    playerState.pendingEpisodeSwitch = null
                                    handlePlayerSessionEnd(
                                        sessionResult = sessionResult,
                                        selectedPlaybackId = selectedPlaybackId,
                                        playbackTrackSelectionStore = playbackTrackSelectionStore,
                                        sourceSelectionStore = sourceSelectionStore,
                                        pendingSourceSelection = playerState.pendingSourceSelection,
                                        onConsumePendingSelection = { playerState.pendingSourceSelection = null },
                                        onResumeHintResolved = { detailsResumePlaybackHint = it },
                                        rememberSourceSelection = currentProfile?.rememberSourceSelection ?: true
                                    )
                                    stopService(Intent(this@MainActivity, TorrentService::class.java))
                                    if (selectedPlaybackId.startsWith("trailer_")) {
                                        trailerReturnToken++
                                    }
                                    activeView = "details"
                                }
                            )
                            }
                        }
                    // ViewSwitcher end
                    }

                    // Player choice dialog (shown when playerPreference == "ask")
                    if (playerState.showPlayerChoiceDialog && selectedVideoUrl.isNotBlank()) {
                        PlayerChoiceDialog(
                            onInternal = {
                                playerState.showPlayerChoiceDialog = false
                                activeView = "player"
                            },
                            onExternal = {
                                playerState.showPlayerChoiceDialog = false
                                launchExternalPlayer(this@MainActivity, selectedVideoUrl)
                            },
                            onDismiss = {
                                playerState.showPlayerChoiceDialog = false
                            }
                        )
                    }

                    if (showTrailerError) {
                        Dialog(onDismissRequest = { showTrailerError = false }) {
                            Box(
                                modifier = Modifier
                                    .width(380.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.background)
                                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                                    .padding(24.dp)
                            ) {
                                androidx.compose.foundation.layout.Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Trailer Unavailable",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(24.dp))
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        VoidButton(
                                            text = "Dismiss",
                                            onClick = { showTrailerError = false },
                                            isPrimary = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Update dialogs (auto-shown after splash)
                    if (_splashFinished.value && !updateDismissed && appUpdateManager.isPopupEnabled) {
                        when (val state = updateState) {
                            is UpdateState.UpdateAvailable -> {
                                UpdateAvailableDialog(
                                    info = state.info,
                                    onUpdate = {
                                        updateDownloadStarted = true
                                        updateScope.launch { appUpdateManager.downloadAndInstall(state.info.apkUrl) }
                                    },
                                    onDismiss = { updateDismissed = true },
                                    onDontShowAgain = {
                                        appUpdateManager.setPopupEnabled(false)
                                        updateDismissed = true
                                    }
                                )
                            }
                            is UpdateState.Downloading -> {
                                UpdateDownloadingDialog(
                                    progress = state.progress,
                                    downloadedMb = state.downloadedMb,
                                    totalMb = state.totalMb
                                )
                            }
                            // A failed background check (TV still joining Wi-Fi, GitHub down) stays silent;
                            // only a download the user started reports its error
                            is UpdateState.Error -> if (updateDownloadStarted) {
                                UpdateErrorDialog(
                                    message = state.message,
                                    onRetry = {
                                        appUpdateManager.resetState()
                                        updateScope.launch { appUpdateManager.checkForUpdate() }
                                    },
                                    onDismiss = {
                                        appUpdateManager.resetState()
                                        updateDismissed = true
                                    }
                                )
                            }
                            else -> {}
                        }
                    }

                }
                }
            }
        }

        // Attach native splash overlay on top of Compose content — renders immediately
        if (showSplash) {
            attachSplashOverlay()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        OpenRequest.from(intent)?.let { pendingOpen.value = it }
    }

    /** A title Neon Home asked to open: whose, and which, as its Continue watching row showed it. */
    data class OpenRequest(
        val profileId: Int,
        val type: String,
        val id: String,
        val name: String,
        val poster: String,
        val background: String,
        val logo: String
    ) {
        companion object {
            fun from(intent: Intent?): OpenRequest? {
                intent ?: return null
                val profileId = intent.getIntExtra(EXTRA_OPEN_PROFILE, -1).takeIf { it >= 0 } ?: return null
                val id = intent.getStringExtra(EXTRA_OPEN_ID)?.takeIf { it.isNotBlank() } ?: return null
                // Taken once, so turning the TV or coming back later does not open it again
                intent.removeExtra(EXTRA_OPEN_PROFILE)
                return OpenRequest(
                    profileId = profileId,
                    type = intent.getStringExtra(EXTRA_OPEN_TYPE) ?: "movie",
                    id = id,
                    name = intent.getStringExtra(EXTRA_OPEN_NAME) ?: "",
                    poster = intent.getStringExtra(EXTRA_OPEN_POSTER) ?: "",
                    background = intent.getStringExtra(EXTRA_OPEN_BACKGROUND) ?: "",
                    logo = intent.getStringExtra(EXTRA_OPEN_LOGO) ?: ""
                )
            }
        }
    }

    companion object {
        private const val KEY_SPLASH_SHOWN = "splash_shown"
        private const val KEY_SPLASH_SHOWN_AT = "splash_shown_at"

        /** Neon Home's Continue watching: the profile to log in as and the title to open. */
        const val EXTRA_OPEN_PROFILE = "nt_open_profile"
        const val EXTRA_OPEN_TYPE = "nt_open_type"
        const val EXTRA_OPEN_ID = "nt_open_id"
        const val EXTRA_OPEN_NAME = "nt_open_name"
        const val EXTRA_OPEN_POSTER = "nt_open_poster"
        const val EXTRA_OPEN_BACKGROUND = "nt_open_background"
        const val EXTRA_OPEN_LOGO = "nt_open_logo"
    }
}
