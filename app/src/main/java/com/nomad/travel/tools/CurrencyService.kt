package com.nomad.travel.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CurrencyService {

    data class Result(
        val amount: Double,
        val fromCode: String,
        val toCode: String,
        val convertedAmount: Double,
        val rate: Double,
        val source: Source,
        val symbol: String? = null,
        val countryName: String? = null
    )

    enum class Source { LIVE_API, ESTIMATED }

    suspend fun convertLive(
        amount: Double,
        from: String,
        to: String
    ): Result? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                "$BASE_URL/api/money/exchange" +
                    "?baseAmount=" + URLEncoder.encode(amount.toString(), "UTF-8") +
                    "&baseCurrencyCode=" + URLEncoder.encode(from.uppercase(), "UTF-8")
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 8000
                setRequestProperty("X-API-Key", API_KEY)
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val payload = JSONObject(body).optJSONObject("payload") ?: return@runCatching null
                val countries = payload.optJSONArray("countries") ?: return@runCatching null
                val target = to.uppercase()
                for (i in 0 until countries.length()) {
                    val row = countries.getJSONObject(i)
                    if (row.optString("currencyCode").uppercase() != target) continue
                    val converted = row.optDouble("amount", Double.NaN)
                    if (converted.isNaN()) return@runCatching null
                    val rate = if (amount != 0.0) converted / amount else 0.0
                    return@runCatching Result(
                        amount = amount,
                        fromCode = from.uppercase(),
                        toCode = target,
                        convertedAmount = converted,
                        rate = rate,
                        source = Source.LIVE_API,
                        symbol = row.optString("symbol").ifBlank { null },
                        countryName = row.optString("countryName").ifBlank { null }
                    )
                }
                null
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    fun convertEstimated(
        amount: Double,
        from: String,
        to: String
    ): Result {
        val fromUsd = USD_RATES[from.uppercase()] ?: 1.0
        val toUsd = USD_RATES[to.uppercase()] ?: 1.0
        val rate = toUsd / fromUsd
        return Result(
            amount = amount,
            fromCode = from.uppercase(),
            toCode = to.uppercase(),
            convertedAmount = amount * rate,
            rate = rate,
            source = Source.ESTIMATED,
            symbol = SYMBOLS[to.uppercase()]
        )
    }

    companion object {
        private const val BASE_URL = "https://apisis.dev"
        private const val API_KEY = "e_C4dA0zNp2mFr42GRGQwsNw_jODLY0w_GVmnSGjt14"

        // Rough "units of currency per 1 USD" — offline estimates, updated 2026-04.
        private val USD_RATES = mapOf(
            "USD" to 1.0,
            "KRW" to 1350.0,
            "JPY" to 150.0,
            "EUR" to 0.92,
            "GBP" to 0.79,
            "CNY" to 7.2,
            "TWD" to 31.5,
            "HKD" to 7.8,
            "THB" to 35.0,
            "VND" to 24500.0,
            "SGD" to 1.34,
            "MYR" to 4.7,
            "PHP" to 56.0,
            "IDR" to 15800.0,
            "INR" to 83.0,
            "AUD" to 1.52,
            "CAD" to 1.36,
            "CHF" to 0.90,
            "NZD" to 1.64,
            "MXN" to 17.0
        )

        private val SYMBOLS = mapOf(
            "USD" to "$",
            "KRW" to "₩",
            "JPY" to "¥",
            "EUR" to "€",
            "GBP" to "£",
            "CNY" to "¥",
            "HKD" to "HK$",
            "TWD" to "NT$",
            "THB" to "฿",
            "VND" to "₫",
            "SGD" to "S$",
            "MYR" to "RM",
            "PHP" to "₱",
            "IDR" to "Rp",
            "INR" to "₹",
            "AUD" to "A$",
            "CAD" to "C$",
            "CHF" to "CHF",
            "NZD" to "NZ$",
            "MXN" to "Mex$"
        )
    }
}
