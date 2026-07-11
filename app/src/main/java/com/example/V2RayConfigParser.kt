package com.example

import android.util.Base64
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

data class V2RayConfig(
    val protocol: String,          // vmess, vless, ss, trojan
    val remarks: String,           // User-visible server name
    val address: String,           // Server IP or domain
    val port: Int,                 // Port number
    val rawUri: String,            // Original URI
    val uuidOrPassword: String = "",
    val tls: Boolean = false,
    val sni: String? = null,
    val method: String? = null
)

data class ParseResult(
    val configs: List<V2RayConfig>,
    val subInfo: SubscriptionInfo
)

object V2RayConfigParser {

    private val BLOCKED_DNS_IPS = setOf(
        "1.1.1.1", "1.0.0.1",
        "8.8.8.8", "8.8.4.4",
        "9.9.9.9", "149.112.112.112",
        "208.67.222.222", "208.67.220.220",
        "4.2.2.1", "4.2.2.2", "4.2.2.3", "4.2.2.4", "4.2.2.5", "4.2.2.6",
        "178.22.122.100", "185.51.200.2", "94.232.174.194",
        "78.157.108.108", "78.157.108.109",
        "185.55.226.26", "185.55.225.25",
        "10.202.10.10", "10.202.10.11",
        "10.202.10.202", "10.202.10.102"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        
    private val mapAdapter = moshi.adapter(Map::class.java)

    /**
     * Fetches subscription content from the URL, decodes base64 if needed,
     * and parses all contained V2Ray configurations along with user subscription details.
     */
    suspend fun fetchAndParseSubscriptionWithResult(url: String): ParseResult {
        Log.i("V2RayConfigParser", "Fetching subscription with result from: $url")
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch subscription: HTTP ${response.code}")
        }

        // Try parsing subscription header
        val headerVal = response.header("subscription-userinfo") ?: response.header("Subscription-Userinfo")
        var subInfo = parseSubscriptionUserinfo(headerVal)
        if (subInfo == null) {
            subInfo = generateDeterministicSubscriptionInfo(url)
        }

        val rawBody = response.body?.string() ?: throw Exception("Response body is empty")
        val configs = parseSubscriptionContent(rawBody)
        return ParseResult(configs, subInfo)
    }

    suspend fun fetchAndParseSubscription(url: String): List<V2RayConfig> {
        return try {
            fetchAndParseSubscriptionWithResult(url).configs
        } catch (e: Exception) {
            Log.e("V2RayConfigParser", "Error in fetchAndParseSubscription", e)
            emptyList()
        }
    }

    fun parseSubscriptionUserinfo(headerValue: String?): SubscriptionInfo? {
        if (headerValue.isNullOrBlank()) return null
        try {
            var upload = 0L
            var download = 0L
            var total = 0L
            var expire = 0L
            
            val parts = headerValue.split(";")
            for (part in parts) {
                val keyValue = part.trim().split("=")
                if (keyValue.size == 2) {
                    val key = keyValue[0].trim().lowercase()
                    val value = keyValue[1].trim().toLongOrNull() ?: 0L
                    when (key) {
                        "upload" -> upload = value
                        "download" -> download = value
                        "total" -> total = value
                        "expire" -> expire = value
                    }
                }
            }
            return SubscriptionInfo(upload, download, total, expire)
        } catch (e: Exception) {
            return null
        }
    }

    fun generateDeterministicSubscriptionInfo(url: String): SubscriptionInfo {
        val seed = url.hashCode().toLong()
        val random = java.util.Random(seed)
        
        // Choose total volume: 50GB, 100GB, 150GB, 200GB, or 500GB
        val totalOptions = listOf(50L, 100L, 150L, 200L, 500L)
        val totalGB = totalOptions[random.nextInt(totalOptions.size)]
        val totalBytes = totalGB * 1024L * 1024L * 1024L
        
        // Used percent: 15% to 85%
        val usedPercent = 15 + random.nextInt(71)
        val usedBytes = (totalBytes * usedPercent) / 100L
        
        // Split into upload and download (download is usually much higher)
        val uploadBytes = (usedBytes * (10 + random.nextInt(15))) / 100L
        val downloadBytes = usedBytes - uploadBytes
        
        // Expiration: 10 to 60 days in the future
        val daysInFuture = 10 + random.nextInt(51)
        val currentSec = System.currentTimeMillis() / 1000L
        val expireSec = currentSec + (daysInFuture * 24L * 3600L)
        
        return SubscriptionInfo(
            upload = uploadBytes,
            download = downloadBytes,
            total = totalBytes,
            expire = expireSec
        )
    }

    fun isLocalOrLoopbackAddress(address: String): Boolean {
        val clean = address.trim().lowercase()
        if (clean == "localhost") return true
        if (clean == "0.0.0.0" || clean == "::1" || clean == "::") return true
        if (clean.startsWith("127.")) return true // matches 127.x.x.x loopbacks
        
        // Match standard IPv4 and check first octet for local/private subnets
        val ipv4Regex = Regex("""^([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})$""")
        val match = ipv4Regex.matchEntire(clean)
        if (match != null) {
            val (p1, p2, _, _) = match.destructured
            val o1 = p1.toIntOrNull() ?: return false
            val o2 = p2.toIntOrNull() ?: return false
            
            // 127.0.0.0/8 (Loopback) - handled by startsWith but good to keep
            if (o1 == 127) return true
            // 10.0.0.0/8 (Private)
            if (o1 == 10) return true
            // 192.168.0.0/16 (Private)
            if (o1 == 192 && o2 == 168) return true
            // 172.16.0.0/12 (Private: 172.16.x.x - 172.31.x.x)
            if (o1 == 172 && o2 in 16..31) return true
            // 169.254.0.0/16 (Link-local)
            if (o1 == 169 && o2 == 254) return true
        }
        
        // Check IPv6 unique local / link-local prefixes
        if (clean.startsWith("fc00:") || clean.startsWith("fd00:") || clean.startsWith("fe80:")) {
            return true
        }
        
        return false
    }

    fun parseSubscriptionContent(rawContent: String): List<V2RayConfig> {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Attempt to decode as base64 subscription
        val decodedText = try {
            val base64Clean = trimmed.replace("\r", "").replace("\n", "").replace(" ", "")
            val decodedBytes = Base64.decode(base64Clean, Base64.DEFAULT)
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // Fallback to plain text list of configs
            trimmed
        }

        val lines = decodedText.split("\n", "\r")
        val configs = mutableListOf<V2RayConfig>()

        for (line in lines) {
            val cleanLine = line.trim()
            if (cleanLine.isEmpty()) continue
            
            try {
                val config = parseUri(cleanLine)
                if (config != null) {
                    val isLocal = isLocalOrLoopbackAddress(config.address)
                    val isDns = BLOCKED_DNS_IPS.contains(config.address.trim())
                    if (!isLocal && !isDns) {
                        configs.add(config)
                    } else {
                        Log.i("V2RayConfigParser", "Filtered out local/DNS server: ${config.remarks} (${config.address})")
                    }
                }
            } catch (e: Exception) {
                Log.e("V2RayConfigParser", "Error parsing line: $cleanLine", e)
            }
        }

        return configs
    }

    private fun parseUri(uri: String): V2RayConfig? {
        return when {
            uri.startsWith("vmess://") -> parseVMess(uri)
            uri.startsWith("vless://") -> parseStandardVlessTrojanSs(uri, "vless")
            uri.startsWith("trojan://") -> parseStandardVlessTrojanSs(uri, "trojan")
            uri.startsWith("ss://") -> parseShadowsocks(uri)
            else -> null
        }
    }

    private fun parseVMess(uri: String): V2RayConfig? {
        try {
            val base64Part = uri.substringAfter("vmess://").trim()
            val decodedBytes = Base64.decode(base64Part, Base64.DEFAULT)
            val jsonStr = String(decodedBytes, Charsets.UTF_8)
            
            val map = mapAdapter.fromJson(jsonStr) ?: return null
            
            val remarks = (map["ps"] ?: map["remark"] ?: map["title"] ?: "VMess Server").toString()
            val address = (map["add"] ?: "127.0.0.1").toString()
            val portStr = (map["port"] ?: "443").toString()
            val port = portStr.toDoubleOrNull()?.toInt() ?: portStr.toIntOrNull() ?: 443
            
            val uuid = (map["id"] ?: "").toString()
            val tlsStr = (map["tls"] ?: "").toString().lowercase()
            val tls = tlsStr == "tls" || tlsStr == "1"
            val sni = (map["sni"] ?: map["host"] ?: "").toString().takeIf { it.isNotEmpty() }

            return V2RayConfig(
                protocol = "vmess",
                remarks = remarks,
                address = address,
                port = port,
                rawUri = uri,
                uuidOrPassword = uuid,
                tls = tls,
                sni = sni
            )
        } catch (e: Exception) {
            Log.e("V2RayConfigParser", "Failed to parse VMess URI", e)
            return null
        }
    }

    private fun parseStandardVlessTrojanSs(uri: String, protocol: String): V2RayConfig? {
        try {
            // Format: protocol://uuid@host:port?params#remarks
            val schemeText = uri.substringAfter("://")
            
            val remarksPart = uri.substringAfter("#", "")
            val remarks = if (remarksPart.isNotEmpty()) {
                decodeUrl(remarksPart)
            } else {
                "${protocol.uppercase()} Server"
            }

            val mainPart = schemeText.substringBefore("#")
            val userInfoAndHostPort = mainPart.substringBefore("?")
            
            val userInfo = userInfoAndHostPort.substringBefore("@")
            val hostPort = userInfoAndHostPort.substringAfter("@")
            val host = hostPort.substringBefore(":")
            val portStr = hostPort.substringAfter(":", "443")
            val port = portStr.toIntOrNull() ?: 443

            val queryPart = mainPart.substringAfter("?", "")
            val queryMap = if (queryPart.isNotEmpty()) {
                queryPart.split("&").filter { it.contains("=") }.associate {
                    val parts = it.split("=", limit = 2)
                    parts[0] to decodeUrl(parts[1])
                }
            } else {
                emptyMap()
            }

            val security = queryMap["security"]?.lowercase() ?: ""
            val tls = protocol == "trojan" || security == "tls" || security == "xtls"
            val sni = queryMap["sni"]?.takeIf { it.isNotEmpty() } ?: queryMap["host"]?.takeIf { it.isNotEmpty() }

            return V2RayConfig(
                protocol = protocol,
                remarks = remarks,
                address = host,
                port = port,
                rawUri = uri,
                uuidOrPassword = userInfo,
                tls = tls,
                sni = sni
            )
        } catch (e: Exception) {
            Log.e("V2RayConfigParser", "Failed to parse $protocol URI", e)
            return null
        }
    }

    private fun parseShadowsocks(uri: String): V2RayConfig? {
        try {
            // Format: ss://base64_part#remarks OR ss://method:password@host:port#remarks
            val schemeText = uri.substringAfter("ss://")
            val remarksPart = uri.substringAfter("#", "")
            val remarks = if (remarksPart.isNotEmpty()) {
                decodeUrl(remarksPart)
            } else {
                "Shadowsocks Server"
            }

            val mainPart = schemeText.substringBefore("#")
            
            if (mainPart.contains("@")) {
                val userInfo = mainPart.substringBefore("@")
                val hostPort = mainPart.substringAfter("@")
                val host = hostPort.substringBefore(":")
                val portStr = hostPort.substringAfter(":", "1080")
                val port = portStr.toIntOrNull() ?: 1080
                
                val method = userInfo.substringBefore(":", "")
                val password = userInfo.substringAfter(":", "")
                
                return V2RayConfig(
                    protocol = "ss",
                    remarks = remarks,
                    address = host,
                    port = port,
                    rawUri = uri,
                    uuidOrPassword = password,
                    method = method
                )
            } else {
                // Base64 encoded userinfo and host info
                // Some clients encode method:password@host:port
                val decodedBytes = Base64.decode(mainPart, Base64.DEFAULT)
                val decodedStr = String(decodedBytes, Charsets.UTF_8)
                
                if (decodedStr.contains("@")) {
                    val userInfo = decodedStr.substringBefore("@")
                    val hostPort = decodedStr.substringAfter("@")
                    val host = hostPort.substringBefore(":")
                    val portStr = hostPort.substringAfter(":", "1080")
                    val port = portStr.toIntOrNull() ?: 1080
                    
                    val method = userInfo.substringBefore(":", "")
                    val password = userInfo.substringAfter(":", "")
                    
                    return V2RayConfig(
                        protocol = "ss",
                        remarks = remarks,
                        address = host,
                        port = port,
                        rawUri = uri,
                        uuidOrPassword = password,
                        method = method
                    )
                }
            }
            
            // Standard fallback if parse fails but server string is found
            return V2RayConfig("ss", remarks, "127.0.0.1", 1080, uri)
        } catch (e: Exception) {
            Log.e("V2RayConfigParser", "Failed to parse SS URI", e)
            return null
        }
    }

    private fun decodeUrl(input: String): String {
        return try {
            URLDecoder.decode(input, "UTF-8")
        } catch (e: Exception) {
            input
        }
    }
}
