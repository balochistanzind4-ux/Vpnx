package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Native Shadowsocks Protocol Transport.
 */
class ShadowsocksTransport(private val node: ProxyNode) : ProxyTransport {

    override suspend fun openTunnel(
        targetHost: String,
        targetPort: Int,
        protectSocket: (Socket) -> Boolean,
        connectTimeoutMs: Int
    ): Socket = withContext(Dispatchers.IO) {
        val socket = Socket()
        protectSocket(socket)
        socket.tcpNoDelay = true
        socket.soTimeout = 30000
        socket.connect(InetSocketAddress(node.server, node.port), connectTimeoutMs)

        // Shadowsocks stream header: [Atyp: 0x01 (IPv4) or 0x03 (Domain)] [Address] [Port: 2B BigEndian]
        val out = socket.getOutputStream()
        val isIpv4 = targetHost.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))

        if (isIpv4) {
            val ipBytes = InetAddress.getByName(targetHost).address
            val header = ByteArray(1 + 4 + 2)
            header[0] = 0x01 // IPv4
            System.arraycopy(ipBytes, 0, header, 1, 4)
            header[5] = ((targetPort shr 8) and 0xFF).toByte()
            header[6] = (targetPort and 0xFF).toByte()
            out.write(header)
        } else {
            val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
            val header = ByteArray(1 + 1 + hostBytes.size + 2)
            header[0] = 0x03 // Domain
            header[1] = hostBytes.size.toByte()
            System.arraycopy(hostBytes, 0, header, 2, hostBytes.size)
            header[2 + hostBytes.size] = ((targetPort shr 8) and 0xFF).toByte()
            header[2 + hostBytes.size + 1] = (targetPort and 0xFF).toByte()
            out.write(header)
        }
        out.flush()

        socket.soTimeout = 0
        return@withContext socket
    }

    override suspend fun testConnection(
        node: ProxyNode,
        protectSocket: (Socket) -> Boolean,
        timeoutMs: Int
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val socket = openTunnel("1.1.1.1", 80, protectSocket, timeoutMs)
            socket.close()
            Pair(true, null)
        } catch (e: Exception) {
            Pair(false, e.localizedMessage ?: "Shadowsocks node unreachable")
        }
    }
}
