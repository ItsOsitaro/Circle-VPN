package com.example

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiagnosticLog(
    val id: Long,
    val timestamp: Long,
    val type: String,      // "TUN" (packet capture), "PROXY" (HTTP proxy request), "SYSTEM" (VPN Lifecycle or Route diagnostics)
    val description: String,
    val details: String,
    val status: String     // "BYPASSED", "TUNNELED", "FAILED", "INFO"
)

object VpnDiagnosticManager {
    private val _logs = MutableStateFlow<List<DiagnosticLog>>(emptyList())
    val logs: StateFlow<List<DiagnosticLog>> = _logs.asStateFlow()
    private var logIdCounter = 0L

    @Synchronized
    fun addLog(type: String, description: String, details: String, status: String) {
        val newLog = DiagnosticLog(
            id = ++logIdCounter,
            timestamp = System.currentTimeMillis(),
            type = type,
            description = description,
            details = details,
            status = status
        )
        val current = _logs.value.toMutableList()
        current.add(0, newLog) // Add to the top (newest first)
        
        // Limit log size to 150 items to prevent high memory usage
        if (current.size > 150) {
            current.removeAt(current.lastIndex)
        }
        _logs.value = current
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
