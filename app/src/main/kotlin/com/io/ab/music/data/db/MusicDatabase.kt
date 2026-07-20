package com.io.ab.music.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.io.ab.music.data.db.dao.*
import com.io.ab.music.data.db.entity.*

// Version 2: Added indices on songs(title, artist, dateAdded, playCount, albumId)
//            for faster sorts and search queries.
//            Uses fallbackToDestructiveMigration — music data is re-scanned from MediaStore
//            on first launch so no data is permanently lost.
@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        FavoriteEntity::class,
        RecentlyPlayedEntity::class,
        LyricsEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentlyPlayedDao(): RecentlyPlayedDao
    abstract fun lyricsDao(): LyricsDao
}
