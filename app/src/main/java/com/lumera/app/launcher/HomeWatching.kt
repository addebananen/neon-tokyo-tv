package com.lumera.app.launcher

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.lumera.app.MainActivity
import com.lumera.app.data.local.AddonDao
import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.domain.ContinueEntry
import com.lumera.app.domain.continueWatching
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The app's database, for the home screen, which Hilt does not build. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HomeWatchingEntryPoint {
    fun dao(): AddonDao
}

/** What one Neon Tokyo profile has left to watch, for the home profile of the same name. */
data class HomeWatching(val profileId: Int, val entries: List<ContinueEntry>)

internal object HomeWatchingSource {
    /** How many titles the row shows: as many as the apps above it. */
    const val SHOWN = 4

    /** The Neon Tokyo profile a home profile goes with: the one with the same name. */
    fun match(homeName: String, profiles: List<ProfileEntity>): ProfileEntity? {
        val name = homeName.trim()
        if (name.isEmpty()) return null
        return profiles.firstOrNull { it.name.trim().equals(name, ignoreCase = true) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun watching(dao: AddonDao, homeName: String): Flow<HomeWatching?> =
        dao.getProfiles()
            .map { match(homeName, it)?.id }
            .distinctUntilChanged()
            .flatMapLatest { id ->
                if (id == null) {
                    flowOf(null)
                } else {
                    combine(dao.getWatchHistoryFlowOf(id), dao.getActiveSeriesNextUpFlowOf(id)) { history, nextUp ->
                        HomeWatching(id, continueWatching(history, nextUp).take(SHOWN))
                    }
                }
            }

    /** Opens [entry] in Neon Tokyo, logged in as [profileId], on its details page. */
    fun open(context: Context, profileId: Int, entry: ContinueEntry) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_OPEN_PROFILE, profileId)
            .putExtra(MainActivity.EXTRA_OPEN_TYPE, entry.type)
            .putExtra(MainActivity.EXTRA_OPEN_ID, entry.id)
            .putExtra(MainActivity.EXTRA_OPEN_NAME, entry.name)
            .putExtra(MainActivity.EXTRA_OPEN_POSTER, entry.poster ?: "")
            .putExtra(MainActivity.EXTRA_OPEN_BACKGROUND, entry.background ?: "")
            .putExtra(MainActivity.EXTRA_OPEN_LOGO, entry.logo ?: "")
        HomeApps.start(context, intent)
    }
}

/**
 * What the Neon Tokyo profile with the same name as the home profile [homeName] has left to
 * watch, kept up to date as it watches. Null when no Neon Tokyo profile has that name.
 */
@Composable
internal fun rememberHomeWatching(context: Context, homeName: String): HomeWatching? {
    val flow = remember(homeName) {
        val dao = EntryPointAccessors.fromApplication(context.applicationContext, HomeWatchingEntryPoint::class.java).dao()
        HomeWatchingSource.watching(dao, homeName)
    }
    val watching by flow.collectAsState(initial = null)
    return watching
}

/** "S2 · E5" for an episode, or null for a film. */
internal fun episodeLabel(entry: ContinueEntry): String? {
    val season = entry.season ?: return null
    val episode = entry.episode ?: return null
    return "S$season · E$episode"
}
