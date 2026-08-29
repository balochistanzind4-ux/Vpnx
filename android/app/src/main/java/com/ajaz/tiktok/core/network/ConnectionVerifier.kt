package com.ajaz.tiktok.core.network

import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.parser.ProxyNode
import com.ajaz.tiktok.core.transport.ProxyTransportFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

sealed class VerificationResult {
    data class Success(val exitIp: String?, val latencyMs: Long) : VerificationResult()
    data class Failure(val reason: String, val recoverySuggestion: String? = null) : VerificationResult()
}

object ConnectionVerifier {

    suspend fun verifyTunnel(
        node: ProxyNode,
        protectSocket: (Socket) -> Boolean,
        timeoutMs: Int = 8000
    ): VerificationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        AppLogger.i("Verifier", "Verifying tunnel connectivity to ${node.name} (${node.server}:${node.port})")

        var tunnelSocket: Socket? = null
        try {
            val transport = ProxyTransportFactory.create(node)
            // Open a tunnel to a reliable lightweight IP probe host (e.g. 1.1.1.1:80 or 8.8.8.8:80 or check endpoint)
            tunnelSocket = transport.openTunnel(
                targetHost = "1.1.1.1",
                targetPort = 80,
                protectSocket = protectSocket,
                connectTimeoutMs = timeoutMs
            )

            val latency = System.currentTimeMillis() - startTime
            AppLogger.i("Verifier", "Proxy handshake succeeded in ${latency}ms")

            // Send lightweight HTTP probe through the tunnel
            tunnelSocket.soTimeout = 4000
            val out = tunnelSocket.getOutputStream()
            val probeReq = "HEAD / HTTP/1.1\r\nHost: 1.1.1.1\r\nUser-Agent: AjazTiktok/1.0\r\nConnection: close\r\n\r\n"
            out.write(probeReq.toByteArray(Charsets.US_ASCII))
            out.flush()

            val reader = BufferedReader(InputStreamReader(tunnelSocket.getInputStream(), Charsets.US_ASCII))
            val statusLine = reader.readLine()
            val totalLatency = System.currentTimeMillis() - startTime

            if (statusLine != null && (statusLine.startsWith("HTTP/") || statusLine.contains("200") || statusLine.contains("301") || statusLine.contains("400"))) {
                AppLogger.i("Verifier", "Exit route verified: Probe responded '$statusLine' in ${totalLatency}ms")
                return@withContext VerificationResult.Success(
                    exitIp = node.server,
                    latencyMs = totalLatency
                )
            } else {
                AppLogger.w("Verifier", "Tunnel connected but probe returned non-standard response")
                return@withContext VerificationResult.Success(
                    exitIp = node.server,
                    latencyMs = totalLatency
                )
            }
        } catch (e: java.net.ConnectException) {
            AppLogger.e("Verifier", "Connection refused: ${e.message}")
            return@withContext VerificationResult.Failure(
                reason = "Remote server ${node.server}:${node.port} refused connection",
                recoverySuggestion = "Verify the server port or select a different server from the profile list"
            )
        } catch (e: java.net.SocketTimeoutException) {
            AppLogger.e("Verifier", "Connection timed out to ${node.server}:${node.port}")
            return@withContext VerificationResult.Failure(
                reason = "Connection timed out after ${timeoutMs / 1000}s",
                recoverySuggestion = "Server is unresponsive. Please check your data connection or choose a lower-latency node"
            )
        } catch (e: java.net.UnknownHostException) {
            AppLogger.e("Verifier", "Unknown host: ${node.server}")
            return@withContext VerificationResult.Failure(
                reason = "Cannot resolve server domain '${node.server}'",
                recoverySuggestion = "Check DNS settings or verify your active mobile/Wi-Fi data"
            )
        } catch (e: IllegalArgumentException) {
            AppLogger.e("Verifier", "Configuration error: ${e.message}")
            return@withContext VerificationResult.Failure(
                reason = e.message ?: "Unsupported proxy configuration",
                recoverySuggestion = "Please select a supported SOCKS5, HTTP, Shadowsocks, or Trojan provider"
            )
        } catch (e: Exception) {
            AppLogger.e("Verifier", "Handshake failed: ${e.message}")
            return@withContext VerificationResult.Failure(
                reason = "Handshake error: ${e.localizedMessage ?: "Failed to establish tunnel"}",
                recoverySuggestion = "Check server credentials and firewall status"
            )
        } finally {
            try {
                tunnelSocket?.close()
            } catch (_: Exception) {}
        }
    }
}
