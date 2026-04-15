package com.nomad.travel.llm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nomad.travel.R
import kotlinx.coroutines.flow.collect
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Background model downloader. Runs as a foreground service so the download
 * survives app backgrounding. Reports progress via Data so the UI can bind
 * to it via WorkManager.getWorkInfoByIdFlow().
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val destPath = inputData.getString(KEY_DEST) ?: return Result.failure()
        val dest = File(destPath)

        setForeground(buildForegroundInfo(0, 0))

        return runCatching {
            streamDownload(url, dest) { downloaded, total ->
                setProgress(workDataOf(PROGRESS_DOWNLOADED to downloaded, PROGRESS_TOTAL to total))
                setForeground(buildForegroundInfo(downloaded, total))
            }
            Result.success()
        }.getOrElse { e ->
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: e::class.simpleName)))
        }
    }

    private suspend fun streamDownload(
        url: String,
        dest: File,
        onProgress: suspend (Long, Long) -> Unit
    ) {
        val part = File(dest.parentFile, dest.name + ".part")
        dest.parentFile?.mkdirs()

        val startByte = if (part.exists()) part.length() else 0L

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 60_000
            if (startByte > 0) setRequestProperty("Range", "bytes=$startByte-")
        }

        try {
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")

            val contentLength = conn.contentLengthLong
            val resumed = startByte > 0 && code == 206
            val total = if (resumed) startByte + contentLength else contentLength

            conn.inputStream.use { input ->
                java.io.FileOutputStream(part, resumed).use { out ->
                    val buf = ByteArray(1 shl 16)
                    var downloaded = if (resumed) startByte else 0L
                    var lastEmit = 0L
                    while (!isStopped) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 500) {
                            onProgress(downloaded, total)
                            lastEmit = now
                        }
                    }
                    onProgress(downloaded, total)
                }
            }

            if (isStopped) return
            if (!part.renameTo(dest)) error("rename failed")
        } finally {
            conn.disconnect()
        }
    }

    private fun buildForegroundInfo(downloaded: Long, total: Long): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model download",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Downloading the on-device Gemma model" }
            nm.createNotificationChannel(channel)
        }

        val pct = if (total > 0) ((downloaded * 100) / total).toInt() else 0
        val mbDone = downloaded / (1024 * 1024)
        val mbTotal = total / (1024 * 1024)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Gemma 4 E2B 다운로드")
            .setContentText(if (total > 0) "$mbDone / $mbTotal MB ($pct%)" else "연결 중…")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, total <= 0)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val UNIQUE_NAME = "gemma_model_download"
        const val CHANNEL_ID = "model_download"
        const val NOTIFICATION_ID = 1001

        const val KEY_URL = "url"
        const val KEY_DEST = "dest"
        const val KEY_ERROR = "error"

        const val PROGRESS_DOWNLOADED = "downloaded"
        const val PROGRESS_TOTAL = "total"

        fun inputData(url: String, dest: File): Data =
            workDataOf(KEY_URL to url, KEY_DEST to dest.absolutePath)
    }
}
