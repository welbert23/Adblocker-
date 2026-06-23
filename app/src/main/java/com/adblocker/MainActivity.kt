package com.adblocker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var blockedText: TextView
    private lateinit var proxyInfoText: TextView
    private lateinit var toggleSwitch: SwitchMaterial
    private lateinit var startStopButton: Button
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

        statusText = findViewById(R.id.statusText)
        blockedText = findViewById(R.id.blockedText)
        proxyInfoText = findViewById(R.id.proxyInfoText)
        toggleSwitch = findViewById(R.id.toggleSwitch)
        startStopButton = findViewById(R.id.startStopButton)

        toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("blockerplus", MODE_PRIVATE)
            prefs.edit().putBoolean("adult_blocking", isChecked).apply()
            if (isRunning) {
                Intent(this, AdBlockVpnService::class.java).apply {
                    action = AdBlockVpnService.ACTION_UPDATE_SETTINGS
                    startService(this)
                }
            }
        }

        startStopButton.setOnClickListener {
            if (isRunning) stopVpn()
            else startVpn()
        }
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
            blockedText.text = "DNS filtering active"
            proxyInfoText.text = "Proxy :${AdBlockVpnService.PROXY_PORT} | VPN + DNS"
            startStopButton.text = "STOP"
            startStopButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        } else {
            statusText.text = "Unprotected"
            blockedText.text = "Tap Start to enable ad blocking"
            proxyInfoText.text = ""
            startStopButton.text = "START"
            startStopButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        }
    }
}
