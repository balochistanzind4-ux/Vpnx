package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.network.DnsResolver
import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64

class HttpConnectTransport(private val node: ProxyNode) : ProxyTransport {

    override suspend fun openTunnel(
        targetHost: String,
        targetPort: Int,
        protectSocket: (Socket) -> Boolean,
        connectTimeoutMs: Int
    ): Socket = withContext(Dispatchers.IO) {
        val socket = Socket()
        val protected = protectSocket(socket)
        if (!protected) {
            AppLogger.w("HttpProxy", "Warning: protectSocket() returned false")
        }

        socket.tcpNoDelay = true
        socket.soTimeout = Math.max(connectTimeoutMs + 5000, 15000)

        // Resolve Server IP via DnsResolver
        val serverIp = DnsResolver.resolve(node.server, protectSocket)
        AppLogger.d("HttpProxy", "Connecting to ${node.name} (${serverIp.hostAddress}:${node.port})...")
        socket.connect(InetSocketAddress(serverIp, node.port), connectTimeoutMs)

        val out = socket.getOutputStream()
        val `in` = socket.getInputStream()

        val reqBuilder = java.lang.StringBuilder()
        reqBuilder.append("CONNECT $targetHost:$targetPort HTTP/1.1\r\n")
        reqBuilder.append("Host: $targetHost:$targetPort\r\n")
        reqBuilder.append("User-Agent: ClashMeta/1.18.0 AjazTiktok/1.0.0\r\n")
        reqBuilder.append("Proxy-Connection: Keep-Alive\r\n")

        if (!node.password.isNullOrBlank()) {
            val user = node.uuid ?: "user"
            val auth = "$user:${node.password}"
            val encoded = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Base64.getEncoder().encodeToString(auth.toByteArray(Charsets.UTF_8))
            } else {
                android.util.Base64.encodeToString(auth.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP).trim()
            }
            reqBuilder.append("Proxy-Authorization: Basic $encoded\r\n")
        }
        reqBuilder.append("\r\n")

        out.write(reqBuilder.toString().toByteArray(Charsets.US_ASCII))
        out.flush()

        // Read HTTP status line byte-by-byte without BufferedReader to prevent stream truncation
        val headerBytes = readHttpHeaderUntilDoubleCrlf(`in`)
        val headerText = String(headerBytes, Charsets.US_ASCII)

        if (!headerText.contains("200")) {
            socket.close()
            throw IOException("HTTP Proxy CONNECT failed: ${headerText.lines().firstOrNull() ?: "Unknown"}")
        }

        socket.soTimeout = 0
        return@withContext socket
    }

    private fun readHttpHeaderUntilDoubleCrlf(input: InputStream): ByteArray {
        val baos = ByteArrayOutputStream(512)
        var state = 0
        val maxHeaderSize = 8192

        while (true) {
            val b = input.read()
            if (b == -1) throw EOFException("Unexpected EOF from HTTP proxy response")
            baos.write(b)

            when (state) {
                0 -> state = if (b == 0x0D) 1 else 0
                1 -> state = if (b == 0x0A) 2 else if (b == 0x0D) 1 else 0
                2 -> state = if (b == 0x0D) 3 else 0
                3 -> {
                    if (b == 0x0A) return baos.toByteArray()
                    state = if (b == 0x0D) 1 else 0
                }
            }

            if (baos.size() > maxHeaderSize) {
                throw IOException("HTTP proxy response header too large")
            }
        }
    }

    override suspend fun testConnection(
        node: ProxyNode,
        protectSocket: (Socket) -> Boolean,
        timeoutMs: Int
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val tunnelSocket = openTunnel("1.1.1.1", 80, protectSocket, timeoutMs)
            tunnelSocket.close()
            Pair(true, null)
        } catch (e: Exception) {
            Pair(false, e.localizedMessage ?: "HTTP proxy tunnel failed")
        }
    }
}
