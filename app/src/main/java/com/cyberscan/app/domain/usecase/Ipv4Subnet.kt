package com.cyberscan.app.domain.usecase

object Ipv4Subnet {
    fun networkCidr(ip: String, prefixLength: Int): String? {
        if (prefixLength !in 0..32) return null

        val octets = ip.split('.')
        if (octets.size != 4) return null
        val values = octets.map { part ->
            if (part.isEmpty() || part.any { !it.isDigit() }) return null
            part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        }

        val address = values.fold(0L) { accumulator, octet ->
            (accumulator shl 8) or octet.toLong()
        }
        val mask = if (prefixLength == 0) {
            0L
        } else {
            (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
        }
        val network = address and mask
        val networkAddress = listOf(
            (network shr 24) and 0xFF,
            (network shr 16) and 0xFF,
            (network shr 8) and 0xFF,
            network and 0xFF,
        ).joinToString(".")

        return "$networkAddress/$prefixLength"
    }
}

