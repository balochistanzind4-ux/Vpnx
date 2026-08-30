package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import java.util.UUID
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Native VLESS Protocol Transport implementation.
 * Supports VLESS standard header framing, TLS / SNI encapsulation,
 * and optional WebSocket transport stream wrapping.
 */
class VlessTransport(private val node: ProxyNode) : ProxyTransport {

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

        var activeSocket: Socket = rawSocket

        // 1. TLS Upgrade if requested or port is 443
        val useTls = node.tls || node.port == 443
        if (useTls) {
            val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val sniHost = node.sni ?: node.host ?: node.server
            val sslSocket = sslFactory.createSocket(rawSocket, sniHost, node.port, true) as SSLSocket

            val sslParams = sslSocket.sslParameters ?: SSLParameters()
            sslParams.serverNames = listOf(SNIHostName(sniHost))
            sslSocket.sslParameters = sslParams
            sslSocket.startHandshake()
            activeSocket = sslSocket
        }

        // 2. WebSocket Upgrade if network is "ws"
        if (node.network.equals("ws", ignoreCase = true)) {
            performWebSocketHandshake(activeSocket, node)
        }

        val out = activeSocket.getOutputStream()
        val inStream = activeSocket.getInputStream()

        // 3. Build VLESS Request Header
        // [Version: 1B (0x00)]
        // [UUID: 16B]
        // [Addons Len: 1B (0x00)]
        // [Command: 1B (0x01 = TCP)]
        // [Port: 2B BigEndian]
        // [Address Type: 1B (0x01=IPv4, 0x02=Domain, 0x03=IPv6)]
        // [Address: 4B / (1B len + domain) / 16B]
        val header = buildVlessHeader(targetHost, targetPort, node.uuid ?: node.password ?: "")
        out.write(header)
        out.flush()

        // 4. Wrap the socket to consume and strip VLESS Server Response Header (Version 1B + Addons Len M + M bytes)
        val wrappedSocket = VlessSocketWrapper(activeSocket)
        wrappedSocket.consumeServerResponseHeader()

        activeSocket.soTimeout = 0
        return@withContext wrappedSocket
    }

    private fun performWebSocketHandshake(socket: Socket, node: ProxyNode) {
        val out = socket.getOutputStream()
        val inStream = socket.getInputStream()
        val hostHeader = node.host ?: node.sni ?: node.server
        val path = if (!node.path.isNullOrBlank()) node.path else "/"

        val wsKeyBytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(wsKeyBytes)
        val secKey = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Base64.getEncoder().encodeToString(wsKeyBytes)
        } else {
            android.util.Base64.encodeToString(wsKeyBytes, android.util.Base64.NO_WRAP)
        }

        val wsRequest = StringBuilder().apply {
            append("GET $path HTTP/1.1\r\n")
            append("Host: $hostHeader\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Key: $secKey\r\n")
            append("Sec-WebSocket-Version: 13\r\n")
            append("User-Agent: AjazTiktok/1.0 (VLESS-WS)\r\n")
            append("\r\n")
        }.toString()

        out.write(wsRequest.toByteArray(Charsets.US_ASCII))
        out.flush()

        val reader = BufferedReader(InputStreamReader(inStream, Charsets.US_ASCII))
        val statusLine = reader.readLine() ?: throw IOException("Empty response during WebSocket upgrade")
        if (!statusLine.contains("101")) {
            throw IOException("WebSocket upgrade failed with status: $statusLine")
        }

        // Read until empty line
        var line: String?
        while (true) {
            line = reader.readLine()
            if (line.isNullOrEmpty()) break
        }
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
 * Socket wrapper that strips the VLESS server response header (version 1B + addons len M + M bytes)
 * before passing the raw data stream back to the caller.
 */
class VlessSocketWrapper(private val delegate: Socket) : Socket() {

    private var wrappedInputStream: InputStream? = null

    fun consumeServerResponseHeader() {
        val rawIn = delegate.getInputStream()
        // Read version (1 byte)
        val version = rawIn.read()
        if (version < 0) throw IOException("VLESS server closed connection unexpectedly")

        // Read addons length (1 byte)
        val addonsLen = rawIn.read()
        if (addonsLen < 0) throw IOException("VLESS server closed connection during header read")

        // Read and discard addons if any
        if (addonsLen > 0) {
            val addons = ByteArray(addonsLen)
            var read = 0
            while (read < addonsLen) {
                val r = rawIn.read(addons, read, addonsLen - read)
                if (r < 0) throw IOException("Unexpected EOF while reading VLESS server addons")
                read += r
            }
        }

        wrappedInputStream = rawIn
    }

    override fun getInputStream(): InputStream {
        return wrappedInputStream ?: delegate.getInputStream()
    }

    override fun getOutputStream(): OutputStream {
        return delegate.getOutputStream()
    }

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
