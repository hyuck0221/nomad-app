package com.nomad.travel.data

import android.content.Context
import com.nomad.travel.data.expense.ExpenseDatabase
import com.nomad.travel.data.expense.ExpenseRepository
import com.nomad.travel.llm.DeviceCapability
import com.nomad.travel.llm.GemmaEngine
import com.nomad.travel.llm.ModelDownloader
import com.nomad.travel.ocr.OcrService
import com.nomad.travel.tools.ToolRouter

interface AppContainer {
    val gemma: GemmaEngine
    val ocr: OcrService
    val expenses: ExpenseRepository
    val toolRouter: ToolRouter
    val prefs: UserPrefs
    val downloader: ModelDownloader
    val chatHistory: ChatHistory
    val device: DeviceCapability
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    override val prefs: UserPrefs by lazy { UserPrefs(appContext) }
    override val device: DeviceCapability by lazy { DeviceCapability(appContext) }
    override val gemma: GemmaEngine by lazy { GemmaEngine(appContext, prefs, device) }
    override val ocr: OcrService by lazy { OcrService() }
    override val downloader: ModelDownloader by lazy { ModelDownloader(appContext) }
    override val chatHistory: ChatHistory by lazy { ChatHistory() }

    override val expenses: ExpenseRepository by lazy {
        ExpenseRepository(ExpenseDatabase.get(appContext).expenseDao())
    }

    override val toolRouter: ToolRouter by lazy {
        ToolRouter(gemma = gemma, ocr = ocr, expenses = expenses, prefs = prefs)
    }
}
