package com.adblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("blockerplus", Context.MODE_PRIVATE)
            if (prefs.getBoolean("vpn_enabled", false)) {
                val vpnIntent = Intent(context, AdBlockVpnService::class.java).apply {
                    action = AdBlockVpnService.ACTION_START
                }
                context.startService(vpnIntent)
            }
        }
    }
}
