package org.fischman.alarmingnotifications

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    private var resumeCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Theme {
                MainScreen(onRegisterResumeCallback = { resumeCallback = it })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCallback?.invoke()
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
fun MainScreen(onRegisterResumeCallback: ((() -> Unit)?) -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { getSharedPreferences(context) }

    var mutedUntilStr by remember { mutableStateOf(mutedUntil(context)) }
    var muteCount by remember { mutableIntStateOf(muteCountRemaining(context)) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == muteDeadlineKey || key == muteCountKey) {
                mutedUntilStr = mutedUntil(context)
                muteCount = muteCountRemaining(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // Permission check trigger (when returning from settings)
    var permissionCheckTrigger by remember { mutableIntStateOf(0) }
    var forcePermissionShow by remember { mutableStateOf(false) }

    // Re-check permissions on every Activity resume to catch return from settings permissions granting.
    DisposableEffect(Unit) {
        onRegisterResumeCallback { permissionCheckTrigger++ }
        onDispose { onRegisterResumeCallback(null) }
    }

    val permissions = remember(permissionCheckTrigger) {
        getPermissionStatuses(context)
    }
    val allGranted = permissions.all { it.granted }

    // Logic: Show permissions if they are missing OR if the user manually requested to see them via '?'
    if (!allGranted || forcePermissionShow) {
        PermissionSetupScreen(permissions) {
            forcePermissionShow = false
            permissionCheckTrigger++
        }
    } else {
        MainDashboard(
            mutedUntilStr = mutedUntilStr,
            muteCount = muteCount,
            onShowPermissions = { forcePermissionShow = true }
        )
    }
}

@Composable
internal fun PermissionSetupScreen(statuses: List<PermissionStatus>, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val nextPermission = statuses.firstOrNull { !it.granted }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefresh() }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(16.dp, 20.dp)
            ) {
                Text("Permission setup", fontSize = 24.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                Text(
                    "Grant the minimum access needed for alarm delivery, notification listening, and snooze support.",
                    fontSize = 15.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
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
                                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                                // No missing permissions? This button acts as a "Back" button
                                null -> onRefresh()
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
        Text(if (status.granted) "●" else "○", fontSize = 18.sp, color = contentColor)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(status.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = contentColor)
            Text(status.reason, fontSize = 12.sp, color = contentColor)
        }
        Text(
            if (status.granted) "Granted" else "Needed",
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = contentColor
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(
    mutedUntilStr: String,
    muteCount: Int,
    onShowPermissions: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.primary).windowInsetsPadding(WindowInsets.statusBars).padding(16.dp, 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✅ Yay", fontSize = 24.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)

                    Surface(
                        color = Color.Black.copy(alpha = 0.2f),
                        shape = CircleShape,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .clickable { onShowPermissions() }
                    ) {
                        Text("?", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Text(
                    "All permissions granted, now awaiting notifications. Feel free to dismiss this app.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (mutedUntilStr.isNotEmpty() || muteCount > 0) {
                item {
                    ActiveMutesSection(mutedUntilStr, muteCount, context)
                }
            }

            item {
                Spacer(
                    modifier = Modifier
                        .height(8.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    QuickMuteSection(context)
                    Spacer(modifier = Modifier.height(24.dp))
                    CustomMuteSection(context)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveMutesSection(mutedUntilStr: String, muteCount: Int, context: Context) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "🔕 Active Mutes",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = MaterialTheme.colorScheme.error,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.clickable { unmuteAll(context) }
            ) {
                Text(
                    "🗑️ Unmute All",
                    color = MaterialTheme.colorScheme.onError,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp, 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (mutedUntilStr.isNotEmpty()) {
                MuteBadge("🔕 Until ${mutedUntilStr.substringBefore('.')}") { unmuteTime(context) }
            }
            if (muteCount > 0) {
                MuteBadge("🔕 $muteCount notification${if (muteCount > 1) "s" else ""}") { unmuteCount(context) }
            }
        }
    }
}

@Composable
fun MuteBadge(text: String, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.error,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 6.dp)
        ) {
            Text(text, color = MaterialTheme.colorScheme.onError, fontSize = 13.sp)

            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clip(CircleShape)
                    .clickable { onRemove() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "×",
                    color = MaterialTheme.colorScheme.onError,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
fun QuickMuteSection(context: Context) {
    Column {
        Text("Quick Mute", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickButton("15m", Modifier.weight(1f)) { muteForMinutes(context, 15) }
            QuickButton("30m", Modifier.weight(1f)) { muteForMinutes(context, 30) }
            QuickButton("1h", Modifier.weight(1f)) { muteForHours(context, 1) }
            QuickButton("2h", Modifier.weight(1f)) { muteForHours(context, 2) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { n ->
                QuickButton("${n}n", Modifier.weight(1f)) { muteForNNotifications(context, n) }
            }
        }
    }
}

@Composable
fun QuickButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(label, fontSize = 14.sp)
    }
}

@Composable
fun CustomMuteSection(context: Context) {
    var hours by remember { mutableIntStateOf(0) }
    var notifs by remember { mutableIntStateOf(0) }
    val isActive = hours > 0 || notifs > 0

    Column {
        Text("Custom Mute", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IntWheelPicker(value = hours, max = 48, label = "Hours") { hours = it }

            // Subtle visual separator
            Text("&", fontSize = 24.sp, color = MaterialTheme.colorScheme.outlineVariant)

            IntWheelPicker(value = notifs, max = 20, label = "Notifications") { notifs = it }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (hours > 0) muteForHours(context, hours)
                if (notifs > 0) muteForNNotifications(context, notifs)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isActive,
            shape = RoundedCornerShape(12.dp), // Matches modern Material 3
        ) {
            Text("Apply Custom Mute", fontWeight = FontWeight.Bold)
        }
    }
}

private fun getPermissionStatuses(context: Context): List<PermissionStatus> {
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
        enabledListeners?.contains("${context.packageName}/${context.packageName}.NotificationListener") == true
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

private fun getNotificationListenerSettingsIntent(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= 30) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
            putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                android.content.ComponentName(context.packageName, "${context.packageName}.NotificationListener").flattenToString()
            )
        }
    } else {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}
