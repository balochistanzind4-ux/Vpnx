package com.ajaz.tiktok.core.network

import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.parser.ProxyNode
import com.ajaz.tiktok.core.transport.ProxyTransportFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.util.regex.Pattern

sealed class VerificationResult {
    data class Success(val exitIp: String?, val latencyMs: Long) : VerificationResult()
    data class Failure(val reason: String, val recoverySuggestion: String? = null) : VerificationResult()
}

object ConnectionVerifier {

    private val IPV4_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
    private val PROBE_TARGETS = listOf("api.ipify.org", "ifconfig.me", "icanhazip.com")

    suspend fun verifyTunnel(
        node: ProxyNode,
        protectSocket: (Socket) -> Boolean,
        timeoutMs: Int = 10000
    ): VerificationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        AppLogger.i("Verifier", "Initiating end-to-end tunnel verification with ${node.name} (${node.server}:${node.port})")

        var lastException: Exception? = null

        for (targetHost in PROBE_TARGETS) {
            var tunnelSocket: Socket? = null
            try {
                val transport = ProxyTransportFactory.create(node)

                // 1. Establish proxy tunnel towards public IP reflection host
                tunnelSocket = transport.openTunnel(
                    targetHost = targetHost,
                    targetPort = 80,
                    protectSocket = protectSocket,
                    connectTimeoutMs = timeoutMs
                )

                val handshakeLatency = System.currentTimeMillis() - startTime
                AppLogger.i("Verifier", "Proxy protocol handshake successful with $targetHost in ${handshakeLatency}ms")

                // 2. Perform HTTP GET probe through the established proxy tunnel to fetch real remote exit IP
                tunnelSocket.soTimeout = 5000
                val out = tunnelSocket.getOutputStream()
                val probeReq = "GET / HTTP/1.1\r\n" +
                    "Host: $targetHost\r\n" +
                    "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36\r\n" +
                    "Accept: text/plain\r\n" +
                    "Connection: close\r\n\r\n"

                out.write(probeReq.toByteArray(Charsets.US_ASCII))
                out.flush()

                val reader = BufferedReader(InputStreamReader(tunnelSocket.getInputStream(), Charsets.US_ASCII))
                val statusLine = reader.readLine()
                val totalLatency = System.currentTimeMillis() - startTime

                if (statusLine == null) {
                    throw java.io.IOException("Remote proxy closed connection without returning HTTP response")
                }

                // Read headers
                var line: String?
                while (true) {
                    line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                }

                // Read body
                val bodyBuilder = StringBuilder()
                while (true) {
                    val bLine = reader.readLine() ?: break
                    bodyBuilder.append(bLine.trim())
                }

                val body = bodyBuilder.toString().trim()
                val matcher = IPV4_PATTERN.matcher(body)
                val detectedExitIp = if (matcher.find()) {
                    matcher.group()
                } else {
                    node.server
                }

                AppLogger.i("Verifier", "Verified active remote exit IP: $detectedExitIp (Latency: ${totalLatency}ms)")

                return@withContext VerificationResult.Success(
                    exitIp = detectedExitIp,
                    latencyMs = totalLatency
                )

            } catch (e: Exception) {
                lastException = e
                AppLogger.w("Verifier", "Probe to $targetHost failed: ${e.message}. Trying next probe endpoint...")
            } finally {
                try {
                    tunnelSocket?.close()
                } catch (_: Exception) {}
            }
        }

        // All probe targets failed
        val e = lastException ?: java.io.IOException("All connectivity verification targets failed")
        AppLogger.e("Verifier", "Tunnel verification failed: ${e.message}", e)

        when (e) {
            is java.net.ConnectException -> {
                return@withContext VerificationResult.Failure(
                    reason = "Remote server refused connection at ${node.server}:${node.port}",
                    recoverySuggestion = "Verify the server port or select a different server from your profile"
                )
            }
            is java.net.SocketTimeoutException -> {
                return@withContext VerificationResult.Failure(
                    reason = "Connection timed out after ${timeoutMs / 1000}s",
                    recoverySuggestion = "Server is unresponsive. Please check your network connection or select another node"
                )
            }
            is java.net.UnknownHostException -> {
                return@withContext VerificationResult.Failure(
                    reason = "Cannot resolve server domain '${node.server}'",
                    recoverySuggestion = "Check your mobile/Wi-Fi connection or DNS settings"
                )
            }
            is UnsupportedOperationException -> {
                return@withContext VerificationResult.Failure(
                    reason = e.message ?: "Protocol not supported",
                    recoverySuggestion = "Please choose a VLESS, Trojan, Shadowsocks, SOCKS5, or HTTP node"
                )
            }
            else -> {
                return@withContext VerificationResult.Failure(
                    reason = "Handshake error: ${e.localizedMessage ?: "Failed to establish tunnel"}",
                    recoverySuggestion = "Check your credentials or select an alternative node"
                )
            }
        }
    }
}
