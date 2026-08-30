package com.cyberscan.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

fun requiredScanPermissions(apiLevel: Int): Array<String> = buildList {
    add(Manifest.permission.BLUETOOTH_SCAN)
    add(Manifest.permission.BLUETOOTH_CONNECT)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (apiLevel >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
fun rememberScanPermissionRequester(
    onGranted: () -> Unit,
    onDenied: () -> Unit,
): ScanPermissionRequester {
    val context = LocalContext.current
    val currentOnGranted = rememberUpdatedState(onGranted)
    val currentOnDenied = rememberUpdatedState(onDenied)
    val permissions = remember { requiredScanPermissions(Build.VERSION.SDK_INT) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (permissions.all { results[it] == true }) {
            currentOnGranted.value()
        } else {
            currentOnDenied.value()
        }
    }
    return remember(context, launcher, permissions) {
        ScanPermissionRequester(
            context = context,
            launcher = launcher,
            permissions = permissions,
            onGranted = { currentOnGranted.value() },
        )
    }
}

class ScanPermissionRequester(
    private val context: Context,
    private val launcher: ActivityResultLauncher<Array<String>>,
    private val permissions: Array<String>,
    private val onGranted: () -> Unit,
) {
    fun request() {
        if (permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            onGranted()
        } else {
            launcher.launch(permissions)
        }
    }
}
