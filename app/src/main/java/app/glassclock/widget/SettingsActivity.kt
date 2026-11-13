package app.glassclock.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import java.text.DateFormat
import java.util.Date
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/** Settings screen. Also opened by the launcher when the widget is long-pressed > Settings. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var s: Settings
    private lateinit var preview: FrameLayout
    private lateinit var status: TextView
    private lateinit var placeLabel: TextView

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = Prefs.load(this)

        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(16f), dp(20f), dp(32f))
        }
        val scroll = ScrollView(this).apply {
            fitsSystemWindows = true
            addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(scroll)

        column.addView(heading("Clock + Weather widget", 22f))

        // ---- live preview on a colourful backdrop, so glass and transparency are visible
        preview = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFFFF7A59.toInt(), 0xFF7C5CFF.toInt(), 0xFF1FB6D6.toInt())
            ).apply { cornerRadius = dp(24f).toFloat() }
            setPadding(dp(12f), dp(20f), dp(12f), dp(20f))
        }
        column.addView(preview, spaced(12f))

        status = TextView(this).apply { textSize = 13f }
        column.addView(status, spaced(8f))

        // ---- look
        column.addView(label("Background"))
        column.addView(
            choices(listOf("liquid" to "Liquid", "glass" to "Glass", "solid" to "Solid", "clear" to "Clear"), s.bg) {
                update(s.copy(bg = it))
            }
        )

        column.addView(label("Transparency"))
        column.addView(Slider(this).apply {
            valueFrom = 0f
            valueTo = 100f
            stepSize = 1f
            value = s.transparency.coerceIn(0, 100).toFloat()
            addOnChangeListener { _, v, fromUser -> if (fromUser) update(s.copy(transparency = v.roundToInt())) }
        })

        column.addView(label("Theme"))
        column.addView(choices(listOf("dark" to "Dark", "light" to "Light"), if (s.dark) "dark" else "light") {
            update(s.copy(dark = it == "dark"))
        })

        column.addView(label("Accent colour"))
        column.addView(
            choices(listOf("217" to "Blue", "150" to "Green", "28" to "Orange", "330" to "Pink", "265" to "Purple"), s.hue.toString()) {
                update(s.copy(hue = it.toInt()))
            }
        )

        column.addView(label("Clock shape"))
        column.addView(
            choices(listOf("scallop" to "Scallop", "circle" to "Circle", "squircle" to "Squircle"), s.shape) {
                update(s.copy(shape = it))
            }
        )

        column.addView(MaterialSwitch(this).apply {
            text = "Second hand"
            isChecked = s.seconds
            setOnCheckedChangeListener { _, on -> update(s.copy(seconds = on)) }
        }, spaced(12f))

        column.addView(label("Units"))
        column.addView(choices(listOf("c" to "°C", "f" to "°F"), if (s.fahrenheit) "f" else "c") {
            update(s.copy(fahrenheit = it == "f"))
        })

        // ---- location
        column.addView(heading("Location", 18f), spaced(24f))
        placeLabel = TextView(this).apply { textSize = 14f }
        column.addView(placeLabel, spaced(4f))
        val city = EditText(this).apply {
            hint = "City name, for example Dhaka"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine()
        }
        column.addView(city, spaced(4f))
        column.addView(button("Search and use this city") {
            val q = city.text.toString().trim()
            if (q.isEmpty()) return@button
            status.text = "Searching…"
            thread {
                val found = try {
                    WeatherRepo.geocode(q)
                } catch (e: Exception) {
                    null
                }
                runOnUiThread {
                    if (found == null) {
                        status.text = "Could not find \"$q\". Check the spelling and your connection."
                    } else {
                        update(s.copy(place = found.name, lat = found.lat, lon = found.lon))
                        refresh()
                    }
                }
            }
        })

        // ---- weather source
        column.addView(heading("Weather source", 18f), spaced(24f))
        column.addView(TextView(this).apply {
            textSize = 14f
            text = "Paste a Google Weather API key to use Google's data. Without a key the widget uses Open-Meteo. " +
                "Weather refreshes every 30 minutes."
        }, spaced(4f))
        val key = EditText(this).apply {
            hint = "Google Weather API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setSingleLine()
            setText(s.apiKey)
        }
        column.addView(key, spaced(4f))
        column.addView(button("Save key and refresh") {
            update(s.copy(apiKey = key.text.toString().trim()))
            refresh()
        })
        column.addView(button("Refresh weather now") { refresh() })

        showPlace()
        showStatus()
        preview.post { showPreview() }
        WeatherWorker.schedule(this)
        if (WeatherRepo.load(this) == null) refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::preview.isInitialized && preview.width > 0) showPreview()
    }

    // ------------------------------------------------------------------ actions

    private fun update(next: Settings) {
        // The widget itself can flip "detail", so keep whatever is stored for that field.
        s = next.copy(detail = Prefs.load(this).detail)
        Prefs.save(this, s)
        showPlace()
        showPreview()
        ClockWeatherWidget.updateAll(this)
    }

    private fun refresh() {
        status.text = "Loading weather…"
        val app = applicationContext
        thread {
            WeatherRepo.refresh(app)
            runOnUiThread {
                showStatus()
                showPreview()
                ClockWeatherWidget.updateAll(app)
            }
        }
    }

    private fun showPlace() {
        placeLabel.text = "Current: " + s.place
    }

    private fun showStatus() {
        val wx = WeatherRepo.load(this)
        val error = WeatherRepo.lastError(this)
        val line = if (wx == null) "No weather loaded yet."
        else "Source: " + wx.source + ", updated " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(wx.updatedAt))
        status.text = if (error.isEmpty()) line else "$line\n$error"
    }

    /** Shows the real widget layout inside the app. */
    private fun showPreview() {
        val density = resources.displayMetrics.density
        val innerPx = preview.width - preview.paddingLeft - preview.paddingRight
        if (innerPx <= 0) return
        val wDp = innerPx / density
        val hDp = wDp * 196f / 358f
        try {
            val view = WidgetRenderer.build(this, wDp, hDp, false).apply(applicationContext, preview)
            preview.removeAllViews()
            preview.addView(view, FrameLayout.LayoutParams(innerPx, (hDp * density).roundToInt(), Gravity.CENTER))
        } catch (e: Exception) {
            status.text = "Preview failed: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    // ------------------------------------------------------------------ small view helpers

    private fun spaced(topDp: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(topDp)
        }

    private fun heading(text: String, sizeSp: Float): TextView = TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        layoutParams = spaced(16f)
    }

    private fun button(text: String, onClick: () -> Unit): MaterialButton = MaterialButton(this).apply {
        this.text = text
        layoutParams = spaced(8f)
        setOnClickListener { onClick() }
    }

    private fun choices(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit): View {
        val group = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            layoutParams = spaced(4f)
        }
        val values = HashMap<Int, String>()
        for ((value, text) in options) {
            val b = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = View.generateViewId()
                this.text = text
                isAllCaps = false
                textSize = 13f
                maxLines = 1
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(4f), 0, dp(4f), 0)
                minHeight = dp(48f)
                minimumHeight = dp(48f)
            }
            values[b.id] = value
            group.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (value == selected) group.check(b.id)
        }
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) values[checkedId]?.let(onPick)
        }
        return group
    }
}
