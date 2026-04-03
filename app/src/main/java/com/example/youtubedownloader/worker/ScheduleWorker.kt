package com.example.youtubedownloader.worker

import android.content.Context
import androidx.work.*
import com.example.youtubedownloader.*
import com.example.youtubedownloader.service.DownloadService
import java.util.*
import java.util.concurrent.TimeUnit

class ScheduleWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Check network
        if (!NetworkMonitor.canDownload(applicationContext)) {
            return Result.retry()
        }

        // Check if there are pending downloads — FIXED
        val pending = App.storage.getPendingQueue()
        if (pending.isEmpty()) return Result.success()

        // Start the download service
        DownloadService.start(applicationContext)

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "scheduled_download"

        fun schedule(context: Context) {
            if (!AppPrefs.isScheduleEnabled(context)) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                return
            }

            val hour = AppPrefs.getScheduleHour(context)
            val minute = AppPrefs.getScheduleMinute(context)

            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
            }

            val delay = target.timeInMillis - now.timeInMillis

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (AppPrefs.isScheduleWifiOnly(context))
                        NetworkType.UNMETERED
                    else NetworkType.CONNECTED
                )
                .build()

            val request = OneTimeWorkRequestBuilder<ScheduleWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }
    }
}