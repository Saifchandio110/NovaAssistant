package com.nova.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private fun StatusDot(color: Color) {
    Spacer(
        Modifier
            .size(10.dp)
            .background(color, CircleShape)
    )
}

@Composable
private fun SetupRow(label: String, onAllow: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(NovaColors.Warn)
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        FilledTonalButton(
            onClick = onAllow,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text("Allow")
        }
    }
}

@Composable
fun ServiceControls(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var active by remember { mutableStateOf(ListenService.running) }
    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var batteryOk by remember { mutableStateOf(isBatteryExempt(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastToggle by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            if (SystemClock.elapsedRealtime() - lastToggle > 2500) {
                active = ListenService.running
            }
            overlayOk = Settings.canDrawOverlays(context)
            batteryOk = isBatteryExempt(context)
            ListenService.lastError?.let { error = it }
            delay(1000)
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            error = null
            startListenService(context)
            active = true
            lastToggle = SystemClock.elapsedRealtime()
        } else {
            error = "Microphone permission is required for hands-free mode."
        }
    }

    fun toggle(on: Boolean) {
        lastToggle = SystemClock.elapsedRealtime()
        if (!on) {
            context.stopService(Intent(context, ListenService::class.java))
            active = false
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            error = null
            startListenService(context)
            active = true
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (active) NovaColors.Ok else NovaColors.TextSecondary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Hands-free mode",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (active) "On. Say \"hey nova\" (first start takes a minute)"
                        else "Off. Turn on to use \"hey nova\"",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = active, onCheckedChange = { toggle(it) })
            }

            if (!overlayOk) {
                SetupRow("Display over other apps (needed to open apps)") {
                    error = openSettings(
                        context,
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            }
            if (!batteryOk) {
                SetupRow("Don't optimize battery (keeps Nova alive)") {
                    error = openSettings(
                        context,
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    )
                }
            }
            if (overlayOk && batteryOk) {
                Text(
                    "Permissions ready",
                    fontSize = 12.sp,
                    color = NovaColors.Ok
                )
            }
            error?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
