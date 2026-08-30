package com.cyberscan.app.data.bluetooth

import com.cyberscan.app.domain.model.BluetoothDevice
import com.cyberscan.app.domain.model.DeviceClass

object BluelogParser {
    private val macRegex = Regex("(?i)([0-9a-f]{2}(?::[0-9a-f]{2}){5})")
    private val timestampRegex = Regex("^\\s*\\[[^]]+]\\s*")
    private val labeledNameRegex = Regex(
        "(?i)\\bName\\s*[:=]\\s*(?:\\\"([^\\\"]+)\\\"|([^,]+?)(?=\\s+(?:CoD|Class|RSSI)\\b|$))",
    )
    private val classOfDeviceRegex = Regex(
        "(?i)\\b(?:CoD|ClassOfDevice)\\s*[:=]\\s*(0x[0-9a-f]+|[0-9]+)",
    )
    private val labeledClassRegex = Regex(
        "(?i)\\bClass\\s*[:=]\\s*([^,]+?)(?=\\s+RSSI\\b|$)",
    )
    private val rssiRegex = Regex("(?i)\\bRSSI\\s*[:=]?\\s*(-[0-9]{1,3})")

    fun parse(line: String, nowMs: Long): BluetoothDevice? {
        val macMatch = macRegex.find(line) ?: return null
        val remainder = timestampRegex.replace(line.replaceRange(macMatch.range, ""), "").trim()
        val hasCommaFields = remainder.trimStart().startsWith(',')
        val commaFields = remainder
            .trimStart(',', ' ', '\t')
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)

        val labeledNameMatch = labeledNameRegex.find(line)
        val name = labeledNameMatch?.let {
            it.groupValues[1].ifBlank { it.groupValues[2] }.trim().ifBlank { null }
        } ?: commaFields.firstOrNull()
            ?.takeIf { hasCommaFields }
            ?.takeUnless { field -> field.startsWith("RSSI", ignoreCase = true) }

        val classOfDevice = classOfDeviceRegex.find(line)?.groupValues?.get(1)?.let(::parseNumber)
        val classHint = labeledClassRegex.find(line)?.groupValues?.get(1)
            ?: commaFields.getOrNull(1)
        val rssi = rssiRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()

        return BluetoothDevice(
            macAddress = macMatch.value,
            name = name,
            deviceClass = classOfDevice?.let(::classifyFromClassOfDevice)
                ?: classifyFromHint(classHint),
            rssi = rssi,
            firstSeenAtMs = nowMs,
            lastSeenAtMs = nowMs,
        )
    }

    private fun parseNumber(value: String): Int? = if (value.startsWith("0x", ignoreCase = true)) {
        value.drop(2).toIntOrNull(16)
    } else {
        value.toIntOrNull()
    }

    private fun classifyFromClassOfDevice(classOfDevice: Int): DeviceClass = when (
        (classOfDevice shr 8) and 0x1F
    ) {
        0x01 -> DeviceClass.COMPUTER
        0x02 -> DeviceClass.PHONE
        0x03 -> DeviceClass.NETWORKING
        0x04 -> DeviceClass.AUDIO_VIDEO
        0x05 -> DeviceClass.PERIPHERAL
        0x07 -> DeviceClass.WEARABLE
        else -> DeviceClass.UNKNOWN
    }

    private fun classifyFromHint(hint: String?): DeviceClass {
        val normalized = hint?.lowercase().orEmpty()
        return when {
            "computer" in normalized || "laptop" in normalized -> DeviceClass.COMPUTER
            "phone" in normalized || "smartphone" in normalized -> DeviceClass.PHONE
            "network" in normalized || "access point" in normalized || "router" in normalized -> {
                DeviceClass.NETWORKING
            }
            "audio" in normalized || "video" in normalized || "headset" in normalized -> {
                DeviceClass.AUDIO_VIDEO
            }
            "peripheral" in normalized || "keyboard" in normalized || "mouse" in normalized -> {
                DeviceClass.PERIPHERAL
            }
            "wearable" in normalized || "watch" in normalized -> DeviceClass.WEARABLE
            else -> DeviceClass.UNKNOWN
        }
    }
}
