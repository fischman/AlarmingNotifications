package org.fischman.alarmingnotifications

import android.annotation.SuppressLint
import android.app.Activity
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AlarmActivity : Activity() {
    private val DEBUG = false
    private fun log(msg: String) { if (DEBUG) Log.e("AMI", msg) }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val label = intent.getStringExtra("label") ?: "MISSING"
        log("In activity, label is $label")

        val pageView = LinearLayout(this)
        pageView.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        pageView.orientation = LinearLayout.VERTICAL

        val labelView = TextView(this)
        labelView.gravity = Gravity.CENTER
        labelView.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, 1F)
        labelView.text = "\n\n$label\n\n"
        labelView.textSize = 35.0F
        labelView.setBackgroundColor(0xFF000000.toInt())
        pageView.addView(labelView)

        // NOTE: might someday consider honoring do-not-disturb mode. Can detect it sans permissions with:
        // https://stackoverflow.com/a/35772278.

        val mp = MediaPlayer()
        mp.setDataSource(applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        mp.setAudioStreamType(AudioManager.STREAM_ALARM)
        mp.prepare()
        mp.start()

        val dismissView = Button(this)
        dismissView.gravity = Gravity.CENTER
        dismissView.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 200, 0F)
        dismissView.text = "Dismiss"
        dismissView.textSize = 35.0F
        dismissView.setTextColor(0xFF00FF00.toInt())
        dismissView.setBackgroundColor(0xFF660066.toInt())
        dismissView.setOnClickListener {
            mp.stop()
            mp.release()
            finish()
        }
        pageView.addView(dismissView)

        setContentView(pageView)
    }
}