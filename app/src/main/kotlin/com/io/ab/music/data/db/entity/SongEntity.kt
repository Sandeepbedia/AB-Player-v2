package com.io.ab.music.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["dateAdded"]),
        Index(value = ["playCount"]),
        Index(value = ["albumId"])
    ]
)
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val path: String,
    val artworkUri: String?,
    val size: Long,
    val dateAdded: Long,
    val playCount: Int = 0
)
