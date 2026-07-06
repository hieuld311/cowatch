package com.hieuld.cowatch.domain.sharing

data class CoWatchSession(
    val sessionId: String,
    val hostDisplayId: Int,
    val participantDisplayIds: Set<Int>,
    val readyDisplayIds: Set<Int> = emptySet(),
    val anchorPositionMs: Long,
    val status: CoWatchSessionStatus
) {
    val allDisplaysReady: Boolean
        get() = participantDisplayIds.isNotEmpty() &&
                readyDisplayIds.containsAll(participantDisplayIds)
}
