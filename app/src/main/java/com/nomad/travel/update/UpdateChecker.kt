package com.nomad.travel.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UpdateChecker(private val repo: String) {

    data class LatestRelease(
        val tag: String,
        val versionName: String,
        val apkUrl: String?,
        val notes: String?
    )

    suspend fun fetchLatest(): LatestRelease? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/$repo/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "nomad-app")
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val j = JSONObject(body)
                val tag = j.optString("tag_name")
                    .takeIf { it.isNotBlank() } ?: return@runCatching null
                val notes = j.optString("body").takeIf { it.isNotBlank() }
                val assets = j.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                            apkUrl = a.optString("browser_download_url")
                                .takeIf { it.isNotBlank() }
                            break
                        }
                    }
                }
                LatestRelease(
                    tag = tag,
                    versionName = tag.removePrefix("v").removePrefix("V"),
                    apkUrl = apkUrl,
                    notes = notes
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    fun isNewer(latest: String, current: String): Boolean {
        val l = semver(latest)
        val c = semver(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun semver(v: String): List<Int> =
        v.removePrefix("v").removePrefix("V")
            .split('.', '-', '+')
            .mapNotNull { it.toIntOrNull() }
}
