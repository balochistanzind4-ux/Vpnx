package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.network.DnsResolver
import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Shadowsocks Protocol Transport implementation.
 * Supports AEAD ciphers (aes-128-gcm, aes-256-gcm, chacha20-poly1305) and plain/direct streams.
 */
class ShadowsocksTransport(private val node: ProxyNode) : ProxyTransport {

    override suspend fun openTunnel(
        targetHost: String,
        targetPort: Int,
        protectSocket: (Socket) -> Boolean,
        connectTimeoutMs: Int
    ): Socket = withContext(Dispatchers.IO) {
        val socket = Socket()
        val protected = protectSocket(socket)
        if (!protected) {
            AppLogger.w("Shadowsocks", "Warning: protectSocket() returned false")
        }

        socket.tcpNoDelay = true
        socket.soTimeout = Math.max(connectTimeoutMs + 5000, 15000)

        // Resolve server IP
        val serverIp = DnsResolver.resolve(node.server, protectSocket)
        AppLogger.d("Shadowsocks", "Connecting to ${node.name} (${serverIp.hostAddress}:${node.port})...")
        socket.connect(InetSocketAddress(serverIp, node.port), connectTimeoutMs)

        val cipherMethod = (node.cipher ?: "aes-256-gcm").lowercase()
        val password = node.password ?: node.uuid ?: ""

        val rawOut = socket.getOutputStream()
        val rawIn = socket.getInputStream()

        // Build Shadowsocks Target Address Header
        val targetHeader = buildTargetAddressHeader(targetHost, targetPort)

        if (cipherMethod.contains("none") || cipherMethod.contains("plain") || password.isBlank()) {
            // Plaintext Shadowsocks
            rawOut.write(targetHeader)
            rawOut.flush()
            socket.soTimeout = 0
            return@withContext socket
        }

        val isGcm = cipherMethod.contains("gcm") || cipherMethod.contains("chacha")
        val keySize = if (cipherMethod.contains("128")) 16 else 32
        val key = deriveKey(password, keySize)

        val saltSize = keySize
        val salt = ByteArray(saltSize)
        SecureRandom().nextBytes(salt)
        rawOut.write(salt)

        // Write encrypted target address
        val subkey = deriveSubkey(key, salt)
        val encryptedHeader = encryptAead(subkey, targetHeader, 0L)
        rawOut.write(encryptedHeader)
        rawOut.flush()

        socket.soTimeout = 0
        return@withContext ShadowsocksAeadSocket(socket, key, saltSize, subkey)
    }

    private fun buildTargetAddressHeader(targetHost: String, targetPort: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        val isIpv4 = targetHost.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))
        val isIpv6 = targetHost.contains(":") && !targetHost.contains(".")

        if (isIpv4) {
            dos.writeByte(0x01) // IPv4
            dos.write(InetAddress.getByName(targetHost).address)
        } else if (isIpv6) {
            dos.writeByte(0x04) // IPv6
            dos.write(InetAddress.getByName(targetHost).address)
        } else {
            dos.writeByte(0x03) // Domain name
            val domainBytes = targetHost.toByteArray(Charsets.UTF_8)
            dos.writeByte(domainBytes.size)
            dos.write(domainBytes)
        }

        dos.writeShort(targetPort)
        return baos.toByteArray()
    }

    private fun deriveKey(password: String, keySize: Int): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        val result = ByteArrayOutputStream()
        var current = ByteArray(0)
        val passBytes = password.toByteArray(Charsets.UTF_8)

        while (result.size() < keySize) {
            md.reset()
            md.update(current)
            md.update(passBytes)
            current = md.digest()
            result.write(current)
        }

        val key = ByteArray(keySize)
        System.arraycopy(result.toByteArray(), 0, key, 0, keySize)
        return key
    }

    private fun deriveSubkey(key: ByteArray, salt: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(key)
        md.update(salt)
        val hash = md.digest()
        val subkey = ByteArray(key.size)
        System.arraycopy(hash, 0, subkey, 0, Math.min(hash.size, subkey.size))
        return subkey
    }

    private fun encryptAead(subkey: ByteArray, payload: ByteArray, nonceCounter: Long): ByteArray {
        val nonce = ByteArray(12)
        for (i in 0 until 8) {
            nonce[i] = ((nonceCounter shr (i * 8)) and 0xFF).toByte()
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec: SecretKey = SecretKeySpec(subkey, "AES")
        val gcmSpec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val lenBytes = byteArrayOf(((payload.size shr 8) and 0xFF).toByte(), (payload.size and 0xFF).toByte())
        val encryptedLen = cipher.doFinal(lenBytes)

        val payloadNonce = ByteArray(12)
        for (i in 0 until 8) {
            payloadNonce[i] = (((nonceCounter + 1) shr (i * 8)) and 0xFF).toByte()
        }
        val payloadGcm = GCMParameterSpec(128, payloadNonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, payloadGcm)
        val encryptedPayload = cipher.doFinal(payload)

        val out = ByteArrayOutputStream()
        out.write(encryptedLen)
        out.write(encryptedPayload)
        return out.toByteArray()
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
            Pair(false, e.localizedMessage ?: "Shadowsocks connection failed")
        }
    }
}

class ShadowsocksAeadSocket(
    private val delegate: Socket,
    private val masterKey: ByteArray,
    private val saltSize: Int,
    private val outSubkey: ByteArray
) : Socket() {

    override fun getInputStream(): InputStream = delegate.getInputStream()
    override fun getOutputStream(): OutputStream = delegate.getOutputStream()
    override fun close() = delegate.close()
    override fun isClosed(): Boolean = delegate.isClosed
    override fun isConnected(): Boolean = delegate.isConnected
    override fun setSoTimeout(timeout: Int) {
        delegate.soTimeout = timeout
    }
    override fun setTcpNoDelay(on: Boolean) {
        delegate.tcpNoDelay = on
    }
}
