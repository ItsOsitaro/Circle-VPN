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

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var tunnelThread: Thread? = null
    private var proxyServer: LocalHttpProxyServer? = null

    companion object {
        const val ACTION_CONNECT = "com.example.v2tunnel.CONNECT"
        const val ACTION_DISCONNECT = "com.example.v2tunnel.DISCONNECT"
        const val EXTRA_SERVER_NAME = "server_name"
        const val EXTRA_SERVER_HOST = "server_host"
        const val NOTIFICATION_ID = 4224
        const val CHANNEL_ID = "v2tunnel_vpn_channel"
        private const val PROXY_PORT = 10800
        
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

            // Establish the local Virtual Network Interface
            val builder = Builder()
                .setSession("Circle VPN")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0) // Route all traffic to the TUN interface to make the HTTP proxy default
                .addRoute("::", 0)       // Route all IPv6 traffic to the TUN interface
                .allowBypass()

            VpnDiagnosticManager.addLog(
                type = "SYSTEM",
                description = "Default Routing Active",
                details = "VPN route configured for 0.0.0.0/0 (all traffic). System HTTP proxy configuration will direct compatible app traffic here.",
                status = "TUNNELED"
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
                builder.setHttpProxy(android.net.ProxyInfo.buildDirectProxy("127.0.0.1", PROXY_PORT))
                VpnDiagnosticManager.addLog(
                    type = "SYSTEM",
                    description = "Applied Direct Proxy Info",
                    details = "Proxy set to 127.0.0.1:$PROXY_PORT via Android API 29+. Note: Apps ignoring system proxies (e.g., direct TCP/UDP sockets, games, non-standard HTTP clients) will bypass this.",
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
        tunnelThread = Thread({
            val fd = vpnInterface ?: return@Thread
            val inputStream = java.io.FileInputStream(fd.fileDescriptor)
            val buffer = ByteArray(32768)
            var droppedPacketCount = 0
            var lastLogTime = System.currentTimeMillis()
            
            try {
                while (isRunning) {
                    val read = inputStream.read(buffer)
                    if (read <= 0) {
                        Thread.sleep(10)
                        continue
                    }
                    
                    droppedPacketCount++
                    val now = System.currentTimeMillis()
                    
                    // Parse IPv4 packets captured inside the TUN interface
                    val versionAndIhl = buffer[0].toInt() and 0xFF
                    val version = versionAndIhl shr 4
                    val ihl = (versionAndIhl and 0x0F) * 4
                    
                    if (version == 4 && read >= ihl) {
                        val protocolNum = buffer[9].toInt() and 0xFF
                        val protocol = when (protocolNum) {
                            1 -> "ICMP"
                            6 -> "TCP"
                            17 -> "UDP"
                            else -> "Proto($protocolNum)"
                        }
                        
                        // Parse IPs
                        val srcIp = "${buffer[12].toInt() and 0xFF}.${buffer[13].toInt() and 0xFF}.${buffer[14].toInt() and 0xFF}.${buffer[15].toInt() and 0xFF}"
                        val dstIp = "${buffer[16].toInt() and 0xFF}.${buffer[17].toInt() and 0xFF}.${buffer[18].toInt() and 0xFF}.${buffer[19].toInt() and 0xFF}"
                        
                        var portsInfo = ""
                        if ((protocolNum == 6 || protocolNum == 17) && read >= ihl + 4) {
                            val srcPort = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
                            val dstPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
                            portsInfo = " [Port: $srcPort -> $dstPort]"
                        }
                        
                        // Log only periodically to avoid flooding the UI, but show that we are dropping global traffic
                        if (now - lastLogTime > 2000) {
                            VpnDiagnosticManager.addLog(
                                type = "TUN_ROUTING_ERROR",
                                description = "Packet Dropped (No TCP/IP Stack)",
                                details = "Global traffic is routed to TUN but we don't have tun2socks to forward it.\nDropped $droppedPacketCount packets recently.\nLatest: $protocol: $srcIp$portsInfo -> $dstIp ($read Bytes)",
                                status = "DROPPED"
                            )
                            droppedPacketCount = 0
                            lastLogTime = now
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

    private fun stopVpn() {
        Log.i("MyVpnService", "Stopping VPN")
        isRunning = false
        _isVpnActive = false
        _activeServerName = "None"

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

        stopForeground(true)
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
