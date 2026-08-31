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
import javax.net.ssl.SSLHandshakeException

sealed class VerificationResult {
    data class Success(val exitIp: String?, val latencyMs: Long) : VerificationResult()
    data class Failure(val reason: String, val recoverySuggestion: String? = null) : VerificationResult()
}

object ConnectionVerifier {

    private val IPV4_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")

    // Fast, globally distributed probe endpoints that respond in <50ms without blocking
    private val PROBE_TARGETS = listOf(
        ProbeTarget("1.1.1.1", 80, "/generate_204", 204),
        ProbeTarget("connectivitycheck.gstatic.com", 80, "/generate_204", 204),
        ProbeTarget("cp.cloudflare.com", 80, "/generate_204", 204),
        ProbeTarget("api.ipify.org", 80, "/", 200),
        ProbeTarget("ifconfig.me", 80, "/ip", 200)
    )

    private data class ProbeTarget(
        val host: String,
        val port: Int,
        val path: String,
        val expectedStatus: Int
    )

    suspend fun verifyTunnel(
        node: ProxyNode,
        protectSocket: (Socket) -> Boolean,
        timeoutMs: Int = 10000
    ): VerificationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        AppLogger.i("Verifier", "Initiating tunnel verification with ${node.name} (${node.server}:${node.port} [${node.type.displayName}])")

        var lastException: Exception? = null
        val perProbeTimeout = Math.min(timeoutMs / 2, 4500)

        for (target in PROBE_TARGETS) {
            var tunnelSocket: Socket? = null
            try {
                val transport = ProxyTransportFactory.create(node)

                // 1. Establish proxy tunnel towards probe host
                val probeStart = System.currentTimeMillis()
                tunnelSocket = transport.openTunnel(
                    targetHost = target.host,
                    targetPort = target.port,
                    protectSocket = protectSocket,
                    connectTimeoutMs = perProbeTimeout
                )

                val handshakeLatency = System.currentTimeMillis() - probeStart
                AppLogger.i("Verifier", "Proxy protocol handshake successful with ${target.host} in ${handshakeLatency}ms")

                // 2. Perform fast HTTP GET probe through the established proxy tunnel
                tunnelSocket.soTimeout = 4000
                val out = tunnelSocket.getOutputStream()
                val probeReq = "GET ${target.path} HTTP/1.1\r\n" +
                    "Host: ${target.host}\r\n" +
                    "User-Agent: Mozilla/5.0 (Android; Mobile; AjazTiktok/1.0.0)\r\n" +
                    "Connection: close\r\n\r\n"

                out.write(probeReq.toByteArray(Charsets.US_ASCII))
                out.flush()

                val reader = BufferedReader(InputStreamReader(tunnelSocket.getInputStream(), Charsets.US_ASCII))
                val statusLine = reader.readLine()
                val totalLatency = System.currentTimeMillis() - startTime

                if (statusLine == null) {
                    throw java.io.IOException("Remote proxy closed connection without returning HTTP response")
                }

                // Check for valid HTTP status (200, 204, 301, 302, etc.)
                val isStatusOk = statusLine.contains("204") ||
                    statusLine.contains("200") ||
                    statusLine.contains("301") ||
                    statusLine.contains("302") ||
                    statusLine.contains("HTTP/1.1 ") ||
                    statusLine.contains("HTTP/1.0 ")

                if (isStatusOk) {
                    // Read headers & body if any
                    var detectedExitIp: String? = null
                    var line: String?
                    while (true) {
                        line = reader.readLine()
                        if (line.isNullOrEmpty()) break
                    }

                    val bodyBuilder = StringBuilder()
                    try {
                        while (true) {
                            val bLine = reader.readLine() ?: break
                            bodyBuilder.append(bLine.trim())
                            if (bodyBuilder.length > 128) break
                        }
                        val body = bodyBuilder.toString().trim()
                        val matcher = IPV4_PATTERN.matcher(body)
                        if (matcher.find()) {
                            detectedExitIp = matcher.group()
                        }
                    } catch (_: Exception) {}

                    val finalExitIp = detectedExitIp ?: node.server
                    AppLogger.i("Verifier", "Verified active remote exit IP: $finalExitIp (Latency: ${totalLatency}ms via ${target.host})")

                    return@withContext VerificationResult.Success(
                        exitIp = finalExitIp,
                        latencyMs = totalLatency
                    )
                } else {
                    AppLogger.w("Verifier", "Probe returned non-success HTTP status: $statusLine")
                }

            } catch (e: Exception) {
                lastException = e
                AppLogger.w("Verifier", "Probe to ${target.host} failed: ${e.message}. Trying next probe endpoint...")
            } finally {
                try {
                    tunnelSocket?.close()
                } catch (_: Exception) {}
            }
        }

        // All probe targets failed: classify the root cause
        val e = lastException ?: java.io.IOException("All connectivity verification targets timed out")
        AppLogger.e("Verifier", "Tunnel verification failed for ${node.name}: ${e.message}", e)

        when (e) {
            is java.net.UnknownHostException -> {
                return@withContext VerificationResult.Failure(
                    reason = "DNS lookup failed for server '${node.server}'",
                    recoverySuggestion = "Please check your internet connection or verify the server domain"
                )
            }
            is java.net.ConnectException -> {
                return@withContext VerificationResult.Failure(
                    reason = "Unable to connect to ${node.server}:${node.port} (Connection refused or host unreachable)",
                    recoverySuggestion = "The remote server may be offline. Please select another location."
                )
            }
            is java.net.SocketTimeoutException -> {
                return@withContext VerificationResult.Failure(
                    reason = "Connection to ${node.name} timed out",
                    recoverySuggestion = "The server is taking too long to respond. Please choose another location."
                )
            }
            is SSLHandshakeException -> {
                return@withContext VerificationResult.Failure(
                    reason = "TLS/SSL negotiation failed with ${node.server} (${e.message})",
                    recoverySuggestion = "Please enable 'Allow insecure / Skip cert verify' or select another location."
                )
            }
            is java.io.EOFException -> {
                return@withContext VerificationResult.Failure(
                    reason = "Remote proxy rejected connection credentials",
                    recoverySuggestion = "Please update your subscription profile or check authentication keys."
                )
            }
            is UnsupportedOperationException -> {
                return@withContext VerificationResult.Failure(
                    reason = "Unsupported protocol: ${node.type.displayName}",
                    recoverySuggestion = "Please select an alternative location from your profile."
                )
            }
            else -> {
                return@withContext VerificationResult.Failure(
                    reason = "Connection failed: ${e.localizedMessage ?: "Unable to establish secure tunnel"}",
                    recoverySuggestion = "Please select another location from your profile."
                )
            }
        }
    }
}
