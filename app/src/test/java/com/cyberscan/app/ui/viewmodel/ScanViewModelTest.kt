package com.cyberscan.app.ui.viewmodel

import com.cyberscan.app.domain.model.ScanUiState
import com.cyberscan.app.service.ScanController
import com.cyberscan.app.service.ScanServiceLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScanViewModelTest {
    @Test
    fun `permission grant starts foreground scan service`() {
        val launcher = FakeServiceLauncher()
        val viewModel = ScanViewModel(FakeController(), launcher)

        viewModel.onStartGranted()

        assertEquals(1, launcher.startCount)
    }

    @Test
    fun `stop delegates to foreground service`() {
        val launcher = FakeServiceLauncher()
        val viewModel = ScanViewModel(FakeController(), launcher)

        viewModel.onStop()

        assertEquals(1, launcher.stopCount)
    }

    @Test
    fun `selection delegates normalized MAC`() {
        val controller = FakeController()
        val viewModel = ScanViewModel(controller, FakeServiceLauncher())

        viewModel.onTargetSelected("aa:bb:cc:11:22:33")

        assertEquals("AA:BB:CC:11:22:33", controller.selectedMac)
    }

    @Test
    fun `permission denial exposes actionable feedback`() {
        val viewModel = ScanViewModel(FakeController(), FakeServiceLauncher())

        viewModel.onPermissionDenied()

        assertEquals(
            "Bluetooth, location, and notification permissions are required to scan.",
            viewModel.permissionMessage.value,
        )
    }

    private class FakeController : ScanController {
        override val state = MutableStateFlow(ScanUiState())
        var selectedMac: String? = null

        override fun start() = Unit
        override fun stop() = Unit
        override fun retry() = Unit
        override fun selectTarget(macAddress: String) {
            selectedMac = macAddress
        }
    }

    private class FakeServiceLauncher : ScanServiceLauncher {
        var startCount = 0
        var stopCount = 0

        override fun start() {
            startCount += 1
        }

        override fun stop() {
            stopCount += 1
        }
    }
}

