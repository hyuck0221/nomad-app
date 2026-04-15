package com.nomad.travel.tools

import android.content.Context
import android.net.Uri
import com.nomad.travel.data.UserPrefs
import com.nomad.travel.data.expense.Expense
import com.nomad.travel.data.expense.ExpenseRepository
import com.nomad.travel.llm.GemmaEngine
import com.nomad.travel.ocr.OcrService

class ToolRouter(
    private val gemma: GemmaEngine,
    private val ocr: OcrService,
    private val expenses: ExpenseRepository,
    private val prefs: UserPrefs
) {

    data class Turn(
        val userText: String,
        val imageUri: Uri? = null,
        val uiLanguage: String = "ko"
    )

    data class Reply(
        val visibleText: String,
        val toolTag: String? = null
    )

    suspend fun handle(context: Context, turn: Turn): Reply {
        if (!gemma.ensureLoaded()) {
            return Reply(visibleText = FALLBACK_NO_MODEL, toolTag = "error")
        }

        val ocrBlock: String? = turn.imageUri?.let { uri ->
            runCatching { ocr.recognize(context, uri) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }

        val tag = when {
            ocrBlock != null -> "menu_translate"
            looksLikeExpense(turn.userText) -> "expense"
            looksLikeMenuSearch(turn.userText) -> "menu_search"
            else -> "travel"
        }

        val customPrompt = prefs.systemPromptBlocking()?.takeIf { it.isNotBlank() }

        val built = Prompt.build(
            uiLanguage = turn.uiLanguage,
            userText = turn.userText,
            ocrBlock = ocrBlock,
            customSystemPrompt = customPrompt
        )

        val raw = gemma.generate(
            systemInstruction = built.systemInstruction,
            userMessage = built.userMessage
        )

        val (visible, executed) = postProcess(raw)
        return Reply(visibleText = visible.ifBlank { raw }, toolTag = executed ?: tag)
    }

    private suspend fun postProcess(raw: String): Pair<String, String?> {
        val match = EXPENSE_TAG.find(raw) ?: return raw to null
        val attrs = parseAttrs(match.value)
        val amount = attrs["amount"]?.toDoubleOrNull() ?: return raw to null
        val currency = attrs["currency"]?.uppercase() ?: "USD"
        val category = attrs["category"] ?: "misc"
        val note = attrs["note"].orEmpty()
        expenses.add(Expense(amount = amount, currency = currency, category = category, note = note))
        val cleaned = raw.replace(match.value, "").trim()
        return cleaned to "expense"
    }

    private fun parseAttrs(tag: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        ATTR.findAll(tag).forEach { m ->
            out[m.groupValues[1]] = m.groupValues[2]
        }
        return out
    }

    private fun looksLikeExpense(text: String): Boolean {
        val lower = text.lowercase()
        return EXPENSE_HINTS.any { lower.contains(it) }
    }

    private fun looksLikeMenuSearch(text: String): Boolean {
        val lower = text.lowercase()
        return MENU_HINTS.any { lower.contains(it) }
    }

    companion object {
        private val EXPENSE_TAG = Regex("<EXPENSE[^>]*>")
        private val ATTR = Regex("(\\w+)=\"([^\"]*)\"")
        private val EXPENSE_HINTS = listOf(
            "지출", "썼어", "결제", "샀어", "환율",
            "spent", "paid", "bought", "expense",
            "花了", "支出",
            "使った", "払った", "支出"
        )
        private val MENU_HINTS = listOf(
            "메뉴", "음식", "요리",
            "menu", "dish", "food",
            "菜", "料理",
            "メニュー", "料理"
        )
        private const val FALLBACK_NO_MODEL =
            "온디바이스 모델이 아직 준비되지 않았습니다. 설정에서 Gemma 모델 파일을 추가해주세요."
    }
}
