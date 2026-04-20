package com.nomad.travel.ui.translate

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.nomad.travel.NomadApp
import com.nomad.travel.llm.GemmaEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/* ── Language catalogue ───────────────────────────────────── */

data class Language(
    val code: String,
    val nameEn: String,
    val nameNative: String,
    val flag: String
)

val SUPPORTED_LANGUAGES: List<Language> = listOf(
    Language("ko", "Korean", "한국어", "\uD83C\uDDF0\uD83C\uDDF7"),
    Language("en", "English", "English", "\uD83C\uDDFA\uD83C\uDDF8"),
    Language("ja", "Japanese", "日本語", "\uD83C\uDDEF\uD83C\uDDF5"),
    Language("zh", "Chinese", "中文", "\uD83C\uDDE8\uD83C\uDDF3"),
    Language("es", "Spanish", "Español", "\uD83C\uDDEA\uD83C\uDDF8"),
    Language("fr", "French", "Français", "\uD83C\uDDEB\uD83C\uDDF7"),
    Language("de", "German", "Deutsch", "\uD83C\uDDE9\uD83C\uDDEA"),
    Language("it", "Italian", "Italiano", "\uD83C\uDDEE\uD83C\uDDF9"),
    Language("pt", "Portuguese", "Português", "\uD83C\uDDF5\uD83C\uDDF9"),
    Language("ru", "Russian", "Русский", "\uD83C\uDDF7\uD83C\uDDFA"),
    Language("th", "Thai", "ไทย", "\uD83C\uDDF9\uD83C\uDDED"),
    Language("vi", "Vietnamese", "Tiếng Việt", "\uD83C\uDDFB\uD83C\uDDF3"),
    Language("id", "Indonesian", "Bahasa Indonesia", "\uD83C\uDDEE\uD83C\uDDE9"),
    Language("ms", "Malay", "Bahasa Melayu", "\uD83C\uDDF2\uD83C\uDDFE"),
    Language("ar", "Arabic", "العربية", "\uD83C\uDDF8\uD83C\uDDE6"),
    Language("hi", "Hindi", "हिन्दी", "\uD83C\uDDEE\uD83C\uDDF3"),
    Language("tr", "Turkish", "Türkçe", "\uD83C\uDDF9\uD83C\uDDF7"),
    Language("nl", "Dutch", "Nederlands", "\uD83C\uDDF3\uD83C\uDDF1"),
    Language("pl", "Polish", "Polski", "\uD83C\uDDF5\uD83C\uDDF1"),
    Language("sv", "Swedish", "Svenska", "\uD83C\uDDF8\uD83C\uDDEA"),
    Language("da", "Danish", "Dansk", "\uD83C\uDDE9\uD83C\uDDF0"),
    Language("fi", "Finnish", "Suomi", "\uD83C\uDDEB\uD83C\uDDEE"),
    Language("nb", "Norwegian", "Norsk", "\uD83C\uDDF3\uD83C\uDDF4"),
    Language("el", "Greek", "Ελληνικά", "\uD83C\uDDEC\uD83C\uDDF7"),
    Language("cs", "Czech", "Čeština", "\uD83C\uDDE8\uD83C\uDDFF"),
    Language("hu", "Hungarian", "Magyar", "\uD83C\uDDED\uD83C\uDDFA"),
    Language("ro", "Romanian", "Română", "\uD83C\uDDF7\uD83C\uDDF4"),
    Language("uk", "Ukrainian", "Українська", "\uD83C\uDDFA\uD83C\uDDE6"),
    Language("he", "Hebrew", "עברית", "\uD83C\uDDEE\uD83C\uDDF1"),
    Language("bn", "Bengali", "বাংলা", "\uD83C\uDDE7\uD83C\uDDE9"),
    Language("tl", "Filipino", "Filipino", "\uD83C\uDDF5\uD83C\uDDED"),
    Language("km", "Khmer", "ខ្មែរ", "\uD83C\uDDF0\uD83C\uDDED"),
    Language("my", "Burmese", "မြန်မာ", "\uD83C\uDDF2\uD83C\uDDF2"),
    Language("ne", "Nepali", "नेपाली", "\uD83C\uDDF3\uD83C\uDDF5"),
    Language("si", "Sinhala", "සිංහල", "\uD83C\uDDF1\uD83C\uDDF0"),
    Language("ta", "Tamil", "தமிழ்", "\uD83C\uDDEE\uD83C\uDDF3"),
    Language("sw", "Swahili", "Kiswahili", "\uD83C\uDDF0\uD83C\uDDEA"),
    Language("mn", "Mongolian", "Монгол", "\uD83C\uDDF2\uD83C\uDDF3"),
    Language("ka", "Georgian", "ქართული", "\uD83C\uDDEC\uD83C\uDDEA"),
    Language("uz", "Uzbek", "Oʻzbek", "\uD83C\uDDFA\uD83C\uDDFF")
)

fun languageByCode(code: String): Language =
    SUPPORTED_LANGUAGES.first { it.code == code }

/* ── State ────────────────────────────────────────────────── */

data class TranslateUiState(
    val sourceLanguage: Language = languageByCode("ko"),
    val targetLanguage: Language = languageByCode("en"),
    val sourceText: String = "",
    val translatedText: String = "",
    val pronunciation: String = "",
    val isTranslating: Boolean = false,
    val isListening: Boolean = false,
    val showSourcePicker: Boolean = false,
    val showTargetPicker: Boolean = false
)

/**
 * Split-screen interpret mode state.
 * Bottom half = my side (normal orientation): I type/speak, result shows on partner's side.
 * Top half = partner's side (upside-down): partner speaks via mic, result shows on my side.
 */
data class InterpretUiState(
    val myLanguage: Language = languageByCode("ko"),
    val theirLanguage: Language = languageByCode("en"),
    // My side (bottom): input area + display of partner→me translation
    val myInput: String = "",
    val myDisplayText: String = "",           // partner's words translated to my language
    val isMyAreaTranslating: Boolean = false,  // translating partner→me
    // Their side (top, upside-down): display of me→partner translation
    val theirDisplayText: String = "",         // my words translated to partner's language
    val isTheirAreaTranslating: Boolean = false, // translating me→partner
    // Mic state
    val isMyMicActive: Boolean = false,
    val isTheirMicActive: Boolean = false,
    // Pickers
    val showMyLanguagePicker: Boolean = false,
    val showTheirLanguagePicker: Boolean = false
)

/* ── ViewModel ────────────────────────────────────────────── */

class TranslateViewModel(
    private val gemma: GemmaEngine,
    private val app: Application
) : ViewModel() {

    /* ── Mode 1: Text translate ── */

    private val _translate = MutableStateFlow(TranslateUiState())
    val translate: StateFlow<TranslateUiState> = _translate.asStateFlow()

    private var translateJob: Job? = null

    fun setSourceLanguage(lang: Language) =
        _translate.update { it.copy(sourceLanguage = lang, showSourcePicker = false) }

    fun setTargetLanguage(lang: Language) =
        _translate.update { it.copy(targetLanguage = lang, showTargetPicker = false) }

    fun toggleSourcePicker() =
        _translate.update { it.copy(showSourcePicker = !it.showSourcePicker, showTargetPicker = false) }

    fun toggleTargetPicker() =
        _translate.update { it.copy(showTargetPicker = !it.showTargetPicker, showSourcePicker = false) }

    fun dismissPickers() =
        _translate.update { it.copy(showSourcePicker = false, showTargetPicker = false) }

    fun swapLanguages() = _translate.update {
        it.copy(
            sourceLanguage = it.targetLanguage,
            targetLanguage = it.sourceLanguage,
            sourceText = it.translatedText,
            translatedText = it.sourceText,
            pronunciation = ""
        )
    }

    fun updateSourceText(text: String) =
        _translate.update { it.copy(sourceText = text) }

    fun translateText() {
        val s = _translate.value
        if (s.sourceText.isBlank() || s.isTranslating) return
        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            _translate.update {
                it.copy(isTranslating = true, translatedText = "", pronunciation = "")
            }
            gemma.ensureLoaded()
            val sysPrompt = buildTranslateSystemPrompt(s.sourceLanguage, s.targetLanguage)
            gemma.generateStream(sysPrompt, s.sourceText).collect { cumulative ->
                _translate.update { it.copy(translatedText = cumulative) }
            }
            val finalText = _translate.value.translatedText
            _translate.update { it.copy(isTranslating = false) }

            val uiLang = Locale.getDefault().language
            if (finalText.isNotBlank() && needsPronunciation(s.targetLanguage.code, uiLang)) {
                val pron = runCatching {
                    gemma.generate(
                        systemInstruction = buildPronunciationSystemPrompt(s.targetLanguage, uiLang),
                        userMessage = finalText
                    ).trim()
                }.getOrDefault("")
                if (pron.isNotEmpty() && pron != finalText) {
                    _translate.update { it.copy(pronunciation = pron) }
                }
            }
        }
    }

    fun cancelTranslation() {
        translateJob?.cancel()
        _translate.update { it.copy(isTranslating = false) }
    }

    fun clearTranslate() =
        _translate.update { it.copy(sourceText = "", translatedText = "", pronunciation = "") }

    /** Pre-set languages from chat tool tag navigation */
    fun presetTranslateLanguages(srcCode: String, tgtCode: String) {
        val src = SUPPORTED_LANGUAGES.firstOrNull { it.code == srcCode } ?: return
        val tgt = SUPPORTED_LANGUAGES.firstOrNull { it.code == tgtCode } ?: return
        _translate.update { it.copy(sourceLanguage = src, targetLanguage = tgt) }
    }

    fun presetInterpretLanguages(srcCode: String, tgtCode: String) {
        val src = SUPPORTED_LANGUAGES.firstOrNull { it.code == srcCode } ?: return
        val tgt = SUPPORTED_LANGUAGES.firstOrNull { it.code == tgtCode } ?: return
        _interpret.update { it.copy(myLanguage = src, theirLanguage = tgt) }
    }

    /* ── Mode 2: Interpret (split-screen) ── */

    private val _interpret = MutableStateFlow(InterpretUiState())
    val interpret: StateFlow<InterpretUiState> = _interpret.asStateFlow()

    private var interpretJob: Job? = null

    fun setMyLanguage(lang: Language) =
        _interpret.update { it.copy(myLanguage = lang, showMyLanguagePicker = false) }

    fun setTheirLanguage(lang: Language) =
        _interpret.update { it.copy(theirLanguage = lang, showTheirLanguagePicker = false) }

    fun toggleMyLanguagePicker() =
        _interpret.update { it.copy(showMyLanguagePicker = !it.showMyLanguagePicker, showTheirLanguagePicker = false) }

    fun toggleTheirLanguagePicker() =
        _interpret.update { it.copy(showTheirLanguagePicker = !it.showTheirLanguagePicker, showMyLanguagePicker = false) }

    fun dismissInterpretPickers() =
        _interpret.update { it.copy(showMyLanguagePicker = false, showTheirLanguagePicker = false) }

    fun swapInterpretLanguages() = _interpret.update {
        it.copy(myLanguage = it.theirLanguage, theirLanguage = it.myLanguage)
    }

    fun updateInterpretInput(text: String) =
        _interpret.update { it.copy(myInput = text) }

    /** I typed/spoke → show my text on my side, translate to partner's language on their side */
    fun sendMyMessage() {
        val s = _interpret.value
        if (s.myInput.isBlank() || s.isTheirAreaTranslating) return
        val text = s.myInput.trim()

        _interpret.update {
            it.copy(
                myInput = "",
                myDisplayText = text,
                theirDisplayText = "",
                isTheirAreaTranslating = true
            )
        }

        interpretJob = viewModelScope.launch {
            gemma.ensureLoaded()
            val sysPrompt = buildTranslateSystemPrompt(s.myLanguage, s.theirLanguage)
            gemma.generateStream(sysPrompt, text).collect { cumulative ->
                _interpret.update { it.copy(theirDisplayText = cumulative) }
            }
            _interpret.update { it.copy(isTheirAreaTranslating = false) }
        }
    }

    /** Partner spoke via mic → show their text on their side, translate to my language on my side */
    fun sendPartnerMessage(text: String) {
        if (text.isBlank()) return
        val s = _interpret.value

        _interpret.update {
            it.copy(
                theirDisplayText = text,
                myDisplayText = "",
                isMyAreaTranslating = true
            )
        }

        interpretJob = viewModelScope.launch {
            gemma.ensureLoaded()
            val sysPrompt = buildTranslateSystemPrompt(s.theirLanguage, s.myLanguage)
            gemma.generateStream(sysPrompt, text).collect { cumulative ->
                _interpret.update { it.copy(myDisplayText = cumulative) }
            }
            _interpret.update { it.copy(isMyAreaTranslating = false) }
        }
    }

    fun clearInterpret() =
        _interpret.update { it.copy(myDisplayText = "", theirDisplayText = "", myInput = "") }

    /* ── STT (shared) ── */

    /** Which side initiated listening */
    enum class SttTarget { TRANSLATE, INTERPRET_ME, INTERPRET_PARTNER }

    private var speechRecognizer: SpeechRecognizer? = null
    private var currentSttTarget: SttTarget? = null

    fun startListening(target: SttTarget) {
        if (!SpeechRecognizer.isRecognitionAvailable(app)) return

        val langCode = when (target) {
            SttTarget.TRANSLATE -> _translate.value.sourceLanguage.code
            SttTarget.INTERPRET_ME -> _interpret.value.myLanguage.code
            SttTarget.INTERPRET_PARTNER -> _interpret.value.theirLanguage.code
        }

        currentSttTarget = target
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(app)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                when (target) {
                    SttTarget.TRANSLATE -> _translate.update { it.copy(isListening = true) }
                    SttTarget.INTERPRET_ME -> _interpret.update { it.copy(isMyMicActive = true) }
                    SttTarget.INTERPRET_PARTNER -> _interpret.update { it.copy(isTheirMicActive = true) }
                }
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                when (target) {
                    SttTarget.TRANSLATE -> _translate.update { it.copy(isListening = false) }
                    SttTarget.INTERPRET_ME -> _interpret.update { it.copy(isMyMicActive = false) }
                    SttTarget.INTERPRET_PARTNER -> _interpret.update { it.copy(isTheirMicActive = false) }
                }
            }
            override fun onError(error: Int) {
                when (target) {
                    SttTarget.TRANSLATE -> _translate.update { it.copy(isListening = false) }
                    SttTarget.INTERPRET_ME -> _interpret.update { it.copy(isMyMicActive = false) }
                    SttTarget.INTERPRET_PARTNER -> _interpret.update { it.copy(isTheirMicActive = false) }
                }
            }
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                when (target) {
                    SttTarget.TRANSLATE -> _translate.update { it.copy(sourceText = text) }
                    SttTarget.INTERPRET_ME -> _interpret.update { it.copy(myInput = text) }
                    SttTarget.INTERPRET_PARTNER -> sendPartnerMessage(text)
                }
            }
            override fun onPartialResults(partial: Bundle?) {
                val text = partial
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                when (target) {
                    SttTarget.TRANSLATE -> _translate.update { it.copy(sourceText = text) }
                    SttTarget.INTERPRET_ME -> _interpret.update { it.copy(myInput = text) }
                    SttTarget.INTERPRET_PARTNER -> {} // only final for partner
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        currentSttTarget = null
        _translate.update { it.copy(isListening = false) }
        _interpret.update { it.copy(isMyMicActive = false, isTheirMicActive = false) }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        translateJob?.cancel()
        interpretJob?.cancel()
    }

    /* ── Prompt ── */

    private fun buildTranslateSystemPrompt(source: Language, target: Language): String =
        """You are a professional translator. Translate the user's text from ${source.nameEn} to ${target.nameEn}.
Rules:
- Output ONLY the translated text, nothing else.
- Preserve the original formatting, line breaks, and punctuation style.
- Do not add explanations, notes, or alternatives.
- If the text contains proper nouns, transliterate them naturally for the target language.
- Maintain the tone and register of the original text."""

    /** Only ask for pronunciation when target and UI scripts are different enough to help the reader. */
    private fun needsPronunciation(targetCode: String, uiCode: String): Boolean {
        if (targetCode == uiCode) return false
        // Same-script clusters — no transliteration needed between these pairs.
        val latin = setOf(
            "en", "es", "fr", "de", "it", "pt", "nl", "pl", "sv", "da", "fi",
            "nb", "ro", "hu", "cs", "tr", "vi", "id", "ms", "tl", "sw", "uz"
        )
        if (targetCode in latin && uiCode in latin) return false
        return true
    }

    private fun uiLangName(uiCode: String): String = SUPPORTED_LANGUAGES
        .firstOrNull { it.code == uiCode }?.nameEn ?: "English"

    private fun buildPronunciationSystemPrompt(target: Language, uiCode: String): String {
        val uiLangEn = uiLangName(uiCode)
        return """You are a pronunciation helper. Rewrite the given ${target.nameEn} text phonetically using ${uiLangEn} script, the way a ${uiLangEn} speaker would naturally read it aloud.
Rules:
- Output ONLY the phonetic transcription, nothing else.
- No explanations, no quotes, no translations, no parentheses.
- Keep it short, on a single line when possible.
- Use standard ${uiLangEn} characters; do not invent symbols."""
    }

    /* ── Factory ── */

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NomadApp
                return TranslateViewModel(
                    gemma = app.container.gemma,
                    app = app
                ) as T
            }
        }
    }
}
