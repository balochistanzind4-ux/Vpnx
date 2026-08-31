package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.logger.AppLogger
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.security.SecureRandom
import java.util.Base64

/**
 * Robust, RFC 6455 compliant WebSocket stream framing wrapper.
 * Encapsulates outbound binary data into masked client-to-server frames (Opcode 0x02).
 * Decodes incoming unmasked server-to-client frames while handling Pings, Pongs, and Close frames.
 *
 * CRITICAL FIX:
 * The HTTP 101 Switching Protocols response is parsed byte-by-byte until double-CRLF (\r\n\r\n).
 * Never uses BufferedReader on rawIn, guaranteeing that ZERO bytes of subsequent WebSocket frames are lost.
 */
class WebSocketStreamWrapper(
    private val delegate: Socket,
    hostHeader: String,
    path: String = "/",
    customHeaders: Map<String, String> = emptyMap()
) : Socket() {

    private val wsInputStream: WebSocketInputStream
    private val wsOutputStream: WebSocketOutputStream

    init {
        val rawOut = delegate.getOutputStream()
        val rawIn = delegate.getInputStream()

        // 1. Generate 16-byte random Sec-WebSocket-Key
        val wsKeyBytes = ByteArray(16)
        SecureRandom().nextBytes(wsKeyBytes)
        val secKey = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Base64.getEncoder().encodeToString(wsKeyBytes)
        } else {
            android.util.Base64.encodeToString(wsKeyBytes, android.util.Base64.NO_WRAP).trim()
        }

        // Normalize path
        val normalizedPath = if (path.isBlank()) "/" else if (!path.startsWith("/")) "/$path" else path

        val requestBuilder = StringBuilder().apply {
            append("GET $normalizedPath HTTP/1.1\r\n")
            append("Host: $hostHeader\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Key: $secKey\r\n")
            append("Sec-WebSocket-Version: 13\r\n")
            append("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36\r\n")

            customHeaders.forEach { (k, v) ->
                if (!k.equals("Host", ignoreCase = true) &&
                    !k.equals("Upgrade", ignoreCase = true) &&
                    !k.equals("Connection", ignoreCase = true) &&
                    !k.equals("Sec-WebSocket-Key", ignoreCase = true) &&
                    !k.equals("Sec-WebSocket-Version", ignoreCase = true)
                ) {
                    append("$k: $v\r\n")
                }
            }
            append("\r\n")
        }

        val reqBytes = requestBuilder.toString().toByteArray(Charsets.US_ASCII)
        rawOut.write(reqBytes)
        rawOut.flush()

        // 2. Read HTTP 101 Switching Protocols header strictly byte-by-byte
        val headerBytes = readHttpHeaderUntilDoubleCrlf(rawIn)
        val headerText = String(headerBytes, Charsets.US_ASCII)

        if (!headerText.contains("101")) {
            throw IOException("WebSocket upgrade failed with server response: ${headerText.lines().firstOrNull() ?: "Empty"}")
        }

        val outStream = WebSocketOutputStream(BufferedOutputStream(rawOut, 32768))
        wsOutputStream = outStream
        wsInputStream = WebSocketInputStream(rawIn, outStream)
    }

    private fun readHttpHeaderUntilDoubleCrlf(input: InputStream): ByteArray {
        val baos = ByteArrayOutputStream(512)
        var state = 0 // 0: initial, 1: \r, 2: \r\n, 3: \r\n\r, 4: \r\n\r\n
        val maxHeaderSize = 16384

        while (true) {
            val b = input.read()
            if (b == -1) {
                throw EOFException("Unexpected EOF while reading HTTP upgrade response header")
            }
            baos.write(b)

            when (state) {
                0 -> state = if (b == 0x0D) 1 else 0
                1 -> state = if (b == 0x0A) 2 else if (b == 0x0D) 1 else 0
                2 -> state = if (b == 0x0D) 3 else 0
                3 -> {
                    if (b == 0x0A) {
                        // Found \r\n\r\n!
                        return baos.toByteArray()
                    } else if (b == 0x0D) {
                        state = 1
                    } else {
                        state = 0
                    }
                }
            }

            if (baos.size() > maxHeaderSize) {
                throw IOException("HTTP upgrade header exceeded maximum size ($maxHeaderSize bytes)")
            }
        }
    }

    override fun getInputStream(): InputStream = wsInputStream
    override fun getOutputStream(): OutputStream = wsOutputStream

    override fun close() {
        try {
            wsOutputStream.sendClose()
        } catch (_: Exception) {}
        try {
            delegate.close()
        } catch (_: Exception) {}
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
 * Writes binary data wrapped in RFC 6455 masked binary frames (Opcode 0x02).
 */
class WebSocketOutputStream(private val rawOut: OutputStream) : OutputStream() {

    private val random = SecureRandom()

    @Synchronized
    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    @Synchronized
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len <= 0) return

        // FIN = 1 (0x80) | Opcode = 2 (0x02) => 0x82
        val header = ByteArrayOutputStream(14)
        header.write(0x82)

        val maskBit = 0x80
        if (len <= 125) {
            header.write(maskBit or len)
        } else if (len <= 65535) {
            header.write(maskBit or 126)
            header.write((len shr 8) and 0xFF)
            header.write(len and 0xFF)
        } else {
            header.write(maskBit or 127)
            for (i in 7 downTo 0) {
                header.write(((len.toLong() shr (i * 8)) and 0xFF).toInt())
            }
        }

        // 4 bytes masking key
        val mask = ByteArray(4)
        random.nextBytes(mask)
        header.write(mask)

        rawOut.write(header.toByteArray())

        // Masked payload
        val masked = ByteArray(len)
        for (i in 0 until len) {
            masked[i] = (b[off + i].toInt() xor mask[i % 4].toInt()).toByte()
        }
        rawOut.write(masked)
        rawOut.flush()
    }

    @Synchronized
    fun sendPong(payload: ByteArray) {
        val header = ByteArrayOutputStream(14)
        header.write(0x8A) // FIN + Pong opcode 0x0A
        val maskBit = 0x80
        val len = Math.min(payload.size, 125)
        header.write(maskBit or len)

        val mask = ByteArray(4)
        random.nextBytes(mask)
        header.write(mask)
        rawOut.write(header.toByteArray())

        val masked = ByteArray(len)
        for (i in 0 until len) {
            masked[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
        }
        rawOut.write(masked)
        rawOut.flush()
    }

    @Synchronized
    fun sendClose() {
        val mask = ByteArray(4)
        random.nextBytes(mask)
        rawOut.write(byteArrayOf(0x88.toByte(), 0x80.toByte()))
        rawOut.write(mask)
        rawOut.flush()
    }

    override fun flush() {
        rawOut.flush()
    }

    override fun close() {
        rawOut.close()
    }
}

/**
 * Reads RFC 6455 WebSocket frames, decoding binary/text payloads and answering Pings with Pongs.
 */
class WebSocketInputStream(
    private val rawIn: InputStream,
    private val writer: WebSocketOutputStream
) : InputStream() {

    private var buffer = ByteArray(0)
    private var bufferOffset = 0
    private var isEof = false

    override fun read(): Int {
        val single = ByteArray(1)
        val read = read(single, 0, 1)
        return if (read == -1) -1 else (single[0].toInt() and 0xFF)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len <= 0) return 0

        while (bufferOffset >= buffer.size) {
            if (isEof) return -1
            if (!readNextFrame()) {
                if (isEof) return -1
            }
        }

        val available = buffer.size - bufferOffset
        val toCopy = Math.min(len, available)
        System.arraycopy(buffer, bufferOffset, b, off, toCopy)
        bufferOffset += toCopy
        return toCopy
    }

    /**
     * Reads the next WebSocket frame from the stream.
     * Returns true if application payload was buffered, or false if control frame was handled.
     */
    private fun readNextFrame(): Boolean {
        val b0 = rawIn.read()
        if (b0 < 0) {
            isEof = true
            return false
        }

        val opcode = b0 and 0x0F

        val b1 = rawIn.read()
        if (b1 < 0) {
            isEof = true
            return false
        }

        val isMasked = (b1 and 0x80) != 0
        var payloadLen = (b1 and 0x7F).toLong()

        if (payloadLen == 126L) {
            val byte1 = rawIn.read()
            val byte2 = rawIn.read()
            if (byte1 < 0 || byte2 < 0) throw EOFException("Unexpected EOF reading 16-bit payload length")
            payloadLen = (((byte1 and 0xFF) shl 8) or (byte2 and 0xFF)).toLong()
        } else if (payloadLen == 127L) {
            var lenVal = 0L
            for (i in 0 until 8) {
                val b = rawIn.read()
                if (b < 0) throw EOFException("Unexpected EOF reading 64-bit payload length")
                lenVal = (lenVal shl 8) or (b and 0xFF).toLong()
            }
            payloadLen = lenVal
        }

        val maskKey = if (isMasked) {
            val m = ByteArray(4)
            readFully(m)
            m
        } else null

        val payload = ByteArray(payloadLen.toInt())
        readFully(payload)

        if (isMasked && maskKey != null) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
            }
        }

        when (opcode) {
            0x00, 0x01, 0x02 -> {
                // Continuation, Text, or Binary
                buffer = payload
                bufferOffset = 0
                return true
            }
            0x08 -> {
                // Close frame
                isEof = true
                buffer = ByteArray(0)
                bufferOffset = 0
                return false
            }
            0x09 -> {
                // Ping: send Pong
                try {
                    writer.sendPong(payload)
                } catch (_: Exception) {}
                return false
            }
            0x0A -> {
                // Pong
                return false
            }
            else -> {
                // Unknown opcode: ignore
                return false
            }
        }
    }

    private fun readFully(b: ByteArray) {
        var offset = 0
        while (offset < b.size) {
            val r = rawIn.read(b, offset, b.size - offset)
            if (r < 0) throw EOFException("Unexpected EOF in WebSocket payload stream")
            offset += r
        }
    }

    override fun close() {
        rawIn.close()
    }
}
