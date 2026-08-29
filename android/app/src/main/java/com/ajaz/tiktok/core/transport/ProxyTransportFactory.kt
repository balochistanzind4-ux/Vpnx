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
            ProxyType.DIRECT -> DirectTransport()

            ProxyType.VMESS,
            ProxyType.VLESS,
            ProxyType.HYSTERIA2,
            ProxyType.WIREGUARD -> {
                Socks5Transport(node)
            }

            ProxyType.REJECT -> {
                throw IllegalArgumentException(
                    "Reject proxy type cannot be used as a transport."
                )
            }

            ProxyType.UNKNOWN -> {
                throw IllegalArgumentException(
                    "Unsupported proxy protocol: '${node.type.displayName}'."
                )
            }
        }
    }
}
