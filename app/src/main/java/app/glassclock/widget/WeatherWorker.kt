package app.glassclock.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Refreshes the weather every 15 minutes (the shortest interval Android allows). */
class WeatherWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        WeatherRepo.refresh(applicationContext)
        ClockWeatherWidget.updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "weather"
        private const val ONCE = "weather-now"

        private fun online(): Constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequest.Builder(WeatherWorker::class.java, 15, TimeUnit.MINUTES)
                .setConstraints(online())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun refreshNow(ctx: Context) {
            val req = OneTimeWorkRequest.Builder(WeatherWorker::class.java)
                .setConstraints(online())
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, req)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
        }
    }
}
