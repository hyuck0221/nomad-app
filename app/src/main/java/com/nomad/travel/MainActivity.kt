package com.nomad.travel

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.nomad.travel.R
import com.nomad.travel.ui.chat.ChatScreen
import com.nomad.travel.ui.menu.MenuSplitScreen
import com.nomad.travel.ui.onboarding.LanguageScreen
import com.nomad.travel.ui.settings.SettingsScreen
import com.nomad.travel.ui.setup.ModelSetupScreen
import com.nomad.travel.ui.theme.NomadTheme
import com.nomad.travel.ui.translate.FullscreenTextOverlay
import com.nomad.travel.ui.translate.InterpretScreen
import com.nomad.travel.ui.translate.TranslateScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale

private enum class Destination { LANGUAGE, SETUP, CHAT, SETTINGS, MENU_VIEW, TRANSLATE, INTERPRET }

private data class MenuViewArgs(val uri: Uri, val text: String)

class MainActivity : ComponentActivity() {

    private var attachedLanguage: String = "ko"

    override fun attachBaseContext(newBase: Context) {
        val app = newBase.applicationContext as NomadApp
        val code = runBlocking { app.container.prefs.languageBlocking() } ?: "ko"
        attachedLanguage = code
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onResume() {
        super.onResume()
        val app = application as NomadApp
        app.container.updateManager.registerListener()
        lifecycleScope.launch { app.container.updateManager.refreshOnResume() }
    }

    override fun onPause() {
        super.onPause()
        val app = application as NomadApp
        app.container.updateManager.unregisterListener()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as NomadApp
        val prefs = app.container.prefs
        val gemma = app.container.gemma
        val updateManager = app.container.updateManager

        lifecycleScope.launch {
            if (prefs.autoUpdateCheckBlocking()) {
                updateManager.checkForUpdate()
            }
        }

        val initial: Destination = runBlocking {
            val lang = prefs.languageBlocking()
            when {
                lang == null -> Destination.LANGUAGE
                !gemma.isActiveReady() -> Destination.SETUP
                else -> Destination.CHAT
            }
        }

        setContent {
            NomadTheme {
                val langPref by prefs.language.collectAsStateWithLifecycle(initialValue = null)
                val activityContext = LocalContext.current
                val activity = this@MainActivity
                val effectiveLanguage = langPref ?: "ko"
                val localizedContext = remember(effectiveLanguage) {
                    val locale = Locale(effectiveLanguage)
                    Locale.setDefault(locale)
                    val cfg = Configuration(activityContext.resources.configuration)
                    cfg.setLocale(locale)
                    activityContext.createConfigurationContext(cfg)
                }

                LaunchedEffect(langPref) {
                    val newLang = langPref ?: return@LaunchedEffect
                    if (newLang != attachedLanguage) {
                        activity.recreate()
                    }
                }

                CompositionLocalProvider(
                    LocalContext provides localizedContext,
                    LocalActivityResultRegistryOwner provides activity,
                    LocalOnBackPressedDispatcherOwner provides activity
                ) {
                    val destinationSaver = remember {
                        Saver<Destination, String>(
                            save = { it.name },
                            restore = { runCatching { Destination.valueOf(it) }.getOrNull() ?: initial }
                        )
                    }
                    var destination by rememberSaveable(stateSaver = destinationSaver) {
                        mutableStateOf(initial)
                    }
                    var menuArgs by remember { mutableStateOf<MenuViewArgs?>(null) }
                    val scope = rememberCoroutineScope()

                    var fullscreenText by remember { mutableStateOf<String?>(null) }
                    var showExitConfirm by remember { mutableStateOf(false) }

                    BackHandler(
                        enabled = destination == Destination.SETTINGS ||
                            destination == Destination.MENU_VIEW ||
                            destination == Destination.TRANSLATE ||
                            destination == Destination.INTERPRET
                    ) {
                        destination = Destination.CHAT
                    }

                    BackHandler(enabled = destination == Destination.CHAT) {
                        showExitConfirm = true
                    }

                    if (showExitConfirm) {
                        AlertDialog(
                            onDismissRequest = { showExitConfirm = false },
                            title = { Text(stringResource(R.string.exit_confirm_title)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showExitConfirm = false
                                    activity.finish()
                                }) { Text(stringResource(R.string.common_yes)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showExitConfirm = false }) {
                                    Text(stringResource(R.string.common_no))
                                }
                            }
                        )
                    }

                    AnimatedContent(
                        targetState = destination,
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                        },
                        label = "nav"
                    ) { dest ->
                        when (dest) {
                            Destination.LANGUAGE -> LanguageScreen(
                                onContinue = { code ->
                                    scope.launch {
                                        prefs.setLanguage(code)
                                        destination = if (gemma.isActiveReady())
                                            Destination.CHAT else Destination.SETUP
                                    }
                                }
                            )
                            Destination.SETUP -> ModelSetupScreen(
                                onReady = { destination = Destination.CHAT }
                            )
                            Destination.CHAT -> ChatScreen(
                                onOpenSettings = { destination = Destination.SETTINGS },
                                onOpenMenuView = { uri, text ->
                                    menuArgs = MenuViewArgs(uri, text)
                                    destination = Destination.MENU_VIEW
                                },
                                onOpenTranslate = { destination = Destination.TRANSLATE },
                                onOpenInterpret = { destination = Destination.INTERPRET }
                            )
                            Destination.SETTINGS -> SettingsScreen(
                                onBack = { destination = Destination.CHAT }
                            )
                            Destination.MENU_VIEW -> {
                                val args = menuArgs
                                if (args == null) {
                                    destination = Destination.CHAT
                                } else {
                                    MenuSplitScreen(
                                        imageUri = args.uri,
                                        text = args.text,
                                        onBack = { destination = Destination.CHAT }
                                    )
                                }
                            }
                            Destination.TRANSLATE -> TranslateScreen(
                                onBack = { destination = Destination.CHAT },
                                onFullscreen = { text -> fullscreenText = text }
                            )
                            Destination.INTERPRET -> InterpretScreen(
                                onBack = { destination = Destination.CHAT }
                            )
                        }
                    }

                    // Fullscreen text overlay for translation
                    fullscreenText?.let { text ->
                        FullscreenTextOverlay(
                            text = text,
                            onDismiss = { fullscreenText = null }
                        )
                    }

                    LaunchedEffect(destination) {
                        if (destination == Destination.CHAT && !gemma.isActiveReady()) {
                            destination = Destination.SETUP
                        }
                    }
                }
            }
        }
    }
}
