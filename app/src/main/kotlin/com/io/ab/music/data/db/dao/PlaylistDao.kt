package com.io.ab.music.data.db.dao

import androidx.room.*
import com.io.ab.music.data.db.entity.PlaylistEntity
import com.io.ab.music.data.db.entity.PlaylistSongCrossRef
import com.io.ab.music.data.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

/** Flat projection returned by getAllPlaylistsWithCount(). Not a Room entity. */
data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val songCount: Int
)

@Dao
interface PlaylistDao {

    /** All playlists with live song counts via LEFT JOIN + GROUP BY. */
    @Query("""
        SELECT p.id, p.name, p.createdAt, COUNT(ps.songId) AS songCount
        FROM playlists p
        LEFT JOIN playlist_songs ps ON p.id = ps.playlistId
        GROUP BY p.id
        ORDER BY p.name ASC
    """)
    fun getAllPlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    /** Legacy — kept for internal use only. */
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Delete
    suspend fun removeSongFromPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_songs ps ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.addedAt DESC
    """)
    fun getPlaylistSongs(playlistId: Long): Flow<List<SongEntity>>

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    fun getPlaylistSongCount(playlistId: Long): Flow<Int>
}
