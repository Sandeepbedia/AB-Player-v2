package com.io.ab.music.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val songId: Long,
    val lyrics: String,
    val synced: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
)
