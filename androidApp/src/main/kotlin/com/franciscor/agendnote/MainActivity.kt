package com.franciscor.agendnote

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.franciscor.agendnote.core.notifications.AndroidNotificationService

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        AndroidNotificationService.onNotificationPermissionResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        AndroidNotificationService.initialize(
            activity = this,
            requestNotificationPermission = {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
        )

        setContent {
            App()
        }
    }

    override fun onResume() {
        super.onResume()
        AndroidNotificationService.onHostResumed(this)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
