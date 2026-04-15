package com.nomad.travel.llm

/** Static catalog of on-device models supported by Nomad. */
data class ModelEntry(
    val id: String,
    val displayName: String,
    val shortName: String,
    val sizeBytes: Long,
    val url: String,
    val fileName: String,
    val recommended: Boolean,
    val tagline: String,
    val badges: List<String>,
    /** Hard lower bound — below this the model must not be downloaded/loaded. 0 = no floor. */
    val minRamBytes: Long = 0L,
    /** Soft warning — below this the UI surfaces a performance advisory. 0 = no warning. */
    val warnRamBytes: Long = 0L
)

object ModelCatalog {

    private const val GB = 1024L * 1024L * 1024L

    val gemma4E2B = ModelEntry(
        id = "gemma-4-e2b",
        displayName = "Gemma 4 E2B-it",
        shortName = "Gemma 4 · 2B",
        sizeBytes = 2_580_000_000L,
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        fileName = "gemma-4-E2B-it.litertlm",
        recommended = true,
        tagline = "모든 기기 권장 · 빠른 응답 · 균형잡힌 품질",
        badges = listOf("추천", "2.4 GB", "저전력"),
        minRamBytes = 4L * GB,
        warnRamBytes = 6L * GB
    )

    val gemma4E4B = ModelEntry(
        id = "gemma-4-e4b",
        displayName = "Gemma 4 E4B-it",
        shortName = "Gemma 4 · 4B",
        sizeBytes = 3_654_467_584L,
        url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        fileName = "gemma-4-E4B-it.litertlm",
        recommended = false,
        tagline = "고사양 기기 · 더 나은 추론 품질 · 긴 맥락에 강함",
        badges = listOf("3.4 GB", "고성능"),
        minRamBytes = 8L * GB,
        warnRamBytes = 12L * GB
    )

    val all: List<ModelEntry> = listOf(gemma4E2B, gemma4E4B)

    val recommended: ModelEntry get() = all.first { it.recommended }

    fun byId(id: String?): ModelEntry? = all.firstOrNull { it.id == id }
}
