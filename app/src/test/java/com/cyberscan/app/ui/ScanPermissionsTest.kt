package com.cyberscan.app.ui

import android.Manifest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScanPermissionsTest {
    @Test
    fun `Android 16 requests nearby location and notification permissions`() {
        assertEquals(
            setOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS,
            ),
            requiredScanPermissions(apiLevel = 36).toSet(),
        )
    }

    @Test
    fun `API 31 omits notification runtime permission`() {
        assertEquals(
            setOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
            requiredScanPermissions(apiLevel = 31).toSet(),
        )
    }
}
