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
        "gambling_domains.txt" to "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/gambling-only/hosts",
        "doh_domains.txt" to "https://raw.githubusercontent.com/oneoffdallas/dohservers/master/list.txt",
        "safe_domains.txt" to "https://raw.githubusercontent.com/anudeepND/whitelist/master/domains/whitelist.txt",
        "adult_keywords.txt" to "https://raw.githubusercontent.com/searchdaimon/adult-words/master/Adult_words_list_basic.txt",
        "gambling_keywords.txt" to "https://raw.githubusercontent.com/IQAndreas/php-spam-filter/master/blacklist-gambling.txt",
        "aggressive_keywords.txt" to "https://raw.githubusercontent.com/poli0981/Poli-Filter-Rules/main/blocks/block-tracking_sorted.txt"
    )

    private val KEYWORD_FILES = setOf("gambling_keywords.txt", "aggressive_keywords.txt")
    private val NO_REMOTE_SOURCE = emptySet<String>()

    fun updateAll(context: Context, onProgress: (String) -> Unit = {}, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        Thread {
            try {
                var success = true
                val results = mutableListOf<String>()
                for ((filename, url) in BLOCKLIST_URLS) {
                    onProgress("Downloading $filename...")
                    try {
                        val count = if (filename in KEYWORD_FILES) {
                            downloadKeywordList(context, filename, url)
                        } else {
                            val requireDot = filename != "adult_keywords.txt"
                            val stripNumber = filename == "adult_keywords.txt"
                            downloadBlocklist(context, filename, url, requireDot, stripNumber)
                        }
                        results.add("$filename: $count entries")
                        Log.i(TAG, "Updated $filename from $url ($count entries)")
                    } catch (e: Exception) {
                        success = false
                        val msg = e.message ?: "Unknown error"
                        results.add("$filename: FAILED ($msg)")
                        Log.e(TAG, "Failed to download $filename: $msg")
                    }
                }
                for (filename in NO_REMOTE_SOURCE) {
                    results.add("$filename: no remote source")
                }
                BlocklistDatabase.reload()
                onDone(success, results.joinToString("\n"))
            } catch (e: Exception) {
                onDone(false, "Update failed: ${e.message}")
            }
        }.apply { isDaemon = true; name = "blocklist-updater" }.start()
    }

    private fun downloadBlocklist(context: Context, filename: String, url: String, requireDot: Boolean = true, stripTrailingNumber: Boolean = false): Int {
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
                val entry = trimmed
                    .replace("0.0.0.0 ", "")
                    .replace("127.0.0.1 ", "")
                    .replace("::1 ", "")
                    .trim()
                if (entry.startsWith("!")) return@forEach
                val cleaned = if (stripTrailingNumber) {
                    entry.replace(Regex("""\s+\d+$"""), "").trim()
                } else entry
                if (requireDot && !cleaned.contains(".")) return@forEach
                if (cleaned.isBlank()) return@forEach
                lines.add(cleaned.lowercase().removePrefix("www."))
            }
        }
        conn.disconnect()

        if (lines.isEmpty()) throw Exception("No entries found")

        val outFile = File(context.filesDir, filename)
        outFile.writeText(lines.joinToString("\n"))

        return lines.size
    }

    private val COMMON_NOISE = setOf(
        "com", "net", "org", "uk", "au", "de", "fr", "it", "es", "ru", "jp", "cn", "br",
        "info", "biz", "tv", "me", "cc", "io", "xyz", "top", "online", "site",
        "club", "app", "dev", "blog", "page", "gov", "edu", "mil", "int", "pro",
        "name", "mobi", "xxx", "asia", "tel", "eu", "nl", "pl", "se", "no", "dk",
        "fi", "be", "at", "ch", "pt", "ca", "mx", "ar", "cl", "in", "kr", "hk",
        "sg", "my", "th", "ph", "id", "vn", "nz", "za", "ng", "eg", "il", "sa",
        "ae", "tr", "gr", "cz", "sk", "hu", "ro", "bg", "rs", "hr", "si", "lt",
        "lv", "ee", "is", "lu", "mt", "cy", "web", "html", "php", "asp", "jsp",
        "www", "amp", "js", "css", "png", "jpg", "gif", "svg", "ico", "xml",
        "json", "rss", "atom", "txt", "pdf", "doc", "xls", "ppt",
        "href", "http", "https", "this", "that", "with", "from", "have",
        "span", "div", "class", "true", "false", "null", "none", "auto",
        "file", "open", "data", "type", "size", "name", "time", "text"
    )

    private fun downloadKeywordList(context: Context, filename: String, url: String): Int {
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

        val keywords = mutableSetOf<String>()
        val wordPattern = Regex("[a-zA-Z]{4,}")
        BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
            reader.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isBlank()) return@forEach
                if (trimmed.startsWith("#") || trimmed.startsWith("!")) return@forEach

                val stripped = trimmed
                    .replace(Regex("^0\\.0\\.0\\.0\\s+"), "")
                    .replace(Regex("^127\\.0\\.0\\.1\\s+"), "")
                    .replace(Regex("^::1\\s+"), "")
                    .replace(Regex("^\\|\\|"), "")
                    .replace(Regex("[\\^\\$].*$"), "")
                    .replace(Regex("^@@\\|\\|"), "")
                    .replace(Regex("^##"), "")
                    .replace(Regex("[.*+?(){|\\[\\]}\\\\\\/\\-]"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .lowercase()

                wordPattern.findAll(stripped).forEach { match ->
                    val word = match.value
                    if (word.length >= 4 && word !in COMMON_NOISE) {
                        keywords.add(word)
                    }
                }
            }
        }
        conn.disconnect()

        if (keywords.isEmpty()) throw Exception("No keywords found")

        val outFile = File(context.filesDir, filename)
        outFile.writeText(keywords.sorted().joinToString("\n"))

        return keywords.size
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

        val imported = File(context.filesDir, "imported_hosts.txt")
        val allDomains = if (imported.exists()) {
            imported.readLines().toMutableSet().apply { addAll(domains) }
        } else domains

        imported.writeText(allDomains.joinToString("\n"))
        BlocklistDatabase.reload()
        return domains
    }
}
