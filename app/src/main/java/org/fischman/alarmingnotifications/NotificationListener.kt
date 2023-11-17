package org.fischman.alarmingnotifications

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {
    private val DEBUG = false
    fun log(msg: String) { if (DEBUG) Log.e("AMI", msg) }

    override fun onListenerConnected() = log("onListenerConnected")
    override fun onListenerDisconnected() = log("onListenerDisconnected")

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (// sbn.packageName != "com.google.android.gm") && // Debug using Gmail chat notifications.
            sbn.packageName != "com.google.android.calendar") {
            return
        }

        val notification = sbn.notification
        val tickerText = notification.tickerText?.toString()
        val extraText = notification.extras.getString(Notification.EXTRA_TEXT)
        if (tickerText == null && extraText == null) { log("Both ticker and extras text are null, so ignoring notification"); return }
        // Ignore all-day events with this one weird trick! Unfortunately doesn't seem to be any
        // other indication of all-day nature of an event other than this text (and absence of a
        // time-window instead).
        if (notification.extras.getString(Notification.EXTRA_TEXT) == "Tomorrow") return

        // Other places that text can be stored in in Notifications. Possibly of future interest for apps other than GCal and GMail.
        if (false) {
            val textFields = listOf(Notification.EXTRA_TEXT, Notification.EXTRA_TEXT_LINES, Notification.EXTRA_BIG_TEXT, Notification.EXTRA_INFO_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_SUMMARY_TEXT, Notification.EXTRA_VERIFICATION_TEXT)
            val textContents = "${sbn.notification.tickerText}\n${
                textFields.map { fieldName: String ->
                    "$fieldName - ${notification.extras.getString(fieldName)}"
                }.joinToString(separator = "\n")
            }"
            log("All text-related fields from notification: $textContents")
            log("Full notification: $sbn")
            log("and extras: ${notification.extras}")
        }

        val label = tickerText?:"" + "\n" + extraText?:""
        log("onNotificationPosted: $label")
        var i = Intent("$packageName.AlarmActivity")
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        i.putExtra("label", label)
        startActivity(i)
    }




}

/*

 */