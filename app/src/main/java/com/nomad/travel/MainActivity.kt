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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nomad.travel.ui.chat.ChatScreen
import com.nomad.travel.ui.menu.MenuSplitScreen
import com.nomad.travel.ui.onboarding.LanguageScreen
import com.nomad.travel.ui.settings.SettingsScreen
import com.nomad.travel.ui.setup.ModelSetupScreen
import com.nomad.travel.ui.theme.NomadTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale

private enum class Destination { LANGUAGE, SETUP, CHAT, SETTINGS, MENU_VIEW }

private data class MenuViewArgs(val uri: Uri, val text: String)

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val app = newBase.applicationContext as NomadApp
        val code = runBlocking { app.container.prefs.languageBlocking() } ?: "ko"
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as NomadApp
        val prefs = app.container.prefs
        val gemma = app.container.gemma

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

                CompositionLocalProvider(
                    LocalContext provides localizedContext,
                    LocalActivityResultRegistryOwner provides activity,
                    LocalOnBackPressedDispatcherOwner provides activity
                ) {
                    var destination by remember { mutableStateOf(initial) }
                    var menuArgs by remember { mutableStateOf<MenuViewArgs?>(null) }
                    val scope = rememberCoroutineScope()

                    BackHandler(
                        enabled = destination == Destination.SETTINGS ||
                            destination == Destination.MENU_VIEW
                    ) {
                        destination = Destination.CHAT
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
                                }
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
                        }
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
