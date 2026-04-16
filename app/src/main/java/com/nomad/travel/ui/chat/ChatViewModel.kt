package com.nomad.travel.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.nomad.travel.NomadApp
import com.nomad.travel.data.ChatMessage
import com.nomad.travel.data.Role
import com.nomad.travel.data.UserPrefs
import com.nomad.travel.data.chat.ChatRepository
import com.nomad.travel.data.chat.ChatSessionEntity
import com.nomad.travel.tools.CurrencyService
import com.nomad.travel.tools.ToolRouter
import com.nomad.travel.tools.ToolTags
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
    val ask: ToolTags.AskCall? = null
)

data class ChatUiState(
    val sessions: List<ChatSessionEntity> = emptyList(),
    val currentSessionId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isResponding: Boolean = false,
    val pending: PendingAction? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val router: ToolRouter,
    private val repo: ChatRepository,
    private val prefs: UserPrefs,
    private val currencyService: CurrencyService
) : ViewModel() {

    private val currentSessionId = MutableStateFlow<Long?>(null)
    private val responding = MutableStateFlow(false)
    /** In-memory streaming/pending message layered on top of persisted messages. */
    private val overlay = MutableStateFlow<ChatMessage?>(null)
    private val pending = MutableStateFlow<PendingAction?>(null)
    private var streamJob: Job? = null

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
        pending
    ) { base, p -> base.copy(pending = p) }
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
                    currencyService = app.container.currencyService
                ) as T
            }
        }
    }
}
