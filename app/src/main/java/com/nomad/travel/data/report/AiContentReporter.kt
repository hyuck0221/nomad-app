package com.nomad.travel.data.report

import android.content.Context
import com.nomad.travel.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

data class AiContentReport(
    val source: String,
    val contentId: String,
    val content: String,
    val reason: String
)

object AiContentReporter {
    private val client = OkHttpClient()

    suspend fun submit(context: Context, report: AiContentReport) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("source", report.source)
            .put("contentId", report.contentId)
            .put("content", report.content.take(MAX_CONTENT_LENGTH))
            .put("reason", report.reason.take(MAX_REASON_LENGTH))
            .put("locale", Locale.getDefault().toLanguageTag())
            .put("createdAt", System.currentTimeMillis())

        storeLocal(context, payload)
        uploadToSupabaseStorage(payload)
    }

    private fun storeLocal(context: Context, payload: JSONObject) {
        val dir = File(context.filesDir, "reports").apply { mkdirs() }
        File(dir, "ai_content_reports.jsonl").appendText(payload.toString() + "\n")
    }

    private fun uploadToSupabaseStorage(payload: JSONObject) {
        val supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/').takeIf { it.isNotBlank() } ?: return
        val anonKey = BuildConfig.SUPABASE_ANON_KEY.takeIf { it.isNotBlank() } ?: return
        val objectPath = "ai-content/${System.currentTimeMillis()}-${UUID.randomUUID()}.json"
        val uploadUrl = "$supabaseUrl/storage/v1/object/${BuildConfig.AI_REPORT_BUCKET}/$objectPath"
        runCatching {
            val request = Request.Builder()
                .url(uploadUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .header("x-upsert", "false")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().close()
        }
    }

    private const val MAX_CONTENT_LENGTH = 8_000
    private const val MAX_REASON_LENGTH = 1_000
}
