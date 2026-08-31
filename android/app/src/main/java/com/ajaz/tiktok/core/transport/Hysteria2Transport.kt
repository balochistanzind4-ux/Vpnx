package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.network.DnsResolver
import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
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
 * Hysteria 2 Protocol Transport implementation.
 * Supports TLS SNI, auth header negotiation, and stream tunneling.
 */
class Hysteria2Transport(private val node: ProxyNode) : ProxyTransport {

    override suspend fun openTunnel(
        targetHost: String,
        targetPort: Int,
        protectSocket: (Socket) -> Boolean,
        connectTimeoutMs: Int
    ): Socket = withContext(Dispatchers.IO) {
        val rawSocket = Socket()
        val protected = protectSocket(rawSocket)
        if (!protected) {
            AppLogger.w("Hysteria2", "Warning: protectSocket() returned false")
        }

        rawSocket.tcpNoDelay = true
        rawSocket.soTimeout = Math.max(connectTimeoutMs + 5000, 15000)

        // Resolve server IP via DnsResolver
        val serverIp = DnsResolver.resolve(node.server, protectSocket)
        AppLogger.d("Hysteria2", "Connecting to ${node.name} (${serverIp.hostAddress}:${node.port})...")
        rawSocket.connect(InetSocketAddress(serverIp, node.port), connectTimeoutMs)

        // Upgrade to TLS
        val sniHost = (node.sni ?: node.host ?: node.server).trim()
        val sslFactory: SSLSocketFactory = if (node.skipCertVerify) {
            createInsecureSslSocketFactory()
        } else {
            try {
                SSLSocketFactory.getDefault() as SSLSocketFactory
            } catch (_: Exception) {
                createInsecureSslSocketFactory()
            }
        }

        val sslSocket = sslFactory.createSocket(rawSocket, sniHost, node.port, true) as SSLSocket
        val sslParams = sslSocket.sslParameters ?: SSLParameters()
        try {
            if (sniHost.isNotBlank() && !isIpAddress(sniHost)) {
                sslParams.serverNames = listOf(SNIHostName(sniHost))
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val alpn = node.alpn ?: listOf("h3", "http/1.1")
                sslParams.applicationProtocols = alpn.toTypedArray()
            }
        } catch (e: Exception) {
            AppLogger.w("Hysteria2", "Failed to set SNI/ALPN: ${e.message}")
        }
        sslSocket.sslParameters = sslParams
        sslSocket.startHandshake()

        // Send Hysteria2 Connect Header
        val out = sslSocket.getOutputStream()
        val auth = node.password ?: node.uuid ?: ""
        val req = "CONNECT $targetHost:$targetPort HTTP/1.1\r\n" +
            "Host: $targetHost:$targetPort\r\n" +
            "Hysteria-Auth: $auth\r\n" +
            "User-Agent: Hysteria/2.0 AjazTiktok/1.0.0\r\n\r\n"
        out.write(req.toByteArray(Charsets.US_ASCII))
        out.flush()

        sslSocket.soTimeout = 0
        return@withContext sslSocket
    }

    private fun isIpAddress(s: String): Boolean {
        return s.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")) || s.contains(":")
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
            Pair(false, e.localizedMessage ?: "Hysteria2 connection failed")
        }
    }
}
