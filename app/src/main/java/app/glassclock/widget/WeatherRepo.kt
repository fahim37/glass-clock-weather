package app.glassclock.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class Day(val date: String, val hiC: Double, val loC: Double, val kind: String)

/** Stored in metric; converted to the chosen units when drawn. */
data class Weather(
    val tempC: Double,
    val feelsC: Double,
    val humidity: Int,
    val windKmh: Double,
    val cond: String,
    val kind: String,      // clear | partly | cloud | rain | storm
    val isDay: Boolean,
    val days: List<Day>,
    val updatedAt: Long,
    val source: String
)

data class Place(val name: String, val lat: Double, val lon: Double)

object WeatherRepo {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun lastError(ctx: Context): String = sp(ctx).getString("error", "") ?: ""

    fun load(ctx: Context): Weather? {
        val raw = sp(ctx).getString("data", null) ?: return null
        return try {
            val o = JSONObject(raw)
            val arr = o.getJSONArray("days")
            val days = (0 until arr.length()).map {
                val d = arr.getJSONObject(it)
                Day(d.getString("date"), d.getDouble("hi"), d.getDouble("lo"), d.getString("kind"))
            }
            Weather(
                o.getDouble("temp"), o.getDouble("feels"), o.getInt("hum"), o.getDouble("wind"),
                o.getString("cond"), o.getString("kind"), o.getBoolean("isDay"), days,
                o.getLong("updatedAt"), o.getString("source")
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun store(ctx: Context, w: Weather, error: String) {
        val days = JSONArray()
        w.days.forEach {
            days.put(JSONObject().put("date", it.date).put("hi", it.hiC).put("lo", it.loC).put("kind", it.kind))
        }
        val o = JSONObject()
            .put("temp", w.tempC).put("feels", w.feelsC).put("hum", w.humidity).put("wind", w.windKmh)
            .put("cond", w.cond).put("kind", w.kind).put("isDay", w.isDay).put("days", days)
            .put("updatedAt", w.updatedAt).put("source", w.source)
        sp(ctx).edit().putString("data", o.toString()).putString("error", error).apply()
    }

    /**
     * Fetches fresh weather. Uses Google when a key is saved; if Google fails (or there is
     * no key) it falls back to Open-Meteo so the widget never goes blank. Call off the main thread.
     */
    fun refresh(ctx: Context): Boolean {
        val s = Prefs.load(ctx)
        var googleError = ""
        if (s.apiKey.isNotBlank()) {
            try {
                store(ctx, fromGoogle(s), "")
                return true
            } catch (e: Exception) {
                googleError = "Google Weather failed: " + (e.message ?: e.javaClass.simpleName)
            }
        }
        return try {
            store(ctx, fromOpenMeteo(s), googleError)
            true
        } catch (e: Exception) {
            val msg = "Could not load weather: " + (e.message ?: e.javaClass.simpleName)
            sp(ctx).edit().putString("error", if (googleError.isEmpty()) msg else "$googleError. $msg").apply()
            false
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 15000
        try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val reason = try {
                    JSONObject(body).getJSONObject("error").getString("message").take(140)
                } catch (e: Exception) {
                    ""
                }
                throw IOException("HTTP $code $reason".trim())
            }
            return body
        } finally {
            c.disconnect()
        }
    }

    // ---------------------------------------------------------------- Google Weather API

    private fun fromGoogle(s: Settings): Weather {
        val q = "key=" + URLEncoder.encode(s.apiKey.trim(), "UTF-8") +
            "&location.latitude=" + s.lat + "&location.longitude=" + s.lon
        val cur = JSONObject(get("https://weather.googleapis.com/v1/currentConditions:lookup?$q"))
        val wc = cur.optJSONObject("weatherCondition")
        val type = wc?.optString("type", "") ?: ""
        val text = wc?.optJSONObject("description")?.optString("text", "").orEmpty().ifEmpty { pretty(type) }
        val temp = cur.getJSONObject("temperature").getDouble("degrees")
        val feels = cur.optJSONObject("feelsLikeTemperature")?.optDouble("degrees", temp) ?: temp
        val wind = cur.optJSONObject("wind")?.optJSONObject("speed")?.optDouble("value", 0.0) ?: 0.0

        val fc = JSONObject(get("https://weather.googleapis.com/v1/forecast/days:lookup?$q&days=4&pageSize=4"))
        val arr = fc.optJSONArray("forecastDays") ?: JSONArray()
        val days = ArrayList<Day>()
        for (i in 0 until minOf(4, arr.length())) {
            val d = arr.getJSONObject(i)
            val dd = d.getJSONObject("displayDate")
            val date = String.format(Locale.US, "%04d-%02d-%02d", dd.getInt("year"), dd.getInt("month"), dd.getInt("day"))
            val dayType = d.optJSONObject("daytimeForecast")?.optJSONObject("weatherCondition")?.optString("type", "") ?: ""
            days.add(
                Day(
                    date,
                    d.getJSONObject("maxTemperature").getDouble("degrees"),
                    d.getJSONObject("minTemperature").getDouble("degrees"),
                    googleKind(dayType)
                )
            )
        }
        return Weather(
            temp, feels, cur.optInt("relativeHumidity", 0), wind, text, googleKind(type),
            cur.optBoolean("isDaytime", true), days, System.currentTimeMillis(), "Google Weather"
        )
    }

    private fun googleKind(type: String): String {
        val t = type.uppercase(Locale.US)
        return when {
            t.contains("THUNDER") -> "storm"
            t.contains("RAIN") || t.contains("SHOWER") || t.contains("SNOW") || t.contains("HAIL") || t.contains("SLEET") -> "rain"
            t.contains("PARTLY") || t == "MOSTLY_CLEAR" -> "partly"
            t.contains("CLOUDY") || t.contains("FOG") || t.contains("HAZE") || t.contains("WINDY") -> "cloud"
            else -> "clear"
        }
    }

    private fun pretty(type: String): String =
        type.lowercase(Locale.US).replace('_', ' ').replaceFirstChar { it.uppercase() }

    // ---------------------------------------------------------------- Open-Meteo (no key)

    private fun fromOpenMeteo(s: Settings): Weather {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=" + s.lat + "&longitude=" + s.lon +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=4"
        val o = JSONObject(get(url))
        val cur = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val days = (0 until minOf(4, dates.length())).map {
            Day(
                dates.getString(it),
                daily.getJSONArray("temperature_2m_max").getDouble(it),
                daily.getJSONArray("temperature_2m_min").getDouble(it),
                wmoKind(daily.getJSONArray("weather_code").getInt(it))
            )
        }
        val code = cur.getInt("weather_code")
        return Weather(
            cur.getDouble("temperature_2m"), cur.getDouble("apparent_temperature"),
            cur.getInt("relative_humidity_2m"), cur.getDouble("wind_speed_10m"),
            wmoText(code), wmoKind(code), cur.optInt("is_day", 1) == 1, days,
            System.currentTimeMillis(), "Open-Meteo"
        )
    }

    private fun wmoKind(code: Int): String = when (code) {
        0 -> "clear"
        1, 2 -> "partly"
        3, 45, 48 -> "cloud"
        in 95..99 -> "storm"
        else -> "rain"
    }

    private fun wmoText(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65, 66, 67 -> "Heavy rain"
        71, 73, 75, 77, 85, 86 -> "Snow"
        80, 81 -> "Showers"
        82 -> "Heavy showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Rain"
    }

    /** City search (Open-Meteo geocoding, no key). Returns null when nothing matches. */
    fun geocode(name: String): Place? {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=" +
            URLEncoder.encode(name.trim(), "UTF-8")
        val results = JSONObject(get(url)).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val r = results.getJSONObject(0)
        return Place(r.getString("name"), r.getDouble("latitude"), r.getDouble("longitude"))
    }
}
