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
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileInputStream
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
        val urls = inputData.getStringArray(KEY_URLS)
            ?: arrayOf(inputData.getString(KEY_URL) ?: return Result.failure())
        val destPaths = inputData.getStringArray(KEY_DESTS)
            ?: arrayOf(inputData.getString(KEY_DEST) ?: return Result.failure())
        if (urls.size != destPaths.size) return Result.failure()

        setForeground(buildForegroundInfo(0, 0, PHASE_DOWNLOADING))

        return runCatching {
            var completedBytes = 0L
            var lastForegroundAt = 0L
            val expectedTotal = inputData.getLong(KEY_EXPECTED_TOTAL, 0L)
            suspend fun publishProgress(
                downloaded: Long,
                total: Long,
                phase: String,
                forceForeground: Boolean = false
            ) {
                setProgress(
                    workDataOf(
                        PROGRESS_DOWNLOADED to downloaded,
                        PROGRESS_TOTAL to total,
                        PROGRESS_PHASE to phase
                    )
                )
                val now = System.currentTimeMillis()
                if (forceForeground || now - lastForegroundAt > FOREGROUND_UPDATE_INTERVAL_MS) {
                    setForeground(buildForegroundInfo(downloaded, total, phase))
                    lastForegroundAt = now
                }
            }
            urls.zip(destPaths).forEach { (url, destPath) ->
                val dest = File(destPath)
                streamDownload(url, dest) { downloaded, total ->
                    val overallDone = completedBytes + downloaded
                    val overallTotal = if (expectedTotal > 0) expectedTotal else completedBytes + total
                    publishProgress(overallDone, overallTotal, PHASE_DOWNLOADING)
                }
                if (isStopped) error("download cancelled")
                completedBytes += dest.length()
            }
            val archiveRoot = inputData.getString(KEY_ARCHIVE_ROOT)
            val archiveDest = inputData.getString(KEY_ARCHIVE_DEST)?.let(::File)
            val archiveTargetDir = inputData.getString(KEY_ARCHIVE_TARGET_DIR)?.let(::File)
            if (archiveRoot != null && archiveDest != null && archiveTargetDir != null) {
                val extractTotal = completedBytes + archiveDest.length()
                publishProgress(completedBytes, extractTotal, PHASE_INSTALLING, forceForeground = true)
                extractTarBz2(archiveDest, archiveTargetDir, archiveRoot) { read ->
                    val done = (completedBytes + read).coerceAtMost(extractTotal)
                    publishProgress(done, extractTotal, PHASE_INSTALLING)
                }
                if (isStopped) error("download cancelled")
                archiveDest.delete()
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
                    val buf = ByteArray(IO_BUFFER_SIZE)
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

            if (isStopped) error("download cancelled")
            if (!part.renameTo(dest)) error("rename failed")
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun extractTarBz2(
        archive: File,
        targetDir: File,
        root: String,
        onProgress: suspend (Long) -> Unit
    ) {
        val canonicalTarget = targetDir.canonicalFile
        targetDir.mkdirs()
        CountingInputStream(FileInputStream(archive)).use { fileIn ->
            var lastEmit = 0L
            BZip2CompressorInputStream(fileIn, true).use { bzIn ->
                TarArchiveInputStream(bzIn).use { tarIn ->
                    while (true) {
                        if (isStopped) return
                        val entry = tarIn.nextEntry ?: break
                        if (!entry.name.startsWith("$root/")) continue
                        val relative = entry.name.removePrefix("$root/").trimStart('/')
                        if (relative.isBlank()) continue
                        val out = File(targetDir, relative).canonicalFile
                        if (!out.path.startsWith(canonicalTarget.path)) {
                            error("Unsafe archive path: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { output ->
                                val buffer = ByteArray(IO_BUFFER_SIZE)
                                while (!isStopped) {
                                    val n = tarIn.read(buffer)
                                    if (n <= 0) break
                                    output.write(buffer, 0, n)
                                    val now = System.currentTimeMillis()
                                    if (now - lastEmit > 500) {
                                        onProgress(fileIn.bytesRead)
                                        lastEmit = now
                                    }
                                }
                            }
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 500) {
                            onProgress(fileIn.bytesRead)
                            lastEmit = now
                        }
                    }
                }
            }
            onProgress(fileIn.bytesRead)
        }
    }

    private class CountingInputStream(
        private val delegate: FileInputStream
    ) : java.io.InputStream() {
        var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            val b = delegate.read()
            if (b >= 0) bytesRead++
            return b
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val n = delegate.read(buffer, offset, length)
            if (n > 0) bytesRead += n
            return n
        }

        override fun close() = delegate.close()
    }

    private fun buildForegroundInfo(downloaded: Long, total: Long, phase: String): ForegroundInfo {
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

        val title = if (phase == PHASE_INSTALLING) "모델 설치 중" else "모델 다운로드"

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
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
        const val KEY_URLS = "urls"
        const val KEY_DESTS = "dests"
        const val KEY_EXPECTED_TOTAL = "expected_total"
        const val KEY_ERROR = "error"
        const val KEY_ARCHIVE_ROOT = "archive_root"
        const val KEY_ARCHIVE_DEST = "archive_dest"
        const val KEY_ARCHIVE_TARGET_DIR = "archive_target_dir"

        const val PROGRESS_DOWNLOADED = "downloaded"
        const val PROGRESS_TOTAL = "total"
        const val PROGRESS_PHASE = "phase"
        const val PHASE_DOWNLOADING = "downloading"
        const val PHASE_INSTALLING = "installing"

        private const val IO_BUFFER_SIZE = 256 * 1024
        private const val FOREGROUND_UPDATE_INTERVAL_MS = 1_500L

        fun inputData(entry: ModelEntry, dest: File): Data {
            val baseDir = dest.parentFile ?: File("")
            val archiveUrl = entry.archiveUrl
            val archiveRoot = entry.archiveRoot
            val archiveDest = File(baseDir, "model.tar.bz2")
            val urls = if (archiveUrl != null && archiveRoot != null) {
                arrayOf(archiveUrl)
            } else {
                buildList {
                    add(entry.url)
                    entry.companionFiles.forEach { add(it.url) }
                }.toTypedArray()
            }
            val dests = if (archiveUrl != null && archiveRoot != null) {
                arrayOf(archiveDest.absolutePath)
            } else {
                buildList {
                    add(dest.absolutePath)
                    entry.companionFiles.forEach { add(File(baseDir, it.fileName).absolutePath) }
                }.toTypedArray()
            }
            val payloadTotal = entry.sizeBytes + entry.companionFiles.sumOf { it.sizeBytes }
            val total = if (archiveUrl != null && archiveRoot != null) {
                payloadTotal * 2
            } else {
                payloadTotal
            }
            val data = mutableMapOf<String, Any>(
                KEY_URLS to urls,
                KEY_DESTS to dests,
                KEY_EXPECTED_TOTAL to total
            )
            if (archiveUrl != null && archiveRoot != null) {
                data[KEY_ARCHIVE_ROOT] = archiveRoot
                data[KEY_ARCHIVE_DEST] = archiveDest.absolutePath
                data[KEY_ARCHIVE_TARGET_DIR] = baseDir.absolutePath
            }
            return workDataOf(*data.map { it.key to it.value }.toTypedArray())
        }
    }
}
