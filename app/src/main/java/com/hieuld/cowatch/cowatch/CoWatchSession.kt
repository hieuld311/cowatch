package com.hieuld.cowatch.cowatch

data class CoWatchSession(
    val sessionId: String,
    val hostDisplayId: Int,
    val participantDisplayIds: Set<Int>,
    val readyDisplayIds: Set<Int> = emptySet(),
    val mediaUrl: String,
    val anchorPositionMs: Long,
    val status: CoWatchSessionStatus
) {
    val allReceiversReady: Boolean
        get() = participantDisplayIds.isNotEmpty() &&
                readyDisplayIds.containsAll(participantDisplayIds)
}
