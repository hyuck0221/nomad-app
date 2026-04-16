package com.nomad.travel.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(
    private val context: Context,
    val checker: UpdateChecker
) {

    suspend fun downloadApk(
        url: String,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val apkDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(apkDir, "update.apk")
            if (outFile.exists()) outFile.delete()

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "nomad-app")
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
                conn.inputStream.use { input ->
                    outFile.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            downloaded += n
                            if (total > 0) {
                                onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                outFile
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    fun canInstallFromUnknownSources(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }
}
