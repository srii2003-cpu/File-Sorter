package com.narasa.imageorganiser

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narasa.imageorganiser.permission.PhotoAccess
import com.narasa.imageorganiser.permission.PhotoPermissions
import com.narasa.imageorganiser.ui.AccessScreen
import com.narasa.imageorganiser.ui.AppTheme
import com.narasa.imageorganiser.ui.HomePlaceholder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val required = PhotoPermissions.required(Build.VERSION.SDK_INT)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onPermissionResult(::isGranted, showRationale = required.any { shouldShowRequestPermissionRationale(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                if (state.access == PhotoAccess.NONE) {
                    AccessScreen(state, onAllow = ::requestAccess, onOpenSettings = ::openAppSettings)
                } else {
                    // Re-requesting under partial access only reopens the photo picker (Android 14+);
                    // "Allow all" lives in the app's permission settings.
                    HomePlaceholder(state, onAllowAll = ::openAppSettings)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshAccess(::isGranted)
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requestAccess() = permissionLauncher.launch(required)

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }
}
