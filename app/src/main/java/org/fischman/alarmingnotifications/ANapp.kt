package org.fischman.alarmingnotifications

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDateTime

const val notificationChannelID = "AlarmingNotifications-ChannelID"

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
    val existingDeadline = getMuteSharedPreferences(context).getString(muteDeadlineKey, null) ?: return ""
    if (existingDeadline <= LocalDateTime.now().toString()) { return "" }
    val parsedDeadline = LocalDateTime.parse(existingDeadline)
    return parsedDeadline.toString()
    // For future reference: val gapInSeconds = Duration.between(LocalDateTime.now(), parsedDeadline).seconds
}

class ANapp : Application() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                notificationChannelID,
                "Alarms",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }
}
