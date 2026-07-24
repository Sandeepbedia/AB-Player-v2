package com.io.ab.music.data.repository

import com.io.ab.music.data.db.dao.*
import com.io.ab.music.data.db.entity.*
import com.io.ab.music.data.scanner.MusicScanner
import com.io.ab.music.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val songDao          : SongDao,
    private val playlistDao      : PlaylistDao,
    private val favoriteDao      : FavoriteDao,
    private val recentlyPlayedDao: RecentlyPlayedDao,
    private val lyricsDao        : LyricsDao,
    private val scanner          : MusicScanner
) {
    // ========== SONGS ==========
    fun getAllSongs(): Flow<List<Song>> = songDao.getAllSongs()
        .map { it.map(::toSong) }
        .flowOn(Dispatchers.Default)

    suspend fun getSongById(id: Long): Song? =
        songDao.getSongById(id)?.let(::toSong)

    fun searchSongs(query: String): Flow<List<Song>> =
        songDao.searchSongs(query)
            .map { it.map(::toSong) }
            .flowOn(Dispatchers.Default)

    fun getRecentlyAdded(limit: Int = 20): Flow<List<Song>> =
        songDao.getRecentlyAdded(limit)
            .map { it.map(::toSong) }
            .flowOn(Dispatchers.Default)

    fun getMostPlayed(limit: Int = 20): Flow<List<Song>> =
        songDao.getMostPlayed(limit)
            .map { it.map(::toSong) }
            .flowOn(Dispatchers.Default)

    suspend fun scanAndSaveMusic(includeSubfolders: Boolean = true) {
        withContext(Dispatchers.IO) {
            val songs = scanner.scanAllMusic(includeSubfolders)
            songDao.upsertSongs(songs)
        }
    }

    suspend fun incrementPlayCount(songId: Long) = songDao.incrementPlayCount(songId)

    // ========== LYRICS ==========
    fun getLyricsSongIds(): Flow<List<Long>> = lyricsDao.getLyricsSongIds()
        .flowOn(Dispatchers.IO)

    suspend fun getCachedLyrics(songId: Long): CachedLyrics? = withContext(Dispatchers.IO) {
        lyricsDao.getLyrics(songId)?.let { CachedLyrics(it.lyrics, it.synced) }
    }

    suspend fun cacheLyrics(songId: Long, lyrics: String, synced: Boolean) = withContext(Dispatchers.IO) {
        if (lyrics.isNotBlank()) lyricsDao.upsertLyrics(LyricsEntity(songId, lyrics, synced))
    }

    suspend fun downloadAndCacheLyrics(song: Song): CachedLyrics? = withContext(Dispatchers.IO) {
        val local = tryLocalLrc(song.path)
        if (local != null) {
            val synced = hasSyncedTimestamps(local)
            lyricsDao.upsertLyrics(LyricsEntity(song.id, local, synced))
            return@withContext CachedLyrics(local, synced)
        }

        val title = cleanTitle(song.title)
        if (title.isBlank()) return@withContext null
        val artist = cleanArtist(song.artist)

        val (syncedDirect, plainDirect) = fetchRawFromLrclib(artist, title)
        val lyric = syncedDirect ?: plainDirect ?: run {
            val (syncedSearch, plainSearch) = fetchRawFromLrclibSearch(artist, title)
            syncedSearch ?: plainSearch
        } ?: return@withContext null

        val synced = hasSyncedTimestamps(lyric)
        lyricsDao.upsertLyrics(LyricsEntity(song.id, lyric, synced))
        CachedLyrics(lyric, synced)
    }

    private fun cleanTitle(raw: String): String = raw.trim()
        .substringBeforeLast(".", raw.trim())
        .replace(Regex("\\s*[\\(\\[]\\s*(feat\\.?|ft\\.?|featuring)\\s+.*?[\\)\\]]", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*[\\(\\[]\\s*(official.*?|lyrics?|lyrical video|audio|video|visualizer|explicit|remaster(ed)?).*?[\\)\\]]", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*[-–—]\\s*(official|lyrics?|lyrical|audio|video|visualizer|remaster(ed)?|explicit).*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun cleanArtist(raw: String): String = raw.trim()
        .takeIf { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
        ?.split(Regex("\\s*(,|;|/|&| x | X | feat\\.? | ft\\.? | featuring )\\s*", RegexOption.IGNORE_CASE))
        ?.firstOrNull()
        ?.trim()
        .orEmpty()

    private fun tryLocalLrc(audioPath: String): String? {
        val base = audioPath.substringBeforeLast('.', audioPath)
        val candidates = listOf("$base.lrc", "$base.LRC", "$audioPath.lrc", "$audioPath.LRC")
        for (path in candidates) {
            try {
                val file = java.io.File(path)
                if (file.exists() && file.canRead()) {
                    val text = file.readText()
                    if (text.isNotBlank()) return text
                }
            } catch (_: Exception) { }
        }
        return null
    }

    private fun hasSyncedTimestamps(text: String): Boolean =
        text.lineSequence().any { Regex("\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?]").containsMatchIn(it) }

    private fun fetchRawFromLrclib(artist: String, title: String): Pair<String?, String?> {
        repeat(2) { attempt ->
            try {
                val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                val queryParams = mutableListOf("track_name=$encodedTitle")
                if (artist.isNotBlank()) {
                    val encodedArtist = java.net.URLEncoder.encode(artist, "UTF-8")
                    queryParams += "artist_name=$encodedArtist"
                }
                val url = java.net.URL("https://lrclib.net/api/get?${queryParams.joinToString("&")}")
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "ABMusic/2.5.8")
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                if (conn.responseCode != 200) {
                    if (attempt == 0) return@repeat else return null to null
                }
                val json = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
                val synced = json.optString("syncedLyrics", "").trim().ifBlank { null }
                val plain  = json.optString("plainLyrics",  "").trim().ifBlank { null }
                return synced to plain
            } catch (_: Exception) {
                if (attempt == 1) return null to null
            }
        }
        return null to null
    }

    private fun fetchRawFromLrclibSearch(artist: String, title: String): Pair<String?, String?> {
        return try {
            val query = java.net.URLEncoder.encode("$artist $title", "UTF-8")
            val url   = java.net.URL("https://lrclib.net/api/search?q=$query")
            val conn  = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "ABMusic/2.5.8")
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (conn.responseCode != 200) return null to null
            val arr = org.json.JSONArray(conn.inputStream.bufferedReader().readText())
            if (arr.length() == 0) return null to null
            val best = (0 until arr.length())
                .map { arr.getJSONObject(it) }
                .firstOrNull { item ->
                    item.optString("syncedLyrics", "").isNotBlank() ||
                        item.optString("plainLyrics", "").isNotBlank()
                } ?: return null to null
            val synced = best.optString("syncedLyrics", "").trim().ifBlank { null }
            val plain  = best.optString("plainLyrics",  "").trim().ifBlank { null }
            synced to plain
        } catch (_: Exception) { null to null }
    }

    // ========== ALBUMS ==========
    fun getAlbums(): Flow<List<Album>> = songDao.getAlbums().map { rows ->
        rows.map { Album(it.albumId, it.album, it.artist, it.songCount, it.artworkUri) }
    }.flowOn(Dispatchers.Default)

    // ========== ARTISTS ==========
    fun getArtists(): Flow<List<Artist>> = songDao.getArtists().map { rows ->
        rows.mapIndexed { i, row -> Artist(i.toLong(), row.artist, 0, row.songCount, null) }
    }.flowOn(Dispatchers.Default)

    fun getSongsByArtist(artistName: String): Flow<List<Song>> =
        songDao.getSongsByArtist(artistName)
            .map { it.map(::toSong) }
            .flowOn(Dispatchers.Default)

    // ========== PLAYLISTS ==========
    fun getAllPlaylists(): Flow<List<Playlist>> =
        playlistDao.getAllPlaylistsWithCount().map { list ->
            list.map { Playlist(it.id, it.name, it.songCount, it.createdAt) }
        }.flowOn(Dispatchers.Default)

    suspend fun createPlaylist(name: String): Long = playlistDao.insertPlaylist(PlaylistEntity(name = name))

    suspend fun deletePlaylist(playlist: Playlist) =
        playlistDao.deletePlaylist(PlaylistEntity(playlist.id, playlist.name, playlist.createdAt))

    suspend fun renamePlaylist(id: Long, newName: String) = playlistDao.updatePlaylist(PlaylistEntity(id, newName))

    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>> =
        playlistDao.getPlaylistSongs(playlistId)
            .map { it.map(::toSong) }
            .flowOn(Dispatchers.Default)

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) =
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId))

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) =
        playlistDao.removeSongFromPlaylist(PlaylistSongCrossRef(playlistId, songId))

    // ========== FAVORITES ==========
    fun getFavoriteSongs(): Flow<List<Song>> =
        favoriteDao.getFavoriteSongs()
            .map { it.map(::toSong) }
            .flowOn(Dispatchers.Default)

    fun isFavorite(songId: Long): Flow<Boolean> = favoriteDao.isFavorite(songId)

    fun getAllFavoriteSongIds(): Flow<List<Long>> = favoriteDao.getAllFavoriteSongIds()

    suspend fun toggleFavorite(songId: Long, isFav: Boolean) {
        if (isFav) favoriteDao.addFavorite(FavoriteEntity(songId))
        else favoriteDao.removeFavorite(songId)
    }

    // ========== RECENTLY PLAYED ==========
    fun getRecentlyPlayed(limit: Int = 30): Flow<List<Song>> =
        recentlyPlayedDao.getRecentlyPlayed(limit)
            .map { it.map(::toSong) }
            .flowOn(Dispatchers.Default)

    suspend fun recordRecentlyPlayed(songId: Long) {
        recentlyPlayedDao.insertRecentlyPlayed(RecentlyPlayedEntity(songId))
        recentlyPlayedDao.pruneOldEntries()
    }

    // ========== MAPPER ==========
    private fun toSong(e: SongEntity) = Song(
        id = e.id,
        title = e.title,
        artist = e.artist,
        album = e.album,
        albumId = e.albumId,
        duration = e.duration,
        path = e.path,
        artworkUri = e.artworkUri,
        size = e.size,
        dateAdded = e.dateAdded,
        playCount = e.playCount
    )
}

data class CachedLyrics(val lyrics: String, val synced: Boolean)
