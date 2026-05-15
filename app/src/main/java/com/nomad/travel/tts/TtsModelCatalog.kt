package com.nomad.travel.tts

import com.nomad.travel.R
import com.nomad.travel.llm.ModelEntry
import com.nomad.travel.llm.ModelFile

/** Downloadable TTS voice models. Separate from the LLM [com.nomad.travel.llm.ModelCatalog]. */
object TtsModelCatalog {

    private const val SUPERTONIC_ARCHIVE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2"
    private const val SUPERTONIC_ARCHIVE_ROOT = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11"
    private val SUPERTONIC_LANGUAGE_CODES = setOf("ko", "en", "ja", "zh")
    val voicePresets: List<String> = listOf("F1", "F2", "F3", "F4", "F5", "M1", "M2", "M3", "M4", "M5")
    const val DEFAULT_VOICE_PRESET = "M1"

    val supertonic3: ModelEntry = ModelEntry(
        id = "tts-supertonic-3",
        displayName = "Supertonic 3 Multilingual Voice",
        displayNameResId = R.string.model_tts_supertonic3_name,
        shortName = "Supertonic 3",
        sizeBytes = 3_700_147L,
        url = SUPERTONIC_ARCHIVE,
        fileName = "tts-supertonic-3/duration_predictor.int8.onnx",
        companionFiles = listOf(
            ModelFile(SUPERTONIC_ARCHIVE, "text_encoder.int8.onnx", 36_416_150L),
            ModelFile(SUPERTONIC_ARCHIVE, "vector_estimator.int8.onnx", 78_400_833L),
            ModelFile(SUPERTONIC_ARCHIVE, "vocoder.int8.onnx", 25_991_073L),
            ModelFile(SUPERTONIC_ARCHIVE, "tts.json", 8_253L),
            ModelFile(SUPERTONIC_ARCHIVE, "unicode_indexer.bin", 262_144L),
            ModelFile(SUPERTONIC_ARCHIVE, "voice.bin", 517_168L)
        ),
        archiveUrl = SUPERTONIC_ARCHIVE,
        archiveRoot = SUPERTONIC_ARCHIVE_ROOT,
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

    fun normalizeVoicePreset(preset: String?): String =
        preset?.uppercase()?.takeIf { it in voicePresets } ?: DEFAULT_VOICE_PRESET
}
