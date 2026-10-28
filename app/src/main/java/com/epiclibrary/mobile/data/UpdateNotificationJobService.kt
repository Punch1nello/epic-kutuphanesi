package com.epiclibrary.mobile.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.epiclibrary.mobile.BuildConfig
import com.epiclibrary.mobile.R
import com.epiclibrary.mobile.ui.AppPreferences
import com.epiclibrary.mobile.ui.AppStrings
import com.epiclibrary.mobile.ui.MainActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object UpdateNotificationScheduler {
    private const val JOB_ID = 4102
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val component = ComponentName(appContext, UpdateNotificationJobService::class.java)
        val scheduler = appContext.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_ID) != null) return

        val job = JobInfo.Builder(JOB_ID, component)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(DAY_MILLIS)
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }

    fun cancel(context: Context) {
        context.applicationContext.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
    }
}

class UpdateNotificationJobService : JobService() {
    private val httpClient by lazy {
        OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
    }

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                runCatching { checkForUpdateAndNotify() }
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    private fun checkForUpdateAndNotify() {
        val preferences = AppPreferences(applicationContext)
        if (!preferences.notificationsEnabled()) return
        if (!hasNotificationPermission()) return
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val manifestUrl = BuildConfig.UPDATE_MANIFEST_URL.trim()
        if (!manifestUrl.startsWith("https://", ignoreCase = true)) return

        val request = Request.Builder().url(manifestUrl).get().build()
        val manifest = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            JSONObject(response.body?.string().orEmpty())
        }
        val versionCode = manifest.optInt("versionCode", 0)
        val versionName = manifest.optString("versionName").trim()
        if (versionCode <= BuildConfig.VERSION_CODE || versionCode <= preferences.lastNotifiedUpdateVersionCode()) return
        if (versionName.isBlank()) return

        createUpdateChannel(preferences)
        val language = preferences.language()
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            versionCode,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(AppStrings.text(language, "notification_update_title"))
            .setContentText(AppStrings.text(language, "notification_update_text", versionName))
            .setStyle(NotificationCompat.BigTextStyle().bigText(AppStrings.text(language, "notification_update_text", versionName)))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            preferences.setLastNotifiedUpdateVersionCode(versionCode)
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createUpdateChannel(preferences: AppPreferences) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            AppStrings.text(preferences.language(), "notification_channel"),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "app_updates"
        const val NOTIFICATION_ID = 4103
    }
}

