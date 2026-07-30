package com.ivi.pid.viewmodel

import androidx.lifecycle.ViewModel
import com.ivi.pid.sharing.PidShareCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FrontPlayerViewModel @Inject constructor(
    val shareCoordinator: PidShareCoordinator
) : ViewModel() {
    val targets = shareCoordinator.targets
    val sessionActive = shareCoordinator.sessionActive
    val hostNotifications = shareCoordinator.hostNotifications
}
