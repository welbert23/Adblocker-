package com.adblocker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File

object BlocklistDatabase {
    private const val TAG = "BlocklistDB"

    lateinit var AD_DOMAINS: Set<String>
    lateinit var ADULT_DOMAINS: Set<String>
    lateinit var ADULT_KEYWORDS: Set<String>
    lateinit var GAMBLING_KEYWORDS: Set<String>
    lateinit var DOH_DOMAINS: Set<String>
    lateinit var AGGRESSIVE_KEYWORDS: Set<String>
    lateinit var SAFE_DOMAINS: Set<String>

    private var initialized = false
    private var appContext: Context? = null

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context

        AD_DOMAINS = loadFromAssetsOrData(context, "ad_domains.txt")
        ADULT_DOMAINS = loadFromAssetsOrData(context, "adult_domains.txt")
        ADULT_KEYWORDS = loadFromAssetsOrData(context, "adult_keywords.txt")
        GAMBLING_KEYWORDS = loadFromAssetsOrData(context, "gambling_keywords.txt")
        DOH_DOMAINS = loadFromAssetsOrData(context, "doh_domains.txt")
        AGGRESSIVE_KEYWORDS = loadFromAssetsOrData(context, "aggressive_keywords.txt")
        SAFE_DOMAINS = loadFromAssetsOrData(context, "safe_domains.txt")

        Log.i(TAG, "Loaded: ${AD_DOMAINS.size} ads, ${ADULT_DOMAINS.size} adult, ${ADULT_KEYWORDS.size} adult-kw, ${GAMBLING_KEYWORDS.size} gambling, ${DOH_DOMAINS.size} doh, ${AGGRESSIVE_KEYWORDS.size} aggressive, ${SAFE_DOMAINS.size} safe")
    }

    private fun loadFromAssetsOrData(context: Context, filename: String): Set<String> {
        val dataFile = File(context.filesDir, filename)
        if (dataFile.exists()) {
            return try {
                dataFile.readLines()
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .toSet()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $filename from data dir: ${e.message}")
                loadFromAssets(context, filename)
            }
        }
        return loadFromAssets(context, filename)
    }

    private fun loadFromAssets(context: Context, filename: String): Set<String> {
        return try {
            context.assets.open(filename)
                .bufferedReader()
                .readLines()
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $filename: ${e.message}")
            emptySet()
        }
    }

    fun reload() {
        initialized = false
        appContext?.let { init(it) }
    }

    fun loadWhitelist(prefs: SharedPreferences): Set<String> {
        val json = prefs.getString("whitelist", "[]") ?: "[]"
        return try {
            json.trim().removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"").lowercase().removePrefix("www.") }
                .filter { it.isNotBlank() }
                .toSet()
        } catch (_: Exception) { emptySet() }
    }

    fun saveWhitelist(prefs: SharedPreferences, domains: Set<String>) {
        val json = domains.joinToString(",") { "\"$it\"" }
        prefs.edit().putString("whitelist", "[$json]").apply()
    }

    fun loadBlockedApps(prefs: SharedPreferences): Set<String> {
        val json = prefs.getString("blocked_apps", "[]") ?: "[]"
        return try {
            json.trim().removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotBlank() }
                .toSet()
        } catch (_: Exception) { emptySet() }
    }

    fun saveBlockedApps(prefs: SharedPreferences, packages: Set<String>) {
        val json = packages.joinToString(",") { "\"$it\"" }
        prefs.edit().putString("blocked_apps", "[$json]").apply()
    }

    fun isInitialized(): Boolean = initialized
}
