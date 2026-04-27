package org.fischman.alarmingnotifications

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NotificationAppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable,
)

private fun Drawable.toBitmap(): Bitmap {
    val bmp = Bitmap.createBitmap(intrinsicWidth.coerceAtLeast(1), intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(this)
        setContent {
            Theme {
                SettingsScreen(
                    prefs = prefs,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var ignoreKeep by remember { mutableStateOf(prefs.getBoolean(ignoreKeepKey, true)) }
    var ignoreSuffix by remember { mutableStateOf(prefs.getString(ignoreSuffixKey, "/s") ?: "") }
    val alarmPackages = remember {
        mutableStateListOf<String>().apply {
            addAll(prefs.getStringSet(alarmPackagesKey, defaultAlarmPackages) ?: defaultAlarmPackages)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var showSelectedOnly by remember { mutableStateOf(false) }
    var allApps by remember { mutableStateOf<List<NotificationAppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { info ->
                    (info.packageName != context.packageName) &&
                            (pm.checkPermission(android.Manifest.permission.POST_NOTIFICATIONS, info.packageName) == PackageManager.PERMISSION_GRANTED)
                }
                .map { info ->
                    NotificationAppInfo(
                        appName = pm.getApplicationLabel(info).toString(),
                        packageName = info.packageName,
                        icon = pm.getApplicationIcon(info),
                    )
                }
                .sortedBy { it.appName.lowercase() }
        }
        isLoading = false
    }

    val filteredApps by remember(searchQuery, allApps, showSelectedOnly) {
        derivedStateOf {
            val query = searchQuery.trim()
            if (query.isEmpty() && !showSelectedOnly) {
                allApps
            } else {
                val alarmSet = alarmPackages.toSet()
                allApps.filter { app ->
                    (query.isEmpty() ||
                     app.appName.contains(query, ignoreCase = true) ||
                     app.packageName.contains(query, ignoreCase = true)) &&
                    (!showSelectedOnly || app.packageName in alarmSet)
                }
            }
        }
    }

    val topSectionItems = listOf<@Composable () -> Unit>(
        { SectionHeader("Notification Filters") },
        {
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    ignoreKeep = !ignoreKeep
                    prefs.edit().putBoolean(ignoreKeepKey, ignoreKeep).apply()
                }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ignore Google Keep reminders", modifier = Modifier.weight(1f))
                Switch(checked = ignoreKeep, onCheckedChange = {
                    ignoreKeep = it
                    prefs.edit().putBoolean(ignoreKeepKey, it).apply()
                })
            }
        },
        { Text("Ignore notifications whose text ends with:", fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp)) },
        {
            OutlinedTextField(
                value = ignoreSuffix,
                onValueChange = {
                    ignoreSuffix = it
                    prefs.edit().putString(ignoreSuffixKey, it).apply()
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("e.g. /s") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
        },
        { Spacer(modifier = Modifier.height(24.dp)) },
        { SectionHeader("Apps to Alarm On") },
        {
            Text(
                "Select additional apps whose notifications should trigger an alarm:",
                fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    )

    val searchBarIndex = topSectionItems.size

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            topSectionItems.forEach { item { it() } }

            // CHANGE: Use stickyHeader instead of item
            stickyHeader {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    // This ensures the background matches the theme and hides scrolling items behind it
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        scope.launch {
                                            listState.animateScrollToItem(searchBarIndex)
                                        }
                                    }
                                },
                            placeholder = { Text("Filter apps...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            FilterChip(
                                selected = showSelectedOnly,
                                onClick = { showSelectedOnly = !showSelectedOnly },
                                label = { Text("Selected (${alarmPackages.size})") },
                                trailingIcon = if (showSelectedOnly) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear filter",
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.padding(32.dp))
                    }
                }
            } else {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        isChecked = alarmPackages.contains(app.packageName),
                        onCheckedChange = { isChecked ->
                            if (isChecked) alarmPackages.add(app.packageName)
                            else alarmPackages.remove(app.packageName)
                            prefs.edit().putStringSet(alarmPackagesKey, alarmPackages.toSet()).apply()
                        }
                    )
                }
            }

            // NEW: Large spacer at the bottom
            // fillParentMaxHeight(0.9f) ensures the list is always long enough to
            // allow the search bar to stay at the top of the screen even if
            // 0 apps match the filter.
            item {
                Spacer(modifier = Modifier.fillParentMaxHeight(0.9f))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun AppRow(app: NotificationAppInfo, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isChecked, onCheckedChange = null)
        Spacer(modifier = Modifier.width(8.dp))
        val bmp = remember(app.icon) { app.icon.toBitmap().asImageBitmap() }
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = app.appName, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text = app.packageName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
