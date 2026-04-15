package com.nomad.travel.ui.setup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nomad.travel.llm.DownloadStatus
import com.nomad.travel.llm.ModelEntry
import com.nomad.travel.ui.theme.NomadAssistantBubble
import com.nomad.travel.ui.theme.NomadGlow
import com.nomad.travel.ui.theme.NomadMist
import com.nomad.travel.ui.theme.NomadMuted
import com.nomad.travel.ui.theme.NomadRoyal
import com.nomad.travel.ui.theme.NomadSilver
import java.util.Locale

private val WarningYellow = Color(0xFFF5C14A)

@Composable
fun ModelCard(
    row: ModelRow,
    active: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val blocked = !row.ramEligible
    val borderColor = when {
        blocked -> Color.White.copy(alpha = 0.06f)
        active -> NomadGlow
        else -> Color.White.copy(alpha = 0.08f)
    }
    val bg = when {
        blocked -> NomadAssistantBubble.copy(alpha = 0.3f)
        active -> NomadRoyal.copy(alpha = 0.18f)
        else -> NomadAssistantBubble.copy(alpha = 0.6f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.5.dp, borderColor, RoundedCornerShape(20.dp))
            .then(if (blocked) Modifier else Modifier.clickable(onClick = onSelect))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (blocked) 0.5f else 1f)
            ) {
                Text(
                    text = row.entry.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = row.entry.tagline,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            when {
                blocked -> LockIcon()
                else -> SelectIndicator(active)
            }
        }

        Spacer(Modifier.size(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.alpha(if (blocked) 0.5f else 1f)
        ) {
            row.entry.badges.forEach { badge ->
                val isRec = badge == "추천"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isRec) NomadGlow.copy(alpha = 0.2f)
                            else Color.White.copy(alpha = 0.07f)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isRec) NomadGlow else NomadMist
                        )
                    )
                }
            }
        }

        Spacer(Modifier.size(12.dp))

        when {
            blocked -> BlockedBanner(row.entry)
            else -> StatusRow(
                row = row,
                onDownload = onDownload,
                onCancel = onCancel,
                onDelete = onDelete
            )
        }

        if (!blocked && row.ramWarning) {
            Spacer(Modifier.size(8.dp))
            WarningBanner(row.entry)
        }
    }
}

@Composable
private fun SelectIndicator(active: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (active) NomadGlow else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (active) NomadGlow else NomadMuted,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (active) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = NomadAssistantBubble,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun LockIcon() {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = "지원되지 않는 모델",
            tint = NomadMuted,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun BlockedBanner(entry: ModelEntry) {
    val gb = entry.minRamBytes / (1024.0 * 1024.0 * 1024.0)
    val label = String.format(Locale.US, "RAM %.0f GB 이상 필요 · 이 기기는 지원하지 않습니다", gb)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = NomadMuted,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(color = NomadMuted)
        )
    }
}

@Composable
private fun WarningBanner(entry: ModelEntry) {
    val gb = entry.warnRamBytes / (1024.0 * 1024.0 * 1024.0)
    val label = String.format(
        Locale.US,
        "RAM %.0f GB 미만에서는 속도가 느리거나 불안정할 수 있습니다",
        gb
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WarningYellow.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = WarningYellow,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(color = WarningYellow)
        )
    }
}

@Composable
private fun StatusRow(
    row: ModelRow,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    when {
        row.status is DownloadStatus.Progress && !row.downloaded -> {
            val target = row.status.fraction.coerceIn(0f, 1f)
            val animated by animateFloatAsState(target, label = "p")
            Column {
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = NomadGlow,
                    trackColor = Color.White.copy(alpha = 0.08f)
                )
                Spacer(Modifier.size(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatMB(row.status.downloaded, row.status.total),
                        style = MaterialTheme.typography.labelSmall.copy(color = NomadSilver)
                    )
                    Text(
                        text = String.format(Locale.US, "%.0f%%", target * 100),
                        style = MaterialTheme.typography.labelSmall.copy(color = NomadGlow)
                    )
                    TextPill("취소", NomadMist, onCancel)
                }
            }
        }
        row.downloaded -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF78E3A9),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "다운로드 완료",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF78E3A9))
                )
                Spacer(Modifier.weight(1f))
                TextPill("삭제", MaterialTheme.colorScheme.error, onDelete)
            }
        }
        row.status is DownloadStatus.Failed -> {
            Column {
                Text(
                    text = "실패: ${row.status.message}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.size(6.dp))
                DownloadButton(row.entry, label = "다시 시도", onDownload)
            }
        }
        else -> {
            DownloadButton(row.entry, label = "다운로드", onDownload)
        }
    }
}

@Composable
private fun DownloadButton(entry: ModelEntry, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(NomadRoyal.copy(alpha = 0.35f))
            .border(1.dp, NomadRoyal, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Download,
            contentDescription = null,
            tint = NomadSilver,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = "$label · ${formatBytes(entry.sizeBytes)}",
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun TextPill(label: String, color: Color, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(color = color),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

private fun formatMB(done: Long, total: Long): String {
    val mb = 1024.0 * 1024.0
    return if (total > 0)
        String.format(Locale.US, "%.0f / %.0f MB", done / mb, total / mb)
    else
        String.format(Locale.US, "%.0f MB", done / mb)
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    return String.format(Locale.US, "%.1f GB", gb)
}
