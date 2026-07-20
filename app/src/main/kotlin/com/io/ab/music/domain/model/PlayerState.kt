package com.io.ab.music.domain.model

import androidx.compose.runtime.Stable

@Stable
data class PlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val progress: Long = 0L,
    val duration: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val queue: List<Song> = emptyList(),
    val currentQueueIndex: Int = 0
)

enum class RepeatMode { OFF, ONE, ALL }
