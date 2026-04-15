package com.nomad.travel.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nomad.travel.R
import com.nomad.travel.data.ChatMessage
import com.nomad.travel.data.Role
import com.nomad.travel.ui.theme.NomadAssistantBubble
import com.nomad.travel.ui.theme.NomadGlow
import com.nomad.travel.ui.theme.NomadInputField
import com.nomad.travel.ui.theme.NomadMist
import com.nomad.travel.ui.theme.NomadMuted
import com.nomad.travel.ui.theme.NomadRoyal
import com.nomad.travel.ui.theme.NomadSilver
import com.nomad.travel.ui.theme.NomadUserBubble
import java.io.File

@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit = {},
    vm: ChatViewModel = viewModel(factory = ChatViewModel.Factory)
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { pendingImage = it } }

    val cameraUri = remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) pendingImage = cameraUri.value }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createTempImageUri(context)
            cameraUri.value = uri
            cameraLauncher.launch(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        ChatTopBar(onOpenSettings = onOpenSettings)

        val listState = rememberLazyListState()
        LaunchedEffect(state.messages.size) {
            if (state.messages.isNotEmpty()) {
                listState.animateScrollToItem(state.messages.lastIndex)
            }
        }

        if (state.messages.isEmpty()) {
            EmptyState(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    MessageRow(msg)
                }
            }
        }

        AttachmentPreview(
            uri = pendingImage,
            onClear = { pendingImage = null }
        )

        InputBar(
            input = input,
            onInputChange = { input = it },
            canSend = !state.isResponding && (input.isNotBlank() || pendingImage != null),
            onCamera = {
                val granted = context.checkSelfPermission(Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) {
                    val uri = createTempImageUri(context)
                    cameraUri.value = uri
                    cameraLauncher.launch(uri)
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onGallery = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onSend = {
                vm.send(context, input, pendingImage)
                input = ""
                pendingImage = null
            }
        )
    }
}

@Composable
private fun ChatTopBar(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Nomad",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF78E3A9))
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "온디바이스 · 온라인",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "설정",
                tint = NomadMist,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(22.dp))
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.size(20.dp))
        Text(
            text = "어디로 떠나시나요?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = "메뉴판 촬영 번역 · 여행지 검색 ·\n환율/지출 관리 · 현지 정보까지\n무엇이든 물어보세요.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(28.dp))
        SuggestionChips()
    }
}

@Composable
private fun SuggestionChips() {
    val chips = listOf(
        "📍 도쿄 3일 코스 짜줘",
        "💴 엔화 환율 알려줘",
        "🍜 이 메뉴판 번역해줘"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { chip ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = chip,
                    style = MaterialTheme.typography.bodyMedium.copy(color = NomadSilver)
                )
            }
        }
    }
}

@Composable
private fun MessageRow(msg: ChatMessage) {
    val isUser = msg.role == Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            AssistantAvatar()
            Spacer(Modifier.size(10.dp))
        }
        BoxWithConstraints(
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
                msg.imageUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .padding(bottom = 6.dp)
                    )
                }
                MessageBubble(msg, isUser)
                msg.toolTag?.takeIf { !isUser && it != "error" }?.let { tag ->
                    Text(
                        text = toolTagLabel(tag),
                        style = MaterialTheme.typography.labelSmall,
                        color = NomadMuted,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantAvatar() {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
    ) {
        Image(
            painter = painterResource(R.drawable.ai_avatar),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, isUser: Boolean) {
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    }
    val bg = if (isUser) NomadUserBubble else NomadAssistantBubble
    val textColor = if (isUser) NomadSilver else NomadSilver

    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (msg.pending) {
            TypingDots()
        } else {
            Text(
                text = msg.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = textColor,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            )
        }
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(22.dp)
    ) {
        repeat(3) { i ->
            val alpha = ((phase - i).coerceIn(0f, 1f) * 0.8f + 0.2f)
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(NomadMist.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun AttachmentPreview(uri: Uri?, onClear: () -> Unit) {
    AnimatedVisibility(visible = uri != null) {
        if (uri == null) return@AnimatedVisibility
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.size(10.dp))
            Text("이미지 첨부됨", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = NomadSilver, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun InputBar(
    input: String,
    onInputChange: (String) -> Unit,
    canSend: Boolean,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircleIconButton(onCamera) {
            Icon(
                Icons.Default.PhotoCamera,
                contentDescription = "카메라",
                tint = NomadMist,
                modifier = Modifier.size(20.dp)
            )
        }
        CircleIconButton(onGallery) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = "갤러리",
                tint = NomadMist,
                modifier = Modifier.size(20.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(NomadInputField)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (input.isEmpty()) {
                Text(
                    text = "메시지 입력…",
                    style = LocalTextStyle.current.copy(
                        color = NomadMuted,
                        fontSize = 15.sp
                    )
                )
            }
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                textStyle = TextStyle(color = NomadSilver, fontSize = 15.sp),
                cursorBrush = SolidColor(NomadGlow),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }

        val bgBrush = if (canSend) {
            Brush.linearGradient(listOf(NomadRoyal, NomadGlow))
        } else {
            SolidColor(Color.White.copy(alpha = 0.1f))
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(bgBrush)
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "전송",
                tint = if (canSend) NomadSilver else NomadMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CircleIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

private fun toolTagLabel(tag: String): String = when (tag) {
    "menu_translate" -> "· 메뉴 번역"
    "menu_search" -> "· 메뉴 검색"
    "expense" -> "· 지출 기록됨"
    "travel" -> "· 여행 정보"
    else -> "· $tag"
}

private fun createTempImageUri(context: Context): Uri {
    val dir = context.cacheDir
    val file = File.createTempFile("capture_", ".jpg", dir)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}
