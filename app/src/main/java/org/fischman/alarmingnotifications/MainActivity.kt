package org.fischman.alarmingnotifications

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
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
    private var contentAttached: Boolean = false
    private var permissionsInFlight: Boolean = false

    private fun launchPermissionsActivityIfNeeded(): Boolean {
        if (permissionsInFlight) return false
        if (hasAllRequiredPermissions(this)) {
            permissionsInFlight = false
            return false
        }
        permissionsInFlight = true
        startActivity(PermissionsActivity.newIntent(this))
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (launchPermissionsActivityIfNeeded()) return

        ensureContent()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_actions, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.menu_permissions -> {
                startActivity(PermissionsActivity.newIntent(this))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        if (launchPermissionsActivityIfNeeded()) return
        ensureContent()
    }

    private fun ensureContent() {
        if (contentAttached) return
        contentAttached = true
        setContent {
            Theme {
                MainScreen()
            }
        }
    }

}

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

    var permissionCheckTrigger by remember { mutableIntStateOf(0) }
    val permissionsActivityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val grantedNow = hasAllRequiredPermissions(context)
        if (grantedNow) {
            (context as? android.app.Activity)?.recreate()
        } else {
            permissionCheckTrigger++
        }
    }

    DisposableEffect(Unit) {
        onRegisterResumeCallback { permissionCheckTrigger++ }
        onDispose { onRegisterResumeCallback(null) }
    }

    MainDashboard(
        mutedUntilStr = mutedUntilStr,
        muteCount = muteCount
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(
    mutedUntilStr: String,
    muteCount: Int,
) {
    val context = LocalContext.current

    Scaffold { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.primary).padding(8.dp, 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✅ Yay", fontSize = 24.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "All permissions granted, now awaiting notifications. Feel free to dismiss this app.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

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
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Apply Custom Mute", fontWeight = FontWeight.Bold)
        }
    }
}
