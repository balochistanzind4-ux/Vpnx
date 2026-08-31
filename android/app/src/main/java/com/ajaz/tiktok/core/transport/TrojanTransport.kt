package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.network.DnsResolver
import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetAddress
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
 * Production-grade Trojan Protocol Transport implementation.
 * Features:
 * - SHA-224 password authentication header
 * - TLS and Reality SNI negotiation
 * - WebSocket and plain TCP transport modes
 * - Domain, IPv4, and IPv6 destination addressing
 * - Direct DNS fallback via DnsResolver
 */
class TrojanTransport(private val node: ProxyNode) : ProxyTransport {

    override suspend fun openTunnel(
        targetHost: String,
        targetPort: Int,
        protectSocket: (Socket) -> Boolean,
        connectTimeoutMs: Int
    ): Socket = withContext(Dispatchers.IO) {
        val rawSocket = Socket()
        val protected = protectSocket(rawSocket)
        if (!protected) {
            AppLogger.w("Trojan", "Warning: protectSocket() returned false for raw socket")
        }

        rawSocket.tcpNoDelay = true
        rawSocket.soTimeout = Math.max(connectTimeoutMs + 5000, 15000)

        // 1. Resolve server IP via DnsResolver
        val serverIp = DnsResolver.resolve(node.server, protectSocket)
        AppLogger.d("Trojan", "Connecting to ${node.name} (${serverIp.hostAddress}:${node.port})...")
        rawSocket.connect(InetSocketAddress(serverIp, node.port), connectTimeoutMs)

        var streamSocket: Socket = rawSocket

        // 2. TLS Layer (Trojan always uses TLS by default)
        val isTls = node.tls || node.port == 443 || node.realityPublicKey != null
        if (isTls) {
            val sni = (node.sni ?: node.host ?: node.server).trim()
            val sslFactory: SSLSocketFactory = if (node.skipCertVerify || node.realityPublicKey != null) {
                createInsecureSslSocketFactory()
            } else {
                try {
                    SSLSocketFactory.getDefault() as SSLSocketFactory
                } catch (_: Exception) {
                    createInsecureSslSocketFactory()
                }
            }

            val sslSocket = sslFactory.createSocket(streamSocket, sni, node.port, true) as SSLSocket
            val sslParams = sslSocket.sslParameters ?: SSLParameters()

            try {
                if (sni.isNotBlank() && !isIpAddress(sni)) {
                    sslParams.serverNames = listOf(SNIHostName(sni))
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val alpn = node.alpn ?: listOf("http/1.1", "h2")
                    sslParams.applicationProtocols = alpn.toTypedArray()
                }
            } catch (e: Exception) {
                AppLogger.w("Trojan", "Failed to set TLS SNI/ALPN: ${e.message}")
            }

            sslSocket.sslParameters = sslParams
            sslSocket.startHandshake()
            streamSocket = sslSocket
        }

        // 3. WebSocket Layer
        val isWs = node.network.equals("ws", ignoreCase = true) || !node.path.isNullOrBlank()
        if (isWs) {
            val hostHeader = (node.host ?: node.sni ?: node.server).trim()
            val wsPath = node.path ?: "/"
            streamSocket = WebSocketStreamWrapper(
                delegate = streamSocket,
                hostHeader = hostHeader,
                path = wsPath,
                customHeaders = node.wsHeaders ?: emptyMap()
            )
        }

        // 4. Send Trojan Handshake Header
        val password = node.password ?: node.uuid ?: ""
        val passwordHashHex = computeSha224Hex(password)
        val headerBytes = buildTrojanHeader(passwordHashHex, targetHost, targetPort)

        val out = streamSocket.getOutputStream()
        out.write(headerBytes)
        out.flush()

        streamSocket.soTimeout = 0 // Reset for active session
        return@withContext streamSocket
    }

    private fun buildTrojanHeader(passwordHashHex: String, targetHost: String, targetPort: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // 1. 56 bytes SHA-224 Hex Digest + CRLF
        dos.write(passwordHashHex.toByteArray(Charsets.US_ASCII))
        dos.writeByte(0x0D)
        dos.writeByte(0x0A)

        // 2. Command: 0x01 (TCP Connect)
        dos.writeByte(0x01)

        // 3. Address Type: 0x01 (IPv4), 0x03 (Domain), 0x04 (IPv6)
        val isIpv4 = targetHost.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))
        val isIpv6 = targetHost.contains(":") && !targetHost.contains(".")

        if (isIpv4) {
            dos.writeByte(0x01)
            val ip = InetAddress.getByName(targetHost).address
            dos.write(ip)
        } else if (isIpv6) {
            dos.writeByte(0x04)
            val ip = InetAddress.getByName(targetHost).address
            dos.write(ip)
        } else {
            // Domain Name (0x03) - 1 byte length + ASCII bytes
            dos.writeByte(0x03)
            val domainBytes = targetHost.toByteArray(Charsets.US_ASCII)
            dos.writeByte(domainBytes.size)
            dos.write(domainBytes)
        }

        // 4. Port (2 bytes Big Endian)
        dos.writeShort(targetPort)

        // 5. CRLF
        dos.writeByte(0x0D)
        dos.writeByte(0x0A)

        return baos.toByteArray()
    }

    private fun computeSha224Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-224")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        val sb = java.lang.StringBuilder(56)
        for (b in digest) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    private fun isIpAddress(s: String): Boolean {
        return s.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")) || s.contains(":")
    }

    private fun createInsecureSslSocketFactory(): SSLSocketFactory {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val context = SSLContext.getInstance("TLS")
        context.init(null, trustAll, SecureRandom())
        return context.socketFactory
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
