package com.nova.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

private fun isBatteryExempt(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun startListenService(context: Context) {
    ContextCompat.startForegroundService(context, Intent(context, ListenService::class.java))
}

private fun openSettings(context: Context, intent: Intent): String? {
    return try {
        context.startActivity(intent)
        null
    } catch (e: Exception) {
        "Could not open that settings screen: ${e.message}"
    }
}

@Composable
fun ServiceControls() {
    val context = LocalContext.current
    var active by remember { mutableStateOf(ListenService.running) }
    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var batteryOk by remember { mutableStateOf(isBatteryExempt(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            active = ListenService.running
            overlayOk = Settings.canDrawOverlays(context)
            batteryOk = isBatteryExempt(context)
            if (ListenService.lastError != null) error = ListenService.lastError
            delay(1000)
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            error = null
            startListenService(context)
        } else {
            error = "Microphone permission is required for the background service."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(
                "Background service: " + (if (active) "RUNNING" else "stopped") +
                    (if (expanded) "  (hide)" else "  (show)")
            )
        }
        if (expanded) {
            Text(
                "Display over other apps: " + if (overlayOk) "allowed" else "NOT allowed",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Battery optimization: " + if (batteryOk) "exempt" else "NOT exempt (Android may stop Nova)",
                style = MaterialTheme.typography.bodySmall
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    if (active) {
                        context.stopService(Intent(context, ListenService::class.java))
                    } else {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            error = null
                            startListenService(context)
                        } else {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (active) "Stop background service" else "Start background service")
            }
            OutlinedButton(
                onClick = {
                    error = openSettings(
                        context,
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Allow display over other apps")
            }
            OutlinedButton(
                onClick = {
                    error = openSettings(
                        context,
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open battery optimization list")
            }
        }
    }
}
