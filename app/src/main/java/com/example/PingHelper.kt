package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object PingHelper {

    /**
     * Measures the TCP handshake connection latency (ping) to a specific host and port.
     * Returns the connection time in milliseconds, or -1 if the host is unreachable/timed out.
     */
    suspend fun measurePing(host: String, port: Int, timeoutMs: Int = 1500): Int = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            val latency = (System.currentTimeMillis() - startTime).toInt()
            latency
        } catch (e: Exception) {
            -1
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                // ignore close errors
            }
        }
    }

    /**
     * Runs TCP latency checks in parallel across all configurations.
     * Returns a map of rawUri to ping latency in ms.
     */
    suspend fun testAllServers(configs: List<V2RayConfig>): Map<String, Int> = withContext(Dispatchers.IO) {
        configs.map { config ->
            async {
                val ping = measurePing(config.address, config.port)
                config.rawUri to ping
            }
        }.awaitAll().toMap()
    }
}
