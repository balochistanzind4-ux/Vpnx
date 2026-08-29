package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import java.security.MessageDigest

class TrojanTransport(private val node: ProxyNode) : ProxyTransport {

    private fun getPasswordHashHex(password: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-224")
        val digest = md.digest(password.toByteArray(Charsets.UTF_8))
        val hexChars = "0123456789abcdef".toCharArray()
        val result = ByteArray(56)
        for (i in digest.indices) {
            val v = digest[i].toInt() and 0xFF
            result[i * 2] = hexChars[v ushr 4].code.toByte()
            result[i * 2 + 1] = hexChars[v and 0x0F].code.toByte()
        }
        return result
    }

    override suspend fun openTunnel(
        targetHost: String,
        targetPort: Int,
        protectSocket: (Socket) -> Boolean,
        connectTimeoutMs: Int
    ): Socket = withContext(Dispatchers.IO) {
        val rawSocket = Socket()
        protectSocket(rawSocket)
        rawSocket.tcpNoDelay = true
        rawSocket.connect(InetSocketAddress(node.server, node.port), connectTimeoutMs)

        // Upgrade to TLS
        val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val sniHost = node.sni ?: node.server
        val sslSocket = sslFactory.createSocket(rawSocket, sniHost, node.port, true) as SSLSocket

        val sslParams = sslSocket.sslParameters ?: SSLParameters()
        sslParams.serverNames = listOf(SNIHostName(sniHost))
        sslSocket.sslParameters = sslParams
        sslSocket.startHandshake()

        val out = sslSocket.getOutputStream()
        val password = node.password ?: ""
        val hashHex = getPasswordHashHex(password)

        // Trojan Request Header:
        // [56 bytes hex(SHA224(password))] + [CRLF: 0x0D, 0x0A] + [Command: 0x01 (CONNECT)] + [Atyp: 0x03 (Domain)] + [Domain Len] + [Domain] + [Port: 2 bytes] + [CRLF]
        out.write(hashHex)
        out.write(byteArrayOf(0x0D, 0x0A, 0x01, 0x03)) // Command 1 = TCP CONNECT, Atyp 3 = Domain

        val targetBytes = targetHost.toByteArray(Charsets.UTF_8)
        out.write(targetBytes.size)
        out.write(targetBytes)
        out.write((targetPort shr 8) and 0xFF)
        out.write(targetPort and 0xFF)
        out.write(byteArrayOf(0x0D, 0x0A))
        out.flush()

        return@withContext sslSocket
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
            Pair(false, e.localizedMessage ?: "Trojan connection failed")
        }
    }
}

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
        socket.connect(InetSocketAddress(node.server, node.port), connectTimeoutMs)

        // Shadowsocks stream header: [Atyp: 0x03 (domain)] [len] [domain] [port: 2 bytes]
        val out = socket.getOutputStream()
        val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
        val header = ByteArray(1 + 1 + hostBytes.size + 2)
        header[0] = 0x03 // Domain
        header[1] = hostBytes.size.toByte()
        System.arraycopy(hostBytes, 0, header, 2, hostBytes.size)
        header[2 + hostBytes.size] = ((targetPort shr 8) and 0xFF).toByte()
        header[2 + hostBytes.size + 1] = (targetPort and 0xFF).toByte()

        out.write(header)
        out.flush()

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
