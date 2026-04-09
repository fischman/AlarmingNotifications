package org.fischman.alarmingnotifications

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView


class MainActivity : Activity() {
    private val debug = BuildConfig.DEBUG
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
        val muteCount = muteCountRemaining(this)
        var maybeMuted = ""
        if (mutedUntilStr != "") {
            maybeMuted = "Currently muted until $mutedUntilStr\n"
        }
        if (muteCount > 0) {
            maybeMuted += "Muted for $muteCount more notification(s)\n"
        }
        var otherButton = "Mute for 1h"
        var onOtherButton = { muteForOneHour(this); val intent = this.intent; this.finish(); this.startActivity(intent) }
        if (maybeMuted != "") {
            maybeMuted = "\n\n$maybeMuted"
            otherButton = "Unmute"
            onOtherButton = { unmute(this); val intent = this.intent; this.finish(); this.startActivity(intent) }
        }
        val restart = { val intent = this.intent; this.finish(); this.startActivity(intent) }
        val muteForNView = buildMuteForNView(restart)
        alert2(
            "Yay",
            "$permissionsYay$maybeMuted",
            { this.finish() },
            otherButton, onOtherButton,
            restart,
            { this.finish() },
            muteForNView
        )
    }

    private fun buildMuteForNView(restart: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("1")
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(Button(this).apply {
            text = "\u2212" // minus sign
            setOnClickListener {
                val cur = input.text.toString().toIntOrNull() ?: 1
                if (cur > 1) input.setText((cur - 1).toString())
            }
        })
        row.addView(input)
        row.addView(Button(this).apply {
            text = "+"
            setOnClickListener {
                val cur = input.text.toString().toIntOrNull() ?: 0
                input.setText((cur + 1).toString())
            }
        })
        row.addView(Button(this).apply {
            text = "Mute"
            setOnClickListener {
                val n = input.text.toString().toIntOrNull() ?: 1
                if (n > 0) {
                    muteForNNotifications(this@MainActivity, n)
                    restart()
                }
            }
        })

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "Mute for N notifications:"
                setPadding(48, 16, 48, 0)
            })
            addView(row)
        }
    }

    private fun alert(title: String, msg: String, onOK: () -> Unit) {
        alert2(title, msg, onOK, null, null, {}, {})
    }

    private fun alert2(title: String, msg: String, onOK: () -> Unit,
                       otherButtonLabel: String?, onOtherButton: (() -> Unit)?,
                       restart: ()->Unit, onCancel: ()->Unit,
                       customView: View? = null) {
        val builder = AlertDialog.Builder(this@MainActivity)
            .setTitle(title)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                onOK()
            }
            .setOnCancelListener() { dialog ->
                dialog.dismiss()
                onCancel()
            }
        if (customView != null) {
            // Wrap the message text and custom view together, since
            // AlertDialog.setMessage and setView don't coexist reliably.
            val wrapper = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = msg
                    setPadding(48, 32, 48, 0)
                })
                addView(customView)
            }
            builder.setView(wrapper)
        } else {
            builder.setMessage(msg)
        }
        if (otherButtonLabel != null) {
            builder.setNeutralButton(otherButtonLabel ?: "") { dialog, _ ->
                dialog.dismiss()
                onOtherButton?.invoke()
                restart()
            }
            if (debug) {
                builder.setNegativeButton("Create\nTest\nEvent") { dialog, _ ->
                    dialog.dismiss()
                    startActivity(
                        Intent(Intent.ACTION_INSERT)
                            .setData(CalendarContract.Events.CONTENT_URI)
                            .putExtra(
                                CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                                System.currentTimeMillis() + 30000
                            )
                            .putExtra(CalendarContract.Events.TITLE, "Test")
                    )
                    restart()
                }
            }
        }
        builder.create().show()
    }
}