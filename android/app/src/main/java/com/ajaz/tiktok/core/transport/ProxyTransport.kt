package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.parser.ProxyNode
import java.net.Socket

interface ProxyTransport {
    /**
     * Establishes a connection to the target remote destination via the configured proxy node.
     * The socket is protected using VpnService.protect() to prevent routing loops.
     */
    suspend fun openTunnel(
        targetHost: String,
        targetPort: Int,
        protectSocket: (Socket) -> Boolean,
        connectTimeoutMs: Int = 10000
    ): Socket

    /**
     * Performs a lightweight handshake test with the proxy server to verify reachability and validity.
     * Returns Pair(isSuccess, errorMessageOrDetails).
     */
    suspend fun testConnection(
        node: ProxyNode,
        protectSocket: (Socket) -> Boolean,
        timeoutMs: Int = 6000
    ): Pair<Boolean, String?>
}
