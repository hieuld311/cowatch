package com.hieuld.cowatch.playback

sealed interface CoWatchPlaybackState {
    data object Idle : CoWatchPlaybackState
    data object Preparing : CoWatchPlaybackState
    data object Playing : CoWatchPlaybackState
    data object Paused : CoWatchPlaybackState
    data object SharingPreparing : CoWatchPlaybackState
    data object Sharing : CoWatchPlaybackState

    data class Error(
        val message: String
    ) : CoWatchPlaybackState
}
