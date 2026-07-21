package com.io.ab.music.data.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import com.io.ab.music.domain.model.Video
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MIN_DURATION_MS = 3_000L   // 3 seconds min
        private const val MIN_SIZE_BYTES  = 100_000L // 100 KB min
    }

    fun scanAllVideos(): List<Video> {
        val videos = mutableListOf<Video>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE
        )

        val selection = "${MediaStore.Video.Media.DURATION} >= $MIN_DURATION_MS AND " +
                        "${MediaStore.Video.Media.SIZE} >= $MIN_SIZE_BYTES"
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        val cursor = context.contentResolver.query(
            collection, projection, selection, null, sortOrder
        ) ?: return videos

        cursor.use { c ->
            val idCol       = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol    = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val dispNameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durCol      = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol     = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthCol    = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol   = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val dataCol     = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val dateAddCol  = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val dateModCol  = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val mimeCol     = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

            while (c.moveToNext()) {
                val id    = c.getLong(idCol)
                val uri   = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                val path  = c.getString(dataCol) ?: ""

                // FIX: "dummy video details" bug — file deleted via a file-manager app
                // outside MediaStore leaves a stale row with cached title/duration/thumb
                // behind on devices whose file manager skips the media rescan. Skip rows
                // whose backing file is gone, and clean up the stale row when possible.
                if (path.isNotBlank() && !java.io.File(path).exists()) {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                    continue
                }

                videos.add(
                    Video(
                        id            = id,
                        title         = c.getString(titleCol) ?: c.getString(dispNameCol).substringBeforeLast("."),
                        displayName   = c.getString(dispNameCol) ?: "",
                        duration      = c.getLong(durCol),
                        size          = c.getLong(sizeCol),
                        width         = c.getInt(widthCol),
                        height        = c.getInt(heightCol),
                        path          = path,
                        contentUri    = uri,
                        thumbnailUri  = null,   // Coil-video decodes frame from contentUri
                        dateAdded     = c.getLong(dateAddCol),
                        dateModified  = c.getLong(dateModCol),
                        mimeType      = c.getString(mimeCol) ?: "video/mp4"
                    )
                )
            }
        }

        return videos
    }
}
