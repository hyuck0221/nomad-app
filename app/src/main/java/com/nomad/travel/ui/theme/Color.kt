package com.nomad.travel.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Core palette — inspired by the provided design sample:
// deep violet gradient background, silvered foreground accents.
val NomadInk      = Color(0xFF0B0818)  // near-black violet, bottom of gradient
val NomadNight    = Color(0xFF17092E)  // dark surface
val NomadPurple   = Color(0xFF2A1558)  // primary surface
val NomadViolet   = Color(0xFF4B2899)  // mid gradient
val NomadRoyal    = Color(0xFF6B36D9)  // accent primary
val NomadGlow     = Color(0xFF9B74FF)  // accent highlight
val NomadSilver   = Color(0xFFE8E4F5)  // headline text
val NomadMist     = Color(0xFFB7B0D4)  // body text
val NomadMuted    = Color(0xFF7A7398)  // muted

val NomadUserBubble      = NomadRoyal
val NomadAssistantBubble = Color(0xFF1F1140)
val NomadInputField      = Color(0xFF1B0F35)

val NomadGradient: Brush
    get() = Brush.verticalGradient(
        colors = listOf(NomadNight, NomadPurple, NomadInk)
    )
