package com.ivi.common.ipc;

import android.view.Surface;
import com.ivi.common.ipc.ReceiverRegistration;
import com.ivi.common.ipc.ReceiverStatus;
import com.ivi.common.ipc.IRearPlaybackReceiver;

interface IPidPlaybackCoordinator {
    int getProtocolVersion();
    void registerReceiver(in ReceiverRegistration registration, IRearPlaybackReceiver receiver);
    void unregisterReceiver(String role, IRearPlaybackReceiver receiver);
    void updateReceiverStatus(in ReceiverStatus status);
    void registerRenderSurface(
        String role,
        String sessionId,
        long surfaceGeneration,
        in Surface surface,
        int width,
        int height
    );
    void unregisterRenderSurface(String role, String sessionId, long surfaceGeneration);
    void requestLeaveSharing(String role, String sessionId);
}
