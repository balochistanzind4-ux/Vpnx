package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

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
        rawSocket.soTimeout = 30000
        rawSocket.connect(InetSocketAddress(node.server, node.port), connectTimeoutMs)

        // Upgrade to TLS
        val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val sniHost = node.sni ?: node.server
        val sslSocket = sslFactory.createSocket(rawSocket, sniHost, node.port, true) as SSLSocket

        val sslParams = sslSocket.sslParameters ?: SSLParameters()
        sslParams.serverNames = listOf(SNIHostName(sniHost))
        sslSocket.sslParameters = sslParams
        sslSocket.startHandshake()

        var activeSocket: Socket = sslSocket

        // Optional WebSocket upgrade
        if (node.network.equals("ws", ignoreCase = true)) {
            val out = activeSocket.getOutputStream()
            val inStream = activeSocket.getInputStream()
            val hostHeader = node.host ?: node.sni ?: node.server
            val path = if (!node.path.isNullOrBlank()) node.path else "/"

            val wsKeyBytes = ByteArray(16)
            java.security.SecureRandom().nextBytes(wsKeyBytes)
            val secKey = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Base64.getEncoder().encodeToString(wsKeyBytes)
            } else {
                android.util.Base64.encodeToString(wsKeyBytes, android.util.Base64.NO_WRAP)
            }

            val wsRequest = "GET $path HTTP/1.1\r\n" +
                "Host: $hostHeader\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $secKey\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "User-Agent: AjazTiktok/1.0 (Trojan-WS)\r\n\r\n"

            out.write(wsRequest.toByteArray(Charsets.US_ASCII))
            out.flush()

            val reader = BufferedReader(InputStreamReader(inStream, Charsets.US_ASCII))
            val statusLine = reader.readLine() ?: throw IOException("Empty response during Trojan WebSocket upgrade")
            if (!statusLine.contains("101")) {
                throw IOException("Trojan WebSocket upgrade failed: $statusLine")
            }

            var line: String?
            while (true) {
                line = reader.readLine()
                if (line.isNullOrEmpty()) break
            }
        }

        val out = activeSocket.getOutputStream()
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

        activeSocket.soTimeout = 0
        return@withContext activeSocket
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

