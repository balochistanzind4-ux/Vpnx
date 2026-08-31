package com.ajaz.tiktok.core.parser

import com.ajaz.tiktok.core.logger.AppLogger
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import java.util.UUID
import org.json.JSONObject

object ClashYamlParser {

    fun parse(rawText: String, profileName: String = "Imported Profile", sourceUrl: String? = null): NetworkProfile {
        var textToParse = rawText.trim()
        if (textToParse.isEmpty()) {
            return NetworkProfile(
                id = UUID.randomUUID().toString(),
                name = profileName,
                sourceUrl = sourceUrl,
                rawConfig = rawText,
                isValid = false,
                validationMessage = "Configuration is empty"
            )
        }

        // 1. Check if the whole text is a Base64-encoded subscription payload
        if (!textToParse.contains("proxies:") && !textToParse.contains("server:") && isLikelyBase64(textToParse)) {
            try {
                val decoded = decodeBase64(textToParse)
                if (decoded.isNotBlank()) {
                    AppLogger.i("Parser", "Successfully decoded Base64 subscription payload (${decoded.length} chars)")
                    textToParse = decoded.trim()
                }
            } catch (e: Exception) {
                AppLogger.d("Parser", "Base64 decode attempt ignored: ${e.message}")
            }
        }

        AppLogger.i("Parser", "Parsing configuration text (${textToParse.length} bytes)")

        // 2. Try YAML parsing first (Clash / Clash.Meta / Mihomo format)
        try {
            val loaderOptions = LoaderOptions().apply {
                maxAliasesForCollections = 100
                isAllowDuplicateKeys = false
            }
            val yaml = Yaml(SafeConstructor(loaderOptions))
            val yamlObj = yaml.load<Any>(StringReader(textToParse))

            if (yamlObj is Map<*, *>) {
                val profile = parseYamlMap(yamlObj, rawText, profileName, sourceUrl)
                if (profile.isValid && profile.proxies.isNotEmpty()) {
                    return profile
                }
            }
        } catch (e: Exception) {
            AppLogger.w("Parser", "YAML parser exception: ${e.message}. Attempting URI / line-by-line fallback.")
        }

        // 3. Try URI list parsing (vmess://, vless://, trojan://, ss://, socks5://, etc.)
        val uriNodes = parseUriList(textToParse)
        if (uriNodes.isNotEmpty()) {
            AppLogger.i("Parser", "Extracted ${uriNodes.size} nodes via Proxy URI parser")
            return NetworkProfile(
                id = UUID.randomUUID().toString(),
                name = profileName,
                sourceUrl = sourceUrl,
                rawConfig = rawText,
                proxyCount = uriNodes.size,
                proxies = uriNodes,
                selectedProxyId = uriNodes.firstOrNull()?.id,
                isValid = true,
                validationMessage = "Loaded ${uriNodes.size} usable provider endpoints"
            )
        }

        // 4. Fallback: try line-by-line Clash item extraction
        val nodes = extractNodesFallback(textToParse)
        if (nodes.isNotEmpty()) {
            AppLogger.i("Parser", "Extracted ${nodes.size} nodes via fallback line parser")
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

        AppLogger.e("Parser", "Failed to find usable proxy definitions in configuration")
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
            val password = item["password"]?.toString() ?: item["pass"]?.toString()
            val uuid = item["uuid"]?.toString() ?: item["username"]?.toString() ?: item["user"]?.toString()
            val alterId = (item["alterId"] as? Number)?.toInt() ?: item["alterId"]?.toString()?.toIntOrNull() ?: 0
            val network = item["network"]?.toString()
            val tls = item["tls"] == true || item["tls"]?.toString()?.lowercase() == "true" || item["security"]?.toString()?.lowercase() == "tls"
            val sni = item["sni"]?.toString() ?: item["servername"]?.toString() ?: item["server-name"]?.toString() ?: item["peer"]?.toString()
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

            // gRPC opts
            val grpcOpts = item["grpc-opts"] as? Map<*, *>
            if (grpcOpts != null) {
                val serviceName = grpcOpts["grpc-service-name"]?.toString() ?: grpcOpts["serviceName"]?.toString()
                if (!serviceName.isNullOrBlank() && path.isNullOrBlank()) {
                    path = serviceName
                }
            }

            // ALPN
            val alpnList = (item["alpn"] as? List<*>)?.mapNotNull { it?.toString() }

            // Reality options
            var realityPublicKey: String? = null
            var realityShortId: String? = null
            val realityOpts = item["reality-opts"] as? Map<*, *>
            if (realityOpts != null) {
                realityPublicKey = realityOpts["public-key"]?.toString() ?: realityOpts["publicKey"]?.toString()
                realityShortId = realityOpts["short-id"]?.toString() ?: realityOpts["shortId"]?.toString()
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

    private fun parseUriList(text: String): List<ProxyNode> {
        val result = mutableListOf<ProxyNode>()
        val lines = text.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) continue

            try {
                if (trimmed.startsWith("vmess://", ignoreCase = true)) {
                    parseVmessUri(trimmed)?.let { result.add(it) }
                } else if (trimmed.startsWith("vless://", ignoreCase = true)) {
                    parseVlessUri(trimmed)?.let { result.add(it) }
                } else if (trimmed.startsWith("trojan://", ignoreCase = true)) {
                    parseTrojanUri(trimmed)?.let { result.add(it) }
                } else if (trimmed.startsWith("ss://", ignoreCase = true)) {
                    parseShadowsocksUri(trimmed)?.let { result.add(it) }
                } else if (trimmed.startsWith("socks5://", ignoreCase = true) || trimmed.startsWith("socks://", ignoreCase = true)) {
                    parseSocksUri(trimmed)?.let { result.add(it) }
                } else if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                    parseHttpUri(trimmed)?.let { result.add(it) }
                }
            } catch (e: Exception) {
                AppLogger.d("Parser", "Error parsing URI line: ${e.message}")
            }
        }
        return result
    }

    private fun parseVmessUri(uriStr: String): ProxyNode? {
        return try {
            val base64Data = uriStr.substringAfter("vmess://").trim()
            val jsonStr = decodeBase64(base64Data)
            val json = JSONObject(jsonStr)

            val name = json.optString("ps", "VMess Endpoint")
            val server = json.optString("add", "").trim()
            if (server.isEmpty()) return null
            val port = json.optInt("port", 443)
            val uuid = json.optString("id", "")
            val alterId = json.optInt("aid", 0)
            val net = json.optString("net", "tcp")
            val tls = json.optString("tls", "").equals("tls", ignoreCase = true)
            val host = json.optString("host", "")
            val path = json.optString("path", "")
            val sni = json.optString("sni", host)

            ProxyNode(
                id = UUID.randomUUID().toString(),
                name = name,
                type = ProxyType.VMESS,
                server = server,
                port = port,
                uuid = uuid,
                alterId = alterId,
                network = net,
                tls = tls,
                sni = if (sni.isNotBlank()) sni else null,
                host = if (host.isNotBlank()) host else null,
                path = if (path.isNotBlank()) path else null,
                cipher = json.optString("type", "auto")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVlessUri(uriStr: String): ProxyNode? {
        return try {
            val uri = URI(uriStr)
            val uuid = uri.userInfo ?: ""
            val server = uri.host ?: return null
            val port = if (uri.port != -1) uri.port else 443
            val name = if (!uri.fragment.isNullOrBlank()) URLDecoder.decode(uri.fragment, "UTF-8") else "VLESS Endpoint"

            val queryParams = parseQueryParams(uri.rawQuery)
            val network = queryParams["type"] ?: "tcp"
            val security = queryParams["security"] ?: "none"
            val tls = security == "tls" || security == "reality"
            val sni = queryParams["sni"] ?: queryParams["peer"]
            val host = queryParams["host"]
            val path = queryParams["path"]?.let { URLDecoder.decode(it, "UTF-8") }
            val pbk = queryParams["pbk"] ?: queryParams["public-key"]
            val sid = queryParams["sid"] ?: queryParams["short-id"]

            ProxyNode(
                id = UUID.randomUUID().toString(),
                name = name,
                type = ProxyType.VLESS,
                server = server,
                port = port,
                uuid = uuid,
                network = network,
                tls = tls,
                sni = sni,
                host = host,
                path = path,
                realityPublicKey = pbk,
                realityShortId = sid
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTrojanUri(uriStr: String): ProxyNode? {
        return try {
            val uri = URI(uriStr)
            val password = uri.userInfo ?: ""
            val server = uri.host ?: return null
            val port = if (uri.port != -1) uri.port else 443
            val name = if (!uri.fragment.isNullOrBlank()) URLDecoder.decode(uri.fragment, "UTF-8") else "Trojan Endpoint"

            val queryParams = parseQueryParams(uri.rawQuery)
            val sni = queryParams["sni"] ?: queryParams["peer"]
            val network = queryParams["type"] ?: "tcp"
            val host = queryParams["host"]
            val path = queryParams["path"]?.let { URLDecoder.decode(it, "UTF-8") }

            ProxyNode(
                id = UUID.randomUUID().toString(),
                name = name,
                type = ProxyType.TROJAN,
                server = server,
                port = port,
                password = password,
                network = network,
                tls = true,
                sni = sni,
                host = host,
                path = path
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseShadowsocksUri(uriStr: String): ProxyNode? {
        return try {
            val raw = uriStr.substringAfter("ss://").trim()
            val fragment = if (raw.contains("#")) raw.substringAfter("#") else null
            val name = if (!fragment.isNullOrBlank()) URLDecoder.decode(fragment, "UTF-8") else "SS Endpoint"
            val mainPart = raw.substringBefore("#")

            if (mainPart.contains("@")) {
                val authPart = mainPart.substringBefore("@")
                val serverPort = mainPart.substringAfter("@")
                val decodedAuth = if (isLikelyBase64(authPart)) decodeBase64(authPart) else authPart
                val cipher = decodedAuth.substringBefore(":")
                val password = decodedAuth.substringAfter(":")
                val server = serverPort.substringBefore(":")
                val port = serverPort.substringAfter(":").toIntOrNull() ?: 8388

                ProxyNode(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    type = ProxyType.SHADOWSOCKS,
                    server = server,
                    port = port,
                    cipher = cipher,
                    password = password
                )
            } else {
                val decoded = decodeBase64(mainPart)
                // format: cipher:password@server:port
                val cipher = decoded.substringBefore(":")
                val rest = decoded.substringAfter(":")
                val password = rest.substringBefore("@")
                val serverPort = rest.substringAfter("@")
                val server = serverPort.substringBefore(":")
                val port = serverPort.substringAfter(":").toIntOrNull() ?: 8388

                ProxyNode(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    type = ProxyType.SHADOWSOCKS,
                    server = server,
                    port = port,
                    cipher = cipher,
                    password = password
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSocksUri(uriStr: String): ProxyNode? {
        return try {
            val uri = URI(uriStr)
            val server = uri.host ?: return null
            val port = if (uri.port != -1) uri.port else 1080
            val name = if (!uri.fragment.isNullOrBlank()) URLDecoder.decode(uri.fragment, "UTF-8") else "SOCKS5 Endpoint"
            val user = uri.userInfo?.substringBefore(":")
            val pass = uri.userInfo?.substringAfter(":")

            ProxyNode(
                id = UUID.randomUUID().toString(),
                name = name,
                type = ProxyType.SOCKS5,
                server = server,
                port = port,
                uuid = user,
                password = pass
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseHttpUri(uriStr: String): ProxyNode? {
        return try {
            val uri = URI(uriStr)
            val server = uri.host ?: return null
            val port = if (uri.port != -1) uri.port else 8080
            val name = if (!uri.fragment.isNullOrBlank()) URLDecoder.decode(uri.fragment, "UTF-8") else "HTTP Endpoint"
            val user = uri.userInfo?.substringBefore(":")
            val pass = uri.userInfo?.substringAfter(":")

            ProxyNode(
                id = UUID.randomUUID().toString(),
                name = name,
                type = ProxyType.HTTP,
                server = server,
                port = port,
                uuid = user,
                password = pass
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseQueryParams(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = pair.substring(0, idx)
                val value = pair.substring(idx + 1)
                result[key] = value
            }
        }
        return result
    }

    private fun isLikelyBase64(s: String): Boolean {
        val clean = s.replace("\n", "").replace("\r", "").replace(" ", "").trim()
        if (clean.length < 16) return false
        val base64Pattern = Regex("""^[A-Za-z0-9+/=_-]+$""")
        return base64Pattern.matches(clean)
    }

    private fun decodeBase64(s: String): String {
        val clean = s.replace("\n", "").replace("\r", "").replace(" ", "").trim()
            .replace("-", "+").replace("_", "/")
        val padLen = (4 - (clean.length % 4)) % 4
        val padded = clean + "=".repeat(padLen)

        val bytes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Base64.getDecoder().decode(padded)
        } else {
            android.util.Base64.decode(padded, android.util.Base64.DEFAULT)
        }
        return String(bytes, Charsets.UTF_8)
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
