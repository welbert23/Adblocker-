package com.adblocker

import android.content.Intent
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class PerAppFilterActivity : AppCompatActivity() {

    private lateinit var appListContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var chkSelectAll: CheckBox
    private var allApps: List<AppInfo> = emptyList()
    private var filteredApps: List<AppInfo> = emptyList()
    private var blockedApps: Set<String> = emptySet()
    private var checkboxes: MutableMap<String, CheckBox> = mutableMapOf()
    private var ignoreSelectAll = false

    data class AppInfo(val name: String, val packageName: String, val icon: android.graphics.drawable.Drawable)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_per_app_filter)

        appListContainer = findViewById(R.id.appListContainer)
        searchInput = findViewById(R.id.searchInput)
        chkSelectAll = findViewById(R.id.chkSelectAll)

        val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
        blockedApps = BlocklistDatabase.loadBlockedApps(prefs)

        findViewById<Button>(R.id.btnSavePerApp).setOnClickListener {
            BlocklistDatabase.saveBlockedApps(prefs, blockedApps)
            Intent(this@PerAppFilterActivity, AdBlockVpnService::class.java).apply {
                action = AdBlockVpnService.ACTION_UPDATE_SETTINGS
                startService(this)
            }
            Toast.makeText(this, "Per-app settings saved. VPN will restart.", Toast.LENGTH_SHORT).show()
            finish()
        }

        loadApps()

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s?.toString()?.lowercase() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        chkSelectAll.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreSelectAll) return@setOnCheckedChangeListener
            for (app in filteredApps) {
                val cb = checkboxes[app.packageName] ?: continue
                cb.isChecked = isChecked
            }
        }
    }

    private fun loadApps() {
        AsyncTask.execute {
            val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }
                .map { app ->
                    AppInfo(
                        name = packageManager.getApplicationLabel(app).toString(),
                        packageName = app.packageName,
                        icon = app.loadIcon(packageManager)
                    )
                }
            runOnUiThread {
                allApps = apps
                filterApps("")
            }
        }
    }

    private fun filterApps(query: String) {
        filteredApps = if (query.isEmpty()) allApps
        else allApps.filter { it.name.lowercase().contains(query) || it.packageName.lowercase().contains(query) }
        renderAppList()
    }

    private fun renderAppList() {
        checkboxes.clear()
        appListContainer.removeAllViews()
        for (app in filteredApps) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 2) }
                setPadding(8, 8, 8, 8)
                background = android.graphics.drawable.ColorDrawable(0xFF222244.toInt())
            }

            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(40, 40).apply { setMargins(0, 0, 12, 0) }
                setImageDrawable(app.icon)
            }

            val nameText = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = app.name
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
                maxLines = 1
            }

            val pkgText = TextView(this).apply {
                text = app.packageName
                setTextColor(0xFF8888AA.toInt())
                textSize = 11f
                maxLines = 1
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(nameText)
                addView(pkgText)
            }

            val check = CheckBox(this).apply {
                isChecked = blockedApps.contains(app.packageName)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) blockedApps = blockedApps + app.packageName
                    else blockedApps = blockedApps - app.packageName
                    updateSelectAllState()
                }
            }
            checkboxes[app.packageName] = check

            row.setOnClickListener { check.isChecked = !check.isChecked }
            row.addView(icon)
            row.addView(textCol)
            row.addView(check)
            appListContainer.addView(row)
        }

        if (filteredApps.isEmpty()) {
            TextView(this).apply {
                text = "No apps found"
                setTextColor(0xFF8888AA.toInt())
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    200
                )
            }.let { appListContainer.addView(it) }
        }
        updateSelectAllState()
    }

    private fun updateSelectAllState() {
        if (filteredApps.isEmpty()) {
            ignoreSelectAll = true
            chkSelectAll.isChecked = false
            chkSelectAll.isEnabled = false
            ignoreSelectAll = false
            return
        }
        chkSelectAll.isEnabled = true
        val allChecked = filteredApps.all { blockedApps.contains(it.packageName) }
        ignoreSelectAll = true
        chkSelectAll.isChecked = allChecked
        ignoreSelectAll = false
    }
}
