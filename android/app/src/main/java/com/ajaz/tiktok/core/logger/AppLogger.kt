package com.ajaz.tiktok.core.logger

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

enum class LogLevel(val label: String) {
    DEBUG("DEBUG"),
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR")
}

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

object AppLogger {
    private const val MAX_LOGS = 500
    private var sequence = 0L

    private val logDeque = ConcurrentLinkedDeque<LogEntry>()
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private val sanitizeRegex = Regex("(?i)(password|secret|uuid|token|key|pwd)[:=]\\s*([^\\s,;\"]+)")

    fun d(tag: String, message: String) {
        addLog(LogLevel.DEBUG, tag, message)
        Log.d("Ajaz×$tag", sanitize(message))
    }

    fun i(tag: String, message: String) {
        addLog(LogLevel.INFO, tag, message)
        Log.i("Ajaz×$tag", sanitize(message))
    }

    fun w(tag: String, message: String) {
        addLog(LogLevel.WARN, tag, message)
        Log.w("Ajaz×$tag", sanitize(message))
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val errText = if (throwable != null) "$message (${throwable.localizedMessage})" else message
        addLog(LogLevel.ERROR, tag, errText)
        Log.e("Ajaz×$tag", sanitize(errText), throwable)
    }

    private fun addLog(level: LogLevel, tag: String, rawMessage: String) {
        val sanitized = sanitize(rawMessage)
        val entry = LogEntry(
            id = ++sequence,
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = sanitized
        )

        logDeque.addLast(entry)
        while (logDeque.size > MAX_LOGS) {
            logDeque.pollFirst()
        }

        _logsFlow.value = logDeque.toList()
    }

    private fun sanitize(input: String): String {
        return sanitizeRegex.replace(input) { matchResult ->
            val key = matchResult.groupValues[1]
            "$key=***REDACTED***"
        }
    }

    fun clear() {
        logDeque.clear()
        _logsFlow.value = emptyList()
        i("Logger", "Diagnostic logs reset by user")
    }
}
