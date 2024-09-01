package org.fischman.alarmingnotifications

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.preference.PreferenceManager
import java.time.LocalDateTime

const val notificationChannelID = "AlarmingNotifications-ChannelID"
private const val muteDeadlineKey = "org.fischman.alarmingnotifications.muteDeadline"

fun muteForOneHour(context: Context) {
    val deadline = LocalDateTime.now().plusHours(1).toString()
    PreferenceManager.getDefaultSharedPreferences(context).edit().putString(muteDeadlineKey, deadline).apply()
}

fun unmute(context: Context) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().remove(muteDeadlineKey).apply()
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