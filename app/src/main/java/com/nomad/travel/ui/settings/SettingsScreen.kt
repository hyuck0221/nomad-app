package com.nomad.travel.ui.settings

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nomad.travel.tools.Prompt
import com.nomad.travel.ui.setup.ModelCard
import com.nomad.travel.ui.theme.NomadGlow
import com.nomad.travel.ui.theme.NomadInputField
import com.nomad.travel.ui.theme.NomadMist
import com.nomad.travel.ui.theme.NomadMuted
import com.nomad.travel.ui.theme.NomadRoyal
import com.nomad.travel.ui.theme.NomadSilver

private data class LangOption(val code: String, val label: String, val flag: String)

private val LANGS = listOf(
    LangOption("ko", "한국어", "🇰🇷"),
    LangOption("en", "English", "🇺🇸"),
    LangOption("zh", "中文", "🇨🇳"),
    LangOption("ja", "日本語", "🇯🇵")
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var promptDraft by remember { mutableStateOf(state.systemPrompt) }
    LaunchedEffect(state.systemPrompt) { promptDraft = state.systemPrompt }

    var confirmClear by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        SettingsTopBar(onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ─── Language ─────────────────────────
            Section("언어") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LANGS.forEach { opt ->
                        LanguageRow(
                            option = opt,
                            selected = state.language == opt.code,
                            onClick = { vm.setLanguage(opt.code) }
                        )
                    }
                }
            }

            // ─── System prompt ────────────────────
            Section("공통 프롬프트") {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(NomadInputField)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
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
                        SecondaryButton("기본값 복원") {
                            promptDraft = Prompt.defaultPersona()
                            vm.resetSystemPrompt()
                        }
                        PrimaryButton("저장") { vm.setSystemPrompt(promptDraft) }
                    }
                }
            }

            // ─── Model ────────────────────────────
            Section("모델 관리") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.modelRows.forEach { row ->
                        ModelCard(
                            row = row,
                            active = row.entry.id == state.activeModelId && row.downloaded,
                            onSelect = { vm.selectModel(row.entry) },
                            onDownload = { vm.startDownload(row.entry) },
                            onCancel = { vm.cancelDownload(row.entry) },
                            onDelete = { vm.deleteModel(row.entry) }
                        )
                    }
                    Text(
                        text = "카드를 탭하면 해당 모델이 활성화됩니다. " +
                            "다운로드가 완료된 모델만 선택 가능합니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NomadMuted
                    )
                }
            }

            // ─── Danger zone ──────────────────────
            Section("채팅") {
                DangerButton(
                    label = "채팅 내역 모두 지우기",
                    onClick = { confirmClear = true }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("채팅 내역을 모두 지울까요?") },
            text = { Text("복구할 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearChats()
                    confirmClear = false
                }) {
                    Text("모두 지우기", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("취소") }
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
                contentDescription = "뒤로",
                tint = NomadSilver
            )
        }
        Spacer(Modifier.size(4.dp))
        Text("설정", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                color = NomadMuted,
                letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        content()
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
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, NomadMuted, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(color = NomadMist)
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
