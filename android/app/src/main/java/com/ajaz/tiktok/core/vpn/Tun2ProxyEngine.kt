package com.ajaz.tiktok.core.vpn

import android.os.ParcelFileDescriptor
import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class Tun2ProxyEngine(
    private val vpnInterface: ParcelFileDescriptor,
    private val proxyNode: ProxyNode,
    private val primaryDns: String,
    private val protectSocket: (Socket) -> Boolean,
    private val protectDatagramSocket: (DatagramSocket) -> Boolean,
    private val onStatisticsUpdate: (VpnStatistics) -> Unit,
    private val scope: CoroutineScope
) {
    private val isRunning = AtomicBoolean(true)
    private var packetLoopJob: Job? = null
    private var statsJob: Job? = null

    private val bytesIn = AtomicLong(0L)
    private val bytesOut = AtomicLong(0L)
    private var lastBytesIn = 0L
    private var lastBytesOut = 0L
    private val startTime = System.currentTimeMillis()

    private val tunInput = FileInputStream(vpnInterface.fileDescriptor)
    private val tunOutput = FileOutputStream(vpnInterface.fileDescriptor)

    private val tcpSessionManager: TcpSessionManager
    private val udpRelay: UdpRelay

    init {
        val trafficCallback: (Long, Long) -> Unit = { rx, tx ->
            if (rx > 0) bytesIn.addAndGet(rx)
            if (tx > 0) bytesOut.addAndGet(tx)
        }

        tcpSessionManager = TcpSessionManager(
            proxyNode = proxyNode,
            tunOutput = tunOutput,
            protectSocket = protectSocket,
            onTraffic = trafficCallback,
            scope = scope
        )

        udpRelay = UdpRelay(
            tunOutput = tunOutput,
            protectSocket = protectDatagramSocket,
            primaryDns = primaryDns,
            onTraffic = trafficCallback,
            scope = scope
        )

        startPacketLoop()
        startStatisticsLoop()
    }

    private fun startPacketLoop() {
        packetLoopJob = scope.launch(Dispatchers.IO) {
            val packetBuffer = ByteArray(32768)
            AppLogger.i("Tun2Proxy", "TUN packet forwarding loop started (MTU: 1500)")

            while (isActive && isRunning.get()) {
                try {
                    val bytesRead = tunInput.read(packetBuffer)
                    if (bytesRead <= 0) {
                        delay(5)
                        continue
                    }

                    val packet = IpPacket(packetBuffer, bytesRead)
                    if (packet.version == 4) {
                        if (packet.isTcp) {
                            tcpSessionManager.handleTcpPacket(packet)
                        } else if (packet.isUdp) {
                            udpRelay.handleUdpPacket(packet)
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        AppLogger.w("Tun2Proxy", "Packet read interrupted: ${e.message}")
                    }
                    break
                }
            }
        }
    }

    private fun startStatisticsLoop() {
        statsJob = scope.launch(Dispatchers.Default) {
            while (isActive && isRunning.get()) {
                delay(1000)
                val currentIn = bytesIn.get()
                val currentOut = bytesOut.get()

                val speedIn = (currentIn - lastBytesIn).coerceAtLeast(0)
                val speedOut = (currentOut - lastBytesOut).coerceAtLeast(0)

                lastBytesIn = currentIn
                lastBytesOut = currentOut

                val duration = (System.currentTimeMillis() - startTime) / 1000

                val stats = VpnStatistics(
                    bytesIn = currentIn,
                    bytesOut = currentOut,
                    speedInBps = speedIn,
                    speedOutBps = speedOut,
                    latencyMs = -1,
                    durationSeconds = duration
                )
                onStatisticsUpdate(stats)
            }
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            AppLogger.i("Tun2Proxy", "Shutting down TUN packet forwarding engine")
            packetLoopJob?.cancel()
            statsJob?.cancel()

            tcpSessionManager.closeAll()
            udpRelay.closeAll()

            try {
                tunInput.close()
            } catch (_: Exception) {}

            try {
                tunOutput.close()
            } catch (_: Exception) {}
        }
    }
}
