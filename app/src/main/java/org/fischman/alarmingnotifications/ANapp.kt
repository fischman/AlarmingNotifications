package org.fischman.alarmingnotifications

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import java.time.Duration
import java.time.LocalDateTime

const val notificationChannelID = "AlarmingNotifications-ChannelID"
const val muteStatusChannelID = "AlarmingNotifications-MuteStatus"

const val maxRandomNotificationId = 999999
const val persistentMuteNotificationId = maxRandomNotificationId + 1 // Arbitrary choice, but must not collide with other notification IDs from this app.

// SharedPreferences keys.
const val muteDeadlineKey = "muteDeadline"
const val muteCountKey = "muteCount"
const val ignoreKeepKey = "ignoreKeep"
const val ignoreSuffixKey = "ignoreSuffix"
const val alarmPackagesKey = "alarmPackages"

val defaultAlarmPackages = setOf("com.google.android.calendar")
private const val settingsPreferencesName = "settings_preferences"
private const val mutePreferencesName = "mute_preferences"

fun log(msg: String) {
    if (BuildConfig.DEBUG) println(msg)
}

fun getSettingsSharedPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences(
        context.packageName + "_" + settingsPreferencesName,
        Context.MODE_PRIVATE
    )
}

fun getMuteSharedPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences(
        context.packageName + "_" + mutePreferencesName,
        Context.MODE_PRIVATE
    )
}

fun muteForHours(context: Context, hours: Int) {
    if (hours <= 0) {
        unmuteTime(context)
        return
    }
    val deadline = LocalDateTime.now().plusHours(hours.toLong()).toString()
    getMuteSharedPreferences(context).edit().putString(muteDeadlineKey, deadline).apply()
}

fun muteForMinutes(context: Context, minutes: Int) {
    if (minutes <= 0) {
        unmuteTime(context)
        return
    }
    val deadline = LocalDateTime.now().plusMinutes(minutes.toLong()).toString()
    getMuteSharedPreferences(context).edit().putString(muteDeadlineKey, deadline).apply()
}

fun unmuteTime(context: Context) {
    getMuteSharedPreferences(context).edit().remove(muteDeadlineKey).apply()
}

fun unmuteCount(context: Context) {
    getMuteSharedPreferences(context).edit().remove(muteCountKey).apply()
}

fun muteForNNotifications(context: Context, n: Int) {
    if (n <= 0) {
        unmuteCount(context)
        return
    }
    getMuteSharedPreferences(context).edit().putInt(muteCountKey, n).apply()
}

fun unmuteAll(context: Context) {
    getMuteSharedPreferences(context).edit().remove(muteDeadlineKey).remove(muteCountKey).apply()
}

/** Returns the remaining mute-by-count, or 0 if not count-muted. */
fun muteCountRemaining(context: Context): Int {
    return getMuteSharedPreferences(context).getInt(muteCountKey, 0)
}

/**
 * If count-muted, decrement and return true (meaning "still muted, suppress this one").
 * When the count reaches 0 the mute is cleared and false is returned ("not muted, fire alarm").
 */
fun decrementMuteCount(context: Context): Boolean {
    val prefs = getMuteSharedPreferences(context)
    val remaining = prefs.getInt(muteCountKey, 0)
    if (remaining <= 0) return false
    val newVal = remaining - 1
    if (newVal <= 0) {
        prefs.edit().remove(muteCountKey).apply()
        return remaining == 1  // mute the notification that took us to zero
    }
    prefs.edit().putInt(muteCountKey, newVal).apply()
    return true  // still muted
}

fun mutedUntil(context: Context): String {
    val prefs = getMuteSharedPreferences(context)
    val existingDeadline = prefs.getString(muteDeadlineKey, null) ?: return ""
    if (existingDeadline <= LocalDateTime.now().toString()) {
        prefs.edit().remove(muteDeadlineKey).apply()
        return ""
    }
    val parsedDeadline = LocalDateTime.parse(existingDeadline)
    return parsedDeadline.toString()
    // For future reference: val gapInSeconds = Duration.between(LocalDateTime.now(), parsedDeadline).seconds
}

class ANapp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                notificationChannelID,
                "Alarms",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                muteStatusChannelID,
                "Mute status",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
    }
}

object MuteStatusNotification {
    private val handler = Handler(Looper.getMainLooper())
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var registeredPrefs: SharedPreferences? = null

    fun startWatching(context: Context) {
        val ctx = context.applicationContext
        val prefs = getMuteSharedPreferences(ctx)
        if (registeredPrefs === prefs) return
        stopWatching()
        registeredPrefs = prefs
        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> update(ctx) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        update(ctx)
    }

    fun stopWatching() {
        handler.removeCallbacksAndMessages(null)
        registeredPrefs?.unregisterOnSharedPreferenceChangeListener(listener)
        registeredPrefs = null
        listener = null
    }

    fun update(context: Context) {
        val ctx = context.applicationContext
        handler.removeCallbacksAndMessages(null)

        val timeStr = mutedUntil(ctx)
        val count = muteCountRemaining(ctx)
        if (timeStr.isEmpty() && count <= 0) {
            ctx.getSystemService(NotificationManager::class.java).cancel(persistentMuteNotificationId)
            return
        }

        val parts = mutableListOf<String>()
        if (timeStr.isNotEmpty()) {
            parts += "Until ${timeStr.substringBefore('.')}"
            // Schedule refresh when the time mute expires.
            val deadline = LocalDateTime.parse(timeStr)
            val millis = Duration.between(LocalDateTime.now(), deadline).toMillis()
            if (millis > 0) handler.postDelayed({ update(ctx) }, millis + 500)
        }
        if (count > 0) {
            parts += "$count notification${if (count > 1) "s" else ""}"
        }

        val text = "Muted: ${parts.joinToString(" & ")}"
        val n = Notification.Builder(ctx, muteStatusChannelID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🔕 *Not* Alarming for Notifications")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            ))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(persistentMuteNotificationId, n)
    }
}
