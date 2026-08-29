package com.ajaz.tiktok.core.vpn

import java.nio.ByteBuffer

object ChecksumCalculator {

    fun calculateChecksum(buffer: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length

        while (i < end - 1) {
            val byte1 = buffer[i].toInt() and 0xFF
            val byte2 = buffer[i + 1].toInt() and 0xFF
            sum += (byte1 shl 8) or byte2
            i += 2
        }

        if (i < end) {
            val byte1 = buffer[i].toInt() and 0xFF
            sum += byte1 shl 8
        }

        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        return (sum.inv()) and 0xFFFF
    }

    fun calculateTcpChecksum(
        packet: ByteArray,
        ipHeaderLength: Int,
        tcpLength: Int,
        srcIp: ByteArray,
        dstIp: ByteArray
    ): Int {
        var sum = 0

        // Pseudo Header: Source IP (4 bytes)
        sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
        sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)

        // Pseudo Header: Dest IP (4 bytes)
        sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
        sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)

        // Pseudo Header: Zero (1 byte) + Protocol (1 byte, 6 for TCP)
        sum += 6

        // Pseudo Header: TCP Length (2 bytes)
        sum += tcpLength

        // TCP Header + Data
        var i = ipHeaderLength
        val end = ipHeaderLength + tcpLength

        while (i < end - 1) {
            val byte1 = packet[i].toInt() and 0xFF
            val byte2 = packet[i + 1].toInt() and 0xFF
            sum += (byte1 shl 8) or byte2
            i += 2
        }

        if (i < end) {
            val byte1 = packet[i].toInt() and 0xFF
            sum += byte1 shl 8
        }

        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        return (sum.inv()) and 0xFFFF
    }

    fun calculateUdpChecksum(
        packet: ByteArray,
        ipHeaderLength: Int,
        udpLength: Int,
        srcIp: ByteArray,
        dstIp: ByteArray
    ): Int {
        var sum = 0

        // Pseudo Header
        sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
        sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)
        sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
        sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)
        sum += 17 // UDP Protocol
        sum += udpLength

        var i = ipHeaderLength
        val end = ipHeaderLength + udpLength

        while (i < end - 1) {
            val byte1 = packet[i].toInt() and 0xFF
            val byte2 = packet[i + 1].toInt() and 0xFF
            sum += (byte1 shl 8) or byte2
            i += 2
        }

        if (i < end) {
            val byte1 = packet[i].toInt() and 0xFF
            sum += byte1 shl 8
        }

        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        val result = (sum.inv()) and 0xFFFF
        return if (result == 0) 0xFFFF else result
    }
}
