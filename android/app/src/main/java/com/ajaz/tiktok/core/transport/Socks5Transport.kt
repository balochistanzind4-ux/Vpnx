package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.network.DnsResolver
import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class Socks5Transport(private val node: ProxyNode) : ProxyTransport {

    override suspend fun openTunnel(
        targetHost: String,
        targetPort: Int,
        protectSocket: (Socket) -> Boolean,
        connectTimeoutMs: Int
    ): Socket = withContext(Dispatchers.IO) {
        val socket = Socket()
        val isProtected = protectSocket(socket)
        if (!isProtected) {
            AppLogger.w("Socks5", "Warning: VpnService.protect() returned false for socket")
        }

        socket.tcpNoDelay = true
        socket.soTimeout = Math.max(connectTimeoutMs + 5000, 15000)

        // Resolve server IP via DnsResolver
        val serverIp = DnsResolver.resolve(node.server, protectSocket)
        AppLogger.d("Socks5", "Connecting to ${node.name} (${serverIp.hostAddress}:${node.port})...")
        socket.connect(InetSocketAddress(serverIp, node.port), connectTimeoutMs)

        val out = DataOutputStream(socket.getOutputStream())
        val `in` = DataInputStream(socket.getInputStream())

        // 1. Negotiation Handshake
        val hasAuth = !node.password.isNullOrBlank() || !node.uuid.isNullOrBlank()
        if (hasAuth) {
            // SOCKS5 (0x05), 2 methods: NO_AUTH (0x00) and USER_PASS (0x02)
            out.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
        } else {
            // SOCKS5 (0x05), 1 method: NO_AUTH (0x00)
            out.write(byteArrayOf(0x05, 0x01, 0x00))
        }
        out.flush()

        val version = `in`.readUnsignedByte()
        val method = `in`.readUnsignedByte()

        if (version != 5) {
            socket.close()
            throw IOException("Invalid SOCKS version from server: $version")
        }

        // 2. Authentication if requested
        if (method == 0x02) {
            val username = node.uuid ?: "user"
            val password = node.password ?: ""
            val uBytes = username.toByteArray(Charsets.UTF_8)
            val pBytes = password.toByteArray(Charsets.UTF_8)

            out.writeByte(0x01) // Subnegotiation version 1
            out.writeByte(uBytes.size)
            out.write(uBytes)
            out.writeByte(pBytes.size)
            out.write(pBytes)
            out.flush()

            val authVer = `in`.readUnsignedByte()
            val authStatus = `in`.readUnsignedByte()
            if (authStatus != 0x00) {
                socket.close()
                throw IOException("SOCKS5 Authentication failed (status $authStatus)")
            }
        } else if (method == 0xFF) {
            socket.close()
            throw IOException("SOCKS5 Server rejected authentication methods")
        }

        // 3. Connect Command
        out.writeByte(0x05)
        out.writeByte(0x01) // CONNECT
        out.writeByte(0x00) // RSV

        val isIpv4 = targetHost.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))
        val isIpv6 = targetHost.contains(":") && !targetHost.contains(".")

        if (isIpv4) {
            val ipBytes = InetAddress.getByName(targetHost).address
            out.writeByte(0x01) // IPv4
            out.write(ipBytes)
        } else if (isIpv6) {
            val ipBytes = InetAddress.getByName(targetHost).address
            out.writeByte(0x04) // IPv6
            out.write(ipBytes)
        } else {
            // Domain name
            val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
            out.writeByte(0x03)
            out.writeByte(hostBytes.size)
            out.write(hostBytes)
        }

        out.writeShort(targetPort)
        out.flush()

        // 4. Read Response
        val repVer = `in`.readUnsignedByte()
        val repStatus = `in`.readUnsignedByte()
        val repRsv = `in`.readUnsignedByte()
        val repAtyp = `in`.readUnsignedByte()

        if (repStatus != 0x00) {
            val errorMsg = when (repStatus) {
                0x01 -> "General SOCKS server failure"
                0x02 -> "Connection not allowed by ruleset"
                0x03 -> "Network unreachable"
                0x04 -> "Host unreachable"
                0x05 -> "Connection refused by target"
                0x06 -> "TTL expired"
                0x07 -> "Command not supported"
                0x08 -> "Address type not supported"
                else -> "SOCKS5 error code $repStatus"
            }
            socket.close()
            throw IOException(errorMsg)
        }

        // Skip bound address
        when (repAtyp) {
            0x01 -> {
                val b = ByteArray(4)
                `in`.readFully(b)
            }
            0x03 -> {
                val len = `in`.readUnsignedByte()
                val b = ByteArray(len)
                `in`.readFully(b)
            }
            0x04 -> {
                val b = ByteArray(16)
                `in`.readFully(b)
            }
        }
        val boundPort = `in`.readUnsignedShort()

        // Reset soTimeout for active session
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
            Pair(false, e.localizedMessage ?: "SOCKS5 connection failed")
        }
    }
}
