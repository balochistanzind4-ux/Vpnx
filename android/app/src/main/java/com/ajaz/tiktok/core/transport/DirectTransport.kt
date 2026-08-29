package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class DirectTransport : ProxyTransport {

    override suspend fun openTunnel(
        targetHost: String,
        targetPort: Int,
        protectSocket: (Socket) -> Boolean,
        connectTimeoutMs: Int
    ): Socket = withContext(Dispatchers.IO) {
        val socket = Socket()
        protectSocket(socket)
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(targetHost, targetPort), connectTimeoutMs)
        return@withContext socket
    }

    override suspend fun testConnection(
        node: ProxyNode,
        protectSocket: (Socket) -> Boolean,
        timeoutMs: Int
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            protectSocket(socket)
            socket.connect(InetSocketAddress(node.server, node.port), timeoutMs)
            socket.close()
            Pair(true, null)
        } catch (e: Exception) {
            Pair(false, e.localizedMessage ?: "Direct endpoint unreachable")
        }
    }
}
