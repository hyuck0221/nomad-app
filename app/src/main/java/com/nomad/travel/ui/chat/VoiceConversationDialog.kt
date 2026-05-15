package com.nomad.travel.ui.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Canvas
import com.nomad.travel.R
import com.nomad.travel.ui.theme.NomadGlow
import com.nomad.travel.ui.theme.NomadInputField
import com.nomad.travel.ui.theme.NomadMist
import com.nomad.travel.ui.theme.NomadMuted
import com.nomad.travel.ui.theme.NomadRoyal
import com.nomad.travel.ui.theme.NomadSilver
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun VoiceConversationDialog(
    isListening: Boolean,
    isResponding: Boolean,
    isMuted: Boolean,
    micLevel: Float,
    onToggleMute: () -> Unit,
    onDismiss: () -> Unit
) {
    val inputLevel by animateFloatAsState(
        targetValue = if (!isMuted && isListening) micLevel.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 90),
        label = "voiceInputLevel"
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0B0F1F),
                            Color(0xFF161A2E),
                            Color(0xFF0B0F1F)
                        )
                    )
                )
        ) {
            // Top close button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.voice_conversation_close),
                    tint = NomadSilver,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Center waveform + status
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.voice_conversation_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = NomadSilver,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.voice_conversation_hint_v2),
                    style = MaterialTheme.typography.bodyMedium.copy(color = NomadMist)
                )
                Spacer(Modifier.height(48.dp))

                // Visual orb. Pulses when active (listening or responding).
                Box(
                    modifier = Modifier.size(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PulsingHalo(
                        active = !isMuted && (isListening || isResponding),
                        level = inputLevel
                    )
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        if (isMuted) NomadMuted.copy(alpha = 0.4f)
                                        else NomadRoyal.copy(alpha = 0.85f),
                                        if (isMuted) Color.Transparent
                                        else NomadGlow.copy(alpha = 0.4f),
                                        Color.Transparent
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Waveform(
                            active = !isMuted && isListening,
                            level = inputLevel,
                            modifier = Modifier
                                .size(width = 110.dp, height = 60.dp)
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))
                StatusLabel(
                    isListening = isListening,
                    isResponding = isResponding,
                    isMuted = isMuted
                )
                Spacer(Modifier.height(28.dp))

                // Mute toggle — independent of close.
                MuteButton(
                    isMuted = isMuted,
                    onClick = onToggleMute
                )
            }
        }
    }
}

@Composable
private fun MuteButton(isMuted: Boolean, onClick: () -> Unit) {
    val bg = if (isMuted) Color.White.copy(alpha = 0.18f)
    else Color.White.copy(alpha = 0.06f)
    val tint = if (isMuted) NomadGlow else NomadSilver
    val label = stringResource(
        if (isMuted) R.string.voice_conversation_unmute
        else R.string.voice_conversation_mute
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                color = tint,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
private fun PulsingHalo(active: Boolean, level: Float) {
    val transition = rememberInfiniteTransition(label = "halo")
    val alpha by transition.animateFloat(
        initialValue = if (active) 0.35f else 0.18f,
        targetValue = if (active) 0.08f else 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )
    Box(
        modifier = Modifier
            .size((220 + level * 34).dp)
            .clip(CircleShape)
            .background(NomadGlow.copy(alpha = (alpha + level * 0.16f).coerceAtMost(0.5f)))
    )
}

@Composable
private fun Waveform(active: Boolean, level: Float, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    val amp by transition.animateFloat(
        initialValue = if (active) 0.7f else 0.25f,
        targetValue = if (active) 1f else 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveAmp"
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val color = NomadSilver
        val steps = 56
        for (i in 0 until steps) {
            val t = i / (steps - 1f)
            val x = t * w
            val freq = 2.4f
            val envelope = sin((t * PI).toFloat())
            val raw = sin((t * freq * 2f * PI).toFloat() + phase)
            val inputBoost = if (active) 0.45f + level * 1.35f else 1f
            val barH = (envelope * raw * amp * inputBoost * (h / 2f - 4f))
                .coerceAtLeast(2f)
            drawLine(
                color = color,
                start = Offset(x, midY - barH),
                end = Offset(x, midY + barH),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun StatusLabel(
    isListening: Boolean,
    isResponding: Boolean,
    isMuted: Boolean
) {
    val text = when {
        isMuted -> stringResource(R.string.voice_conversation_status_muted)
        isResponding -> stringResource(R.string.voice_conversation_status_responding)
        isListening -> stringResource(R.string.voice_conversation_status_listening)
        else -> stringResource(R.string.voice_conversation_status_idle)
    }
    val color = when {
        isMuted -> NomadMuted
        isResponding || isListening -> NomadGlow
        else -> NomadMuted
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(NomadInputField)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(color = NomadSilver)
        )
    }
}
