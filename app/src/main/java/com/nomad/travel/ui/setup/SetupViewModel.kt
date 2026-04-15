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
    val selectedId: String = ModelCatalog.recommended.id
) {
    val selected: ModelRow? get() = rows.firstOrNull { it.entry.id == selectedId }
}

class SetupViewModel(
    private val gemma: GemmaEngine,
    private val downloader: ModelDownloader,
    private val prefs: UserPrefs,
    private val device: DeviceCapability
) : ViewModel() {

    private val selectedIdFlow = MutableStateFlow(ModelCatalog.recommended.id)
    private val refreshTick = MutableStateFlow(0)

    private val statusesFlow = combine(
        ModelCatalog.all.map { downloader.status(it) }
    ) { arr -> arr.toList() }

    val state: StateFlow<SetupUiState> = combine(
        statusesFlow,
        selectedIdFlow,
        refreshTick
    ) { statuses, selectedId, _ ->
        val rows = ModelCatalog.all.mapIndexed { i, entry ->
            ModelRow(
                entry = entry,
                downloaded = gemma.isDownloaded(entry),
                status = statuses.getOrNull(i) ?: DownloadStatus.Idle,
                ramEligible = device.isEligible(entry),
                ramWarning = device.shouldWarn(entry)
            )
        }
        // Auto-pick recommended, or first eligible model if recommended is blocked.
        val resolvedSelection = rows.firstOrNull { it.entry.id == selectedId }
            ?.takeIf { it.ramEligible }
            ?.entry?.id
            ?: rows.firstOrNull { it.ramEligible }?.entry?.id
            ?: selectedId
        SetupUiState(rows = rows, selectedId = resolvedSelection)
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
            }
        )
    )

    fun select(entry: ModelEntry) {
        if (!device.isEligible(entry)) return
        selectedIdFlow.value = entry.id
    }

    fun startDownload(entry: ModelEntry) {
        if (!device.isEligible(entry)) return
        if (gemma.isDownloaded(entry)) return
        downloader.start(entry, gemma.fileFor(entry))
    }

    fun cancelDownload(entry: ModelEntry) = downloader.cancel(entry)

    fun delete(entry: ModelEntry) {
        gemma.delete(entry)
        refreshTick.value++
    }

    /** Commit the currently-selected model as the active one. */
    suspend fun commitSelection(): Boolean {
        val row = state.value.selected ?: return false
        if (!row.downloaded) return false
        prefs.setActiveModelId(row.entry.id)
        gemma.reload()
        return true
    }

    fun commitSelectionAnd(onDone: () -> Unit) {
        viewModelScope.launch { if (commitSelection()) onDone() }
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
                    device = app.container.device
                ) as T
            }
        }
    }
}
