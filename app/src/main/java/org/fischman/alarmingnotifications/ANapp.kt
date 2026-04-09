package org.fischman.alarmingnotifications

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.preference.PreferenceManager
import java.time.LocalDateTime

const val notificationChannelID = "AlarmingNotifications-ChannelID"
private const val muteDeadlineKey = "org.fischman.alarmingnotifications.muteDeadline"
private const val muteCountKey = "org.fischman.alarmingnotifications.muteCount"

fun muteForOneHour(context: Context) {
    val deadline = LocalDateTime.now().plusHours(1).toString()
    PreferenceManager.getDefaultSharedPreferences(context).edit().putString(muteDeadlineKey, deadline).apply()
}

fun muteForNNotifications(context: Context, n: Int) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(muteCountKey, n).apply()
}

fun unmute(context: Context) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().remove(muteDeadlineKey).remove(muteCountKey).apply()
}

/** Returns the remaining mute-by-count, or 0 if not count-muted. */
fun muteCountRemaining(context: Context): Int {
    return PreferenceManager.getDefaultSharedPreferences(context).getInt(muteCountKey, 0)
}

/**
 * If count-muted, decrement and return true (meaning "still muted, suppress this one").
 * When the count reaches 0 the mute is cleared and false is returned ("not muted, fire alarm").
 */
fun decrementMuteCount(context: Context): Boolean {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
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
    val existingDeadline = PreferenceManager.getDefaultSharedPreferences(context).getString(muteDeadlineKey, null) ?: return ""
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