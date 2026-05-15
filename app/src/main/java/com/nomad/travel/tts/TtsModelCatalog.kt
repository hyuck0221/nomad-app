package com.nomad.travel.tts

import com.nomad.travel.R
import com.nomad.travel.llm.ModelEntry
import com.nomad.travel.llm.ModelFile

/** Downloadable TTS voice models. Separate from the LLM [com.nomad.travel.llm.ModelCatalog]. */
object TtsModelCatalog {

    private const val SUPERTONIC_BASE = "https://huggingface.co/Supertone/supertonic-3/resolve/main"
    private val SUPERTONIC_LANGUAGE_CODES = setOf("ko", "en", "ja", "zh")

    val supertonic3: ModelEntry = ModelEntry(
        id = "tts-supertonic-3",
        displayName = "Supertonic 3 Multilingual Voice",
        displayNameResId = R.string.model_tts_supertonic3_name,
        shortName = "Supertonic 3",
        sizeBytes = 3_700_147L,
        url = "$SUPERTONIC_BASE/onnx/duration_predictor.onnx",
        fileName = "tts-supertonic-3/onnx/duration_predictor.onnx",
        companionFiles = listOf(
            ModelFile("$SUPERTONIC_BASE/onnx/text_encoder.onnx", "text_encoder.onnx", 36_416_150L),
            ModelFile("$SUPERTONIC_BASE/onnx/vector_estimator.onnx", "vector_estimator.onnx", 256_534_781L),
            ModelFile("$SUPERTONIC_BASE/onnx/vocoder.onnx", "vocoder.onnx", 101_424_195L),
            ModelFile("$SUPERTONIC_BASE/onnx/tts.json", "tts.json", 8_253L),
            ModelFile("$SUPERTONIC_BASE/onnx/unicode_indexer.json", "unicode_indexer.json", 277_676L),
            ModelFile("$SUPERTONIC_BASE/voice_styles/M1.json", "../voice_styles/M1.json", 291_748L)
        ),
        recommended = false,
        tagline = "Lightweight local multilingual TTS for Korean, English, Japanese, and Chinese fallback",
        taglineResId = R.string.model_tts_supertonic3_tagline,
        badges = listOf("TTS", "Supertonic 3", "KO", "EN", "JA", "ZH"),
        minRamBytes = 0L,
        warnRamBytes = 0L
    )

    val all: List<ModelEntry> = listOf(supertonic3)

    fun forLanguage(languageCode: String?): ModelEntry? =
        languageCode
            ?.lowercase()
            ?.takeIf { it in SUPERTONIC_LANGUAGE_CODES }
            ?.let { supertonic3 }

    fun byId(id: String?): ModelEntry? = all.firstOrNull { it.id == id }

    fun languageFor(entry: ModelEntry): String? = null

    fun supportsLanguage(entry: ModelEntry, languageCode: String): Boolean =
        isSupertonic(entry) && languageCode.lowercase() in SUPERTONIC_LANGUAGE_CODES

    fun isSupertonic(entry: ModelEntry): Boolean = entry.id == supertonic3.id
}
