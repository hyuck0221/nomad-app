package com.nomad.travel.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nomad.travel.R
import com.nomad.travel.data.report.AiContentReport
import com.nomad.travel.data.report.AiContentReporter
import com.nomad.travel.ui.theme.NomadInputField
import com.nomad.travel.ui.theme.NomadMuted
import com.nomad.travel.ui.theme.NomadSilver
import kotlinx.coroutines.launch

@Composable
fun AiContentReportDialog(
    context: Context,
    source: String,
    contentId: String,
    content: String,
    onDismiss: () -> Unit
) {
    var reason by remember(contentId) { mutableStateOf("") }
    var submitted by remember(contentId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_report_title)) },
        text = {
            if (submitted) {
                Text(stringResource(R.string.ai_report_submitted))
            } else {
                Column {
                    Text(
                        text = stringResource(R.string.ai_report_body),
                        style = MaterialTheme.typography.bodyMedium.copy(color = NomadMuted)
                    )
                    Spacer(Modifier.height(12.dp))
                    BasicTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        minLines = 3,
                        maxLines = 5,
                        textStyle = LocalTextStyle.current.copy(color = NomadSilver),
                        cursorBrush = SolidColor(NomadSilver),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 104.dp)
                            .background(NomadInputField, RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        decorationBox = { inner ->
                            if (reason.isBlank()) {
                                Text(
                                    text = stringResource(R.string.ai_report_hint),
                                    style = LocalTextStyle.current.copy(color = NomadMuted)
                                )
                            }
                            inner()
                        }
                    )
                }
            }
        },
        confirmButton = {
            if (submitted) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_ok))
                }
            } else {
                TextButton(
                    enabled = reason.isNotBlank(),
                    onClick = {
                        scope.launch {
                            AiContentReporter.submit(
                                context = context,
                                report = AiContentReport(
                                    source = source,
                                    contentId = contentId,
                                    content = content,
                                    reason = reason
                                )
                            )
                            submitted = true
                        }
                    }
                ) {
                    Text(stringResource(R.string.ai_report_submit))
                }
            }
        },
        dismissButton = {
            if (!submitted) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        }
    )
}
