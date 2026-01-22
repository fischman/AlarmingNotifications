package org.fischman.alarmingnotifications

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlin.random.Random


class NotificationListener : NotificationListenerService() {
    private val debug = false
    private fun log(msg: String) {
        if (debug) println(msg)
    }

    private val mp = MediaPlayer()
    private var originalNotificationKeyToAlarmingID: MutableMap<String, Int> = mutableMapOf()

    override fun onListenerConnected() = log("onListenerConnected")
    override fun onListenerDisconnected() = log("onListenerDisconnected")

    override fun onCreate() {
        super.onCreate()
        mp.setDataSource(
            applicationContext,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        )
        mp.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build())
        mp.isLooping = true
    }

    private fun isInteresting(sbn: StatusBarNotification): Boolean {
        // Ignore Keep Reminders, now surfaced as Tasks notifications from
        // Calendar (when Tasks app isn't installed).
        if (sbn.notification.actions?.any {
                it.title == "Open note" && it.getIcon() != null
            } ?: false) {
            return false
        }

        return !originalNotificationKeyToAlarmingID.contains(sbn.key) && (
            // (debug && sbn.packageName == "com.google.android.gm") || // Debug using Gmail chat notifications.
            sbn.packageName == "com.google.android.calendar"
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!isInteresting(sbn)) {
            return
        }
        originalNotificationKeyToAlarmingID[sbn.key]?.let { dismiss(it, sbn.key) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val mutedUntilStr = mutedUntil(this)
        if (mutedUntilStr != "") {
            log("Suppressing notification because muted until $mutedUntilStr")
            return
        }

        if (!isInteresting(sbn)) {
            return
        }

        // Other places that text can be stored in in Notifications. Possibly of future interest for apps other than GCal and GMail.
        if (0 > 1) {
            val textFields = mutableListOf(
                Notification.EXTRA_TITLE,
                Notification.EXTRA_TITLE_BIG,
                Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_INFO_TEXT,
                Notification.EXTRA_SUB_TEXT,
                Notification.EXTRA_SUMMARY_TEXT,
                Notification.EXTRA_TEXT,
                Notification.EXTRA_TEXT_LINES,
            )
            if (Build.VERSION.SDK_INT >= 31) {
                textFields += "Notification.EXTRA_VERIFICATION_TEXT"
            }
            val textContents = "${sbn.notification.tickerText}\n${
                textFields.joinToString(separator = "\n") { fieldName: String ->
                    "$fieldName - ${sbn.notification.extras.get(fieldName)?.toString()}"
                }
            }"
            log("All text-related fields from notification: $textContents")
            log("Full notification: $sbn")
            log("and extras: ")
            for (key in sbn.notification.extras.keySet()) {
                log("key:" + key + ", value: " + sbn.notification.extras.get(key)?.toString())
            }
        }

        val notification = sbn.notification
        val tickerText = notification.tickerText?.toString()
        val extraText = notification.extras.getString(Notification.EXTRA_TEXT)
        val titleText = notification.extras.getString(Notification.EXTRA_TITLE)
        if (tickerText == null && extraText == null && titleText == null) {
            log("Both ticker and extras text are null, so ignoring notification"); return
        }
        // Ignore all-day events with this one weird trick! Unfortunately doesn't seem to be any
        // other indication of all-day nature of an event other than this text (and absence of a
        // time-window instead).
        if (extraText == "Tomorrow") return

        // Ignore notifications for events that start after 2am tomorrow.
        if (extraText?.contains("Tomorrow, (0[2-9]|1|2)".toRegex()) == true) return

        val label =
            ((tickerText ?: "") + "\n" + (extraText ?: "") + "\n" + (titleText ?: "")).trim()
        log("onNotificationPosted: $label")
        showNotification(label, sbn.key)
    }

    private fun createPendingIntent(
        requestCode: Int,
        action: String,
        notificationID: Int,
        label: String,
        originalNotificationKey: String
    ): PendingIntent {
        val intent = Intent(this, NotificationListener::class.java)
        intent.putExtra("action", action)
        intent.putExtra("notificationID", notificationID)
        intent.putExtra("label", label)
        intent.putExtra("originalNotificationKey", originalNotificationKey)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    @Suppress("DEPRECATION")
    private fun showNotification(label: String, originalNotificationKey: String) {
        if (!mp.isPlaying) {
            mp.prepare()
            mp.start()
        }

        val notificationID = Random.nextInt(0, 999999)
        originalNotificationKeyToAlarmingID[originalNotificationKey] = notificationID

        val stopIntent =
            createPendingIntent(0, "stop", notificationID, label, originalNotificationKey)
        val snooze1mIntent =
            createPendingIntent(1, "snooze1m", notificationID, label, originalNotificationKey)
        val snooze5mIntent =
            createPendingIntent(2, "snooze5m", notificationID, label, originalNotificationKey)

        val notificationManager = getSystemService(NotificationManager::class.java)
        val notificationBuilder =
            Notification.Builder(this, notificationChannelID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentTitle(label)
                .setContentText("")
                .setCategory(Notification.CATEGORY_CALL)
                .setFlag(Notification.FLAG_NO_CLEAR, true)
                .setDeleteIntent(stopIntent)
                .addAction(
                    Notification.Action.Builder(
                        android.R.drawable.stat_notify_call_mute,
                        "Stop",
                        stopIntent
                    )
                        .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MUTE)
                        .build()
                )
                .addAction(
                    Notification.Action.Builder(
                        android.R.drawable.stat_notify_call_mute,
                        "Snooze 1m",
                        snooze1mIntent
                    )
                        .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MUTE)
                        .build()
                )
                .addAction(
                    Notification.Action.Builder(
                        android.R.drawable.stat_notify_call_mute,
                        "Snooze 5m",
                        snooze5mIntent
                    )
                        .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MUTE)
                        .build()
                )
        notificationManager.notify(notificationID, notificationBuilder.build())
    }

    private fun bundleToString(bundle: Bundle?): String {
        if (bundle == null) return "(null bundle))"
        var str = "Bundle{"
        @Suppress("DEPRECATION")
        for (key in bundle.keySet()) str += " $key: ${bundle[key]};"
        str += "}"
        return str
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("extras: ${bundleToString(intent?.extras)}")
        val action = intent?.getStringExtra("action") ?: return START_NOT_STICKY
        val label = intent.getStringExtra("label") ?: return START_NOT_STICKY
        val originalNotificationKey =
            intent.getStringExtra("originalNotificationKey") ?: return START_NOT_STICKY

        if (action == "show") {
            showNotification(label, originalNotificationKey)
            return START_NOT_STICKY
        }

        val notificationID = intent.getIntExtra("notificationID", -1)
        if (notificationID < 0) return START_NOT_STICKY

        when (action) {
            "stop" -> {
                dismiss(notificationID, originalNotificationKey)
            }

            "snooze1m", "snooze5m" -> {
                snooze(action, label, notificationID, originalNotificationKey)
            }

            else -> {
                Log.e("AMI", "Unknown action: $action!")
            }
        }
        return START_NOT_STICKY

    }

    @SuppressLint("ScheduleExactAlarm")
    private fun setExactAlarm(
        alarmManager: AlarmManager,
        aci: AlarmManager.AlarmClockInfo,
        pendingIntent: PendingIntent
    ) {
        alarmManager.setAlarmClock(aci, pendingIntent)
    }

    private fun snooze(
        action: String,
        label: String,
        notificationID: Int,
        originalNotificationKey: String
    ) {
        log("snooze: $action $label $notificationID")
        val durStr = action.removePrefix("snooze")
        if (durStr == action) {
            Log.wtf("AMI", "Missing prefix 'snooze' in $action")
        }
        val minutesStr = durStr.removeSuffix("m")
        if (minutesStr == durStr) {
            Log.wtf("AMI", "Missing suffix 'm' in $action")
        }
        val minutes = minutesStr.toInt()
        if (minutes != 5 && minutes != 1) {
            Log.wtf("AMI", "Unexpected snooze duration of $minutes in $action")
        }

        dismiss(notificationID, "")

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, NotificationListener::class.java)
        intent.putExtra("action", "show")
        intent.putExtra("label", label)
        intent.putExtra("originalNotificationKey", originalNotificationKey)
        val pendingIntent = PendingIntent.getService(
            this,
            4,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val aci =
            AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 60 * 1000 * minutes, null)
        log("snoozed for $minutes minutes")
        setExactAlarm(alarmManager, aci, pendingIntent)
    }

    private fun dismiss(notificationID: Int, originalNotificationKey: String) {
        log("dismiss: notificationID: $notificationID")
        originalNotificationKeyToAlarmingID.remove(originalNotificationKey)
        if (mp.isPlaying) mp.stop()
        getSystemService(NotificationManager::class.java).cancel(notificationID)

        cancelNotification(originalNotificationKey)
    }

}
