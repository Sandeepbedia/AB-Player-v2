package com.io.ab.music.data.db.dao

import androidx.room.*
import com.io.ab.music.data.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC LIMIT :limit")
    fun getRecentlyAdded(limit: Int = 20): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY playCount DESC LIMIT :limit")
    fun getMostPlayed(limit: Int = 20): Flow<List<SongEntity>>

    @Upsert
    suspend fun upsertSongs(songs: List<SongEntity>)

    @Upsert
    suspend fun upsertSong(song: SongEntity)

    @Query("DELETE FROM songs WHERE path = :path")
    suspend fun deleteSongByPath(path: String)

    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()

    @Query("UPDATE songs SET playCount = playCount + 1 WHERE id = :songId")
    suspend fun incrementPlayCount(songId: Long)

    @Query("SELECT DISTINCT album, albumId, artist, artworkUri, COUNT(*) as songCount FROM songs GROUP BY albumId")
    fun getAlbums(): Flow<List<AlbumRow>>

    @Query("SELECT DISTINCT artist, COUNT(*) as songCount FROM songs GROUP BY artist")
    fun getArtists(): Flow<List<ArtistRow>>

    @Query("SELECT DISTINCT substr(path, 1, length(path) - length(replace(path, '/', '')) + 1) as folderPath, COUNT(*) as songCount FROM songs GROUP BY folderPath")
    fun getFolders(): Flow<List<FolderRow>>

    @Query("SELECT * FROM songs WHERE artist = :artistName ORDER BY title ASC")
    fun getSongsByArtist(artistName: String): Flow<List<SongEntity>>
}

data class AlbumRow(
    val album: String,
    val albumId: Long,
    val artist: String,
    val artworkUri: String?,
    val songCount: Int
)

data class ArtistRow(val artist: String, val songCount: Int)
data class FolderRow(val folderPath: String, val songCount: Int)
