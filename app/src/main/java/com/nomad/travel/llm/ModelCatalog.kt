package com.nomad.travel.llm

import androidx.annotation.StringRes
import com.nomad.travel.R

/** Static catalog of on-device models supported by Nomad. */
data class ModelEntry(
    val id: String,
    /** Code-side fallback display name; used when [displayNameResId] is 0. */
    val displayName: String,
    @StringRes val displayNameResId: Int = 0,
    val shortName: String,
    val sizeBytes: Long,
    val url: String,
    val fileName: String,
    val companionFiles: List<ModelFile> = emptyList(),
    val archiveUrl: String? = null,
    val archiveRoot: String? = null,
    val recommended: Boolean,
    /** Code-side fallback tagline; used when [taglineResId] is 0. */
    val tagline: String,
    @StringRes val taglineResId: Int = 0,
    val badges: List<String>,
    /** Hard lower bound — below this the model must not be downloaded/loaded. 0 = no floor. */
    val minRamBytes: Long = 0L,
    /** Soft warning — below this the UI surfaces a performance advisory. 0 = no warning. */
    val warnRamBytes: Long = 0L
)

data class ModelFile(
    val url: String,
    val fileName: String,
    val sizeBytes: Long = 0L
)

object ModelCatalog {

    private const val GB = 1024L * 1024L * 1024L

    val gemma4E2B = ModelEntry(
        id = "gemma-4-e2b",
        displayName = "Gemma4 2B",
        displayNameResId = R.string.model_gemma_e2b_name,
        shortName = "기본형",
        sizeBytes = 2_580_000_000L,
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        fileName = "gemma-4-E2B-it.litertlm",
        recommended = true,
        tagline = "대부분의 기기에서 빠르고 안정적으로 작동해요",
        taglineResId = R.string.model_gemma_e2b_tagline,
        badges = listOf("추천", "2.4 GB", "빠름"),
        minRamBytes = 4L * GB,
        warnRamBytes = 6L * GB
    )

    val gemma4E4B = ModelEntry(
        id = "gemma-4-e4b",
        displayName = "Gemma4 4B",
        displayNameResId = R.string.model_gemma_e4b_name,
        shortName = "고급형",
        sizeBytes = 3_654_467_584L,
        url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        fileName = "gemma-4-E4B-it.litertlm",
        recommended = false,
        tagline = "더 섬세하고 정확한 답변을 원할 때 좋아요",
        taglineResId = R.string.model_gemma_e4b_tagline,
        badges = listOf("3.4 GB", "고성능"),
        minRamBytes = 8L * GB,
        warnRamBytes = 12L * GB
    )

    val all: List<ModelEntry> = listOf(gemma4E2B, gemma4E4B)

    val recommended: ModelEntry get() = all.first { it.recommended }

    fun byId(id: String?): ModelEntry? = all.firstOrNull { it.id == id }
}
