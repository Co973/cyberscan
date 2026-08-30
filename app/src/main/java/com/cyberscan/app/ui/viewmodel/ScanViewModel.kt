package com.cyberscan.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.cyberscan.app.domain.model.ScanUiState
import com.cyberscan.app.domain.model.normalizeMac
import com.cyberscan.app.service.ScanController
import com.cyberscan.app.service.ScanServiceLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val controller: ScanController,
    private val serviceLauncher: ScanServiceLauncher,
) : ViewModel() {
    val uiState: StateFlow<ScanUiState> = controller.state

    private val _permissionMessage = MutableStateFlow<String?>(null)
    val permissionMessage: StateFlow<String?> = _permissionMessage.asStateFlow()

    fun onStartGranted() {
        _permissionMessage.value = null
        serviceLauncher.start()
    }

    fun onRetryGranted() {
        _permissionMessage.value = null
        serviceLauncher.start()
    }

    fun onStop() {
        serviceLauncher.stop()
    }

    fun onTargetSelected(macAddress: String) {
        controller.selectTarget(normalizeMac(macAddress))
    }

    fun onPermissionDenied() {
        _permissionMessage.value =
            "Bluetooth, location, and notification permissions are required to scan."
    }
}

