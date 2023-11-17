package org.fischman.alarmingnotifications

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import android.util.Log


class MainActivity : Activity() {
    private val DEBUG = false
    private fun log(msg: String) { if (DEBUG) Log.e("AMI", msg) }

    override fun onResume() {
        super.onResume()

        // For development and debugging: directly launch the AlarmActivity.
        if (false) {
            val i = Intent("$packageName.AlarmActivity")
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            i.putExtra("label", "DEBUGGING from MainActivity")
            startActivity(i)
            finish()
            return
        }

        val appName = resources.getString(R.string.app_name)

        if (!Settings.Secure.getString(contentResolver,"enabled_notification_listeners").contains(
                "$packageName/$packageName.NotificationListener")) {
            log("Not already listening for notifications, launching settings")
            alert(this, "Need Permission", "Please grant the \"Device & app notifications\" permission for $appName and then restart the app") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            return
        } else {
            log("Already listening for notifications, yay")
        }

        if (!Settings.canDrawOverlays(this)) {
            log("Jumping to settings for overlay permissions")
            alert(this, "Need Permission", "Please grant the \"Display over other apps\" permission for $appName and then restart the app") {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
            return
        } else {
            log("Already have overlay permission")
        }

        alert(this,"Yay", "All permissions granted, now awaiting notifications.\nFeel free to dismiss this app now, notifications will be watched in the background.") {}
    }

    private fun alert(activity: Activity, title: String, msg: String, onOK: () -> Unit) {
        val alertDialog = AlertDialog.Builder(this@MainActivity).create()
        alertDialog.setTitle(title)
        alertDialog.setMessage(msg)
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK") { dialog, _ ->
            run {
                dialog.dismiss()
                onOK()
                activity.finish()
            }
        }
        alertDialog.show()
    }
}