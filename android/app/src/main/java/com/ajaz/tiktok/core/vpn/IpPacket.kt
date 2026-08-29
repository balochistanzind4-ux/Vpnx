package com.ajaz.tiktok.core.vpn

import java.net.InetAddress
import java.nio.ByteBuffer

class IpPacket(val rawData: ByteArray, val length: Int) {

    val version: Int
    val headerLength: Int
    val totalLength: Int
    val protocol: Int
    val sourceIp: ByteArray
    val destIp: ByteArray
    val sourceAddress: String
    val destAddress: String

    // TCP specific fields
    var isTcp: Boolean = false
    var srcPort: Int = 0
    var dstPort: Int = 0
    var sequenceNumber: Long = 0L
    var ackNumber: Long = 0L
    var tcpHeaderLength: Int = 0
    var isSyn: Boolean = false
    var isAck: Boolean = false
    var isFin: Boolean = false
    var isRst: Boolean = false
    var isPsh: Boolean = false
    var tcpPayloadOffset: Int = 0
    var tcpPayloadLength: Int = 0

    // UDP specific fields
    var isUdp: Boolean = false
    var udpLength: Int = 0
    var udpPayloadOffset: Int = 0
    var udpPayloadLength: Int = 0

    init {
        val buffer = ByteBuffer.wrap(rawData, 0, length)
        val firstByte = buffer.get(0).toInt() and 0xFF
        version = firstByte shr 4

        if (version == 4) {
            headerLength = (firstByte and 0x0F) * 4
            totalLength = (buffer.getShort(2).toInt() and 0xFFFF).coerceAtMost(length)
            protocol = buffer.get(9).toInt() and 0xFF

            sourceIp = ByteArray(4)
            destIp = ByteArray(4)
            System.arraycopy(rawData, 12, sourceIp, 0, 4)
            System.arraycopy(rawData, 16, destIp, 0, 4)

            sourceAddress = InetAddress.getByAddress(sourceIp).hostAddress ?: "0.0.0.0"
            destAddress = InetAddress.getByAddress(destIp).hostAddress ?: "0.0.0.0"

            if (protocol == 6 && totalLength >= headerLength + 20) {
                // TCP
                isTcp = true
                srcPort = buffer.getShort(headerLength).toInt() and 0xFFFF
                dstPort = buffer.getShort(headerLength + 2).toInt() and 0xFFFF
                sequenceNumber = buffer.getInt(headerLength + 4).toLong() and 0xFFFFFFFFL
                ackNumber = buffer.getInt(headerLength + 8).toLong() and 0xFFFFFFFFL

                val dataOffsetByte = buffer.get(headerLength + 12).toInt() and 0xFF
                tcpHeaderLength = (dataOffsetByte shr 4) * 4

                val flags = buffer.get(headerLength + 13).toInt() and 0xFF
                isFin = (flags and 0x01) != 0
                isSyn = (flags and 0x02) != 0
                isRst = (flags and 0x04) != 0
                isPsh = (flags and 0x08) != 0
                isAck = (flags and 0x10) != 0

                tcpPayloadOffset = headerLength + tcpHeaderLength
                tcpPayloadLength = (totalLength - tcpPayloadOffset).coerceAtLeast(0)
            } else if (protocol == 17 && totalLength >= headerLength + 8) {
                // UDP
                isUdp = true
                srcPort = buffer.getShort(headerLength).toInt() and 0xFFFF
                dstPort = buffer.getShort(headerLength + 2).toInt() and 0xFFFF
                udpLength = buffer.getShort(headerLength + 4).toInt() and 0xFFFF
                udpPayloadOffset = headerLength + 8
                udpPayloadLength = (udpLength - 8).coerceAtLeast(0).coerceAtMost(length - udpPayloadOffset)
            }
        } else {
            headerLength = 40
            totalLength = length
            protocol = 0
            sourceIp = ByteArray(16)
            destIp = ByteArray(16)
            sourceAddress = "::"
            destAddress = "::"
        }
    }

    fun getPayload(): ByteArray {
        val offset = if (isTcp) tcpPayloadOffset else if (isUdp) udpPayloadOffset else headerLength
        val len = if (isTcp) tcpPayloadLength else if (isUdp) udpPayloadLength else 0
        if (len <= 0 || offset + len > rawData.size) return ByteArray(0)
        val payload = ByteArray(len)
        System.arraycopy(rawData, offset, payload, 0, len)
        return payload
    }

    companion object {

        fun createTcpPacket(
            srcIp: ByteArray,
            dstIp: ByteArray,
            srcPort: Int,
            dstPort: Int,
            seqNumber: Long,
            ackNumber: Long,
            flags: Int, // SYN=2, ACK=16, FIN=1, RST=4, PSH=8
            payload: ByteArray = ByteArray(0),
            windowSize: Int = 65535
        ): ByteArray {
            val ipHeaderLen = 20
            val tcpHeaderLen = 20
            val totalLen = ipHeaderLen + tcpHeaderLen + payload.size
            val packet = ByteArray(totalLen)
            val buffer = ByteBuffer.wrap(packet)

            // 1. IP Header
            buffer.put(0, 0x45.toByte()) // Version 4, IHL 5 (20 bytes)
            buffer.put(1, 0x00.toByte()) // TOS
            buffer.putShort(2, totalLen.toShort()) // Total Length
            buffer.putShort(4, (0..65535).random().toShort()) // Identification
            buffer.putShort(6, 0x4000.toShort()) // Flags (Don't Fragment)
            buffer.put(8, 64.toByte()) // TTL
            buffer.put(9, 6.toByte()) // Protocol 6 = TCP
            buffer.putShort(10, 0.toShort()) // Checksum placeholder

            // Source & Destination IP
            System.arraycopy(srcIp, 0, packet, 12, 4)
            System.arraycopy(dstIp, 0, packet, 16, 4)

            // Calculate IP Checksum
            val ipChecksum = ChecksumCalculator.calculateChecksum(packet, 0, ipHeaderLen)
            buffer.putShort(10, ipChecksum.toShort())

            // 2. TCP Header
            buffer.putShort(ipHeaderLen, srcPort.toShort())
            buffer.putShort(ipHeaderLen + 2, dstPort.toShort())
            buffer.putInt(ipHeaderLen + 4, seqNumber.toInt())
            buffer.putInt(ipHeaderLen + 8, ackNumber.toInt())
            buffer.put(ipHeaderLen + 12, 0x50.toByte()) // Data Offset (5 * 4 = 20 bytes)
            buffer.put(ipHeaderLen + 13, flags.toByte()) // Flags
            buffer.putShort(ipHeaderLen + 14, windowSize.toShort()) // Window
            buffer.putShort(ipHeaderLen + 16, 0.toShort()) // TCP Checksum placeholder
            buffer.putShort(ipHeaderLen + 18, 0.toShort()) // Urgent pointer

            // 3. Payload
            if (payload.isNotEmpty()) {
                System.arraycopy(payload, 0, packet, ipHeaderLen + tcpHeaderLen, payload.size)
            }

            // Calculate TCP Checksum
            val tcpChecksum = ChecksumCalculator.calculateTcpChecksum(
                packet,
                ipHeaderLen,
                tcpHeaderLen + payload.size,
                srcIp,
                dstIp
            )
            buffer.putShort(ipHeaderLen + 16, tcpChecksum.toShort())

            return packet
        }

        fun createUdpPacket(
            srcIp: ByteArray,
            dstIp: ByteArray,
            srcPort: Int,
            dstPort: Int,
            payload: ByteArray
        ): ByteArray {
            val ipHeaderLen = 20
            val udpHeaderLen = 8
            val udpTotalLen = udpHeaderLen + payload.size
            val totalLen = ipHeaderLen + udpTotalLen
            val packet = ByteArray(totalLen)
            val buffer = ByteBuffer.wrap(packet)

            // IP Header
            buffer.put(0, 0x45.toByte())
            buffer.put(1, 0x00.toByte())
            buffer.putShort(2, totalLen.toShort())
            buffer.putShort(4, (0..65535).random().toShort())
            buffer.putShort(6, 0x0000.toShort())
            buffer.put(8, 64.toByte())
            buffer.put(9, 17.toByte()) // UDP
            buffer.putShort(10, 0.toShort())

            System.arraycopy(srcIp, 0, packet, 12, 4)
            System.arraycopy(dstIp, 0, packet, 16, 4)

            val ipChecksum = ChecksumCalculator.calculateChecksum(packet, 0, ipHeaderLen)
            buffer.putShort(10, ipChecksum.toShort())

            // UDP Header
            buffer.putShort(ipHeaderLen, srcPort.toShort())
            buffer.putShort(ipHeaderLen + 2, dstPort.toShort())
            buffer.putShort(ipHeaderLen + 4, udpTotalLen.toShort())
            buffer.putShort(ipHeaderLen + 6, 0.toShort())

            // Payload
            System.arraycopy(payload, 0, packet, ipHeaderLen + udpHeaderLen, payload.size)

            val udpChecksum = ChecksumCalculator.calculateUdpChecksum(
                packet,
                ipHeaderLen,
                udpTotalLen,
                srcIp,
                dstIp
            )
            buffer.putShort(ipHeaderLen + 6, udpChecksum.toShort())

            return packet
        }
    }
}
