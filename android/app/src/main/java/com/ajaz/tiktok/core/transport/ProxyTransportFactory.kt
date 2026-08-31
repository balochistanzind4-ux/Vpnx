package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.parser.ProxyNode
import com.ajaz.tiktok.core.parser.ProxyType

object ProxyTransportFactory {

    fun create(node: ProxyNode): ProxyTransport {
        return when (node.type) {
            ProxyType.SOCKS5 -> Socks5Transport(node)
            ProxyType.HTTP -> HttpConnectTransport(node)
            ProxyType.SHADOWSOCKS -> ShadowsocksTransport(node)
            ProxyType.TROJAN -> TrojanTransport(node)
            ProxyType.VLESS -> VlessTransport(node)
            ProxyType.VMESS -> VmessTransport(node)
            ProxyType.HYSTERIA2 -> Hysteria2Transport(node)
            ProxyType.WIREGUARD -> WireguardTransport(node)
            ProxyType.DIRECT -> DirectTransport()
            ProxyType.REJECT -> {
                throw IllegalArgumentException("Connection rejected by configuration rule: '${node.name}'")
            }
            ProxyType.UNKNOWN -> {
                // Fallback to SOCKS5/HTTP or VLESS based on port/fields
                if (!node.uuid.isNullOrBlank() || node.tls || node.port == 443) {
                    VlessTransport(node)
                } else {
                    Socks5Transport(node)
                }
            }
        }
    }
}
