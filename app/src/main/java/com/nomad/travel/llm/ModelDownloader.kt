package com.nomad.travel.llm

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data class Progress(val downloaded: Long, val total: Long) : DownloadStatus {
        val fraction: Float get() = if (total > 0) downloaded.toFloat() / total else 0f
    }
    data object Done : DownloadStatus
    data class Failed(val message: String) : DownloadStatus
    data object Cancelled : DownloadStatus
}

/**
 * WorkManager facade for downloading model files. Uses one unique work per
 * model id so multiple downloads could in theory run, though the UI only
 * exposes one at a time.
 */
class ModelDownloader(context: Context) {

    private val workManager = WorkManager.getInstance(context)

    fun start(entry: ModelEntry, dest: File) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(DownloadWorker.inputData(entry.url, dest))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            workName(entry),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(entry: ModelEntry) {
        workManager.cancelUniqueWork(workName(entry))
    }

    fun status(entry: ModelEntry): Flow<DownloadStatus> =
        workManager.getWorkInfosForUniqueWorkFlow(workName(entry)).map { infos ->
            val info = infos.firstOrNull() ?: return@map DownloadStatus.Idle
            when (info.state) {
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.BLOCKED -> DownloadStatus.Progress(0, 0)
                WorkInfo.State.RUNNING -> {
                    val downloaded = info.progress.getLong(DownloadWorker.PROGRESS_DOWNLOADED, 0)
                    val total = info.progress.getLong(DownloadWorker.PROGRESS_TOTAL, 0)
                    DownloadStatus.Progress(downloaded, total)
                }
                WorkInfo.State.SUCCEEDED -> DownloadStatus.Done
                WorkInfo.State.FAILED -> {
                    val msg = info.outputData.getString(DownloadWorker.KEY_ERROR) ?: "unknown"
                    DownloadStatus.Failed(msg)
                }
                WorkInfo.State.CANCELLED -> DownloadStatus.Cancelled
            }
        }

    private fun workName(entry: ModelEntry): String = "download_${entry.id}"
}
