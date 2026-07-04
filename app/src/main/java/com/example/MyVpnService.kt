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

    companion object {
        const val ACTION_CONNECT = "com.example.v2tunnel.CONNECT"
        const val ACTION_DISCONNECT = "com.example.v2tunnel.DISCONNECT"
        const val EXTRA_SERVER_NAME = "server_name"
        const val EXTRA_SERVER_HOST = "server_host"
        const val NOTIFICATION_ID = 4224
        const val CHANNEL_ID = "v2tunnel_vpn_channel"
        
        private var _activeServerName = "None"
        val activeServerName: String get() = _activeServerName
        
        private var _isVpnActive = false
        val isVpnActive: Boolean get() = _isVpnActive
    }

    override fun onCreate() {
        super.onCreate()
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

        _activeServerName = serverName
        _isVpnActive = true

        startVpn(serverName, serverHost)
        return START_STICKY
    }

    private fun startVpn(serverName: String, serverHost: String) {
        if (isRunning) {
            stopVpn()
        }

        isRunning = true
        Log.i("MyVpnService", "Starting VPN for server: $serverName ($serverHost)")

        try {
            // Establish the local Virtual Network Interface
            val builder = Builder()
                .setSession("V2Tunnel")
                .addAddress("10.0.0.2", 24)
                .addDnsServer("8.8.8.8")
                .addRoute("10.0.0.0", 24)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            vpnInterface = builder.establish()

            // Create notification and run in foreground
            val notification = createNotification(serverName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

        } catch (e: Exception) {
            Log.e("MyVpnService", "Failed to start VPN interface", e)
            stopVpn()
        }
    }

    private fun stopVpn() {
        Log.i("MyVpnService", "Stopping VPN")
        isRunning = false
        _isVpnActive = false
        _activeServerName = "None"
        
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
                "V2Tunnel VPN Status",
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
            .setContentTitle("V2Tunnel Connected")
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
