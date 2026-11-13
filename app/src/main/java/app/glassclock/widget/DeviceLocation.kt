package app.glassclock.widget

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Reads the phone's own location. Every call here blocks, so never call it on the main thread. */
object DeviceLocation {

    private fun granted(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun hasPrecise(ctx: Context) = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasAny(ctx: Context) = hasPrecise(ctx) || granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
    fun hasBackground(ctx: Context) = granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private val executor = Executors.newSingleThreadExecutor()

    @SuppressLint("MissingPermission")
    fun current(ctx: Context, timeoutMs: Long = 20_000): Location? {
        if (!hasAny(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { p -> try { lm.isProviderEnabled(p) } catch (e: Exception) { false } }

        val last = providers.mapNotNull { p ->
            try { lm.getLastKnownLocation(p) } catch (e: Exception) { null }
        }.maxByOrNull { it.time }
        val fresh = last != null && System.currentTimeMillis() - last.time < 10 * 60_000L && last.accuracy < 150f
        if (fresh) return last

        for (p in providers) {
            val latch = CountDownLatch(1)
            var result: Location? = null
            val signal = CancellationSignal()
            try {
                lm.getCurrentLocation(p, signal, executor) { loc ->
                    result = loc
                    latch.countDown()
                }
            } catch (e: Exception) {
                continue
            }
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) signal.cancel()
            result?.let { return it }
        }
        return last
    }

    /** Area name such as "Mohakhali", or null when the phone cannot look it up. */
    @Suppress("DEPRECATION")
    fun placeName(ctx: Context, lat: Double, lon: Double): String? = try {
        if (!Geocoder.isPresent()) null
        else Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()?.let {
            it.subLocality ?: it.locality ?: it.subAdminArea ?: it.adminArea
        }
    } catch (e: Exception) {
        null
    }
}
