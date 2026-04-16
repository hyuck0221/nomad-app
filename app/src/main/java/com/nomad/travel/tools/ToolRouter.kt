package com.nomad.travel.tools

import android.content.Context
import android.net.Uri
import com.nomad.travel.data.ChatMessage
import com.nomad.travel.data.UserPrefs
import com.nomad.travel.data.expense.Expense
import com.nomad.travel.data.expense.ExpenseRepository
import com.nomad.travel.llm.GemmaEngine
import com.nomad.travel.ocr.OcrService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ToolRouter(
    private val gemma: GemmaEngine,
    private val ocr: OcrService,
    private val expenses: ExpenseRepository,
    private val prefs: UserPrefs
) {

    data class Turn(
        val userText: String,
        val imageUri: Uri? = null,
        val uiLanguage: String = "ko",
        /** Persisted prior messages for the current session, chronological. */
        val history: List<ChatMessage> = emptyList()
    )

    sealed interface StreamEvent {
        data class Delta(val text: String) : StreamEvent
        data class Complete(
            val text: String,
            val toolTag: String?,
            val currency: ToolTags.CurrencyCall? = null,
            val ask: ToolTags.AskCall? = null
        ) : StreamEvent
    }

    fun handleStream(context: Context, turn: Turn): Flow<StreamEvent> = flow {
        if (!gemma.ensureLoaded()) {
            emit(StreamEvent.Complete(FALLBACK_NO_MODEL, "error"))
            return@flow
        }

        val ocrBlock: String? = turn.imageUri?.let { uri ->
            runCatching { ocr.recognize(context, uri) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }

        val baseTag = when {
            ocrBlock != null -> "menu_translate"
            looksLikeExpense(turn.userText) -> "expense"
            else -> "chat"
        }

        val customPrompt = prefs.systemPromptBlocking()?.takeIf { it.isNotBlank() }
        val strategy = ContextStrategy.from(prefs.contextStrategyBlocking())

        val window = Prompt.buildWindow(
            strategy = strategy,
            history = turn.history,
            pendingInputText = turn.userText,
            ocrBlock = ocrBlock,
            summarize = { dropped -> summarize(dropped) }
        )

        val built = Prompt.build(
            uiLanguage = turn.uiLanguage,
            userText = turn.userText,
            ocrBlock = ocrBlock,
            customSystemPrompt = customPrompt,
            window = window
        )

        var lastCumulative = ""
        gemma.generateStream(built.systemInstruction, built.userMessage).collect { cumulative ->
            lastCumulative = cumulative
            emit(StreamEvent.Delta(ToolTags.stripForStream(cumulative)))
        }

        val post = postProcess(lastCumulative)
        val finalTag = post.toolTag ?: baseTag
        emit(
            StreamEvent.Complete(
                text = post.visibleText.ifBlank { ToolTags.stripAll(lastCumulative) },
                toolTag = finalTag,
                currency = post.currency,
                ask = post.ask
            )
        )
    }

    suspend fun generateTitle(firstUserText: String): String {
        val trimmed = firstUserText.trim()
        if (trimmed.isEmpty()) return ""
        if (!gemma.ensureLoaded()) return ""
        return runCatching {
            gemma.generate(
                systemInstruction = "You generate chat titles. Read the user's first message and " +
                    "output a single short title (max 6 words) that captures its topic. " +
                    "Reply in the same language as the message. " +
                    "No quotes, no trailing punctuation, no preamble, no markdown.",
                userMessage = trimmed
            )
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
                .trim('"', '\'', ' ', '.', '。', '!', '?', '·', '-')
                .take(40)
        }.getOrDefault("")
    }

    private suspend fun summarize(messages: List<com.nomad.travel.data.ChatMessage>): String {
        if (messages.isEmpty()) return ""
        val transcript = messages.joinToString("\n") { m ->
            val who = if (m.role == com.nomad.travel.data.Role.USER) "User" else "Assistant"
            "$who: ${m.text.trim()}"
        }
        return runCatching {
            gemma.generate(
                systemInstruction = "You compress conversations. Output a compact 2-5 sentence " +
                    "summary that preserves user intents, names, places, dates, and decisions. " +
                    "No preamble, no markdown, no bullets.",
                userMessage = transcript
            ).trim()
        }.getOrDefault("")
    }

    private data class PostResult(
        val visibleText: String,
        val toolTag: String?,
        val currency: ToolTags.CurrencyCall?,
        val ask: ToolTags.AskCall?
    )

    private suspend fun postProcess(raw: String): PostResult {
        var tag: String? = null

        EXPENSE_TAG.find(raw)?.let { m ->
            val attrs = ToolTags.parseAttrs(m.value)
            val amount = attrs["amount"]?.toDoubleOrNull()
            if (amount != null) {
                val currency = attrs["currency"]?.uppercase() ?: "USD"
                val category = attrs["category"] ?: "misc"
                val note = attrs["note"].orEmpty()
                expenses.add(
                    Expense(
                        amount = amount,
                        currency = currency,
                        category = category,
                        note = note
                    )
                )
                tag = "expense"
            }
        }

        val currency = ToolTags.extractCurrency(raw)
        if (currency != null) tag = "currency"

        val ask = ToolTags.extractAsk(raw)
        if (ask != null) tag = "ask"

        return PostResult(
            visibleText = ToolTags.stripAll(raw),
            toolTag = tag,
            currency = currency,
            ask = ask
        )
    }

    private fun looksLikeExpense(text: String): Boolean {
        val lower = text.lowercase()
        return EXPENSE_HINTS.any { lower.contains(it) }
    }

    companion object {
        private val EXPENSE_TAG = Regex("<EXPENSE[^>]*>")
        private val EXPENSE_HINTS = listOf(
            "지출", "썼어", "결제", "샀어",
            "spent", "paid", "bought", "expense",
            "花了", "支出",
            "使った", "払った"
        )
        private const val FALLBACK_NO_MODEL =
            "온디바이스 모델이 아직 준비되지 않았습니다. 설정에서 Gemma 모델 파일을 추가해주세요."
    }
}
