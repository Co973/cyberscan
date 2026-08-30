package com.cyberscan.app.data.bluetooth

data class HciAdapter(
    val name: String,
    val isUp: Boolean,
    val isRunning: Boolean,
)

object BluetoothAdapterDetector {
    private val blockRegex = Regex(
        pattern = "(?m)^(hci[0-9]+):[^\\r\\n]*(?:\\r?\\n((?:[ \\t]+[^\\r\\n]*(?:\\r?\\n|$))*))?",
    )

    fun parse(output: String): List<HciAdapter> = blockRegex.findAll(output).map { match ->
        val statusText = match.value.uppercase()
        HciAdapter(
            name = match.groupValues[1],
            isUp = Regex("\\bUP\\b").containsMatchIn(statusText),
            isRunning = Regex("\\bRUNNING\\b").containsMatchIn(statusText),
        )
    }.toList()

    fun select(adapters: List<HciAdapter>): HciAdapter? = adapters
        .asSequence()
        .filter { it.isUp && it.isRunning }
        .sortedBy { it.name.removePrefix("hci").toInt() }
        .firstOrNull()
}

