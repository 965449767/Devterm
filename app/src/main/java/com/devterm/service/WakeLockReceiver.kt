package com.devterm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.net.Uri

class WakeLockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_WAKELOCK -> {
                val intent = Intent(context, TerminalService::class.java).apply {
                    action = ACTION_TOGGLE_WAKELOCK
                }
                context.startService(intent)
            }
            ACTION_REQUEST_BATTERY_OPT -> {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                ).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_WAKELOCK = "com.devterm.action.TOGGLE_WAKELOCK"
        const val ACTION_REQUEST_BATTERY_OPT = "com.devterm.action.REQUEST_BATTERY_OPT"
    }
}
