package com.nomad.travel.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nomad.travel.R
import com.nomad.travel.ui.NomadHaptics
import com.nomad.travel.ui.components.AiContentReportDialog
import com.nomad.travel.ui.components.NomadLogoSpinner
import com.nomad.travel.ui.theme.NomadGlow
import com.nomad.travel.ui.theme.NomadInputField
import com.nomad.travel.ui.theme.NomadMist
import com.nomad.travel.ui.theme.NomadMuted
import com.nomad.travel.ui.theme.NomadRoyal
import com.nomad.travel.ui.theme.NomadSilver
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

@Composable
fun CameraSearchScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: CameraSearchViewModel = viewModel(factory = CameraSearchViewModel.Factory)
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    var showQueue by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }
    var captureFlashTick by remember { mutableStateOf(0) }
    var reportTarget by remember { mutableStateOf<QueueItem?>(null) }
    val previewItem = state.queue.firstOrNull { it.id == state.instantPreviewItemId }
    var completedHapticIds by remember { mutableStateOf(emptySet<String>()) }
    var realtimeHapticItemId by remember { mutableStateOf<String?>(null) }
    var realtimeHapticTextLength by remember { mutableStateOf(0) }

    LaunchedEffect(state.queue, state.instantPreviewItemId) {
        val previewId = state.instantPreviewItemId
        val newlyCompleted = state.queue
            .filter { it.status == QueueStatus.DONE && it.id !in completedHapticIds }
        if (newlyCompleted.any { it.id != previewId }) {
            NomadHaptics.answerComplete(context)
        }
        if (newlyCompleted.isNotEmpty()) {
            completedHapticIds = completedHapticIds + newlyCompleted.map { it.id }
        }
        val existingIds = state.queue.map { it.id }.toSet()
        if (completedHapticIds.any { it !in existingIds }) {
            completedHapticIds = completedHapticIds.filterTo(mutableSetOf()) { it in existingIds }
        }
    }

    val previewItemId = previewItem?.id
    val previewAnswerLength = previewItem?.answer?.length ?: 0
    val previewStatus = previewItem?.status
    LaunchedEffect(previewItemId, previewAnswerLength, previewStatus) {
        if (previewItemId == null || previewStatus != QueueStatus.PROCESSING) {
            realtimeHapticItemId = null
            realtimeHapticTextLength = 0
            return@LaunchedEffect
        }
        val isNewPreviewStream = realtimeHapticItemId != previewItemId
        if (isNewPreviewStream) {
            realtimeHapticItemId = previewItemId
            realtimeHapticTextLength = 0
        }
        if (previewAnswerLength > realtimeHapticTextLength) {
            NomadHaptics.lightTick(context)
            realtimeHapticTextLength = previewAnswerLength
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        reportTarget?.let { target ->
            AiContentReportDialog(
                context = context,
                source = "camera_search",
                contentId = target.id,
                content = target.answer,
                onDismiss = { reportTarget = null }
            )
        }

        if (hasPermission) {
            CameraPreview(
                imageCapture = imageCapture,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PermissionPlaceholder(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )
        }

        TopBar(
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
        ) {
            AnimatedVisibility(
                visible = showQueue && state.queue.isNotEmpty(),
                enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(160))
            ) {
                QueueRail(
                    items = state.queue,
                    processingId = state.processingItemId,
                    deleteTargetId = deleteTargetId,
                    onTapItem = { id ->
                        deleteTargetId = null
                        vm.openInstantPreview(id)
                    },
                    onLongPressItem = { id ->
                        deleteTargetId = if (deleteTargetId == id) null else id
                    },
                    onDelete = { id ->
                        deleteTargetId = null
                        vm.deleteItem(id)
                    },
                    onMoveLeft = { vm.moveUp(it) },
                    onMoveRight = { vm.moveDown(it) }
                )
            }

            Box(modifier = Modifier.padding(bottom = 28.dp)) {
                BottomControls(
                    queueCount = state.queue.size,
                    queueOpen = showQueue,
                    onToggleQueue = { showQueue = !showQueue },
                    onOpenSettings = onOpenSettings,
                    onShutter = {
                        if (hasPermission) {
                            // Fire the visual feedback immediately on tap so the
                            // motion lines up with the user's gesture, not with
                            // when the JPEG finishes writing to disk.
                            captureFlashTick++
                            triggerCapture(context, imageCapture) { file ->
                                vm.enqueueCapture(context, file)
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                )
            }
        }

        CaptureFlash(trigger = captureFlashTick)

        previewItem?.let { item ->
            BackHandler { vm.closeInstantPreview() }
            DetailOverlay(
                item = item,
                onDismiss = { vm.closeInstantPreview() },
                onReport = { reportTarget = item }
            )
        }
    }
}

/**
 * Mechanical-shutter feedback: the screen briefly dims to black like an
 * iris closing, then snaps back. Driven entirely by [trigger] — increment
 * it on shutter tap to play the animation. ~180ms total.
 */
@Composable
private fun CaptureFlash(trigger: Int) {
    val shutter = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        shutter.snapTo(0f)
        shutter.animateTo(0.88f, tween(45, easing = LinearEasing))
        shutter.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
    }
    val alpha = shutter.value
    if (alpha <= 0f) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha))
    )
}

@Composable
private fun CameraPreview(imageCapture: ImageCapture, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val view = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            bindCamera(ctx, lifecycleOwner, view, imageCapture)
            view
        }
    )
}

private fun bindCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    imageCapture: ImageCapture
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        val provider = providerFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun triggerCapture(
    context: Context,
    imageCapture: ImageCapture,
    onResult: (File) -> Unit
) {
    val dir = File(context.cacheDir, "camera_search").apply { mkdirs() }
    val file = File(dir, "cap_${System.currentTimeMillis()}.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    val executor: Executor = ContextCompat.getMainExecutor(context)
    imageCapture.takePicture(
        options,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onResult(file)
            }
            override fun onError(exc: ImageCaptureException) {
                // Silently ignore; preview keeps running.
            }
        }
    )
}

@Composable
private fun TopBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = NomadSilver
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.camera_search_title),
            style = MaterialTheme.typography.titleMedium.copy(color = NomadSilver),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BottomControls(
    queueCount: Int,
    queueOpen: Boolean,
    onToggleQueue: () -> Unit,
    onOpenSettings: () -> Unit,
    onShutter: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QueueButton(count = queueCount, active = queueOpen, onClick = onToggleQueue)
        ShutterButton(onClick = onShutter)
        SmallControlButton(
            icon = Icons.Default.Settings,
            description = stringResource(R.string.settings_title),
            onClick = onOpenSettings
        )
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(78.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(NomadRoyal),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(R.string.camera_search_shutter),
                tint = NomadSilver,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun QueueButton(count: Int, active: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    if (active) NomadRoyal else Color.Black.copy(alpha = 0.5f)
                )
                .border(
                    1.dp,
                    if (active) NomadGlow else Color.White.copy(alpha = 0.18f),
                    CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = stringResource(R.string.camera_search_queue),
                tint = NomadSilver,
                modifier = Modifier.size(22.dp)
            )
        }
        if (count > 0) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp, end = 2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(NomadGlow),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (count > 99) "99+" else count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun SmallControlButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = NomadSilver, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun PermissionPlaceholder(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.permission_camera_denied),
            style = MaterialTheme.typography.titleMedium.copy(color = NomadSilver)
        )
        Spacer(Modifier.size(16.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(NomadRoyal)
                .clickable(onClick = onRequest)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(
                text = stringResource(R.string.camera_search_grant_permission),
                style = MaterialTheme.typography.labelLarge.copy(color = NomadSilver)
            )
        }
    }
}

/* ── Queue rail (inline above shutter) ───────────────────────── */

@Composable
private fun QueueRail(
    items: List<QueueItem>,
    processingId: String?,
    deleteTargetId: String?,
    onTapItem: (String) -> Unit,
    onLongPressItem: (String) -> Unit,
    onDelete: (String) -> Unit,
    onMoveLeft: (String) -> Unit,
    onMoveRight: (String) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) {
            listState.animateScrollToItem(items.lastIndex)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(Color.Black.copy(alpha = 0.35f))
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items, key = { it.id }) { item ->
                QueueRailItem(
                    item = item,
                    isFirst = items.first().id == item.id,
                    isLast = items.last().id == item.id,
                    isProcessing = item.id == processingId,
                    showDelete = deleteTargetId == item.id,
                    onTap = { onTapItem(item.id) },
                    onLongPress = { onLongPressItem(item.id) },
                    onDelete = { onDelete(item.id) },
                    onMoveLeft = { onMoveLeft(item.id) },
                    onMoveRight = { onMoveRight(item.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRailItem(
    item: QueueItem,
    isFirst: Boolean,
    isLast: Boolean,
    isProcessing: Boolean,
    showDelete: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit
) {
    val density = LocalDensity.current
    val swapThresholdPx = with(density) { 36.dp.toPx() }
    var dragOffsetPx by remember(item.id) { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .size(64.dp)
            .offset { IntOffset(dragOffsetPx.toInt(), 0) }
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black)
            .border(
                width = if (isProcessing || showDelete) 2.dp else 1.dp,
                color = if (showDelete) MaterialTheme.colorScheme.error else statusColor(item.status),
                shape = RoundedCornerShape(10.dp)
            )
            .pointerInput(item.id, isFirst, isLast) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragOffsetPx > swapThresholdPx && !isLast -> onMoveRight()
                            dragOffsetPx < -swapThresholdPx && !isFirst -> onMoveLeft()
                        }
                        dragOffsetPx = 0f
                    },
                    onDragCancel = { dragOffsetPx = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetPx += dragAmount
                    }
                )
            }
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
    ) {
        AsyncImage(
            model = File(item.imagePath),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(if (showDelete) Modifier.blur(4.dp) else Modifier)
        )

        // Status indicator (hidden when delete affordance is showing).
        if (!showDelete) {
            if (isProcessing) {
                NomadLogoSpinner(
                    modifier = Modifier.align(Alignment.Center),
                    size = 32.dp,
                    showHalo = false
                )
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor(item.status))
                        .border(1.dp, Color.Black.copy(alpha = 0.5f), CircleShape)
                )
            }
        }

        // Dim + centered trash on long-press.
        androidx.compose.animation.AnimatedVisibility(
            visible = showDelete,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showDelete,
                    enter = fadeIn(tween(140)) + scaleIn(tween(160), initialScale = 0.6f),
                    exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.6f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                            .clickable(onClick = onDelete),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = NomadSilver,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun statusColor(status: QueueStatus): Color =
    when (status) {
        QueueStatus.PENDING -> NomadMuted
        QueueStatus.PROCESSING -> NomadGlow
        QueueStatus.DONE -> NomadGlow
        QueueStatus.ERROR -> MaterialTheme.colorScheme.error
    }

@Composable
private fun IconControl(
    icon: ImageVector,
    description: String,
    tint: Color = NomadMist,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val effectiveTint = if (enabled) tint else tint.copy(alpha = 0.3f)
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.10f else 0.04f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = effectiveTint, modifier = Modifier.size(14.dp))
    }
}

/* ── Detail overlay ──────────────────────────────────────────── */

@Composable
private fun DetailOverlay(
    item: QueueItem,
    onDismiss: () -> Unit,
    onReport: () -> Unit
) {
    var actionsVisible by remember(item.id) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onDismiss)
    ) {
        AsyncImage(
            model = File(item.imagePath),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = NomadSilver)
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 360.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .clickable { actionsVisible = !actionsVisible }
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.camera_search_answer_title),
                        style = MaterialTheme.typography.labelMedium.copy(color = NomadGlow),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.status == QueueStatus.PROCESSING) {
                        NomadLogoSpinner(
                            size = 22.dp,
                            showHalo = false
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
                val text = when {
                    item.errorMessage != null && item.answer.isBlank() ->
                        item.errorMessage
                    item.answer.isBlank() && item.status == QueueStatus.PENDING ->
                        stringResource(R.string.camera_search_status_pending)
                    item.answer.isBlank() && item.status == QueueStatus.PROCESSING ->
                        stringResource(R.string.camera_search_status_processing)
                    else -> item.answer
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = NomadSilver,
                        lineHeight = 22.sp
                    )
                )
                if (actionsVisible) {
                    Spacer(Modifier.size(8.dp))
                    CameraAnswerMetaRow(
                        sentAt = remember(item.createdAtMs) { formatCameraTime(item.createdAtMs) },
                        canReport = item.answer.isNotBlank() && item.errorMessage == null,
                        onReport = onReport
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraAnswerMetaRow(
    sentAt: String,
    canReport: Boolean,
    onReport: () -> Unit
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
    }
}

private fun formatCameraTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
