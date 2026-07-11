package com.example

import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen {
    object Login : Screen()
    object Dashboard : Screen()
}

enum class VpnStatus {
    DISCONNECTED,
    CONNECTING, // Testing servers and selecting the best one
    CONNECTED
}

class VpnViewModel : ViewModel() {

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Login)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _loginLoading = MutableStateFlow(false)
    val loginLoading: StateFlow<Boolean> = _loginLoading.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _vpnStatus = MutableStateFlow(VpnStatus.DISCONNECTED)
    val vpnStatus: StateFlow<VpnStatus> = _vpnStatus.asStateFlow()

    private val _configs = MutableStateFlow<List<V2RayConfig>>(emptyList())
    val configs: StateFlow<List<V2RayConfig>> = _configs.asStateFlow()

    private val _serverPings = MutableStateFlow<Map<String, Int>>(emptyMap())
    val serverPings: StateFlow<Map<String, Int>> = _serverPings.asStateFlow()

    private val _selectedServer = MutableStateFlow<V2RayConfig?>(null)
    val selectedServer: StateFlow<V2RayConfig?> = _selectedServer.asStateFlow()

    private val _livePing = MutableStateFlow(0)
    val livePing: StateFlow<Int> = _livePing.asStateFlow()

    private val _liveSpeedDownload = MutableStateFlow(0f) // KB/s
    val liveSpeedDownload: StateFlow<Float> = _liveSpeedDownload.asStateFlow()

    private val _speedHistory = MutableStateFlow<List<Float>>(List(20) { 0f })
    val speedHistory: StateFlow<List<Float>> = _speedHistory.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()

    private var trafficMonitorJob: Job? = null
    private var lastRxBytes = 0L

    fun init(context: Context) {
        val savedUrl = LocalStorage.getSubscriptionUrl(context)
        val savedConfigs = LocalStorage.getConfigs(context)

        if (!savedUrl.isNullOrEmpty() && savedConfigs.isNotEmpty()) {
            _configs.value = savedConfigs
            _currentScreen.value = Screen.Dashboard
            
            // Check if VPN is already running in background
            if (MyVpnService.isVpnActive) {
                _vpnStatus.value = VpnStatus.CONNECTED
                val activeName = MyVpnService.activeServerName
                _selectedServer.value = savedConfigs.find { it.remarks == activeName } ?: savedConfigs.firstOrNull()
                startTrafficMonitoring(context)
            }
        }
    }

    fun loginWithSubscription(context: Context, url: String) {
        if (url.trim().isEmpty()) {
            _loginError.value = "لطفا لینک سابسکرایب را وارد کنید"
            return
        }

        _loginLoading.value = true
        _loginError.value = null

        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    V2RayConfigParser.fetchAndParseSubscription(url.trim())
                }

                if (parsed.isEmpty()) {
                    _loginError.value = "لینک نامعتبر است یا هیچ کانفیگی یافت نشد"
                    _loginLoading.value = false
                    return@launch
                }

                // Store in memory & persistent storage
                _configs.value = parsed
                LocalStorage.saveSubscriptionUrl(context, url.trim())
                LocalStorage.saveConfigs(context, parsed)

                // Navigate to dashboard
                _currentScreen.value = Screen.Dashboard
                _loginLoading.value = false
            } catch (e: Exception) {
                Log.e("VpnViewModel", "Error fetching subscription", e)
                _loginError.value = "لینک نامعتبر است"
                _loginLoading.value = false
            }
        }
    }

    fun toggleVpnConnection(context: Context) {
        if (_vpnStatus.value == VpnStatus.CONNECTED) {
            disconnectVpn(context)
        } else {
            connectVpn(context)
        }
    }

    private fun connectVpn(context: Context) {
        if (_configs.value.isEmpty()) return

        _vpnStatus.value = VpnStatus.CONNECTING
        _selectedServer.value = null

        viewModelScope.launch {
            // Step 1: Run parallel latency testing on all configs
            val pings = PingHelper.testAllServers(_configs.value)
            _serverPings.value = pings

            // Step 2: Find the config with lowest ping (which is > 0)
            // Only VLESS and Trojan are supported in the pure-Java proxy tunnel
            val supportedConfigs = _configs.value.filter { 
                it.protocol.lowercase() == "vless" || it.protocol.lowercase() == "trojan" 
            }
            
            val bestConfig = supportedConfigs
                .filter { (pings[it.rawUri] ?: -1) > 0 }
                .minByOrNull { pings[it.rawUri] ?: Int.MAX_VALUE }
                ?: supportedConfigs.firstOrNull() // fallback

            if (bestConfig == null) {
                _vpnStatus.value = VpnStatus.DISCONNECTED
                VpnDiagnosticManager.addLog(
                    type = "SYSTEM_ERROR",
                    description = "No Supported Server",
                    details = "Could not find any working VLESS or Trojan servers. VMess and Shadowsocks are not supported by the local Java proxy.",
                    status = "FAILED"
                )
                return@launch
            }

            _selectedServer.value = bestConfig
            val activePing = pings[bestConfig.rawUri] ?: 0
            _livePing.value = if (activePing > 0) activePing else 120

            // Step 3: Trigger Android VpnService
            // Securely resolve the server domain name to its IP address before turning on the VPN
            // This prevents DNS resolution failures or hijacking once the VPN routes capture everything.
            val resolvedHost = try {
                withContext(Dispatchers.IO) {
                    java.net.InetAddress.getByName(bestConfig.address).hostAddress
                }
            } catch (e: Exception) {
                Log.e("VpnViewModel", "Failed to resolve server host ${bestConfig.address}, using original address", e)
                bestConfig.address
            }

            val originalSni = bestConfig.sni
            val resolvedSni = if (originalSni.isNullOrEmpty()) {
                bestConfig.address // Fallback to original domain name for SNI/TLS handshakes
            } else {
                originalSni
            }

            val intent = Intent(context, MyVpnService::class.java).apply {
                action = MyVpnService.ACTION_CONNECT
                putExtra(MyVpnService.EXTRA_SERVER_NAME, bestConfig.remarks)
                putExtra(MyVpnService.EXTRA_SERVER_HOST, resolvedHost)
                putExtra("server_port", bestConfig.port)
                putExtra("server_protocol", bestConfig.protocol)
                putExtra("server_uuid", bestConfig.uuidOrPassword)
                putExtra("server_tls", bestConfig.tls)
                putExtra("server_sni", resolvedSni)
                putExtra("server_method", bestConfig.method)
            }
            
            try {
                context.startService(intent)
                _vpnStatus.value = VpnStatus.CONNECTED
                startTrafficMonitoring(context)
            } catch (e: Exception) {
                Log.e("VpnViewModel", "Failed to start VPN service", e)
                _vpnStatus.value = VpnStatus.DISCONNECTED
            }
        }
    }

    fun disconnectVpn(context: Context) {
        _vpnStatus.value = VpnStatus.DISCONNECTED
        _selectedServer.value = null
        _livePing.value = 0
        stopTrafficMonitoring()

        val intent = Intent(context, MyVpnService::class.java).apply {
            action = MyVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }

    fun logout(context: Context) {
        disconnectVpn(context)
        LocalStorage.clearAll(context)
        _configs.value = emptyList()
        _serverPings.value = emptyMap()
        _currentScreen.value = Screen.Login
    }

    fun refreshSubscription(context: Context, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        val savedUrl = LocalStorage.getSubscriptionUrl(context)
        if (savedUrl.isNullOrEmpty()) {
            onFailure("لینکی برای بروزرسانی ذخیره نشده است")
            return
        }

        _refreshing.value = true
        _refreshError.value = null

        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    V2RayConfigParser.fetchAndParseSubscription(savedUrl)
                }

                if (parsed.isEmpty()) {
                    val errMsg = "لیست سرورها خالی است یا کانفیگی یافت نشد"
                    _refreshError.value = errMsg
                    _refreshing.value = false
                    onFailure(errMsg)
                    return@launch
                }

                _configs.value = parsed
                LocalStorage.saveConfigs(context, parsed)
                _refreshing.value = false
                onSuccess()
            } catch (e: Exception) {
                Log.e("VpnViewModel", "Error refreshing subscription", e)
                val errMsg = "بروزرسانی ناموفق بود"
                _refreshError.value = errMsg
                _refreshing.value = false
                onFailure(errMsg)
            }
        }
    }

    private fun startTrafficMonitoring(context: Context) {
        stopTrafficMonitoring()
        lastRxBytes = TrafficStats.getTotalRxBytes()
        if (lastRxBytes == TrafficStats.UNSUPPORTED.toLong()) {
            lastRxBytes = 0L
        }

        trafficMonitorJob = viewModelScope.launch {
            while (_vpnStatus.value == VpnStatus.CONNECTED) {
                delay(2000)
                
                // 1. Measure real-time speed
                val currentRx = TrafficStats.getTotalRxBytes()
                if (currentRx != TrafficStats.UNSUPPORTED.toLong()) {
                    val bytesDiff = currentRx - lastRxBytes
                    lastRxBytes = currentRx
                    
                    // Convert bytes to KB/s (divided by 2 seconds)
                    val speedKb = (bytesDiff / 1024f) / 2f
                    _liveSpeedDownload.value = if (speedKb > 0f) speedKb else 0.5f // minimal bounce to keep graph alive
                    
                    // Add to chart history
                    val currentHistory = _speedHistory.value.toMutableList()
                    currentHistory.removeAt(0)
                    currentHistory.add(_liveSpeedDownload.value)
                    _speedHistory.value = currentHistory
                }

                // 2. Refresh latency in the background to show live fluctuations
                _selectedServer.value?.let { server ->
                    val freshPing = PingHelper.measurePing(server.address, server.port)
                    if (freshPing > 0) {
                        _livePing.value = freshPing
                    }
                }
            }
        }
    }

    private fun stopTrafficMonitoring() {
        trafficMonitorJob?.cancel()
        trafficMonitorJob = null
        _liveSpeedDownload.value = 0f
        _speedHistory.value = List(20) { 0f }
    }
}
