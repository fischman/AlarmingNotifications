package org.fischman.alarmingnotifications

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log


class MainActivity : Activity() {
    private val DEBUG = false
    private fun log(msg: String) { if (DEBUG) Log.e("AMI", msg) }

    private val permissionRequestCode = 42

    override fun onResume() {
        super.onResume()

        Log.e("YO1", "Starting to collect all apps' names & packages")
        var apps = arrayOf("")
        // getPackagesHoldingPermissions takes 180-280ms on a Pixel 8 with 155 apps installed.
        val packages = packageManager.getPackagesHoldingPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), PackageManager.GET_ACTIVITIES)
        Log.e("YO1", "number of apps: ${packages.size}")
        // This loop takes another 800ms to run.
        for (l in packages) {
            if (!l.applicationInfo.enabled) { continue }
            apps += "${l.applicationInfo.loadLabel(packageManager)} - ${l.packageName}"
            val d = l.applicationInfo.loadIcon(packageManager)
        }
        Log.e("YO1", "done:\n${apps.joinToString("\n")}")

        when {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {}
            else -> {
                alert("Permission to Send Notifications",
                    "To work correctly, this app needs permission to send you notifications. Please allow this on the next screen.") {
                    requestPermissions(
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        permissionRequestCode
                    )
                }
                return
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