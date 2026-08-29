package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
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
        protectSocket(socket)
        socket.tcpNoDelay = true
        socket.soTimeout = 20000
        socket.connect(InetSocketAddress(node.server, node.port), connectTimeoutMs)

        val out = socket.getOutputStream()
        val `in` = socket.getInputStream()

        val reqBuilder = StringBuilder()
        reqBuilder.append("CONNECT $targetHost:$targetPort HTTP/1.1\r\n")
        reqBuilder.append("Host: $targetHost:$targetPort\r\n")
        reqBuilder.append("User-Agent: Clash/1.18.0 AjazTiktok/1.0.0\r\n")
        reqBuilder.append("Proxy-Connection: Keep-Alive\r\n")

        if (!node.password.isNullOrBlank()) {
            val user = node.uuid ?: "user"
            val auth = "$user:${node.password}"
            val encoded = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Base64.getEncoder().encodeToString(auth.toByteArray(Charsets.UTF_8))
            } else {
                android.util.Base64.encodeToString(auth.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            }
            reqBuilder.append("Proxy-Authorization: Basic $encoded\r\n")
        }
        reqBuilder.append("\r\n")

        out.write(reqBuilder.toString().toByteArray(Charsets.US_ASCII))
        out.flush()

        // Read HTTP status line
        val reader = BufferedReader(InputStreamReader(`in`, Charsets.US_ASCII))
        val statusLine = reader.readLine() ?: throw IOException("Empty response from HTTP proxy")

        if (!statusLine.contains(" 200 ")) {
            socket.close()
            throw IOException("HTTP Proxy CONNECT failed: $statusLine")
        }

        // Read until empty line (end of headers)
        var line: String?
        while (true) {
            line = reader.readLine()
            if (line.isNullOrEmpty()) break
        }

        socket.soTimeout = 0
        return@withContext socket
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
