package app.glassclock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class ClockWeatherWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
        WeatherWorker.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        render(context, manager, id)
    }

    override fun onEnabled(context: Context) {
        WeatherWorker.schedule(context)
        WeatherWorker.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        WeatherWorker.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            val s = Prefs.load(context)
            Prefs.save(context, s.copy(detail = !s.detail))
            updateAll(context)
            val w = WeatherRepo.load(context)
            if (w == null || System.currentTimeMillis() - w.updatedAt > 10 * 60 * 1000L) {
                WeatherWorker.refreshNow(context)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "app.glassclock.widget.TOGGLE_DETAIL"

        fun updateAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, ClockWeatherWidget::class.java))
            ids.forEach { render(ctx, manager, it) }
        }

        fun render(ctx: Context, manager: AppWidgetManager, id: Int) {
            val o = manager.getAppWidgetOptions(id)
            val portrait = ctx.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            var w = o.getInt(
                if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0
            ).toFloat()
            var h = o.getInt(
                if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0
            ).toFloat()
            if (w <= 0f) w = 340f
            if (h <= 0f) h = 170f
            try {
                manager.updateAppWidget(id, WidgetRenderer.build(ctx, w, h, true))
            } catch (e: Exception) {
                Log.e("GlassClock", "Widget update failed", e)
            }
        }
    }
}

object WidgetRenderer {

    private val dayName = intArrayOf(R.id.d0_name, R.id.d1_name, R.id.d2_name, R.id.d3_name)
    private val dayIcon = intArrayOf(R.id.d0_icon, R.id.d1_icon, R.id.d2_icon, R.id.d3_icon)
    private val dayHi = intArrayOf(R.id.d0_hi, R.id.d1_hi, R.id.d2_hi, R.id.d3_hi)
    private val dayLo = intArrayOf(R.id.d0_lo, R.id.d1_lo, R.id.d2_lo, R.id.d3_lo)

    /**
     * Builds the whole widget for a box of [wDp] x [hDp]. The design is drawn for 358 x 196
     * and scaled to fit; short widgets use a compact layout without the date line.
     */
    fun build(ctx: Context, wDp: Float, hDp: Float, interactive: Boolean): RemoteViews {
        val s = Prefs.load(ctx)
        val wx = WeatherRepo.load(ctx)
        val density = ctx.resources.displayMetrics.density
        val compact = hDp < 185f
        val baseH = if (compact) 152f else 196f
        val k = minOf(wDp / 358f, hDp / baseH).coerceIn(0.7f, 1.35f)

        val shadow = s.dark && (s.bg == "clear" || s.bg == "liquid")
        val v = RemoteViews(ctx.packageName, if (shadow) R.layout.widget_shadow else R.layout.widget)

        val fg = if (s.dark) Color.WHITE else 0xFF14161A.toInt()
        val sub = if (s.dark) 0xFFDFE3EA.toInt() else 0xFF2F343D.toInt()
        val cloud = if (s.dark) 0xFFEEF1F6.toInt() else 0xFF9AA5B6.toInt()

        fun px(dp: Float): Int = (dp * k * density).roundToInt()
        fun size(id: Int, dp: Float) {
            v.setViewLayoutWidth(id, dp * k, TypedValue.COMPLEX_UNIT_DIP)
            v.setViewLayoutHeight(id, dp * k, TypedValue.COMPLEX_UNIT_DIP)
        }
        fun text(id: Int, dp: Float, color: Int) {
            v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, dp * k)
            v.setTextColor(id, color)
        }
        fun temp(c: Double): String = (if (s.fahrenheit) c * 9.0 / 5.0 + 32.0 else c).roundToInt().toString() + "°"

        // ---- background
        if (s.bg == "clear") {
            v.setViewVisibility(R.id.bg, View.GONE)
        } else {
            val rd = minOf(density, 2.5f)
            v.setViewVisibility(R.id.bg, View.VISIBLE)
            v.setImageViewBitmap(R.id.bg, Render.background((wDp * rd).roundToInt(), (hDp * rd).roundToInt(), rd * k, s))
        }
        val pad = px(if (compact) 14f else 16f)
        v.setViewPadding(R.id.content, pad, pad, pad, pad)
        v.setViewLayoutMargin(R.id.weather, RemoteViews.MARGIN_START, 14f * k, TypedValue.COMPLEX_UNIT_DIP)

        // ---- clock (the system moves the hands, so it stays smooth and exact)
        val clockDp = if (compact) 120f else 140f
        size(R.id.clock_box, clockDp)
        val cpx = px(clockDp)
        val clockId = if (s.seconds) R.id.clock_sec else R.id.clock_nosec
        v.setViewVisibility(R.id.clock_sec, if (s.seconds) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.clock_nosec, if (s.seconds) View.GONE else View.VISIBLE)
        v.setIcon(clockId, "setDial", Icon.createWithBitmap(Render.dial(cpx, s)))
        v.setIcon(clockId, "setHourHand", Icon.createWithBitmap(Render.hourHand(cpx, s)))
        v.setIcon(clockId, "setMinuteHand", Icon.createWithBitmap(Render.minuteHand(cpx, s)))
        if (s.seconds) v.setIcon(clockId, "setSecondHand", Icon.createWithBitmap(Render.secondHand(cpx, s)))

        // ---- weather
        if (wx == null) {
            v.setViewVisibility(R.id.now, View.GONE)
            v.setViewVisibility(R.id.detail, View.GONE)
            v.setViewVisibility(R.id.status, View.VISIBLE)
            v.setTextViewText(R.id.status, WeatherRepo.lastError(ctx).ifEmpty { "Loading weather…" })
            text(R.id.status, 13f, sub)
        } else {
            v.setViewVisibility(R.id.status, View.GONE)
            v.setViewVisibility(R.id.now, if (s.detail) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.detail, if (s.detail) View.VISIBLE else View.GONE)

            // now
            v.setViewVisibility(R.id.date_row, if (compact) View.GONE else View.VISIBLE)
            text(R.id.date, 13f, sub)
            text(R.id.place, 13f, sub)
            v.setTextViewText(R.id.place, " · " + s.place)
            text(R.id.temp, if (compact) 34f else 40f, fg)
            v.setTextViewText(R.id.temp, temp(wx.tempC))
            val iconDp = if (compact) 36f else 44f
            size(R.id.icon, iconDp)
            v.setImageViewBitmap(R.id.icon, Render.weatherIcon(wx.kind, wx.isDay, px(iconDp), cloud))
            v.setContentDescription(R.id.icon, wx.cond)
            text(R.id.cond, 14f, fg)
            v.setTextViewText(R.id.cond, wx.cond)
            size(R.id.drop, 12f)
            v.setImageViewBitmap(R.id.drop, Render.drop(px(12f)))
            text(R.id.hum, 13f, fg)
            v.setTextViewText(R.id.hum, wx.humidity.toString() + "%")
            v.setInt(R.id.divider, "setBackgroundColor", if (s.dark) Render.alpha(Color.WHITE, 0.24f) else Render.alpha(Color.BLACK, 0.16f))

            for (i in 0 until 4) {
                val d = wx.days.getOrNull(i)
                text(dayName[i], 12f, sub)
                text(dayHi[i], 13f, fg)
                text(dayLo[i], 12f, sub)
                size(dayIcon[i], 16f)
                if (d == null) {
                    v.setTextViewText(dayName[i], "")
                    v.setTextViewText(dayHi[i], "")
                    v.setTextViewText(dayLo[i], "")
                    v.setViewVisibility(dayIcon[i], View.INVISIBLE)
                } else {
                    v.setTextViewText(dayName[i], if (i == 0) "Today" else weekday(d.date))
                    v.setTextViewText(dayHi[i], temp(d.hiC))
                    v.setTextViewText(dayLo[i], temp(d.loC))
                    v.setViewVisibility(dayIcon[i], View.VISIBLE)
                    v.setImageViewBitmap(dayIcon[i], Render.weatherIcon(d.kind, true, px(16f), cloud))
                }
            }

            // details
            text(R.id.detail_title, 14f, fg)
            text(R.id.feels_label, 14f, sub)
            text(R.id.feels, 14f, fg)
            v.setTextViewText(R.id.feels, temp(wx.feelsC))
            text(R.id.wind_label, 14f, sub)
            text(R.id.wind, 14f, fg)
            v.setTextViewText(
                R.id.wind,
                if (s.fahrenheit) (wx.windKmh * 0.621371).roundToInt().toString() + " mph"
                else wx.windKmh.roundToInt().toString() + " km/h"
            )
            text(R.id.updated, 12f, sub)
            v.setTextViewText(
                R.id.updated,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(wx.updatedAt)) + " · " + wx.source
            )
        }

        if (interactive) {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val toggle = Intent(ctx, ClockWeatherWidget::class.java).setAction(ClockWeatherWidget.ACTION_TOGGLE)
            v.setOnClickPendingIntent(R.id.weather, PendingIntent.getBroadcast(ctx, 1, toggle, flags))
            val open = Intent(ctx, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            v.setOnClickPendingIntent(R.id.clock_box, PendingIntent.getActivity(ctx, 2, open, flags))
        }
        return v
    }

    private fun weekday(date: String): String = try {
        LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    } catch (e: Exception) {
        ""
    }
}
