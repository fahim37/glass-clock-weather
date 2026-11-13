package app.glassclock.widget

import android.content.Context

/** Everything the user can change. One set of settings is shared by all widgets. */
data class Settings(
    val bg: String = "liquid",        // liquid | glass | solid | clear
    val transparency: Int = 55,       // 0..100
    val dark: Boolean = true,
    val hue: Int = 217,               // accent colour hue
    val shape: String = "scallop",    // scallop | circle | squircle
    val seconds: Boolean = true,
    val fahrenheit: Boolean = false,
    val lat: Double = 23.8103,
    val lon: Double = 90.4125,
    val place: String = "Dhaka",
    val apiKey: String = "",          // Google Weather API key (optional)
    val useDevice: Boolean = true,    // follow the phone's precise location
    val detail: Boolean = false       // weather side shows the details view
)

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(ctx: Context): Settings {
        val p = sp(ctx)
        val d = Settings()
        return Settings(
            bg = p.getString("bg", d.bg) ?: d.bg,
            transparency = p.getInt("transparency", d.transparency),
            dark = p.getBoolean("dark", d.dark),
            hue = p.getInt("hue", d.hue),
            shape = p.getString("shape", d.shape) ?: d.shape,
            seconds = p.getBoolean("seconds", d.seconds),
            fahrenheit = p.getBoolean("fahrenheit", d.fahrenheit),
            lat = (p.getString("lat", null) ?: "").toDoubleOrNull() ?: d.lat,
            lon = (p.getString("lon", null) ?: "").toDoubleOrNull() ?: d.lon,
            place = p.getString("place", d.place) ?: d.place,
            apiKey = p.getString("apiKey", d.apiKey) ?: d.apiKey,
            useDevice = p.getBoolean("useDevice", d.useDevice),
            detail = p.getBoolean("detail", d.detail)
        )
    }

    fun save(ctx: Context, s: Settings) {
        sp(ctx).edit()
            .putString("bg", s.bg)
            .putInt("transparency", s.transparency)
            .putBoolean("dark", s.dark)
            .putInt("hue", s.hue)
            .putString("shape", s.shape)
            .putBoolean("seconds", s.seconds)
            .putBoolean("fahrenheit", s.fahrenheit)
            .putString("lat", s.lat.toString())
            .putString("lon", s.lon.toString())
            .putString("place", s.place)
            .putString("apiKey", s.apiKey)
            .putBoolean("useDevice", s.useDevice)
            .putBoolean("detail", s.detail)
            .apply()
    }

    /** Writes only the location, so it never overwrites a setting changed at the same moment. */
    fun saveLocation(ctx: Context, lat: Double, lon: Double, place: String) {
        sp(ctx).edit()
            .putString("lat", lat.toString())
            .putString("lon", lon.toString())
            .putString("place", place)
            .apply()
    }
}
