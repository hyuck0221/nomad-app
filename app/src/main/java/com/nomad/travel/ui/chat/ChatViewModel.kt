package com.nomad.travel.ui.chat

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.nomad.travel.BuildConfig
import com.nomad.travel.NomadApp
import com.nomad.travel.data.ChatMessage
import com.nomad.travel.data.Role
import com.nomad.travel.data.UserPrefs
import com.nomad.travel.data.chat.ChatRepository
import com.nomad.travel.data.chat.ChatSessionEntity
import com.nomad.travel.tools.CurrencyService
import com.nomad.travel.tools.ToolRouter
import com.nomad.travel.tools.ToolTags
import com.nomad.travel.update.UpdateChecker
import com.nomad.travel.update.UpdateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.Locale

data class PendingAction(
    val currency: ToolTags.CurrencyCall? = null,
    val ask: ToolTags.AskCall? = null,
    val translate: ToolTags.TranslateCall? = null,
    val interpret: ToolTags.InterpretCall? = null
)

sealed interface UpdateBannerState {
    data object Hidden : UpdateBannerState
    data class Available(val release: UpdateChecker.LatestRelease) : UpdateBannerState
    data class Downloading(val progress: Float) : UpdateBannerState
    data object Installing : UpdateBannerState
}

data class ChatUiState(
    val sessions: List<ChatSessionEntity> = emptyList(),
    val currentSessionId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isResponding: Boolean = false,
    val isListening: Boolean = false,
    val pending: PendingAction? = null,
    val updateBanner: UpdateBannerState = UpdateBannerState.Hidden
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val router: ToolRouter,
    private val repo: ChatRepository,
    private val prefs: UserPrefs,
    private val currencyService: CurrencyService,
    private val updateManager: UpdateManager,
    private val app: Application
) : ViewModel() {

    private val currentSessionId = MutableStateFlow<Long?>(null)
    private val responding = MutableStateFlow(false)
    private val listening = MutableStateFlow(false)
    /** In-memory streaming/pending message layered on top of persisted messages. */
    private val overlay = MutableStateFlow<ChatMessage?>(null)
    private val pending = MutableStateFlow<PendingAction?>(null)
    private val updateBanner = MutableStateFlow<UpdateBannerState>(UpdateBannerState.Hidden)
    private var streamJob: Job? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val persistedMessages = currentSessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repo.observeMessages(id)
    }

    val state: StateFlow<ChatUiState> = combine(
        combine(
            repo.observeSessions(),
            currentSessionId,
            persistedMessages,
            responding,
            overlay
        ) { sessions, id, msgs, busy, live ->
            val combined = if (live != null) msgs + live else msgs
            ChatUiState(
                sessions = sessions,
                currentSessionId = id,
                messages = combined,
                isResponding = busy
            )
        },
        pending,
        updateBanner,
        listening
    ) { base, p, banner, listen -> base.copy(pending = p, updateBanner = banner, isListening = listen) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())

    init {
        viewModelScope.launch {
            val last = prefs.lastSessionIdBlocking()
            val resolved = last?.takeIf { repo.sessionExists(it) }
                ?: repo.mostRecentSessionId()
                ?: repo.createSession(DEFAULT_TITLE)
            currentSessionId.value = resolved
            prefs.setLastSessionId(resolved)
        }
        // Auto-check for updates on app start
        viewModelScope.launch {
            if (!prefs.autoUpdateCheckBlocking()) return@launch
            val checker = updateManager.checker
            val release = checker.fetchLatest() ?: return@launch
            if (!checker.isNewer(release.versionName, BuildConfig.VERSION_NAME)) return@launch
            val skipped = prefs.skippedVersionBlocking()
            if (skipped == release.versionName) return@launch
            updateBanner.value = UpdateBannerState.Available(release)
        }
    }

    fun switchSession(id: Long) {
        if (currentSessionId.value == id) return
        overlay.value = null
        pending.value = null
        currentSessionId.value = id
        viewModelScope.launch { prefs.setLastSessionId(id) }
    }

    fun newSession() {
        viewModelScope.launch {
            overlay.value = null
            pending.value = null
            val id = repo.createSession(DEFAULT_TITLE)
            currentSessionId.value = id
            prefs.setLastSessionId(id)
        }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            repo.deleteSession(id)
            if (currentSessionId.value == id) {
                val next = repo.mostRecentSessionId() ?: repo.createSession(DEFAULT_TITLE)
                currentSessionId.value = next
                prefs.setLastSessionId(next)
                overlay.value = null
                pending.value = null
            }
        }
    }

    fun send(context: Context, text: String, image: Uri?) {
        if (text.isBlank() && image == null) return
        if (responding.value) return
        val sessionId = currentSessionId.value ?: return

        pending.value = null

        streamJob = viewModelScope.launch {
            val historySnapshot = repo.snapshotMessages(sessionId)
                .filter { it.toolTag != "cancelled" }
            val isFirstTurn = historySnapshot.isEmpty()
            repo.appendMessage(sessionId, Role.USER, text, image)
            overlay.value = ChatMessage(
                role = Role.ASSISTANT,
                text = "",
                pending = true,
                streaming = false
            )
            responding.value = true
            applyFallbackTitle(sessionId, isFirstTurn, text)

            try {
                router.handleStream(
                    context = context,
                    turn = ToolRouter.Turn(
                        userText = text,
                        imageUri = image,
                        uiLanguage = Locale.getDefault().language,
                        history = historySnapshot
                    )
                ).collect { evt ->
                    when (evt) {
                        is ToolRouter.StreamEvent.Delta -> {
                            overlay.update { cur ->
                                (cur ?: ChatMessage(role = Role.ASSISTANT, text = "")).copy(
                                    text = evt.text,
                                    pending = false,
                                    streaming = true
                                )
                            }
                        }
                        is ToolRouter.StreamEvent.Complete -> {
                            repo.appendMessage(
                                sessionId,
                                Role.ASSISTANT,
                                evt.text,
                                toolTag = evt.toolTag
                            )
                            overlay.value = null
                            if (evt.currency != null) {
                                pending.value = PendingAction(currency = evt.currency)
                            } else if (evt.ask != null) {
                                pending.value = PendingAction(ask = evt.ask)
                            } else if (evt.translate != null) {
                                pending.value = PendingAction(translate = evt.translate)
                            } else if (evt.interpret != null) {
                                pending.value = PendingAction(interpret = evt.interpret)
                            }
                        }
                    }
                }
            } catch (ce: CancellationException) {
                val partial = overlay.value?.text.orEmpty()
                withContext(NonCancellable) {
                    repo.appendMessage(
                        sessionId,
                        Role.ASSISTANT,
                        partial,
                        toolTag = "cancelled"
                    )
                }
                overlay.value = null
                throw ce
            } catch (e: Throwable) {
                repo.appendMessage(
                    sessionId,
                    Role.ASSISTANT,
                    "⚠️ ${e.message ?: "error"}",
                    toolTag = "error"
                )
                overlay.value = null
            } finally {
                responding.value = false
            }
            if (isFirstTurn) {
                generateLlmTitle(sessionId, text)
            }
        }
    }

    fun cancelResponse() {
        streamJob?.cancel()
        streamJob = null
    }

    fun dismissPending() {
        pending.value = null
    }

    /** User chose how to run a pending currency conversion. */
    fun resolveCurrency(useLiveApi: Boolean) {
        val call = pending.value?.currency ?: return
        val sessionId = currentSessionId.value ?: return
        pending.value = null

        viewModelScope.launch {
            val placeholder = repo.appendMessage(
                sessionId,
                Role.ASSISTANT,
                loadingText(useLiveApi),
                toolTag = "currency_loading"
            )

            val result: CurrencyService.Result = if (useLiveApi) {
                currencyService.convertLive(call.amount, call.from, call.to)
                    ?: currencyService.convertEstimated(call.amount, call.from, call.to)
                        .copy(source = CurrencyService.Source.ESTIMATED)
            } else {
                currencyService.convertEstimated(call.amount, call.from, call.to)
            }

            val formatted = formatCurrencyResult(result)
            repo.updateMessage(placeholder, formatted, "currency_result")
        }
    }

    /** User picked one of the ASK options — feed it back as a user turn. */
    fun resolveAsk(context: Context, option: String) {
        val p = pending.value?.ask ?: return
        pending.value = null
        send(context, option, null)
        // keep `p` reference-silent to hint the choice applied
        @Suppress("UNUSED_VARIABLE") val _ignore = p
    }

    private suspend fun applyFallbackTitle(
        sessionId: Long,
        isFirstTurn: Boolean,
        firstUserText: String
    ) {
        if (!isFirstTurn) return
        val trimmed = firstUserText.trim()
        if (trimmed.isEmpty()) return
        val title = if (trimmed.length <= 28) trimmed else trimmed.take(28) + "…"
        repo.renameSession(sessionId, title)
    }

    private suspend fun generateLlmTitle(sessionId: Long, firstUserText: String) {
        val title = router.generateTitle(firstUserText)
        if (title.isNotBlank()) {
            repo.renameSession(sessionId, title)
        }
    }

    private fun loadingText(live: Boolean): String =
        if (live) "환율을 조회하고 있어요…" else "오프라인 예상 환율로 계산하고 있어요…"

    private fun formatCurrencyResult(r: CurrencyService.Result): String {
        val amtFmt = DecimalFormat("#,##0.##")
        val rateFmt = DecimalFormat("#,##0.####")
        val source = when (r.source) {
            CurrencyService.Source.LIVE_API -> "실시간 환율"
            CurrencyService.Source.ESTIMATED -> "오프라인 예상"
        }
        val sym = r.symbol?.let { "$it " } ?: ""
        return buildString {
            append("**")
            append(amtFmt.format(r.amount))
            append(' ').append(r.fromCode).append(" → ")
            append(sym).append(amtFmt.format(r.convertedAmount))
            append(' ').append(r.toCode)
            append("**\n")
            append("1 ").append(r.fromCode).append(" ≈ ")
            append(rateFmt.format(r.rate)).append(' ').append(r.toCode)
            append("  ·  ").append(source)
        }
    }

    fun startUpdate() {
        val release = (updateBanner.value as? UpdateBannerState.Available)?.release ?: return
        val apkUrl = release.apkUrl
        if (apkUrl.isNullOrBlank()) {
            updateBanner.value = UpdateBannerState.Hidden
            return
        }
        viewModelScope.launch {
            updateBanner.value = UpdateBannerState.Downloading(0f)
            val file = updateManager.downloadApk(apkUrl) { progress ->
                updateBanner.value = UpdateBannerState.Downloading(progress)
            }
            if (file != null) {
                updateBanner.value = UpdateBannerState.Installing
                updateManager.installApk(file)
                updateBanner.value = UpdateBannerState.Hidden
            } else {
                updateBanner.value = UpdateBannerState.Hidden
            }
        }
    }

    fun skipUpdate() {
        val release = (updateBanner.value as? UpdateBannerState.Available)?.release
        if (release != null) {
            viewModelScope.launch { prefs.setSkippedVersion(release.versionName) }
        }
        updateBanner.value = UpdateBannerState.Hidden
    }

    /* ── STT ── */

    /** Callback set by the UI to receive partial/final speech text */
    var onSttResult: ((String) -> Unit)? = null

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(app)) return
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(app)

        val langCode = java.util.Locale.getDefault().language
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening.value = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening.value = false }
            override fun onError(error: Int) { listening.value = false }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                onSttResult?.invoke(text)
            }
            override fun onPartialResults(partial: Bundle?) {
                val text = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                onSttResult?.invoke(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        listening.value = false
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        streamJob?.cancel()
    }

    fun canInstallUnknownSources(): Boolean = updateManager.canInstallFromUnknownSources()

    fun unknownSourcesIntent() = updateManager.unknownSourcesSettingsIntent()

    companion object {
        private const val DEFAULT_TITLE = "New chat"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NomadApp
                return ChatViewModel(
                    router = app.container.toolRouter,
                    repo = app.container.chatRepository,
                    prefs = app.container.prefs,
                    currencyService = app.container.currencyService,
                    updateManager = app.container.updateManager,
                    app = app
                ) as T
            }
        }
    }
}
