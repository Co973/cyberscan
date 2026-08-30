package com.cyberscan.app.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class Ipv4SubnetTest {
    @ParameterizedTest
    @CsvSource(
        "192.168.1.42, 24, 192.168.1.0/24",
        "10.2.3.4, 0, 0.0.0.0/0",
        "10.2.3.4, 31, 10.2.3.4/31",
        "10.2.3.4, 32, 10.2.3.4/32",
    )
    fun `network CIDR handles valid boundaries`(ip: String, prefix: Int, expected: String) {
        assertEquals(expected, Ipv4Subnet.networkCidr(ip, prefix))
    }

    @ParameterizedTest
    @CsvSource(
        "256.1.1.1, 24",
        "10.0.0, 24",
        "10.0.0.1, -1",
        "10.0.0.1, 33",
        "10.a.0.1, 24",
    )
    fun `invalid addresses and prefixes return null`(ip: String, prefix: Int) {
        assertNull(Ipv4Subnet.networkCidr(ip, prefix))
    }
}

