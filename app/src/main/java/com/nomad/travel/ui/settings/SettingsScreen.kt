package com.nomad.travel.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nomad.travel.R
import com.nomad.travel.llm.ModelCatalog
import com.nomad.travel.llm.ModelEntry
import com.nomad.travel.tools.ContextStrategy
import com.nomad.travel.tts.SystemTtsEngine
import com.nomad.travel.tts.TtsModelCatalog
import com.nomad.travel.ui.setup.ModelCard
import com.nomad.travel.ui.theme.NomadGlow
import com.nomad.travel.ui.theme.NomadInputField
import com.nomad.travel.ui.theme.NomadMist
import com.nomad.travel.ui.theme.NomadMuted
import com.nomad.travel.ui.theme.NomadRoyal
import com.nomad.travel.ui.theme.NomadSilver
import com.nomad.travel.update.UpdateState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.first

private const val PRIVACY_POLICY_URL = "https://nomad-ai-android.github.io/privacy.html"
private const val TERMS_OF_SERVICE_URL = "https://nomad-ai-android.github.io/terms.html"

private data class LangOption(val code: String, val label: String, val flag: String)

private enum class UpgradeDialogTarget {
    Text,
    Voice
}

private val LANGS = listOf(
    LangOption("ko", "한국어", "🇰🇷"),
    LangOption("en", "English", "🇺🇸"),
    LangOption("zh", "中文", "🇨🇳"),
    LangOption("ja", "日本語", "🇯🇵")
)

private fun languageLabelFor(code: String): String =
    LANGS.firstOrNull { it.code == code }?.label ?: code.uppercase()

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    scrollToCameraSection: Boolean = false,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var promptDraft by remember { mutableStateOf(state.systemPrompt) }
    LaunchedEffect(state.systemPrompt) { promptDraft = state.systemPrompt }

    var confirmClear by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ModelEntry?>(null) }
    var pendingTtsDelete by remember { mutableStateOf<ModelEntry?>(null) }
    var pendingTtsLanguageChange by remember { mutableStateOf<ModelEntry?>(null) }
    var pendingCancel by remember { mutableStateOf<ModelEntry?>(null) }
    var languageExpanded by rememberSaveable { mutableStateOf(false) }
    var upgradeDialogTarget by rememberSaveable { mutableStateOf<UpgradeDialogTarget?>(null) }
    var contextExpanded by rememberSaveable { mutableStateOf(false) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }

    if (showLicenses) {
        LicensesScreen(onBack = { showLicenses = false })
        return
    }

    val updateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { /* Play handles result visually; state flows come from InstallStateUpdatedListener. */ }

    val scrollState = rememberScrollState()
    var cameraSectionY by remember { mutableStateOf(-1) }
    LaunchedEffect(scrollToCameraSection) {
        if (scrollToCameraSection) {
            val y = androidx.compose.runtime.snapshotFlow { cameraSectionY }
                .first { it >= 0 }
            scrollState.animateScrollTo(y)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        SettingsTopBar(onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ─── Language ─────────────────────────
            Section(
                title = stringResource(R.string.settings_language),
                icon = Icons.Default.Language
            ) {
                val current = LANGS.firstOrNull { it.code == state.language } ?: LANGS[0]
                CollapsibleRow(
                    leading = {
                        Text(current.flag, fontSize = 22.sp)
                        Spacer(Modifier.size(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_language_change),
                                style = MaterialTheme.typography.labelSmall.copy(color = NomadMuted)
                            )
                            Text(
                                text = current.label,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    expanded = languageExpanded,
                    onToggle = { languageExpanded = !languageExpanded }
                )
                AnimatedVisibility(visible = languageExpanded) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        LANGS.forEach { opt ->
                            LanguageRow(
                                option = opt,
                                selected = state.language == opt.code,
                                onClick = {
                                    if (state.language != opt.code) {
                                        vm.setLanguage(opt.code)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ─── System prompt ────────────────────
            Section(
                title = stringResource(R.string.settings_system_prompt),
                icon = Icons.Default.Tune
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(NomadInputField)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        if (promptDraft.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_system_prompt_hint),
                                style = TextStyle(
                                    color = NomadMuted,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            )
                        }
                        BasicTextField(
                            value = promptDraft,
                            onValueChange = { promptDraft = it },
                            textStyle = TextStyle(
                                color = NomadSilver,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            ),
                            cursorBrush = SolidColor(NomadGlow),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(stringResource(R.string.settings_prompt_clear)) {
                            promptDraft = ""
                            vm.resetSystemPrompt()
                        }
                        PrimaryButton(stringResource(R.string.settings_prompt_save)) {
                            vm.setSystemPrompt(promptDraft)
                        }
                    }
                }
            }

            // ─── Context strategy ─────────────────
            Section(
                title = stringResource(R.string.settings_context_title),
                icon = Icons.Default.Memory
            ) {
                val currentLabel = when (state.contextStrategy) {
                    ContextStrategy.RESET -> stringResource(R.string.settings_context_reset)
                    else -> stringResource(R.string.settings_context_drop_oldest)
                }
                CollapsibleRow(
                    leading = {
                        Column {
                            Text(
                                text = stringResource(R.string.settings_context_change),
                                style = MaterialTheme.typography.labelSmall.copy(color = NomadMuted)
                            )
                            Text(
                                text = currentLabel,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    expanded = contextExpanded,
                    onToggle = { contextExpanded = !contextExpanded }
                )
                AnimatedVisibility(visible = contextExpanded) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        ContextStrategyRow(
                            title = stringResource(R.string.settings_context_drop_oldest),
                            body = stringResource(R.string.settings_context_drop_oldest_body),
                            selected = state.contextStrategy == ContextStrategy.DROP_OLDEST,
                            onClick = { vm.setContextStrategy(ContextStrategy.DROP_OLDEST) }
                        )
                        ContextStrategyRow(
                            title = stringResource(R.string.settings_context_reset),
                            body = stringResource(R.string.settings_context_reset_body),
                            selected = state.contextStrategy == ContextStrategy.RESET,
                            onClick = { vm.setContextStrategy(ContextStrategy.RESET) }
                        )
                    }
                }
            }

            // ─── Model ────────────────────────────
            Section(
                title = stringResource(R.string.settings_model_management),
                icon = Icons.Default.SmartToy
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_model_llm_group),
                        style = MaterialTheme.typography.labelMedium.copy(color = NomadMuted),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    state.modelRows
                        .filter { it.entry.id == ModelCatalog.recommended.id }
                        .forEach { row ->
                            ModelCard(
                                row = row,
                                active = row.entry.id == state.activeModelId && row.downloaded,
                                onSelect = { vm.selectModel(row.entry) },
                                onDownload = { vm.startDownload(row.entry) },
                                onCancel = { pendingCancel = row.entry },
                                onDelete = { pendingDelete = row.entry }
                            )
                        }
                    state.modelRows
                        .filter {
                            it.entry.id != ModelCatalog.recommended.id &&
                                it.entry.id == state.activeModelId &&
                                it.downloaded
                        }
                        .forEach { row ->
                            ModelCard(
                                row = row,
                                active = true,
                                onSelect = { vm.selectModel(row.entry) },
                                onDownload = { vm.startDownload(row.entry) },
                                onCancel = { pendingCancel = row.entry },
                                onDelete = { pendingDelete = row.entry }
                            )
                        }
                    UpgradeButton(onClick = { upgradeDialogTarget = UpgradeDialogTarget.Text })

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_model_tts_group),
                        style = MaterialTheme.typography.labelMedium.copy(color = NomadMuted),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    val selectedTtsDownloaded = state.ttsModelRows.firstOrNull {
                        it.entry.id == state.activeTtsModelId
                    }?.downloaded == true
                    val upgradedTtsSelected = state.ttsEngineId != SystemTtsEngine.ID &&
                        selectedTtsDownloaded
                    TtsEngineRow(
                        title = stringResource(R.string.settings_model_internal_tts_name),
                        body = stringResource(R.string.settings_model_internal_tts_body),
                        selected = !upgradedTtsSelected,
                        enabled = true,
                        onClick = { vm.setTtsEngine(SystemTtsEngine.ID) }
                    )
                    state.ttsModelRows
                        .filter {
                            upgradedTtsSelected &&
                                it.entry.id == state.activeTtsModelId &&
                                it.downloaded
                        }
                        .forEach { row ->
                            ModelCard(
                                row = row,
                                active = true,
                                onSelect = {
                                    val targetLanguage = TtsModelCatalog.languageFor(row.entry)
                                    if (targetLanguage != null && targetLanguage != state.language) {
                                        pendingTtsLanguageChange = row.entry
                                    } else {
                                        vm.selectTtsModel(row.entry)
                                    }
                                },
                                onDownload = { vm.startTtsDownload(row.entry) },
                                onCancel = { vm.cancelTtsDownload(row.entry) },
                                onDelete = { pendingTtsDelete = row.entry }
                            )
                        }
                    UpgradeButton(onClick = { upgradeDialogTarget = UpgradeDialogTarget.Voice })
                }
            }

            // ─── Voice / TTS ──────────────────────
            Section(
                title = stringResource(R.string.settings_tts_section),
                icon = Icons.Default.GraphicEq
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val supertonicSelected = state.ttsEngineId != SystemTtsEngine.ID &&
                        state.activeTtsModelId == TtsModelCatalog.supertonic3.id
                    AnimatedVisibility(visible = supertonicSelected) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_tts_voice_preset),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NomadSilver
                            )
                            Text(
                                text = stringResource(R.string.settings_tts_voice_preset_body),
                                style = MaterialTheme.typography.labelSmall.copy(color = NomadMuted)
                            )
                            TtsModelCatalog.voicePresets.chunked(5).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    row.forEach { preset ->
                                        VoicePresetButton(
                                            label = preset,
                                            selected = state.ttsVoicePreset == preset,
                                            onClick = { vm.setTtsVoicePreset(preset) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            SecondaryButton(
                                label = stringResource(R.string.settings_tts_voice_preview),
                                loadingLabel = stringResource(R.string.settings_tts_voice_preview_loading),
                                loading = state.ttsVoicePreviewLoading,
                                enabled = !state.ttsVoicePreviewLoading,
                                onClick = { vm.previewTtsVoice() }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_voice_loop),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NomadSilver
                            )
                            Spacer(Modifier.size(2.dp))
                            Text(
                                text = stringResource(R.string.settings_voice_loop_body),
                                style = MaterialTheme.typography.labelSmall.copy(color = NomadMuted)
                            )
                        }
                        Switch(
                            checked = state.voiceLoopEnabled,
                            onCheckedChange = { vm.setVoiceLoopEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NomadGlow,
                                checkedTrackColor = NomadRoyal,
                                uncheckedThumbColor = NomadMuted,
                                uncheckedTrackColor = NomadMuted.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }

            // ─── Camera search ────────────────────
            Box(
                modifier = Modifier.onGloballyPositioned { coords ->
                    cameraSectionY = coords.boundsInParent().top.toInt()
                }
            ) {
                Section(
                    title = stringResource(R.string.settings_camera_section),
                    icon = Icons.Default.CameraAlt
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_camera_instant_preview),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NomadSilver
                                )
                                Spacer(Modifier.size(2.dp))
                                Text(
                                    text = stringResource(R.string.settings_camera_instant_preview_body),
                                    style = MaterialTheme.typography.labelSmall.copy(color = NomadMuted)
                                )
                            }
                            Switch(
                                checked = state.cameraInstantPreview,
                                onCheckedChange = { vm.setCameraInstantPreview(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NomadGlow,
                                    checkedTrackColor = NomadRoyal,
                                    uncheckedThumbColor = NomadMuted,
                                    uncheckedTrackColor = NomadMuted.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }
            }

            // ─── App version & update ────────────
            Section(
                title = stringResource(R.string.settings_app_version),
                icon = Icons.Default.SystemUpdate
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_auto_update_check),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NomadSilver
                    )
                    Switch(
                        checked = state.autoUpdateCheck,
                        onCheckedChange = { vm.setAutoUpdateCheck(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NomadGlow,
                            checkedTrackColor = NomadRoyal,
                            uncheckedThumbColor = NomadMuted,
                            uncheckedTrackColor = NomadMuted.copy(alpha = 0.3f)
                        )
                    )
                }
                UpdateSection(
                    state = state.updateState,
                    currentVersion = state.currentVersion,
                    onCheck = { vm.checkForUpdate() },
                    onStart = { vm.startUpdate(updateLauncher) },
                    onInstall = { vm.installUpdate() },
                    onDismiss = { vm.dismissUpdateState() }
                )
            }

            // ─── Danger zone ──────────────────────
            Section(
                title = stringResource(R.string.settings_chat_section),
                icon = Icons.Default.Chat
            ) {
                DangerButton(
                    label = stringResource(R.string.settings_clear_chats),
                    onClick = { confirmClear = true }
                )
            }

            // ─── Legal (always last) ───────────────
            Section(
                title = stringResource(R.string.settings_legal_section),
                icon = Icons.Default.Gavel
            ) {
                val uriHandler = LocalUriHandler.current
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegalRow(
                        label = stringResource(R.string.settings_privacy_policy),
                        onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }
                    )
                    LegalRow(
                        label = stringResource(R.string.settings_terms_of_service),
                        onClick = { uriHandler.openUri(TERMS_OF_SERVICE_URL) }
                    )
                    Spacer(Modifier.height(4.dp))
                    InternalNavRow(
                        label = stringResource(R.string.settings_open_source_licenses),
                        onClick = { showLicenses = true }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearChats()
                    confirmClear = false
                }) {
                    Text(
                        stringResource(R.string.settings_clear_confirm_ok),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            containerColor = NomadInputField,
            titleContentColor = NomadSilver,
            textContentColor = NomadMist
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.settings_delete_model_title)) },
            text = {
                Text(
                    text = entry.displayName + "\n\n" +
                        stringResource(R.string.settings_delete_model_body)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteModel(entry)
                    pendingDelete = null
                }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            containerColor = NomadInputField,
            titleContentColor = NomadSilver,
            textContentColor = NomadMist
        )
    }

    pendingTtsDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingTtsDelete = null },
            title = { Text(stringResource(R.string.settings_tts_delete_title)) },
            text = {
                val entryName = if (entry.displayNameResId != 0)
                    stringResource(entry.displayNameResId)
                else entry.displayName
                Text(
                    text = entryName + "\n\n" +
                        stringResource(R.string.settings_tts_delete_body)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTtsModel(entry)
                    pendingTtsDelete = null
                }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTtsDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            containerColor = NomadInputField,
            titleContentColor = NomadSilver,
            textContentColor = NomadMist
        )
    }

    pendingCancel?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingCancel = null },
            title = { Text(stringResource(R.string.model_cancel_confirm_title)) },
            text = { Text(stringResource(R.string.model_cancel_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.cancelDownload(entry)
                    pendingCancel = null
                }) { Text(stringResource(R.string.common_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancel = null }) {
                    Text(stringResource(R.string.common_no))
                }
            },
            containerColor = NomadInputField,
            titleContentColor = NomadSilver,
            textContentColor = NomadMist
        )
    }

    upgradeDialogTarget?.let { target ->
        val textUpgradeRow = state.modelRows.firstOrNull { it.entry.id == ModelCatalog.gemma4E4B.id }
        AlertDialog(
            onDismissRequest = { upgradeDialogTarget = null },
            title = { Text(stringResource(R.string.setup_upgrade_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(
                            if (target == UpgradeDialogTarget.Voice) {
                                R.string.settings_voice_model_upgrade_desc
                            } else {
                                R.string.setup_upgrade_desc
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(color = NomadMist)
                    )
                    when (target) {
                        UpgradeDialogTarget.Text -> {
                            textUpgradeRow?.let { row ->
                                ModelCard(
                                    row = row,
                                    active = row.entry.id == state.activeModelId && row.downloaded,
                                    onSelect = { vm.selectModel(row.entry) },
                                    onDownload = { vm.startDownload(row.entry) },
                                    onCancel = {
                                        upgradeDialogTarget = null
                                        pendingCancel = row.entry
                                    },
                                    onDelete = {
                                        upgradeDialogTarget = null
                                        pendingDelete = row.entry
                                    }
                                )
                            }
                        }

                        UpgradeDialogTarget.Voice -> {
                            state.ttsModelRows.forEach { row ->
                                ModelCard(
                                    row = row,
                                    active = state.ttsEngineId != SystemTtsEngine.ID &&
                                        row.entry.id == state.activeTtsModelId &&
                                        row.downloaded,
                                    onSelect = {
                                        val targetLanguage = TtsModelCatalog.languageFor(row.entry)
                                        if (targetLanguage != null && targetLanguage != state.language) {
                                            pendingTtsLanguageChange = row.entry
                                        } else {
                                            vm.selectTtsModel(row.entry)
                                        }
                                    },
                                    onDownload = { vm.startTtsDownload(row.entry) },
                                    onCancel = { vm.cancelTtsDownload(row.entry) },
                                    onDelete = {
                                        upgradeDialogTarget = null
                                        pendingTtsDelete = row.entry
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { upgradeDialogTarget = null }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            containerColor = NomadInputField,
            titleContentColor = NomadSilver,
            textContentColor = NomadMist
        )
    }

    pendingTtsLanguageChange?.let { entry ->
        val targetLanguage = TtsModelCatalog.languageFor(entry).orEmpty()
        AlertDialog(
            onDismissRequest = { pendingTtsLanguageChange = null },
            title = { Text(stringResource(R.string.settings_tts_language_change_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.settings_tts_language_change_body,
                        languageLabelFor(targetLanguage)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.selectTtsModel(entry)
                    pendingTtsLanguageChange = null
                    upgradeDialogTarget = null
                }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTtsLanguageChange = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            containerColor = NomadInputField,
            titleContentColor = NomadSilver,
            textContentColor = NomadMist
        )
    }
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = NomadSilver
            )
        }
        Spacer(Modifier.size(4.dp))
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun Section(
    title: String,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NomadRoyal.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = NomadGlow,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.size(10.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = NomadSilver,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.025f))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun CollapsibleRow(
    leading: @Composable () -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chev")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) { leading() }
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = NomadMist,
            modifier = Modifier
                .size(22.dp)
                .rotate(rotation)
        )
    }
}

@Composable
private fun ContextStrategyRow(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val border = if (selected) NomadGlow else Color.White.copy(alpha = 0.08f)
    val bg = if (selected) NomadRoyal.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.labelSmall.copy(color = NomadMuted)
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(NomadGlow)
            )
        }
    }
}

@Composable
private fun TtsEngineRow(
    title: String,
    body: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val border = when {
        !enabled -> Color.White.copy(alpha = 0.06f)
        selected -> NomadGlow
        else -> Color.White.copy(alpha = 0.08f)
    }
    val bg = when {
        !enabled -> Color.White.copy(alpha = 0.02f)
        selected -> NomadRoyal.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.04f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.labelSmall.copy(color = NomadMuted)
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(NomadGlow),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun VoicePresetButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) NomadRoyal.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.04f))
            .border(
                1.dp,
                if (selected) NomadGlow.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        style = MaterialTheme.typography.labelMedium.copy(
            color = if (selected) NomadSilver else NomadMist,
            fontWeight = FontWeight.SemiBold
        ),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
private fun UpgradeButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Text(
            text = stringResource(R.string.settings_model_upgrade_link),
            style = MaterialTheme.typography.labelMedium.copy(
                color = NomadGlow,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun LanguageRow(option: LangOption, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) NomadGlow else Color.White.copy(alpha = 0.08f)
    val bg = if (selected) NomadRoyal.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(option.flag, fontSize = 22.sp)
        Spacer(Modifier.size(12.dp))
        Text(option.label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(NomadGlow)
            )
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NomadRoyal)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(color = NomadSilver),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SecondaryButton(
    label: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingLabel: String = label,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, NomadMuted, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .alpha(if (enabled) 1f else 0.62f)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = NomadGlow
                )
            }
            Text(
                text = if (loading) loadingLabel else label,
                style = MaterialTheme.typography.labelLarge.copy(color = NomadMist)
            )
        }
    }
}

@Composable
private fun UpdateSection(
    state: UpdateState,
    currentVersion: String,
    onCheck: () -> Unit,
    onStart: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_current_version, currentVersion),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        when (state) {
            is UpdateState.Idle -> {
                PrimaryButton(stringResource(R.string.settings_check_update), onClick = onCheck)
            }
            is UpdateState.Checking -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = NomadGlow
                    )
                    Text(
                        text = stringResource(R.string.update_checking),
                        style = MaterialTheme.typography.bodyMedium.copy(color = NomadMist)
                    )
                }
            }
            is UpdateState.UpToDate -> {
                Text(
                    text = stringResource(R.string.update_up_to_date),
                    style = MaterialTheme.typography.bodyMedium.copy(color = NomadGlow)
                )
            }
            is UpdateState.Available -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NomadRoyal.copy(alpha = 0.18f))
                        .border(1.5.dp, NomadGlow, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.update_available_desc),
                        style = MaterialTheme.typography.titleMedium.copy(color = NomadSilver),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.size(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(
                            stringResource(R.string.update_action),
                            onClick = onStart
                        )
                        SecondaryButton(
                            stringResource(R.string.common_cancel),
                            onClick = onDismiss
                        )
                    }
                }
            }
            is UpdateState.Downloading -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(
                            R.string.update_downloading,
                            (state.progress * 100).toInt()
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(color = NomadMist)
                    )
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NomadGlow,
                        trackColor = Color.White.copy(alpha = 0.08f),
                        strokeCap = StrokeCap.Round
                    )
                }
            }
            is UpdateState.ReadyToInstall -> {
                PrimaryButton(
                    stringResource(R.string.update_install_now),
                    onClick = onInstall
                )
            }
            is UpdateState.Error -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.update_error),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.error
                        )
                    )
                    SecondaryButton(
                        stringResource(R.string.settings_check_update),
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun LegalRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = NomadSilver,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = NomadMist,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun InternalNavRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = NomadSilver,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = NomadMist,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun DangerButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.error
            ),
            fontWeight = FontWeight.Medium
        )
    }
}
