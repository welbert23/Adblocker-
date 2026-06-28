package com.adblocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class AdBlockVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var running = false
    @Volatile private var blockAdult = true
    private val blockedCount = AtomicLong(0)
    @Volatile private var lastBlockedDomain: String? = null

    private var customBlockList: Set<String> = emptySet()
    private var whitelist: Set<String> = emptySet()
    private var importedHosts: Set<String> = emptySet()
    private var blockedApps: Set<String> = emptySet()
    private var adultBlockList: Set<String> = BlocklistDatabase.ADULT_DOMAINS
    private var adultKeywords: Set<String> = BlocklistDatabase.ADULT_KEYWORDS
    private var gamblingDomains: Set<String> = BlocklistDatabase.GAMBLING_DOMAINS
    private var gamblingKeywords: Set<String> = BlocklistDatabase.GAMBLING_KEYWORDS
    private var dnsServers: List<String> = DEFAULT_DNS

    private val tcpConnections = ConcurrentHashMap<IpUtil.TcpKey, IpUtil.TcpState>()
    private val udpForwarders = ConcurrentHashMap<UdpKey, DatagramSocket>()
    @Volatile private var tunOutput: OutputStream? = null

    companion object {
        const val ACTION_START = "com.adblocker.START"
        const val ACTION_STOP = "com.adblocker.STOP"
        const val ACTION_UPDATE_SETTINGS = "com.adblocker.UPDATE_SETTINGS"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "adblocker_vpn"

        val DEFAULT_DNS = listOf(
            "8.8.8.8", "8.8.4.4",
            "1.1.1.1", "1.0.0.1",
            "208.67.222.222", "208.67.220.220",
            "9.9.9.9", "149.112.112.112"
        )

        val DNS_PRESETS = mapOf(
            "Google" to listOf("8.8.8.8", "8.8.4.4"),
            "Cloudflare" to listOf("1.1.1.1", "1.0.0.1"),
            "OpenDNS" to listOf("208.67.222.222", "208.67.220.220"),
            "Quad9" to listOf("9.9.9.9", "149.112.112.112"),
            "Custom" to emptyList()
        )
    }

    override fun onCreate() {
        super.onCreate()
        BlocklistDatabase.init(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
            ACTION_UPDATE_SETTINGS -> loadSettings()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Ad Blocker",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "DNS ad blocker" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
        whitelist = BlocklistDatabase.loadWhitelist(prefs)
        blockAdult = prefs.getBoolean("adult_blocking", true)
        customBlockList = loadCustomBlocklist(prefs)
        importedHosts = BlocklistDatabase.IMPORTED_HOSTS
        blockedApps = BlocklistDatabase.loadBlockedApps(prefs)
        adultBlockList = BlocklistDatabase.ADULT_DOMAINS
        adultKeywords = BlocklistDatabase.ADULT_KEYWORDS
        gamblingDomains = BlocklistDatabase.GAMBLING_DOMAINS
        gamblingKeywords = BlocklistDatabase.GAMBLING_KEYWORDS
        dnsServers = loadDnsServers(prefs)
        if (running) restartVpn()
    }

    private fun loadCustomBlocklist(prefs: SharedPreferences): Set<String> {
        val json = prefs.getString("custom_blocklist", "[]") ?: "[]"
        return try {
            json.trim().removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"").lowercase() }
                .filter { it.isNotBlank() }
                .toSet()
        } catch (_: Exception) { emptySet() }
    }

    private fun loadDnsServers(prefs: SharedPreferences): List<String> {
        val preset = prefs.getString("dns_preset", "All") ?: "All"
        if (preset == "All") return DEFAULT_DNS
        if (preset == "Custom") {
            val custom = prefs.getString("dns_custom", "") ?: ""
            return custom.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
        return DNS_PRESETS[preset] ?: DEFAULT_DNS
    }

    private fun getMergedBlocklist(): Set<String> {
        val merged = mutableSetOf<String>()
        merged.addAll(BlocklistDatabase.AD_DOMAINS)
        merged.addAll(BlocklistDatabase.DOH_DOMAINS)
        if (blockAdult) merged.addAll(adultBlockList)
        merged.addAll(customBlockList)
        merged.addAll(importedHosts)
        return merged
    }

    private fun restartVpn() {
        stopVpnInternal()
        startVpn()
    }

    private fun stopVpnInternal() {
        running = false
        tcpConnections.forEach { (_, state) ->
            try { state.remoteSocket?.close() } catch (_: Exception) {}
        }
        tcpConnections.clear()
        udpForwarders.forEach { (_, sock) ->
            try { sock.close() } catch (_: Exception) {}
        }
        udpForwarders.clear()
        vpnInterface?.close()
        vpnInterface = null
    }

    private fun startVpn() {
        try {
            loadSettings()

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Blocker+")
                    .setContentText("Active")
                    .setSmallIcon(android.R.drawable.ic_menu_manage)
                    .setContentIntent(pi).setOngoing(true).build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
                    .setContentTitle("Blocker+")
                    .setContentText("Active")
                    .setSmallIcon(android.R.drawable.ic_menu_manage)
                    .setContentIntent(pi).setOngoing(true).build()
            }
            startForeground(NOTIFICATION_ID, notif)

            val b = Builder().apply {
                setSession("Blocker+")
                setMtu(1500)
                addAddress("10.0.1.1", 32)
                addAddress("fd00:1:2:3::1", 126)

                addRoute("0.0.0.0", 0)

                if (Build.VERSION.SDK_INT >= 21) {
                    addRoute("::", 0)
                    addRoute("2001:4860:4860::8888", 128)
                    addRoute("2001:4860:4860::8844", 128)
                    addRoute("2606:4700:4700::1111", 128)
                    addRoute("2606:4700:4700::1001", 128)
                    addRoute("2620:119:35::35", 128)
                    addRoute("2620:119:53::53", 128)
                    addRoute("2620:fe::fe", 128)
                    addRoute("2620:fe::9", 128)
                }

                for (dns in dnsServers) {
                    try {
                        val addr = InetAddress.getByName(dns)
                        if (addr.address.size == 4) addDnsServer(dns)
                        else if (Build.VERSION.SDK_INT >= 21) addDnsServer(dns)
                    } catch (_: Exception) {}
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (blockedApps.isEmpty()) {
                        addDisallowedApplication(packageName)
                    } else {
                        for (pkg in blockedApps) {
                            try { addAllowedApplication(pkg) } catch (_: Exception) {}
                        }
                    }
                }
            }

            vpnInterface?.close()
            vpnInterface = b.establish() ?: run {
                stopForeground(STOP_FOREGROUND_REMOVE)
                return@startVpn
            }

            running = true
            Executors.newSingleThreadExecutor().submit { vpnLoop() }
        } catch (e: Exception) { e.printStackTrace(); stopVpn() }
    }

    private fun stopVpn() {
        running = false
        tcpConnections.forEach { (_, state) ->
            try { state.remoteSocket?.close() } catch (_: Exception) {}
        }
        tcpConnections.clear()
        udpForwarders.forEach { (_, sock) ->
            try { sock.close() } catch (_: Exception) {}
        }
        udpForwarders.clear()
        vpnInterface?.close(); vpnInterface = null
        tunOutput = null
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    @Volatile private var vpnThread: Thread? = null

    private fun vpnLoop() {
        vpnThread = Thread.currentThread()
        try {
            val input = BufferedInputStream(FileInputStream(vpnInterface?.fileDescriptor))
            val output = FileOutputStream(vpnInterface?.fileDescriptor)
            tunOutput = output
            val pkt = ByteArray(65535)

            while (running) {
                try {
                    val len = input.read(pkt)
                    if (len <= 0) continue
                    val pktCopy = pkt.copyOf(len)
                    processPacket(pktCopy)
                } catch (e: java.io.InterruptedIOException) { break }
                  catch (_: Exception) { if (!running) break }
            }
            try { input.close() } catch (_: Exception) {}
        } catch (e: Exception) { e.printStackTrace() } finally {
            tunOutput = null
            vpnThread = null
        }
    }

    private val outputLock = Any()

    private fun writeToTun(packet: ByteArray) {
        val out = tunOutput ?: return
        try {
            synchronized(outputLock) { out.write(packet); out.flush() }
        } catch (_: Exception) {}
    }

    private data class UdpKey(val srcIp: ByteArray, val srcPort: Int, val dstIp: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is UdpKey) return false
            return srcIp.contentEquals(other.srcIp) && srcPort == other.srcPort && dstIp.contentEquals(other.dstIp)
        }
        override fun hashCode(): Int = srcIp.contentHashCode() * 31 + srcPort + dstIp.contentHashCode()
    }

    private fun processPacket(pkt: ByteArray) {
        try {
            val version = IpUtil.ipVersion(pkt)
            if (version == 4) {
                processPacket4(pkt)
            } else if (version == 6) {
                processPacket6(pkt)
            }
        } catch (_: Exception) {}
    }

    private fun processPacket4(pkt: ByteArray) {
        val ihl = IpUtil.ipHeaderLen(pkt)
        if (ihl < 20 || ihl > pkt.size) return
        val proto = IpUtil.protocol(pkt)
        val srcIp = IpUtil.srcIp(pkt)
        val dstIp = IpUtil.dstIp(pkt)
        handleTransport(proto, pkt, srcIp, dstIp, ihl)
    }

    private fun processPacket6(pkt: ByteArray) {
        if (pkt.size < 40) return
        val proto = IpUtil.ip6Protocol(pkt)
        val srcIp = IpUtil.ip6SrcIp(pkt)
        val dstIp = IpUtil.ip6DstIp(pkt)
        handleTransport(proto, pkt, srcIp, dstIp, 40)
    }

    private fun handleTransport(proto: Int, pkt: ByteArray, srcIp: ByteArray, dstIp: ByteArray, ihl: Int) {
        if (proto == 17) {
            val srcPort = IpUtil.srcPort(pkt, ihl)
            val dstPort = IpUtil.dstPort(pkt, ihl)
            if (dstPort == 53) {
                handleDns(pkt, pkt.size, ihl)
            } else if (dstPort == 443) {
                return
            } else {
                forwardUdp(pkt, srcIp, dstIp, srcPort, dstPort)
            }
        } else if (proto == 6) {
            handleTcp(pkt, srcIp, dstIp, ihl)
        }
    }

    private fun forwardUdp(pkt: ByteArray, srcIp: ByteArray, dstIp: ByteArray, srcPort: Int, dstPort: Int) {
        try {
            val ihl = IpUtil.ipHeaderLen(pkt)
            val udpOffset = ihl + 8
            val udpLen = pkt.size - udpOffset
            if (udpLen <= 0) return

            val key = UdpKey(srcIp, srcPort, dstIp)
            var sock = udpForwarders[key]
            if (sock == null) {
                sock = DatagramSocket()
                protect(sock)
                sock.soTimeout = 30000
                udpForwarders[key] = sock
                val finalSock = sock
                val capturedKey = key
                Thread {
                    try {
                        val buf = ByteArray(2048)
                        while (running) {
                            val rp = DatagramPacket(buf, buf.size)
                            finalSock.receive(rp)
                            val data = ByteArray(rp.length)
                            System.arraycopy(buf, 0, data, 0, rp.length)
                            val resp = buildUdpResponse(srcIp, dstIp, dstPort, srcPort, data)
                            writeToTun(resp)
                        }
                    } catch (_: Exception) {}
                    finally {
                        udpForwarders.remove(capturedKey)
                        try { finalSock.close() } catch (_: Exception) {}
                    }
                }.start()
            }

            val payload = ByteArray(udpLen)
            System.arraycopy(pkt, udpOffset, payload, 0, udpLen)
            sock.send(DatagramPacket(payload, udpLen, InetAddress.getByAddress(dstIp), dstPort))
        } catch (_: Exception) {}
    }

    private fun buildUdpResponse(srcIp: ByteArray, dstIp: ByteArray, srcPort: Int, dstPort: Int, data: ByteArray): ByteArray {
        return if (srcIp.size == 16) {
            buildUdp6Response(dstIp, srcIp, dstPort, srcPort, data)
        } else {
            buildUdp4Response(srcIp, dstIp, srcPort, dstPort, data)
        }
    }

    private fun buildUdp4Response(srcIp: ByteArray, dstIp: ByteArray, srcPort: Int, dstPort: Int, data: ByteArray): ByteArray {
        val ipLen = 20; val udpLen = 8; val total = ipLen + udpLen + data.size
        val b = ByteBuffer.allocate(total)
        b.put(0x45.toByte()); b.put(0x00.toByte())
        b.putShort(2, total.toShort()); b.putInt(4, 0)
        b.put(8, 64.toByte()); b.put(9, 17.toByte()); b.putShort(10, 0)
        b.position(12); b.put(dstIp); b.position(16); b.put(srcIp)
        var sum = 0L
        for (i in 0 until ipLen step 2) sum += (b.getShort(i).toInt() and 0xFFFF).toLong()
        while ((sum shr 16) > 0) sum = (sum and 0xFFFF) + (sum shr 16)
        b.putShort(10, ((sum.toInt() xor 0xFFFF) and 0xFFFF).toShort())
        b.position(ipLen)
        b.putShort(srcPort.toShort()); b.putShort(dstPort.toShort())
        b.putShort((udpLen + data.size).toShort()); b.putShort(0)
        b.put(data)
        return b.array()
    }

    private fun buildUdp6Response(srcIp: ByteArray, dstIp: ByteArray, srcPort: Int, dstPort: Int, data: ByteArray): ByteArray {
        val udpLen = 8 + data.size
        val total = 40 + udpLen
        val b = ByteBuffer.allocate(total)
        b.put(0x60.toByte()); b.put(0x00.toByte()); b.put(0x00.toByte()); b.put(0x00.toByte())
        b.putShort(4, udpLen.toShort())
        b.put(6, 17.toByte()); b.put(7, 64.toByte())
        b.position(8); b.put(srcIp)
        b.position(24); b.put(dstIp)
        b.position(40)
        b.putShort(srcPort.toShort()); b.putShort(dstPort.toShort())
        b.putShort(udpLen.toShort()); b.putShort(0)
        b.put(data)
        return b.array()
    }

    private fun handleTcp(pkt: ByteArray, srcIp: ByteArray, dstIp: ByteArray, ihl: Int) {
        try {
            if (pkt.size < 40) return
            val srcPort = IpUtil.srcPort(pkt, ihl)
            val dstPort = IpUtil.dstPort(pkt, ihl)
            val flags = IpUtil.tcpFlags(pkt, ihl)
            val seq = IpUtil.tcpSeq(pkt, ihl)

            val clientKey = IpUtil.TcpKey(srcIp, srcPort, dstIp, dstPort)
            val serverKey = IpUtil.TcpKey(dstIp, dstPort, srcIp, srcPort)

            if ((flags and IpUtil.TCP_SYN) != 0 && (flags and IpUtil.TCP_ACK) == 0) {
                val old = tcpConnections[clientKey]
                if (old != null) {
                    try { old.remoteSocket?.close() } catch (_: Exception) {}
                    cleanupTcp(old)
                }
                val state = IpUtil.TcpState().apply {
                    this.srcIp = srcIp; this.srcPort = srcPort
                    this.dstIp = dstIp; this.dstPort = dstPort
                    clientSeq = seq
                    serverSeq = (System.currentTimeMillis() / 1000) and 0xFFFFFFFFL
                    serverAck = (seq + 1) and 0xFFFFFFFFL
                }
                tcpConnections[clientKey] = state
                tcpConnections[serverKey] = state
                val synAck = IpUtil.createTcpSynAck(dstIp, srcIp, dstPort, srcPort, state.serverSeq, state.clientSeq + 1)
                writeToTun(synAck)
                return
            }

            val state = tcpConnections[clientKey] ?: tcpConnections[serverKey] ?: return

            if ((flags and IpUtil.TCP_RST) != 0) {
                cleanupTcp(state)
                return
            }

            val dataLen = IpUtil.tcpDataLen(pkt, ihl)

            if (!state.established) {
                if ((flags and IpUtil.TCP_ACK) != 0) {
                    state.established = true
                    state.clientAck = seq
                    if ((dstPort != 443 && dstPort != 80) || state.blocked) {
                        connectRemote(state, pkt, ihl)
                        return
                    }
                    if (dataLen <= 0) return
                } else {
                    return
                }
            }

            if (dataLen <= 0) return

            val dataOffset = IpUtil.tcpDataOffset(pkt, ihl)
            val payload = pkt.copyOfRange(dataOffset, dataOffset + dataLen)
            state.clientSeq = seq

            if (!state.sniChecked && !state.blocked) {
                state.sniChecked = true
                val domain = if (dstPort == 443) TlsSniffer.extractSni(payload)
                    else extractHttpHost(payload)
                if (domain != null) {
                    val lower = domain.lowercase().removePrefix("www.")
                    val blockResult = checkBlock(lower)
                    if (blockResult.blocked) {
                        state.blocked = true
                        blockedCount.incrementAndGet()
                        lastBlockedDomain = lower
                        saveBlockStats(lower)
                        updateNotification()
                        val rst = IpUtil.createTcpRst(dstIp, srcIp, dstPort, srcPort,
                            state.serverSeq, (seq + dataLen) and 0xFFFFFFFFL)
                        writeToTun(rst)
                        cleanupTcp(state)
                        return
                    }
                }
                if (!state.blocked) {
                    connectRemote(state, pkt, ihl)
                    return
                }
                return
            }

            if (state.remoteOut != null && !state.blocked) {
                try { state.remoteOut!!.write(payload); state.remoteOut!!.flush() } catch (_: Exception) {
                    cleanupTcp(state)
                }
            }
        } catch (_: Exception) {}
    }

    private data class SimpleBlockResult(val blocked: Boolean, val reason: String = "")

    private fun checkBlock(domain: String): SimpleBlockResult {
        val lower = domain
        if (BlocklistDatabase.SAFE_DOMAINS.any { safe ->
                val clean = safe.lowercase().removePrefix("www.")
                lower == clean || lower.endsWith(".$clean")
            }) return SimpleBlockResult(false)
        if (whitelist.any { w -> lower == w || lower.endsWith(".$w") })
            return SimpleBlockResult(false)
        if (BlocklistDatabase.DOH_DOMAINS.any { doh ->
                val clean = doh.lowercase().removePrefix("www.")
                lower == clean || lower.endsWith(".$clean")
            }) return SimpleBlockResult(true, "DoH")
        if (customBlockList.any { custom ->
                val clean = custom.lowercase().removePrefix("www.")
                lower == clean || lower.endsWith(".$clean")
            }) return SimpleBlockResult(true, "Custom")
        if (importedHosts.any { h ->
                val clean = h.lowercase().removePrefix("www.")
                lower == clean || lower.endsWith(".$clean")
            }) return SimpleBlockResult(true, "Imported")
        if (BlocklistDatabase.AGGRESSIVE_KEYWORDS.any { lower.contains(it.lowercase()) })
            return SimpleBlockResult(true, "Keyword")
        if (gamblingDomains.any { gd ->
                val clean = gd.lowercase().removePrefix("www.")
                lower == clean || lower.endsWith(".$clean")
            }) return SimpleBlockResult(true, "Gambling")
        if (gamblingKeywords.any { lower.contains(it.lowercase()) })
            return SimpleBlockResult(true, "Gambling keyword")
        if (blockAdult) {
            if (adultBlockList.any { adult ->
                    val clean = adult.lowercase().removePrefix("www.")
                    lower == clean || lower.endsWith(".$clean")
                }) return SimpleBlockResult(true, "Adult")
            if (adultKeywords.any { lower.contains(it.lowercase()) })
                return SimpleBlockResult(true, "Adult keyword")
        }
        if (BlocklistDatabase.AD_DOMAINS.any { ad ->
                val clean = ad.lowercase().removePrefix("www.")
                lower == clean || lower.endsWith(".$clean")
            }) return SimpleBlockResult(true, "Ad")
        return SimpleBlockResult(false)
    }

    private fun extractHttpHost(data: ByteArray): String? {
        val str = String(data, 0, minOf(data.size, 2048))
        val lines = str.split("\r\n")
        for (line in lines) {
            if (line.lowercase().startsWith("host:")) {
                return line.substring(5).trim()
            }
        }
        val firstLine = lines.firstOrNull() ?: return null
        val parts = firstLine.split(" ")
        if (parts.size >= 2) {
            try {
                val url = URL(parts[1])
                return url.host
            } catch (_: Exception) {}
        }
        return null
    }

    private fun connectRemote(state: IpUtil.TcpState, firstPkt: ByteArray? = null, firstIhl: Int = 0) {
        try {
            val sock = Socket()
            protect(sock)
            sock.connect(InetSocketAddress(InetAddress.getByAddress(state.dstIp), state.dstPort), 15000)
            sock.soTimeout = 0
            state.remoteSocket = sock
            state.remoteOut = sock.getOutputStream()
            state.remoteIn = sock.getInputStream()

            if (firstPkt != null) {
                val dataLen = IpUtil.tcpDataLen(firstPkt, firstIhl)
                if (dataLen > 0) {
                    val offset = IpUtil.tcpDataOffset(firstPkt, firstIhl)
                    val payload = ByteArray(dataLen)
                    System.arraycopy(firstPkt, offset, payload, 0, dataLen)
                    state.remoteOut!!.write(payload)
                    state.remoteOut!!.flush()
                }
            }

            threadRelay(state)
        } catch (_: Exception) {
            val rst = IpUtil.createTcpRst(state.dstIp, state.srcIp, state.dstPort, state.srcPort,
                0, 0)
            writeToTun(rst)
            cleanupTcp(state)
        }
    }

    private fun threadRelay(state: IpUtil.TcpState) {
        val sock = state.remoteSocket ?: return
        Thread {
            try {
                val buf = ByteArray(32768)
                var serverSeq = state.serverSeq + 1
                while (running && !state.closed && !sock.isClosed) {
                    val n = sock.getInputStream().read(buf)
                    if (n <= 0) break
                    val data = ByteArray(n)
                    System.arraycopy(buf, 0, data, 0, n)
                    val pkt = IpUtil.createTcpDataPacket(
                        state.dstIp, state.srcIp,
                        state.dstPort, state.srcPort,
                        serverSeq, state.clientSeq,
                        data
                    )
                    serverSeq = (serverSeq + n) and 0xFFFFFFFFL
                    val out = tunOutput
                    if (out != null) {
                        synchronized(outputLock) {
                            try { out.write(pkt); out.flush() } catch (_: Exception) {}
                        }
                    }
                }
            } catch (_: Exception) {}
            finally { cleanupTcp(state) }
        }.apply { isDaemon = true; name = "relay-${state.dstPort}" }.start()
    }

    private fun cleanupTcp(state: IpUtil.TcpState) {
        if (state.closed) return
        state.closed = true
        val clientKey = IpUtil.TcpKey(state.srcIp, state.srcPort, state.dstIp, state.dstPort)
        val serverKey = IpUtil.TcpKey(state.dstIp, state.dstPort, state.srcIp, state.srcPort)
        tcpConnections.remove(clientKey)
        tcpConnections.remove(serverKey)
        try { state.remoteSocket?.close() } catch (_: Exception) {}
    }

    private fun handleDns(pkt: ByteArray, len: Int, ihl: Int) {
        try {
            val isV6 = ihl == 40
            val srcIp = if (isV6) IpUtil.ip6SrcIp(pkt) else IpUtil.srcIp(pkt)
            val dstIp = if (isV6) IpUtil.ip6DstIp(pkt) else IpUtil.dstIp(pkt)
            val srcPort = IpUtil.srcPort(pkt, ihl)

            val dnsStart = ihl + 8
            val dnsLen = len - dnsStart
            if (dnsLen <= 12) return
            val dnsData = ByteArray(dnsLen)
            System.arraycopy(pkt, dnsStart, dnsData, 0, dnsLen)

            val dnsPkt = DnsPacket(ByteBuffer.wrap(dnsData))
            if (dnsPkt.isResponse) return

            val blockResult = dnsPkt.shouldBlock(
                BlocklistDatabase.AD_DOMAINS,
                adultBlockList,
                adultKeywords,
                gamblingDomains,
                gamblingKeywords,
                customBlockList,
                importedHosts,
                BlocklistDatabase.SAFE_DOMAINS,
                blockAdult,
                whitelist
            )

            if (blockResult.blocked) {
                val domain = (dnsPkt.questions.firstOrNull() ?: "unknown").lowercase().removePrefix("www.")
                val resp = dnsPkt.asResponse("0.0.0.0")
                writeDnsResponse(srcIp, srcPort, dstIp, 53, resp, isV6)
                blockedCount.incrementAndGet()
                lastBlockedDomain = domain
                updateNotification()
                saveBlockStats(domain)
            } else if (dnsPkt.questionTypes.any { it == 64 || it == 65 }) {
                val resp = dnsPkt.asEmptyResponse()
                writeDnsResponse(srcIp, srcPort, dstIp, 53, resp, isV6)
            } else {
                val sock = DatagramSocket()
                try {
                    protect(sock)
                    sock.soTimeout = 5000
                    sock.send(DatagramPacket(dnsData, dnsLen, InetAddress.getByAddress(dstIp), 53))
                    val rb = ByteArray(512); val rp = DatagramPacket(rb, rb.size)
                    sock.receive(rp)
                    val rd = ByteArray(rp.length)
                    System.arraycopy(rb, 0, rd, 0, rp.length)
                    writeDnsResponse(srcIp, srcPort, dstIp, 53, ByteBuffer.wrap(rd), isV6)
                } catch (_: Exception) {} finally { sock.close() }
            }
        } catch (_: Exception) {}
    }

    private fun writeDnsResponse(srcIp: ByteArray, srcPort: Int, dstIp: ByteArray, dstPort: Int, payload: ByteBuffer, isV6: Boolean = false) {
        val out = tunOutput ?: return
        try {
            if (isV6) {
                val udpLen = 8 + payload.remaining()
                val total = 40 + udpLen
                val b = ByteBuffer.allocate(total)
                b.put(0x60.toByte()); b.put(0x00.toByte()); b.put(0x00.toByte()); b.put(0x00.toByte())
                b.putShort(4, udpLen.toShort())
                b.put(6, 17.toByte()); b.put(7, 64.toByte())
                b.position(8); b.put(dstIp)
                b.position(24); b.put(srcIp)
                b.position(40)
                b.putShort(dstPort.toShort()); b.putShort(srcPort.toShort())
                b.putShort(udpLen.toShort()); b.putShort(0)
                b.put(payload)
                synchronized(outputLock) { out.write(b.array(), 0, total); out.flush() }
            } else {
                val ipLen = 20; val udpLen = 8; val total = ipLen + udpLen + payload.remaining()
                val b = ByteBuffer.allocate(total)
                b.put(0x45.toByte()); b.put(0x00.toByte())
                b.putShort(2, total.toShort()); b.putInt(4, 0)
                b.put(8, 64.toByte()); b.put(9, 17.toByte()); b.putShort(10, 0)
                b.position(12); b.put(dstIp); b.position(16); b.put(srcIp)
                var sum = 0L
                for (i in 0 until ipLen step 2) sum += (b.getShort(i).toInt() and 0xFFFF).toLong()
                while ((sum shr 16) > 0) sum = (sum and 0xFFFF) + (sum shr 16)
                b.putShort(10, ((sum.toInt() xor 0xFFFF) and 0xFFFF).toShort())
                b.position(ipLen)
                b.putShort(dstPort.toShort()); b.putShort(srcPort.toShort())
                b.putShort((udpLen + payload.remaining()).toShort()); b.putShort(0)
                b.put(payload)
                synchronized(outputLock) { out.write(b.array(), 0, total); out.flush() }
            }
        } catch (_: Exception) {}
    }

    private fun saveBlockStats(domain: String?) {
        if (domain == null) return
        try {
            val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val total = prefs.getInt("stat_total_blocked", 0) + 1
            val todayCount = prefs.getInt("stat_today_$today", 0) + 1
            val week = (prefs.getStringSet("stat_week_domains", mutableSetOf()) ?: mutableSetOf()).toMutableSet()
            week.add(domain)
            val topSites = prefs.getString("stat_top_sites", "{}") ?: "{}"
            val siteCounts = mutableMapOf<String, Int>()
            try {
                topSites.removeSurrounding("{", "}").split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) siteCounts[parts[0].trim().removeSurrounding("\"")] = parts[1].trim().toIntOrNull() ?: 0
                }
            } catch (_: Exception) {}
            siteCounts[domain] = (siteCounts[domain] ?: 0) + 1
            prefs.edit()
                .putInt("stat_total_blocked", total)
                .putInt("stat_today_$today", todayCount)
                .putStringSet("stat_week_domains", week)
                .putString("stat_top_sites", siteCounts.entries.joinToString(",") { "\"${it.key}\":${it.value}" }.let { "{$it}" })
                .apply()
        } catch (_: Exception) {}
    }

    private fun updateNotification() {
        try {
            val text = if (lastBlockedDomain != null)
                "${blockedCount.get()} blocked \u2022 $lastBlockedDomain"
            else
                "${blockedCount.get()} blocked"
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Blocker+ Active")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_menu_manage)
                    .setContentIntent(pi).setOngoing(true).build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
                    .setContentTitle("Blocker+ Active")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_menu_manage)
                    .setContentIntent(pi).setOngoing(true).build()
            }
            startForeground(NOTIFICATION_ID, n)
        } catch (_: Exception) {}
    }
}
