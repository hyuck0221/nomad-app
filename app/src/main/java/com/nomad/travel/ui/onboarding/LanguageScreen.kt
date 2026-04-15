package com.nomad.travel.ui.onboarding

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.nomad.travel.R
import com.nomad.travel.ui.theme.NomadGlow
import com.nomad.travel.ui.theme.NomadMist
import com.nomad.travel.ui.theme.NomadRoyal
import com.nomad.travel.ui.theme.NomadSilver

data class LanguageOption(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val flag: String
)

private val LANGUAGES = listOf(
    LanguageOption("ko", "한국어", "Korean", "🇰🇷"),
    LanguageOption("en", "English", "English", "🇺🇸"),
    LanguageOption("zh", "中文", "Chinese", "🇨🇳"),
    LanguageOption("ja", "日本語", "Japanese", "🇯🇵")
)

@Composable
fun LanguageScreen(
    onContinue: (String) -> Unit
) {
    var selected by rememberSaveable { mutableStateOf("ko") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .align(Alignment.CenterHorizontally)
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Nomad",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "당신의 여행 AI 동반자",
            style = MaterialTheme.typography.bodyLarge.copy(color = NomadMist),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(40.dp))

        Text(
            text = "언어 선택 / Language",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(LANGUAGES, key = { it.code }) { lang ->
                LanguageRow(
                    option = lang,
                    selected = selected == lang.code,
                    onClick = { selected = lang.code }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onContinue(selected) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NomadRoyal,
                contentColor = NomadSilver
            )
        ) {
            Text(
                text = "계속 / Continue",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun LanguageRow(
    option: LanguageOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) NomadGlow else Color.Transparent
    val bgColor = if (selected) NomadRoyal.copy(alpha = 0.18f)
    else Color.White.copy(alpha = 0.04f)

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = option.flag, fontSize = 24.sp)
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = option.nativeName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = option.englishName,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = NomadGlow,
                        shape = CircleShape,
                        modifier = Modifier.size(10.dp)
                    ) {}
                }
            }
        }
    }
}
