package com.io.ab.music.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.io.ab.music.data.db.entity.LyricsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE songId = :songId")
    suspend fun getLyrics(songId: Long): LyricsEntity?

    @Query("SELECT songId FROM lyrics")
    fun getLyricsSongIds(): Flow<List<Long>>

    @Upsert
    suspend fun upsertLyrics(lyrics: LyricsEntity)
}
