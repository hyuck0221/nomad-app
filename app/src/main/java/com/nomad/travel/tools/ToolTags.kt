package com.nomad.travel.tools

/**
 * Tool-call tags the on-device model is taught to emit inline with its
 * response. The router parses these out of the raw stream so they never
 * surface to the user as visible text.
 */
object ToolTags {

    private val ATTR = Regex("(\\w+)=\"([^\"]*)\"")
    private val CURRENCY = Regex("<CURRENCY\\b[^>]*>")
    private val ASK = Regex("<ASK\\b[^>]*>")

    // Strip ANY XML-ish tag starting with an uppercase letter (EXPENSE, CURRENCY,
    // ASK, and also hallucinated tags like TEXT, END, RESPONSE that the model
    // sometimes emits because we taught it tag syntax). Closing form `</NAME>` too.
    private val ANY_TAG_CLOSED = Regex("</?[A-Z][A-Z0-9_]*\\b[^>]*>")
    private val ANY_TAG_OPEN_TAIL = Regex("</?[A-Z][A-Z0-9_]*\\b[^>]*$")

    data class CurrencyCall(
        val amount: Double,
        val from: String,
        val to: String
    )

    data class AskCall(
        val id: String,
        val prompt: String,
        val options: List<String>
    )

    fun parseAttrs(tag: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        ATTR.findAll(tag).forEach { m -> out[m.groupValues[1]] = m.groupValues[2] }
        return out
    }

    fun extractCurrency(raw: String): CurrencyCall? {
        val match = CURRENCY.find(raw) ?: return null
        val a = parseAttrs(match.value)
        val amount = a["amount"]?.toDoubleOrNull() ?: return null
        val from = a["from"]?.takeIf { it.isNotBlank() }?.uppercase() ?: return null
        val to = a["to"]?.takeIf { it.isNotBlank() }?.uppercase() ?: return null
        return CurrencyCall(amount, from, to)
    }

    fun extractAsk(raw: String): AskCall? {
        val match = ASK.find(raw) ?: return null
        val a = parseAttrs(match.value)
        val prompt = a["prompt"]?.takeIf { it.isNotBlank() } ?: return null
        val opts = (a["options"] ?: "").split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (opts.size < 2) return null
        val id = a["id"].orEmpty().ifBlank { "ask_${System.currentTimeMillis()}" }
        return AskCall(id, prompt, opts)
    }

    /** Remove any uppercase-named XML-ish tag (final pass). */
    fun stripAll(raw: String): String = ANY_TAG_CLOSED.replace(raw, "").trim()

    /** Remove uppercase tags AND any half-written tag at the tail of a
     *  partially streamed chunk so the user never sees raw XML mid-stream. */
    fun stripForStream(raw: String): String =
        ANY_TAG_OPEN_TAIL.replace(ANY_TAG_CLOSED.replace(raw, ""), "").trimEnd()
}
