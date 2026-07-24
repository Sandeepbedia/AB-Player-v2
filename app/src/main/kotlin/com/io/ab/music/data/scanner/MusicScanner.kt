package com.io.ab.music.data.scanner

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.io.ab.music.data.db.entity.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MIN_DURATION_MS = 30_000L  // 30 seconds
    }

    /**
     * Scans MediaStore for audio files.
     * @param includeSubfolders When false, only top-level Music directory songs are included
     *                          (i.e. no songs nested more than one folder deep inside Music/).
     */
    fun scanAllMusic(includeSubfolders: Boolean = true): List<SongEntity> {
        val songs = mutableListOf<SongEntity>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= $MIN_DURATION_MS"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )

        cursor?.use {
            val idCol      = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol   = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol  = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol   = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol= it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol    = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeCol    = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateCol    = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (it.moveToNext()) {
                val id      = it.getLong(idCol)
                val albumId = it.getLong(albumIdCol)
                val path    = it.getString(dataCol) ?: ""

                // Include Subfolders filter:
                // When disabled, skip songs whose path is nested more than 2 levels below
                // a standard top-level music directory (e.g. /Music/subfolder/song.mp3 → skip)
                if (!includeSubfolders && isNestedBeyondTopLevel(path)) continue

                // FIX: "dummy song details" bug — when a file is deleted straight from a
                // file-manager app (not through MediaStore), some OEM file managers never
                // trigger a media rescan, so the old MediaStore row (and its cached
                // title/duration/artwork) lingers and still shows up here even though the
                // file is gone. Skip any row whose backing file no longer exists, and best
                // effort clean up the stale MediaStore row so it doesn't keep reappearing.
                if (path.isNotBlank() && !java.io.File(path).exists()) {
                    runCatching {
                        val staleUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        context.contentResolver.delete(staleUri, null, null)
                    }
                    continue
                }

                // Per-song embedded artwork URI (reads ID3/FLAC thumbnail directly)
                val artworkUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                ).buildUpon().appendPath("albumart").build().toString()

                songs.add(
                    SongEntity(
                        id         = id,
                        title      = it.getString(titleCol)  ?: "Unknown",
                        artist     = it.getString(artistCol) ?: "Unknown Artist",
                        album      = it.getString(albumCol)  ?: "Unknown Album",
                        albumId    = albumId,
                        duration   = it.getLong(durationCol),
                        path       = path,
                        artworkUri = artworkUri,
                        size       = it.getLong(sizeCol),
                        dateAdded  = it.getLong(dateCol) * 1000
                    )
                )
            }
        }
        return songs
    }

    /**
     * Returns true if [path] is nested more than one folder deep under a
     * known music root (/Music, /Download, /DCIM, etc.).
     * Example: /storage/emulated/0/Music/Artist/song.mp3  →  true (skip)
     *          /storage/emulated/0/Music/song.mp3          →  false (keep)
     */
    private fun isNestedBeyondTopLevel(path: String): Boolean {
        val musicRoots = listOf("/Music/", "/music/", "/Download/", "/Downloads/")
        for (root in musicRoots) {
            val idx = path.indexOf(root)
            if (idx >= 0) {
                val afterRoot = path.substring(idx + root.length)
                // If there is another '/' before the filename, it's nested
                return afterRoot.contains('/')
            }
        }
        return false // Unknown path → include by default
    }
}
