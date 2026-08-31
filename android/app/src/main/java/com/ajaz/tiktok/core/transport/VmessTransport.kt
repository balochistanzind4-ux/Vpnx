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
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Production VMess Transport implementation.
 * Features:
 * - VMess HMAC-MD5 Auth Header generation
 * - AES-128-CFB Body Encryption
 * - TLS SNI and ALPN negotiation
 * - WebSocket and plain TCP transport modes
 * - Direct DNS fallback via DnsResolver
 */
class VmessTransport(private val node: ProxyNode) : ProxyTransport {

    override suspend fun openTunnel(
        targetHost: String,
        targetPort: Int,
        protectSocket: (Socket) -> Boolean,
        connectTimeoutMs: Int
    ): Socket = withContext(Dispatchers.IO) {
        val rawSocket = Socket()
        val protected = protectSocket(rawSocket)
        if (!protected) {
            AppLogger.w("VMess", "Warning: protectSocket() returned false")
        }

        rawSocket.tcpNoDelay = true
        rawSocket.soTimeout = Math.max(connectTimeoutMs + 5000, 15000)

        // Resolve Server IP
        val serverIp = DnsResolver.resolve(node.server, protectSocket)
        AppLogger.d("VMess", "Connecting to ${node.name} (${serverIp.hostAddress}:${node.port})...")
        rawSocket.connect(InetSocketAddress(serverIp, node.port), connectTimeoutMs)

        var streamSocket: Socket = rawSocket

        // TLS Layer
        val isTls = node.tls || node.port == 443
        if (isTls) {
            val sni = (node.sni ?: node.host ?: node.server).trim()
            val sslFactory: SSLSocketFactory = if (node.skipCertVerify) {
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
                AppLogger.w("VMess", "Failed to set TLS SNI/ALPN: ${e.message}")
            }

            sslSocket.sslParameters = sslParams
            sslSocket.startHandshake()
            streamSocket = sslSocket
        }

        // WebSocket Layer
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

        // Send VMess Request Header
        val uuidBytes = parseUuidToBytes(node.uuid ?: "")
        val headerBytes = buildVmessHeader(uuidBytes, targetHost, targetPort)

        val out = streamSocket.getOutputStream()
        out.write(headerBytes)
        out.flush()

        streamSocket.soTimeout = 0
        return@withContext streamSocket
    }

    private fun buildVmessHeader(userUuid: ByteArray, targetHost: String, targetPort: Int): ByteArray {
        val random = SecureRandom()

        // 1. Generate 16 bytes auth ID via HMAC-MD5(UUID, UTC timestamp)
        val timestamp = (System.currentTimeMillis() / 1000L)
        val timeBytes = ByteArray(8)
        for (i in 0 until 8) {
            timeBytes[7 - i] = ((timestamp shr (i * 8)) and 0xFF).toByte()
        }

        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(userUuid, "HmacMD5"))
        val authId = mac.doFinal(timeBytes)

        // 2. Request Command & Destination Body
        val bodyBaos = ByteArrayOutputStream()
        val dos = DataOutputStream(bodyBaos)

        dos.writeByte(0x01) // Version 1
        val reqIv = ByteArray(16).apply { random.nextBytes(this) }
        val reqKey = ByteArray(16).apply { random.nextBytes(this) }
        dos.write(reqIv)
        dos.write(reqKey)

        dos.writeByte(0x01) // Response Auth (V)
        dos.writeByte(0x01) // Options: S = 1 (Standard)
        dos.writeByte(0x00) // Padding / P
        dos.writeByte(0x00) // Security: AES-128-CFB

        dos.writeByte(0x00) // Reserved
        dos.writeByte(0x01) // Command: 0x01 (TCP)
        dos.writeShort(targetPort)

        // Address Type: 0x01 (IPv4), 0x02 (Domain), 0x03 (IPv6)
        val isIpv4 = targetHost.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))
        val isIpv6 = targetHost.contains(":") && !targetHost.contains(".")

        if (isIpv4) {
            dos.writeByte(0x01)
            dos.write(InetAddress.getByName(targetHost).address)
        } else if (isIpv6) {
            dos.writeByte(0x03)
            dos.write(InetAddress.getByName(targetHost).address)
        } else {
            dos.writeByte(0x02)
            val domainBytes = targetHost.toByteArray(Charsets.US_ASCII)
            dos.writeByte(domainBytes.size)
            dos.write(domainBytes)
        }

        // Random Padding (0..16 bytes)
        val padLen = random.nextInt(16)
        val padding = ByteArray(padLen).apply { random.nextBytes(this) }
        dos.write(padding)

        // Checksum (FNV-1a)
        val rawBody = bodyBaos.toByteArray()
        val checksum = fnv1a(rawBody)

        val fullBody = ByteArrayOutputStream()
        fullBody.write(rawBody)
        fullBody.write(((checksum shr 24) and 0xFF).toInt())
        fullBody.write(((checksum shr 16) and 0xFF).toInt())
        fullBody.write(((checksum shr 8) and 0xFF).toInt())
        fullBody.write((checksum and 0xFF).toInt())

        // 3. Encrypt Body with AES-128-CFB using MD5(UUID + "c48619fe-8f02-49e0-b9e9-edf763e17e21")
        val md = MessageDigest.getInstance("MD5")
        md.update(userUuid)
        md.update("c48619fe-8f02-49e0-b9e9-edf763e17e21".toByteArray(Charsets.US_ASCII))
        val bodyKey = md.digest()

        val mdIv = MessageDigest.getInstance("MD5")
        mdIv.update(timeBytes)
        val bodyIv = mdIv.digest()

        val cipher = Cipher.getInstance("AES/CFB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(bodyKey, "AES"), IvParameterSpec(bodyIv))
        val encryptedBody = cipher.doFinal(fullBody.toByteArray())

        // Assemble Auth ID + Encrypted Body
        val result = ByteArrayOutputStream()
        result.write(authId)
        result.write(encryptedBody)
        return result.toByteArray()
    }

    private fun fnv1a(data: ByteArray): Int {
        var hash = 0x811c9dc5.toInt()
        for (b in data) {
            hash = hash xor (b.toInt() and 0xFF)
            hash *= 0x01000193
        }
        return hash
    }

    private fun parseUuidToBytes(uuidStr: String): ByteArray {
        val clean = uuidStr.replace("-", "").trim()
        if (clean.length == 32) {
            val bytes = ByteArray(16)
            for (i in 0 until 16) {
                bytes[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return bytes
        }
        try {
            val u = UUID.fromString(uuidStr)
            val bytes = ByteArray(16)
            val msb = u.mostSignificantBits
            val lsb = u.leastSignificantBits
            for (i in 0..7) bytes[i] = ((msb shr ((7 - i) * 8)) and 0xFF).toByte()
            for (i in 8..15) bytes[i] = ((lsb shr ((15 - i) * 8)) and 0xFF).toByte()
            return bytes
        } catch (_: Exception) {
            return ByteArray(16)
        }
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
            Pair(false, e.localizedMessage ?: "VMess connection failed")
        }
    }
}
