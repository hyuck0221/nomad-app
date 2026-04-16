package com.nomad.travel.tools

import com.nomad.travel.data.ChatMessage
import com.nomad.travel.data.Role
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds a (system instruction, user message) pair for the LiteRT-LM Engine.
 * Chat templating is handled by the runtime — we supply plain prose.
 *
 * Base persona + capability/tool-tag rules are always applied. The user's
 * custom prompt from Settings is appended as *additional* instructions so
 * side-effects like expense logging keep working.
 *
 * Per-session multi-turn context is encoded inside the single user message
 * as a transcript, since LiteRT-LM conversations are created fresh per turn.
 */
object Prompt {

    private const val BASE_PERSONA =
        "You are NOMAD AI (노마드 AI), a concise on-device travel assistant. " +
            "When asked your name or who you are, identify yourself as \"NOMAD AI\" (Korean: 노마드 AI). " +
            "Capabilities: translate menus (any source language), explain dishes, suggest places, " +
            "answer travel questions, log expenses."

    private const val TOOL_RULES =
        "Answering policy: ALWAYS give a direct, complete answer first. " +
            "Make reasonable assumptions and state them, rather than asking the user to choose. " +
            "NEVER ask follow-up questions, present options, or request clarification unless " +
            "you truly cannot answer at all without the missing information. " +
            "If there are several possible answers, pick the most likely one and mention alternatives briefly. " +
            "\n\nTOOLS — you can call these by emitting the exact tag inline at the end of your reply. " +
            "Tags are machine-parsed and hidden from the user, so still write a short natural-language " +
            "sentence BEFORE the tag describing what you are about to do.\n" +
            "1) EXPENSE — when the user logs a spend, append exactly one tag: " +
            "<EXPENSE amount=\"NUMBER\" currency=\"ISO\" category=\"food|transport|stay|misc\" note=\"SHORT\">.\n" +
            "2) CURRENCY — ONLY when the user EXPLICITLY asks to convert money or check an exchange rate " +
            "(e.g. \"100달러 원화로 얼마야?\", \"엔화 환율 알려줘\", \"convert 50 USD to EUR\"). " +
            "Do NOT emit this tag for expense logging, price mentions, general travel talk, or time questions. " +
            "DO NOT guess or compute the rate yourself. " +
            "Append exactly one tag: <CURRENCY amount=\"NUMBER\" from=\"ISO_CODE\" to=\"ISO_CODE\">. " +
            "Use 3-letter ISO codes (USD, KRW, JPY, EUR, CNY, ...). " +
            "Before the tag, briefly restate what you will convert. " +
            "Do not output numeric results — the app runs the conversion and shows them separately.\n" +
            "3) ASK — when you genuinely need the user to pick between 2 to 4 mutually exclusive options " +
            "to proceed (and no sensible default exists), append: " +
            "<ASK prompt=\"SHORT QUESTION\" options=\"option one|option two|option three\">. Use sparingly. " +
            "The app renders the options as tappable chips and feeds the chosen one back as the next turn.\n" +
            "4) TRANSLATE — when the user wants to translate text or asks about translation, " +
            "append: <TRANSLATE src=\"LANG_CODE\" tgt=\"LANG_CODE\">. " +
            "Use 2-letter language codes (ko, en, ja, zh, es, fr, de, ...). " +
            "Example: user says \"영어로 번역하고 싶어\" → <TRANSLATE src=\"ko\" tgt=\"en\">.\n" +
            "5) INTERPRET — when the user wants real-time face-to-face interpretation or " +
            "conversation with someone speaking another language, " +
            "append: <INTERPRET src=\"LANG_CODE\" tgt=\"LANG_CODE\">. " +
            "Example: user says \"일본인과 대화해야 해\" → <INTERPRET src=\"ko\" tgt=\"ja\">.\n" +
            "STRICT: the ONLY tags you may ever emit are <EXPENSE>, <CURRENCY>, <ASK>, <TRANSLATE>, and <INTERPRET>. " +
            "Never write any other XML/HTML-like tag in your reply — no <TEXT>, <END>, <RESPONSE>, " +
            "<ANSWER>, <OUTPUT>, <THINK>, or closing tags of any kind. Plain prose only, outside the five tool tags above.\n" +
            "For menu OCR, list each item with: original · translation · 1-line description."

    private const val MID_CONVERSATION_RULE =
        "This is a continuing conversation. Do NOT greet the user, introduce yourself, " +
            "restate your name or capabilities, or say things like \"Hello\" / \"I'm NOMAD AI\". " +
            "Jump straight to answering the current message."

    data class Built(val systemInstruction: String, val userMessage: String)

    data class ContextWindow(
        /** Prior turns to replay as transcript, in chronological order. */
        val history: List<ChatMessage>,
        /** Optional condensed summary prepended to the transcript. */
        val summary: String? = null
    )

    fun build(
        uiLanguage: String,
        userText: String,
        ocrBlock: String?,
        customSystemPrompt: String? = null,
        window: ContextWindow = ContextWindow(emptyList())
    ): Built {
        val lang = langName(uiLanguage)
        val extra = customSystemPrompt?.trim().orEmpty()

        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd (E) HH:mm", Locale.getDefault())
        val currentTime = "Current local time: ${now.format(timeFmt)} (${zone.id}). " +
            "Use this when the user asks about the current time, date, or day of week. " +
            "You may also calculate other timezone times based on this."

        val midConversation = window.history.isNotEmpty() || !window.summary.isNullOrBlank()
        val systemInstruction = buildString {
            append(BASE_PERSONA)
            append(' ')
            append(currentTime)
            append(' ')
            append("Always answer in ")
            append(lang)
            append(". ")
            append(TOOL_RULES)
            if (midConversation) {
                append(' ')
                append(MID_CONVERSATION_RULE)
            }
            if (extra.isNotEmpty()) {
                append(' ')
                append("Additional user instructions: ")
                append(extra)
            }
        }

        val userMessage = buildString {
            if (!window.summary.isNullOrBlank()) {
                append("Summary of earlier conversation:\n")
                append(window.summary.trim())
                append("\n\n")
            }
            if (window.history.isNotEmpty()) {
                append("Previous conversation:\n")
                window.history.forEach { m ->
                    val who = if (m.role == Role.USER) "User" else "Assistant"
                    append(who).append(": ").append(m.text.trim()).append('\n')
                }
                append('\n')
            }
            if (!ocrBlock.isNullOrBlank()) {
                append("[MENU OCR]\n")
                append(ocrBlock.trim())
                append("\n\n")
                append(userText.ifBlank { "Translate and explain each item." })
            } else {
                append(userText)
            }
        }

        return Built(systemInstruction = systemInstruction, userMessage = userMessage)
    }

    /**
     * Apply the active [ContextStrategy] to [history] so the built user
     * message fits within [inputTokenBudget] along with the incoming turn
     * and any OCR block.
     *
     * For [ContextStrategy.COMPACT] the caller must supply [summarize] which
     * produces a short summary of the dropped portion (typically by calling
     * the same on-device model with a summarization prompt).
     */
    suspend fun buildWindow(
        strategy: ContextStrategy,
        history: List<ChatMessage>,
        pendingInputText: String,
        ocrBlock: String?,
        inputTokenBudget: Int = DEFAULT_INPUT_TOKEN_BUDGET,
        summarize: suspend (List<ChatMessage>) -> String = { "" }
    ): ContextWindow {
        if (history.isEmpty()) return ContextWindow(emptyList())
        val reserved = estimateTokens(pendingInputText) + estimateTokens(ocrBlock.orEmpty()) + 200
        val budget = (inputTokenBudget - reserved).coerceAtLeast(256)

        val historyTokens = history.sumOf { estimateTokens(it.text) + 8 }
        if (historyTokens <= budget) return ContextWindow(history)

        return when (strategy) {
            ContextStrategy.RESET -> ContextWindow(emptyList())
            ContextStrategy.DROP_OLDEST -> {
                val kept = ArrayDeque<ChatMessage>()
                var running = 0
                for (m in history.reversed()) {
                    val cost = estimateTokens(m.text) + 8
                    if (running + cost > budget) break
                    kept.addFirst(m)
                    running += cost
                }
                ContextWindow(kept.toList())
            }
            ContextStrategy.COMPACT -> {
                val kept = ArrayDeque<ChatMessage>()
                var running = 0
                for (m in history.reversed()) {
                    val cost = estimateTokens(m.text) + 8
                    if (running + cost > budget / 2) break
                    kept.addFirst(m)
                    running += cost
                }
                val dropped = history.dropLast(kept.size)
                val summary = if (dropped.isEmpty()) null else runCatching {
                    summarize(dropped).takeIf { it.isNotBlank() }
                }.getOrNull()
                ContextWindow(kept.toList(), summary)
            }
        }
    }

    private fun langName(code: String): String = when (code.lowercase()) {
        "ko" -> "Korean"
        "en" -> "English"
        "zh" -> "Chinese"
        "ja" -> "Japanese"
        else -> "English"
    }
}
