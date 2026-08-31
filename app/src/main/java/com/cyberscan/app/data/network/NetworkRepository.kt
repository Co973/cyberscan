package com.cyberscan.app.data.network

import com.cyberscan.app.core.shell.CommandExecutor
import com.cyberscan.app.core.shell.CommandEnvironment
import com.cyberscan.app.core.shell.CommandEnvironmentResolver
import com.cyberscan.app.domain.model.NetworkDevice
import com.cyberscan.app.domain.usecase.Ipv4Subnet
import com.cyberscan.app.service.NetworkScanGateway

class NetworkRepository(
    private val commandExecutor: CommandExecutor,
    private val environmentResolver: CommandEnvironmentResolver? = null,
) : NetworkScanGateway {
    override suspend fun scan(interfaceName: String): Result<List<NetworkDevice>> = runCatching {
        require(INTERFACE_NAME.matches(interfaceName)) { "Invalid network interface name" }
        check(commandExecutor.start()) { "Root access is unavailable for Nmap" }
        val environment = environmentResolver?.resolve(setOf("ip", "nmap"))
            ?: if (environmentResolver == null) CommandEnvironment.AndroidRoot
            else error("Nmap is not available in a supported command environment")
        val addressResult = commandExecutor.run(
            listOf("ip", "-o", "-4", "addr", "show", "dev", interfaceName),
            environment,
        )
        check(addressResult.exitCode == 0) { "Unable to read the local network interface" }

        val addressMatch = IPV4_WITH_PREFIX.find(addressResult.stdout)
            ?: error("No IPv4 subnet is available on $interfaceName")
        val cidr = Ipv4Subnet.networkCidr(
            ip = addressMatch.groupValues[1],
            prefixLength = addressMatch.groupValues[2].toInt(),
        ) ?: error("The local IPv4 subnet is invalid")

        val nmapResult = commandExecutor.run(
            listOf("nmap", "-sn", "-oX", "-", cidr),
            environment,
        )
        check(nmapResult.exitCode == 0) { "Network sweep failed" }
        NmapXmlParser.parse(nmapResult.stdout).getOrThrow()
    }

    private companion object {
        val INTERFACE_NAME = Regex("^[A-Za-z0-9_.-]+$")
        val IPV4_WITH_PREFIX = Regex("\\binet\\s+(\\d{1,3}(?:\\.\\d{1,3}){3})/(\\d{1,2})\\b")
    }
}
