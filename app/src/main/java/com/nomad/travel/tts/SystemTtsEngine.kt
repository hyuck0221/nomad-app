package com.nomad.travel.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Wraps Android's built-in [TextToSpeech]. Always available. */
class SystemTtsEngine(context: Context) : TtsEngine {

    override val id: String = ID

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null

    @Volatile
    private var ready: Boolean = false
    private var pending: Pair<String, String>? = null

    override var onCompletion: (() -> Unit)? = null
    override var onStart: (() -> Unit)? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { onStart?.invoke() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { onCompletion?.invoke() }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        onCompletion?.invoke()
                    }
                    override fun onDone(utteranceId: String?) { onCompletion?.invoke() }
                })
                pending?.let { (text, lang) ->
                    pending = null
                    speakInternal(text, lang, TextToSpeech.QUEUE_FLUSH)
                }
            }
        }
    }

    override fun isReady(): Boolean = ready

    override fun supportsLanguage(languageCode: String): Boolean {
        val engine = tts ?: return false
        if (!ready) return true // optimistic until init completes
        return runCatching {
            engine.isLanguageAvailable(resolveLocale(languageCode))
        }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
    }

    override fun speak(text: String, languageCode: String) {
        if (text.isBlank()) return
        if (!ready) {
            pending = text to languageCode
            return
        }
        speakInternal(text, languageCode, TextToSpeech.QUEUE_FLUSH)
    }

    fun speakQueued(text: String, languageCode: String) {
        if (text.isBlank()) return
        if (!ready) {
            pending = text to languageCode
            return
        }
        speakInternal(text, languageCode, TextToSpeech.QUEUE_ADD)
    }

    override fun stop() {
        pending = null
        tts?.stop()
    }

    override fun shutdown() {
        pending = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun speakInternal(text: String, languageCode: String, queueMode: Int) {
        val engine = tts ?: return
        val locale = resolveLocale(languageCode)
        val available = runCatching { engine.isLanguageAvailable(locale) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (available >= TextToSpeech.LANG_AVAILABLE) {
            engine.language = locale
        }
        val params = Bundle()
        engine.speak(
            text,
            queueMode,
            params,
            "nomad-system-tts-${System.currentTimeMillis()}"
        )
    }

    private fun resolveLocale(code: String): Locale = when (code) {
        "zh" -> Locale.SIMPLIFIED_CHINESE
        "ko" -> Locale.KOREAN
        "ja" -> Locale.JAPANESE
        "en" -> Locale.US
        else -> Locale.forLanguageTag(code)
    }

    companion object {
        const val ID = "system"
    }
}
