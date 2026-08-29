package com.ajaz.tiktok.core.vpn

import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.parser.ProxyNode
import com.ajaz.tiktok.core.transport.ProxyTransportFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class TcpProxySession(
    val sessionKey: String,
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val dstAddress: String,
    private val proxyNode: ProxyNode,
    private val tunOutput: FileOutputStream,
    private val protectSocket: (Socket) -> Boolean,
    private val onSessionClosed: (TcpProxySession) -> Unit,
    private val onTraffic: (bytesIn: Long, bytesOut: Long) -> Unit,
    private val scope: CoroutineScope
) {
    enum class State {
        SYN_RECEIVED,
        CONNECTING,
        ESTABLISHED,
        CLOSING,
        CLOSED
    }

    @Volatile
    var state: State = State.SYN_RECEIVED
        private set

    private var mySeqNum: Long = (0..65535).random().toLong()
    private var clientAckNum: Long = 0L

    private var proxySocket: Socket? = null
    private var socketIn: InputStream? = null
    private var socketOut: OutputStream? = null

    private val isClosed = AtomicBoolean(false)
    private var readerJob: Job? = null

    val lastActivityTime = AtomicLong(System.currentTimeMillis())

    fun handleIncomingPacket(packet: IpPacket) {
        lastActivityTime.set(System.currentTimeMillis())

        if (packet.isRst) {
            close()
            return
        }

        if (packet.isFin) {
            sendFinAck(packet.sequenceNumber + 1)
            close()
            return
        }

        if (packet.isSyn && state == State.SYN_RECEIVED) {
            clientAckNum = packet.sequenceNumber + 1
            // 1. Send SYN-ACK back to local client
            sendSynAck()

            // 2. Connect to remote proxy asynchronously
            state = State.CONNECTING
            scope.launch(Dispatchers.IO) {
                try {
                    val transport = ProxyTransportFactory.create(proxyNode)
                    val socket = transport.openTunnel(
                        targetHost = dstAddress,
                        targetPort = dstPort,
                        protectSocket = protectSocket,
                        connectTimeoutMs = 12000
                    )
                    proxySocket = socket
                    socketIn = socket.getInputStream()
                    socketOut = socket.getOutputStream()
                    state = State.ESTABLISHED
                    startProxyReaderLoop()
                } catch (e: Exception) {
                    AppLogger.w("TcpSession", "Failed to connect tunnel for $dstAddress:$dstPort: ${e.message}")
                    sendRst()
                    close()
                }
            }
            return
        }

        // Handle regular payload data
        val payload = packet.getPayload()
        if (payload.isNotEmpty()) {
            clientAckNum = packet.sequenceNumber + payload.size
            sendAck()

            val out = socketOut
            if (state == State.ESTABLISHED && out != null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        out.write(payload)
                        out.flush()
                        onTraffic(0, payload.size.toLong())
                    } catch (e: Exception) {
                        AppLogger.w("TcpSession", "Error forwarding data to proxy: ${e.message}")
                        close()
                    }
                }
            }
        }
    }

    private fun startProxyReaderLoop() {
        readerJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(32768)
            val input = socketIn ?: return@launch

            try {
                while (isActive && !isClosed.get()) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead <= 0) break

                    lastActivityTime.set(System.currentTimeMillis())
                    onTraffic(bytesRead.toLong(), 0)

                    val payload = ByteArray(bytesRead)
                    System.arraycopy(buffer, 0, payload, 0, bytesRead)

                    sendDataToTun(payload)
                }
            } catch (_: Exception) {
                // Socket read finished or closed
            } finally {
                sendFin()
                close()
            }
        }
    }

    private fun sendSynAck() {
        mySeqNum++
        val reply = IpPacket.createTcpPacket(
            srcIp = dstIp,
            dstIp = srcIp,
            srcPort = dstPort,
            dstPort = srcPort,
            seqNumber = mySeqNum,
            ackNumber = clientAckNum,
            flags = 0x12 // SYN (0x02) | ACK (0x10)
        )
        writeToTun(reply)
    }

    private fun sendAck() {
        val reply = IpPacket.createTcpPacket(
            srcIp = dstIp,
            dstIp = srcIp,
            srcPort = dstPort,
            dstPort = srcPort,
            seqNumber = mySeqNum + 1,
            ackNumber = clientAckNum,
            flags = 0x10 // ACK (0x10)
        )
        writeToTun(reply)
    }

    private fun sendDataToTun(data: ByteArray) {
        val reply = IpPacket.createTcpPacket(
            srcIp = dstIp,
            dstIp = srcIp,
            srcPort = dstPort,
            dstPort = srcPort,
            seqNumber = mySeqNum + 1,
            ackNumber = clientAckNum,
            flags = 0x18, // PSH (0x08) | ACK (0x10)
            payload = data
        )
        mySeqNum += data.size
        writeToTun(reply)
    }

    private fun sendFin() {
        val reply = IpPacket.createTcpPacket(
            srcIp = dstIp,
            dstIp = srcIp,
            srcPort = dstPort,
            dstPort = srcPort,
            seqNumber = mySeqNum + 1,
            ackNumber = clientAckNum,
            flags = 0x11 // FIN (0x01) | ACK (0x10)
        )
        writeToTun(reply)
    }

    private fun sendFinAck(ackNum: Long) {
        val reply = IpPacket.createTcpPacket(
            srcIp = dstIp,
            dstIp = srcIp,
            srcPort = dstPort,
            dstPort = srcPort,
            seqNumber = mySeqNum + 1,
            ackNumber = ackNum,
            flags = 0x10 // ACK (0x10)
        )
        writeToTun(reply)
    }

    private fun sendRst() {
        val reply = IpPacket.createTcpPacket(
            srcIp = dstIp,
            dstIp = srcIp,
            srcPort = dstPort,
            dstPort = srcPort,
            seqNumber = mySeqNum,
            ackNumber = clientAckNum,
            flags = 0x04 // RST (0x04)
        )
        writeToTun(reply)
    }

    @Synchronized
    private fun writeToTun(packet: ByteArray) {
        if (isClosed.get()) return
        try {
            tunOutput.write(packet)
            tunOutput.flush()
        } catch (_: Exception) {
            // Tunnel output closed
        }
    }

    fun close() {
        if (isClosed.compareAndSet(false, true)) {
            state = State.CLOSED
            readerJob?.cancel()
            try {
                socketIn?.close()
                socketOut?.close()
                proxySocket?.close()
            } catch (_: Exception) {}
            onSessionClosed(this)
        }
    }
}
