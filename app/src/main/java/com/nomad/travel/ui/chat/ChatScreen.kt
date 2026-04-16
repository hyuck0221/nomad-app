package com.nomad.travel.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nomad.travel.R
import com.nomad.travel.data.ChatMessage
import com.nomad.travel.data.Role
import com.nomad.travel.data.chat.ChatSessionEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    onOpenMenuView: (Uri, String) -> Unit = { _, _ -> },
    onOpenTranslate: () -> Unit = {},
    onOpenInterpret: () -> Unit = {},
    onOpenTranslateWithLangs: (String, String) -> Unit = { _, _ -> },
    onOpenInterpretWithLangs: (String, String) -> Unit = { _, _ -> },
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

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var pendingDeleteSessionId by remember { mutableStateOf<Long?>(null) }
    var sendTick by remember { mutableStateOf(0) }
    var showTranslateSheet by remember { mutableStateOf(false) }

    // STT: wire mic results to input field
    vm.onSttResult = { text -> input = text }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startListening()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = NomadInputField,
                drawerContentColor = NomadSilver
            ) {
                SessionDrawerContent(
                    sessions = state.sessions,
                    currentId = state.currentSessionId,
                    onNewChat = {
                        vm.newSession()
                        scope.launch { drawerState.close() }
                    },
                    onSelect = { id ->
                        vm.switchSession(id)
                        scope.launch { drawerState.close() }
                    },
                    onDelete = { id -> pendingDeleteSessionId = id }
                )
            }
        }
    ) {
        ChatScreenBody(
            state = state,
            sendTick = sendTick,
            input = input,
            onInputChange = { input = it },
            pendingImage = pendingImage,
            onClearPendingImage = { pendingImage = null },
            onOpenSettings = onOpenSettings,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onOpenTranslate = { showTranslateSheet = true },
            onResolveTranslate = { call ->
                vm.dismissPending()
                onOpenTranslateWithLangs(call.src, call.tgt)
            },
            onResolveInterpret = { call ->
                vm.dismissPending()
                onOpenInterpretWithLangs(call.src, call.tgt)
            },
            onSend = {
                vm.send(context, input, pendingImage)
                input = ""
                pendingImage = null
                sendTick++
            },
            onCancel = { vm.cancelResponse() },
            onOpenMenuView = onOpenMenuView,
            onResolveCurrency = { live -> vm.resolveCurrency(live) },
            onResolveAsk = { option -> vm.resolveAsk(context, option) },
            onDismissPending = { vm.dismissPending() },
            onStartUpdate = { vm.startUpdate() },
            onSkipUpdate = { vm.skipUpdate() },
            onMic = {
                if (state.isListening) {
                    vm.stopListening()
                } else {
                    val granted = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) vm.startListening()
                    else micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            },
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
            }
        )
    }

    pendingDeleteSessionId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSessionId = null },
            title = { Text(stringResource(R.string.chat_session_delete_title)) },
            text = { Text(stringResource(R.string.chat_session_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSession(id)
                    pendingDeleteSessionId = null
                }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSessionId = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            containerColor = NomadInputField,
            titleContentColor = NomadSilver,
            textContentColor = NomadMist
        )
    }

    // Translate mode picker sheet
    if (showTranslateSheet) {
        TranslateModeSheet(
            onDismiss = { showTranslateSheet = false },
            onTranslate = {
                showTranslateSheet = false
                onOpenTranslate()
            },
            onInterpret = {
                showTranslateSheet = false
                onOpenInterpret()
            }
        )
    }
}

@Composable
private fun ChatScreenBody(
    state: ChatUiState,
    sendTick: Int,
    input: String,
    onInputChange: (String) -> Unit,
    pendingImage: Uri?,
    onClearPendingImage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenTranslate: () -> Unit,
    onResolveTranslate: (com.nomad.travel.tools.ToolTags.TranslateCall) -> Unit,
    onResolveInterpret: (com.nomad.travel.tools.ToolTags.InterpretCall) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onMic: () -> Unit,
    onOpenMenuView: (Uri, String) -> Unit,
    onResolveCurrency: (Boolean) -> Unit,
    onResolveAsk: (String) -> Unit,
    onDismissPending: () -> Unit,
    onStartUpdate: () -> Unit = {},
    onSkipUpdate: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        ChatTopBar(onOpenDrawer = onOpenDrawer, onOpenTranslate = onOpenTranslate, onOpenSettings = onOpenSettings)

        val listState = rememberLazyListState()
        var autoScroll by remember { mutableStateOf(true) }

        // Release auto-scroll when the user drags away from the bottom,
        // re-enable it when they scroll back to the bottom.
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }
                .collect { scrolling ->
                    if (!scrolling) {
                        autoScroll = !listState.canScrollForward
                    }
                }
        }

        val lastMessageId = state.messages.lastOrNull()?.id
        val lastMessageLength = state.messages.lastOrNull()?.text?.length ?: 0
        LaunchedEffect(lastMessageId, lastMessageLength) {
            if (!autoScroll || state.messages.isEmpty()) return@LaunchedEffect
            val lastIdx = state.messages.lastIndex
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            if (lastVisible == null || lastVisible.index != lastIdx) {
                listState.scrollToItem(lastIdx, Int.MAX_VALUE / 2)
                return@LaunchedEffect
            }
            val viewportBottom = info.viewportEndOffset - info.afterContentPadding
            val itemBottom = lastVisible.offset + lastVisible.size
            val delta = (itemBottom - viewportBottom).toFloat()
            if (delta > 0f) listState.animateScrollBy(delta)
        }

        LaunchedEffect(sendTick) {
            if (sendTick == 0) return@LaunchedEffect
            autoScroll = true
            if (state.messages.isNotEmpty()) {
                val lastIdx = state.messages.lastIndex
                listState.animateScrollToItem(lastIdx, Int.MAX_VALUE / 2)
            }
        }

        val messageCount = state.messages.size
        LaunchedEffect(messageCount) {
            if (messageCount == 0) return@LaunchedEffect
            autoScroll = true
            val lastIdx = state.messages.lastIndex
            listState.animateScrollToItem(lastIdx, Int.MAX_VALUE / 2)
        }

        // When the keyboard appears while auto-scroll is active,
        // scroll to bottom so the latest message stays visible.
        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)
        LaunchedEffect(imeBottom) {
            if (imeBottom > 0 && autoScroll && state.messages.isNotEmpty()) {
                val lastIdx = state.messages.lastIndex
                listState.scrollToItem(lastIdx, Int.MAX_VALUE / 2)
            }
        }

        // Track which message IDs were already on screen so we only animate truly new ones.
        // Re-seeded when the session changes (first message ID changes).
        val sessionKey = state.messages.firstOrNull()?.id
        val renderedIds = remember(sessionKey) {
            mutableSetOf<String>().apply { state.messages.forEach { add(it.id) } }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (state.messages.isEmpty()) {
                EmptyState(modifier = Modifier.matchParentSize())
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(
                        items = state.messages,
                        key = { _, m -> m.id }
                    ) { index, msg ->
                        val priorImageUri = state.messages
                            .getOrNull(index - 1)
                            ?.takeIf { it.role == Role.USER }
                            ?.imageUri

                        val isNew = remember(msg.id) {
                            val new = msg.id !in renderedIds
                            renderedIds.add(msg.id)
                            new
                        }
                        val shouldAnimate = isNew && (msg.role == Role.USER || msg.pending)

                        val animProgress = remember(msg.id) {
                            Animatable(if (shouldAnimate) 0f else 1f)
                        }
                        if (shouldAnimate) {
                            val animDelay = if (msg.role == Role.ASSISTANT) 400 else 0
                            LaunchedEffect(msg.id) {
                                animProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = 350,
                                        delayMillis = animDelay,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                            }
                        }
                        Box(
                            modifier = Modifier.graphicsLayer {
                                alpha = animProgress.value
                                translationY = (1f - animProgress.value) * 40f
                            }
                        ) {
                            MessageRow(
                                msg = msg,
                                menuImageUri = priorImageUri,
                                onOpenMenuView = onOpenMenuView
                            )
                        }
                    }
                }
            }

            // Floating update card
            androidx.compose.animation.AnimatedVisibility(
                visible = state.updateBanner !is UpdateBannerState.Hidden,
                enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                UpdateFloatingCard(
                    bannerState = state.updateBanner,
                    onUpdate = onStartUpdate,
                    onSkip = onSkipUpdate
                )
            }
        }

        PendingActionCard(
            pending = state.pending,
            onResolveCurrency = onResolveCurrency,
            onResolveAsk = onResolveAsk,
            onResolveTranslate = onResolveTranslate,
            onResolveInterpret = onResolveInterpret,
            onDismiss = onDismissPending
        )

        AttachmentPreview(
            uri = pendingImage,
            onClear = onClearPendingImage
        )

        InputBar(
            input = input,
            onInputChange = onInputChange,
            isResponding = state.isResponding,
            isListening = state.isListening,
            canSend = input.isNotBlank() || pendingImage != null,
            onCamera = onCamera,
            onGallery = onGallery,
            onMic = onMic,
            onSend = onSend,
            onCancel = onCancel
        )
    }
}

@Composable
private fun SessionDrawerContent(
    sessions: List<ChatSessionEntity>,
    currentId: Long?,
    onNewChat: () -> Unit,
    onSelect: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.chat_sessions_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = NomadSilver
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NomadRoyal)
                .clickable(onClick = onNewChat)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = NomadSilver,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.chat_new_session),
                style = MaterialTheme.typography.labelLarge.copy(color = NomadSilver),
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(sessions, key = { it.id }) { s ->
                val selected = s.id == currentId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) NomadRoyal.copy(alpha = 0.22f)
                            else Color.White.copy(alpha = 0.04f)
                        )
                        .clickable { onSelect(s.id) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = s.title,
                        modifier = Modifier.weight(1f),
                        color = NomadSilver,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onDelete(s.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.chat_session_delete),
                            tint = NomadMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatTopBar(onOpenDrawer: () -> Unit, onOpenTranslate: () -> Unit, onOpenSettings: () -> Unit) {
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
                .clickable(onClick = onOpenDrawer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = stringResource(R.string.chat_session_menu),
                tint = NomadMist,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "NOMAD AI",
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
                    text = stringResource(R.string.chat_top_status),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .clickable(onClick = onOpenTranslate),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Translate,
                contentDescription = stringResource(R.string.translate_title),
                tint = NomadMist,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.size(8.dp))
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
                contentDescription = stringResource(R.string.chat_settings_desc),
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
                painter = painterResource(R.drawable.ic_launcher_logo),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.size(20.dp))
        Text(
            text = stringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = stringResource(R.string.chat_empty_body),
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
        stringResource(R.string.chat_suggestion_1),
        stringResource(R.string.chat_suggestion_2),
        stringResource(R.string.chat_suggestion_3)
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
private fun MessageRow(
    msg: ChatMessage,
    menuImageUri: Uri? = null,
    onOpenMenuView: (Uri, String) -> Unit = { _, _ -> }
) {
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
        Box(
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
                if (msg.imageUri != null) {
                    ImageMessageBubble(msg, isUser)
                } else if (!(isUser && msg.text.isBlank())) {
                    MessageBubble(msg, isUser)
                }
                if (!isUser && msg.toolTag == "menu_translate" && menuImageUri != null && !msg.streaming) {
                    Spacer(Modifier.size(6.dp))
                    MenuViewButton(
                        onClick = { onOpenMenuView(menuImageUri, msg.text) }
                    )
                }
                msg.toolTag?.takeIf { !isUser && it != "error" }?.let { tag ->
                    val label = toolTagLabel(tag)
                    if (label != null) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = NomadMuted,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuViewButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(listOf(NomadRoyal, NomadGlow))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.PhotoLibrary,
            contentDescription = null,
            tint = NomadSilver,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.menu_view_button),
            style = MaterialTheme.typography.labelLarge.copy(
                color = NomadSilver,
                fontWeight = FontWeight.SemiBold
            )
        )
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
    if (msg.pending && !isUser) {
        PendingBubble(msg.id)
        return
    }

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
        val rendered = remember(msg.text, msg.streaming, isUser) {
            if (isUser) AnnotatedString(msg.text)
            else renderMarkdown(
                text = msg.text,
                appendCaret = msg.streaming
            )
        }
        Text(
            text = rendered,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = textColor,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        )
    }
}

@Composable
private fun ImageMessageBubble(msg: ChatMessage, isUser: Boolean) {
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    }
    val bg = if (isUser) NomadUserBubble else NomadAssistantBubble
    val textColor = NomadSilver

    Column(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .padding(6.dp)
    ) {
        msg.imageUri?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        }
        if (msg.text.isNotBlank()) {
            Spacer(Modifier.size(6.dp))
            val rendered = remember(msg.text, msg.streaming, isUser) {
                if (isUser) AnnotatedString(msg.text)
                else renderMarkdown(text = msg.text, appendCaret = msg.streaming)
            }
            Text(
                text = rendered,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = textColor,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

private val MD_INLINE = Regex(
    "(\\*\\*([^*\\n]+?)\\*\\*)|(__([^_\\n]+?)__)|(\\*([^*\\n]+?)\\*)|(_([^_\\n]+?)_)|(`([^`\\n]+?)`)"
)

private fun renderMarkdown(text: String, appendCaret: Boolean): AnnotatedString =
    buildAnnotatedString {
        val lines = text.split('\n')
        lines.forEachIndexed { index, rawLine ->
            if (index > 0) append('\n')
            val leadingSpaces = rawLine.takeWhile { it == ' ' }.length
            val trimmed = rawLine.drop(leadingSpaces)
            val indent = " ".repeat(leadingSpaces)
            when {
                trimmed.startsWith("### ") -> {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        )
                    ) {
                        appendInlineMd(indent + trimmed.removePrefix("### "))
                    }
                }
                trimmed.startsWith("## ") -> {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    ) {
                        appendInlineMd(indent + trimmed.removePrefix("## "))
                    }
                }
                trimmed.startsWith("# ") -> {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    ) {
                        appendInlineMd(indent + trimmed.removePrefix("# "))
                    }
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    append(indent)
                    append("• ")
                    appendInlineMd(trimmed.drop(2))
                }
                trimmed.matches(Regex("^\\d+\\. .*")) -> {
                    append(indent)
                    val dot = trimmed.indexOf(". ")
                    append(trimmed.substring(0, dot + 2))
                    appendInlineMd(trimmed.substring(dot + 2))
                }
                trimmed.startsWith("> ") -> {
                    append(indent)
                    withStyle(SpanStyle(color = Color(0xFFB7B0D4), fontStyle = FontStyle.Italic)) {
                        appendInlineMd(trimmed.removePrefix("> "))
                    }
                }
                else -> appendInlineMd(rawLine)
            }
        }
        if (appendCaret) append("▍")
    }

private fun AnnotatedString.Builder.appendInlineMd(line: String) {
    if (line.isEmpty()) return
    var cursor = 0
    for (match in MD_INLINE.findAll(line)) {
        if (match.range.first > cursor) {
            append(line.substring(cursor, match.range.first))
        }
        val g = match.groupValues
        when {
            g[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(g[2])
            }
            g[3].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(g[4])
            }
            g[5].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(g[6])
            }
            g[7].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(g[8])
            }
            g[9].isNotEmpty() -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color.White.copy(alpha = 0.10f)
                )
            ) {
                append(g[10])
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < line.length) append(line.substring(cursor))
}

@Composable
private fun PendingBubble(key: String) {
    var visible by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        delay(350)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(260)),
        exit = fadeOut(tween(160))
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 20.dp,
                        bottomStart = 20.dp,
                        bottomEnd = 20.dp
                    )
                )
                .background(NomadAssistantBubble)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            TypingDots()
        }
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.height(22.dp)
    ) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 620,
                        delayMillis = i * 180,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$i"
            )
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
            Text(
                text = stringResource(R.string.chat_image_attached),
                style = MaterialTheme.typography.bodySmall
            )
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
    isResponding: Boolean,
    isListening: Boolean,
    canSend: Boolean,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    var showAttachMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        // Attach menu popup
        AnimatedVisibility(
            visible = showAttachMenu,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(150))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AttachOptionButton(
                    icon = { Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = NomadGlow, modifier = Modifier.size(20.dp)) },
                    label = stringResource(R.string.camera),
                    onClick = {
                        showAttachMenu = false
                        onCamera()
                    }
                )
                AttachOptionButton(
                    icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = NomadGlow, modifier = Modifier.size(20.dp)) },
                    label = stringResource(R.string.gallery),
                    onClick = {
                        showAttachMenu = false
                        onGallery()
                    }
                )
            }
        }

        // Main input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // + button (hamburger for attachments)
            CircleIconButton(onClick = { showAttachMenu = !showAttachMenu }) {
                Icon(
                    imageVector = if (showAttachMenu) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = null,
                    tint = NomadMist,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Text field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(NomadInputField)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (input.isEmpty()) {
                    Text(
                        text = stringResource(R.string.chat_input_placeholder),
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

            // Mic button
            CircleIconButton(onClick = onMic) {
                Icon(
                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = stringResource(R.string.translate_mic),
                    tint = if (isListening) NomadGlow else NomadMist,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Send / Cancel button
            val active = isResponding || canSend
            val bgBrush = if (active) {
                Brush.linearGradient(listOf(NomadRoyal, NomadGlow))
            } else {
                SolidColor(Color.White.copy(alpha = 0.1f))
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(bgBrush)
                    .clickable(enabled = active) {
                        if (isResponding) onCancel() else onSend()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isResponding) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.send),
                    tint = if (active) NomadSilver else NomadMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachOptionButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NomadInputField)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = NomadSilver,
                fontWeight = FontWeight.Medium
            )
        )
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

@Composable
private fun toolTagLabel(tag: String): String? = when (tag) {
    "menu_translate" -> stringResource(R.string.tool_label_menu_translate)
    "expense" -> stringResource(R.string.tool_label_expense)
    "cancelled" -> stringResource(R.string.tool_label_cancelled)
    "currency", "currency_loading", "currency_result" ->
        stringResource(R.string.tool_label_currency)
    "ask" -> stringResource(R.string.tool_label_ask)
    "chat", "travel", "menu_search" -> stringResource(R.string.tool_label_chat)
    "translate" -> stringResource(R.string.tool_label_translate)
    "interpret" -> stringResource(R.string.tool_label_interpret)
    else -> null
}

@Composable
private fun PendingActionCard(
    pending: PendingAction?,
    onResolveCurrency: (Boolean) -> Unit,
    onResolveAsk: (String) -> Unit,
    onResolveTranslate: (com.nomad.travel.tools.ToolTags.TranslateCall) -> Unit,
    onResolveInterpret: (com.nomad.travel.tools.ToolTags.InterpretCall) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(visible = pending != null) {
        if (pending == null) return@AnimatedVisibility
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(NomadInputField)
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(18.dp)
                )
                .padding(14.dp)
        ) {
            when {
                pending.currency != null -> CurrencyChoiceContent(
                    call = pending.currency,
                    onResolve = onResolveCurrency,
                    onDismiss = onDismiss
                )
                pending.ask != null -> AskChoiceContent(
                    call = pending.ask,
                    onPick = onResolveAsk,
                    onDismiss = onDismiss
                )
                pending.translate != null -> TranslateActionContent(
                    call = pending.translate,
                    onGo = { onResolveTranslate(pending.translate) },
                    onDismiss = onDismiss
                )
                pending.interpret != null -> InterpretActionContent(
                    call = pending.interpret,
                    onGo = { onResolveInterpret(pending.interpret) },
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun CurrencyChoiceContent(
    call: com.nomad.travel.tools.ToolTags.CurrencyCall,
    onResolve: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.currency_choice_title),
            style = MaterialTheme.typography.labelLarge.copy(color = NomadSilver),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = NomadMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
    Spacer(Modifier.size(4.dp))
    val amountLabel = remember(call) {
        val f = java.text.DecimalFormat("#,##0.##")
        "${f.format(call.amount)} ${call.from} → ${call.to}"
    }
    Text(
        text = amountLabel,
        style = MaterialTheme.typography.bodyMedium.copy(color = NomadMist)
    )
    Spacer(Modifier.size(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceButton(
            label = stringResource(R.string.currency_choice_live),
            primary = true,
            modifier = Modifier.weight(1f),
            onClick = { onResolve(true) }
        )
        ChoiceButton(
            label = stringResource(R.string.currency_choice_estimated),
            primary = false,
            modifier = Modifier.weight(1f),
            onClick = { onResolve(false) }
        )
    }
}

@Composable
private fun AskChoiceContent(
    call: com.nomad.travel.tools.ToolTags.AskCall,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = call.prompt,
            style = MaterialTheme.typography.labelLarge.copy(color = NomadSilver),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = NomadMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
    Spacer(Modifier.size(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        call.options.forEach { opt ->
            ChoiceButton(
                label = opt,
                primary = false,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onPick(opt) }
            )
        }
    }
}

@Composable
private fun ChoiceButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (primary) {
        Brush.linearGradient(listOf(NomadRoyal, NomadGlow))
    } else {
        SolidColor(Color.White.copy(alpha = 0.08f))
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                color = NomadSilver,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun UpdateFloatingCard(
    bannerState: UpdateBannerState,
    onUpdate: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NomadAssistantBubble.copy(alpha = 0.97f))
            .padding(14.dp)
    ) {
        when (bannerState) {
            is UpdateBannerState.Available -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = NomadGlow,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                R.string.update_available_title,
                                bannerState.release.versionName
                            ),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = NomadSilver,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = stringResource(R.string.update_available_desc),
                            style = MaterialTheme.typography.bodySmall.copy(color = NomadMist)
                        )
                    }
                    Text(
                        text = stringResource(R.string.update_action),
                        color = NomadGlow,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NomadGlow.copy(alpha = 0.12f))
                            .clickable(onClick = onUpdate)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.update_skip),
                        tint = NomadMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(onClick = onSkip)
                    )
                }
            }
            is UpdateBannerState.Downloading -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = NomadGlow,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(
                                R.string.update_downloading,
                                (bannerState.progress * 100).toInt()
                            ),
                            style = MaterialTheme.typography.bodyMedium.copy(color = NomadSilver)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { bannerState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = NomadGlow,
                        trackColor = NomadMuted.copy(alpha = 0.3f)
                    )
                }
            }
            is UpdateBannerState.Installing -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = NomadGlow,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.update_install_now),
                        style = MaterialTheme.typography.bodyMedium.copy(color = NomadSilver)
                    )
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun TranslateActionContent(
    call: com.nomad.travel.tools.ToolTags.TranslateCall,
    onGo: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Translate, contentDescription = null, tint = NomadGlow, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.translate_action_title),
            style = MaterialTheme.typography.labelLarge.copy(color = NomadSilver),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = NomadMuted, modifier = Modifier.size(14.dp))
        }
    }
    Spacer(Modifier.size(4.dp))
    Text(
        text = "${call.src.uppercase()} → ${call.tgt.uppercase()}",
        style = MaterialTheme.typography.bodyMedium.copy(color = NomadMist)
    )
    Spacer(Modifier.size(10.dp))
    ChoiceButton(
        label = stringResource(R.string.translate_action_go),
        primary = true,
        modifier = Modifier.fillMaxWidth(),
        onClick = onGo
    )
}

@Composable
private fun InterpretActionContent(
    call: com.nomad.travel.tools.ToolTags.InterpretCall,
    onGo: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Group, contentDescription = null, tint = NomadGlow, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.interpret_action_title),
            style = MaterialTheme.typography.labelLarge.copy(color = NomadSilver),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = NomadMuted, modifier = Modifier.size(14.dp))
        }
    }
    Spacer(Modifier.size(4.dp))
    Text(
        text = "${call.src.uppercase()} ⇄ ${call.tgt.uppercase()}",
        style = MaterialTheme.typography.bodyMedium.copy(color = NomadMist)
    )
    Spacer(Modifier.size(10.dp))
    ChoiceButton(
        label = stringResource(R.string.interpret_action_go),
        primary = true,
        modifier = Modifier.fillMaxWidth(),
        onClick = onGo
    )
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

/* ── Translate mode picker sheet ── */

@Composable
private fun TranslateModeSheet(
    onDismiss: () -> Unit,
    onTranslate: () -> Unit,
    onInterpret: () -> Unit
) {
    // Scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss)
    )
    // Sheet
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(NomadInputField)
                .clickable(enabled = false) {} // block scrim clicks
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            // Handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NomadMuted.copy(alpha = 0.5f))
            )
            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.translate_sheet_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    color = NomadSilver,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(Modifier.height(16.dp))

            // Mode 1: Text translate
            TranslateModeCard(
                icon = { Icon(Icons.Default.TextFields, contentDescription = null, tint = NomadGlow, modifier = Modifier.size(28.dp)) },
                title = stringResource(R.string.translate_mode_text_title),
                desc = stringResource(R.string.translate_mode_text_desc),
                onClick = onTranslate
            )
            Spacer(Modifier.height(10.dp))

            // Mode 2: Interpret
            TranslateModeCard(
                icon = { Icon(Icons.Default.Group, contentDescription = null, tint = NomadGlow, modifier = Modifier.size(28.dp)) },
                title = stringResource(R.string.translate_mode_interpret_title),
                desc = stringResource(R.string.translate_mode_interpret_desc),
                onClick = onInterpret
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TranslateModeCard(
    icon: @Composable () -> Unit,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NomadRoyal.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = NomadSilver,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(color = NomadMist)
            )
        }
    }
}
