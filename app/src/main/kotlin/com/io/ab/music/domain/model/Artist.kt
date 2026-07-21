package com.io.ab.music.domain.model

import androidx.compose.runtime.Stable

@Stable
data class Artist(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val songCount: Int,
    val artworkUri: String?
)
