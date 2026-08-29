package com.ajaz.tiktok.core.network

import com.ajaz.tiktok.core.logger.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkClient {

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    data class DownloadResult(
        val isSuccess: Boolean,
        val content: String? = null,
        val contentType: String? = null,
        val subscriptionUserInfo: String? = null,
        val errorMessage: String? = null,
        val statusCode: Int = 0
    )

    suspend fun fetchSubscription(url: String): DownloadResult = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext DownloadResult(
                isSuccess = false,
                errorMessage = "Invalid URL protocol. Must start with http:// or https://"
            )
        }

        AppLogger.i("Network", "Initiating subscription request: ${maskUrl(cleanUrl)}")

        val request = Request.Builder()
            .url(cleanUrl)
            .header("User-Agent", "ClashMeta/1.18.0 ClashForAndroid/2.5.12 AjazTiktok/1.0.0")
            .header("Accept", "text/yaml, application/yaml, text/plain, application/x-yaml, text/x-yaml, */*")
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val code = response.code
            val contentType = response.header("Content-Type") ?: "text/plain"
            val subInfo = response.header("Subscription-Userinfo")

            if (!response.isSuccessful) {
                AppLogger.e("Network", "Subscription fetch failed with HTTP $code")
                return@withContext DownloadResult(
                    isSuccess = false,
                    statusCode = code,
                    errorMessage = "Server returned HTTP $code: ${response.message}"
                )
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                AppLogger.w("Network", "Server returned empty response body")
                return@withContext DownloadResult(
                    isSuccess = false,
                    statusCode = code,
                    errorMessage = "Downloaded configuration is empty"
                )
            }

            AppLogger.i("Network", "Successfully received ${body.length} bytes (type: $contentType)")
            return@withContext DownloadResult(
                isSuccess = true,
                content = body,
                contentType = contentType,
                subscriptionUserInfo = subInfo,
                statusCode = code
            )
        } catch (e: IOException) {
            AppLogger.e("Network", "Network I/O error during fetch: ${e.message}")
            return@withContext DownloadResult(
                isSuccess = false,
                errorMessage = "Connection failed: ${e.localizedMessage ?: "Unknown network error"}"
            )
        } catch (e: Exception) {
            AppLogger.e("Network", "Unexpected error during subscription fetch: ${e.message}")
            return@withContext DownloadResult(
                isSuccess = false,
                errorMessage = "Error: ${e.localizedMessage ?: "Failed to download configuration"}"
            )
        }
    }

    private fun maskUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}${uri.path}***"
        } catch (_: Exception) {
            "https://***"
        }
    }
}
