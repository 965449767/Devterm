package com.devterm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.devterm.MainActivity

class TerminalService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    private val binder = LocalBinder()

    inner class LocalBinder : android.os.Binder() {
        fun getService(): TerminalService = this@TerminalService
    }

    override fun onCreate() {
        super.onCreate()
        setupNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            WakeLockReceiver.ACTION_TOGGLE_WAKELOCK -> toggleWakeLock()
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun toggleWakeLock() {
        if (wakeLock != null) {
            releaseWakeLock()
        } else {
            acquireWakeLock()
        }
    }

    fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "${packageName}:terminal-wakelock"
        )
        wakeLock?.acquire(10 * 60 * 1000L)
        updateNotification()
    }

    fun releaseWakeLock() {
        wakeLock?.release()
        wakeLock = null
        updateNotification()
    }

    fun isWakeLockHeld(): Boolean = wakeLock != null

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(WakeLockReceiver.ACTION_TOGGLE_WAKELOCK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val batteryIntent = PendingIntent.getBroadcast(
            this, 2,
            Intent(WakeLockReceiver.ACTION_REQUEST_BATTERY_OPT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wlLabel = if (wakeLock != null) "WakeLock ON" else "WakeLock OFF"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DevTerm")
            .setContentText("Terminal running")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, wlLabel, toggleIntent)
            .addAction(0, "Battery Opt", batteryIntent)
            .build()
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DevTerm Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                setSound(null, null)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "devterm_service_channel"

        fun start(context: Context) {
            val intent = Intent(context, TerminalService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TerminalService::class.java))
        }
    }
}
