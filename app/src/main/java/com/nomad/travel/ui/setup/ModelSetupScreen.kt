package com.nomad.travel.ui.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nomad.travel.R
import com.nomad.travel.llm.DownloadStatus
import com.nomad.travel.llm.ModelCatalog
import com.nomad.travel.llm.ModelEntry
import com.nomad.travel.tts.TtsModelCatalog
import com.nomad.travel.ui.theme.NomadGlow
import com.nomad.travel.ui.theme.NomadInputField
import com.nomad.travel.ui.theme.NomadMist
import com.nomad.travel.ui.theme.NomadMuted
import com.nomad.travel.ui.theme.NomadRoyal
import com.nomad.travel.ui.theme.NomadSilver

private enum class SetupStep {
    CHOOSE_PLAN,
    INSTALL_MODELS
}

@Composable
fun ModelSetupScreen(
    onReady: () -> Unit,
    vm: SetupViewModel = viewModel(factory = SetupViewModel.Factory)
) {
    val state by vm.state.collectAsStateWithLifecycle()

    val notifPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless of result */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var pendingCancel by remember { mutableStateOf<ModelEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<ModelEntry?>(null) }
    var autoStartAfterDownload by rememberSaveable { mutableStateOf(false) }
    var step by rememberSaveable { mutableStateOf(SetupStep.CHOOSE_PLAN) }

    val selectedDownloaded = state.selected?.downloaded == true &&
        (state.selectedTts?.downloaded ?: true)
    val selectedDownloading = state.selected?.status is DownloadStatus.Progress ||
        state.selectedTts?.status is DownloadStatus.Progress

    LaunchedEffect(selectedDownloaded, autoStartAfterDownload) {
        if (autoStartAfterDownload && selectedDownloaded) {
            autoStartAfterDownload = false
            vm.commitSelectionAnd(onReady)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_logo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.size(14.dp))
            Column {
                Text(
                    text = stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = stringResource(R.string.setup_subtitle),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        IntroCard()
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (step) {
                SetupStep.CHOOSE_PLAN -> {
                    ChoosePlanContent(
                        state = state,
                        onSelectPlan = vm::selectPlan,
                        onSelectChat = vm::select,
                        onSelectTts = vm::selectTts
                    )
                }
                SetupStep.INSTALL_MODELS -> {
                    InstallModelsContent(
                        state = state,
                        onDownloadChat = vm::startDownload,
                        onDownloadTts = vm::startTtsDownload,
                        onCancel = { pendingCancel = it },
                        onDelete = { pendingDelete = it }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        val selected = state.selected
        val buttonLabel = when (step) {
            SetupStep.CHOOSE_PLAN -> stringResource(R.string.common_next)
            SetupStep.INSTALL_MODELS -> when {
                selected == null -> stringResource(R.string.setup_download_first)
                selectedDownloaded -> stringResource(R.string.setup_start)
                selectedDownloading -> stringResource(R.string.setup_downloading)
                else -> stringResource(R.string.setup_download_and_start)
            }
        }

        Column(
            modifier = Modifier.padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (step == SetupStep.INSTALL_MODELS) {
                TextButton(
                    onClick = {
                        autoStartAfterDownload = false
                        step = SetupStep.CHOOSE_PLAN
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.common_previous),
                        color = NomadMist,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Button(
                onClick = {
                    when (step) {
                        SetupStep.CHOOSE_PLAN -> step = SetupStep.INSTALL_MODELS
                        SetupStep.INSTALL_MODELS -> {
                            if (selectedDownloaded) {
                                vm.commitSelectionAnd(onReady)
                            } else {
                                autoStartAfterDownload = true
                                vm.startSelectedDownloads()
                            }
                        }
                    }
                },
                enabled = selected != null && (step == SetupStep.CHOOSE_PLAN || !selectedDownloading),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NomadRoyal,
                    contentColor = NomadSilver,
                    disabledContainerColor = Color.White.copy(alpha = 0.08f),
                    disabledContentColor = NomadMuted
                )
            ) {
                Text(
                    text = buttonLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    pendingCancel?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingCancel = null },
            title = { Text(stringResource(R.string.model_cancel_confirm_title)) },
            text = { Text(stringResource(R.string.model_cancel_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.cancelDownload(entry)
                    autoStartAfterDownload = false
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

    pendingDelete?.let { entry ->
        val isTts = TtsModelCatalog.byId(entry.id) != null
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.settings_delete_model_title)) },
            text = {
                Text(
                    text = entry.displayName + "\n\n" +
                        stringResource(
                            if (isTts) R.string.settings_tts_delete_body
                            else R.string.settings_delete_model_body
                        )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isTts) vm.deleteTts(entry) else vm.delete(entry)
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
}

@Composable
private fun IntroCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NomadRoyal.copy(alpha = 0.12f))
            .border(1.dp, NomadRoyal.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.setup_intro),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = NomadSilver,
                lineHeight = 20.sp
            )
        )
    }
}

@Composable
private fun SetupPlanCard(
    title: String,
    body: String,
    meta: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        !enabled -> Color.White.copy(alpha = 0.06f)
        selected -> NomadGlow
        else -> Color.White.copy(alpha = 0.08f)
    }
    val background = when {
        !enabled -> NomadInputField.copy(alpha = 0.35f)
        selected -> NomadRoyal.copy(alpha = 0.18f)
        else -> NomadInputField.copy(alpha = 0.7f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Transparent)
                .border(1.5.dp, if (selected) NomadGlow else NomadMuted, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(NomadGlow)
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) NomadSilver else NomadMuted
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (enabled) NomadMist else NomadMuted,
                    lineHeight = 17.sp
                )
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (selected && enabled) NomadGlow else NomadMuted
                )
            )
        }
    }
}

@Composable
private fun ChoosePlanContent(
    state: SetupUiState,
    onSelectPlan: (SetupPlan) -> Unit,
    onSelectChat: (ModelEntry) -> Unit,
    onSelectTts: (ModelEntry?) -> Unit
) {
    Text(
        text = stringResource(R.string.setup_bundle_section),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = NomadSilver
    )

    SetupPlanCard(
        title = stringResource(R.string.setup_plan_light_title),
        body = stringResource(R.string.setup_plan_light_body),
        meta = stringResource(R.string.setup_plan_light_meta),
        selected = state.selectedPlan == SetupPlan.LIGHT,
        enabled = state.rows.firstOrNull { it.entry.id == ModelCatalog.gemma4E2B.id }?.ramEligible != false,
        onClick = { onSelectPlan(SetupPlan.LIGHT) }
    )
    SetupPlanCard(
        title = stringResource(R.string.setup_plan_balanced_title),
        body = stringResource(R.string.setup_plan_balanced_body),
        meta = stringResource(R.string.setup_plan_balanced_meta),
        selected = state.selectedPlan == SetupPlan.BALANCED,
        enabled = state.rows.firstOrNull { it.entry.id == ModelCatalog.gemma4E2B.id }?.ramEligible != false,
        onClick = { onSelectPlan(SetupPlan.BALANCED) }
    )
    SetupPlanCard(
        title = stringResource(R.string.setup_plan_max_title),
        body = stringResource(R.string.setup_plan_max_body),
        meta = stringResource(R.string.setup_plan_max_meta),
        selected = state.selectedPlan == SetupPlan.MAX,
        enabled = state.rows.firstOrNull { it.entry.id == ModelCatalog.gemma4E4B.id }?.ramEligible != false,
        onClick = { onSelectPlan(SetupPlan.MAX) }
    )
    SetupPlanCard(
        title = stringResource(R.string.setup_plan_custom_title),
        body = stringResource(R.string.setup_plan_custom_body),
        meta = stringResource(R.string.setup_plan_custom_meta),
        selected = state.selectedPlan == SetupPlan.CUSTOM,
        enabled = true,
        onClick = { onSelectPlan(SetupPlan.CUSTOM) }
    )

    AnimatedVisibility(visible = state.selectedPlan == SetupPlan.CUSTOM) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.setup_custom_chat_model),
                style = MaterialTheme.typography.labelLarge,
                color = NomadMist,
                modifier = Modifier.padding(top = 4.dp)
            )
            state.rows.forEach { row ->
                SelectOnlyModelCard(
                    row = row,
                    selected = row.entry.id == state.selectedId,
                    onSelect = { onSelectChat(row.entry) }
                )
            }

            Text(
                text = stringResource(R.string.setup_custom_tts_model),
                style = MaterialTheme.typography.labelLarge,
                color = NomadMist,
                modifier = Modifier.padding(top = 4.dp)
            )
            SystemTtsCard(
                selected = state.selectedTtsId == null,
                onClick = { onSelectTts(null) }
            )
            state.ttsRows.forEach { row ->
                SelectOnlyModelCard(
                    row = row,
                    selected = row.entry.id == state.selectedTtsId,
                    onSelect = { onSelectTts(row.entry) }
                )
            }
        }
    }
}

@Composable
private fun InstallModelsContent(
    state: SetupUiState,
    onDownloadChat: (ModelEntry) -> Unit,
    onDownloadTts: (ModelEntry) -> Unit,
    onCancel: (ModelEntry) -> Unit,
    onDelete: (ModelEntry) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NomadRoyal.copy(alpha = 0.1f))
            .border(1.dp, NomadRoyal.copy(alpha = 0.26f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.setup_install_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = NomadSilver
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = stringResource(R.string.setup_install_section_body),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = NomadMuted,
                    lineHeight = 17.sp
                )
            )
        }
        state.selected?.let { row ->
            ModelCard(
                row = row,
                active = true,
                onSelect = {},
                onDownload = { onDownloadChat(row.entry) },
                onCancel = { onCancel(row.entry) },
                onDelete = { onDelete(row.entry) }
            )
        }
        state.selectedTts?.let { row ->
            ModelCard(
                row = row,
                active = true,
                onSelect = {},
                onDownload = { onDownloadTts(row.entry) },
                onCancel = { onCancel(row.entry) },
                onDelete = { onDelete(row.entry) }
            )
        } ?: SystemTtsCard(selected = true, onClick = {})
    }
}

@Composable
private fun SelectOnlyModelCard(
    row: ModelRow,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val name = if (row.entry.displayNameResId != 0)
        stringResource(row.entry.displayNameResId)
    else row.entry.displayName
    val tagline = if (row.entry.taglineResId != 0)
        stringResource(row.entry.taglineResId)
    else row.entry.tagline
    SetupPlanCard(
        title = name,
        body = tagline,
        meta = row.entry.badges.joinToString(" · "),
        selected = selected,
        enabled = row.ramEligible,
        onClick = onSelect
    )
}

@Composable
private fun SystemTtsCard(
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) NomadRoyal.copy(alpha = 0.14f) else NomadInputField.copy(alpha = 0.55f))
            .border(
                1.5.dp,
                if (selected) NomadGlow else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.setup_system_tts_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = NomadSilver
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = stringResource(R.string.setup_system_tts_body),
                style = MaterialTheme.typography.bodySmall.copy(color = NomadMist)
            )
        }
    }
}
