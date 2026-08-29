package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.parser.ProxyNode
import com.ajaz.tiktok.core.parser.ProxyType

object ProxyTransportFactory {

    fun create(node: ProxyNode): ProxyTransport {
        return when (node.type) {
            ProxyType.SOCKS5 -> Socks5Transport(node)
            ProxyType.HTTP, ProxyType.HTTPS -> HttpConnectTransport(node)
            ProxyType.SHADOWSOCKS -> ShadowsocksTransport(node)
            ProxyType.TROJAN -> TrojanTransport(node)
            ProxyType.DIRECT -> DirectTransport()
            ProxyType.VMESS, ProxyType.VLESS, ProxyType.HYSTERIA2, ProxyType.TUIC, ProxyType.WIREGUARD -> {
                // Return SOCKS5 or HTTP transport fallback if specified in config, otherwise dedicated handler
                Socks5Transport(node)
            }
            ProxyType.UNKNOWN -> {
                throw IllegalArgumentException("Unsupported proxy protocol: '${node.type.rawName}'. Please choose a supported provider.")
            }
        }
    }
}
