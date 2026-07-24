package com.io.ab.music.data.db.dao

import androidx.room.*
import com.io.ab.music.data.db.entity.FavoriteEntity
import com.io.ab.music.data.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT s.* FROM songs s INNER JOIN favorites f ON s.id = f.songId ORDER BY f.addedAt DESC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    fun isFavorite(songId: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(fav: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun removeFavorite(songId: Long)

    @Query("SELECT songId FROM favorites")
    fun getAllFavoriteSongIds(): Flow<List<Long>>
}
