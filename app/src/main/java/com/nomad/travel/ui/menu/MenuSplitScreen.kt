package com.nomad.travel.ui.menu

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nomad.travel.R
import com.nomad.travel.ui.theme.NomadAssistantBubble
import com.nomad.travel.ui.theme.NomadMist
import com.nomad.travel.ui.theme.NomadMuted
import com.nomad.travel.ui.theme.NomadSilver

@Composable
fun MenuSplitScreen(
    imageUri: Uri,
    text: String,
    onBack: () -> Unit
) {
    val config = LocalConfiguration.current
    val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        MenuTopBar(onBack = onBack)

        if (landscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                MenuImagePane(
                    uri = imageUri,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 16.dp)
                )
                MenuTextPane(
                    text = text,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                MenuImagePane(
                    uri = imageUri,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
                MenuTextPane(
                    text = text,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun MenuTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = NomadMist,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.menu_view_title),
            style = MaterialTheme.typography.titleMedium.copy(color = NomadSilver),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MenuImagePane(uri: Uri, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun MenuTextPane(text: String, modifier: Modifier = Modifier) {
    val rendered = remember(text) { renderSimpleMarkdown(text) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(NomadAssistantBubble)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = rendered,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = NomadSilver,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            )
        }
    }
}

private val MD_INLINE = Regex(
    "(\\*\\*([^*\\n]+?)\\*\\*)|(__([^_\\n]+?)__)|(\\*([^*\\n]+?)\\*)|(_([^_\\n]+?)_)|(`([^`\\n]+?)`)"
)

private fun renderSimpleMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.split('\n')
    lines.forEachIndexed { index, rawLine ->
        if (index > 0) append('\n')
        val trimmed = rawLine.trimStart()
        val indent = " ".repeat(rawLine.length - trimmed.length)
        when {
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                append(indent)
                append("• ")
                appendInline(trimmed.drop(2))
            }
            trimmed.startsWith("### ") -> withStyle(
                SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            ) { appendInline(indent + trimmed.removePrefix("### ")) }
            trimmed.startsWith("## ") -> withStyle(
                SpanStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp)
            ) { appendInline(indent + trimmed.removePrefix("## ")) }
            else -> appendInline(rawLine)
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInline(line: String) {
    if (line.isEmpty()) return
    var cursor = 0
    for (match in MD_INLINE.findAll(line)) {
        if (match.range.first > cursor) append(line.substring(cursor, match.range.first))
        val g = match.groupValues
        when {
            g[1].isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[2]) }
            g[3].isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[4]) }
            g[5].isNotEmpty() ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[6]) }
            g[7].isNotEmpty() ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[8]) }
            g[9].isNotEmpty() -> append(g[10])
        }
        cursor = match.range.last + 1
    }
    if (cursor < line.length) append(line.substring(cursor))
}
