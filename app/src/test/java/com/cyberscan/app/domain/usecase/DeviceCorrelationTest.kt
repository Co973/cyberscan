package com.cyberscan.app.domain.usecase

import com.cyberscan.app.domain.model.BluetoothDevice
import com.cyberscan.app.domain.model.Confidence
import com.cyberscan.app.domain.model.DeviceClass
import com.cyberscan.app.domain.model.NetworkDevice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DeviceCorrelationTest {
    @Test
    fun `eligible OUI match promotes confidence to high`() {
        val bt = btDevice("AA:BB:CC:00:00:01", DeviceClass.COMPUTER)
        val network = networkDevice("AA:BB:CC:99:88:77", "192.168.1.10")

        val merged = CorrelateWithNetwork.correlate(listOf(bt), listOf(network)).single()

        assertEquals(Confidence.HIGH, merged.confidence)
        assertEquals(network, merged.correlatedNetwork)
        assertEquals("Manufacturer prefix matched a network host", merged.confidenceReason)
    }

    @Test
    fun `peripheral is never promoted by coincidental OUI`() {
        val bt = btDevice("AA:BB:CC:00:00:01", DeviceClass.PERIPHERAL)
        val network = networkDevice("AA:BB:CC:99:88:77", "192.168.1.10")

        val merged = CorrelateWithNetwork.correlate(listOf(bt), listOf(network)).single()

        assertEquals(Confidence.NONE, merged.confidence)
        assertNull(merged.correlatedNetwork)
    }

    @Test
    fun `normalized hostname match promotes eligible device`() {
        val bt = btDevice("10:20:30:00:00:01", DeviceClass.PHONE, name = "Cyber Deck")
        val network = networkDevice(
            mac = "AA:BB:CC:99:88:77",
            ip = "192.168.1.10",
            hostname = "cyber-deck.local",
        )

        val merged = CorrelateWithNetwork.correlate(listOf(bt), listOf(network)).single()

        assertEquals(Confidence.HIGH, merged.confidence)
        assertEquals("Advertised name matched the network hostname", merged.confidenceReason)
    }

    @Test
    fun `eligible device without network match remains maybe`() {
        val bt = btDevice("10:20:30:00:00:01", DeviceClass.NETWORKING)

        val merged = CorrelateWithNetwork.correlate(listOf(bt), emptyList()).single()

        assertEquals(Confidence.MAYBE, merged.confidence)
        assertEquals("Bluetooth class may support IP networking", merged.confidenceReason)
    }

    private fun btDevice(
        mac: String,
        deviceClass: DeviceClass,
        name: String? = "node",
    ) = BluetoothDevice(mac, name, deviceClass, null, 1, 2)

    private fun networkDevice(
        mac: String,
        ip: String,
        hostname: String? = null,
    ) = NetworkDevice(mac, ip, "Vendor", hostname)
}

