package com.nomad.travel.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.nomad.travel.NomadApp
import com.nomad.travel.data.UserPrefs
import com.nomad.travel.llm.DeviceCapability
import com.nomad.travel.llm.DownloadStatus
import com.nomad.travel.llm.GemmaEngine
import com.nomad.travel.llm.ModelCatalog
import com.nomad.travel.llm.ModelDownloader
import com.nomad.travel.llm.ModelEntry
import com.nomad.travel.tts.MeloTtsEngine
import com.nomad.travel.tts.SystemTtsEngine
import com.nomad.travel.tts.TtsManager
import com.nomad.travel.tts.TtsModelCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModelRow(
    val entry: ModelEntry,
    val downloaded: Boolean,
    val status: DownloadStatus,
    val ramEligible: Boolean = true,
    val ramWarning: Boolean = false
)

data class SetupUiState(
    val rows: List<ModelRow> = emptyList(),
    val ttsRows: List<ModelRow> = emptyList(),
    val selectedId: String = ModelCatalog.recommended.id,
    val selectedTtsId: String? = null,
    val selectedPlan: SetupPlan = SetupPlan.BALANCED
) {
    val selected: ModelRow? get() = rows.firstOrNull { it.entry.id == selectedId }
    val selectedTts: ModelRow? get() = selectedTtsId?.let { id -> ttsRows.firstOrNull { it.entry.id == id } }
}

enum class SetupPlan {
    LIGHT,
    BALANCED,
    MAX,
    CUSTOM
}

private data class SetupSelection(
    val chatModelId: String,
    val ttsModelId: String?,
    val plan: SetupPlan
)

class SetupViewModel(
    private val gemma: GemmaEngine,
    private val downloader: ModelDownloader,
    private val prefs: UserPrefs,
    private val device: DeviceCapability,
    private val tts: TtsManager
) : ViewModel() {

    private val defaultPlan = recommendedPlanForDevice()
    private val selectedIdFlow = MutableStateFlow(chatModelFor(defaultPlan).id)
    private val selectedTtsIdFlow = MutableStateFlow<String?>(ttsModelIdFor(defaultPlan))
    private val selectedPlanFlow = MutableStateFlow(defaultPlan)
    private val refreshTick = MutableStateFlow(0)

    private val statusesFlow = combine(
        ModelCatalog.all.map { downloader.status(it) }
    ) { arr -> arr.toList() }

    private val ttsStatusesFlow = combine(
        TtsModelCatalog.all.map { downloader.status(it) }
    ) { arr -> arr.toList() }

    private val selectionFlow = combine(
        selectedIdFlow,
        selectedTtsIdFlow,
        selectedPlanFlow
    ) { selectedId, selectedTtsId, selectedPlan ->
        SetupSelection(selectedId, selectedTtsId, selectedPlan)
    }

    private val statusBundleFlow = combine(
        statusesFlow,
        ttsStatusesFlow
    ) { statuses, ttsStatuses ->
        statuses to ttsStatuses
    }

    val state: StateFlow<SetupUiState> = combine(
        statusBundleFlow,
        selectionFlow,
        refreshTick
    ) { statusBundle, selection, _ ->
        val (statuses, ttsStatuses) = statusBundle
        val rows = ModelCatalog.all.mapIndexed { i, entry ->
            ModelRow(
                entry = entry,
                downloaded = gemma.isDownloaded(entry),
                status = statuses.getOrNull(i) ?: DownloadStatus.Idle,
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
        // Auto-pick recommended, or first eligible model if recommended is blocked.
        val resolvedSelection = rows.firstOrNull { it.entry.id == selection.chatModelId }
            ?.takeIf { it.ramEligible }
            ?.entry?.id
            ?: rows.firstOrNull { it.ramEligible }?.entry?.id
            ?: selection.chatModelId
        val resolvedTtsSelection = selection.ttsModelId?.let { id ->
            ttsRows.firstOrNull { it.entry.id == id }?.takeIf { it.ramEligible }?.entry?.id
        }
        SetupUiState(
            rows = rows,
            ttsRows = ttsRows,
            selectedId = resolvedSelection,
            selectedTtsId = resolvedTtsSelection,
            selectedPlan = selection.plan
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SetupUiState(
            rows = ModelCatalog.all.map {
                ModelRow(
                    entry = it,
                    downloaded = gemma.isDownloaded(it),
                    status = DownloadStatus.Idle,
                    ramEligible = device.isEligible(it),
                    ramWarning = device.shouldWarn(it)
                )
            },
            ttsRows = TtsModelCatalog.all.map {
                ModelRow(
                    entry = it,
                    downloaded = tts.isModelDownloaded(it),
                    status = DownloadStatus.Idle,
                    ramEligible = device.isEligible(it),
                    ramWarning = device.shouldWarn(it)
                )
            },
            selectedId = chatModelFor(defaultPlan).id,
            selectedTtsId = ttsModelIdFor(defaultPlan),
            selectedPlan = defaultPlan
        )
    )

    fun selectPlan(plan: SetupPlan) {
        selectedPlanFlow.value = plan
        when (plan) {
            SetupPlan.LIGHT -> {
                selectedIdFlow.value = chatModelFor(plan).id
                selectedTtsIdFlow.value = ttsModelIdFor(plan)
            }
            SetupPlan.BALANCED -> {
                selectedIdFlow.value = chatModelFor(plan).id
                selectedTtsIdFlow.value = ttsModelIdFor(plan)
            }
            SetupPlan.MAX -> {
                selectedIdFlow.value = chatModelFor(plan).id
                selectedTtsIdFlow.value = ttsModelIdFor(plan)
            }
            SetupPlan.CUSTOM -> Unit
        }
    }

    fun select(entry: ModelEntry) {
        if (!device.isEligible(entry)) return
        selectedPlanFlow.value = SetupPlan.CUSTOM
        selectedIdFlow.value = entry.id
    }

    fun selectTts(entry: ModelEntry?) {
        if (entry != null && !device.isEligible(entry)) return
        selectedPlanFlow.value = SetupPlan.CUSTOM
        selectedTtsIdFlow.value = entry?.id
    }

    fun startDownload(entry: ModelEntry) {
        if (!device.isEligible(entry)) return
        if (gemma.isDownloaded(entry)) return
        downloader.start(entry, gemma.fileFor(entry))
    }

    fun startTtsDownload(entry: ModelEntry) {
        if (!device.isEligible(entry)) return
        if (tts.isModelDownloaded(entry)) return
        downloader.start(entry, tts.fileFor(entry))
    }

    fun cancelDownload(entry: ModelEntry) = downloader.cancel(entry)

    fun delete(entry: ModelEntry) {
        gemma.delete(entry)
        refreshTick.value++
    }

    fun deleteTts(entry: ModelEntry) {
        tts.deleteModel(entry)
        refreshTick.value++
    }

    fun startSelectedDownloads() {
        val s = state.value
        s.selected?.takeIf { !it.downloaded }?.entry?.let(::startDownload)
        s.selectedTts?.takeIf { !it.downloaded }?.entry?.let(::startTtsDownload)
    }

    fun selectedDownloadsComplete(): Boolean {
        val s = state.value
        val chatReady = s.selected?.downloaded == true
        val ttsReady = s.selectedTts?.downloaded ?: true
        return chatReady && ttsReady
    }

    fun selectedDownloading(): Boolean {
        val s = state.value
        return s.selected?.status is DownloadStatus.Progress ||
            s.selectedTts?.status is DownloadStatus.Progress
    }

    /** Commit the currently-selected model as the active one. */
    suspend fun commitSelection(): Boolean {
        val s = state.value
        val row = s.selected ?: return false
        if (!selectedDownloadsComplete()) return false
        prefs.setActiveModelId(row.entry.id)
        val ttsEntry = s.selectedTts
        if (ttsEntry?.downloaded == true) {
            prefs.setActiveTtsModelId(ttsEntry.entry.id)
            prefs.setTtsEngine(MeloTtsEngine.ID)
        } else {
            prefs.setTtsEngine(SystemTtsEngine.ID)
        }
        gemma.reload()
        return true
    }

    fun commitSelectionAnd(onDone: () -> Unit) {
        viewModelScope.launch { if (commitSelection()) onDone() }
    }

    private fun recommendedPlanForDevice(): SetupPlan = when {
        device.isEligible(ModelCatalog.gemma4E4B) && !device.shouldWarn(ModelCatalog.gemma4E4B) -> SetupPlan.MAX
        device.isEligible(ModelCatalog.gemma4E2B) && !device.shouldWarn(ModelCatalog.gemma4E2B) -> SetupPlan.BALANCED
        else -> SetupPlan.LIGHT
    }

    private fun chatModelFor(plan: SetupPlan): ModelEntry = when (plan) {
        SetupPlan.MAX -> ModelCatalog.gemma4E4B
        else -> ModelCatalog.gemma4E2B
    }

    private fun ttsModelIdFor(plan: SetupPlan): String? = when (plan) {
        SetupPlan.LIGHT -> null
        SetupPlan.BALANCED,
        SetupPlan.MAX -> TtsModelCatalog.supertonic3.id
        SetupPlan.CUSTOM -> selectedTtsIdFlow.value
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NomadApp
                return SetupViewModel(
                    gemma = app.container.gemma,
                    downloader = app.container.downloader,
                    prefs = app.container.prefs,
                    device = app.container.device,
                    tts = app.container.tts
                ) as T
            }
        }
    }
}
