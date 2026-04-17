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
            "Reveal your name or identity ONLY when the user explicitly asks who/what you are " +
            "(\"이름이 뭐야?\", \"누구야?\", \"what are you?\"). " +
            "Otherwise NEVER mention \"NOMAD AI\", \"노마드 AI\", that you are an AI/assistant, " +
            "or list your capabilities — just answer the user's message directly. " +
            "You help with travel questions, dish/menu explanations, place suggestions, and expense logging."

    private const val TOOL_RULES =
        "Answer directly and completely. Make reasonable assumptions; do NOT ask follow-ups or " +
            "present options. If several answers are possible, pick the likeliest and mention " +
            "alternatives briefly.\n" +
            "\n" +
            "TOOLS. DEFAULT = plain prose, NO tag. Emit a tag ONLY when its trigger below clearly " +
            "matches. At most ONE tag, at the very END of your reply, after a short natural sentence. " +
            "The five tags below are the only ones allowed — never invent other XML-like tags or " +
            "closing tags.\n" +
            "\n" +
            "<EXPENSE amount=\"N\" currency=\"ISO\" category=\"food|transport|stay|misc\" note=\"SHORT\">\n" +
            "  When: user logs money they already spent (\"점심 만원 썼어\", \"paid 20 USD for taxi\").\n" +
            "\n" +
            "<CURRENCY amount=\"N\" from=\"ISO\" to=\"ISO\">  — 3-letter codes.\n" +
            "  When: user EXPLICITLY asks to convert money or check an FX rate " +
            "(\"100달러 원화로?\", \"엔 환율\", \"convert 50 USD to EUR\"). " +
            "Do NOT compute the number — the app does. " +
            "Not for expense logging, price mentions, or general chat.\n" +
            "\n" +
            "<ASK prompt=\"Q\" options=\"A|B|C\">\n" +
            "  When: you truly cannot continue without the user choosing among 2-4 mutually " +
            "exclusive options and no sensible default exists. Rare.\n" +
            "\n" +
            "TRANSLATE vs INTERPRET — decide BEFORE emitting:\n" +
            "  • TRANSLATE = user will TYPE text themselves and wants it converted. One person, one device.\n" +
            "  • INTERPRET = user wants to have a LIVE CONVERSATION with another person who speaks a " +
            "different language. Two people, face-to-face. Any mention of \"~사람/~인과 대화\", " +
            "\"외국인과 말해야\", \"실시간 대화/통역\", \"talk with a ___\", \"speak to a ___\" ⇒ INTERPRET.\n" +
            "\n" +
            "<TRANSLATE src=\"XX\" tgt=\"YY\">  — 2-letter codes (ko, en, ja, zh, vi, es, fr, de, ...).\n" +
            "  MUST emit when user asks to open/switch to/launch the translator " +
            "(\"번역기 열어줘\", \"일본어 번역기\", \"영어 번역 모드\", \"중국어 번역기 켜줘\", \"open translator\"). " +
            "Reply with one short sentence (\"번역기를 열게요.\") then the tag.\n" +
            "  Lang inference: src = user's UI language; tgt = foreign language they named " +
            "(일본어=ja, 영어=en, 중국어=zh, 베트남어=vi, 스페인어=es). If only one language is named, " +
            "that is tgt.\n" +
            "  Do NOT emit for: menu/OCR input; a one-off \"translate this: …\" (answer inline); " +
            "chat merely mentioning translation; ANY case involving another person speaking to the user " +
            "(that is INTERPRET).\n" +
            "\n" +
            "<INTERPRET src=\"XX\" tgt=\"YY\">  — 2-letter codes.\n" +
            "  MUST emit when user wants real-time face-to-face interpretation with another person " +
            "(\"일본인과 대화해야 해\", \"베트남 사람이랑 실시간으로 대화\", \"현지인과 말해야 해\", " +
            "\"통역 모드 켜줘\", \"talk with a Vietnamese person\", \"start interpreter\"). " +
            "Reply with one short sentence (\"통역 모드를 켤게요.\") then the tag. " +
            "Lang inference: src = user's UI language; tgt = the other person's language " +
            "(베트남 사람=vi, 일본인=ja, 중국인=zh, 미국인=en, 스페인 사람=es).\n" +
            "\n" +
            "MENU OCR: when the user message contains a [MENU OCR] block, list each item as " +
            "\"original · translation · 1-line note\" in plain prose. NO tag."

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
