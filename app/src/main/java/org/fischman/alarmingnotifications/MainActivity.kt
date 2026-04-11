package org.fischman.alarmingnotifications

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.TextView


class MainActivity : Activity() {
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        // When mute settings change, refresh the UI
        if (key == muteDeadlineKey || key == muteCountKey) {
            runOnUiThread {
                if (hasAllPermissions()) {
                    setContentView(buildMainUI())
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Register preferences listener to auto-update UI when mutes change
        getSharedPreferences(this).registerOnSharedPreferenceChangeListener(prefsListener)

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

        // All permissions granted - show the main UI
        setContentView(buildMainUI())
    }

    override fun onPause() {
        super.onPause()
        // Unregister listener to avoid leaks
        getSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun hasAllPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }

        if (Build.VERSION.SDK_INT >= 31) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                return false
            }
        }

        if (!Settings.Secure.getString(contentResolver, "enabled_notification_listeners").contains(
                "$packageName/$packageName.NotificationListener"
            )
        ) {
            return false
        }

        return true
    }

    private fun buildMainUI(): View {
        val restart = {
            val intent = this.intent
            this.finish()
            this.startActivity(intent)
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Header - green banner with Yay
        rootLayout.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setPadding(dp(16), dp(20), dp(16), dp(20))

            addView(TextView(this@MainActivity).apply {
                text = "✅ Yay"
                textSize = 24f
                setTextColor(Color.WHITE)
            })
            addView(TextView(this@MainActivity).apply {
                text = "All permissions granted, now awaiting notifications. Feel free to dismiss this app."
                textSize = 13f
                setTextColor(Color.WHITE)
                alpha = 0.9f
                setPadding(0, dp(4), 0, 0)
            })
        })

        // Active Mutes section
        val mutedUntilStr = mutedUntil(this)
        val muteCount = muteCountRemaining(this)
        if (mutedUntilStr.isNotEmpty() || muteCount > 0) {
            rootLayout.addView(buildActiveMutesSection(mutedUntilStr, muteCount, restart))
        }

        // Divider
        rootLayout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)
            )
            setBackgroundColor(Color.parseColor("#f0f0f0"))
        })

        // Scrollable content area
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // Quick Mute section
        contentLayout.addView(buildQuickMuteSection(restart))

        // Custom Mute section
        contentLayout.addView(buildCustomMuteSection(restart))

        scrollView.addView(contentLayout)
        rootLayout.addView(scrollView)

        return rootLayout
    }

    private fun buildActiveMutesSection(mutedUntilStr: String, muteCount: Int, restart: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#fff3e0"))
            setPadding(dp(16), dp(16), dp(16), dp(16))

            // First row: "Active Mutes" title on left, "Unmute All" button on right
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(8))

                addView(TextView(this@MainActivity).apply {
                    text = "🔕 Active Mutes"
                    textSize = 15f
                    setTextColor(Color.parseColor("#e65100"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })

                addView(createUnmuteAllButton(restart))
            })

            // Second row: mute badges side-by-side
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                if (mutedUntilStr.isNotEmpty()) {
                    // Format time string: drop subseconds
                    val cleanTime = mutedUntilStr.substringBefore('.')
                    addView(createMuteBadge("🔕 Until $cleanTime") {
                        unmuteTime(this@MainActivity)
                        restart()
                    })
                }

                if (muteCount > 0) {
                    addView(createMuteBadge("🔕 $muteCount notes") {
                        unmuteCount(this@MainActivity)
                        restart()
                    })
                }
            })
        }
    }

    private fun createMuteBadge(text: String, onClose: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#ff9800"))
                cornerRadius = dp(20).toFloat()
            }
            setPadding(dp(12), dp(6), dp(12), dp(6))
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, dp(8), 0)
            layoutParams = lp

            addView(TextView(this@MainActivity).apply {
                this.text = text
                textSize = 13f
                setTextColor(Color.WHITE)
            })

            addView(TextView(this@MainActivity).apply {
                this.text = " ×"
                textSize = 16f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dp(6), 0, 0, 0)
                setOnClickListener { onClose() }
            })
        }
    }

    private fun createUnmuteAllButton(restart: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#f44336"))
                cornerRadius = dp(20).toFloat()
            }
            setPadding(dp(12), dp(6), dp(12), dp(6))
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener {
                unmuteAll(this@MainActivity)
                restart()
            }

            addView(TextView(this@MainActivity).apply {
                text = "🗑️ Unmute All"
                textSize = 13f
                setTextColor(Color.WHITE)
            })
        }
    }

    private fun buildQuickMuteSection(restart: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(24))
            }
            addView(TextView(this@MainActivity).apply {
                text = "Quick Mute"
                textSize = 15f
                setTextColor(Color.parseColor("#333333"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, dp(12))
            })

            // Time quick buttons: 15m, 30m, 1h, 2h
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dp(8))
                }

                addView(createQuickButton("15m") { muteForMinutes(this@MainActivity, 15); restart() })
                addView(createQuickButton("30m") { muteForMinutes(this@MainActivity, 30); restart() })
                addView(createQuickButton("1h") { muteForHours(this@MainActivity, 1); restart() })
                addView(createQuickButton("2h") { muteForHours(this@MainActivity, 2); restart() })
            })

            // Notification count quick buttons: 1n-5n
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL

                addView(createQuickButton("1n") { muteForNNotifications(this@MainActivity, 1); restart() })
                addView(createQuickButton("2n") { muteForNNotifications(this@MainActivity, 2); restart() })
                addView(createQuickButton("3n") { muteForNNotifications(this@MainActivity, 3); restart() })
                addView(createQuickButton("4n") { muteForNNotifications(this@MainActivity, 4); restart() })
                addView(createQuickButton("5n") { muteForNNotifications(this@MainActivity, 5); restart() })
            })
        }
    }

    private fun createQuickButton(label: String, onClick: () -> Unit): View {
        return Button(this).apply {
            text = label
            textSize = 14f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#f5f5f5"))
                setStroke(dp(1), Color.parseColor("#e0e0e0"))
                cornerRadius = dp(8).toFloat()
            }
            setTextColor(Color.parseColor("#333333"))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(0, 0, dp(8), 0)
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun buildCustomMuteSection(restart: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            addView(TextView(this@MainActivity).apply {
                text = "Custom Mute"
                textSize = 15f
                setTextColor(Color.parseColor("#333333"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, dp(12))
            })

            // NumberPickers row
            val pickerRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 0, 0, dp(16))
                layoutParams = lp
            }

            val hoursPicker = NumberPicker(this@MainActivity).apply {
                minValue = 0
                maxValue = 24
                value = 1
            }

            val notifPicker = NumberPicker(this@MainActivity).apply {
                minValue = 0
                maxValue = 24
                value = 5
            }

            val hoursGroup = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.setMargins(0, 0, dp(8), 0)
                layoutParams = lp
                gravity = Gravity.CENTER_HORIZONTAL

                addView(TextView(this@MainActivity).apply {
                    text = "Hours"
                    textSize = 12f
                    setTextColor(Color.parseColor("#666666"))
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dp(8))
                })
                addView(hoursPicker, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(150)
                ))
            }

            val notifsGroup = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.CENTER_HORIZONTAL

                addView(TextView(this@MainActivity).apply {
                    text = "Notifications"
                    textSize = 12f
                    setTextColor(Color.parseColor("#666666"))
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dp(8))
                })
                addView(notifPicker, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(150)
                ))
            }

            pickerRow.addView(hoursGroup)
            pickerRow.addView(notifsGroup)
            addView(pickerRow)

            // Apply button
            addView(Button(this@MainActivity).apply {
                text = "Apply Custom Mute"
                textSize = 15f
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#4CAF50"))
                    cornerRadius = dp(8).toFloat()
                }
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(14), 0, dp(14))
                setOnClickListener {
                    val hours = hoursPicker.value
                    val notifs = notifPicker.value
                    muteForHours(this@MainActivity, hours)
                    muteForNNotifications(this@MainActivity, notifs)
                    restart()
                }
            })
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
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
