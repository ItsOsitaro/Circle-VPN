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
    val protocol: String,  // vmess, vless, ss, trojan
    val remarks: String,   // User-visible server name
    val address: String,   // Server IP or domain
    val port: Int,         // Port number
    val rawUri: String     // Original URI
)

object V2RayConfigParser {

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
     * and parses all contained V2Ray configurations.
     */
    suspend fun fetchAndParseSubscription(url: String): List<V2RayConfig> {
        Log.i("V2RayConfigParser", "Fetching subscription from: $url")
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch subscription: HTTP ${response.code}")
        }

        val rawBody = response.body?.string() ?: throw Exception("Response body is empty")
        return parseSubscriptionContent(rawBody)
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
                    configs.add(config)
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

            return V2RayConfig(
                protocol = "vmess",
                remarks = remarks,
                address = address,
                port = port,
                rawUri = uri
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
            
            val hostPort = userInfoAndHostPort.substringAfter("@")
            val host = hostPort.substringBefore(":")
            val portStr = hostPort.substringAfter(":", "443")
            val port = portStr.toIntOrNull() ?: 443

            return V2RayConfig(
                protocol = protocol,
                remarks = remarks,
                address = host,
                port = port,
                rawUri = uri
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
                val hostPort = mainPart.substringAfter("@")
                val host = hostPort.substringBefore(":")
                val portStr = hostPort.substringAfter(":", "1080")
                val port = portStr.toIntOrNull() ?: 1080
                return V2RayConfig("ss", remarks, host, port, uri)
            } else {
                // Base64 encoded userinfo and host info
                // Some clients encode method:password@host:port
                val decodedBytes = Base64.decode(mainPart, Base64.DEFAULT)
                val decodedStr = String(decodedBytes, Charsets.UTF_8)
                
                if (decodedStr.contains("@")) {
                    val hostPort = decodedStr.substringAfter("@")
                    val host = hostPort.substringBefore(":")
                    val portStr = hostPort.substringAfter(":", "1080")
                    val port = portStr.toIntOrNull() ?: 1080
                    return V2RayConfig("ss", remarks, host, port, uri)
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
