package com.ajaz.tiktok.core.parser

import com.ajaz.tiktok.core.logger.AppLogger
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.StringReader
import java.util.UUID

object ClashYamlParser {

    fun parse(rawText: String, profileName: String = "Imported Profile", sourceUrl: String? = null): NetworkProfile {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return NetworkProfile(
                id = UUID.randomUUID().toString(),
                name = profileName,
                sourceUrl = sourceUrl,
                rawConfig = rawText,
                isValid = false,
                validationMessage = "Configuration is empty"
            )
        }

        AppLogger.i("Parser", "Parsing configuration text (${trimmed.length} bytes)")

        // Try YAML parsing first
        try {
            val loaderOptions = LoaderOptions().apply {
                maxAliasesForCollections = 50
                isAllowDuplicateKeys = false
            }
            val yaml = Yaml(SafeConstructor(loaderOptions))
            val yamlObj = yaml.load<Any>(StringReader(trimmed))

            if (yamlObj is Map<*, *>) {
                return parseYamlMap(yamlObj, rawText, profileName, sourceUrl)
            }
        } catch (e: Exception) {
            AppLogger.w("Parser", "Standard YAML parser exception: ${e.message}. Attempting line-by-line fallback.")
        }

        // Fallback: try line-by-line Clash / Proxy URI extraction
        val nodes = extractNodesFallback(trimmed)
        if (nodes.isNotEmpty()) {
            AppLogger.i("Parser", "Extracted ${nodes.size} nodes via fallback parser")
            return NetworkProfile(
                id = UUID.randomUUID().toString(),
                name = profileName,
                sourceUrl = sourceUrl,
                rawConfig = rawText,
                proxyCount = nodes.size,
                proxies = nodes,
                selectedProxyId = nodes.firstOrNull()?.id,
                isValid = true,
                validationMessage = "Loaded ${nodes.size} usable provider endpoints"
            )
        }

        AppLogger.e("Parser", "Failed to find usable proxies in configuration")
        return NetworkProfile(
            id = UUID.randomUUID().toString(),
            name = profileName,
            sourceUrl = sourceUrl,
            rawConfig = rawText,
            isValid = false,
            validationMessage = "Configuration contains no usable proxy definitions"
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseYamlMap(
        map: Map<*, *>,
        rawText: String,
        profileName: String,
        sourceUrl: String?
    ): NetworkProfile {
        val proxiesList = mutableListOf<ProxyNode>()
        val proxyGroupsList = mutableListOf<String>()

        val rawProxies = map["proxies"] as? List<*>
        if (rawProxies != null) {
            for (item in rawProxies) {
                if (item is Map<*, *>) {
                    parseProxyItem(item)?.let { proxiesList.add(it) }
                }
            }
        }

        val rawGroups = map["proxy-groups"] as? List<*>
        if (rawGroups != null) {
            for (grp in rawGroups) {
                if (grp is Map<*, *>) {
                    val gName = grp["name"]?.toString()
                    if (!gName.isNullOrBlank()) {
                        proxyGroupsList.add(gName)
                    }
                }
            }
        }

        val isValid = proxiesList.isNotEmpty()
        val message = if (isValid) {
            "Successfully parsed ${proxiesList.size} providers and ${proxyGroupsList.size} groups"
        } else {
            "YAML is valid but contains 0 supported proxy definitions"
        }

        AppLogger.i("Parser", message)

        return NetworkProfile(
            id = UUID.randomUUID().toString(),
            name = profileName,
            sourceUrl = sourceUrl,
            rawConfig = rawText,
            proxyCount = proxiesList.size,
            proxies = proxiesList,
            proxyGroups = proxyGroupsList,
            selectedProxyId = proxiesList.firstOrNull()?.id,
            isValid = isValid,
            validationMessage = message
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseProxyItem(item: Map<*, *>): ProxyNode? {
        try {
            val name = item["name"]?.toString()?.trim() ?: "Unnamed Endpoint"
            val typeStr = item["type"]?.toString()?.trim()
            val server = item["server"]?.toString()?.trim() ?: return null
            val port = (item["port"] as? Number)?.toInt() ?: item["port"]?.toString()?.toIntOrNull() ?: 443

            val type = ProxyType.fromString(typeStr)
            val cipher = item["cipher"]?.toString()
            val password = item["password"]?.toString()
            val uuid = item["uuid"]?.toString()
            val alterId = (item["alterId"] as? Number)?.toInt() ?: 0
            val network = item["network"]?.toString()
            val tls = item["tls"] == true || item["tls"]?.toString()?.lowercase() == "true"
            val sni = item["sni"]?.toString() ?: item["servername"]?.toString() ?: item["server-name"]?.toString()
            var host = item["host"]?.toString()
            var path = (item["ws-path"] ?: item["path"])?.toString()

            val wsHeaders = mutableMapOf<String, String>()

            // Parse ws-opts or ws-headers
            val wsOpts = item["ws-opts"] as? Map<*, *>
            if (wsOpts != null) {
                val wsPath = wsOpts["path"]?.toString()
                if (!wsPath.isNullOrBlank()) path = wsPath
                val headers = wsOpts["headers"] as? Map<*, *>
                headers?.forEach { (k, v) ->
                    if (k != null && v != null) {
                        wsHeaders[k.toString()] = v.toString()
                        if (k.toString().equals("Host", ignoreCase = true)) {
                            host = v.toString()
                        }
                    }
                }
            }

            val rawWsHeaders = item["ws-headers"] as? Map<*, *>
            rawWsHeaders?.forEach { (k, v) ->
                if (k != null && v != null) {
                    wsHeaders[k.toString()] = v.toString()
                    if (k.toString().equals("Host", ignoreCase = true)) {
                        host = v.toString()
                    }
                }
            }

            // ALPN
            val alpnList = (item["alpn"] as? List<*>)?.mapNotNull { it?.toString() }

            // Reality options
            var realityPublicKey: String? = null
            var realityShortId: String? = null
            val realityOpts = item["reality-opts"] as? Map<*, *>
            if (realityOpts != null) {
                realityPublicKey = realityOpts["public-key"]?.toString()
                realityShortId = realityOpts["short-id"]?.toString()
            }

            val skipCertVerify = item["skip-cert-verify"] == true || item["allowInsecure"] == true
            val udp = item["udp"] != false

            return ProxyNode(
                id = UUID.randomUUID().toString(),
                name = name,
                type = type,
                server = server,
                port = port,
                cipher = cipher,
                password = password,
                uuid = uuid,
                alterId = alterId,
                network = network,
                tls = tls,
                sni = sni,
                host = host,
                path = path,
                wsHeaders = wsHeaders,
                alpn = alpnList,
                realityPublicKey = realityPublicKey,
                realityShortId = realityShortId,
                skipCertVerify = skipCertVerify,
                udp = udp
            )
        } catch (e: Exception) {
            AppLogger.w("Parser", "Error parsing individual proxy item: ${e.message}")
            return null
        }
    }

    private fun extractNodesFallback(rawText: String): List<ProxyNode> {
        val result = mutableListOf<ProxyNode>()
        val lines = rawText.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("- {") && trimmed.contains("name:") && trimmed.contains("server:")) {
                try {
                    val content = trimmed.removePrefix("-").trim()
                    val yaml = Yaml(SafeConstructor(LoaderOptions()))
                    val parsed = yaml.load<Map<*, *>>(content)
                    parseProxyItem(parsed)?.let { result.add(it) }
                } catch (_: Exception) {}
            }
        }
        return result
    }
}
