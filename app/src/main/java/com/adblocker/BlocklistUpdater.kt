package com.adblocker

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object BlocklistUpdater {
    private const val TAG = "BlocklistUpdater"

    private val BLOCKLIST_URLS = mapOf(
        "ad_domains.txt" to "https://raw.githubusercontent.com/anudeepND/blacklist/master/adservers.txt",
        "adult_domains.txt" to "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts",
        "gambling_keywords.txt" to "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/gambling-only/hosts"
    )

    fun updateAll(context: Context, onProgress: (String) -> Unit = {}, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        Thread {
            try {
                var success = true
                val results = mutableListOf<String>()
                for ((filename, url) in BLOCKLIST_URLS) {
                    onProgress("Downloading $filename...")
                    try {
                        val count = downloadBlocklist(context, filename, url)
                        results.add("$filename: $count entries")
                        Log.i(TAG, "Updated $filename from $url ($count entries)")
                    } catch (e: Exception) {
                        success = false
                        val msg = e.message ?: "Unknown error"
                        results.add("$filename: FAILED ($msg)")
                        Log.e(TAG, "Failed to download $filename: $msg")
                    }
                }
                BlocklistDatabase.reload()
                onDone(success, results.joinToString("\n"))
            } catch (e: Exception) {
                onDone(false, "Update failed: ${e.message}")
            }
        }.apply { isDaemon = true; name = "blocklist-updater" }.start()
    }

    private fun downloadBlocklist(context: Context, filename: String, url: String): Int {
        val urlObj = URL(url)
        val conn = urlObj.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true

        val responseCode = conn.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            val error = try { conn.responseMessage } catch (_: Exception) { "Unknown error" }
            conn.disconnect()
            throw Exception("HTTP $responseCode $error")
        }

        val lines = mutableListOf<String>()
        BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
            reader.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isBlank()) return@forEach
                if (trimmed.startsWith("#")) return@forEach
                val domain = trimmed
                    .replace("0.0.0.0 ", "")
                    .replace("127.0.0.1 ", "")
                    .replace("::1 ", "")
                    .trim()
                if (domain.startsWith("!") || !domain.contains(".")) return@forEach
                lines.add(domain.lowercase().removePrefix("www."))
            }
        }
        conn.disconnect()

        if (lines.isEmpty()) throw Exception("No entries found")

        val outFile = File(context.filesDir, filename)
        outFile.writeText(lines.joinToString("\n"))

        return lines.size
    }

    fun importHostsFile(context: Context, content: String): Set<String> {
        val domains = content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val parts = line.split("\\s+".toRegex())
                when {
                    parts.size >= 2 && parts[0].let { it == "0.0.0.0" || it == "127.0.0.1" || it == "::1" } ->
                        parts[1].lowercase().removePrefix("www.")
                    parts.size == 1 && parts[0].contains(".") -> parts[0].lowercase().removePrefix("www.")
                    else -> ""
                }
            }
            .filter { it.isNotBlank() && it.contains(".") }
            .toSet()

        if (domains.isEmpty()) return emptySet()

        val existing = File(context.filesDir, "ad_domains.txt")
        val allDomains = if (existing.exists()) {
            existing.readLines().toMutableSet().apply { addAll(domains) }
        } else domains

        existing.writeText(allDomains.joinToString("\n"))
        BlocklistDatabase.reload()
        return domains
    }
}
