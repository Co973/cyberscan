package com.cyberscan.app.data.network

import com.cyberscan.app.domain.model.NetworkDevice
import com.cyberscan.app.domain.model.normalizeMac
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

object NmapXmlParser {
    fun parse(xml: String): Result<List<NetworkDevice>> = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            runCatching {
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
            }
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val hosts = document.getElementsByTagName("host")

        buildList {
            for (hostIndex in 0 until hosts.length) {
                val host = hosts.item(hostIndex) as? Element ?: continue
                val addresses = host.getElementsByTagName("address")
                var ipv4: String? = null
                var mac: String? = null
                var vendor: String? = null

                for (addressIndex in 0 until addresses.length) {
                    val address = addresses.item(addressIndex) as? Element ?: continue
                    when (address.getAttribute("addrtype")) {
                        "ipv4" -> ipv4 = address.getAttribute("addr").takeIf(String::isNotBlank)
                        "mac" -> {
                            mac = address.getAttribute("addr").takeIf(String::isNotBlank)
                            vendor = address.getAttribute("vendor").takeIf(String::isNotBlank)
                        }
                    }
                }

                val hostnames = host.getElementsByTagName("hostname")
                val hostname = (hostnames.item(0) as? Element)
                    ?.getAttribute("name")
                    ?.takeIf(String::isNotBlank)

                if (ipv4 != null && mac != null) {
                    add(
                        NetworkDevice(
                            macAddress = normalizeMac(mac),
                            ipAddress = ipv4,
                            vendorOui = vendor,
                            hostname = hostname,
                        ),
                    )
                }
            }
        }
    }
}
