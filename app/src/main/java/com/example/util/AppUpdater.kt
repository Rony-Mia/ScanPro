package com.example.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer build than the one currently installed,
 * and downloads + installs it — this is what makes "check for updates"
 * inside the app actually work, instead of the user having to remember to
 * go back to GitHub, find the Actions run, download the artifact zip and
 * manually reinstall every time.
 *
 * Relies on the CI workflow (.github/workflows/build_apk.yml) publishing a
 * GitHub Release tagged "v<versionCode>" with the APK attached on every
 * push to main, and on every build being signed with the same committed
 * debug.keystore so installs land as an update, not a conflicting package.
 */
object AppUpdater {

    private const val REPO = Constants.GITHUB_REPO
    private const val LATEST_RELEASE_API = Constants.GITHUB_LATEST_API

    /**
     * Dynamically queries the latest GitHub release to find the real APK download URL,
     * falling back to the standard latest download URL constant.
     */
    suspend fun getLatestReleaseApkDownloadUrl(): String = withContext(Dispatchers.IO) {
        try {
            val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            val url = asset.optString("browser_download_url", "")
                            if (url.isNotBlank()) return@withContext url
                        }
                    }
                }
            }
            connection.disconnect()
        } catch (_: Exception) {
            // Network failure / rate limit / offline - fall back to reliable constant
        }
        Constants.LATEST_APK_DOWNLOAD_URL
    }

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String,
        val releaseNotes: String
    )

    /** Returns update info if a newer build is available on GitHub, else null. */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return@withContext null
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "")
            val remoteVersionCode = tagName.removePrefix("v").toIntOrNull() ?: return@withContext null

            if (remoteVersionCode <= BuildConfig.VERSION_CODE) {
                return@withContext null
            }

            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url", null)
                    break
                }
            }
            val downloadUrl = apkUrl ?: return@withContext null

            UpdateInfo(
                versionCode = remoteVersionCode,
                versionName = json.optString("name", tagName),
                downloadUrl = downloadUrl,
                releaseNotes = json.optString("body", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Downloads the update APK via the system DownloadManager (so it shows
     * real progress in the notification shade and survives the app being
     * backgrounded), then prompts the user to install it once done.
     */
    fun downloadAndInstall(context: Context, update: UpdateInfo) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val fileName = "ScanPro_${update.versionName}.apk"

        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("ScanPro update")
            .setDescription("Downloading ${update.versionName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)

        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (completedId == downloadId) {
                    context.unregisterReceiver(this)
                    val downloadedFile = File(
                        context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),
                        fileName
                    )
                    if (downloadedFile.exists()) {
                        installApk(context, downloadedFile)
                    }
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
