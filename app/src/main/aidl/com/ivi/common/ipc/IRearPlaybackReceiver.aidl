package com.ivi.common.ipc;

import com.ivi.common.ipc.SharedSessionSnapshot;

oneway interface IRearPlaybackReceiver {
    void onShareRequest(in SharedSessionSnapshot snapshot);
    void onSharedSession(in SharedSessionSnapshot snapshot);
    void onStopSharing(String sessionId, String reason);
}
