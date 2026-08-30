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
            ProxyType.DIRECT -> DirectTransport()
            ProxyType.VMESS, ProxyType.HYSTERIA2, ProxyType.WIREGUARD -> {
                throw UnsupportedOperationException("Protocol '${node.type.displayName}' is not supported natively in this build. Supported protocols: VLESS, Trojan, Shadowsocks, SOCKS5, HTTP, Direct.")
            }
            ProxyType.REJECT -> {
                throw IllegalArgumentException("Connection rejected by configuration: '${node.name}'")
            }
            ProxyType.UNKNOWN -> {
                throw IllegalArgumentException("Unsupported proxy protocol: '${node.type.displayName}'. Please select a supported node (VLESS, Trojan, Shadowsocks, SOCKS5, HTTP).")
            }
        }
    }
}
