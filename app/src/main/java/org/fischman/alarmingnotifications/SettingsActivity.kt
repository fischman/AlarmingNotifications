package org.fischman.alarmingnotifications

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.view.MenuItem

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUI())
        actionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle("Settings")
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun buildUI(): View {
        val prefs = getSharedPreferences(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        content.addView(sectionHeader("Notification Filters"))

        content.addView(Switch(this).apply {
            text = "Ignore Google Keep reminders:"
            textSize = 15f
            isChecked = prefs.getBoolean(ignoreKeepKey, true)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(8), 0, dp(8)) }
            setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                prefs.edit().putBoolean(ignoreKeepKey, checked).apply()
            }
        })

        content.addView(TextView(this).apply {
            text = "Ignore notifications whose text ends with:"
            textSize = 15f
            setTextColor(Color.BLACK)
            setPadding(0, dp(8), 0, dp(4))
        })
        content.addView(EditText(this).apply {
            setText(prefs.getString(ignoreSuffixKey, "/s"))
            textSize = 15f
            hint = "e.g. /s"
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(1), Color.GRAY)
                cornerRadius = dp(6).toFloat()
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(8)) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(e: Editable?) {
                    prefs.edit().putString(ignoreSuffixKey, e?.toString() ?: "").apply()
                }
            })
        })

        content.addView(sectionHeader("Apps to Alarm On"))
        content.addView(TextView(this).apply {
            text = "Select additional apps whose notifications should trigger an alarm:"
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, dp(4), 0, dp(12))
        })

        val appsView = NotificationAppsView(this).apply {
            checkedPackages.addAll(prefs.getStringSet(alarmPackagesKey, defaultAlarmPackages) ?: defaultAlarmPackages)
            onCheckedChangeListener = { pkg, checked ->
                prefs.edit().putStringSet(alarmPackagesKey, checkedPackages).apply()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        content.addView(appsView)

        root.addView(content)
        return root
    }

    private fun sectionHeader(title: String): View =
        TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(8), 0, 0) }
        }
}
