package com.epiclibrary.mobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.epiclibrary.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val releaseNotes: String
)

sealed class AppUpdateStatus {
    data object NotChecked : AppUpdateStatus()
    data object Checking : AppUpdateStatus()
    data object NotConfigured : AppUpdateStatus()
    data object UpToDate : AppUpdateStatus()
    data class Available(val update: AppUpdateInfo) : AppUpdateStatus()
    data class PermissionRequired(val update: AppUpdateInfo) : AppUpdateStatus()
    data class Downloading(val update: AppUpdateInfo) : AppUpdateStatus()
    data object InstallerOpened : AppUpdateStatus()
    data class Failed(val message: String) : AppUpdateStatus()
}

class AppUpdateManager(
    context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val appContext = context.applicationContext

    suspend fun checkForUpdate(): AppUpdateStatus = withContext(Dispatchers.IO) {
        val manifestUrl = BuildConfig.UPDATE_MANIFEST_URL.trim()
        if (manifestUrl.isBlank()) return@withContext AppUpdateStatus.NotConfigured
        if (Uri.parse(manifestUrl).scheme != "https") {
            return@withContext AppUpdateStatus.Failed("Güncelleme adresi güvenli HTTPS olmalı.")
        }

        try {
            val request = Request.Builder().url(manifestUrl).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Manifest unavailable")
                val body = response.body?.string() ?: throw IOException("Manifest empty")
                val json = JSONObject(body)
                val versionCode = json.optInt("versionCode", 0)
                val versionName = json.optString("versionName").trim()
                val apkUrl = json.optString("apkUrl").trim()
                val sha256 = json.optString("sha256").trim()
                val notes = json.optString("releaseNotes").trim()

                if (versionCode <= BuildConfig.VERSION_CODE) {
                    return@withContext AppUpdateStatus.UpToDate
                }
                if (versionName.isBlank() || Uri.parse(apkUrl).scheme != "https" ||
                    !sha256.matches(Regex("^[A-Fa-f0-9]{64}$"))
                ) {
                    throw IOException("Invalid update manifest")
                }

                AppUpdateStatus.Available(
                    AppUpdateInfo(versionCode, versionName, apkUrl, sha256.lowercase(), notes)
                )
            }
        } catch (_: Exception) {
            AppUpdateStatus.Failed("Güncelleme bilgisi alınamadı. Daha sonra tekrar dene.")
        }
    }

    suspend fun downloadUpdate(update: AppUpdateInfo): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(update.apkUrl).get().build()
        val updateDirectory = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updateDirectory, "epic-library-update.apk")

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Update download failed")
            val body = response.body ?: throw IOException("Update file is empty")
            body.byteStream().use { input ->
                apkFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        if (apkFile.sha256() != update.sha256) {
            apkFile.delete()
            throw IOException("Update integrity check failed")
        }
        apkFile
    }

    fun canRequestInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || appContext.packageManager.canRequestPackageInstalls()

    fun installPermissionSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))

    fun installerIntent(apkFile: File): Intent {
        val apkUri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
