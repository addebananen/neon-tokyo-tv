package com.lumera.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lumera.app.data.model.ActiveProfileEntity
import com.lumera.app.data.model.AddonEntity
import com.lumera.app.data.model.CatalogConfigEntity
import com.lumera.app.data.model.HubRowEntity
import com.lumera.app.data.model.HubRowWithItems
import com.lumera.app.data.model.HubRowItemEntity
import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.data.model.ThemeEntity
import com.lumera.app.data.model.SeriesNextUpEntity
import com.lumera.app.data.model.WatchHistoryEntity
import com.lumera.app.data.model.WatchlistEntity
import kotlinx.coroutines.flow.Flow
import androidx.room.Delete

/** Rows that belong to the profile that is watching, see [ActiveProfileEntity]. */
private const val WATCHING = "profileId = (SELECT profileId FROM active_profile WHERE slot = 0)"

/** History and lists made before profiles kept their own, until they are handed out at the next login. */
const val UNASSIGNED_PROFILE = -1

@Dao
interface AddonDao {

    @Query("SELECT * FROM addons ORDER BY sortOrder ASC")
    fun getAllAddons(): Flow<List<AddonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddon(addon: AddonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddons(addons: List<AddonEntity>)

    @Query("DELETE FROM addons WHERE transportUrl = :url")
    suspend fun deleteAddonByUrl(url: String)

    @Query("DELETE FROM addons")
    suspend fun clearAddons()

    @Query("SELECT * FROM addons WHERE transportUrl = :transportUrl")
    suspend fun getAddon(transportUrl: String): AddonEntity?

    @Query("SELECT * FROM catalog_configs")
    fun getAllCatalogConfigs(): Flow<List<CatalogConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCatalogConfig(config: CatalogConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCatalogConfigs(configs: List<CatalogConfigEntity>)

    @Query("DELETE FROM catalog_configs WHERE transportUrl = :url")
    suspend fun deleteCatalogConfigs(url: String)

    @Query("DELETE FROM catalog_configs")
    suspend fun clearCatalogConfigs()

    @Query("SELECT * FROM catalog_configs WHERE uniqueId = :uniqueId")
    suspend fun getCatalogConfig(uniqueId: String): CatalogConfigEntity?

    @Query("SELECT * FROM profiles")
    fun getProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: Int): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun getProfileFlow(id: Int): Flow<ProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteProfile(id: Int)

    // ── Who is watching ──

    @Query("SELECT profileId FROM active_profile WHERE slot = 0")
    suspend fun getActiveProfileId(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setActiveProfile(row: ActiveProfileEntity)

    @Query("DELETE FROM active_profile")
    suspend fun clearActiveProfile()

    // ── Watch history of the watching profile ──

    @Query("SELECT * FROM watch_history WHERE $WATCHING ORDER BY lastWatched DESC")
    fun getWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE $WATCHING")
    suspend fun getAllWatchHistoryOnce(): List<WatchHistoryEntity>

    /** Saves as they are, profileId included. Use [upsertHistory] to save for the watching profile. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryRows(items: List<WatchHistoryEntity>)

    @Transaction
    suspend fun upsertHistory(item: WatchHistoryEntity) {
        upsertHistoryItems(listOf(item))
    }

    @Transaction
    suspend fun upsertHistoryItems(items: List<WatchHistoryEntity>) {
        val watching = getActiveProfileId() ?: return
        insertHistoryRows(items.map { it.copy(profileId = watching) })
    }

    @Query("DELETE FROM watch_history WHERE $WATCHING")
    suspend fun clearWatchHistory()

    @Query("SELECT * FROM watch_history WHERE $WATCHING AND id = :id")
    suspend fun getHistoryItem(id: String): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history WHERE $WATCHING AND id LIKE :prefix || '%'")
    suspend fun getHistoryItemsByPrefix(prefix: String): List<WatchHistoryEntity>

    @Query(
        "SELECT * FROM watch_history " +
            "WHERE $WATCHING AND type = 'series' AND id LIKE :episodePrefix " +
            "ORDER BY lastWatched DESC LIMIT 1"
    )
    suspend fun getLatestSeriesEpisodeHistory(episodePrefix: String): WatchHistoryEntity?

    @Query("DELETE FROM watch_history WHERE $WATCHING AND id = :id")
    suspend fun deleteHistoryItem(id: String)

    @Query("SELECT * FROM watch_history WHERE $WATCHING AND type = 'series' AND id LIKE :episodePrefix")
    suspend fun getSeriesEpisodeHistory(episodePrefix: String): List<WatchHistoryEntity>

    @Query("DELETE FROM watch_history WHERE $WATCHING AND type = 'series' AND id LIKE :episodePrefix")
    suspend fun deleteSeriesHistory(episodePrefix: String)

    @Query("SELECT * FROM themes")
    fun getAllThemes(): Flow<List<ThemeEntity>>

    @Query("SELECT * FROM themes WHERE id = :id")
    suspend fun getThemeById(id: String): ThemeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTheme(theme: ThemeEntity)

    @Delete
    suspend fun deleteTheme(theme: ThemeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHubRow(row: HubRowEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHubRows(rows: List<HubRowEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHubRowItems(items: List<HubRowItemEntity>)

    @Transaction
    suspend fun insertHubRowWithItems(row: HubRowEntity, items: List<HubRowItemEntity>) {
        insertHubRow(row)
        insertHubRowItems(items)
    }

    @Query("SELECT * FROM hub_rows ORDER BY homeOrder ASC, createdAt ASC")
    fun getAllHubRows(): Flow<List<HubRowEntity>>

    @Query("SELECT * FROM hub_row_items ORDER BY itemOrder ASC")
    fun getAllHubRowItems(): Flow<List<HubRowItemEntity>>

    @Query("DELETE FROM hub_rows WHERE id = :hubRowId")
    suspend fun deleteHubRow(hubRowId: String)

    @Query("DELETE FROM hub_row_items WHERE hubRowId = :hubRowId")
    suspend fun deleteHubRowItems(hubRowId: String)

    @Query("DELETE FROM hub_row_items")
    suspend fun clearHubRowItems()

    @Query("DELETE FROM hub_rows")
    suspend fun clearHubRows()

    @Transaction
    suspend fun deleteHubRowWithItems(hubRowId: String) {
        deleteHubRowItems(hubRowId)
        deleteHubRow(hubRowId)
    }

    @Query("UPDATE hub_row_items SET customImageUrl = :imageUrl WHERE hubRowId = :hubRowId AND configUniqueId = :configUniqueId")
    suspend fun updateHubItemImage(hubRowId: String, configUniqueId: String, imageUrl: String?)

    @Transaction
    @Query("SELECT * FROM hub_rows ORDER BY homeOrder ASC, createdAt ASC")
    fun getHubRowsWithItems(): Flow<List<HubRowWithItems>>



    @Query("SELECT MAX(homeOrder) FROM hub_rows")
    suspend fun getMaxHubHomeOrder(): Int?

    @Query("SELECT MAX(moviesOrder) FROM hub_rows")
    suspend fun getMaxHubMoviesOrder(): Int?

    @Query("SELECT MAX(seriesOrder) FROM hub_rows")
    suspend fun getMaxHubSeriesOrder(): Int?



    @Update
    suspend fun updateHubRow(row: HubRowEntity)

    @Update
    suspend fun updateHubRows(rows: List<HubRowEntity>)

    @Query("DELETE FROM hub_row_items WHERE hubRowId = :hubRowId AND configUniqueId = :configUniqueId")
    suspend fun deleteHubRowItem(hubRowId: String, configUniqueId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHubRowItem(item: HubRowItemEntity)

    @Query("SELECT MAX(itemOrder) FROM hub_row_items WHERE hubRowId = :hubRowId")
    suspend fun getMaxHubItemOrder(hubRowId: String): Int?

    @Update
    suspend fun updateHubRowItem(item: HubRowItemEntity)

    @Update
    suspend fun updateHubRowItems(items: List<HubRowItemEntity>)

    // ── Watchlist of the watching profile ──

    @Query("SELECT * FROM watchlist WHERE $WATCHING ORDER BY addedAt DESC")
    fun getWatchlist(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist WHERE $WATCHING AND type = :type ORDER BY addedAt DESC")
    fun getWatchlistByType(type: String): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist WHERE $WATCHING ORDER BY addedAt DESC")
    suspend fun getWatchlistOnce(): List<WatchlistEntity>

    @Query("SELECT * FROM watchlist WHERE $WATCHING AND id = :id")
    suspend fun getWatchlistItem(id: String): WatchlistEntity?

    @Query("SELECT * FROM watch_history WHERE $WATCHING AND scrobbled = 1 AND watched = 0")
    suspend fun getScrobbledInProgressItems(): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE $WATCHING AND scrobbled = 1 AND watched = 1")
    suspend fun getScrobbledWatchedItems(): List<WatchHistoryEntity>

    @Query("SELECT id FROM watch_history WHERE $WATCHING AND watched = 1")
    fun getWatchedIds(): Flow<List<String>>

    // Artwork is the same whoever watched it, so every profile's entry gets the new images
    @Query("UPDATE watch_history SET poster = :poster, background = :background, logo = :logo WHERE id = :id")
    suspend fun updateHistoryImages(id: String, poster: String?, background: String?, logo: String?)

    // ── Series next up of the watching profile ──

    /** Saves as they are, profileId included. Use [upsertSeriesNextUp] to save for the watching profile. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeriesNextUpRows(entries: List<SeriesNextUpEntity>)

    @Transaction
    suspend fun upsertSeriesNextUp(entry: SeriesNextUpEntity) {
        val watching = getActiveProfileId() ?: return
        insertSeriesNextUpRows(listOf(entry.copy(profileId = watching)))
    }

    @Query("SELECT * FROM series_next_up WHERE $WATCHING AND isComplete = 0 ORDER BY updatedAt DESC")
    fun getActiveSeriesNextUp(): Flow<List<SeriesNextUpEntity>>

    @Query("SELECT * FROM series_next_up WHERE $WATCHING AND seriesId = :seriesId")
    suspend fun getSeriesNextUp(seriesId: String): SeriesNextUpEntity?

    @Query("DELETE FROM series_next_up WHERE $WATCHING AND seriesId = :seriesId")
    suspend fun deleteSeriesNextUp(seriesId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE $WATCHING AND id = :id)")
    suspend fun isInWatchlist(id: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE $WATCHING AND id = :id)")
    fun isInWatchlistFlow(id: String): Flow<Boolean>

    /** Saves as they are, profileId included. Use [addToWatchlist] to save for the watching profile. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchlistRows(items: List<WatchlistEntity>)

    @Transaction
    suspend fun addToWatchlist(item: WatchlistEntity) {
        val watching = getActiveProfileId() ?: return
        insertWatchlistRows(listOf(item.copy(profileId = watching)))
    }

    @Query("DELETE FROM watchlist WHERE $WATCHING AND id = :id")
    suspend fun removeFromWatchlist(id: String)

    // ── One profile's rows, for moving old data and deleting a profile ──

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId")
    suspend fun getHistoryOf(profileId: Int): List<WatchHistoryEntity>

    @Query("SELECT * FROM watchlist WHERE profileId = :profileId")
    suspend fun getWatchlistOf(profileId: Int): List<WatchlistEntity>

    @Query("SELECT * FROM series_next_up WHERE profileId = :profileId")
    suspend fun getSeriesNextUpOf(profileId: Int): List<SeriesNextUpEntity>

    @Query("DELETE FROM watch_history WHERE profileId = :profileId")
    suspend fun deleteHistoryOf(profileId: Int)

    @Query("DELETE FROM watchlist WHERE profileId = :profileId")
    suspend fun deleteWatchlistOf(profileId: Int)

    @Query("DELETE FROM series_next_up WHERE profileId = :profileId")
    suspend fun deleteSeriesNextUpOf(profileId: Int)

    @Transaction
    suspend fun deletePersonalData(profileId: Int) {
        deleteHistoryOf(profileId)
        deleteWatchlistOf(profileId)
        deleteSeriesNextUpOf(profileId)
    }
}
