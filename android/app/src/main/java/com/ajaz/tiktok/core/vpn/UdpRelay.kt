package com.ajaz.tiktok.core.vpn

import com.ajaz.tiktok.core.logger.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class UdpRelay(
    private val tunOutput: FileOutputStream,
    private val protectSocket: (DatagramSocket) -> Boolean,
    private val primaryDns: String = "1.1.1.1",
    private val onTraffic: (bytesIn: Long, bytesOut: Long) -> Unit,
    private val scope: CoroutineScope
) {
    private val activeSockets = ConcurrentHashMap<String, DatagramSocket>()

    fun handleUdpPacket(packet: IpPacket) {
        val payload = packet.getPayload()
        if (payload.isEmpty()) return

        val srcPort = packet.srcPort
        val dstPort = packet.dstPort
        val srcIp = packet.sourceIp
        val dstIp = packet.destIp
        val dstAddress = packet.destAddress

        scope.launch(Dispatchers.IO) {
            try {
                val socketKey = "$srcPort->$dstAddress:$dstPort"
                var datagramSocket = activeSockets[socketKey]

                if (datagramSocket == null || datagramSocket.isClosed) {
                    datagramSocket = DatagramSocket()
                    protectSocket(datagramSocket)
                    datagramSocket.soTimeout = 4000
                    activeSockets[socketKey] = datagramSocket

                    // Start background receiver for this UDP conversation
                    val currentSocket = datagramSocket
                    scope.launch(Dispatchers.IO) {
                        val rxBuffer = ByteArray(2048)
                        try {
                            while (isActive && !currentSocket.isClosed) {
                                val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)
                                currentSocket.receive(rxPacket)

                                val receivedData = ByteArray(rxPacket.length)
                                System.arraycopy(rxBuffer, 0, receivedData, 0, rxPacket.length)

                                onTraffic(receivedData.size.toLong(), 0)

                                // Construct reply IP/UDP packet back to local app
                                val replyIpPacket = IpPacket.createUdpPacket(
                                    srcIp = dstIp,
                                    dstIp = srcIp,
                                    srcPort = dstPort,
                                    dstPort = srcPort,
                                    payload = receivedData
                                )

                                synchronized(tunOutput) {
                                    tunOutput.write(replyIpPacket)
                                    tunOutput.flush()
                                }
                            }
                        } catch (_: Exception) {
                            // Timeout or closed
                        } finally {
                            activeSockets.remove(socketKey)
                            try {
                                currentSocket.close()
                            } catch (_: Exception) {}
                        }
                    }
                }

                // Send outbound UDP packet
                val targetAddr = if (dstPort == 53) {
                    InetAddress.getByName(primaryDns)
                } else {
                    InetAddress.getByName(dstAddress)
                }

                val txPacket = DatagramPacket(payload, payload.size, targetAddr, dstPort)
                datagramSocket.send(txPacket)
                onTraffic(0, payload.size.toLong())
            } catch (e: Exception) {
                AppLogger.w("UdpRelay", "UDP dispatch failed for $dstAddress:$dstPort: ${e.message}")
            }
        }
    }

    fun closeAll() {
        activeSockets.values.forEach {
            try {
                it.close()
            } catch (_: Exception) {}
        }
        activeSockets.clear()
    }
}
