package com.nomad.travel.tts

import android.content.Context
import com.nomad.travel.data.UserPrefs
import com.nomad.travel.llm.ModelEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Routes [TtsEngine.speak] calls to the right backend based on the user's
 * preference and language support.
 *
 * Routing rule: use local mobile AI TTS only when the selected language has its own
 * downloaded model. Otherwise fall back to Android's system TTS.
 */
class TtsManager(
    context: Context,
    private val prefs: UserPrefs,
    val systemEngine: SystemTtsEngine = SystemTtsEngine(context),
    val meloEngine: MeloTtsEngine = MeloTtsEngine(context)
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferredEngineId = MutableStateFlow(SystemTtsEngine.ID)
    val preferredEngine: StateFlow<String> = preferredEngineId.asStateFlow()

    private var lastEngine: TtsEngine = systemEngine

    /** External callers (e.g. conversation mode) listen here for utterance completion. */
    var onSpeakComplete: (() -> Unit)? = null

    init {
        scope.launch {
            prefs.ttsEngine.collect { id ->
                preferredEngineId.value = when (id) {
                    MeloTtsEngine.ID,
                    MeloTtsEngine.LEGACY_KOKORO_ID -> MeloTtsEngine.ID
                    else -> SystemTtsEngine.ID
                }
            }
        }
        scope.launch {
            prefs.activeTtsModelId.collect { id ->
                meloEngine.setPreferredModel(id)
            }
        }
        // Wire engine completion → manager-level callback.
        val forward: () -> Unit = { onSpeakComplete?.invoke() }
        systemEngine.onCompletion = forward
        meloEngine.onCompletion = forward
    }

    fun speak(text: String, languageCode: String) {
        if (text.isBlank()) return
        val engine = pickEngine(languageCode)
        lastEngine = engine
        engine.speak(text, languageCode)
    }

    fun speakQueued(text: String, languageCode: String) {
        if (text.isBlank()) return
        val engine = pickEngine(languageCode)
        lastEngine = engine
        if (engine === systemEngine) {
            systemEngine.speakQueued(text, languageCode)
        } else if (engine === meloEngine) {
            meloEngine.speakQueued(text, languageCode)
        } else {
            engine.speak(text, languageCode)
        }
    }

    fun stop() {
        systemEngine.stop()
        meloEngine.stop()
    }

    fun shutdown() {
        systemEngine.shutdown()
        meloEngine.shutdown()
    }

    suspend fun setPreferredEngine(id: String) {
        prefs.setTtsEngine(id)
    }

    /** Filesystem location where the downloader should place [entry]. */
    fun fileFor(entry: ModelEntry): File = meloEngine.fileFor(entry)

    fun isModelDownloaded(entry: ModelEntry): Boolean =
        meloEngine.isModelDownloaded(entry)

    fun isModelUsable(entry: ModelEntry): Boolean =
        meloEngine.isModelUsable(entry)

    fun deleteModel(entry: ModelEntry): Boolean = meloEngine.delete(entry)

    fun isMeloReadyForLanguage(languageCode: String): Boolean =
        meloEngine.isReadyForLanguage(languageCode)

    private fun pickEngine(languageCode: String): TtsEngine {
        val pref = preferredEngineId.value
        if (pref == MeloTtsEngine.ID &&
            meloEngine.isReadyForLanguage(languageCode)
        ) {
            return meloEngine
        }
        return systemEngine
    }
}
