package org.fischman.alarmingnotifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlin.random.Random


class NotificationListener : NotificationListenerService() {
    private val DEBUG = false
    private fun log(msg: String) { if (DEBUG) Log.e("AMI", msg) }

    private val mp = MediaPlayer()

    override fun onListenerConnected() = log("onListenerConnected")
    override fun onListenerDisconnected() = log("onListenerDisconnected")

    override fun onCreate() {
        super.onCreate()
        mp.setDataSource(applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        mp.setAudioStreamType(AudioManager.STREAM_ALARM)
        mp.isLooping = true

    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (// sbn.packageName != "com.google.android.gm" && // Debug using Gmail chat notifications.
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
                textFields.joinToString(separator = "\n") { fieldName: String ->
                    "$fieldName - ${notification.extras.getString(fieldName)}"
                }
            }"
            log("All text-related fields from notification: $textContents")
            log("Full notification: $sbn")
            log("and extras: ${notification.extras}")
        }

        val label = (tickerText?:"") + "\n" + (extraText?:"")
        log("onNotificationPosted: $label")

        mp.prepare()
        mp.start()
        val notificationID: Int = Random.nextInt(0, 999999)
        val intent = Intent(this, NotificationListener::class.java)
        intent.putExtra("action", "stop")
        intent.putExtra("notificationID", notificationID)
        val stopPlayingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notificationManager = getSystemService(NotificationManager::class.java)
        val notificationBuilder =
            Notification.Builder(this, notificationChannelID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(label)
                .setContentText("")
                .setCategory(Notification.CATEGORY_ALARM)
                .addAction(
                    Notification.Action.Builder(android.R.drawable.stat_notify_call_mute, "Stop", stopPlayingIntent)
                    .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MUTE)
                    .build())
        notificationManager.notify(notificationID, notificationBuilder.build())
    }

    private fun bundleToString(bundle: Bundle?): String {
        if (bundle == null) return "(null bundle))"
        var str = "Bundle{"
        for (key in bundle.keySet()) str += " $key: ${bundle[key]};"
        str += "}"
        return str
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("extras: ${bundleToString(intent?.extras)}")
        val action = intent?.getStringExtra("action")
        val notificationID = intent?.getIntExtra("notificationID", -2) ?: -3
        if (action != null && notificationID >= 0) dismiss(notificationID)
        else {
            if (action != null) Log.e("AMI", "onStartCommand: action $action is not null but notificationID is $notificationID!")
            if (notificationID >= 0) Log.e("AMI", "onStartCommand: action is $action but notificationID isn't negative: $notificationID!")
        }
        return START_NOT_STICKY
    }

    private fun dismiss(notificationID: Int) {
        log("dismiss: notificationID: ${notificationID}")
        if (mp.isPlaying) { mp.stop() }
        getSystemService(NotificationManager::class.java).cancel(notificationID)
    }
}

/*

 */