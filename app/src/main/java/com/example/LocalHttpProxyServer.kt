package com.example

import android.os.Build
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class LocalHttpProxyServer(
    private val port: Int,
    private val serverHost: String,
    private val serverPort: Int,
    private val serverProtocol: String,
    private val serverUuid: String,
    private val serverTls: Boolean,
    private val serverSni: String?,
    private val serverMethod: String?
) {

    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start() {
        isRunning = true
        thread(name = "LocalHttpProxyAcceptor") {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    thread(name = "LocalHttpProxyHandler") {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                Log.e("ProxyServer", "Error in accept loop", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
    }

    private fun handleClient(clientSocket: Socket) {
        var destSocket: Socket? = null
        try {
            val clientInput = clientSocket.getInputStream()
            val clientOutput = clientSocket.getOutputStream()

            // Read request line
            val firstLine = readLine(clientInput) ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val url = parts[1]
            val protocol = if (parts.size > 2) parts[2] else "HTTP/1.1"

            var host = ""
            var targetPort = 80

            if (method.uppercase() == "CONNECT") {
                // HTTPS Tunneling
                val hostPort = url.split(":")
                host = hostPort[0]
                targetPort = if (hostPort.size > 1) hostPort[1].toInt() else 443

                VpnDiagnosticManager.addLog(
                    type = "PROXY",
                    description = "HTTPS Tunnel Request",
                    details = "Connecting: $host:$targetPort",
                    status = "INFO"
                )

                // Skip headers
                while (true) {
                    val line = readLine(clientInput)
                    if (line.isNullOrEmpty()) break
                }

                // Connect to destination via V2Ray tunnel
                try {
                    destSocket = connectToRemoteProxyTunnel(host, targetPort)
                } catch (e: Exception) {
                    Log.e("ProxyServer", "Failed to connect to V2Ray tunnel for $host:$targetPort", e)
                    VpnDiagnosticManager.addLog(
                        type = "PROXY",
                        description = "HTTPS Tunnel Failed",
                        details = "Destination: $host:$targetPort\nError: ${e.message ?: e.toString()}",
                        status = "FAILED"
                    )
                    try {
                        clientOutput.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
                        clientOutput.flush()
                    } catch (ex: Exception) {}
                    clientSocket.close()
                    return
                }

                // Handle protocol response header if needed
                if (serverProtocol.lowercase() == "vless") {
                    try {
                        val remoteInput = destSocket.getInputStream()
                        val version = remoteInput.read()
                        if (version >= 0) {
                            val addonLength = remoteInput.read()
                            if (addonLength > 0) {
                                val discard = ByteArray(addonLength)
                                var readBytes = 0
                                while (readBytes < addonLength) {
                                    val r = remoteInput.read(discard, readBytes, addonLength - readBytes)
                                    if (r < 0) break
                                    readBytes += r
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ProxyServer", "Error skipping VLess response header", e)
                    }
                }

                clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                clientOutput.flush()

                VpnDiagnosticManager.addLog(
                    type = "PROXY",
                    description = "HTTPS Tunnel Established",
                    details = "Active tunnel: $host:$targetPort via remote proxy $serverHost:$serverPort",
                    status = "TUNNELED"
                )

                val destInput = destSocket.getInputStream()
                val destOutput = destSocket.getOutputStream()

                // Start bidirectional copy with immediate close on finish
                val t1 = thread(name = "ProxyCopyClientToDest") {
                    try {
                        copyStream(clientInput, destOutput)
                    } finally {
                        try { destSocket.shutdownOutput() } catch (e: Exception) {}
                        try { clientSocket.close() } catch (e: Exception) {}
                    }
                }
                val t2 = thread(name = "ProxyCopyDestToClient") {
                    try {
                        copyStream(destInput, clientOutput)
                    } finally {
                        try { clientSocket.shutdownOutput() } catch (e: Exception) {}
                        try { destSocket.close() } catch (e: Exception) {}
                    }
                }

                t1.join()
                t2.join()
            } else {
                // HTTP Connection
                var uri = url
                if (uri.startsWith("http://")) {
                    uri = uri.substring(7)
                }
                val hostPortPath = uri.split("/", limit = 2)
                val hostPort = hostPortPath[0].split(":")
                host = hostPort[0]
                targetPort = if (hostPort.size > 1) hostPort[1].toInt() else 80

                VpnDiagnosticManager.addLog(
                    type = "PROXY",
                    description = "HTTP Request",
                    details = "$method $url",
                    status = "INFO"
                )

                // Reconstruct raw HTTP request
                val requestBuilder = StringBuilder()
                requestBuilder.append("$method $url $protocol\r\n")
                
                // Read headers
                while (true) {
                    val line = readLine(clientInput)
                    if (line.isNullOrEmpty()) break
                    requestBuilder.append(line).append("\r\n")
                }
                requestBuilder.append("\r\n")

                try {
                    destSocket = connectToRemoteProxyTunnel(host, targetPort)
                } catch (e: Exception) {
                    Log.e("ProxyServer", "Failed to connect to V2Ray tunnel for $host:$targetPort", e)
                    VpnDiagnosticManager.addLog(
                        type = "PROXY",
                        description = "HTTP Proxy Tunnel Failed",
                        details = "$method $url\nError: ${e.message ?: e.toString()}",
                        status = "FAILED"
                    )
                    try {
                        clientOutput.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
                        clientOutput.flush()
                    } catch (ex: Exception) {}
                    clientSocket.close()
                    return
                }

                // Handle protocol response header if needed
                if (serverProtocol.lowercase() == "vless") {
                    try {
                        val remoteInput = destSocket.getInputStream()
                        val version = remoteInput.read()
                        if (version >= 0) {
                            val addonLength = remoteInput.read()
                            if (addonLength > 0) {
                                val discard = ByteArray(addonLength)
                                var readBytes = 0
                                while (readBytes < addonLength) {
                                    val r = remoteInput.read(discard, readBytes, addonLength - readBytes)
                                    if (r < 0) break
                                    readBytes += r
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ProxyServer", "Error skipping VLess response header", e)
                    }
                }

                VpnDiagnosticManager.addLog(
                    type = "PROXY",
                    description = "HTTP Request Tunneled",
                    details = "Sent $method request to $host:$targetPort",
                    status = "TUNNELED"
                )

                val destInput = destSocket.getInputStream()
                val destOutput = destSocket.getOutputStream()

                destOutput.write(requestBuilder.toString().toByteArray())
                destOutput.flush()

                // Start bidirectional copy with immediate close on finish
                val t1 = thread(name = "ProxyCopyClientToDest") {
                    try {
                        copyStream(clientInput, destOutput)
                    } finally {
                        try { destSocket.shutdownOutput() } catch (e: Exception) {}
                        try { clientSocket.close() } catch (e: Exception) {}
                    }
                }
                val t2 = thread(name = "ProxyCopyDestToClient") {
                    try {
                        copyStream(destInput, clientOutput)
                    } finally {
                        try { clientSocket.shutdownOutput() } catch (e: Exception) {}
                        try { destSocket.close() } catch (e: Exception) {}
                    }
                }

                t1.join()
                t2.join()
            }
        } catch (e: Exception) {
            // Socket exceptions are common during close
        } finally {
            try { clientSocket.close() } catch (e: Exception) {}
            try { destSocket?.close() } catch (e: Exception) {}
        }
    }

    private fun isIpAddress(host: String): Boolean {
        return try {
            host.matches(Regex("""^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$""")) || host.contains(":")
        } catch (e: Exception) {
            false
        }
    }

    private fun getTrustAllSslSocketFactory(): javax.net.ssl.SSLSocketFactory {
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
            object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            }
        )
        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        return sslContext.socketFactory
    }

    private fun connectToRemoteProxyTunnel(targetHost: String, targetPort: Int): Socket {
        val rawSocket = Socket()
        MyVpnService.protectSocket(rawSocket)
        rawSocket.connect(InetSocketAddress(serverHost, serverPort), 10000)

        var finalSocket = rawSocket

        if (serverTls) {
            try {
                val sslSocketFactory = javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
                val sslSocket = sslSocketFactory.createSocket(rawSocket, serverSni ?: serverHost, serverPort, true) as javax.net.ssl.SSLSocket
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val params = sslSocket.sslParameters
                    params.serverNames = listOf(javax.net.ssl.SNIHostName(serverSni ?: serverHost))
                    sslSocket.sslParameters = params
                }
                
                sslSocket.startHandshake()
                finalSocket = sslSocket
            } catch (e: Exception) {
                Log.w("ProxyServer", "Default SSL handshake failed, trying trust-all SSL: ${e.message}")
                VpnDiagnosticManager.addLog(
                    type = "SYSTEM",
                    description = "SSL Fallback Active",
                    details = "Default SSL failed (${e.message}). Switched to Trust-All TLS to bypass censorship.",
                    status = "INFO"
                )
                try {
                    rawSocket.close()
                } catch (ec: Exception) {}

                val fallbackSocket = Socket()
                MyVpnService.protectSocket(fallbackSocket)
                fallbackSocket.connect(InetSocketAddress(serverHost, serverPort), 10000)

                val trustAllFactory = getTrustAllSslSocketFactory()
                val sslSocket = trustAllFactory.createSocket(fallbackSocket, serverSni ?: serverHost, serverPort, true) as javax.net.ssl.SSLSocket
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val params = sslSocket.sslParameters
                    params.serverNames = listOf(javax.net.ssl.SNIHostName(serverSni ?: serverHost))
                    sslSocket.sslParameters = params
                }
                
                sslSocket.startHandshake()
                finalSocket = sslSocket
            }
        }

        val output = finalSocket.getOutputStream()
        when (serverProtocol.lowercase()) {
            "trojan" -> {
                val hashHex = sha224Hex(serverUuid)
                val headerStream = java.io.ByteArrayOutputStream()
                headerStream.write(hashHex.toByteArray(Charsets.UTF_8))
                headerStream.write("\r\n".toByteArray(Charsets.UTF_8))
                headerStream.write(1) // Command: CONNECT TCP
                headerStream.write(getAddressHeader(targetHost, targetPort))
                headerStream.write("\r\n".toByteArray(Charsets.UTF_8))
                output.write(headerStream.toByteArray())
                output.flush()
            }
            "vless" -> {
                val headerStream = java.io.ByteArrayOutputStream()
                headerStream.write(0) // Version: 0
                headerStream.write(uuidToBytes(serverUuid)) // UUID: 16 bytes
                headerStream.write(0) // Addon length: 0
                headerStream.write(1) // Command: CONNECT TCP
                headerStream.write(getVLessAddressHeader(targetHost, targetPort))
                output.write(headerStream.toByteArray())
                output.flush()
            }
            else -> {
                VpnDiagnosticManager.addLog(
                    type = "SYSTEM",
                    description = "Unsupported Protocol",
                    details = "Protocol '$serverProtocol' is not supported in the pure-Java tunnel due to heavy encryption/AEAD requirements. Please use Trojan or VLESS.",
                    status = "FAILED"
                )
                throw Exception("Protocol '$serverProtocol' is not supported in the pure-Java tunnel.")
            }
        }

        return finalSocket
    }

    private fun sha224Hex(input: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-224")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    private fun uuidToBytes(uuidStr: String): ByteArray {
        return try {
            val uuid = java.util.UUID.fromString(uuidStr.trim())
            val bb = java.nio.ByteBuffer.wrap(ByteArray(16))
            bb.putLong(uuid.mostSignificantBits)
            bb.putLong(uuid.leastSignificantBits)
            bb.array()
        } catch (e: Exception) {
            ByteArray(16)
        }
    }

    private fun getAddressHeader(host: String, port: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        if (isIpAddress(host)) {
            try {
                val inetAddr = java.net.InetAddress.getByName(host)
                val addressBytes = inetAddr.address
                if (addressBytes.size == 4) {
                    output.write(1) // IPv4
                    output.write(addressBytes)
                } else if (addressBytes.size == 16) {
                    output.write(4) // IPv6
                    output.write(addressBytes)
                } else {
                    writeDomain(output, host)
                }
            } catch (e: Exception) {
                writeDomain(output, host)
            }
        } else {
            writeDomain(output, host)
        }
        output.write((port shr 8) and 0xFF)
        output.write(port and 0xFF)
        return output.toByteArray()
    }

    private fun writeDomain(output: java.io.ByteArrayOutputStream, host: String) {
        output.write(3) // Domain
        val domainBytes = host.toByteArray(Charsets.UTF_8)
        output.write(domainBytes.size and 0xFF)
        output.write(domainBytes)
    }

    private fun getVLessAddressHeader(host: String, port: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        output.write((port shr 8) and 0xFF)
        output.write(port and 0xFF)
        if (isIpAddress(host)) {
            try {
                val inetAddr = java.net.InetAddress.getByName(host)
                val addressBytes = inetAddr.address
                if (addressBytes.size == 4) {
                    output.write(1) // IPv4
                    output.write(addressBytes)
                } else if (addressBytes.size == 16) {
                    output.write(3) // IPv6
                    output.write(addressBytes)
                } else {
                    writeVLessDomain(output, host)
                }
            } catch (e: Exception) {
                writeVLessDomain(output, host)
            }
        } else {
            writeVLessDomain(output, host)
        }
        return output.toByteArray()
    }

    private fun writeVLessDomain(output: java.io.ByteArrayOutputStream, host: String) {
        output.write(2) // Domain
        val domainBytes = host.toByteArray(Charsets.UTF_8)
        output.write(domainBytes.size and 0xFF)
        output.write(domainBytes)
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16384)
        var read: Int
        try {
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (e: Exception) {
            // connection reset or closed
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var c: Int
        while (input.read().also { c = it } != -1) {
            if (c == '\n'.toInt()) {
                break
            }
            if (c == '\r'.toInt()) {
                continue
            }
            sb.append(c.toChar())
        }
        return if (sb.isEmpty() && c == -1) null else sb.toString()
    }
}
