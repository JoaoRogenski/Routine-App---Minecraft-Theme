package com.example.routineapp.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.routineapp.app

data class PermissionState(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreen: Boolean,
    val batteryUnrestricted: Boolean,
)

fun Context.permissionState() = PermissionState(
    notifications = app.notifier.canPost(),
    exactAlarms = app.scheduler.canScheduleExact(),
    fullScreen = app.notifier.canUseFullScreen(),
    batteryUnrestricted = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
)

/** Re-reads permission state whenever the screen resumes (the user may come back from system settings). */
@Composable
fun rememberPermissionState(): Pair<PermissionState, () -> Unit> {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val state = remember(tick) { context.permissionState() }
    return state to { tick++ }
}

@Composable
fun rememberNotificationPermissionRequest(onResult: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }
    return {
        if (Build.VERSION.SDK_INT >= 33 && !context.app.notifier.canPost() &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else context.openNotificationSettings()
    }
}

fun Context.openNotificationSettings() = startActivity(
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
}

fun Context.openFullScreenSettings() {
    if (Build.VERSION.SDK_INT >= 34) startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
}

fun Context.openBatterySettings() = startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

/** Shown on the calendar when something would stop reminders from arriving. */
@Composable
fun PermissionBanner() {
    val context = LocalContext.current
    val (state, refresh) = rememberPermissionState()
    val requestNotifications = rememberNotificationPermissionRequest(refresh)
    val (text, action) = when {
        !state.notifications -> "As notificações estão bloqueadas: você não vai receber lembretes nem cobranças." to requestNotifications
        !state.exactAlarms -> "Sem permissão de alarme exato, o Android pode atrasar os avisos em até 1 hora." to { context.openExactAlarmSettings() }
        else -> return
    }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = withClick(action), modifier = Modifier.padding(0.dp)) { Text("Corrigir") }
        }
    }
}
