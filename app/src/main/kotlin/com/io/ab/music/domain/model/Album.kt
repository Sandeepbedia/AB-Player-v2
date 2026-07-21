package com.io.ab.music.domain.model

import androidx.compose.runtime.Stable

@Stable
data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val artworkUri: String?
)
