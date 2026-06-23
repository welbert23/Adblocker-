package com.adblocker

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors

class ProxyServer(
    private val blocklist: Set<String>,
    private val port: Int
) {
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private var running = false
    var blockedCount = 0L

    fun start() {
        if (running) return
        running = true
        executor.submit {
            try {
                serverSocket = ServerSocket(port, 50, java.net.InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
                while (running) {
                    try {
                        val client = serverSocket!!.accept()
                        executor.submit { handleClient(client) }
                    } catch (_: Exception) { break }
                }
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 30000
            val input = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = input.readLine() ?: return
            if (requestLine.isBlank()) return

            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val target = parts[1]
            val host: String
            val port: Int

            if (method == "CONNECT") {
                val hostPort = target.split(":")
                host = hostPort[0]
                port = hostPort.getOrElse(1) { "443" }.toIntOrNull() ?: 443

                val lowerHost = host.lowercase().removePrefix("www.")
                val isAd = blocklist.any { ad ->
                    lowerHost == ad || lowerHost.endsWith(".$ad")
                }

                if (isAd) {
                    client.getOutputStream().write("HTTP/1.1 403 Forbidden\r\n\r\n".toByteArray())
                    blockedCount++
                    return
                }

                val remote = Socket()
                try {
                    remote.connect(java.net.InetSocketAddress(host, port), 10000)
                    client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                    relayData(client, remote)
                } catch (e: Exception) {
                    client.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
                } finally {
                    try { remote.close() } catch (_: Exception) {}
                }
            } else {
                val url = URL(target)
                val h = url.host.lowercase().removePrefix("www.")
                val isAd = blocklist.any { ad -> h == ad || h.endsWith(".$ad") }

                if (isAd) {
                    client.getOutputStream().write("HTTP/1.1 403 Forbidden\r\n\r\n".toByteArray())
                    blockedCount++
                    return
                }

                val remote = Socket()
                try {
                    val remotePort = url.port.takeIf { it != -1 } ?: 80
                    remote.connect(java.net.InetSocketAddress(url.host, remotePort), 10000)
                    val remoteOut = remote.getOutputStream()
                    val firstLine = "$method ${url.path}${if (url.query != null) "?${url.query}" else ""} HTTP/1.1\r\n"
                    remoteOut.write(firstLine.toByteArray())
                    var line = input.readLine()
                    while (line != null && line.isNotBlank()) {
                        remoteOut.write("$line\r\n".toByteArray())
                        line = input.readLine()
                    }
                    remoteOut.write("\r\n".toByteArray())
                    remoteOut.flush()
                    relayData(client, remote)
                } catch (e: Exception) {
                    client.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
                } finally {
                    try { remote.close() } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun relayData(client: Socket, remote: Socket) {
        val clientIn = client.getInputStream()
        val remoteIn = remote.getInputStream()
        val clientOut = client.getOutputStream()
        val remoteOut = remote.getOutputStream()

        val t1 = Thread {
            try {
                val buf = ByteArray(32768)
                while (true) {
                    val read = clientIn.read(buf)
                    if (read <= 0) break
                    remoteOut.write(buf, 0, read)
                    remoteOut.flush()
                }
            } catch (_: Exception) {}
        }

        val t2 = Thread {
            try {
                val buf = ByteArray(32768)
                while (true) {
                    val read = remoteIn.read(buf)
                    if (read <= 0) break
                    clientOut.write(buf, 0, read)
                    clientOut.flush()
                }
            } catch (_: Exception) {}
        }

        t1.start()
        t2.start()
        try { t1.join(30000) } catch (_: Exception) {}
        try { t2.join(30000) } catch (_: Exception) {}
    }
}
