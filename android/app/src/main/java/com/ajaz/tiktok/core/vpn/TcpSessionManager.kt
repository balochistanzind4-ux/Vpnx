package com.ajaz.tiktok.core.vpn

import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class TcpSessionManager(
    private val proxyNode: ProxyNode,
    private val tunOutput: FileOutputStream,
    private val protectSocket: (Socket) -> Boolean,
    private val onTraffic: (bytesIn: Long, bytesOut: Long) -> Unit,
    private val scope: CoroutineScope
) {
    private val sessions = ConcurrentHashMap<String, TcpProxySession>()
    private var cleanupJob: Job? = null

    init {
        startCleanupLoop()
    }

    fun handleTcpPacket(packet: IpPacket) {
        val sessionKey = "${packet.sourceAddress}:${packet.srcPort}->${packet.destAddress}:${packet.dstPort}"
        var session = sessions[sessionKey]

        if (session == null) {
            if (packet.isSyn) {
                val newSession = TcpProxySession(
                    sessionKey = sessionKey,
                    srcIp = packet.sourceIp,
                    dstIp = packet.destIp,
                    srcPort = packet.srcPort,
                    dstPort = packet.dstPort,
                    dstAddress = packet.destAddress,
                    proxyNode = proxyNode,
                    tunOutput = tunOutput,
                    protectSocket = protectSocket,
                    onSessionClosed = { closedSession ->
                        sessions.remove(closedSession.sessionKey)
                    },
                    onTraffic = onTraffic,
                    scope = scope
                )
                sessions[sessionKey] = newSession
                newSession.handleIncomingPacket(packet)
            }
        } else {
            session.handleIncomingPacket(packet)
        }
    }

    private fun startCleanupLoop() {
        cleanupJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(15000)
                val now = System.currentTimeMillis()
                val iterator = sessions.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    val session = entry.value
                    if (now - session.lastActivityTime.get() > 60000 || session.state == TcpProxySession.State.CLOSED) {
                        session.close()
                        iterator.remove()
                    }
                }
            }
        }
    }

    fun closeAll() {
        cleanupJob?.cancel()
        sessions.values.forEach { it.close() }
        sessions.clear()
    }
}
