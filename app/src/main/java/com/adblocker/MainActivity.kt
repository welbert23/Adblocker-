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
    private lateinit var panelSettings: ScrollView

    private lateinit var inputCustomDomain: EditText
    private lateinit var btnAddDomain: Button
    private lateinit var customDomainList: TextView

    private lateinit var settingsWhitelistInput: EditText
    private lateinit var settingsBtnAddWhitelist: Button
    private lateinit var settingsWhitelistList: TextView
    private lateinit var settingsBtnDns: Button
    private lateinit var settingsBtnPerApp: Button
    private lateinit var settingsBtnImportHosts: Button
    private lateinit var settingsBtnUpdateBlocklists: Button
    private lateinit var settingsBtnClearStats: Button

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

        BlocklistDatabase.init(this)

        initViews()
        setupListeners()
        loadCustomDomains()
        loadWhitelist()
        panelSettings.visibility = android.view.View.VISIBLE
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

        panelSettings = findViewById(R.id.panelSettings)

        inputCustomDomain = findViewById(R.id.inputCustomDomain)
        btnAddDomain = findViewById(R.id.btnAddDomain)
        customDomainList = findViewById(R.id.customDomainList)

        settingsWhitelistInput = findViewById(R.id.settingsWhitelistInput)
        settingsBtnAddWhitelist = findViewById(R.id.settingsBtnAddWhitelist)
        settingsWhitelistList = findViewById(R.id.settingsWhitelistList)
        settingsBtnDns = findViewById(R.id.settingsBtnDns)
        settingsBtnPerApp = findViewById(R.id.settingsBtnPerApp)
        settingsBtnImportHosts = findViewById(R.id.settingsBtnImportHosts)
        settingsBtnUpdateBlocklists = findViewById(R.id.settingsBtnUpdateBlocklists)
        settingsBtnClearStats = findViewById(R.id.settingsBtnClearStats)
    }

    private fun setupListeners() {
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
        customDomainList.setOnLongClickListener {
            if (customDomainList.text.isNotEmpty() && customDomainList.text != "No custom domains added yet\n\nType a domain and tap +") {
                showRemoveDomainDialog()
            }
            true
        }

        settingsBtnAddWhitelist.setOnClickListener { addWhitelistDomain() }
        settingsWhitelistList.setOnLongClickListener {
            showRemoveWhitelistDialog()
            true
        }
        settingsBtnDns.setOnClickListener { showDnsPicker() }
        settingsBtnPerApp.setOnClickListener {
            startActivity(Intent(this, PerAppFilterActivity::class.java))
        }
        settingsBtnImportHosts.setOnClickListener { showHostsImportDialog() }
        settingsBtnUpdateBlocklists.setOnClickListener { updateBlocklists() }
        settingsBtnClearStats.setOnClickListener { clearStats() }
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
            customDomainList.text = "No custom domains added yet\n\nType a domain and tap +"
        } else {
            customDomainList.text = domains.joinToString("\n") { "\u2022 $it" }
        }
    }

    private fun addWhitelistDomain() {
        var domain = settingsWhitelistInput.text.toString().trim().lowercase()
        if (domain.isEmpty()) {
            Toast.makeText(this, "Enter a domain to allow", Toast.LENGTH_SHORT).show()
            return
        }
        domain = domain.removePrefix("http://").removePrefix("https://").removePrefix("www.")
        domain = domain.split("/").first().trim()
        if (domain.isEmpty() || !domain.contains(".")) {
            Toast.makeText(this, "Invalid domain", Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
        val whitelist = BlocklistDatabase.loadWhitelist(prefs).toMutableSet()
        if (whitelist.contains(domain)) {
            Toast.makeText(this, "Domain already in whitelist", Toast.LENGTH_SHORT).show()
            return
        }
        whitelist.add(domain)
        BlocklistDatabase.saveWhitelist(prefs, whitelist)
        settingsWhitelistInput.text.clear()
        loadWhitelist()
        if (isRunning) {
            Intent(this, AdBlockVpnService::class.java).apply {
                action = AdBlockVpnService.ACTION_UPDATE_SETTINGS
                startService(this)
            }
        }
        Toast.makeText(this, "Added $domain to whitelist", Toast.LENGTH_SHORT).show()
    }

    private fun showRemoveWhitelistDialog() {
        val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
        val whitelist = BlocklistDatabase.loadWhitelist(prefs).toMutableList()
        if (whitelist.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Remove from whitelist")
            .setItems(whitelist.toTypedArray()) { _, which ->
                whitelist.removeAt(which)
                BlocklistDatabase.saveWhitelist(prefs, whitelist.toSet())
                loadWhitelist()
                if (isRunning) {
                    Intent(this, AdBlockVpnService::class.java).apply {
                        action = AdBlockVpnService.ACTION_UPDATE_SETTINGS
                        startService(this)
                    }
                }
                Toast.makeText(this, "Removed from whitelist", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun loadWhitelist() {
        val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
        val whitelist = BlocklistDatabase.loadWhitelist(prefs)
        if (whitelist.isEmpty()) {
            settingsWhitelistList.text = "No whitelisted domains\n\nAdd domains that should bypass blocking"
        } else {
            settingsWhitelistList.text = whitelist.joinToString("\n") { "\u2022 $it" }
        }
    }

    private fun showDnsPicker() {
        val presets = listOf("All", "Google", "Cloudflare", "OpenDNS", "Quad9", "Custom")
        val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
        val current = prefs.getString("dns_preset", "All") ?: "All"

        AlertDialog.Builder(this)
            .setTitle("DNS Server")
            .setSingleChoiceItems(presets.toTypedArray(), presets.indexOf(current).coerceAtLeast(0)) { _, which ->
                val selected = presets[which]
                prefs.edit().putString("dns_preset", selected).apply()
                if (selected == "Custom") {
                    showCustomDnsDialog()
                } else {
                    if (isRunning) {
                        Intent(this, AdBlockVpnService::class.java).apply {
                            action = AdBlockVpnService.ACTION_UPDATE_SETTINGS
                            startService(this)
                        }
                    }
                    Toast.makeText(this, "DNS: $selected", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun showCustomDnsDialog() {
        val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
        val current = prefs.getString("dns_custom", "8.8.8.8,8.8.4.4") ?: "8.8.8.8,8.8.4.4"
        val input = EditText(this).apply {
            setText(current)
            hint = "e.g. 8.8.8.8,8.8.4.4"
        }
        AlertDialog.Builder(this)
            .setTitle("Custom DNS")
            .setMessage("Enter comma-separated DNS IPs")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) {
                    prefs.edit().putString("dns_custom", value).apply()
                    if (isRunning) {
                        Intent(this, AdBlockVpnService::class.java).apply {
                            action = AdBlockVpnService.ACTION_UPDATE_SETTINGS
                            startService(this)
                        }
                    }
                    Toast.makeText(this, "Custom DNS saved", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun showHostsImportDialog() {
        val input = EditText(this).apply {
            hint = "Paste hosts file content here"
            gravity = android.view.Gravity.TOP
            minLines = 8
        }
        AlertDialog.Builder(this)
            .setTitle("Import Hosts File")
            .setMessage("Paste contents of a hosts file or plain domain list")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val content = input.text.toString().trim()
                if (content.isEmpty()) {
                    Toast.makeText(this, "No content to import", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val count = BlocklistUpdater.importHostsFile(this@MainActivity, content).size
                if (count > 0) {
                    Toast.makeText(this, "Imported $count domains", Toast.LENGTH_SHORT).show()
                    if (isRunning) {
                        Intent(this, AdBlockVpnService::class.java).apply {
                            action = AdBlockVpnService.ACTION_UPDATE_SETTINGS
                            startService(this)
                        }
                    }
                } else {
                    Toast.makeText(this, "No valid domains found", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun updateBlocklists() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Updating Blocklists")
            .setMessage("Downloading...")
            .setCancelable(true)
            .setNegativeButton("Cancel") { _, _ -> }
            .create()
            .apply { show() }

        BlocklistUpdater.updateAll(this,
            onProgress = { msg ->
                runOnUiThread {
                    try { dialog.setMessage(msg) } catch (_: Exception) {}
                }
            },
            onDone = { success, result ->
                runOnUiThread {
                    if (dialog.isShowing) {
                        try { dialog.dismiss() } catch (_: Exception) {}
                    }
                    if (!isFinishing) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle(if (success) "Update Complete" else "Update Issues")
                            .setMessage(result)
                            .setPositiveButton("OK", null)
                            .create()
                            .show()
                    }
                }
            }
        )
    }

    private fun clearStats() {
        AlertDialog.Builder(this)
            .setTitle("Clear Statistics")
            .setMessage("Reset all blocking statistics?")
            .setPositiveButton("Clear") { _, _ ->
                val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                prefs.edit()
                    .remove("stat_total_blocked")
                    .remove("stat_today_$today")
                    .remove("stat_week_domains")
                    .remove("stat_top_sites")
                    .apply()
                refreshStats()
                Toast.makeText(this, "Statistics cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun refreshStats() {
        val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
        val total = prefs.getInt("stat_total_blocked", 0)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val todayCount = prefs.getInt("stat_today_$today", 0)
        if (total > 0) {
            statsSummary.text = "$total blocked \u2022 $todayCount today"
        } else {
            statsSummary.text = ""
        }
    }
}
