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
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles UDP packet forwarding over the Android VPN interface.
 * Routes DNS (port 53) queries through protected sockets to high-speed DNS resolvers.
 * Suppresses direct UDP/QUIC (port 443) bypass to force browsers to use the secure TCP proxy tunnel,
 * preventing ERR_QUIC_PROTOCOL_ERROR and IP leakage.
 */
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

        // If this is QUIC (UDP 443 / 80), do not forward directly as it bypasses the proxy.
        // Dropping non-DNS UDP prompts Chrome / Android apps to seamlessly use TLS/TCP proxying.
        if (dstPort != 53) {
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val socketKey = "$srcPort->$dstAddress:$dstPort"
                var datagramSocket = activeSockets[socketKey]

                if (datagramSocket == null || datagramSocket.isClosed) {
                    datagramSocket = DatagramSocket()
                    val protected = protectSocket(datagramSocket)
                    if (!protected) {
                        AppLogger.w("UdpRelay", "Warning: VpnService.protect() returned false for UDP DNS socket")
                    }
                    datagramSocket.soTimeout = 4000
                    activeSockets[socketKey] = datagramSocket

                    val currentSocket = datagramSocket
                    scope.launch(Dispatchers.IO) {
                        val rxBuffer = ByteArray(4096)
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

                // Send outbound DNS query to primary/fallback DNS
                val targetAddr = InetAddress.getByName(primaryDns)
                val txPacket = DatagramPacket(payload, payload.size, targetAddr, dstPort)
                datagramSocket.send(txPacket)
                onTraffic(0, payload.size.toLong())
            } catch (e: Exception) {
                AppLogger.w("UdpRelay", "DNS query dispatch failed: ${e.message}")
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
