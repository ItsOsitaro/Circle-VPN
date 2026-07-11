package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var tunnelThread: Thread? = null
    private var proxyServer: LocalHttpProxyServer? = null
    private var sessionExecutor: java.util.concurrent.ExecutorService? = null

    // User-space TCP/IP Stack fields
    private val writeLock = Any()
    private val sessionLock = Any()
    private val udpSessions = ConcurrentHashMap<String, UdpSession>()
    private val tcpSessions = ConcurrentHashMap<String, TcpSession>()

    enum class TcpState {
        SYN_RECEIVED,
        ESTABLISHED,
        CLOSED
    }

    class UdpSession(
        val srcIp: String, val srcPort: Int,
        val dstIp: String, val dstPort: Int,
        val socket: DatagramSocket,
        var lastActivity: Long
    )

    class TcpSession(
        val srcIp: String, val srcPort: Int,
        val dstIp: String, val dstPort: Int,
        var clientSeq: Long,
        var serverSeq: Long,
        var socket: Socket?,
        var lastActivity: Long,
        var state: TcpState
    )

    companion object {
        const val ACTION_CONNECT = "com.example.v2tunnel.CONNECT"
        const val ACTION_DISCONNECT = "com.example.v2tunnel.DISCONNECT"
        const val EXTRA_SERVER_NAME = "server_name"
        const val EXTRA_SERVER_HOST = "server_host"
        const val NOTIFICATION_ID = 4224
        const val CHANNEL_ID = "v2tunnel_vpn_channel"
        private const val PROXY_PORT = 10800
        const val MAX_CONCURRENT_SESSIONS = 200
        
        private var _activeServerName = "None"
        val activeServerName: String get() = _activeServerName
        
        private var _isVpnActive = false
        val isVpnActive: Boolean get() = _isVpnActive

        private var instance: MyVpnService? = null
        fun protectSocket(socket: java.net.Socket): Boolean {
            return instance?.protect(socket) ?: false
        }

        fun protectSocket(socket: java.net.DatagramSocket): Boolean {
            return instance?.protect(socket) ?: false
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()

        // Register UncaughtExceptionHandler to catch and log any crash in threads
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("MyVpnService", "Uncaught exception in thread ${thread.name}", throwable)
            try {
                VpnDiagnosticManager.addLog(
                    type = "CRITICAL_ERROR",
                    description = "Uncaught Thread Exception",
                    details = "Thread: ${thread.name}\nError: ${throwable.message}\nStacktrace:\n${Log.getStackTraceString(throwable)}",
                    status = "FAILED"
                )
            } catch (e: Exception) {
                // Ignore secondary errors during diagnostic logging
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_DISCONNECT) {
            stopVpn()
            return START_NOT_STICKY
        }

        val serverName = intent?.getStringExtra(EXTRA_SERVER_NAME) ?: "Default Server"
        val serverHost = intent?.getStringExtra(EXTRA_SERVER_HOST) ?: "unknown"
        val serverPort = intent?.getIntExtra("server_port", 443) ?: 443
        val serverProtocol = intent?.getStringExtra("server_protocol") ?: "trojan"
        val serverUuid = intent?.getStringExtra("server_uuid") ?: ""
        val serverTls = intent?.getBooleanExtra("server_tls", false) ?: false
        val serverSni = intent?.getStringExtra("server_sni")
        val serverMethod = intent?.getStringExtra("server_method")

        _activeServerName = serverName
        _isVpnActive = true

        startVpn(
            serverName = serverName,
            serverHost = serverHost,
            serverPort = serverPort,
            serverProtocol = serverProtocol,
            serverUuid = serverUuid,
            serverTls = serverTls,
            serverSni = serverSni,
            serverMethod = serverMethod
        )
        return START_STICKY
    }

    private fun startVpn(
        serverName: String,
        serverHost: String,
        serverPort: Int,
        serverProtocol: String,
        serverUuid: String,
        serverTls: Boolean,
        serverSni: String?,
        serverMethod: String?
    ) {
        if (isRunning) {
            stopVpn()
        }

        isRunning = true
        Log.i("MyVpnService", "Starting VPN for server: $serverName ($serverHost:$serverPort)")
        
        VpnDiagnosticManager.clear()
        VpnDiagnosticManager.addLog(
            type = "SYSTEM",
            description = "Starting VPN Core Connection",
            details = "Selected Server: $serverName ($serverHost:$serverPort)\nProtocol: $serverProtocol",
            status = "INFO"
        )

        try {
            // Start local HTTP/S Proxy Server
            proxyServer = LocalHttpProxyServer(
                port = PROXY_PORT,
                serverHost = serverHost,
                serverPort = serverPort,
                serverProtocol = serverProtocol,
                serverUuid = serverUuid,
                serverTls = serverTls,
                serverSni = serverSni,
                serverMethod = serverMethod
            ).apply {
                start()
            }
            
            VpnDiagnosticManager.addLog(
                type = "SYSTEM",
                description = "HTTP Proxy Started",
                details = "Listening locally on 127.0.0.1:$PROXY_PORT. System HTTP proxy configuration will direct compatible app traffic here.",
                status = "INFO"
            )

            // Initialize dynamic thread pool for sessions
            sessionExecutor = java.util.concurrent.Executors.newCachedThreadPool()

            // Establish the local Virtual Network Interface
            val builder = Builder()
                .setSession("Circle VPN")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0) // Route all IPv4 traffic to TUN
                .addDnsServer("1.1.1.1") // Configure DNS servers so system uses them
                .addDnsServer("8.8.8.8")
                .allowBypass()

            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.e("MyVpnService", "Failed to disallow application: $packageName", e)
            }

            VpnDiagnosticManager.addLog(
                type = "SYSTEM",
                description = "VPN Route and DNS Configuration",
                details = "VPN route configured for 0.0.0.0/0 (all traffic). System DNS set to 1.1.1.1 & 8.8.8.8. User-space TCP/IP stack will process raw packets.",
                status = "INFO"
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
                builder.setHttpProxy(android.net.ProxyInfo.buildDirectProxy("127.0.0.1", PROXY_PORT))
                VpnDiagnosticManager.addLog(
                    type = "SYSTEM",
                    description = "Applied Direct Proxy Info",
                    details = "Proxy set to 127.0.0.1:$PROXY_PORT via Android API 29+.",
                    status = "INFO"
                )
            }

            vpnInterface = builder.establish()
            VpnDiagnosticManager.addLog(
                type = "SYSTEM",
                description = "TUN Interface Established",
                details = "Interface address: 10.0.0.2/24",
                status = "TUNNELED"
            )

            // Run a background thread to drain the interface and keep routing active
            startTunnelLoop()

            // Create notification and run in foreground
            val notification = createNotification(serverName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

        } catch (e: Exception) {
            Log.e("MyVpnService", "Failed to start VPN interface", e)
            VpnDiagnosticManager.addLog(
                type = "SYSTEM",
                description = "VPN Initialization Failed",
                details = e.message ?: e.toString(),
                status = "FAILED"
            )
            stopVpn()
        }
    }

    private fun startTunnelLoop() {
        startUdpCleanupTimer()
        startTcpCleanupTimer()

        tunnelThread = Thread({
            val fd = vpnInterface ?: return@Thread
            val inputStream = java.io.FileInputStream(fd.fileDescriptor)
            val outputStream = java.io.FileOutputStream(fd.fileDescriptor)
            val buffer = ByteArray(32768)
            
            try {
                while (isRunning) {
                    val read = inputStream.read(buffer)
                    if (read <= 0) {
                        Thread.sleep(10)
                        continue
                    }
                    
                    // Parse IPv4 packets captured inside the TUN interface
                    val versionAndIhl = buffer[0].toInt() and 0xFF
                    val version = versionAndIhl shr 4
                    val ihl = (versionAndIhl and 0x0F) * 4
                    
                    if (version == 4 && read >= ihl) {
                        val protocolNum = buffer[9].toInt() and 0xFF
                        val totalLength = ((buffer[2].toInt() and 0xFF) shl 8) or (buffer[3].toInt() and 0xFF)
                        
                        // Parse IPs
                        val srcIp = "${buffer[12].toInt() and 0xFF}.${buffer[13].toInt() and 0xFF}.${buffer[14].toInt() and 0xFF}.${buffer[15].toInt() and 0xFF}"
                        val dstIp = "${buffer[16].toInt() and 0xFF}.${buffer[17].toInt() and 0xFF}.${buffer[18].toInt() and 0xFF}.${buffer[19].toInt() and 0xFF}"
                        
                        // Ignore any traffic going to/from local loopback to prevent loopback interception
                        if (srcIp == "127.0.0.1" || dstIp == "127.0.0.1") {
                            continue
                        }

                        if (protocolNum == 17 && read >= ihl + 8) { // UDP
                            val srcPort = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
                            val dstPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
                            val udpLen = ((buffer[ihl + 4].toInt() and 0xFF) shl 8) or (buffer[ihl + 5].toInt() and 0xFF)
                            
                            val payloadLength = udpLen - 8
                            if (payloadLength > 0 && ihl + 8 + payloadLength <= read) {
                                val payload = ByteArray(payloadLength)
                                System.arraycopy(buffer, ihl + 8, payload, 0, payloadLength)
                                
                                handleIncomingUdpPacket(
                                    srcIp = srcIp, srcPort = srcPort,
                                    dstIp = dstIp, dstPort = dstPort,
                                    payload = payload,
                                    outputStream = outputStream
                                )
                            }
                        } else if (protocolNum == 6 && read >= ihl + 20) { // TCP
                            val srcPort = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
                            val dstPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
                            
                            val seq = ((buffer[ihl + 4].toLong() and 0xFF) shl 24) or 
                                      ((buffer[ihl + 5].toLong() and 0xFF) shl 16) or 
                                      ((buffer[ihl + 6].toLong() and 0xFF) shl 8) or 
                                      (buffer[ihl + 7].toLong() and 0xFF)
                                      
                            val ack = ((buffer[ihl + 8].toLong() and 0xFF) shl 24) or 
                                      ((buffer[ihl + 9].toLong() and 0xFF) shl 16) or 
                                      ((buffer[ihl + 10].toLong() and 0xFF) shl 8) or 
                                      (buffer[ihl + 11].toLong() and 0xFF)
                                      
                            val dataOffset = ((buffer[ihl + 12].toInt() and 0xF0) shr 4) * 4
                            val flags = buffer[ihl + 13].toInt() and 0xFF
                            
                            val headerLength = ihl + dataOffset
                            val payloadLength = totalLength - ihl - dataOffset
                            
                            if (payloadLength < 0 || headerLength + payloadLength > read || headerLength < 0) {
                                continue
                            }
                            
                            val payload = if (payloadLength > 0) {
                                val p = ByteArray(payloadLength)
                                System.arraycopy(buffer, headerLength, p, 0, payloadLength)
                                p
                            } else {
                                null
                            }
                            
                            handleIncomingTcpPacket(
                                srcIp = srcIp, srcPort = srcPort,
                                dstIp = dstIp, dstPort = dstPort,
                                seq = seq, ack = ack,
                                flags = flags,
                                payload = payload,
                                outputStream = outputStream
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MyVpnService", "Error in tunnel loop", e)
                VpnDiagnosticManager.addLog(
                    type = "SYSTEM_ERROR",
                    description = "Tunnel Loop Error",
                    details = "Packet forwarding loop crashed: ${e.message}",
                    status = "FAILED"
                )
            }
        }, "VpnTunnelThread").apply {
            start()
        }
    }

    private fun handleIncomingUdpPacket(
        srcIp: String, srcPort: Int,
        dstIp: String, dstPort: Int,
        payload: ByteArray,
        outputStream: java.io.FileOutputStream
    ) {
        val key = "$srcIp:$srcPort->$dstIp:$dstPort"
        var session = udpSessions[key]
        if (session == null || session.socket.isClosed) {
            synchronized(sessionLock) {
                session = udpSessions[key]
                if (session == null || session!!.socket.isClosed) {
                    val currentTotal = tcpSessions.size + udpSessions.size
                    if (currentTotal >= MAX_CONCURRENT_SESSIONS) {
                        VpnDiagnosticManager.addLog(
                            type = "TUN_ROUTING_LIMIT",
                            description = "UDP Packet Dropped",
                            details = "Active sessions ($currentTotal) exceeded MAX_CONCURRENT_SESSIONS ($MAX_CONCURRENT_SESSIONS). Dropping UDP packet from $srcIp:$srcPort to $dstIp:$dstPort.",
                            status = "DROPPED"
                        )
                        return
                    }

                    try {
                        val socket = java.net.DatagramSocket()
                        protect(socket)
                        socket.soTimeout = 5000 // 5 seconds timeout
                        
                        val newSession = UdpSession(
                            srcIp = srcIp, srcPort = srcPort,
                            dstIp = dstIp, dstPort = dstPort,
                            socket = socket,
                            lastActivity = System.currentTimeMillis()
                        )
                        udpSessions[key] = newSession
                        session = newSession
                    } catch (e: Exception) {
                        Log.e("MyVpnService", "Failed to setup UDP session for $key", e)
                        return
                    }
                }
            }
            
            val finalSession = session ?: return
            val socket = finalSession.socket
            
            // Submit to the executor outside of sessionLock
            try {
                sessionExecutor?.execute {
                    val originalName = Thread.currentThread().name
                    Thread.currentThread().name = "UdpListener-$key"
                    try {
                        val recvBuffer = ByteArray(4096)
                        val recvPacket = java.net.DatagramPacket(recvBuffer, recvBuffer.size)
                        while (isRunning && !socket.isClosed) {
                            socket.receive(recvPacket)
                            val responsePayload = ByteArray(recvPacket.length)
                            System.arraycopy(recvBuffer, 0, responsePayload, 0, recvPacket.length)
                            
                            // Build raw IP/UDP response
                            val responsePacket = buildUdpPacket(
                                srcIp = dstIp,
                                srcPort = dstPort,
                                dstIp = srcIp,
                                dstPort = srcPort,
                                payload = responsePayload
                            )
                            
                            synchronized(writeLock) {
                                outputStream.write(responsePacket)
                                outputStream.flush()
                            }
                            
                            finalSession.lastActivity = System.currentTimeMillis()
                        }
                    } catch (e: Exception) {
                        // Timeout or socket closed
                    } finally {
                        socket.close()
                        udpSessions.remove(key)
                        Thread.currentThread().name = originalName
                    }
                }
            } catch (e: java.util.concurrent.RejectedExecutionException) {
                Log.e("MyVpnService", "Rejected UDP session task for $key", e)
                socket.close()
                udpSessions.remove(key)
            }
        }
        
        // Send the incoming payload
        try {
            session.lastActivity = System.currentTimeMillis()
            val targetAddr = java.net.InetAddress.getByName(dstIp)
            val sendPacket = java.net.DatagramPacket(payload, payload.size, targetAddr, dstPort)
            session.socket.send(sendPacket)
        } catch (e: Exception) {
            Log.e("MyVpnService", "Failed to send UDP packet to $dstIp:$dstPort", e)
        }
    }

    private fun handleIncomingTcpPacket(
        srcIp: String, srcPort: Int,
        dstIp: String, dstPort: Int,
        seq: Long, ack: Long,
        flags: Int,
        payload: ByteArray?,
        outputStream: java.io.FileOutputStream
    ) {
        val key = "$srcIp:$srcPort->$dstIp:$dstPort"
        val isSyn = (flags and 0x02) != 0
        val isAck = (flags and 0x10) != 0
        val isFin = (flags and 0x01) != 0
        val isRst = (flags and 0x04) != 0
        
        var session = tcpSessions[key]
        
        if (isSyn) {
            var rejected = false
            var rstPacket: ByteArray? = null
            var responsePacket: ByteArray? = null
            var newSession: TcpSession? = null

            synchronized(sessionLock) {
                // Clean up old session if any
                session?.let {
                    try { it.socket?.close() } catch (e: Exception) {}
                    tcpSessions.remove(key)
                }
                
                // Check session limit
                val currentTotal = tcpSessions.size + udpSessions.size
                if (currentTotal >= MAX_CONCURRENT_SESSIONS) {
                    VpnDiagnosticManager.addLog(
                        type = "TUN_ROUTING_LIMIT",
                        description = "TCP Connection Rejected",
                        details = "Active sessions ($currentTotal) exceeded MAX_CONCURRENT_SESSIONS ($MAX_CONCURRENT_SESSIONS). Rejecting TCP from $srcIp:$srcPort to $dstIp:$dstPort.",
                        status = "DROPPED"
                    )
                    
                    // Immediately build a RST back
                    rstPacket = buildTcpPacket(
                        srcIp = dstIp, srcPort = dstPort,
                        dstIp = srcIp, dstPort = srcPort,
                        seq = 0L,
                        ack = seq + 1,
                        flags = 0x14 // RST | ACK
                    )
                    rejected = true
                } else {
                    // Respond SYN-ACK immediately to establish local TCP connection
                    val initialServerSeq = (0L..100000L).random()
                    responsePacket = buildTcpPacket(
                        srcIp = dstIp, srcPort = dstPort,
                        dstIp = srcIp, dstPort = srcPort,
                        seq = initialServerSeq,
                        ack = seq + 1,
                        flags = 0x12 // SYN | ACK
                    )
                    
                    newSession = TcpSession(
                        srcIp = srcIp, srcPort = srcPort,
                        dstIp = dstIp, dstPort = dstPort,
                        clientSeq = seq + 1,
                        serverSeq = initialServerSeq + 1,
                        socket = null,
                        lastActivity = System.currentTimeMillis(),
                        state = TcpState.SYN_RECEIVED
                    )
                    tcpSessions[key] = newSession!!
                }
            }

            if (rejected) {
                rstPacket?.let { packet ->
                    synchronized(writeLock) {
                        try {
                            outputStream.write(packet)
                            outputStream.flush()
                        } catch (e: Exception) {}
                    }
                }
                return
            }

            // Write SYN-ACK
            responsePacket?.let { packet ->
                synchronized(writeLock) {
                    try {
                        outputStream.write(packet)
                        outputStream.flush()
                    } catch (e: Exception) {}
                }
            }

            val finalSession = newSession ?: return
            
            // Connect to the remote server via V2Ray tunnel in background using executor
            try {
                sessionExecutor?.execute {
                    val originalName = Thread.currentThread().name
                    Thread.currentThread().name = "TcpTunnel-$key"
                    try {
                        // Reuse proxy configuration from proxyServer inside MyVpnService
                        val proxy = proxyServer ?: throw Exception("Proxy server not initialized")
                        val clientSocket = proxy.connectToRemoteProxyTunnel(dstIp, dstPort)
                        finalSession.socket = clientSocket
                        finalSession.state = TcpState.ESTABLISHED
                        
                        VpnDiagnosticManager.addLog(
                            type = "TUN_ROUTING",
                            description = "TCP Session Established",
                            details = "Established connection to $dstIp:$dstPort via V2Ray",
                            status = "TUNNELED"
                        )
                        
                        // Read from remote socket and write to TUN
                        val input = clientSocket.getInputStream()
                        val buffer = ByteArray(16384)
                        while (isRunning && finalSession.state == TcpState.ESTABLISHED && !clientSocket.isClosed) {
                            val readBytes = input.read(buffer)
                            if (readBytes == -1) break
                            if (readBytes > 0) {
                                val data = ByteArray(readBytes)
                                System.arraycopy(buffer, 0, data, 0, readBytes)
                                
                                val dataPacket = buildTcpPacket(
                                    srcIp = dstIp, srcPort = dstPort,
                                    dstIp = srcIp, dstPort = srcPort,
                                    seq = finalSession.serverSeq,
                                    ack = finalSession.clientSeq,
                                    flags = 0x18, // PSH | ACK
                                    payload = data
                                )
                                
                                synchronized(writeLock) {
                                    outputStream.write(dataPacket)
                                    outputStream.flush()
                                }
                                
                                finalSession.serverSeq += readBytes
                                finalSession.lastActivity = System.currentTimeMillis()
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("MyVpnService", "TCP tunnel closed or error for $key: ${e.message}")
                    } finally {
                        handleTcpTearDown(key, outputStream)
                        Thread.currentThread().name = originalName
                    }
                }
            } catch (e: java.util.concurrent.RejectedExecutionException) {
                Log.e("MyVpnService", "Rejected TCP session task for $key", e)
                handleTcpTearDown(key, outputStream)
            }
            
            return
        }
        
        if (session == null) {
            // If we get an ACK or data packet for a session we don't know, send a RST
            if (!isRst) {
                val rstPacket = buildTcpPacket(
                    srcIp = dstIp, srcPort = dstPort,
                    dstIp = srcIp, dstPort = srcPort,
                    seq = ack,
                    ack = seq,
                    flags = 0x04 // RST
                )
                synchronized(writeLock) {
                    try {
                        outputStream.write(rstPacket)
                        outputStream.flush()
                    } catch (e: Exception) {}
                }
            }
            return
        }
        
        session.lastActivity = System.currentTimeMillis()
        
        if (isRst || isFin) {
            handleTcpTearDown(key, outputStream)
            return
        }
        
        if (isAck) {
            if (session.state == TcpState.SYN_RECEIVED) {
                // Client completed handshake
                session.state = TcpState.ESTABLISHED
                session.clientSeq = seq
                return
            }
            
            // Update clientSeq
            if (seq > session.clientSeq) {
                session.clientSeq = seq
            }
        }
        
        // Process incoming data
        val payloadSize = payload?.size ?: 0
        if (payloadSize > 0 && payload != null) {
            // Send ACK back to client for the data received
            session.clientSeq = seq + payloadSize
            val ackPacket = buildTcpPacket(
                srcIp = dstIp, srcPort = dstPort,
                dstIp = srcIp, dstPort = srcPort,
                seq = session.serverSeq,
                ack = session.clientSeq,
                flags = 0x10 // ACK
            )
            
            synchronized(writeLock) {
                try {
                    outputStream.write(ackPacket)
                    outputStream.flush()
                } catch (e: Exception) {}
            }
            
            // Write data to remote socket
            try {
                val s = session.socket
                if (s != null && !s.isClosed) {
                    val out = s.getOutputStream()
                    out.write(payload)
                    out.flush()
                }
            } catch (e: Exception) {
                Log.e("MyVpnService", "Failed to write TCP data to remote proxy for $key", e)
                handleTcpTearDown(key, outputStream)
            }
        }
    }

    private fun handleTcpTearDown(key: String, outputStream: java.io.FileOutputStream) {
        try {
            val session = tcpSessions.remove(key) ?: return
            session.state = TcpState.CLOSED
            try {
                session.socket?.close()
            } catch (e: Exception) {}
            
            // Send FIN-ACK to client to close connection cleanly
            val finPacket = buildTcpPacket(
                srcIp = session.dstIp, srcPort = session.dstPort,
                dstIp = session.srcIp, dstPort = session.srcPort,
                seq = session.serverSeq,
                ack = session.clientSeq,
                flags = 0x11 // FIN | ACK
            )
            synchronized(writeLock) {
                try {
                    outputStream.write(finPacket)
                    outputStream.flush()
                } catch (e: Exception) {}
            }
        } catch (t: Throwable) {
            Log.e("MyVpnService", "Error in handleTcpTearDown for $key", t)
        }
    }

    private fun startUdpCleanupTimer() {
        Thread({
            while (isRunning) {
                try {
                    Thread.sleep(10000)
                    val now = System.currentTimeMillis()
                    val iterator = udpSessions.entries.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        if (now - entry.value.lastActivity > 15000) {
                            try {
                                entry.value.socket.close()
                            } catch (e: Exception) {}
                            iterator.remove()
                        }
                    }
                } catch (e: Exception) {}
            }
        }, "UdpCleanupThread").start()
    }

    private fun startTcpCleanupTimer() {
        Thread({
            while (isRunning) {
                try {
                    Thread.sleep(15000)
                    val now = System.currentTimeMillis()
                    val iterator = tcpSessions.entries.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        if (now - entry.value.lastActivity > 60000) {
                            try {
                                entry.value.socket?.close()
                            } catch (e: Exception) {}
                            iterator.remove()
                        }
                    }
                } catch (e: Exception) {}
            }
        }, "TcpCleanupThread").start()
    }

    private fun buildUdpPacket(
        srcIp: String, srcPort: Int,
        dstIp: String, dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val totalLength = ipHeaderLen + udpHeaderLen + payload.size
        val packet = ByteArray(totalLength)
        
        // --- IP Header ---
        packet[0] = 0x45.toByte() // Version 4, IHL 5
        packet[1] = 0x00.toByte() // TOS
        
        packet[2] = ((totalLength shr 8) and 0xFF).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        
        val id = (0..65535).random()
        packet[4] = ((id shr 8) and 0xFF).toByte()
        packet[5] = (id and 0xFF).toByte()
        
        packet[6] = 0x40.toByte() // Don't Fragment
        packet[7] = 0x00.toByte()
        
        packet[8] = 64.toByte() // TTL
        packet[9] = 17.toByte() // Protocol (UDP)
        
        // Checksum initially 0
        packet[10] = 0x00.toByte()
        packet[11] = 0x00.toByte()
        
        val srcParts = srcIp.split(".")
        packet[12] = srcParts[0].toInt().toByte()
        packet[13] = srcParts[1].toInt().toByte()
        packet[14] = srcParts[2].toInt().toByte()
        packet[15] = srcParts[3].toInt().toByte()
        
        val dstParts = dstIp.split(".")
        packet[16] = dstParts[0].toInt().toByte()
        packet[17] = dstParts[1].toInt().toByte()
        packet[18] = dstParts[2].toInt().toByte()
        packet[19] = dstParts[3].toInt().toByte()
        
        val ipChecksum = calculateIpChecksum(packet, 0, ipHeaderLen)
        packet[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()
        
        // --- UDP Header ---
        val udpOffset = ipHeaderLen
        
        // Source Port
        packet[udpOffset] = ((srcPort shr 8) and 0xFF).toByte()
        packet[udpOffset + 1] = (srcPort and 0xFF).toByte()
        
        // Destination Port
        packet[udpOffset + 2] = ((dstPort shr 8) and 0xFF).toByte()
        packet[udpOffset + 3] = (dstPort and 0xFF).toByte()
        
        // UDP Length
        val udpLen = udpHeaderLen + payload.size
        packet[udpOffset + 4] = ((udpLen shr 8) and 0xFF).toByte()
        packet[udpOffset + 5] = (udpLen and 0xFF).toByte()
        
        // UDP Checksum (0x0000 is allowed in IPv4 UDP, indicating no checksum)
        packet[udpOffset + 6] = 0x00.toByte()
        packet[udpOffset + 7] = 0x00.toByte()
        
        // --- Payload ---
        System.arraycopy(payload, 0, packet, ipHeaderLen + udpHeaderLen, payload.size)
        
        return packet
    }

    private fun buildTcpPacket(
        srcIp: String, srcPort: Int,
        dstIp: String, dstPort: Int,
        seq: Long, ack: Long,
        flags: Int,
        payload: ByteArray? = null
    ): ByteArray {
        val ipHeaderLen = 20
        val tcpHeaderLen = 20
        val payloadSize = payload?.size ?: 0
        val tcpLength = tcpHeaderLen + payloadSize
        val totalLength = ipHeaderLen + tcpLength
        val packet = ByteArray(totalLength)
        
        // --- IP Header ---
        packet[0] = 0x45.toByte() // Version 4, IHL 5
        packet[1] = 0x00.toByte() // TOS
        
        packet[2] = ((totalLength shr 8) and 0xFF).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        
        val id = (0..65535).random()
        packet[4] = ((id shr 8) and 0xFF).toByte()
        packet[5] = (id and 0xFF).toByte()
        
        packet[6] = 0x40.toByte() // Don't Fragment
        packet[7] = 0x00.toByte()
        
        packet[8] = 64.toByte() // TTL
        packet[9] = 6.toByte() // Protocol (TCP)
        
        // Checksum initially 0
        packet[10] = 0x00.toByte()
        packet[11] = 0x00.toByte()
        
        val srcParts = srcIp.split(".")
        packet[12] = srcParts[0].toInt().toByte()
        packet[13] = srcParts[1].toInt().toByte()
        packet[14] = srcParts[2].toInt().toByte()
        packet[15] = srcParts[3].toInt().toByte()
        
        val dstParts = dstIp.split(".")
        packet[16] = dstParts[0].toInt().toByte()
        packet[17] = dstParts[1].toInt().toByte()
        packet[18] = dstParts[2].toInt().toByte()
        packet[19] = dstParts[3].toInt().toByte()
        
        val ipChecksum = calculateIpChecksum(packet, 0, ipHeaderLen)
        packet[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()
        
        // --- TCP Header ---
        val tcpOffset = ipHeaderLen
        
        // Source Port
        packet[tcpOffset] = ((srcPort shr 8) and 0xFF).toByte()
        packet[tcpOffset + 1] = (srcPort and 0xFF).toByte()
        
        // Destination Port
        packet[tcpOffset + 2] = ((dstPort shr 8) and 0xFF).toByte()
        packet[tcpOffset + 3] = (dstPort and 0xFF).toByte()
        
        // Sequence Number
        packet[tcpOffset + 4] = ((seq shr 24) and 0xFF).toByte()
        packet[tcpOffset + 5] = ((seq shr 16) and 0xFF).toByte()
        packet[tcpOffset + 6] = ((seq shr 8) and 0xFF).toByte()
        packet[tcpOffset + 7] = (seq and 0xFF).toByte()
        
        // Acknowledgment Number
        packet[tcpOffset + 8] = ((ack shr 24) and 0xFF).toByte()
        packet[tcpOffset + 9] = ((ack shr 16) and 0xFF).toByte()
        packet[tcpOffset + 10] = ((ack shr 8) and 0xFF).toByte()
        packet[tcpOffset + 11] = (ack and 0xFF).toByte()
        
        packet[tcpOffset + 12] = 0x50.toByte() // Data Offset = 5 (20 bytes)
        packet[tcpOffset + 13] = (flags and 0xFF).toByte()
        
        // Window Size (65535)
        packet[tcpOffset + 14] = 0xFF.toByte()
        packet[tcpOffset + 15] = 0xFF.toByte()
        
        // Checksum set to 0, will be calculated next
        packet[tcpOffset + 16] = 0x00.toByte()
        packet[tcpOffset + 17] = 0x00.toByte()
        
        // Urgent pointer
        packet[tcpOffset + 18] = 0x00.toByte()
        packet[tcpOffset + 19] = 0x00.toByte()
        
        // Payload
        if (payload != null && payloadSize > 0) {
            System.arraycopy(payload, 0, packet, ipHeaderLen + tcpHeaderLen, payloadSize)
        }
        
        // Calculate TCP Checksum
        val tcpChecksum = calculateTcpChecksum(packet, 0, tcpOffset, tcpLength)
        packet[tcpOffset + 16] = ((tcpChecksum shr 8) and 0xFF).toByte()
        packet[tcpOffset + 17] = (tcpChecksum and 0xFF).toByte()
        
        return packet
    }

    private fun calculateIpChecksum(header: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i < end) {
            val word = ((header[i].toInt() and 0xFF) shl 8) or (header[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv()) and 0xFFFF
    }

    private fun calculateTcpChecksum(
        packet: ByteArray,
        ipHeaderOffset: Int,
        tcpHeaderOffset: Int,
        tcpLength: Int
    ): Int {
        packet[tcpHeaderOffset + 16] = 0
        packet[tcpHeaderOffset + 17] = 0
        
        var sum = 0
        
        // Pseudo-header: Source IP (4 bytes)
        for (i in 0 until 4) {
            val word = ((packet[ipHeaderOffset + 12 + i * 2].toInt() and 0xFF) shl 8) or
                       (packet[ipHeaderOffset + 12 + i * 2 + 1].toInt() and 0xFF)
            sum += word
        }
        
        // Pseudo-header: Destination IP (4 bytes)
        for (i in 0 until 4) {
            val word = ((packet[ipHeaderOffset + 16 + i * 2].toInt() and 0xFF) shl 8) or
                       (packet[ipHeaderOffset + 16 + i * 2 + 1].toInt() and 0xFF)
            sum += word
        }
        
        // Pseudo-header: Protocol (6)
        sum += 6
        
        // Pseudo-header: TCP Length
        sum += tcpLength
        
        // TCP header + payload
        var i = tcpHeaderOffset
        val end = tcpHeaderOffset + tcpLength
        while (i < end - 1) {
            val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            val word = (packet[i].toInt() and 0xFF) shl 8
            sum += word
        }
        
        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv()) and 0xFFFF
    }

    private fun stopVpn() {
        Log.i("MyVpnService", "Stopping VPN")
        isRunning = false
        _isVpnActive = false
        _activeServerName = "None"

        try {
            for (session in udpSessions.values) {
                session.socket.close()
            }
            udpSessions.clear()
        } catch (e: Exception) {}

        try {
            for (session in tcpSessions.values) {
                session.socket?.close()
            }
            tcpSessions.clear()
        } catch (e: Exception) {}

        try {
            sessionExecutor?.shutdownNow()
            sessionExecutor = null
        } catch (e: Exception) {
            Log.e("MyVpnService", "Error shutting down session executor", e)
        }

        try {
            proxyServer?.stop()
            proxyServer = null
        } catch (e: Exception) {
            Log.e("MyVpnService", "Error stopping proxy server", e)
        }

        try {
            tunnelThread?.interrupt()
            tunnelThread = null
        } catch (e: Exception) {
            Log.e("MyVpnService", "Error interrupting tunnel thread", e)
        }
        
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e("MyVpnService", "Error closing VPN interface", e)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Circle VPN Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active VPN connection status"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(serverName: String): Notification {
        val stopIntent = Intent(this, MyVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 1, stopIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingMainIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Circle VPN Connected")
            .setContentText("Active server: $serverName")
            .setSmallIcon(android.R.drawable.ic_menu_share) // fallback icon
            .setContentIntent(pendingMainIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                pendingStopIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
