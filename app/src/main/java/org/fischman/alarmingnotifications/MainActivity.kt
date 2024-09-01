package org.fischman.alarmingnotifications

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log


class MainActivity : Activity() {
    private val debug = false
    private fun log(msg: String) {
        if (debug) Log.e("AMI", msg)
    }

    override fun onResume() {
        super.onResume()

        if (Build.VERSION.SDK_INT >= 33) {
            when (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                PackageManager.PERMISSION_GRANTED -> {}
                else -> {
                    alert(
                        "Permission to Send Notifications",
                        "To work correctly, this app needs permission to send you notifications. Please allow this on the next screen."
                    ) {
                        requestPermissions(
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            0, // Request code is unused since we don't listen for rejections. But the platform requires this to be >=0.
                        )
                    }
                    return
                }
            }
        }

        if (Build.VERSION.SDK_INT >= 31) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                log("Not already allowed to schedule exact alarms, launching settings")
                alert(
                    "Permission to set exact alarms",
                    "Please grant the \"Alarms & reminders\" permission for ${resources.getString(R.string.app_name)} to enable snooze functionality."
                ) {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    finish()
                }
                return
            } else {
                log("Already listening for notifications, yay")
            }
        }

        if (!Settings.Secure.getString(contentResolver, "enabled_notification_listeners").contains(
                "$packageName/$packageName.NotificationListener"
            )
        ) {
            log("Not already listening for notifications, launching settings")
            alert(
                "Permission to Read Notifications",
                "Please grant the \"Device & app notifications\" permission for ${
                    resources.getString(
                        R.string.app_name
                    )
                } and then restart the app."
            ) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                finish()
            }
            return
        } else {
            log("Already listening for notifications, yay")
        }

        val permissionsYay = "All permissions granted, now awaiting notifications.\n" +
            "Feel free to dismiss this app now, notifications will be watched in the background."
        val mutedUntilStr = mutedUntil(this)
        var maybeMuted = ""
        if (mutedUntilStr != "") {
            maybeMuted = "Currently muted until $mutedUntilStr\n"
        }
        var otherButton = "Mute for 1h"
        var onOtherButton = { muteForOneHour(this); val intent = this.intent; this.finish(); this.startActivity(intent) }
        if (maybeMuted != "") {
            maybeMuted = "\n\n$maybeMuted"
            otherButton = "Unmute"
            onOtherButton = { unmute(this); val intent = this.intent; this.finish(); this.startActivity(intent) }
        }
        alert2(
            "Yay",
            "$permissionsYay$maybeMuted",
            { this.finish() },
            otherButton, onOtherButton,
            { val intent = this.intent; this.finish(); this.startActivity(intent) },
            { this.finish() }
        )
    }

    private fun alert(title: String, msg: String, onOK: () -> Unit) {
        alert2(title, msg, onOK, null, null, {}, {})
    }

    private fun alert2(title: String, msg: String, onOK: () -> Unit,
                       otherButtonLabel: String?, onOtherButton: (() -> Unit)?,
                       restart: ()->Unit, onCancel: ()->Unit) {
        val builder = AlertDialog.Builder(this@MainActivity)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                onOK()
            }
            .setOnCancelListener() { dialog ->
                dialog.dismiss()
                onCancel()
            }
        if (otherButtonLabel != null) {
            builder.setNeutralButton(otherButtonLabel ?: "") { dialog, _ ->
                dialog.dismiss()
                onOtherButton?.invoke()
                restart()
            }
        }
        builder.create().show()
    }
}