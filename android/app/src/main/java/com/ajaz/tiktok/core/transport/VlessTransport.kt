package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Standard VLESS Protocol Transport implementation.
 * Supports VLESS standard binary framing, TLS / SNI negotiation,
 * Reality / WebSocket stream encapsulation, and lazy server response header consumption.
 */
class VlessTransport(private val node: ProxyNode) : ProxyTransport {

    override suspend fun openTunnel(
        targetHost: String,
        targetPort: Int,
        protectSocket: (Socket) -> Boolean,
        connectTimeoutMs: Int
    ): Socket = withContext(Dispatchers.IO) {
        val rawSocket = Socket()
        val isProtected = protectSocket(rawSocket)
        if (!isProtected) {
            AppLogger.w("VlessTransport", "Warning: VpnService.protect() returned false for raw socket")
        }
        rawSocket.tcpNoDelay = true
        rawSocket.soTimeout = 30000
        rawSocket.connect(InetSocketAddress(node.server, node.port), connectTimeoutMs)

        var activeSocket: Socket = rawSocket

        // 1. TLS Upgrade if requested, port is 443, or Reality is configured
        val useTls = node.tls || node.port == 443 || !node.realityPublicKey.isNullOrBlank()
        val sniHost = (node.sni ?: node.host ?: node.server).trim()

        if (useTls) {
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
                AppLogger.w("VlessTransport", "Failed to set SNI '$sniHost': ${e.message}")
            }
            sslSocket.sslParameters = sslParams
            sslSocket.startHandshake()
            activeSocket = sslSocket
        }

        // 2. WebSocket Upgrade if network is "ws"
        if (node.network.equals("ws", ignoreCase = true)) {
            val hostHeader = node.host ?: sniHost
            val path = if (!node.path.isNullOrBlank()) node.path else "/"
            activeSocket = WebSocketStreamWrapper(activeSocket, hostHeader, path, node.wsHeaders)
        }

        // 3. Write VLESS Request Header immediately
        // [Version: 1B (0x00)] [UUID: 16B] [Addons Len: 1B (0x00)] [Command: 1B (0x01 = TCP)]
        // [Port: 2B BigEndian] [Address Type: 1B] [Address: 4B / (1B len + domain) / 16B]
        val header = buildVlessHeader(targetHost, targetPort, node.uuid ?: node.password ?: "")
        val out = activeSocket.getOutputStream()
        out.write(header)
        out.flush()

        // 4. Wrap the socket with VlessSocketWrapper to consume server response header upon the first incoming read
        val wrappedSocket = VlessSocketWrapper(activeSocket)
        activeSocket.soTimeout = 0
        return@withContext wrappedSocket
    }

    private fun buildVlessHeader(targetHost: String, targetPort: Int, rawUuid: String): ByteArray {
        val uuidBytes = parseUuidToBytes(rawUuid)
        val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
        val isIpv4 = targetHost.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))

        val addrType: Byte
        val addrBytes: ByteArray

        if (isIpv4) {
            addrType = 0x01 // IPv4
            addrBytes = InetAddress.getByName(targetHost).address
        } else {
            addrType = 0x02 // Domain Name
            addrBytes = ByteArray(1 + hostBytes.size)
            addrBytes[0] = hostBytes.size.toByte()
            System.arraycopy(hostBytes, 0, addrBytes, 1, hostBytes.size)
        }

        val headerSize = 1 + 16 + 1 + 1 + 2 + 1 + addrBytes.size
        val header = ByteArray(headerSize)
        var offset = 0

        header[offset++] = 0x00 // VLESS Version 0
        System.arraycopy(uuidBytes, 0, header, offset, 16)
        offset += 16
        header[offset++] = 0x00 // Addons length (0 = no addons)
        header[offset++] = 0x01 // Command: 0x01 = TCP CONNECT

        // Port (Big Endian)
        header[offset++] = ((targetPort shr 8) and 0xFF).toByte()
        header[offset++] = (targetPort and 0xFF).toByte()

        // Address Type & Address
        header[offset++] = addrType
        System.arraycopy(addrBytes, 0, header, offset, addrBytes.size)

        return header
    }

    private fun parseUuidToBytes(rawUuid: String): ByteArray {
        val clean = rawUuid.replace("-", "").trim()
        val result = ByteArray(16)
        if (clean.length == 32) {
            for (i in 0 until 16) {
                val hexByte = clean.substring(i * 2, i * 2 + 2)
                result[i] = hexByte.toIntOrNull(16)?.toByte() ?: 0
            }
        } else {
            try {
                val uuid = UUID.fromString(rawUuid)
                val msb = uuid.mostSignificantBits
                val lsb = uuid.leastSignificantBits
                for (i in 0..7) {
                    result[i] = ((msb shr ((7 - i) * 8)) and 0xFF).toByte()
                    result[8 + i] = ((lsb shr ((7 - i) * 8)) and 0xFF).toByte()
                }
            } catch (_: Exception) {
                // Fallback default
                val defaultUuid = UUID.randomUUID()
                val msb = defaultUuid.mostSignificantBits
                val lsb = defaultUuid.leastSignificantBits
                for (i in 0..7) {
                    result[i] = ((msb shr ((7 - i) * 8)) and 0xFF).toByte()
                    result[8 + i] = ((lsb shr ((7 - i) * 8)) and 0xFF).toByte()
                }
            }
        }
        return result
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
            Pair(false, e.localizedMessage ?: "VLESS node unreachable")
        }
    }
}

/**
 * Socket wrapper that lazily consumes and strips the VLESS server response header
 * (version 1B + addons len M + M bytes) on the first read of incoming data.
 */
class VlessSocketWrapper(private val delegate: Socket) : Socket() {

    private val vlessIn = VlessInputStream(delegate.getInputStream())

    override fun getInputStream(): InputStream = vlessIn
    override fun getOutputStream(): OutputStream = delegate.getOutputStream()

    override fun close() {
        delegate.close()
    }

    override fun isClosed(): Boolean = delegate.isClosed
    override fun isConnected(): Boolean = delegate.isConnected
    override fun setSoTimeout(timeout: Int) {
        delegate.soTimeout = timeout
    }
    override fun setTcpNoDelay(on: Boolean) {
        delegate.tcpNoDelay = on
    }
}

/**
 * InputStream that strips the VLESS server response header (version 1B + addons len M + M bytes)
 * upon the first read.
 */
class VlessInputStream(private val rawIn: InputStream) : InputStream() {

    private var headerConsumed = false

    private fun ensureHeaderConsumed() {
        if (!headerConsumed) {
            headerConsumed = true

            // Read version (1 byte)
            val version = rawIn.read()
            if (version < 0) throw EOFException("VLESS server closed connection during response header read")

            // Read addons length (1 byte)
            val addonsLen = rawIn.read()
            if (addonsLen < 0) throw EOFException("VLESS server closed connection reading addons length")

            // Read and discard addons if any
            if (addonsLen > 0) {
                val addons = ByteArray(addonsLen)
                var read = 0
                while (read < addonsLen) {
                    val r = rawIn.read(addons, read, addonsLen - read)
                    if (r < 0) throw EOFException("Unexpected EOF reading VLESS server addons")
                    read += r
                }
            }
        }
    }

    override fun read(): Int {
        ensureHeaderConsumed()
        return rawIn.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len <= 0) return 0
        ensureHeaderConsumed()
        return rawIn.read(b, off, len)
    }

    override fun close() {
        rawIn.close()
    }
}
