package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Standard Trojan Protocol Transport implementation.
 * Supports Trojan authentication, TLS / SNI negotiation,
 * and RFC 6455 WebSocket stream encapsulation.
 */
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
        val isProtected = protectSocket(rawSocket)
        if (!isProtected) {
            AppLogger.w("TrojanTransport", "Warning: VpnService.protect() returned false for raw socket")
        }
        rawSocket.tcpNoDelay = true
        rawSocket.soTimeout = 30000
        rawSocket.connect(InetSocketAddress(node.server, node.port), connectTimeoutMs)

        var activeSocket: Socket = rawSocket

        // 1. Upgrade to TLS
        val sniHost = (node.sni ?: node.host ?: node.server).trim()
        val sslFactory: SSLSocketFactory = if (node.skipCertVerify) {
            createInsecureSslSocketFactory()
        } else {
            SSLSocketFactory.getDefault() as SSLSocketFactory
        }

        val sslSocket = sslFactory.createSocket(rawSocket, sniHost, node.port, true) as SSLSocket
        val sslParams = sslSocket.sslParameters ?: SSLParameters()
        try {
            sslParams.serverNames = listOf(SNIHostName(sniHost))
            if (!node.alpn.isNullOrEmpty()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    sslParams.applicationProtocols = node.alpn.toTypedArray()
                }
            }
        } catch (e: Exception) {
            AppLogger.w("TrojanTransport", "Failed to set SNI '$sniHost': ${e.message}")
        }
        sslSocket.sslParameters = sslParams
        sslSocket.startHandshake()
        activeSocket = sslSocket

        // 2. WebSocket Upgrade if network is "ws"
        if (node.network.equals("ws", ignoreCase = true)) {
            val hostHeader = node.host ?: sniHost
            val path = if (!node.path.isNullOrBlank()) node.path else "/"
            activeSocket = WebSocketStreamWrapper(activeSocket, hostHeader, path, node.wsHeaders)
        }

        // 3. Write Trojan Request Header:
        // [56 bytes hex(SHA224(password))] + [CRLF: 0x0D, 0x0A] + [Command: 0x01 (CONNECT)] + [Atyp: 0x03 (Domain)] + [Domain Len] + [Domain] + [Port: 2 bytes] + [CRLF]
        val out = activeSocket.getOutputStream()
        val password = node.password ?: node.uuid ?: ""
        val hashHex = getPasswordHashHex(password)

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

    private fun createInsecureSslSocketFactory(): SSLSocketFactory {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        return sslContext.socketFactory
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
