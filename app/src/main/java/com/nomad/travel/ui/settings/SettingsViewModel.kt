package com.nomad.travel.ui.settings

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.nomad.travel.BuildConfig
import com.nomad.travel.NomadApp
import com.nomad.travel.data.UserPrefs
import com.nomad.travel.data.chat.ChatRepository
import com.nomad.travel.llm.DeviceCapability
import com.nomad.travel.llm.DownloadStatus
import com.nomad.travel.llm.GemmaEngine
import com.nomad.travel.llm.ModelCatalog
import com.nomad.travel.llm.ModelDownloader
import com.nomad.travel.llm.ModelEntry
import com.nomad.travel.tools.ContextStrategy
import com.nomad.travel.tts.SystemTtsEngine
import com.nomad.travel.tts.TtsManager
import com.nomad.travel.tts.TtsModelCatalog
import com.nomad.travel.tts.MeloTtsEngine
import com.nomad.travel.ui.setup.ModelRow
import com.nomad.travel.update.UpdateManager
import com.nomad.travel.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val language: String = "ko",
    val systemPrompt: String = "",
    val activeModelId: String = ModelCatalog.recommended.id,
    val modelRows: List<ModelRow> = emptyList(),
    val contextStrategy: ContextStrategy = ContextStrategy.DROP_OLDEST,
    val updateState: UpdateState = UpdateState.Idle,
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val autoUpdateCheck: Boolean = true,
    val cameraInstantPreview: Boolean = false,
    val ttsEngineId: String = SystemTtsEngine.ID,
    val activeTtsModelId: String? = null,
    val ttsModelRows: List<ModelRow> = emptyList(),
    val ttsVoicePreset: String = TtsModelCatalog.DEFAULT_VOICE_PRESET,
    val voiceLoopEnabled: Boolean = true
)

class SettingsViewModel(
    private val prefs: UserPrefs,
    private val gemma: GemmaEngine,
    private val downloader: ModelDownloader,
    private val chatRepo: ChatRepository,
    private val device: DeviceCapability,
    private val updateManager: UpdateManager,
    private val tts: TtsManager
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0)

    private val statusesFlow = combine(
        ModelCatalog.all.map { downloader.status(it) }
    ) { arr -> arr.toList() }

    private val ttsStatusesFlow = combine(
        TtsModelCatalog.all.map { downloader.status(it) }
    ) { arr -> arr.toList() }

    private val basePrefsFlow = combine(
        prefs.language,
        prefs.systemPrompt,
        prefs.activeModelId,
        prefs.contextStrategy,
        prefs.cameraInstantPreview
    ) { lang, prompt, activeId, strategy, cameraInstant ->
        listOf(lang, prompt, activeId, strategy, cameraInstant)
    }

    private val ttsPrefsFlow = combine(
        prefs.ttsEngine,
        prefs.activeTtsModelId,
        prefs.ttsVoicePreset,
        prefs.voiceLoopEnabled,
        prefs.autoUpdateCheck
    ) { engine, activeTtsModelId, voicePreset, loop, auto ->
        listOf(engine, activeTtsModelId, voicePreset, loop, auto)
    }

    private val statusBundleFlow = combine(
        statusesFlow,
        ttsStatusesFlow
    ) { llm, tts -> llm to tts }

    val state: StateFlow<SettingsUiState> = combine(
        basePrefsFlow,
        ttsPrefsFlow,
        statusBundleFlow,
        refreshTick,
        updateManager.state
    ) { base, ttsPrefs, statusBundle, _, uState ->
        val lang = base[0] as String?
        val prompt = base[1] as String?
        val activeId = base[2] as String?
        val strategy = base[3] as String?
        val cameraInstant = base[4] as Boolean
        val ttsEngineId = ttsPrefs[0] as String?
        val activeTtsModelId = ttsPrefs[1] as String?
        val voicePreset = ttsPrefs[2] as String?
        val voiceLoop = ttsPrefs[3] as Boolean
        val autoUpdate = ttsPrefs[4] as Boolean
        val (llmStatuses, ttsStatuses) = statusBundle

        val rows = ModelCatalog.all.mapIndexed { i, entry ->
            ModelRow(
                entry = entry,
                downloaded = gemma.isDownloaded(entry),
                status = llmStatuses.getOrNull(i) ?: DownloadStatus.Idle,
                ramEligible = device.isEligible(entry),
                ramWarning = device.shouldWarn(entry)
            )
        }
        val ttsRows = TtsModelCatalog.all.mapIndexed { i, entry ->
            ModelRow(
                entry = entry,
                downloaded = tts.isModelDownloaded(entry),
                status = ttsStatuses.getOrNull(i) ?: DownloadStatus.Idle,
                ramEligible = device.isEligible(entry),
                ramWarning = device.shouldWarn(entry)
            )
        }
        val normalizedTtsEngineId = when (ttsEngineId) {
            MeloTtsEngine.ID,
            MeloTtsEngine.LEGACY_KOKORO_ID -> MeloTtsEngine.ID
            else -> SystemTtsEngine.ID
        }
        val currentTtsEntry = TtsModelCatalog.byId(activeTtsModelId)
            ?.takeIf { TtsModelCatalog.supportsLanguage(it, lang ?: "ko") }
            ?: TtsModelCatalog.forLanguage(lang ?: "ko")
        val currentTtsDownloaded = currentTtsEntry?.let { entry ->
            ttsRows.firstOrNull { it.entry.id == entry.id }?.downloaded
        } == true
        val effectiveTtsEngineId = if (
            normalizedTtsEngineId == MeloTtsEngine.ID &&
            currentTtsDownloaded
        ) {
            MeloTtsEngine.ID
        } else {
            SystemTtsEngine.ID
        }
        SettingsUiState(
            language = lang ?: "ko",
            systemPrompt = prompt.orEmpty(),
            activeModelId = activeId ?: ModelCatalog.recommended.id,
            modelRows = rows,
            contextStrategy = ContextStrategy.from(strategy),
            updateState = uState,
            autoUpdateCheck = autoUpdate,
            cameraInstantPreview = cameraInstant,
            ttsEngineId = effectiveTtsEngineId,
            activeTtsModelId = currentTtsEntry?.id,
            ttsModelRows = ttsRows,
            ttsVoicePreset = TtsModelCatalog.normalizeVoicePreset(voicePreset),
            voiceLoopEnabled = voiceLoop
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setLanguage(code: String) {
        viewModelScope.launch {
            prefs.setLanguage(code)
            val targetTts = TtsModelCatalog.forLanguage(code)
            val engine = if (targetTts != null && tts.isModelDownloaded(targetTts)) {
                MeloTtsEngine.ID
            } else {
                SystemTtsEngine.ID
            }
            if (targetTts != null) prefs.setActiveTtsModelId(targetTts.id)
            prefs.setTtsEngine(engine)
        }
    }

    fun setSystemPrompt(text: String) {
        viewModelScope.launch { prefs.setSystemPrompt(text) }
    }

    fun setAutoUpdateCheck(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoUpdateCheck(enabled) }
    }

    fun setCameraInstantPreview(enabled: Boolean) {
        viewModelScope.launch { prefs.setCameraInstantPreview(enabled) }
    }

    fun resetSystemPrompt() {
        viewModelScope.launch { prefs.setSystemPrompt("") }
    }

    fun setContextStrategy(strategy: ContextStrategy) {
        viewModelScope.launch { prefs.setContextStrategy(ContextStrategy.toKey(strategy)) }
    }

    fun clearChats() {
        viewModelScope.launch { chatRepo.deleteAllSessions() }
    }

    fun startDownload(entry: ModelEntry) {
        if (!device.isEligible(entry)) return
        if (!gemma.isDownloaded(entry)) downloader.start(entry, gemma.fileFor(entry))
    }

    fun cancelDownload(entry: ModelEntry) = downloader.cancel(entry)

    fun deleteModel(entry: ModelEntry) {
        gemma.delete(entry)
        refreshTick.value++
        if (state.value.activeModelId == entry.id) {
            viewModelScope.launch {
                prefs.setActiveModelId(ModelCatalog.recommended.id)
                gemma.reload()
            }
        }
    }

    fun startTtsDownload(entry: ModelEntry) {
        if (!tts.isModelDownloaded(entry)) downloader.start(entry, tts.fileFor(entry))
    }

    fun isTtsModelUsable(entry: ModelEntry): Boolean = tts.isModelUsable(entry)

    fun cancelTtsDownload(entry: ModelEntry) = downloader.cancel(entry)

    fun deleteTtsModel(entry: ModelEntry) {
        tts.deleteModel(entry)
        refreshTick.value++
        if (state.value.activeTtsModelId == entry.id) {
            viewModelScope.launch { prefs.setTtsEngine(SystemTtsEngine.ID) }
        }
    }

    fun setTtsEngine(id: String) {
        viewModelScope.launch {
            val normalized = if (id == MeloTtsEngine.LEGACY_KOKORO_ID) MeloTtsEngine.ID else id
            val targetTts = TtsModelCatalog.forLanguage(state.value.language)
            val allowed = normalized == SystemTtsEngine.ID ||
                (normalized == MeloTtsEngine.ID && targetTts != null && tts.isModelDownloaded(targetTts))
            prefs.setTtsEngine(if (allowed) normalized else SystemTtsEngine.ID)
        }
    }

    fun setVoiceLoopEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setVoiceLoopEnabled(enabled) }
    }

    fun setTtsVoicePreset(preset: String) {
        viewModelScope.launch { tts.setVoicePreset(preset) }
    }

    fun previewTtsVoice() {
        val lang = state.value.language
        viewModelScope.launch {
            tts.setVoicePreset(state.value.ttsVoicePreset)
            tts.speak(ttsPreviewText(lang), lang)
        }
    }

    fun selectModel(entry: ModelEntry) {
        if (!device.isEligible(entry)) return
        if (!gemma.isDownloaded(entry)) return
        viewModelScope.launch {
            prefs.setActiveModelId(entry.id)
            gemma.reload()
        }
    }

    fun selectTtsModel(entry: ModelEntry) {
        if (!tts.isModelDownloaded(entry)) return
        viewModelScope.launch {
            TtsModelCatalog.languageFor(entry)?.let { prefs.setLanguage(it) }
            prefs.setActiveTtsModelId(entry.id)
            prefs.setTtsEngine(MeloTtsEngine.ID)
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch { updateManager.checkForUpdate() }
    }

    fun startUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        updateManager.startFlexibleUpdate(launcher)
    }

    fun installUpdate() {
        updateManager.completeUpdate()
    }

    fun dismissUpdateState() {
        updateManager.setIdle()
    }

    companion object {
        private fun ttsPreviewText(language: String): String =
            when (language.lowercase()) {
                "en" -> "Hello, I am NOMAD AI."
                "ja" -> "こんにちは、私はノマドAIです。"
                "zh" -> "你好，我是 NOMAD AI。"
                else -> "안녕하세요. 저는 노마드 AI입니다."
            }

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NomadApp
                return SettingsViewModel(
                    prefs = app.container.prefs,
                    gemma = app.container.gemma,
                    downloader = app.container.downloader,
                    chatRepo = app.container.chatRepository,
                    device = app.container.device,
                    updateManager = app.container.updateManager,
                    tts = app.container.tts
                ) as T
            }
        }
    }
}
