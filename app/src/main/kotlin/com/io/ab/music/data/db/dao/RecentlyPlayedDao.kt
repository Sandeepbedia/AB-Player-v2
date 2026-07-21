package com.io.ab.music.data.db.dao

import androidx.room.*
import com.io.ab.music.data.db.entity.RecentlyPlayedEntity
import com.io.ab.music.data.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyPlayedDao {
    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN recently_played rp ON s.id = rp.songId
        ORDER BY rp.playedAt DESC
        LIMIT :limit
    """)
    fun getRecentlyPlayed(limit: Int = 30): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentlyPlayed(entity: RecentlyPlayedEntity)

    @Query("DELETE FROM recently_played WHERE songId NOT IN (SELECT songId FROM recently_played ORDER BY playedAt DESC LIMIT 100)")
    suspend fun pruneOldEntries()
}
