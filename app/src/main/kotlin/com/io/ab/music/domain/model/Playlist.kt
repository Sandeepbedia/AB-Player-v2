package com.io.ab.music.domain.model

import androidx.compose.runtime.Stable

@Stable
data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val artworkUri: String? = null
)
