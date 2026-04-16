package com.nomad.travel.data

import android.content.Context
import com.nomad.travel.data.chat.ChatDatabase
import com.nomad.travel.data.chat.ChatRepository
import com.nomad.travel.data.expense.ExpenseDatabase
import com.nomad.travel.data.expense.ExpenseRepository
import com.nomad.travel.llm.DeviceCapability
import com.nomad.travel.llm.GemmaEngine
import com.nomad.travel.llm.ModelDownloader
import com.nomad.travel.ocr.OcrService
import com.nomad.travel.tools.CurrencyService
import com.nomad.travel.tools.ToolRouter
import com.nomad.travel.update.UpdateChecker
import com.nomad.travel.update.UpdateManager

interface AppContainer {
    val gemma: GemmaEngine
    val ocr: OcrService
    val expenses: ExpenseRepository
    val toolRouter: ToolRouter
    val currencyService: CurrencyService
    val prefs: UserPrefs
    val downloader: ModelDownloader
    val chatRepository: ChatRepository
    val device: DeviceCapability
    val updateManager: UpdateManager
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    override val prefs: UserPrefs by lazy { UserPrefs(appContext) }
    override val device: DeviceCapability by lazy { DeviceCapability(appContext) }
    override val gemma: GemmaEngine by lazy { GemmaEngine(appContext, prefs, device) }
    override val ocr: OcrService by lazy { OcrService() }
    override val downloader: ModelDownloader by lazy { ModelDownloader(appContext) }
    override val currencyService: CurrencyService by lazy { CurrencyService() }

    override val updateManager: UpdateManager by lazy {
        UpdateManager(
            context = appContext,
            checker = UpdateChecker(com.nomad.travel.BuildConfig.GITHUB_REPO)
        )
    }

    override val chatRepository: ChatRepository by lazy {
        val db = ChatDatabase.get(appContext)
        ChatRepository(db.sessionDao(), db.messageDao())
    }

    override val expenses: ExpenseRepository by lazy {
        ExpenseRepository(ExpenseDatabase.get(appContext).expenseDao())
    }

    override val toolRouter: ToolRouter by lazy {
        ToolRouter(gemma = gemma, ocr = ocr, expenses = expenses, prefs = prefs)
    }
}
