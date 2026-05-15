package com.nomad.travel.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomad.travel.R
import com.nomad.travel.ui.theme.NomadGlow
import com.nomad.travel.ui.theme.NomadMist
import com.nomad.travel.ui.theme.NomadMuted
import com.nomad.travel.ui.theme.NomadSilver

private data class LicenseEntry(
    val name: String,
    val owner: String,
    val license: String,
    val url: String
)

private const val APACHE_2_0_URL = "https://www.apache.org/licenses/LICENSE-2.0"
private const val ANDROID_SDK_TERMS_URL = "https://developer.android.com/studio/terms"
private const val MLKIT_TERMS_URL = "https://developers.google.com/ml-kit/terms"
private const val GEMMA_TERMS_URL = "https://ai.google.dev/gemma/terms"
private const val SHERPA_ONNX_URL = "https://github.com/k2-fsa/sherpa-onnx"
private const val SUPERTONIC_LICENSE_URL = "https://huggingface.co/Supertone/supertonic-3/blob/main/LICENSE"
private const val ONNXRUNTIME_URL = "https://github.com/microsoft/onnxruntime"

private val APACHE = "Apache License 2.0"
private val MIT = "MIT License"

private val LIBRARIES = listOf(
    LicenseEntry("AndroidX (Jetpack)", "The Android Open Source Project", APACHE, APACHE_2_0_URL),
    LicenseEntry("Jetpack Compose", "The Android Open Source Project", APACHE, APACHE_2_0_URL),
    LicenseEntry("Material Icons Extended", "Google LLC", APACHE, APACHE_2_0_URL),
    LicenseEntry("AndroidX Navigation Compose", "The Android Open Source Project", APACHE, APACHE_2_0_URL),
    LicenseEntry("AndroidX Room", "The Android Open Source Project", APACHE, APACHE_2_0_URL),
    LicenseEntry("AndroidX DataStore", "The Android Open Source Project", APACHE, APACHE_2_0_URL),
    LicenseEntry("AndroidX CameraX", "The Android Open Source Project", APACHE, APACHE_2_0_URL),
    LicenseEntry("AndroidX WorkManager", "The Android Open Source Project", APACHE, APACHE_2_0_URL),
    LicenseEntry("Kotlin", "JetBrains s.r.o.", APACHE, APACHE_2_0_URL),
    LicenseEntry("Kotlin Coroutines", "JetBrains s.r.o.", APACHE, APACHE_2_0_URL),
    LicenseEntry("Coil", "Coil Contributors", APACHE, APACHE_2_0_URL),
    LicenseEntry("OkHttp", "Square, Inc.", APACHE, APACHE_2_0_URL),
    LicenseEntry("ML Kit Text Recognition", "Google LLC", "ML Kit Terms of Service", MLKIT_TERMS_URL),
    LicenseEntry("LiteRT-LM", "Google LLC", APACHE, APACHE_2_0_URL),
    LicenseEntry("Play App Update", "Google LLC", "Android Software Development Kit License", ANDROID_SDK_TERMS_URL),
    LicenseEntry("Play Install Referrer", "Google LLC", "Android Software Development Kit License", ANDROID_SDK_TERMS_URL),
    LicenseEntry("Gemma (AI Model)", "Google LLC", "Gemma Terms of Use", GEMMA_TERMS_URL),
    LicenseEntry("sherpa-onnx", "k2-fsa", APACHE, SHERPA_ONNX_URL),
    LicenseEntry("ONNX Runtime", "Microsoft", MIT, ONNXRUNTIME_URL),
    LicenseEntry("Supertonic 3 (Voice Model)", "Supertone Inc.", "OpenRAIL", SUPERTONIC_LICENSE_URL),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        TopBar(
            title = stringResource(R.string.licenses_title),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.licenses_intro),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = NomadMist,
                    lineHeight = 20.sp
                )
            )

            Spacer(Modifier.height(4.dp))

            LIBRARIES.forEach { lib ->
                LicenseCard(lib) { uriHandler.openUri(lib.url) }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
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
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LicenseCard(entry: LicenseEntry, onClick: () -> Unit) {
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
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium,
                color = NomadSilver
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = entry.owner,
                style = MaterialTheme.typography.bodySmall.copy(color = NomadMuted)
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = entry.license,
                style = MaterialTheme.typography.labelSmall.copy(color = NomadGlow)
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = NomadMist,
            modifier = Modifier.size(18.dp)
        )
    }
}
