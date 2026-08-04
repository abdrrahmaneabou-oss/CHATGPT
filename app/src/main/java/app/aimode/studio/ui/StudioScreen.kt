package app.aimode.studio.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Troubleshoot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aimode.studio.R
import app.aimode.studio.domain.PromptEngine
import app.aimode.studio.model.AnswerShape
import app.aimode.studio.model.PrecisionControl
import app.aimode.studio.model.Readiness
import app.aimode.studio.model.ReadinessGap
import app.aimode.studio.model.StudioUiState
import app.aimode.studio.model.ThinkingLens
import app.aimode.studio.model.VisualAsset
import app.aimode.studio.model.Workspace
import app.aimode.studio.ui.StudioViewModel.StudioEvent
import app.aimode.studio.ui.theme.Acid
import app.aimode.studio.ui.theme.Ink
import app.aimode.studio.ui.theme.Iris
import app.aimode.studio.ui.theme.Solar
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    onOpenAiMode: () -> Unit,
    onShareBoard: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workspace = state.workspace
    val isArabic = LocalLayoutDirection.current == LayoutDirection.Rtl || Locale.getDefault().language == "ar"
    val prompt = remember(workspace, isArabic) { PromptEngine.compile(workspace, isArabic) }
    val readiness = remember(workspace) { PromptEngine.readiness(workspace) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showResetDialog by remember { mutableStateOf(false) }
    var showPrompt by remember { mutableStateOf(false) }

    val copiedMessage = stringResource(R.string.copied)
    val importFailedMessage = stringResource(R.string.import_failed)
    val maxImagesMessage = stringResource(R.string.max_images)
    val emptyPromptMessage = stringResource(R.string.prompt_empty)
    val boardSavedMessage = stringResource(R.string.board_saved)
    val exportFailedMessage = stringResource(R.string.export_failed)
    val removedMessage = stringResource(R.string.remove_image)
    val undoLabel = stringResource(R.string.undo)
    val storageDeniedMessage = stringResource(R.string.storage_denied)

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(5),
    ) { uris -> viewModel.importVisuals(uris) }

    var launchAfterPermission by remember { mutableStateOf(false) }
    val storagePermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (launchAfterPermission) {
            launchAfterPermission = false
            if (granted) viewModel.prepareAndLaunch(isArabic, exportBoard = true)
            else {
                scope.launch { snackbarHostState.showSnackbar(storageDeniedMessage) }
                viewModel.prepareAndLaunch(isArabic, exportBoard = false)
            }
        }
    }

    fun beginLaunch() {
        val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            workspace.visuals.isNotEmpty() &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission) {
            launchAfterPermission = true
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.prepareAndLaunch(isArabic, exportBoard = true)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                StudioEvent.Copied -> snackbarHostState.showSnackbar(copiedMessage)
                StudioEvent.EmptyPrompt -> snackbarHostState.showSnackbar(emptyPromptMessage)
                StudioEvent.ImportFailed -> snackbarHostState.showSnackbar(importFailedMessage)
                StudioEvent.MaxImages -> snackbarHostState.showSnackbar(maxImagesMessage)
                StudioEvent.VisualRemoved -> {
                    val result = snackbarHostState.showSnackbar(removedMessage, actionLabel = undoLabel)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove()
                }
                is StudioEvent.LaunchPrepared -> {
                    onOpenAiMode()
                    val message = when {
                        event.boardFailed -> exportFailedMessage
                        event.boardUri != null -> boardSavedMessage
                        else -> copiedMessage
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(WindowInsets.safeDrawing.asPaddingValues()),
    ) {
        ContextAtmosphere()
        val wide = maxWidth >= 820.dp

        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item { StudioHeader(onReset = { showResetDialog = true }) }
                    item { ContextHero(readiness) }
                    item { LensSection(workspace.lens, viewModel::selectLens) }
                    item {
                        VisualSection(
                            state = state,
                            onAdd = {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onSelect = viewModel::selectVisual,
                            onCaption = viewModel::setVisualCaption,
                            onMove = viewModel::moveVisual,
                            onRemove = viewModel::removeVisual,
                        )
                    }
                    item {
                        GoalSection(
                            workspace = workspace,
                            prompt = prompt,
                            showPrompt = showPrompt,
                            onTogglePrompt = { showPrompt = !showPrompt },
                            onGoal = viewModel::setGoal,
                            onShape = viewModel::selectAnswerShape,
                            onPrecision = viewModel::togglePrecision,
                        )
                    }
                }

                CapsulePanel(
                    modifier = Modifier.width(360.dp).fillMaxHeight(),
                    state = state,
                    readiness = readiness,
                    prompt = prompt,
                    onLaunch = ::beginLaunch,
                    onCopy = { viewModel.copyPrompt(isArabic) },
                    onShare = state.lastBoardUri?.let { uri -> { onShareBoard(uri) } },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 132.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item { StudioHeader(onReset = { showResetDialog = true }) }
                item { ContextHero(readiness) }
                item { LensSection(workspace.lens, viewModel::selectLens) }
                item {
                    VisualSection(
                        state = state,
                        onAdd = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onSelect = viewModel::selectVisual,
                        onCaption = viewModel::setVisualCaption,
                        onMove = viewModel::moveVisual,
                        onRemove = viewModel::removeVisual,
                    )
                }
                item {
                    GoalSection(
                        workspace = workspace,
                        prompt = prompt,
                        showPrompt = showPrompt,
                        onTogglePrompt = { showPrompt = !showPrompt },
                        onGoal = viewModel::setGoal,
                        onShape = viewModel::selectAnswerShape,
                        onPrecision = viewModel::togglePrecision,
                    )
                }
                state.lastBoardUri?.let { uri ->
                    item {
                        TextButton(onClick = { onShareBoard(uri) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.share_board))
                        }
                    }
                }
            }

            LaunchDock(
                modifier = Modifier.align(Alignment.BottomCenter),
                state = state,
                readiness = readiness,
                onLaunch = ::beginLaunch,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (wide) 12.dp else 112.dp),
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.reset_body)) },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        viewModel.resetWorkspace()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.clear)) }
            },
        )
    }
}

@Composable
private fun ContextAtmosphere() {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(listOf(primary.copy(alpha = 0.09f), Color.Transparent)),
            radius = size.minDimension * 0.72f,
            center = Offset(size.width * 0.08f, size.height * 0.18f),
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(secondary.copy(alpha = 0.07f), Color.Transparent)),
            radius = size.minDimension * 0.64f,
            center = Offset(size.width * 0.92f, size.height * 0.74f),
        )
    }
}

@Composable
private fun StudioHeader(onReset: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OrbitMark()
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.brand_kicker),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.7.sp,
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.privacy_local), style = MaterialTheme.typography.labelLarge, fontSize = 11.sp)
            }
        }
        IconButton(onClick = onReset) {
            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.reset_workspace))
        }
    }
}

@Composable
private fun OrbitMark() {
    val surface = MaterialTheme.colorScheme.onBackground
    Canvas(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(surface)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Acid, radius = size.minDimension * 0.11f, center = center)
        drawArc(
            color = Color.White.copy(alpha = 0.72f),
            startAngle = -28f,
            sweepAngle = 238f,
            useCenter = false,
            topLeft = Offset(size.width * 0.17f, size.height * 0.28f),
            size = Size(size.width * 0.66f, size.height * 0.44f),
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
        )
        drawCircle(Solar, radius = size.minDimension * 0.055f, center = Offset(size.width * 0.78f, size.height * 0.42f))
    }
}

@Composable
private fun ContextHero(readiness: Readiness) {
    val shape = RoundedCornerShape(34.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.onBackground,
        contentColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth().drawBehind {
            drawCircle(Iris.copy(alpha = 0.32f), radius = size.minDimension * 0.62f, center = Offset(size.width, 0f))
            drawCircle(Solar.copy(alpha = 0.18f), radius = size.minDimension * 0.42f, center = Offset(0f, size.height))
            repeat(3) { index ->
                val inset = (index + 1) * 28.dp.toPx()
                drawArc(
                    color = Color.White.copy(alpha = 0.055f),
                    startAngle = 205f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(size.width - inset * 3f, -inset),
                    size = Size(inset * 3.5f, inset * 3.5f),
                    style = Stroke(1.dp.toPx()),
                )
            }
        }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.hero_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = Color(0xFFF8F3E9),
                    )
                    Text(
                        text = stringResource(R.string.hero_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFBDBAB3),
                    )
                }
                ReadinessDial(readiness)
            }
        }
    }
}

@Composable
private fun ReadinessDial(readiness: Readiness, compact: Boolean = false) {
    val animated by animateFloatAsState(
        targetValue = readiness.score / 100f,
        animationSpec = tween(700),
        label = "readiness",
    )
    val description = "${stringResource(R.string.readiness)} ${readiness.score}%"
    val diameter = if (compact) 52.dp else 92.dp
    Box(
        modifier = Modifier
            .requiredSize(diameter)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(animated, 0f..1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = if (compact) 4.dp.toPx() else 7.dp.toPx()
            val pad = stroke / 2f
            val bounds = Rect(pad, pad, size.width - pad, size.height - pad)
            drawArc(
                color = Color.White.copy(alpha = 0.14f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = bounds.topLeft,
                size = bounds.size,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(Acid, Solar, Acid)),
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = bounds.topLeft,
                size = bounds.size,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "${readiness.score}",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = if (compact) 14.sp else 25.sp,
        )
    }
}

@Composable
private fun LensSection(selected: ThinkingLens, onSelect: (ThinkingLens) -> Unit) {
    SectionIntro(R.string.lens_title, R.string.lens_subtitle)
    Spacer(Modifier.height(10.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(ThinkingLens.entries) { lens ->
            LensCard(lens = lens, selected = lens == selected, onClick = { onSelect(lens) })
        }
    }
}

@Composable
private fun LensCard(lens: ThinkingLens, selected: Boolean, onClick: () -> Unit) {
    val title = lensTitle(lens)
    val icon = when (lens) {
        ThinkingLens.ANALYZE -> Icons.Rounded.Analytics
        ThinkingLens.COMPARE -> Icons.AutoMirrored.Rounded.CompareArrows
        ThinkingLens.EXTRACT -> Icons.Rounded.DataObject
        ThinkingLens.CREATE -> Icons.Rounded.Lightbulb
        ThinkingLens.SOLVE -> Icons.Rounded.Troubleshoot
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.width(112.dp).padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(23.dp))
                if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Acid, modifier = Modifier.size(18.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun VisualSection(
    state: StudioUiState,
    onAdd: () -> Unit,
    onSelect: (String) -> Unit,
    onCaption: (String, String) -> Unit,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
) {
    val workspace = state.workspace
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionIntro(R.string.visuals_title, R.string.visuals_subtitle)
                }
                IconButton(onClick = onAdd, enabled = !state.isImporting && workspace.visuals.size < 5) {
                    if (state.isImporting) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = stringResource(R.string.add_visuals))
                    }
                }
            }

            if (workspace.visuals.isEmpty()) {
                EmptyVisualWell(onAdd = onAdd, loading = state.isImporting)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(workspace.visuals, key = { _, visual -> visual.id }) { index, visual ->
                        VisualCard(
                            visual = visual,
                            number = index + 1,
                            selected = workspace.selectedVisualId == visual.id,
                            onClick = { onSelect(visual.id) },
                        )
                    }
                    if (workspace.visuals.size < 5) {
                        item { CompactAddVisual(onAdd, state.isImporting) }
                    }
                }
            }

            val selectedIndex = workspace.visuals.indexOfFirst { it.id == workspace.selectedVisualId }
            val selected = workspace.visuals.getOrNull(selectedIndex)
            AnimatedVisibility(selected != null) {
                selected?.let { visual ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            stringResource(R.string.image_number, selectedIndex + 1),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        OutlinedTextField(
                            value = visual.caption,
                            onValueChange = { onCaption(visual.id, it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.image_role_hint)) },
                            minLines = 1,
                            maxLines = 2,
                            shape = RoundedCornerShape(18.dp),
                            trailingIcon = {
                                Text(
                                    "${visual.caption.length}/120",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                )
                            },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { onMove(visual.id, -1) }, enabled = selectedIndex > 0) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.move_earlier))
                            }
                            IconButton(
                                onClick = { onMove(visual.id, 1) },
                                enabled = selectedIndex in 0 until workspace.visuals.lastIndex,
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = stringResource(R.string.move_later))
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { onRemove(visual.id) }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.remove_image),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyVisualWell(onAdd: () -> Unit, loading: Boolean) {
    val outline = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = outline.copy(alpha = 0.65f),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(11f, 9f))),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
                )
            }
            .clickable(enabled = !loading, onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Add, contentDescription = null)
                }
            }
            Text(stringResource(R.string.add_visuals), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun VisualCard(visual: VisualAsset, number: Int, selected: Boolean, onClick: () -> Unit) {
    val bitmap by rememberLocalThumbnail(visual.localPath)
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 118.dp, height = 148.dp),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Solar else MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(Modifier.fillMaxSize()) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = stringResource(R.string.image_number, number),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(9.dp)
                    .size(29.dp)
                    .clip(CircleShape)
                    .background(if (selected) Solar else Ink),
                contentAlignment = Alignment.Center,
            ) {
                Text(number.toString(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            if (visual.caption.isNotBlank()) {
                Text(
                    text = visual.caption,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Ink.copy(alpha = 0.84f)).padding(9.dp),
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CompactAddVisual(onAdd: () -> Unit, loading: Boolean) {
    Surface(
        onClick = onAdd,
        enabled = !loading,
        modifier = Modifier.size(width = 82.dp, height = 148.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_visuals))
        }
    }
}

@Composable
private fun GoalSection(
    workspace: Workspace,
    prompt: String,
    showPrompt: Boolean,
    onTogglePrompt: () -> Unit,
    onGoal: (String) -> Unit,
    onShape: (AnswerShape) -> Unit,
    onPrecision: (PrecisionControl) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionIntro(R.string.goal_title, R.string.goal_subtitle)
            OutlinedTextField(
                value = workspace.goal,
                onValueChange = onGoal,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.goal_hint)) },
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(22.dp),
                trailingIcon = {
                    Text(
                        "${workspace.goal.length}/1600",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                    )
                },
            )

            Text(stringResource(R.string.shape_title), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnswerShape.entries.forEach { shape ->
                    FilterChip(
                        selected = workspace.answerShape == shape,
                        onClick = { onShape(shape) },
                        label = { Text(shapeTitle(shape)) },
                        leadingIcon = if (workspace.answerShape == shape) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                    )
                }
            }

            Text(stringResource(R.string.precision_title), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrecisionControl.entries.forEach { control ->
                    FilterChip(
                        selected = control in workspace.precision,
                        onClick = { onPrecision(control) },
                        label = { Text(precisionTitle(control)) },
                    )
                }
            }

            TextButton(onClick = onTogglePrompt, enabled = prompt.isNotBlank()) {
                Icon(Icons.Rounded.Psychology, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (showPrompt) R.string.hide_full_prompt else R.string.preview_prompt))
            }
            AnimatedVisibility(showPrompt && prompt.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                ) {
                    Text(
                        text = prompt,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CapsulePanel(
    modifier: Modifier,
    state: StudioUiState,
    readiness: Readiness,
    prompt: String,
    onLaunch: () -> Unit,
    onCopy: () -> Unit,
    onShare: (() -> Unit)?,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(34.dp),
        color = MaterialTheme.colorScheme.onBackground,
        contentColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReadinessDial(readiness)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.readiness), color = Color(0xFFAAA79F), fontSize = 12.sp)
                    Text(readinessHint(readiness), color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            Text(stringResource(R.string.compiled_prompt), color = Acid, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.07f),
            ) {
                Text(
                    text = prompt.ifBlank { stringResource(R.string.goal_hint) },
                    modifier = Modifier.padding(18.dp),
                    color = if (prompt.isBlank()) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 18,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCopy, enabled = prompt.isNotBlank()) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.copy_only))
                }
                onShare?.let { share ->
                    TextButton(onClick = share) {
                        Icon(Icons.Rounded.Share, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.share_board))
                    }
                }
            }
            LaunchButton(
                loading = state.isLaunching,
                enabled = prompt.isNotBlank(),
                onClick = onLaunch,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Acid, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.workspace_private), color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun BoxScope.LaunchDock(
    modifier: Modifier,
    state: StudioUiState,
    readiness: Readiness,
    onLaunch: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.onBackground,
        contentColor = MaterialTheme.colorScheme.background,
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            ReadinessDial(readiness, compact = true)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.launch_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(
                    readinessHint(readiness),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LaunchButton(
                loading = state.isLaunching,
                enabled = state.workspace.goal.isNotBlank(),
                onClick = onLaunch,
            )
        }
    }
}

@Composable
private fun LaunchButton(
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(if (loading) R.string.launching else R.string.launch_action)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(Solar, Color(0xFFFF8149)))
                else Brush.linearGradient(listOf(Color(0xFF565551), Color(0xFF565551))),
            )
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
            }
            Text(description, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            if (!loading) Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun SectionIntro(title: Int, subtitle: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun lensTitle(lens: ThinkingLens): String = stringResource(
    when (lens) {
        ThinkingLens.ANALYZE -> R.string.lens_analyze
        ThinkingLens.COMPARE -> R.string.lens_compare
        ThinkingLens.EXTRACT -> R.string.lens_extract
        ThinkingLens.CREATE -> R.string.lens_create
        ThinkingLens.SOLVE -> R.string.lens_solve
    },
)

@Composable
private fun shapeTitle(shape: AnswerShape): String = stringResource(
    when (shape) {
        AnswerShape.BRIEF -> R.string.shape_brief
        AnswerShape.STEPS -> R.string.shape_steps
        AnswerShape.TABLE -> R.string.shape_table
        AnswerShape.DEEP_DIVE -> R.string.shape_deep
    },
)

@Composable
private fun precisionTitle(control: PrecisionControl): String = stringResource(
    when (control) {
        PrecisionControl.UNCERTAINTY -> R.string.precision_uncertainty
        PrecisionControl.IMAGE_REFERENCES -> R.string.precision_evidence
        PrecisionControl.ASK_BEFORE_ASSUMING -> R.string.precision_questions
    },
)

@Composable
private fun readinessHint(readiness: Readiness): String = stringResource(
    when (readiness.nextGap) {
        ReadinessGap.GOAL -> R.string.readiness_start
        ReadinessGap.SPECIFICITY -> R.string.readiness_goal
        ReadinessGap.VISUALS -> R.string.readiness_visuals
        ReadinessGap.VISUAL_LABELS -> R.string.readiness_labels
        null -> R.string.readiness_ready
    },
)

@Composable
private fun rememberLocalThumbnail(path: String) = produceState<androidx.compose.ui.graphics.ImageBitmap?>(
    initialValue = null,
    key1 = path,
) {
    value = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.isFile) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 560) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }
}
