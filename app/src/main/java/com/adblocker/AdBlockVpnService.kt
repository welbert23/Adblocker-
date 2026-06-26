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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class AdBlockVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var running = false
    @Volatile private var blockAdult = true
    private val blockedCount = AtomicLong(0)
    @Volatile private var lastBlockedDomain: String? = null
    private var proxyServer: ProxyServer? = null

    private var customBlockList: Set<String> = emptySet()
    private var adultBlockList: Set<String> = BlocklistDatabase.ADULT_DOMAINS
    private var adultKeywords: Set<String> = BlocklistDatabase.ADULT_KEYWORDS
    private var gamblingKeywords: Set<String> = BlocklistDatabase.GAMBLING_KEYWORDS

    companion object {
        const val ACTION_START = "com.adblocker.START"
        const val ACTION_STOP = "com.adblocker.STOP"
        const val ACTION_UPDATE_SETTINGS = "com.adblocker.UPDATE_SETTINGS"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "adblocker_vpn"
        const val PROXY_PORT = 9898
    }

    override fun onCreate() {
        super.onCreate()
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
        blockAdult = prefs.getBoolean("adult_blocking", true)
        customBlockList = loadCustomBlocklist(prefs)
        adultBlockList = BlocklistDatabase.ADULT_DOMAINS
        adultKeywords = BlocklistDatabase.ADULT_KEYWORDS
        gamblingKeywords = BlocklistDatabase.GAMBLING_KEYWORDS
        if (running) restartProxy()
    }

    private fun loadCustomBlocklist(prefs: SharedPreferences): Set<String> {
        val json = prefs.getString("custom_blocklist", "[]") ?: "[]"
        return try {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) {
                trimmed.removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"").lowercase() }
                    .filter { it.isNotBlank() }
                    .toSet()
            } else emptySet()
        } catch (_: Exception) { emptySet() }
    }

    private fun getMergedBlocklist(): Set<String> {
        val merged = mutableSetOf<String>()
        merged.addAll(BlocklistDatabase.AD_DOMAINS)
        merged.addAll(BlocklistDatabase.DOH_DOMAINS)
        if (blockAdult) {
            merged.addAll(adultBlockList)
        }
        merged.addAll(customBlockList)
        return merged
    }

    private fun restartProxy() {
        try {
            proxyServer?.stop()
            proxyServer = ProxyServer(getMergedBlocklist(), PROXY_PORT).also { it.start() }
        } catch (_: Exception) {}
    }

    private fun startVpn() {
        try {
            loadSettings()

            proxyServer?.stop()
            proxyServer = ProxyServer(getMergedBlocklist(), PROXY_PORT).also { it.start() }

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Blocker+ Active")
                    .setContentText("${blockedCount.get()} blocked")
                    .setSmallIcon(android.R.drawable.ic_menu_manage)
                    .setContentIntent(pi).setOngoing(true).build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
                    .setContentTitle("Blocker+ Active")
                    .setContentText("${blockedCount.get()} blocked")
                    .setSmallIcon(android.R.drawable.ic_menu_manage)
                    .setContentIntent(pi).setOngoing(true).build()
            }
            startForeground(NOTIFICATION_ID, notif)

            val b = Builder().apply {
                setSession("Blocker+")
                setMtu(1500)
                addAddress("10.0.1.1", 32)
                addAddress("fd00:1:2:3::1", 126)
                addRoute("8.8.8.8", 32)
                addRoute("1.1.1.1", 32)
                if (Build.VERSION.SDK_INT >= 21)
                    addRoute("2001:4860:4860::8888", 128)
                addDnsServer("8.8.8.8")
                addDnsServer("1.1.1.1")
                if (Build.VERSION.SDK_INT >= 21)
                    addDnsServer("2001:4860:4860::8888")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    addDisallowedApplication(packageName)
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
        vpnInterface?.close(); vpnInterface = null
        proxyServer?.stop(); proxyServer = null
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    private fun vpnLoop() {
        val input = FileInputStream(vpnInterface?.fileDescriptor)
        val output = FileOutputStream(vpnInterface?.fileDescriptor)
        val pkt = ByteArray(65535)

        while (running) {
            try {
                val len = input.read(pkt)
                if (len <= 0) continue
                val buf = ByteBuffer.wrap(pkt, 0, len)
                val version = (buf.get(0).toInt() shr 4) and 0x0F

                if (version == 4) {
                    val ihl = (buf.get(0).toInt() and 0x0F) * 4
                    val proto = buf.get(9).toInt() and 0xFF

                    if (proto == 17) {
                        val dstPort = buf.getShort(ihl + 2).toInt() and 0xFFFF
                        if (dstPort == 53) {
                            handleDns(pkt, len, ihl, output)
                            continue
                        }
                    }
                }

                output.write(pkt, 0, len)
                output.flush()
            } catch (_: Exception) { if (!running) break }
        }
        input.close(); output.close()
    }

    private fun handleDns(pkt: ByteArray, len: Int, ihl: Int, out: FileOutputStream) {
        try {
            val buf = ByteBuffer.wrap(pkt, 0, len)
            val totalLen = buf.getShort(2).toInt() and 0xFFFF
            val srcIp = ByteArray(4); val dstIp = ByteArray(4)
            buf.position(12); buf.get(srcIp)
            buf.position(16); buf.get(dstIp)
            val srcPort = ((pkt[ihl].toInt() and 0xFF) shl 8) or (pkt[ihl + 1].toInt() and 0xFF)

            val dnsStart = ihl + 8
            val dnsLen = totalLen - dnsStart
            if (dnsLen <= 12) return
            buf.position(dnsStart)
            val dnsData = ByteArray(dnsLen)
            buf.get(dnsData)

            val dnsPkt = DnsPacket(ByteBuffer.wrap(dnsData))
            if (dnsPkt.isResponse) return

            if (dnsPkt.shouldBlock(
                    BlocklistDatabase.AD_DOMAINS,
                    adultBlockList,
                    adultKeywords,
                    gamblingKeywords,
                    customBlockList,
                    BlocklistDatabase.SAFE_DOMAINS,
                    blockAdult
                )) {
                val resp = dnsPkt.asResponse("0.0.0.0")
                writeUdpResponse(out, srcIp, srcPort, dstIp, 53, resp)
                blockedCount.incrementAndGet()
                lastBlockedDomain = dnsPkt.questions.firstOrNull()
                updateNotification()
                saveBlockStats(dnsPkt.questions.firstOrNull())
            } else if (dnsPkt.questionTypes.any { it == 64 || it == 65 }) {
                val resp = dnsPkt.asEmptyResponse()
                writeUdpResponse(out, srcIp, srcPort, dstIp, 53, resp)
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
                    writeUdpResponse(out, srcIp, srcPort, dstIp, 53, ByteBuffer.wrap(rd))
                } catch (_: Exception) {} finally { sock.close() }
            }
        } catch (_: Exception) {}
    }

    private fun saveBlockStats(domain: String?) {
        if (domain == null) return
        try {
            val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val total = prefs.getInt("stat_total_blocked", 0) + 1
            val todayCount = prefs.getInt("stat_today_$today", 0) + 1
            val week = prefs.getStringSet("stat_week_domains", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            week.add(domain)

            val topSites = prefs.getString("stat_top_sites", "{}") ?: "{}"
            val siteCounts = mutableMapOf<String, Int>()
            try {
                val entries = topSites.removeSurrounding("{", "}")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                for (entry in entries) {
                    val parts = entry.split(":")
                    if (parts.size == 2) {
                        siteCounts[parts[0].trim().removeSurrounding("\"")] = parts[1].trim().toIntOrNull() ?: 0
                    }
                }
            } catch (_: Exception) {}
            siteCounts[domain] = (siteCounts[domain] ?: 0) + 1
            val topSitesStr = siteCounts.entries.joinToString(",") { "\"${it.key}\":${it.value}" }

            prefs.edit()
                .putInt("stat_total_blocked", total)
                .putInt("stat_today_$today", todayCount)
                .putStringSet("stat_week_domains", week)
                .putString("stat_top_sites", "{$topSitesStr}")
                .apply()
        } catch (_: Exception) {}
    }

    private fun writeUdpResponse(out: FileOutputStream, s: ByteArray, sp: Int,
                                  d: ByteArray, dp: Int, payload: ByteBuffer) {
        try {
            val ipLen = 20; val udpLen = 8; val total = ipLen + udpLen + payload.remaining()
            val b = ByteBuffer.allocate(total)
            b.put(0x45.toByte()); b.put(0x00.toByte())
            b.putShort(2, total.toShort()); b.putInt(4, 0)
            b.put(8, 64.toByte()); b.put(9, 17.toByte()); b.putShort(10, 0)
            b.position(12); b.put(d); b.position(16); b.put(s)
            var sum = 0L
            for (i in 0 until ipLen step 2) sum += (b.getShort(i).toInt() and 0xFFFF).toLong()
            while ((sum shr 16) > 0) sum = (sum and 0xFFFF) + (sum shr 16)
            b.putShort(10, ((sum.toInt() xor 0xFFFF) and 0xFFFF).toShort())
            b.position(ipLen)
            b.putShort(dp.toShort()); b.putShort(sp.toShort())
            b.putShort((udpLen + payload.remaining()).toShort()); b.putShort(0)
            b.put(payload)
            synchronized(out) { out.write(b.array(), 0, total); out.flush() }
        } catch (_: Exception) {}
    }

    private fun updateNotification() {
        try {
            val text = if (lastBlockedDomain != null)
                "${blockedCount.get()} blocked • $lastBlockedDomain"
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
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
        } catch (_: Exception) {}
    }

    fun getBlockedCount(): Long = blockedCount.get()
}
