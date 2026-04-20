package com.nomad.travel.ui.translate

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nomad.travel.R
import com.nomad.travel.ui.theme.NomadAssistantBubble
import com.nomad.travel.ui.theme.NomadGlow
import com.nomad.travel.ui.theme.NomadInputField
import com.nomad.travel.ui.theme.NomadMist
import com.nomad.travel.ui.theme.NomadMuted
import com.nomad.travel.ui.theme.NomadRoyal
import com.nomad.travel.ui.theme.NomadSilver

@Composable
fun TranslateScreen(
    onBack: () -> Unit,
    onFullscreen: (String) -> Unit = {},
    presetSrc: String? = null,
    presetTgt: String? = null,
    vm: TranslateViewModel = viewModel(factory = TranslateViewModel.Factory)
) {
    // Apply preset languages once
    LaunchedEffect(presetSrc, presetTgt) {
        if (presetSrc != null && presetTgt != null) {
            vm.presetTranslateLanguages(presetSrc, presetTgt)
        }
    }

    val state by vm.translate.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startListening(TranslateViewModel.SttTarget.TRANSLATE)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TranslateTopBar(
            title = stringResource(R.string.translate_title),
            onBack = onBack
        )

        LanguageSelectorRow(
            sourceLanguage = state.sourceLanguage,
            targetLanguage = state.targetLanguage,
            onSourceClick = { vm.toggleSourcePicker() },
            onTargetClick = { vm.toggleTargetPicker() },
            onSwap = { vm.swapLanguages() }
        )

        AnimatedVisibility(
            visible = state.showSourcePicker,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LanguagePickerGrid(selected = state.sourceLanguage, onSelect = { vm.setSourceLanguage(it) })
        }
        AnimatedVisibility(
            visible = state.showTargetPicker,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LanguagePickerGrid(selected = state.targetLanguage, onSelect = { vm.setTargetLanguage(it) })
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))

            // Source label
            Text(
                text = state.sourceLanguage.nameNative,
                style = MaterialTheme.typography.labelMedium,
                color = NomadMuted,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NomadInputField)
                    .padding(16.dp)
            ) {
                if (state.sourceText.isEmpty()) {
                    Text(
                        text = stringResource(R.string.translate_input_hint),
                        style = MaterialTheme.typography.bodyLarge.copy(color = NomadMuted)
                    )
                }
                BasicTextField(
                    value = state.sourceText,
                    onValueChange = { vm.updateSourceText(it) },
                    textStyle = TextStyle(color = NomadSilver, fontSize = 16.sp, lineHeight = 24.sp),
                    cursorBrush = SolidColor(NomadGlow),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Action row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallActionButton(
                    onClick = {
                        if (state.isListening) {
                            vm.stopListening()
                        } else {
                            val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) vm.startListening(TranslateViewModel.SttTarget.TRANSLATE)
                            else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    highlight = state.isListening
                ) {
                    Icon(
                        imageVector = if (state.isListening) Icons.Outlined.Mic else Icons.Default.Mic,
                        contentDescription = stringResource(R.string.translate_mic),
                        tint = if (state.isListening) NomadGlow else NomadMist,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (state.sourceText.isNotEmpty()) {
                    SmallActionButton(onClick = { vm.clearTranslate() }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.translate_clear), tint = NomadMist, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                val canSend = state.sourceText.isNotBlank() && !state.isTranslating
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (canSend) Brush.linearGradient(listOf(NomadRoyal, NomadGlow))
                            else SolidColor(Color.White.copy(alpha = 0.1f))
                        )
                        .clickable(enabled = canSend) { vm.translateText() }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.translate_action),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = if (canSend) NomadSilver else NomadMuted,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            // Divider
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
            Spacer(Modifier.height(12.dp))

            // Result label
            Text(
                text = state.targetLanguage.nameNative,
                style = MaterialTheme.typography.labelMedium,
                color = NomadMuted,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Result box with aura border
            AuraBox(
                isActive = state.isTranslating,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NomadAssistantBubble)
                        .padding(16.dp)
                ) {
                    if (state.translatedText.isEmpty() && !state.isTranslating) {
                        Text(
                            text = stringResource(R.string.translate_result_hint),
                            style = MaterialTheme.typography.bodyLarge.copy(color = NomadMuted)
                        )
                    } else {
                        val displayText = if (state.isTranslating) state.translatedText + "▍" else state.translatedText
                        Column {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodyLarge.copy(color = NomadSilver, fontSize = 16.sp, lineHeight = 24.sp)
                            )
                            if (state.pronunciation.isNotBlank() && !state.isTranslating) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = state.pronunciation,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = NomadMuted,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Result actions
            if (state.translatedText.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallActionButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("translation", state.translatedText))
                        Toast.makeText(context, context.getString(R.string.translate_copied), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.translate_copy), tint = NomadMist, modifier = Modifier.size(18.dp))
                    }
                    SmallActionButton(onClick = { onFullscreen(state.translatedText) }) {
                        Icon(Icons.Default.Fullscreen, contentDescription = stringResource(R.string.translate_fullscreen), tint = NomadMist, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/* ── Aura border box: glowing animated border when active, smoke fade when done ── */

@Composable
fun AuraBox(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Float = 16f,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aura")
    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    var wasActive by remember { mutableStateOf(false) }
    var showSmoke by remember { mutableStateOf(false) }
    val smokeProgress = remember { Animatable(0f) }

    LaunchedEffect(isActive) {
        if (wasActive && !isActive) {
            showSmoke = true
            smokeProgress.snapTo(0f)
            smokeProgress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            showSmoke = false
        }
        wasActive = isActive
    }

    val glowAlpha = when {
        isActive -> auraPulse
        showSmoke -> (1f - smokeProgress.value) * 0.9f
        else -> 0f
    }
    val glowSpread = when {
        isActive -> auraPulse * 4f   // subtle pulse spread when active
        showSmoke -> smokeProgress.value * 28f
        else -> 0f
    }

    val c1 = NomadGlow
    val c2 = NomadRoyal
    val cr = cornerRadius

    // Padding for glow to breathe outside the content
    val glowPad = 6.dp

    Box(modifier = modifier.padding(glowPad)) {
        content()

        // Overlay: draw glow ON TOP of content so it's always visible
        if (glowAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val spread = glowSpread.dp.toPx()
                        val baseStroke = if (isActive) 2f + auraPulse * 2f else (1f - smokeProgress.value) * 3f
                        val layers = if (showSmoke) 4 else 2
                        for (i in 0 until layers) {
                            val ls = spread * (1f + i * 0.5f)
                            val la = glowAlpha / (1f + i * 0.6f)
                            val sw = baseStroke + ls * 0.2f
                            drawRoundRect(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        c1.copy(alpha = la),
                                        c2.copy(alpha = la * 0.6f),
                                        c1.copy(alpha = la * 0.25f),
                                        c2.copy(alpha = la * 0.8f),
                                        c1.copy(alpha = la)
                                    )
                                ),
                                topLeft = Offset(-ls, -ls),
                                size = Size(size.width + ls * 2, size.height + ls * 2),
                                cornerRadius = CornerRadius(cr.dp.toPx()),
                                style = Stroke(width = sw)
                            )
                        }
                        // Inner glow: soft color inside the border
                        if (isActive) {
                            drawRoundRect(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        c1.copy(alpha = auraPulse * 0.12f),
                                        c2.copy(alpha = auraPulse * 0.06f),
                                        c1.copy(alpha = auraPulse * 0.1f),
                                        c2.copy(alpha = auraPulse * 0.08f),
                                        c1.copy(alpha = auraPulse * 0.12f)
                                    )
                                ),
                                cornerRadius = CornerRadius(cr.dp.toPx()),
                                style = Stroke(width = 8f)
                            )
                        }
                    }
            )
        }
    }
}

/* ── Fullscreen overlay with auto landscape ── */

@Composable
fun FullscreenTextOverlay(
    text: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Force landscape when entering fullscreen
    DisposableEffect(Unit) {
        val original = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = original ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineLarge.copy(
                color = NomadSilver,
                fontSize = 36.sp,
                lineHeight = 50.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(40.dp)
        )
    }
}

/* ── Shared components ── */

@Composable
fun TranslateTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
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
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = NomadMist,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = NomadSilver)
    }
}

@Composable
fun LanguageSelectorRow(
    sourceLanguage: Language,
    targetLanguage: Language,
    onSourceClick: () -> Unit,
    onTargetClick: () -> Unit,
    onSwap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LanguageChip(language = sourceLanguage, onClick = onSourceClick, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(NomadRoyal.copy(alpha = 0.3f))
                .clickable(onClick = onSwap),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.translate_swap), tint = NomadGlow, modifier = Modifier.size(20.dp))
        }
        LanguageChip(language = targetLanguage, onClick = onTargetClick, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LanguageChip(language: Language, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NomadInputField)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(text = language.flag, fontSize = 18.sp)
        Spacer(Modifier.size(8.dp))
        Text(
            text = language.nameNative,
            style = MaterialTheme.typography.bodyMedium.copy(color = NomadSilver, fontWeight = FontWeight.Medium),
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.size(4.dp))
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = NomadMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun LanguagePickerGrid(selected: Language, onSelect: (Language) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NomadInputField),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(SUPPORTED_LANGUAGES) { lang ->
            val isSelected = lang.code == selected.code
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) NomadRoyal.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable { onSelect(lang) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = lang.flag, fontSize = 20.sp)
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(text = lang.nameNative, style = MaterialTheme.typography.bodyMedium.copy(color = NomadSilver, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal))
                    Text(text = lang.nameEn, style = MaterialTheme.typography.bodySmall.copy(color = NomadMuted))
                }
            }
        }
    }
}

@Composable
fun SmallActionButton(onClick: () -> Unit, highlight: Boolean = false, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (highlight) NomadGlow.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f))
            .then(if (highlight) Modifier.border(1.dp, NomadGlow.copy(alpha = 0.4f), CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
