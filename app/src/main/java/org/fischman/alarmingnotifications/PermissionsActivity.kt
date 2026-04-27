package org.fischman.alarmingnotifications

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class PermissionsActivity : ComponentActivity() {
    private var refreshCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Theme {
                PermissionsScreen(
                    onRegisterRefreshCallback = { refreshCallback = it },
                    onComplete = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCallback?.invoke()
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, PermissionsActivity::class.java)
    }
}

internal enum class PermissionStep {
    SendNotifications,
    ReadNotifications,
    SetExactAlarms,
}

internal data class PermissionStatus(
    val step: PermissionStep,
    val label: String,
    val reason: String,
    val granted: Boolean,
)

@Composable
internal fun PermissionsScreen(
    onRegisterRefreshCallback: ((() -> Unit)?) -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var permissionCheckTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onRegisterRefreshCallback { permissionCheckTrigger++ }
        onDispose { onRegisterRefreshCallback(null) }
    }

    val statuses = remember(permissionCheckTrigger) { getPermissionStatuses(context) }

    PermissionSetupScreen(
        statuses = statuses,
        onRefresh = { permissionCheckTrigger++ },
        onComplete = onComplete
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun PermissionSetupScreen(
    statuses: List<PermissionStatus>,
    onRefresh: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val nextPermission = statuses.firstOrNull { !it.granted }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefresh() }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Permissions") },
                navigationIcon = {
                    IconButton(onClick = onComplete) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(16.dp, 20.dp)
            ) {
                Text(
                    "Grant the minimum access needed for alarm delivery, notification listening, and snooze support.",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    PermissionStatusCard(statuses)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            when (nextPermission?.step) {
                                PermissionStep.SendNotifications -> {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                PermissionStep.ReadNotifications -> {
                                    context.startActivity(getNotificationListenerSettingsIntent(context))
                                }
                                PermissionStep.SetExactAlarms -> {
                                    if (Build.VERSION.SDK_INT >= 31) {
                                        context.startActivity(getExactAlarmSettingsIntent(context))
                                    }
                                }
                                null -> onComplete()
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            if (nextPermission == null) "All set" else "Continue: ${nextPermission.label}",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PermissionStatusCard(statuses: List<PermissionStatus>) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Required permissions:", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(modifier = Modifier.height(12.dp))
            statuses.forEach { status ->
                PermissionStatusRow(status)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
internal fun PermissionStatusRow(status: PermissionStatus) {
    val chipColor = if (status.granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val contentColor = if (status.granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(chipColor, RoundedCornerShape(4.dp))
            .padding(14.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Text(if (status.granted) "✔️" else "❌", fontSize = 24.sp, color = contentColor)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(status.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = contentColor)
            Text(status.reason, fontSize = 12.sp, color = contentColor)
        }
        Text(
            if (status.granted) "" else "Needed",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

internal fun hasAllRequiredPermissions(context: Context): Boolean {
    return getPermissionStatuses(context).all { it.granted }
}

internal fun getPermissionStatuses(context: Context): List<PermissionStatus> {
    val statuses = mutableListOf<PermissionStatus>()

    if (Build.VERSION.SDK_INT >= 33) {
        statuses += PermissionStatus(
            PermissionStep.SendNotifications,
            "Send notifications",
            "Required so the app can post alarms.",
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    statuses += PermissionStatus(
        PermissionStep.ReadNotifications,
        "Read notifications",
        "Required so the app can detect eligible notifications from other apps.",
        enabledListeners?.contains(notificationListenerComponent(context)) == true
    )

    if (Build.VERSION.SDK_INT >= 31) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        statuses += PermissionStatus(
            PermissionStep.SetExactAlarms,
            "Set exact alarms",
            "Required for reliable timing of snooze on an alarming notification.",
            alarmManager.canScheduleExactAlarms(),
        )
    }

    return statuses
}

private fun notificationListenerComponent(context: Context): String {
    return ComponentName(context.packageName, NotificationListener::class.java.name).flattenToString()
}

private fun getNotificationListenerSettingsIntent(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= 30) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
            putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                notificationListenerComponent(context)
            )
        }
    } else {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}

private fun getExactAlarmSettingsIntent(context: Context): Intent {
    return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
}
