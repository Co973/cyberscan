package com.cyberscan.app.service

import android.content.Context
import android.content.Intent

class AndroidScanServiceLauncher(
    private val context: Context,
) : ScanServiceLauncher {
    override fun start() {
        context.startForegroundService(
            Intent(context, ScanForegroundService::class.java)
                .setAction(ScanForegroundService.ACTION_START),
        )
    }

    override fun stop() {
        context.startService(
            Intent(context, ScanForegroundService::class.java)
                .setAction(ScanForegroundService.ACTION_STOP),
        )
    }
}

