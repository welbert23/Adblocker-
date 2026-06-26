package com.adblocker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var blockedText: TextView
    private lateinit var statsSummary: TextView
    private lateinit var toggleSwitch: SwitchMaterial
    private lateinit var startStopButton: Button

    private lateinit var tabStats: Button
    private lateinit var tabBlocklist: Button
    private lateinit var panelStats: LinearLayout
    private lateinit var panelBlocklist: LinearLayout

    private lateinit var statTotalBlocked: TextView
    private lateinit var statToday: TextView
    private lateinit var statWeek: TextView
    private lateinit var statBreakdown: TextView
    private lateinit var statTopSites: TextView
    private lateinit var btnExportCsv: Button

    private lateinit var inputCustomDomain: EditText
    private lateinit var btnAddDomain: Button
    private lateinit var customDomainList: TextView

    private var isRunning = false

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            onVpnPrepared()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        refreshStats()
        loadCustomDomains()
        updateTabSelection(0)
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        blockedText = findViewById(R.id.blockedText)
        statsSummary = findViewById(R.id.statsSummary)
        toggleSwitch = findViewById(R.id.toggleSwitch)
        startStopButton = findViewById(R.id.startStopButton)

        tabStats = findViewById(R.id.tabStats)
        tabBlocklist = findViewById(R.id.tabBlocklist)
        panelStats = findViewById(R.id.panelStats)
        panelBlocklist = findViewById(R.id.panelBlocklist)

        statTotalBlocked = findViewById(R.id.statTotalBlocked)
        statToday = findViewById(R.id.statToday)
        statWeek = findViewById(R.id.statWeek)
        statBreakdown = findViewById(R.id.statBreakdown)
        statTopSites = findViewById(R.id.statTopSites)
        btnExportCsv = findViewById(R.id.btnExportCsv)

        inputCustomDomain = findViewById(R.id.inputCustomDomain)
        btnAddDomain = findViewById(R.id.btnAddDomain)
        customDomainList = findViewById(R.id.customDomainList)
    }

    private fun setupListeners() {
        tabStats.setOnClickListener { updateTabSelection(0) }
        tabBlocklist.setOnClickListener { updateTabSelection(1) }

        val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
        toggleSwitch.isChecked = prefs.getBoolean("adult_blocking", true)
        toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("adult_blocking", isChecked).apply()
            if (isRunning) {
                Intent(this, AdBlockVpnService::class.java).apply {
                    action = AdBlockVpnService.ACTION_UPDATE_SETTINGS
                    startService(this)
                }
            }
        }

        startStopButton.setOnClickListener {
            if (isRunning) stopVpn() else startVpn()
        }

        btnAddDomain.setOnClickListener { addCustomDomain() }
        btnExportCsv.setOnClickListener { exportCsv() }

        customDomainList.setOnLongClickListener {
            if (customDomainList.text.isNotEmpty() && customDomainList.text != "No custom domains added yet") {
                showRemoveDomainDialog()
            }
            true
        }
    }

    private fun updateTabSelection(index: Int) {
        val tabs = listOf(tabStats, tabBlocklist)
        val panels = listOf(panelStats, panelBlocklist)
        for (i in tabs.indices) {
            tabs[i].setTextColor(if (i == index) ContextCompat.getColor(this, R.color.button_green) else ContextCompat.getColor(this, android.R.color.darker_gray))
            panels[i].visibility = if (i == index) android.view.View.VISIBLE else android.view.View.GONE
        }
        if (index == 1) loadCustomDomains()
        if (index == 0) refreshStats()
    }

    private fun startVpn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
                return
            }
        }
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            onVpnPrepared()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVpn()
            } else {
                Toast.makeText(this, "Notification permission needed for VPN service", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onVpnPrepared() {
        val intent = Intent(this, AdBlockVpnService::class.java).apply {
            action = AdBlockVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        isRunning = true
        updateUi(true)
    }

    private fun stopVpn() {
        val intent = Intent(this, AdBlockVpnService::class.java).apply {
            action = AdBlockVpnService.ACTION_STOP
        }
        startService(intent)
        isRunning = false
        updateUi(false)
    }

    private fun updateUi(running: Boolean) {
        if (running) {
            statusText.text = "Protected"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            blockedText.text = "DNS filtering active"
            startStopButton.text = "STOP"
            startStopButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        } else {
            statusText.text = "Unprotected"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            blockedText.text = "Tap Start to enable"
            startStopButton.text = "START"
            startStopButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        }
    }

    private fun addCustomDomain() {
        var domain = inputCustomDomain.text.toString().trim().lowercase()
        if (domain.isEmpty()) {
            Toast.makeText(this, "Enter a domain name", Toast.LENGTH_SHORT).show()
            return
        }
        domain = domain.removePrefix("http://").removePrefix("https://").removePrefix("www.")
        domain = domain.split("/").first().split("?").first().trim()
        if (domain.isEmpty() || !domain.contains(".")) {
            Toast.makeText(this, "Invalid domain", Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
        val json = prefs.getString("custom_blocklist", "[]") ?: "[]"
        val domains = mutableSetOf<String>()
        try {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) {
                domains.addAll(trimmed.removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"").lowercase() }
                    .filter { it.isNotBlank() })
            }
        } catch (_: Exception) {}
        if (domains.contains(domain)) {
            Toast.makeText(this, "Domain already in blocklist", Toast.LENGTH_SHORT).show()
            return
        }
        domains.add(domain)
        saveCustomDomains(domains)
        inputCustomDomain.text.clear()
        loadCustomDomains()
        if (isRunning) {
            Intent(this, AdBlockVpnService::class.java).apply {
                action = AdBlockVpnService.ACTION_UPDATE_SETTINGS
                startService(this)
            }
        }
        Toast.makeText(this, "Added $domain to blocklist", Toast.LENGTH_SHORT).show()
    }

    private fun showRemoveDomainDialog() {
        val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
        val json = prefs.getString("custom_blocklist", "[]") ?: "[]"
        val domains = mutableListOf<String>()
        try {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) {
                domains.addAll(trimmed.removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"").lowercase() }
                    .filter { it.isNotBlank() })
            }
        } catch (_: Exception) {}
        if (domains.isEmpty()) return
        val items = domains.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Remove domain")
            .setItems(items) { _, which ->
                domains.removeAt(which)
                saveCustomDomains(domains.toSet())
                loadCustomDomains()
                if (isRunning) {
                    Intent(this, AdBlockVpnService::class.java).apply {
                        action = AdBlockVpnService.ACTION_UPDATE_SETTINGS
                        startService(this)
                    }
                }
                Toast.makeText(this, "Removed from blocklist", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun saveCustomDomains(domains: Set<String>) {
        val json = domains.joinToString(",") { "\"$it\"" }
        getSharedPreferences("blockerplus", MODE_PRIVATE)
            .edit().putString("custom_blocklist", "[$json]").apply()
    }

    private fun loadCustomDomains() {
        val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
        val json = prefs.getString("custom_blocklist", "[]") ?: "[]"
        val domains = mutableListOf<String>()
        try {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) {
                domains.addAll(trimmed.removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotBlank() })
            }
        } catch (_: Exception) {}
        if (domains.isEmpty()) {
            customDomainList.text = "No custom domains added yet\n\nTap + to add a domain\nLong-press this area to remove"
        } else {
            customDomainList.text = domains.joinToString("\n") { "• $it" }
        }
    }

    private fun refreshStats() {
        val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
        val total = prefs.getInt("stat_total_blocked", 0)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val todayCount = prefs.getInt("stat_today_$today", 0)
        val weekDomains = prefs.getStringSet("stat_week_domains", mutableSetOf())?.size ?: 0

        statTotalBlocked.text = total.toString()
        statToday.text = todayCount.toString()
        statWeek.text = weekDomains.toString()
        statBreakdown.text = "Domain: $total  |  Keyword: 0  |  Custom: ${getCustomDomainCount()}"

        val topSites = prefs.getString("stat_top_sites", "{}") ?: "{}"
        val sites = mutableListOf<Pair<String, Int>>()
        try {
            val entries = topSites.removeSurrounding("{", "}")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            for (entry in entries) {
                val parts = entry.split(":")
                if (parts.size == 2) {
                    val name = parts[0].trim().removeSurrounding("\"")
                    val count = parts[1].trim().toIntOrNull() ?: 0
                    sites.add(name to count)
                }
            }
        } catch (_: Exception) {}
        sites.sortByDescending { it.second }

        if (sites.isEmpty()) {
            statTopSites.text = "No blocked sites yet"
        } else {
            statTopSites.text = sites.take(10).mapIndexed { i, (name, count) ->
                "${i + 1}. $name ($count)"
            }.joinToString("\n")
        }

        if (total > 0) {
            statsSummary.text = "$total blocked • $todayCount today"
        } else {
            statsSummary.text = ""
        }
    }

    private fun getCustomDomainCount(): Int {
        val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
        val json = prefs.getString("custom_blocklist", "[]") ?: "[]"
        return try {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) {
                trimmed.removeSurrounding("[", "]")
                    .split(",")
                    .count { it.trim().isNotBlank() }
            } else 0
        } catch (_: Exception) { 0 }
    }

    private fun exportCsv() {
        val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
        val total = prefs.getInt("stat_total_blocked", 0)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val todayCount = prefs.getInt("stat_today_$today", 0)
        val topSites = prefs.getString("stat_top_sites", "{}") ?: "{}"

        val csv = buildString {
            appendLine("Blocker+ Adult Monitoring Report")
            appendLine("Exported: $today")
            appendLine()
            appendLine("Total Blocked,$total")
            appendLine("Today,$todayCount")
            appendLine()
            appendLine("Domain,Count")
            try {
                val entries = topSites.removeSurrounding("{", "}")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                for (entry in entries) {
                    val parts = entry.split(":")
                    if (parts.size == 2) {
                        appendLine("${parts[0].trim().removeSurrounding("\"")},${parts[1].trim()}")
                    }
                }
            } catch (_: Exception) {}
        }

        try {
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(dir, "blockerplus_stats_$today.csv")
            file.writeText(csv)
            Toast.makeText(this, "Exported to Downloads/${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            try {
                val file = java.io.File(cacheDir, "blockerplus_stats_$today.csv")
                file.writeText(csv)
                Toast.makeText(this, "Exported to cache/${file.name}", Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                Toast.makeText(this, "Export failed: ${e2.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
