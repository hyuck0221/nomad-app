package com.nomad.travel.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Mic
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nomad.travel.NomadApp
import com.nomad.travel.R
import com.nomad.travel.data.ChatMessage
import com.nomad.travel.data.Role
import com.nomad.travel.data.chat.ChatSessionEntity
import com.nomad.travel.ui.NomadHaptics
import com.nomad.travel.ui.components.AiContentReportDialog
import com.nomad.travel.ui.components.NomadLogoSpinner
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit = {},
    onOpenMenuView: (Uri, String) -> Unit = { _, _ -> },
    onOpenTranslate: () -> Unit = {},
    onOpenInterpret: () -> Unit = {},
    onOpenCameraSearch: () -> Unit = {},
    vm: ChatViewModel = viewModel(factory = ChatViewModel.Factory)
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var reportTarget by remember { mutableStateOf<ChatMessage?>(null) }
    val pendingImage = state.pendingImage

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { vm.attachImage(it) } }

    val cameraUri = remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) vm.attachImage(cameraUri.value) }

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
    var viewerImage by remember { mutableStateOf<Uri?>(null) }
    var voiceModeOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var lastHapticMessageId by remember { mutableStateOf<String?>(null) }
    var lastHapticTextLength by remember { mutableStateOf(0) }

    val streamingAssistant = state.messages.lastOrNull {
        it.role == Role.ASSISTANT && it.streaming
    }
    val streamingMessageId = streamingAssistant?.id
    val streamingTextLength = streamingAssistant?.text?.length ?: 0
    LaunchedEffect(streamingMessageId, streamingTextLength) {
        if (streamingMessageId == null) {
            lastHapticMessageId = null
            lastHapticTextLength = 0
            return@LaunchedEffect
        }
        val isNewStream = lastHapticMessageId != streamingMessageId
        if (isNewStream) {
            lastHapticMessageId = streamingMessageId
            lastHapticTextLength = 0
        }
        if (streamingTextLength > lastHapticTextLength) {
            NomadHaptics.lightTick(context)
            lastHapticTextLength = streamingTextLength
        }
    }

    val app = context.applicationContext as NomadApp
    val tts = remember { app.container.tts }
    val prefs = remember { app.container.prefs }
    val voiceLoopEnabled by prefs.voiceLoopEnabled.collectAsStateWithLifecycle(initialValue = true)
    val voiceModeOpenState = rememberUpdatedState(voiceModeOpen)
    val voiceLoopState = rememberUpdatedState(voiceLoopEnabled)
    val isRespondingState = rememberUpdatedState(state.isResponding)
    val isMutedState = rememberUpdatedState(state.isMuted)
    val spokenMessageIds = remember { mutableSetOf<String>() }
    var streamingSpeechMessageId by remember { mutableStateOf<String?>(null) }
    var streamingSpeechOffset by remember { mutableStateOf(0) }
    var streamingSpeechText by remember { mutableStateOf("") }
    var streamedSpeechActive by remember { mutableStateOf(false) }

    // STT: wire mic results.
    //  - In voice mode, suppress partial-text typing into the input box so the
    //    chat stays visible without churn. The final result auto-sends.
    //  - Outside voice mode, partials populate the input field as before.
    vm.onSttResult = { text ->
        if (!voiceModeOpenState.value) input = text
    }
    vm.onSttFinalResult = { text ->
        if (voiceModeOpenState.value && !isMutedState.value && text.isNotBlank()) {
            input = ""
            spokenMessageIds.clear()
            scope.launch {
                // Barge-in: cancel any in-flight response (also stops streaming
                // tokens) and stop TTS before starting a new turn.
                tts.stop()
                streamingSpeechMessageId = null
                streamingSpeechOffset = 0
                streamingSpeechText = ""
                streamedSpeechActive = false
                if (isRespondingState.value) vm.cancelAndAwait()
                vm.send(context, text, null, voiceMode = true)
            }
        }
    }

    // Stop TTS the instant the recognizer detects user speech, so the user
    // can talk over the assistant. Don't cancel the response stream here —
    // we wait for the final transcript to confirm it was real speech.
    vm.onSttSpeechStart = {
        if (voiceModeOpenState.value && !isMutedState.value) {
            tts.stop()
        }
    }

    // Speak streaming assistant text as soon as stable sentence/chunk boundaries appear.
    LaunchedEffect(voiceModeOpen, streamingMessageId, streamingTextLength) {
        if (!voiceModeOpen) {
            streamingSpeechMessageId = null
            streamingSpeechOffset = 0
            streamingSpeechText = ""
            streamedSpeechActive = false
            return@LaunchedEffect
        }
        val streaming = streamingAssistant ?: return@LaunchedEffect
        if (streamingSpeechMessageId != streaming.id) {
            streamingSpeechMessageId = streaming.id
            streamingSpeechOffset = 0
            streamingSpeechText = ""
            streamedSpeechActive = false
        }
        streamingSpeechText = streaming.text
        var next = nextSpeakableTtsChunk(streaming.text, streamingSpeechOffset, final = false)
        while (next != null) {
            tts.speakQueued(next.text, Locale.getDefault().language)
            streamingSpeechOffset = next.endIndex
            streamedSpeechActive = true
            next = nextSpeakableTtsChunk(streaming.text, streamingSpeechOffset, final = false)
        }
    }

    // Speak each new assistant reply while voice mode is open. If it already
    // streamed, only speak the residual tail that had no boundary yet.
    LaunchedEffect(voiceModeOpen, state.isResponding, state.messages.size) {
        if (!voiceModeOpen) return@LaunchedEffect
        if (state.isResponding) return@LaunchedEffect
        val last = state.messages.lastOrNull() ?: return@LaunchedEffect
        if (last.role != Role.ASSISTANT) return@LaunchedEffect
        if (last.pending || last.streaming) return@LaunchedEffect
        if (last.text.isBlank()) return@LaunchedEffect
        if (last.id in spokenMessageIds) return@LaunchedEffect
        spokenMessageIds.add(last.id)
        if (streamedSpeechActive) {
            var next = nextSpeakableTtsChunk(last.text, streamingSpeechOffset, final = true)
            while (next != null) {
                tts.speakQueued(next.text, Locale.getDefault().language)
                streamingSpeechOffset = next.endIndex
                next = nextSpeakableTtsChunk(last.text, streamingSpeechOffset, final = true)
            }
            streamingSpeechMessageId = null
            streamingSpeechOffset = 0
            streamingSpeechText = ""
            streamedSpeechActive = false
        } else {
            var offset = 0
            var next = nextSpeakableTtsChunk(last.text, offset, final = true)
            while (next != null) {
                tts.speakQueued(next.text, Locale.getDefault().language)
                offset = next.endIndex
                next = nextSpeakableTtsChunk(last.text, offset, final = true)
            }
        }
    }

    // The continuous recognizer (started by VoiceConversationDialog) already
    // re-arms itself after each session, so we no longer rely on TTS completion
    // to restart STT. Keep the callback null to avoid stacking restarts.
    DisposableEffect(Unit) {
        tts.onSpeakComplete = null
        onDispose { tts.onSpeakComplete = null }
    }

    reportTarget?.let { target ->
        AiContentReportDialog(
            context = context,
            source = "chat",
            contentId = target.id,
            content = target.text,
            onDismiss = { reportTarget = null }
        )
    }

    // Suppress unused-variable warnings for prefs flags that the dialog reads.
    @Suppress("UNUSED_EXPRESSION") voiceLoopState
    @Suppress("UNUSED_EXPRESSION") voiceLoopEnabled
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
                drawerContentColor = NomadSilver,
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .widthIn(max = 320.dp)
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
            onClearPendingImage = { vm.clearAttachedImage() },
            onOpenSettings = onOpenSettings,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onOpenTranslate = { showTranslateSheet = true },
            onOpenCameraSearch = onOpenCameraSearch,
            onSend = {
                vm.send(context, input, pendingImage)
                input = ""
                sendTick++
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
            },
            onCancel = { vm.cancelResponse() },
            onOpenMenuView = onOpenMenuView,
            onImageClick = { viewerImage = it },
            onReport = { reportTarget = it },
            onResolveCurrency = { live -> vm.resolveCurrency(live) },
            onResolveAsk = { option -> vm.resolveAsk(context, option) },
            onDismissPending = { vm.dismissPending() },
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
            onVoice = { voiceModeOpen = true },
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

    viewerImage?.let { uri ->
        FullScreenImageViewer(uri = uri, onDismiss = { viewerImage = null })
    }

    val voiceMicLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.startContinuousListening() }

    if (voiceModeOpen) {
        // Auto-start continuous listening when the dialog appears.
        LaunchedEffect(Unit) {
            val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) vm.startContinuousListening()
            else voiceMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        VoiceConversationDialog(
            isListening = state.isListening,
            isResponding = state.isResponding,
            isMuted = state.isMuted,
            micLevel = state.micLevel,
            onToggleMute = { vm.setMuted(!state.isMuted) },
            onDismiss = {
                vm.stopContinuousListening()
                tts.stop()
                spokenMessageIds.clear()
                vm.setMuted(false)
                voiceModeOpen = false
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
    onOpenCameraSearch: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onMic: () -> Unit,
    onVoice: () -> Unit,
    onOpenMenuView: (Uri, String) -> Unit,
    onImageClick: (Uri) -> Unit,
    onReport: (ChatMessage) -> Unit,
    onResolveCurrency: (Boolean) -> Unit,
    onResolveAsk: (String) -> Unit,
    onDismissPending: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        ChatTopBar(
            onOpenDrawer = onOpenDrawer,
            onOpenCameraSearch = onOpenCameraSearch,
            onOpenTranslate = onOpenTranslate,
            onOpenSettings = onOpenSettings
        )

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
        var selectionResetKey by remember { mutableStateOf(0) }
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
                EmptyState(
                    modifier = Modifier.matchParentSize(),
                    sessionKey = state.currentSessionId,
                    onChipClick = onInputChange
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                selectionResetKey++
                                focusManager.clearFocus()
                            })
                        },
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
                        val isUserMsg = msg.role == Role.USER
                        val shouldAnimate = isNew && (isUserMsg || msg.pending)

                        val animProgress = remember(msg.id) {
                            Animatable(if (shouldAnimate) 0f else 1f)
                        }
                        if (shouldAnimate) {
                            LaunchedEffect(msg.id) {
                                if (isUserMsg) {
                                    animProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                } else {
                                    animProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(
                                            durationMillis = 350,
                                            delayMillis = 400,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                }
                            }
                        }
                        val transformOriginX = if (isUserMsg) 1f else 0f
                        Box(
                            modifier = Modifier.graphicsLayer {
                                val p = animProgress.value
                                alpha = p
                                if (isUserMsg) {
                                    val scale = 0.6f + 0.4f * p
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = TransformOrigin(transformOriginX, 1f)
                                    translationY = (1f - p) * 28f
                                    translationX = (1f - p) * 24f
                                } else {
                                    translationY = (1f - p) * 40f
                                }
                            }
                        ) {
                            MessageRow(
                                msg = msg,
                                menuImageUri = priorImageUri,
                                onOpenMenuView = onOpenMenuView,
                                onImageClick = onImageClick,
                                selectionResetKey = selectionResetKey,
                                onClearTextSelection = { selectionResetKey++ },
                                onReport = { onReport(msg) }
                            )
                        }
                    }
                }
            }

        }

        Column(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.ime.union(WindowInsets.navigationBars)
            )
        ) {
            PendingActionCard(
                pending = state.pending,
                onResolveCurrency = onResolveCurrency,
                onResolveAsk = onResolveAsk,
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
                onCancel = onCancel,
                onVoice = onVoice
            )
        }
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
private fun ChatTopBar(
    onOpenDrawer: () -> Unit,
    onOpenCameraSearch: () -> Unit,
    onOpenTranslate: () -> Unit,
    onOpenSettings: () -> Unit
) {
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
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF7BC9A4))
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
                .clickable(onClick = onOpenCameraSearch),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.CameraAlt,
                contentDescription = stringResource(R.string.camera_search_open),
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
private fun EmptyState(
    modifier: Modifier = Modifier,
    sessionKey: Long? = null,
    onChipClick: (String) -> Unit = {}
) {
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
        SuggestionChips(sessionKey = sessionKey, onChipClick = onChipClick)
    }
}

@Composable
private fun SuggestionChips(
    sessionKey: Long?,
    onChipClick: (String) -> Unit
) {
    val pool = listOf(
        stringResource(R.string.chat_suggestion_1),
        stringResource(R.string.chat_suggestion_2),
        stringResource(R.string.chat_suggestion_3),
        stringResource(R.string.chat_suggestion_4),
        stringResource(R.string.chat_suggestion_5),
        stringResource(R.string.chat_suggestion_6),
        stringResource(R.string.chat_suggestion_7),
        stringResource(R.string.chat_suggestion_8)
    )
    val chips = remember(sessionKey, pool) { pool.shuffled().take(3) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { chip ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                    .clickable { onChipClick(chip) }
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
    onOpenMenuView: (Uri, String) -> Unit = { _, _ -> },
    onImageClick: (Uri) -> Unit = {},
    selectionResetKey: Int = 0,
    onClearTextSelection: () -> Unit = {},
    onReport: () -> Unit = {}
) {
    val isUser = msg.role == Role.USER
    val context = LocalContext.current
    var actionsVisible by remember(msg.id) { mutableStateOf(false) }
    val canReport = !isUser &&
        !msg.pending &&
        !msg.streaming &&
        msg.text.isNotBlank() &&
        msg.toolTag != "error"
    val copyText = remember(msg.text, isUser) {
        if (isUser) msg.text else stripTtsExpressionTags(msg.text)
    }
    val canCopy = copyText.isNotBlank() && !msg.pending && !msg.streaming
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            AssistantAvatar(thinking = msg.pending)
            Spacer(Modifier.size(2.dp))
        }
        Box(
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
                if (msg.imageUri != null) {
                    ImageMessageBubble(
                        msg = msg,
                        isUser = isUser,
                        onImageClick = onImageClick,
                        selectionResetKey = selectionResetKey,
                        onClick = {
                            onClearTextSelection()
                            actionsVisible = !actionsVisible
                        },
                        onCopyCode = { code -> context.copyToClipboard(code) }
                    )
                } else if (!(isUser && msg.text.isBlank())) {
                    MessageBubble(
                        msg = msg,
                        isUser = isUser,
                        selectionResetKey = selectionResetKey,
                        onClick = {
                            onClearTextSelection()
                            actionsVisible = !actionsVisible
                        },
                        onCopyCode = { code -> context.copyToClipboard(code) }
                    )
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
                if (actionsVisible && !msg.pending && !msg.streaming) {
                    Spacer(Modifier.size(4.dp))
                    MessageMetaRow(
                        sentAt = remember(msg.createdAt) { formatMessageTime(msg.createdAt) },
                        canReport = canReport,
                        canCopy = canCopy,
                        onReport = onReport,
                        onCopy = { context.copyToClipboard(copyText) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageMetaRow(
    sentAt: String,
    canReport: Boolean,
    canCopy: Boolean,
    onReport: () -> Unit,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.chat_message_sent_at, sentAt),
            style = MaterialTheme.typography.labelSmall.copy(color = NomadMuted)
        )
        if (canReport) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.ai_report_action),
                style = MaterialTheme.typography.labelSmall.copy(color = NomadMuted),
                modifier = Modifier.clickable(onClick = onReport)
            )
        }
        if (canCopy) {
            Spacer(Modifier.size(8.dp))
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.chat_copy_message),
                tint = NomadMuted,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onCopy)
            )
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
private fun AssistantAvatar(thinking: Boolean = false) {
    Crossfade(
        targetState = thinking,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "assistantAvatarMode"
    ) { isThinking ->
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isThinking) {
                NomadLogoSpinner(size = 40.dp, showHalo = true)
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    isUser: Boolean,
    selectionResetKey: Int,
    onClick: () -> Unit = {},
    onCopyCode: (String) -> Unit = {}
) {
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
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        MessageTextContent(
            text = if (isUser) msg.text else stripTtsExpressionTags(msg.text),
            isUser = isUser,
            appendCaret = msg.streaming,
            textColor = textColor,
            selectionResetKey = selectionResetKey,
            onCopyCode = onCopyCode
        )
    }
}

private fun formatMessageTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun ImageMessageBubble(
    msg: ChatMessage,
    isUser: Boolean,
    onImageClick: (Uri) -> Unit = {},
    selectionResetKey: Int,
    onClick: () -> Unit = {},
    onCopyCode: (String) -> Unit = {}
) {
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    }
    val bg = if (isUser) NomadUserBubble else NomadAssistantBubble
    val textColor = NomadSilver

    Column(
        modifier = Modifier
            .width(200.dp)
            .clip(shape)
            .background(bg)
    ) {
        msg.imageUri?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(200.dp)
                    .clickable { onImageClick(uri) }
            )
        }
        if (msg.text.isNotBlank()) {
            Box(
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                MessageTextContent(
                    text = if (isUser) msg.text else stripTtsExpressionTags(msg.text),
                    isUser = isUser,
                    appendCaret = msg.streaming,
                    textColor = textColor,
                    selectionResetKey = selectionResetKey,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    onCopyCode = onCopyCode
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .clickable(onClick = onClick)
            )
        }
    }
}

private sealed interface MarkdownBlock {
    data class Text(val text: String) : MarkdownBlock
    data class Code(val language: String, val code: String) : MarkdownBlock
}

@Composable
private fun MessageTextContent(
    text: String,
    isUser: Boolean,
    appendCaret: Boolean,
    textColor: Color,
    selectionResetKey: Int,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 22.sp,
    onCopyCode: (String) -> Unit
) {
    val displayText = remember(text, appendCaret) {
        if (appendCaret) "$text▍" else text
    }
    val blocks = remember(displayText) { parseMarkdownBlocks(displayText) }
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = textColor,
        fontSize = fontSize,
        lineHeight = lineHeight
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Text -> {
                    if (block.text.isNotBlank()) {
                        val rendered = remember(block.text, isUser) {
                            if (isUser) AnnotatedString(block.text) else renderMarkdown(block.text)
                        }
                        NomadSelectionContainer(selectionResetKey = selectionResetKey) {
                            Text(text = rendered, style = textStyle)
                        }
                    }
                }
                is MarkdownBlock.Code -> CodeBlock(
                    language = block.language,
                    code = block.code,
                    selectionResetKey = selectionResetKey,
                    onCopy = { onCopyCode(block.code.removeSuffix("▍")) }
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(
    language: String,
    code: String,
    selectionResetKey: Int,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.05f))
                .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall.copy(
                    color = NomadMuted,
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.chat_copy_code),
                tint = NomadMist,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onCopy)
            )
        }
        NomadSelectionContainer(selectionResetKey = selectionResetKey) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = NomadSilver,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp)
            )
        }
    }
}

@Composable
private fun NomadSelectionContainer(
    selectionResetKey: Int,
    content: @Composable () -> Unit
) {
    val colors = remember {
        TextSelectionColors(
            handleColor = Color(0xFF2CE0C5),
            backgroundColor = Color(0xFF2CE0C5).copy(alpha = 0.38f)
        )
    }
    CompositionLocalProvider(LocalTextSelectionColors provides colors) {
        key(selectionResetKey) {
            SelectionContainer(content = content)
        }
    }
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    if (!text.contains("```")) return listOf(MarkdownBlock.Text(text))

    val blocks = mutableListOf<MarkdownBlock>()
    val textPart = StringBuilder()
    val codePart = StringBuilder()
    var inCode = false
    var language = ""

    fun flushText() {
        if (textPart.isNotEmpty()) {
            blocks += MarkdownBlock.Text(textPart.toString().trimEnd('\n'))
            textPart.clear()
        }
    }

    fun flushCode() {
        blocks += MarkdownBlock.Code(language.trim(), codePart.toString().trimEnd('\n'))
        codePart.clear()
        language = ""
    }

    text.split('\n').forEach { line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (inCode) {
                flushCode()
                inCode = false
            } else {
                flushText()
                language = trimmed.removePrefix("```").trim()
                inCode = true
            }
        } else if (inCode) {
            codePart.append(line).append('\n')
        } else {
            textPart.append(line).append('\n')
        }
    }

    if (inCode) flushCode() else flushText()
    return blocks.ifEmpty { listOf(MarkdownBlock.Text(text)) }
}

private val MD_INLINE = Regex(
    "(\\*\\*([^*\\n]+?)\\*\\*)|(__([^_\\n]+?)__)|(\\*([^*\\n]+?)\\*)|(_([^_\\n]+?)_)|(`([^`\\n]+?)`)"
)

private fun renderMarkdown(text: String): AnnotatedString =
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
                    withStyle(SpanStyle(color = NomadMist, fontStyle = FontStyle.Italic)) {
                        appendInlineMd(trimmed.removePrefix("> "))
                    }
                }
                else -> appendInlineMd(rawLine)
            }
        }
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
        delay(250)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(260)),
        exit = fadeOut(tween(160))
    ) {
        Box(modifier = Modifier.padding(vertical = 8.dp)) {
            ThinkingLabel()
        }
    }
}

@Composable
private fun ThinkingLabel() {
    val transition = rememberInfiniteTransition(label = "thinkingLabel")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinkingAlpha"
    )
    Text(
        text = stringResource(R.string.thinking),
        style = MaterialTheme.typography.bodyMedium.copy(
            color = NomadMist.copy(alpha = alpha),
            fontWeight = FontWeight.Medium
        )
    )
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

private enum class InputBarAction { Cancel, Send, Voice }

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
    onCancel: () -> Unit,
    onVoice: () -> Unit
) {
    var showAttachMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                    imageVector = if (isListening) Icons.Outlined.Mic else Icons.Default.Mic,
                    contentDescription = stringResource(R.string.translate_mic),
                    tint = if (isListening) NomadGlow else NomadMist,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Send / Cancel / Voice button — voice replaces send when input is empty.
            val mode = when {
                isResponding -> InputBarAction.Cancel
                canSend -> InputBarAction.Send
                else -> InputBarAction.Voice
            }
            val bgBrush = Brush.linearGradient(listOf(NomadRoyal, NomadGlow))
            val (icon, desc) = when (mode) {
                InputBarAction.Cancel -> Icons.Default.Close to stringResource(R.string.send)
                InputBarAction.Send -> Icons.AutoMirrored.Filled.Send to stringResource(R.string.send)
                InputBarAction.Voice -> Icons.Default.GraphicEq to stringResource(R.string.chat_voice_open)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(bgBrush)
                    .clickable {
                        when (mode) {
                            InputBarAction.Cancel -> onCancel()
                            InputBarAction.Send -> onSend()
                            InputBarAction.Voice -> onVoice()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = desc,
                    tint = NomadSilver,
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
    "image_ocr" -> stringResource(R.string.tool_label_image_ocr)
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
private fun FullScreenImageViewer(uri: Uri, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(16.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = NomadSilver,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
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

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
    Toast.makeText(this, getString(R.string.chat_copied), Toast.LENGTH_SHORT).show()
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

private data class TtsChunk(val text: String, val endIndex: Int)

private val TTS_EXPRESSION_TAGS = Regex("</?(laugh|breath|sigh)\\s*/?>", RegexOption.IGNORE_CASE)

private fun stripTtsExpressionTags(text: String): String =
    TTS_EXPRESSION_TAGS.replace(text, "").replace(Regex("[ \\t]{2,}"), " ").trim()

private fun nextSpeakableTtsChunk(text: String, startIndex: Int, final: Boolean): TtsChunk? {
    if (startIndex >= text.length) return null
    val raw = text.substring(startIndex)
    val leading = raw.indexOfFirst { !it.isWhitespace() }
    if (leading < 0) return null
    val start = startIndex + leading
    val segment = text.substring(start)

    val sentenceEnd = segment.indexOfFirst { it in ".!?。！？\n" }
    if (sentenceEnd >= 0) {
        val end = (start + sentenceEnd + 1).coerceAtMost(text.length)
        val chunk = text.substring(start, end).trim()
        if (chunk.isNotBlank()) return TtsChunk(chunk, end)
    }

    val minChunk = 36
    if (!final && text.length - start >= minChunk) {
        val searchEnd = (start + 90).coerceAtMost(text.length)
        val space = text.substring(start, searchEnd).indexOfLast { it.isWhitespace() }
        if (space >= minChunk) {
            val end = start + space + 1
            val chunk = text.substring(start, end).trim()
            if (chunk.isNotBlank()) return TtsChunk(chunk, end)
        }
    }

    if (final) {
        val chunk = text.substring(start).trim()
        if (chunk.isNotBlank()) return TtsChunk(chunk, text.length)
    }
    return null
}
