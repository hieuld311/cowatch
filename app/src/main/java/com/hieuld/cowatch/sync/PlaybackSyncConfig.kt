package com.hieuld.cowatch.sync

import android.content.Context

object PlaybackSyncConfig {

    private const val PREFS_NAME = "cowatch_sync_config"
    private const val AUDIO_LATENCY_PREFIX = "audio_latency_offset_ms_display_"

    // Production calibration can persist one audio offset per physical display ID.
    fun getAudioLatencyOffsetMs(
        context: Context,
        displayId: Int
    ): Long {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(AUDIO_LATENCY_PREFIX + displayId, 0L)
    }
}
