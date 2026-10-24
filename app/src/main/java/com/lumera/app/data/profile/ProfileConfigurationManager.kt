package com.lumera.app.data.profile

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.room.withTransaction
import com.google.gson.Gson
import com.lumera.app.data.auth.StremioAuthManager
import com.lumera.app.data.local.AddonDao
import com.lumera.app.data.local.LumeraDatabase
import com.lumera.app.data.local.UNASSIGNED_PROFILE
import com.lumera.app.data.model.ActiveProfileEntity
import com.lumera.app.data.model.AddonEntity
import com.lumera.app.data.model.CatalogConfigEntity
import com.lumera.app.data.model.HubRowEntity
import com.lumera.app.data.model.HubRowItemEntity
import com.lumera.app.data.model.WatchHistoryEntity
import com.lumera.app.data.repository.AddonRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Addons, catalogs and hubs; also the format of the per-profile snapshots Lumera used to keep. */
data class ProfileRuntimeSnapshot(
    val addons: List<AddonEntity> = emptyList(),
    val catalogConfigs: List<CatalogConfigEntity> = emptyList(),
    val hubRows: List<HubRowEntity> = emptyList(),
    val hubRowItems: List<HubRowItemEntity> = emptyList(),
    val watchHistory: List<WatchHistoryEntity> = emptyList()
)

/**
 * Who is watching. Addons, catalogs, hubs and the Stremio account are the same for every
 * profile; history, the watchlist and next episodes belong to the profile that is signed in.
 */
@Singleton
class ProfileConfigurationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: LumeraDatabase,
    private val dao: AddonDao,
    private val stremioAuthManager: StremioAuthManager,
    private val addonRepository: AddonRepository
) {
    companion object {
        private const val PREFS_FILE = "profile_configuration_prefs"
        private const val KEY_PENDING_SETUP_PROFILES = "pending_setup_profiles"
        private const val KEY_LAST_ACTIVE_PROFILE_ID = "last_active_profile_id"
        private const val KEY_SHARED_CATALOG_READY = "shared_catalog_ready"
        private const val KEY_PROFILES_SPLIT = "profiles_split_v45"
        private const val SNAPSHOT_DIR = "profile_snapshots"
        private const val SNAPSHOT_BACKUP_DIR = "profile_snapshots_before_v45"
        private const val TAG = "NeonTokyoStarter"
        private const val STARTER_MANIFEST_TIMEOUT_MS = 12_000L
    }

    private val gson = Gson()
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }
    private val setupLock = Mutex()

    fun markPendingSetup(profileId: Int) {
        val updated = getPendingSetupIds().toMutableSet().apply { add(profileId.toString()) }
        prefs.edit().putStringSet(KEY_PENDING_SETUP_PROFILES, updated).apply()
    }

    fun needsInitialSetup(profileId: Int): Boolean {
        return getPendingSetupIds().contains(profileId.toString())
    }

    fun clearPendingSetup(profileId: Int) {
        val updated = getPendingSetupIds().toMutableSet().apply { remove(profileId.toString()) }
        prefs.edit().putStringSet(KEY_PENDING_SETUP_PROFILES, updated).apply()
    }

    /** From here on history, the watchlist and next episodes are read and saved as [profileId]'s. */
    suspend fun activateProfile(profileId: Int) {
        setupLock.withLock { splitLegacyProfilesIfNeeded() }
        dao.setActiveProfile(ActiveProfileEntity(profileId = profileId))
        setLastActiveProfileId(profileId)
    }

    /** Nobody is watching: on the profile screen nothing reads or saves anyone's history. */
    suspend fun deactivateProfile() {
        dao.clearActiveProfile()
        clearLastActiveProfileId()
    }

    /**
     * Gets a new profile ready. The first profile on the TV sets up the Neon Tokyo starter
     * catalog that everybody shares. A later profile finds that catalog in place, takes the home
     * layout of the first profile and starts with a history of its own, which is empty.
     */
    suspend fun prepareNewProfile(profileId: Int) = setupLock.withLock {
        splitLegacyProfilesIfNeeded()
        if (!prefs.getBoolean(KEY_SHARED_CATALOG_READY, false)) {
            if (dao.getAllAddons().first().isEmpty()) installStarterCatalog()
            prefs.edit().putBoolean(KEY_SHARED_CATALOG_READY, true).apply()
        } else {
            val template = dao.getProfiles().first()
                .firstOrNull { it.id != profileId && !needsInitialSetup(it.id) }
            if (template != null) copyProfileDisplayAndDashboardConfig(profileId, template.id)
        }
        clearPendingSetup(profileId)
    }

    suspend fun deleteProfileState(profileId: Int) {
        clearPendingSetup(profileId)
        dao.deletePersonalData(profileId)
        if (dao.getActiveProfileId() == profileId) dao.clearActiveProfile()
        if (getLastActiveProfileId() == profileId) clearLastActiveProfileId()
    }

    private suspend fun copyProfileDisplayAndDashboardConfig(targetProfileId: Int, sourceProfileId: Int) {
        val sourceProfile = dao.getProfileById(sourceProfileId) ?: return
        val targetProfile = dao.getProfileById(targetProfileId) ?: return

        dao.insertProfile(
            targetProfile.copy(
                roundCorners = sourceProfile.roundCorners,
                hubRoundCorners = sourceProfile.hubRoundCorners,
                navPosition = sourceProfile.navPosition,
                homeTabLayout = sourceProfile.homeTabLayout,
                moviesTabLayout = sourceProfile.moviesTabLayout,
                seriesTabLayout = sourceProfile.seriesTabLayout,
                homeHeroCategory = sourceProfile.homeHeroCategory,
                homeHeroPosterCount = sourceProfile.homeHeroPosterCount,
                homeHeroAutoScrollSeconds = sourceProfile.homeHeroAutoScrollSeconds,
                moviesHeroCategory = sourceProfile.moviesHeroCategory,
                moviesHeroPosterCount = sourceProfile.moviesHeroPosterCount,
                moviesHeroAutoScrollSeconds = sourceProfile.moviesHeroAutoScrollSeconds,
                seriesHeroCategory = sourceProfile.seriesHeroCategory,
                seriesHeroPosterCount = sourceProfile.seriesHeroPosterCount,
                seriesHeroAutoScrollSeconds = sourceProfile.seriesHeroAutoScrollSeconds
            )
        )
    }

    private suspend fun installStarterCatalog() {
        val starter = createDefaultRuntimeSnapshot()
        database.withTransaction {
            dao.insertAddons(starter.addons)
            dao.saveCatalogConfigs(starter.catalogConfigs)
            dao.insertHubRows(starter.hubRows)
            dao.insertHubRowItems(starter.hubRowItems)
        }
    }

    private suspend fun createDefaultRuntimeSnapshot(): ProfileRuntimeSnapshot = coroutineScope {
        val manifests = NeonTokyoStarterPack.MANIFEST_URLS.map { manifestUrl ->
            async {
                val manifest = runCatching {
                    withTimeout(STARTER_MANIFEST_TIMEOUT_MS) { addonRepository.fetchManifest(manifestUrl) }
                }.onFailure { Log.w(TAG, "Starter addon unavailable: $manifestUrl", it) }.getOrNull()
                manifestUrl.removeSuffix("/manifest.json") to manifest
            }
        }.awaitAll().toMap()

        NeonTokyoStarterPack.build(manifests, gson).also { snapshot ->
            snapshot.catalogConfigs.forEach {
                Log.i(TAG, "Starter catalog ${it.addonName} ${it.catalogType}/${it.catalogId} " +
                    "${it.extraArgs ?: ""} '${it.customTitle ?: it.catalogName}' home=${it.showInHome}")
            }
        }
    }

    /**
     * Once, after the update that made history personal: the addons every profile had become
     * the shared setup, and the history each profile had becomes its own. The old snapshots
     * are kept aside, untouched, in [SNAPSHOT_BACKUP_DIR].
     */
    private suspend fun splitLegacyProfilesIfNeeded() {
        if (prefs.getBoolean(KEY_PROFILES_SPLIT, false)) return
        // Off the main thread, and finished in one go even if the login that started it is cancelled
        withContext(Dispatchers.IO + NonCancellable) { splitLegacyProfiles() }
    }

    private suspend fun splitLegacyProfiles() {
        val profiles = dao.getProfiles().first()
        val ids = profiles.map { it.id }
        // The profile whose session the live tables hold, if someone was signed in
        val owner = getLastActiveProfileId()?.takeIf { it in ids }
        val snapshots = ids.associateWith { readSnapshot(it) }
        val live = ProfileRuntimeSnapshot(
            addons = dao.getAllAddons().first(),
            catalogConfigs = dao.getAllCatalogConfigs().first(),
            hubRows = dao.getAllHubRows().first(),
            hubRowItems = dao.getAllHubRowItems().first()
        )
        val missing = LegacyProfileSplit.missingFromLive(live, ids.filter { it != owner }.mapNotNull { snapshots[it] })

        database.withTransaction {
            dao.insertAddons(missing.addons)
            dao.saveCatalogConfigs(missing.catalogConfigs)
            dao.insertHubRows(missing.hubRows)
            dao.insertHubRowItems(missing.hubRowItems)

            val personal = LegacyProfileSplit.personal(
                profileIds = ids,
                owner = owner,
                liveHistory = dao.getHistoryOf(UNASSIGNED_PROFILE),
                snapshotHistory = snapshots.mapValues { (_, snapshot) -> snapshot?.watchHistory.orEmpty() },
                sharedWatchlist = dao.getWatchlistOf(UNASSIGNED_PROFILE),
                sharedNextUp = dao.getSeriesNextUpOf(UNASSIGNED_PROFILE)
            )
            dao.deletePersonalData(UNASSIGNED_PROFILE)
            dao.insertHistoryRows(personal.history)
            dao.insertWatchlistRows(personal.watchlist)
            dao.insertSeriesNextUpRows(personal.nextUp)
            Log.i(TAG, "Profiles split: ${ids.size} profiles, ${missing.addons.size} addons shared, " +
                "${personal.history.size} history rows, ${personal.watchlist.size} watchlist rows")
        }
        stremioAuthManager.shareAccount(listOfNotNull(owner) + ids)

        // A TV that already had a profile in use has its catalog; a new profile doesn't set it up again
        if (ids.any { !needsInitialSetup(it) }) prefs.edit().putBoolean(KEY_SHARED_CATALOG_READY, true).apply()
        prefs.edit().putBoolean(KEY_PROFILES_SPLIT, true).apply()
        val snapshotDir = File(context.filesDir, SNAPSHOT_DIR)
        if (snapshotDir.exists()) snapshotDir.renameTo(File(context.filesDir, SNAPSHOT_BACKUP_DIR))
    }

    private fun readSnapshot(profileId: Int): ProfileRuntimeSnapshot? {
        val file = File(File(context.filesDir, SNAPSHOT_DIR), "profile_$profileId.json")
        if (!file.exists()) return null
        val raw = runCatching {
            gson.fromJson(file.readText(), ProfileRuntimeSnapshot::class.java)
        }.getOrNull() ?: return null
        // Gson leaves a list that is missing from the file null, whatever its Kotlin type says
        return ProfileRuntimeSnapshot(
            addons = listOrEmpty(raw.addons),
            catalogConfigs = listOrEmpty(raw.catalogConfigs),
            hubRows = listOrEmpty(raw.hubRows),
            hubRowItems = listOrEmpty(raw.hubRowItems),
            watchHistory = listOrEmpty(raw.watchHistory)
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> listOrEmpty(value: Any?): List<T> = (value as? List<T>) ?: emptyList()

    private fun getPendingSetupIds(): Set<String> {
        return prefs.getStringSet(KEY_PENDING_SETUP_PROFILES, emptySet()) ?: emptySet()
    }

    fun getLastActiveProfileId(): Int? {
        val value = prefs.getInt(KEY_LAST_ACTIVE_PROFILE_ID, -1)
        return if (value == -1) null else value
    }

    fun clearLastActiveProfileId() {
        prefs.edit().remove(KEY_LAST_ACTIVE_PROFILE_ID).apply()
    }

    private fun setLastActiveProfileId(profileId: Int) {
        prefs.edit().putInt(KEY_LAST_ACTIVE_PROFILE_ID, profileId).apply()
    }
}
