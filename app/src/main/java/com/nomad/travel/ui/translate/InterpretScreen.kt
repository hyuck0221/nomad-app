package com.nomad.travel.ui.translate

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
import com.nomad.travel.ui.theme.NomadInk
import com.nomad.travel.ui.theme.NomadMist
import com.nomad.travel.ui.theme.NomadMuted
import com.nomad.travel.ui.theme.NomadNight
import com.nomad.travel.ui.theme.NomadRoyal
import com.nomad.travel.ui.theme.NomadSilver

@Composable
fun InterpretScreen(
    onBack: () -> Unit,
    presetSrc: String? = null,
    presetTgt: String? = null,
    vm: TranslateViewModel = viewModel(factory = TranslateViewModel.Factory)
) {
    // Apply preset languages once
    androidx.compose.runtime.LaunchedEffect(presetSrc, presetTgt) {
        if (presetSrc != null && presetTgt != null) {
            vm.presetInterpretLanguages(presetSrc, presetTgt)
        }
    }

    val state by vm.interpret.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // We store which mic requested permission in a simple way
        if (granted) vm.startListening(TranslateViewModel.SttTarget.INTERPRET_ME)
    }

    val partnerMicPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startListening(TranslateViewModel.SttTarget.INTERPRET_PARTNER)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        /* ══════════════════════════════════════════════════════
         *  TOP HALF — Partner's side (upside-down)
         *  Shows: translation of my words in partner's language
         *  Has: mic button for partner to speak
         * ══════════════════════════════════════════════════════ */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer { rotationZ = 180f }  // flip upside-down
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(NomadNight, NomadInk))
                    )
                    .padding(16.dp)
            ) {
                // Partner's language label (appears at top for them)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${state.theirLanguage.flag} ${state.theirLanguage.nameNative}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = NomadSilver,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    // Partner mic button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.isTheirMicActive) NomadGlow.copy(alpha = 0.2f)
                                else Color.White.copy(alpha = 0.08f)
                            )
                            .clickable {
                                if (state.isTheirMicActive) {
                                    vm.stopListening()
                                } else {
                                    val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                        PackageManager.PERMISSION_GRANTED
                                    if (granted) vm.startListening(TranslateViewModel.SttTarget.INTERPRET_PARTNER)
                                    else partnerMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (state.isTheirMicActive) Icons.Outlined.Mic else Icons.Default.Mic,
                            contentDescription = stringResource(R.string.translate_mic),
                            tint = if (state.isTheirMicActive) NomadGlow else NomadMist,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Translation result display with aura
                AuraBox(
                    isActive = state.isTheirAreaTranslating,
                    cornerRadius = 20f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(NomadAssistantBubble)
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = if (state.theirDisplayText.isEmpty() && !state.isTheirAreaTranslating)
                            Alignment.Center else Alignment.TopStart
                    ) {
                        if (state.theirDisplayText.isEmpty() && !state.isTheirAreaTranslating) {
                            Text(
                                text = interpretPartnerPlaceholder(state.theirLanguage.code),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = NomadMuted,
                                    textAlign = TextAlign.Center
                                )
                            )
                        } else {
                            val displayText = if (state.isTheirAreaTranslating)
                                state.theirDisplayText + "▍" else state.theirDisplayText
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    color = NomadSilver,
                                    fontSize = 22.sp,
                                    lineHeight = 32.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        /* ── Center divider with language labels ── */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NomadRoyal.copy(alpha = 0.15f))
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${state.myLanguage.flag} ${state.myLanguage.nameNative}",
                style = MaterialTheme.typography.labelLarge.copy(color = NomadMist)
            )
            Text(
                text = "  ⇄  ",
                style = MaterialTheme.typography.labelLarge.copy(color = NomadMuted)
            )
            Text(
                text = "${state.theirLanguage.flag} ${state.theirLanguage.nameNative}",
                style = MaterialTheme.typography.labelLarge.copy(color = NomadMist)
            )
            Spacer(Modifier.weight(1f))
            // Settings: language pickers + clear
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { vm.toggleMyLanguagePicker() },
                contentAlignment = Alignment.Center
            ) {
                Text("${state.myLanguage.flag}", fontSize = 14.sp)
            }
            Spacer(Modifier.size(6.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { vm.toggleTheirLanguagePicker() },
                contentAlignment = Alignment.Center
            ) {
                Text("${state.theirLanguage.flag}", fontSize = 14.sp)
            }
            Spacer(Modifier.size(6.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { vm.clearInterpret() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = NomadMuted, modifier = Modifier.size(16.dp))
            }
        }

        // Language pickers (shown between halves)
        AnimatedVisibility(
            visible = state.showMyLanguagePicker,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LanguagePickerGrid(selected = state.myLanguage, onSelect = { vm.setMyLanguage(it) })
        }
        AnimatedVisibility(
            visible = state.showTheirLanguagePicker,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LanguagePickerGrid(selected = state.theirLanguage, onSelect = { vm.setTheirLanguage(it) })
        }

        /* ══════════════════════════════════════════════════════
         *  BOTTOM HALF — My side (normal orientation)
         *  Shows: translation of partner's words in my language
         *  Has: text input + mic + send button
         * ══════════════════════════════════════════════════════ */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(NomadInk, NomadNight))
                    )
                    .padding(16.dp)
            ) {
                // My language label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.myLanguage.flag} ${state.myLanguage.nameNative}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = NomadSilver,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(Modifier.weight(1f))
                    // Back button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                            tint = NomadMist,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Display area: partner's words translated to my language
                AuraBox(
                    isActive = state.isMyAreaTranslating,
                    cornerRadius = 20f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(NomadAssistantBubble)
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = if (state.myDisplayText.isEmpty() && !state.isMyAreaTranslating)
                            Alignment.Center else Alignment.TopStart
                    ) {
                        if (state.myDisplayText.isEmpty() && !state.isMyAreaTranslating) {
                            Text(
                                text = stringResource(R.string.interpret_empty_me),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = NomadMuted,
                                    textAlign = TextAlign.Center
                                )
                            )
                        } else {
                            val displayText = if (state.isMyAreaTranslating)
                                state.myDisplayText + "▍" else state.myDisplayText
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    color = NomadSilver,
                                    fontSize = 22.sp,
                                    lineHeight = 32.sp
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Input row: mic + text + send
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // My mic
                    SmallActionButton(
                        onClick = {
                            if (state.isMyMicActive) {
                                vm.stopListening()
                            } else {
                                val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED
                                if (granted) vm.startListening(TranslateViewModel.SttTarget.INTERPRET_ME)
                                else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        highlight = state.isMyMicActive
                    ) {
                        Icon(
                            imageVector = if (state.isMyMicActive) Icons.Outlined.Mic else Icons.Default.Mic,
                            contentDescription = stringResource(R.string.translate_mic),
                            tint = if (state.isMyMicActive) NomadGlow else NomadMist,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Text input
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(NomadInputField)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        if (state.myInput.isEmpty()) {
                            Text(
                                text = stringResource(R.string.interpret_input_hint, state.myLanguage.nameNative),
                                style = MaterialTheme.typography.bodyMedium.copy(color = NomadMuted, fontSize = 15.sp)
                            )
                        }
                        BasicTextField(
                            value = state.myInput,
                            onValueChange = { vm.updateInterpretInput(it) },
                            textStyle = TextStyle(color = NomadSilver, fontSize = 15.sp),
                            cursorBrush = SolidColor(NomadGlow),
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Send
                    val canSend = state.myInput.isNotBlank() && !state.isTheirAreaTranslating
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) Brush.linearGradient(listOf(NomadRoyal, NomadGlow))
                                else SolidColor(Color.White.copy(alpha = 0.1f))
                            )
                            .clickable(enabled = canSend) { vm.sendMyMessage() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.send),
                            tint = if (canSend) NomadSilver else NomadMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun interpretPartnerPlaceholder(code: String): String = when (code) {
    "ko" -> "상대방이 작성하거나 마이크로 말하면\n여기에 번역이 표시됩니다"
    "en" -> "When the other person types or speaks into the mic,\nthe translation will appear here"
    "ja" -> "相手が入力したりマイクで話すと、\nここに翻訳が表示されます"
    "zh" -> "当对方输入或对着麦克风说话时，\n翻译将显示在这里"
    "es" -> "Cuando la otra persona escriba o hable al micrófono,\nla traducción aparecerá aquí"
    "fr" -> "Lorsque l'autre personne tape ou parle dans le micro,\nla traduction apparaîtra ici"
    "de" -> "Wenn die andere Person tippt oder ins Mikrofon spricht,\nerscheint hier die Übersetzung"
    "it" -> "Quando l'altra persona digita o parla al microfono,\nla traduzione apparirà qui"
    "pt" -> "Quando a outra pessoa digitar ou falar ao microfone,\na tradução aparecerá aqui"
    "ru" -> "Когда собеседник печатает или говорит в микрофон,\nздесь появится перевод"
    "th" -> "เมื่ออีกฝ่ายพิมพ์หรือพูดเข้าไมโครโฟน\nคำแปลจะปรากฏที่นี่"
    "vi" -> "Khi người kia nhập hoặc nói vào micrô,\nbản dịch sẽ hiển thị ở đây"
    "id" -> "Saat lawan bicara mengetik atau berbicara ke mikrofon,\nterjemahan akan muncul di sini"
    "ms" -> "Apabila rakan bicara menaip atau bercakap ke mikrofon,\nterjemahan akan muncul di sini"
    "ar" -> "عندما يكتب الطرف الآخر أو يتحدث في الميكروفون،\nستظهر الترجمة هنا"
    "hi" -> "जब सामने वाला टाइप करे या माइक में बोले,\nअनुवाद यहाँ दिखाई देगा"
    "tr" -> "Karşı taraf yazdığında veya mikrofona konuştuğunda,\nçeviri burada görünecek"
    "nl" -> "Wanneer de ander typt of in de microfoon spreekt,\nverschijnt hier de vertaling"
    "pl" -> "Gdy rozmówca pisze lub mówi do mikrofonu,\ntłumaczenie pojawi się tutaj"
    "sv" -> "När motparten skriver eller talar i mikrofonen,\nvisas översättningen här"
    "da" -> "Når den anden person skriver eller taler i mikrofonen,\nvises oversættelsen her"
    "fi" -> "Kun toinen henkilö kirjoittaa tai puhuu mikrofoniin,\nkäännös näkyy tässä"
    "nb" -> "Når den andre personen skriver eller snakker i mikrofonen,\nvises oversettelsen her"
    "el" -> "Όταν ο συνομιλητής πληκτρολογεί ή μιλά στο μικρόφωνο,\nη μετάφραση θα εμφανιστεί εδώ"
    "cs" -> "Když druhá osoba píše nebo mluví do mikrofonu,\npřeklad se zobrazí zde"
    "hu" -> "Amikor a beszélgetőtárs gépel vagy a mikrofonba beszél,\nitt jelenik meg a fordítás"
    "ro" -> "Când cealaltă persoană scrie sau vorbește la microfon,\ntraducerea va apărea aici"
    "uk" -> "Коли співрозмовник друкує або говорить у мікрофон,\nтут з'явиться переклад"
    "he" -> "כשהצד השני מקליד או מדבר למיקרופון,\nהתרגום יופיע כאן"
    "bn" -> "অন্য পক্ষ টাইপ করলে বা মাইকে কথা বললে,\nঅনুবাদ এখানে দেখানো হবে"
    "tl" -> "Kapag nag-type o nagsalita ang kausap sa mikropono,\nlilitaw ang salin dito"
    else -> "When the other person types or speaks into the mic,\nthe translation will appear here"
}
