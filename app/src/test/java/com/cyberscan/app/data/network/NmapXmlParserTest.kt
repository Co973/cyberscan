package com.cyberscan.app.data.network

import com.cyberscan.app.domain.model.NetworkDevice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NmapXmlParserTest {
    @Test
    fun `XML extracts IPv4 MAC vendor and hostname`() {
        val xml = """
            <?xml version="1.0"?>
            <nmaprun>
              <host><status state="up"/>
                <address addr="192.168.1.8" addrtype="ipv4"/>
                <address addr="aa:bb:cc:11:22:33" addrtype="mac" vendor="Intel"/>
                <hostnames><hostname name="deck.local" type="PTR"/></hostnames>
              </host>
            </nmaprun>
        """.trimIndent()

        val devices = NmapXmlParser.parse(xml).getOrThrow()

        assertEquals(
            listOf(NetworkDevice("AA:BB:CC:11:22:33", "192.168.1.8", "Intel", "deck.local")),
            devices,
        )
    }

    @Test
    fun `hosts without both IPv4 and MAC are ignored`() {
        val xml = """
            <nmaprun>
              <host><address addr="192.168.1.2" addrtype="ipv4"/></host>
              <host><address addr="AA:BB:CC:11:22:33" addrtype="mac"/></host>
              <host>
                <address addr="fe80::1" addrtype="ipv6"/>
                <address addr="AA:BB:CC:44:55:66" addrtype="mac"/>
              </host>
            </nmaprun>
        """.trimIndent()

        assertEquals(emptyList<NetworkDevice>(), NmapXmlParser.parse(xml).getOrThrow())
    }

    @Test
    fun `missing optional metadata remains null`() {
        val xml = """
            <nmaprun><host>
              <address addr="10.0.0.4" addrtype="ipv4"/>
              <address addr="00:11:22:33:44:55" addrtype="mac"/>
            </host></nmaprun>
        """.trimIndent()

        val device = NmapXmlParser.parse(xml).getOrThrow().single()

        assertEquals(null, device.vendorOui)
        assertEquals(null, device.hostname)
    }

    @Test
    fun `malformed XML returns failure`() {
        assertTrue(NmapXmlParser.parse("<nmaprun><host>").isFailure)
    }
}
