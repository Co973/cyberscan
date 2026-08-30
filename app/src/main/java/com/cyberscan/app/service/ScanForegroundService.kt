package com.cyberscan.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.cyberscan.app.R
import com.cyberscan.app.core.shell.AppProcessRegistry
import com.cyberscan.app.core.shell.CommandExecutor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ScanForegroundService : Service() {
    @Inject lateinit var controller: ScanSessionController
    @Inject lateinit var processRegistry: AppProcessRegistry
    @Inject lateinit var commandExecutor: CommandExecutor

    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "CyberScan hardware scan",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows when CyberScan is actively monitoring nearby hardware"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                cleanup(removeNotification = true)
                stopSelf()
            }
            else -> {
                promoteToForeground()
                controller.start()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        cleanup(removeNotification = true)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        cleanup(removeNotification = true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground() {
        if (foregroundStarted) return
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_scan_status)
            .setContentTitle("CYBERSCAN // ACTIVE")
            .setContentText("Bluetooth and network correlation in progress")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        foregroundStarted = true
    }

    private fun cleanup(removeNotification: Boolean) {
        controller.stop()
        processRegistry.killAll()
        commandExecutor.shutdown()
        if (foregroundStarted && removeNotification) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
    }

    companion object {
        const val ACTION_START = "com.cyberscan.app.action.START_SCAN"
        const val ACTION_STOP = "com.cyberscan.app.action.STOP_SCAN"
        const val CHANNEL_ID = "cyberscan_hardware_scan"
        const val NOTIFICATION_ID = 7017
    }
}

