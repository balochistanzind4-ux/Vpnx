package com.ajaz.tiktok.core.network

import com.ajaz.tiktok.core.logger.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Resilient DNS Resolution Engine for VPN proxy tunnels.
 * Features:
 * 1. Immediate bypass for raw IPv4/IPv6 addresses.
 * 2. In-memory TTL cache to prevent duplicate lookups.
 * 3. Quick System DNS resolution attempt (1.5s).
 * 4. Resilient Fallback to direct public DNS (1.1.1.1, 8.8.8.8, 9.9.9.9)
 *    using protected UDP datagram sockets to bypass local ISP censorship/poisoning.
 */
object DnsResolver {

    private data class CacheEntry(val address: InetAddress, val expiry: Long)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

    private val PUBLIC_DNS_SERVERS = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9", "1.0.0.1")

    /**
     * Resolves a host to an InetAddress.
     */
    suspend fun resolve(
        host: String,
        protectSocket: ((Socket) -> Boolean)? = null,
        protectDatagramSocket: ((DatagramSocket) -> Boolean)? = null,
        timeoutMs: Long = 4000L
    ): InetAddress = withContext(Dispatchers.IO) {
        val trimmedHost = host.trim()

        // 1. Check if already an IPv4 or IPv6 address
        if (isIpAddress(trimmedHost)) {
            return@withContext InetAddress.getByName(trimmedHost)
        }

        // 2. Check Cache
        val cached = cache[trimmedHost]
        if (cached != null && cached.expiry > System.currentTimeMillis()) {
            return@withContext cached.address
        }

        // 3. Try Fast System DNS (timeout 1500ms)
        val systemResult = withTimeoutOrNull(1500L) {
            try {
                val addrs = InetAddress.getAllByName(trimmedHost)
                // Prefer IPv4 for proxy endpoints
                val v4 = addrs.firstOrNull { it.address.size == 4 }
                v4 ?: addrs.firstOrNull()
            } catch (_: Exception) {
                null
            }
        }

        if (systemResult != null) {
            cache[trimmedHost] = CacheEntry(systemResult, System.currentTimeMillis() + CACHE_TTL_MS)
            return@withContext systemResult
        }

        // 4. Fallback: Direct DNS Query over Protected UDP socket to public resolvers
        AppLogger.d("DnsResolver", "System DNS failed for '$trimmedHost'. Attempting protected public DNS fallback...")

        for (dnsServer in PUBLIC_DNS_SERVERS) {
            try {
                val resolved = resolveViaProtectedUdp(trimmedHost, dnsServer, protectDatagramSocket)
                if (resolved != null) {
                    AppLogger.i("DnsResolver", "Resolved '$trimmedHost' -> ${resolved.hostAddress} via $dnsServer")
                    cache[trimmedHost] = CacheEntry(resolved, System.currentTimeMillis() + CACHE_TTL_MS)
                    return@withContext resolved
                }
            } catch (e: Exception) {
                AppLogger.d("DnsResolver", "Public DNS $dnsServer failed for '$trimmedHost': ${e.message}")
            }
        }

        // 5. Final fallback to blocking system call
        val finalAddr = InetAddress.getByName(trimmedHost)
        cache[trimmedHost] = CacheEntry(finalAddr, System.currentTimeMillis() + CACHE_TTL_MS)
        return@withContext finalAddr
    }

    private fun isIpAddress(host: String): Boolean {
        if (host.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))) return true
        if (host.contains(":") && !host.contains(".")) return true
        return false
    }

    /**
     * Sends a standard DNS A-record query over UDP to a public DNS resolver.
     */
    private fun resolveViaProtectedUdp(
        domain: String,
        dnsIp: String,
        protectSocket: ((DatagramSocket) -> Boolean)?
    ): InetAddress? {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            protectSocket?.invoke(socket)
            socket.soTimeout = 2000

            val query = buildDnsQuery(domain)
            val dnsAddr = InetAddress.getByName(dnsIp)
            val packet = DatagramPacket(query, query.size, dnsAddr, 53)
            socket.send(packet)

            val rxBuffer = ByteArray(512)
            val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)
            socket.receive(rxPacket)

            return parseDnsResponse(rxBuffer, rxPacket.length, domain)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    private fun buildDnsQuery(domain: String): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // Transaction ID (random)
        val id = (0..65535).random()
        dos.writeShort(id)
        // Flags: Standard query (0x0100)
        dos.writeShort(0x0100)
        // Questions count: 1
        dos.writeShort(1)
        // Answers RRs: 0
        dos.writeShort(0)
        // Authority RRs: 0
        dos.writeShort(0)
        // Additional RRs: 0
        dos.writeShort(0)

        // QNAME: length-prefixed domain labels
        for (part in domain.split('.')) {
            val bytes = part.toByteArray(Charsets.US_ASCII)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0) // Root label

        // QTYPE: A record (1)
        dos.writeShort(1)
        // QCLASS: IN (1)
        dos.writeShort(1)

        return baos.toByteArray()
    }

    private fun parseDnsResponse(buffer: ByteArray, length: Int, expectedDomain: String): InetAddress? {
        if (length < 12) return null

        val dis = DataInputStream(buffer.inputStream())
        dis.skipBytes(2) // Transaction ID
        val flags = dis.readUnsignedShort()
        val rcode = flags and 0x0F
        if (rcode != 0) return null // DNS error

        val qdCount = dis.readUnsignedShort()
        val anCount = dis.readUnsignedShort()
        dis.skipBytes(4) // NSCOUNT + ARCOUNT

        // Skip Question Section
        for (i in 0 until qdCount) {
            skipDomainName(dis, buffer)
            dis.skipBytes(4) // QTYPE + QCLASS
        }

        // Parse Answer Section
        for (i in 0 until anCount) {
            skipDomainName(dis, buffer)
            val type = dis.readUnsignedShort()
            val clazz = dis.readUnsignedShort()
            dis.skipBytes(4) // TTL
            val rdLength = dis.readUnsignedShort()

            if (type == 1 && clazz == 1 && rdLength == 4) {
                // IPv4 A record
                val ipBytes = ByteArray(4)
                dis.readFully(ipBytes)
                return InetAddress.getByAddress(expectedDomain, ipBytes)
            } else {
                dis.skipBytes(rdLength)
            }
        }
        return null
    }

    private fun skipDomainName(dis: DataInputStream, buffer: ByteArray) {
        while (true) {
            val len = dis.readUnsignedByte()
            if (len == 0) break
            if ((len and 0xC0) == 0xC0) {
                // Pointer
                dis.readUnsignedByte()
                break
            } else {
                dis.skipBytes(len)
            }
        }
    }
}
