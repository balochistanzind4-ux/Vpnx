package com.ajaz.tiktok.core.parser

enum class ProxyType(val displayName: String) {
    SHADOWSOCKS("SS"),
    VMESS("VMess"),
    TROJAN("Trojan"),
    VLESS("VLess"),
    HYSTERIA2("Hysteria2"),
    WIREGUARD("WireGuard"),
    SOCKS5("Socks5"),
    HTTP("HTTP"),
    DIRECT("Direct"),
    REJECT("Reject"),
    UNKNOWN("Custom");

    companion object {
        fun fromString(type: String?): ProxyType {
            return when (type?.lowercase()?.trim()) {
                "ss", "shadowsocks" -> SHADOWSOCKS
                "vmess" -> VMESS
                "trojan" -> TROJAN
                "vless" -> VLESS
                "hysteria2", "hy2" -> HYSTERIA2
                "wireguard", "wg" -> WIREGUARD
                "socks5", "socks" -> SOCKS5
                "http", "https" -> HTTP
                "direct" -> DIRECT
                "reject" -> REJECT
                else -> UNKNOWN
            }
        }
    }
}

data class ProxyNode(
    val id: String,
    val name: String,
    val type: ProxyType,
    val server: String,
    val port: Int,
    val cipher: String? = null,
    val password: String? = null, // Redacted in UI & logs
    val uuid: String? = null,     // Redacted in UI & logs
    val alterId: Int = 0,
    val network: String? = null,  // ws, tcp, grpc, h2
    val tls: Boolean = false,
    val sni: String? = null,
    val host: String? = null,
    val path: String? = null,
    val wsHeaders: Map<String, String> = emptyMap(),
    val alpn: List<String>? = null,
    val realityPublicKey: String? = null,
    val realityShortId: String? = null,
    val skipCertVerify: Boolean = false,
    val udp: Boolean = true,
    val latencyMs: Long = -1,
    val isOnline: Boolean = true
) {
    fun getMaskedServerAddress(): String {
        return if (server.length > 8) {
            "${server.take(4)}***${server.takeLast(4)}:$port"
        } else {
            "$server:$port"
        }
    }
}

data class NetworkProfile(
    val id: String,
    val name: String,
    val sourceUrl: String? = null,
    val rawConfig: String,
    val format: String = "yaml",
    val proxyCount: Int = 0,
    val proxies: List<ProxyNode> = emptyList(),
    val proxyGroups: List<String> = emptyList(),
    val selectedProxyId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isValid: Boolean = true,
    val validationMessage: String? = null,
    val subscriptionUserInfo: String? = null
)
