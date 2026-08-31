package com.cyberscan.app.domain.model

enum class Confidence { NONE, MAYBE, HIGH }

enum class DeviceClass {
    COMPUTER,
    PHONE,
    NETWORKING,
    AUDIO_VIDEO,
    PERIPHERAL,
    WEARABLE,
    UNKNOWN,
}

enum class NetworkStatus { Pending, Available, Unavailable }

class BluetoothDevice(
    macAddress: String,
    val name: String?,
    val deviceClass: DeviceClass,
    val rssi: Int?,
    val firstSeenAtMs: Long,
    val lastSeenAtMs: Long,
) {
    val macAddress: String = normalizeMac(macAddress)

    fun copy(
        macAddress: String = this.macAddress,
        name: String? = this.name,
        deviceClass: DeviceClass = this.deviceClass,
        rssi: Int? = this.rssi,
        firstSeenAtMs: Long = this.firstSeenAtMs,
        lastSeenAtMs: Long = this.lastSeenAtMs,
    ): BluetoothDevice = BluetoothDevice(
        macAddress = macAddress,
        name = name,
        deviceClass = deviceClass,
        rssi = rssi,
        firstSeenAtMs = firstSeenAtMs,
        lastSeenAtMs = lastSeenAtMs,
    )

    override fun equals(other: Any?): Boolean =
        other is BluetoothDevice &&
            macAddress == other.macAddress &&
            name == other.name &&
            deviceClass == other.deviceClass &&
            rssi == other.rssi &&
            firstSeenAtMs == other.firstSeenAtMs &&
            lastSeenAtMs == other.lastSeenAtMs

    override fun hashCode(): Int {
        var result = macAddress.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + deviceClass.hashCode()
        result = 31 * result + (rssi ?: 0)
        result = 31 * result + firstSeenAtMs.hashCode()
        result = 31 * result + lastSeenAtMs.hashCode()
        return result
    }

    override fun toString(): String =
        "BluetoothDevice(macAddress=$macAddress, name=$name, deviceClass=$deviceClass, " +
            "rssi=$rssi, firstSeenAtMs=$firstSeenAtMs, lastSeenAtMs=$lastSeenAtMs)"
}

data class NetworkDevice(
    val macAddress: String,
    val ipAddress: String,
    val vendorOui: String?,
    val hostname: String?,
) {
    init {
        require(MAC_PATTERN.matches(macAddress.uppercase())) { "Invalid MAC address" }
    }

    val normalizedMacAddress: String = macAddress.uppercase()
}

data class MergedDevice(
    val bluetooth: BluetoothDevice,
    val correlatedNetwork: NetworkDevice?,
    val confidence: Confidence,
    val confidenceReason: String,
) {
    val isNmapCandidate: Boolean = confidence != Confidence.NONE
}

data class EmfReading(
    val magnitudeMicroTesla: Float,
    val baselineMicroTesla: Float,
    val anomalyMicroTesla: Float,
    val timestampMs: Long,
)

sealed interface ScanPhase {
    data object Idle : ScanPhase
    data object Calibrating : ScanPhase
    data object Scanning : ScanPhase
    data object Complete : ScanPhase
    data class Failed(val reason: String) : ScanPhase
}

data class ScanUiState(
    val phase: ScanPhase = ScanPhase.Idle,
    val devices: List<MergedDevice> = emptyList(),
    val selectedMac: String? = null,
    val networkStatus: NetworkStatus = NetworkStatus.Pending,
    val adapterName: String? = null,
    val emf: EmfReading? = null,
    val warning: String? = null,
)

private val MAC_PATTERN = Regex("^[0-9A-F]{2}(?::[0-9A-F]{2}){5}$")

fun normalizeMac(value: String): String {
    val normalized = value.trim().uppercase().replace('-', ':')
    require(MAC_PATTERN.matches(normalized)) { "Invalid MAC address" }
    return normalized
}
