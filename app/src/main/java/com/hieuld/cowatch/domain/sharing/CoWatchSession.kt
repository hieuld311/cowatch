package com.hieuld.cowatch.domain.sharing

data class CoWatchSession(
    val sessionId: String,
    val hostDisplayId: Int,
    val requestedDisplayIds: Set<Int>,
    val participantDisplayIds: Set<Int>,
    val deniedDisplayIds: Set<Int> = emptySet(),
    val readyDisplayIds: Set<Int> = emptySet(),
    val anchorPositionMs: Long,
    val status: CoWatchSessionStatus
) {
    val pendingDisplayIds: Set<Int>
        get() = requestedDisplayIds - participantDisplayIds - deniedDisplayIds

    val allResponsesReceived: Boolean
        get() = pendingDisplayIds.isEmpty()

    val allDisplaysReady: Boolean
        get() = participantDisplayIds.isNotEmpty() &&
                readyDisplayIds.containsAll(participantDisplayIds)
}
