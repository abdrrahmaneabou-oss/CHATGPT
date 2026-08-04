package app.aimode.studio.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aimode.studio.R
import app.aimode.studio.domain.MosaicImage
import app.aimode.studio.domain.MosaicPlanner
import app.aimode.studio.model.StudioUiState
import app.aimode.studio.model.VisualAsset
import app.aimode.studio.ui.StudioViewModel.StudioEvent
import app.aimode.studio.ui.theme.Acid
import app.aimode.studio.ui.theme.Ink
import app.aimode.studio.ui.theme.Iris
import app.aimode.studio.ui.theme.Paper
import app.aimode.studio.ui.theme.Solar
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    onOpenAiMode: () -> Unit,
    onShareBoard: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workspace = state.workspace
    val context = LocalContext.current
    val isArabic = LocalLayoutDirection.current == LayoutDirection.Rtl
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showResetDialog by remember { mutableStateOf(false) }
    var exportAfterPermission by remember { mutableStateOf(false) }

    val importFailedMessage = stringResource(R.string.import_failed)
    val maxImagesMessage = stringResource(R.string.max_images)
    val boardSavedMessage = stringResource(R.string.board_saved)
    val exportFailedMessage = stringResource(R.string.export_failed)
    val removedMessage = stringResource(R.string.remove_image)
    val undoLabel = stringResource(R.string.undo)
    val storageDeniedMessage = stringResource(R.string.storage_denied)

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(5),
    ) { uris -> viewModel.importVisuals(uris) }

    fun chooseImages() {
        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val storagePermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (exportAfterPermission) {
            exportAfterPermission = false
            if (granted) viewModel.exportBoard(isArabic)
            else scope.launch { snackbarHostState.showSnackbar(storageDeniedMessage) }
        }
    }

    fun exportBoard() {
        if (workspace.visuals.isEmpty()) return
        val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission) {
            exportAfterPermission = true
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.exportBoard(isArabic)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                StudioEvent.ImportFailed -> snackbarHostState.showSnackbar(importFailedMessage)
                StudioEvent.MaxImages -> snackbarHostState.showSnackbar(maxImagesMessage)
                StudioEvent.BoardExportFailed -> snackbarHostState.showSnackbar(exportFailedMessage)
                is StudioEvent.BoardExported -> snackbarHostState.showSnackbar(boardSavedMessage)
                StudioEvent.VisualRemoved -> {
                    val result = snackbarHostState.showSnackbar(removedMessage, actionLabel = undoLabel)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove()
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
        VisualAtmosphere()
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
                    item {
                        VisualHero(
                            imageCount = workspace.visuals.size,
                            onOpenAiMode = onOpenAiMode,
                        )
                    }
                    item {
                        VisualStudio(
                            state = state,
                            onAdd = ::chooseImages,
                            onSelect = viewModel::selectVisual,
                            onCaption = viewModel::setVisualCaption,
                            onMove = viewModel::moveVisual,
                            onRemove = viewModel::removeVisual,
                            onExport = ::exportBoard,
                            onShare = state.lastBoardUri?.let { uri -> { onShareBoard(uri) } },
                        )
                    }
                }
                PortalRail(
                    modifier = Modifier.width(360.dp).fillMaxHeight(),
                    state = state,
                    onOpenAiMode = onOpenAiMode,
                    onExport = ::exportBoard,
                    onShare = state.lastBoardUri?.let { uri -> { onShareBoard(uri) } },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item { StudioHeader(onReset = { showResetDialog = true }) }
                item {
                    VisualHero(
                        imageCount = workspace.visuals.size,
                        onOpenAiMode = onOpenAiMode,
                    )
                }
                item {
                    VisualStudio(
                        state = state,
                        onAdd = ::chooseImages,
                        onSelect = viewModel::selectVisual,
                        onCaption = viewModel::setVisualCaption,
                        onMove = viewModel::moveVisual,
                        onRemove = viewModel::removeVisual,
                        onExport = ::exportBoard,
                        onShare = state.lastBoardUri?.let { uri -> { onShareBoard(uri) } },
                    )
                }
                item { DirectPortalCard(onOpenAiMode = onOpenAiMode) }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
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
private fun VisualAtmosphere() {
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
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val showPrivacyLabel = maxWidth >= 390.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (showPrivacyLabel) 12.dp else 8.dp),
        ) {
            OrbitMark()
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.brand_kicker),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.7.sp,
                    maxLines = 1,
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = if (showPrivacyLabel) 11.dp else 9.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                    if (showPrivacyLabel) {
                        Text(stringResource(R.string.privacy_local), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            IconButton(onClick = onReset) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.reset_workspace))
            }
        }
    }
}

@Composable
private fun OrbitMark() {
    Canvas(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Ink)) {
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
private fun VisualHero(imageCount: Int, onOpenAiMode: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(34.dp),
        color = Ink,
        contentColor = Paper,
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().drawBehind {
                drawCircle(Iris.copy(alpha = 0.34f), radius = size.minDimension * 0.62f, center = Offset(size.width, 0f))
                drawCircle(Solar.copy(alpha = 0.20f), radius = size.minDimension * 0.44f, center = Offset(0f, size.height))
            },
        ) {
            val compact = maxWidth < 430.dp
            Column(
                modifier = Modifier.fillMaxWidth().padding(if (compact) 22.dp else 26.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = CircleShape, color = Acid, contentColor = Ink) {
                        Text(
                            text = stringResource(R.string.image_count, imageCount, 5),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        text = stringResource(R.string.hd_output_badge),
                        color = Paper.copy(alpha = 0.64f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = stringResource(R.string.hero_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = Color(0xFFF8F3E9),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.hero_body),
                    color = Color(0xFFBDBAB3),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                PortalButton(onClick = onOpenAiMode, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun VisualStudio(
    state: StudioUiState,
    onAdd: () -> Unit,
    onSelect: (String) -> Unit,
    onCaption: (String, String) -> Unit,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onExport: () -> Unit,
    onShare: (() -> Unit)?,
) {
    val visuals = state.workspace.visuals
    val selected = visuals.firstOrNull { it.id == state.workspace.selectedVisualId } ?: visuals.firstOrNull()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SectionIntro(R.string.visuals_title, R.string.visuals_subtitle)

            if (visuals.isEmpty()) {
                EmptyVisualBoard(onAdd = onAdd, loading = state.isImporting)
            } else {
                SmartMosaicPreview(
                    visuals = visuals,
                    selectedId = selected?.id,
                    onSelect = onSelect,
                )
                ImageOrderStrip(
                    visuals = visuals,
                    selectedId = selected?.id,
                    onSelect = onSelect,
                    onAdd = onAdd,
                    loading = state.isImporting,
                )
                selected?.let { visual ->
                    SelectedImageEditor(
                        visual = visual,
                        index = visuals.indexOfFirst { it.id == visual.id },
                        total = visuals.size,
                        onCaption = { onCaption(visual.id, it) },
                        onMove = { onMove(visual.id, it) },
                        onRemove = { onRemove(visual.id) },
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onAdd,
                    enabled = visuals.size < 5 && !state.isImporting,
                ) {
                    if (state.isImporting) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.add_visuals))
                }
                OutlinedButton(
                    onClick = onExport,
                    enabled = visuals.isNotEmpty() && !state.isExporting,
                ) {
                    if (state.isExporting) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.HighQuality, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(if (state.isExporting) R.string.exporting_board else R.string.export_hd_board))
                }
                onShare?.let { share ->
                    TextButton(onClick = share) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(stringResource(R.string.share_board))
                    }
                }
            }
            Text(
                text = stringResource(R.string.export_quality_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun EmptyVisualBoard(onAdd: () -> Unit, loading: Boolean) {
    Surface(
        onClick = onAdd,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth().height(270.dp),
        shape = RoundedCornerShape(28.dp),
        color = Ink,
        contentColor = Paper,
        border = BorderStroke(1.dp, Acid.copy(alpha = 0.32f)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().drawBehind {
                drawCircle(Iris.copy(alpha = 0.18f), radius = size.minDimension * 0.52f, center = Offset(size.width, 0f))
            },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = Acid, contentColor = Ink) {
                    Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                        if (loading) CircularProgressIndicator(Modifier.size(28.dp), color = Ink, strokeWidth = 3.dp)
                        else Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(31.dp))
                    }
                }
                Text(stringResource(R.string.empty_board_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.empty_board_body),
                    color = Paper.copy(alpha = 0.58f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun SmartMosaicPreview(
    visuals: List<VisualAsset>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    val plan = remember(visuals) {
        MosaicPlanner.plan(
            visuals.map { visual ->
                MosaicImage(
                    id = visual.id,
                    aspectRatio = if (visual.width > 0 && visual.height > 0) {
                        visual.width.toFloat() / visual.height
                    } else {
                        1f
                    },
                )
            },
        )
    }
    val visualById = remember(visuals) { visuals.associateBy { it.id } }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Ink,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().aspectRatio(plan.canvasAspectRatio).background(Ink),
        ) {
            plan.cells.forEachIndexed { index, cell ->
                val visual = visualById.getValue(cell.imageId)
                MosaicTile(
                    visual = visual,
                    number = index + 1,
                    selected = visual.id == selectedId,
                    onClick = { onSelect(visual.id) },
                    modifier = Modifier
                        .offset(x = maxWidth * cell.left, y = maxHeight * cell.top)
                        .size(width = maxWidth * cell.width, height = maxHeight * cell.height),
                )
            }
        }
    }
}

@Composable
private fun MosaicTile(
    visual: VisualAsset,
    number: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val image by rememberLocalThumbnail(visual.localPath)
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF25282D))
            .then(if (selected) Modifier.border(2.dp, Acid, shape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = stringResource(R.string.image_number, number),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } ?: CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center).size(24.dp),
            color = Acid,
            strokeWidth = 2.dp,
        )
        Box(
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(29.dp).clip(CircleShape).background(Ink.copy(alpha = 0.90f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), color = Acid, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        if (visual.caption.isNotBlank()) {
            Text(
                text = visual.caption,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Ink.copy(alpha = 0.80f)).padding(8.dp),
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ImageOrderStrip(
    visuals: List<VisualAsset>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    loading: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.image_order), style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(visuals, key = { _, item -> item.id }) { index, visual ->
                val image by rememberLocalThumbnail(visual.localPath)
                val selected = visual.id == selectedId
                Surface(
                    onClick = { onSelect(visual.id) },
                    modifier = Modifier.size(width = 82.dp, height = 110.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Solar else MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box {
                        image?.let {
                            Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Box(
                            modifier = Modifier.align(Alignment.TopStart).padding(7.dp).size(25.dp).clip(CircleShape).background(if (selected) Solar else Ink),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text((index + 1).toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        if (selected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp).size(19.dp).clip(CircleShape).background(Solar).padding(3.dp),
                            )
                        }
                    }
                }
            }
            if (visuals.size < 5) {
                item {
                    Surface(
                        onClick = onAdd,
                        enabled = !loading,
                        modifier = Modifier.size(width = 82.dp, height = 110.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = stringResource(R.string.add_visuals))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedImageEditor(
    visual: VisualAsset,
    index: Int,
    total: Int,
    onCaption: (String) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.image_number, index + 1), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.image_dimensions, visual.width, visual.height),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.move_earlier))
                }
                IconButton(onClick = { onMove(1) }, enabled = index < total - 1) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = stringResource(R.string.move_later))
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.remove_image), tint = MaterialTheme.colorScheme.error)
                }
            }
            OutlinedTextField(
                value = visual.caption,
                onValueChange = onCaption,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.image_label)) },
                placeholder = { Text(stringResource(R.string.image_label_hint)) },
                minLines = 1,
                maxLines = 2,
                shape = RoundedCornerShape(18.dp),
                supportingText = { Text(stringResource(R.string.image_label_note)) },
            )
        }
    }
}

@Composable
private fun PortalRail(
    modifier: Modifier,
    state: StudioUiState,
    onOpenAiMode: () -> Unit,
    onExport: () -> Unit,
    onShare: (() -> Unit)?,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(34.dp),
        color = Ink,
        contentColor = Paper,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Surface(shape = CircleShape, color = Acid, contentColor = Ink) {
                Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(26.dp))
                }
            }
            Text(stringResource(R.string.direct_portal_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.direct_portal_body),
                color = Paper.copy(alpha = 0.64f),
                style = MaterialTheme.typography.bodyMedium,
            )
            PortalButton(onClick = onOpenAiMode, modifier = Modifier.fillMaxWidth())
            if (state.workspace.visuals.isNotEmpty()) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                Text(stringResource(R.string.hd_mosaic_title), color = Acid, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.hd_mosaic_body), color = Paper.copy(alpha = 0.60f), fontSize = 12.sp)
                OutlinedButton(
                    onClick = onExport,
                    enabled = !state.isExporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isExporting) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(if (state.isExporting) R.string.exporting_board else R.string.export_hd_board))
                }
                onShare?.let { share ->
                    TextButton(onClick = share, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Share, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text(stringResource(R.string.share_board))
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectPortalCard(onOpenAiMode: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = Ink,
        contentColor = Paper,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = Acid, contentColor = Ink) {
                    Box(Modifier.size(45.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(21.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.launch_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.launch_subtitle), color = Paper.copy(alpha = 0.58f), fontSize = 12.sp)
                }
            }
            PortalButton(onClick = onOpenAiMode, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PortalButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.launch_action)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Solar, Color(0xFFFF8149))))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
            Text(
                description,
                modifier = Modifier.weight(1f),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
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
private fun rememberLocalThumbnail(path: String) = produceState<ImageBitmap?>(
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
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 900) sample *= 2
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@withContext null
        val rotation = runCatching {
            when (
                ExifInterface(path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (rotation == 0f) {
            decoded.asImageBitmap()
        } else {
            Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                Matrix().apply { postRotate(rotation) },
                true,
            ).also { if (it !== decoded) decoded.recycle() }.asImageBitmap()
        }
    }
}
