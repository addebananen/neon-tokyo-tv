package com.lumera.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The profile that is watching right now, in a table of one row. History, the watchlist and
 * next episodes are kept per profile, and the queries for them read this row, so switching
 * profile is one write and every open list follows it.
 */
@Entity(tableName = "active_profile")
data class ActiveProfileEntity(
    @PrimaryKey val slot: Int = 0,
    val profileId: Int
)
