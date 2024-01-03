package org.fischman.alarmingnotifications

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log


class MainActivity : Activity() {
    private val debug = false
    private fun log(msg: String) { if (debug) Log.e("AMI", msg) }

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
                            -1, // Request code is unused since we don't listen for rejections.
                        )
                    }
                    return
                }
            }
        }

        if (!Settings.Secure.getString(contentResolver,"enabled_notification_listeners").contains(
                "$packageName/$packageName.NotificationListener")) {
            log("Not already listening for notifications, launching settings")
            alert("Permission to Read Notifications",
                "Please grant the \"Device & app notifications\" permission for ${resources.getString(R.string.app_name)} and then restart the app.") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                finish()
            }
            return
        } else {
            log("Already listening for notifications, yay")
        }

        alert("Yay", "All permissions granted, now awaiting notifications.\nFeel free to dismiss this app now, notifications will be watched in the background.") { finish() }
    }

    private fun alert(title: String, msg: String, onOK: () -> Unit) {
        AlertDialog.Builder(this@MainActivity)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                onOK()
            }
            .create()
            .show()
    }
}