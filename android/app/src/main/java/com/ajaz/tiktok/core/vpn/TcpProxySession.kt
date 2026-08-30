package com.ajaz.tiktok.core.vpn

import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.parser.ProxyNode
import com.ajaz.tiktok.core.transport.ProxyTransportFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages an individual TCP session between a local client (via TUN interface)
 * and the remote proxy server (via VLESS, Trojan, Shadowsocks, etc.).
 * Guarantees packet fragmentation under TUN MTU (1500) and strict TCP sequence ordering.
 */
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
    companion object {
        private const val MAX_TCP_MSS = 1360 // Sliced under 1500 MTU (IP 20B + TCP 20B + MSS = 1400B)
    }

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

    private val outboundChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private val isClosed = AtomicBoolean(false)
    private var readerJob: Job? = null
    private var writerJob: Job? = null

    val lastActivityTime = AtomicLong(System.currentTimeMillis())

    fun handleIncomingPacket(packet: IpPacket) {
        lastActivityTime.set(System.currentTimeMillis())

        if (packet.isRst) {
            close()
            return
        }

        if (packet.isFin) {
            clientAckNum = packet.sequenceNumber + 1
            sendFinAck(clientAckNum)
            close()
            return
        }

        if (packet.isSyn) {
            if (state == State.SYN_RECEIVED) {
                clientAckNum = packet.sequenceNumber + 1
                sendSynAck()

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
                        val out = socket.getOutputStream()
                        socketOut = out
                        state = State.ESTABLISHED

                        startProxyWriterLoop(out)
                        startProxyReaderLoop()
                    } catch (e: Exception) {
                        AppLogger.w("TcpSession", "Failed to establish tunnel for $dstAddress:$dstPort: ${e.message}")
                        sendRst()
                        close()
                    }
                }
            } else if (state == State.CONNECTING || state == State.ESTABLISHED) {
                // Retransmitted SYN from client: re-send SYN-ACK
                resendSynAck()
            }
            return
        }

        // Handle incoming client TCP data payload
        val payload = packet.getPayload()
        if (payload.isNotEmpty()) {
            clientAckNum = packet.sequenceNumber + payload.size
            sendAck()

            // Feed payload to remote tunnel
            outboundChannel.trySend(payload)
        }
    }

    private fun startProxyWriterLoop(out: OutputStream) {
        writerJob = scope.launch(Dispatchers.IO) {
            try {
                for (chunk in outboundChannel) {
                    if (isClosed.get()) break
                    out.write(chunk)
                    out.flush()
                    onTraffic(0, chunk.size.toLong())
                    lastActivityTime.set(System.currentTimeMillis())
                }
            } catch (e: Exception) {
                if (!isClosed.get()) {
                    AppLogger.w("TcpSession", "Socket write error on $dstAddress:$dstPort: ${e.message}")
                    close()
                }
            }
        }
    }

    private fun startProxyReaderLoop() {
        readerJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(16384)
            val input = socketIn ?: return@launch

            try {
                while (isActive && !isClosed.get()) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead <= 0) break

                    lastActivityTime.set(System.currentTimeMillis())
                    onTraffic(bytesRead.toLong(), 0)

                    // Slice incoming stream into segments <= MAX_TCP_MSS to prevent MTU drops
                    var offset = 0
                    while (offset < bytesRead && !isClosed.get()) {
                        val chunkSize = Math.min(MAX_TCP_MSS, bytesRead - offset)
                        val chunk = ByteArray(chunkSize)
                        System.arraycopy(buffer, offset, chunk, 0, chunkSize)
                        sendDataToTun(chunk)
                        offset += chunkSize
                    }
                }
            } catch (_: Exception) {
                // Socket stream finished or closed
            } finally {
                sendFin()
                close()
            }
        }
    }

    private fun sendSynAck() {
        val reply = IpPacket.createTcpPacket(
            srcIp = dstIp,
            dstIp = srcIp,
            srcPort = dstPort,
            dstPort = srcPort,
            seqNumber = mySeqNum,
            ackNumber = clientAckNum,
            flags = 0x12 // SYN | ACK
        )
        mySeqNum++
        writeToTun(reply)
    }

    private fun resendSynAck() {
        val reply = IpPacket.createTcpPacket(
            srcIp = dstIp,
            dstIp = srcIp,
            srcPort = dstPort,
            dstPort = srcPort,
            seqNumber = mySeqNum - 1,
            ackNumber = clientAckNum,
            flags = 0x12
        )
        writeToTun(reply)
    }

    private fun sendAck() {
        val reply = IpPacket.createTcpPacket(
            srcIp = dstIp,
            dstIp = srcIp,
            srcPort = dstPort,
            dstPort = srcPort,
            seqNumber = mySeqNum,
            ackNumber = clientAckNum,
            flags = 0x10 // ACK
        )
        writeToTun(reply)
    }

    private fun sendDataToTun(data: ByteArray) {
        val reply = IpPacket.createTcpPacket(
            srcIp = dstIp,
            dstIp = srcIp,
            srcPort = dstPort,
            dstPort = srcPort,
            seqNumber = mySeqNum,
            ackNumber = clientAckNum,
            flags = 0x18, // PSH | ACK
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
            seqNumber = mySeqNum,
            ackNumber = clientAckNum,
            flags = 0x11 // FIN | ACK
        )
        mySeqNum++
        writeToTun(reply)
    }

    private fun sendFinAck(ackNum: Long) {
        val reply = IpPacket.createTcpPacket(
            srcIp = dstIp,
            dstIp = srcIp,
            srcPort = dstPort,
            dstPort = srcPort,
            seqNumber = mySeqNum,
            ackNumber = ackNum,
            flags = 0x10 // ACK
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
            flags = 0x04 // RST
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
            outboundChannel.close()
            readerJob?.cancel()
            writerJob?.cancel()
            try {
                socketIn?.close()
                socketOut?.close()
                proxySocket?.close()
            } catch (_: Exception) {}
            onSessionClosed(this)
        }
    }
}
