package org.fischman.alarmingnotifications

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

const val notificationChannelID = "AlarmingNotifications-ChannelID"

class ANapp : Application() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(notificationChannelID,"Alarms", NotificationManager.IMPORTANCE_HIGH))
    }
}