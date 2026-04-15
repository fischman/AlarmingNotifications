package org.fischman.alarmingnotifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.concurrent.Executors

data class NotificationAppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable,
)

/**
 * View showing a filterable, scrollable, checkable list of
 * every installed app that holds the POST_NOTIFICATIONS permission.
 */
class NotificationAppsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /** Currently-checked package names. */
    val checkedPackages: MutableSet<String> = mutableSetOf()

    /** Optional callback: (packageName, isChecked) -> Unit */
    var onCheckedChangeListener: ((String, Boolean) -> Unit)? = null

    private val allApps = mutableListOf<NotificationAppInfo>()
    private val filteredApps = mutableListOf<NotificationAppInfo>()
    private val adapter = AppAdapter()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = Executors.newSingleThreadExecutor()

    private val filterEdit: EditText
    private val clearButton: TextView
    private val listView: ListView
    private val progress: ProgressBar
    private val emptyText: TextView

    init {
        orientation = VERTICAL

        filterEdit = EditText(context).apply {
            hint = "Filter apps…"
            setSingleLine()
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        clearButton = TextView(context).apply {
            text = "✕"
            textSize = 10f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER_VERTICAL or Gravity.END).apply {
                marginEnd = dp(10)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.LTGRAY)
            }
            visibility = View.GONE
            setOnClickListener { filterEdit.text.clear() }
        }

        val filterContainer = FrameLayout(context)
        filterContainer.addView(filterEdit)
        filterContainer.addView(clearButton)
        addView(filterContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        progress = ProgressBar(context).apply {
            visibility = View.VISIBLE
        }
        addView(progress, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
            topMargin = dp(32)
        })

        emptyText = TextView(context).apply {
            text = "No matching apps"
            gravity = Gravity.CENTER
            visibility = View.GONE
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
        }
        addView(emptyText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        listView = ListView(context).apply {
            adapter = this@NotificationAppsView.adapter
            dividerHeight = 0
            visibility = View.GONE
        }
        addView(listView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        filterEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { 
                clearButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                applyFilter()
            }
        })

        bgExecutor.execute { loadApps() }
    }

    private fun loadApps() {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { info ->
                (info.packageName != context.packageName) && (pm.checkPermission(Manifest.permission.POST_NOTIFICATIONS, info.packageName) == PackageManager.PERMISSION_GRANTED)
            }
            .map { info ->
                NotificationAppInfo(
                    appName = pm.getApplicationLabel(info).toString(),
                    packageName = info.packageName,
                    icon = pm.getApplicationIcon(info),
                )
            }
            .sortedBy { it.appName.lowercase() }

        mainHandler.post {
            allApps.clear()
            allApps.addAll(apps)
            progress.visibility = View.GONE
            listView.visibility = View.VISIBLE
            applyFilter()
        }
    }

    private fun applyFilter() {
        val query = filterEdit.text?.toString()?.trim()?.lowercase().orEmpty()
        filteredApps.clear()
        if (query.isEmpty()) {
            filteredApps.addAll(allApps)
        } else {
            allApps.filterTo(filteredApps) {
                it.appName.lowercase().contains(query) ||
                    it.packageName.lowercase().contains(query)
            }
        }
        emptyText.visibility = if (filteredApps.isEmpty() && progress.visibility != View.VISIBLE) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = filteredApps.size
        override fun getItem(pos: Int) = filteredApps[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? RowView) ?: RowView(context)
            row.bind(filteredApps[position])
            return row
        }
    }

    private inner class RowView(ctx: Context) : LinearLayout(ctx) {
        private val checkBox = CheckBox(ctx).apply {
            buttonTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
                intArrayOf(Color.BLUE, Color.LTGRAY)
            )
        }
        private val iconView = ImageView(ctx)
        private val nameText = TextView(ctx).apply {
            textSize = 16f
            setSingleLine()
            setTextColor(Color.BLACK)
        }
        private val pkgText = TextView(ctx).apply {
            textSize = 12f
            setSingleLine()
            setTextColor(Color.DKGRAY)
        }
        private var boundPkg: String? = null

        private val checkedListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            val pkg = boundPkg ?: return@OnCheckedChangeListener
            if (isChecked) checkedPackages.add(pkg) else checkedPackages.remove(pkg)
            onCheckedChangeListener?.invoke(pkg, isChecked)
        }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val hPad = dp(12)
            val vPad = dp(6)
            setPadding(hPad, vPad, hPad, vPad)

            addView(checkBox, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(iconView, LayoutParams(dp(40), dp(40)).apply { leftMargin = dp(8) })

            addView(LinearLayout(ctx).apply {
                orientation = VERTICAL
                addView(nameText)
                addView(pkgText)
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12) })

            checkBox.setOnCheckedChangeListener(checkedListener)
        }

        fun bind(app: NotificationAppInfo) {
            boundPkg = app.packageName
            checkBox.setOnCheckedChangeListener(null) // Prevent spurious callback.
            checkBox.isChecked = app.packageName in checkedPackages
            checkBox.setOnCheckedChangeListener(checkedListener)
            iconView.setImageDrawable(app.icon)
            nameText.text = app.appName
            pkgText.text = app.packageName
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
