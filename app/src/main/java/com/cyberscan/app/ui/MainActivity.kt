package com.cyberscan.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyberscan.app.ui.hud.ScanScreen
import com.cyberscan.app.ui.theme.CyberScanTheme
import com.cyberscan.app.ui.viewmodel.ScanViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CyberScanTheme {
                val viewModel: ScanViewModel = viewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val permissionMessage by viewModel.permissionMessage.collectAsStateWithLifecycle()
                val startPermissions = rememberScanPermissionRequester(
                    onGranted = viewModel::onStartGranted,
                    onDenied = viewModel::onPermissionDenied,
                )
                val retryPermissions = rememberScanPermissionRequester(
                    onGranted = viewModel::onRetryGranted,
                    onDenied = viewModel::onPermissionDenied,
                )
                ScanScreen(
                    state = state,
                    permissionMessage = permissionMessage,
                    onStart = startPermissions::request,
                    onStop = viewModel::onStop,
                    onRetry = retryPermissions::request,
                    onTargetSelected = viewModel::onTargetSelected,
                )
            }
        }
    }
}
