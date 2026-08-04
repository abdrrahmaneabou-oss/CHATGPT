package app.aimode.studio.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.aimode.studio.R
import app.aimode.studio.ui.theme.Acid
import app.aimode.studio.ui.theme.Ink
import app.aimode.studio.ui.theme.Paper
import app.aimode.studio.ui.theme.Solar
import kotlinx.coroutines.delay

enum class PortalPresentation {
    Closed,
    Expanded,
    Minimized,
}

@Composable
fun AiPortalHost(
    presentation: PortalPresentation,
    onPresentationChange: (PortalPresentation) -> Unit,
    onOpenExternal: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = presentation != PortalPresentation.Closed,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(tween(220)) + slideInVertically(
            initialOffsetY = { it / 5 },
            animationSpec = tween(460, easing = FastOutSlowInEasing),
        ),
        exit = fadeOut(tween(180)) + slideOutVertically(
            targetOffsetY = { it / 7 },
            animationSpec = tween(280, easing = FastOutSlowInEasing),
        ),
    ) {
        PortalSession(
            presentation = presentation,
            onPresentationChange = onPresentationChange,
            onOpenExternal = onOpenExternal,
        )
    }
}

@Composable
private fun PortalSession(
    presentation: PortalPresentation,
    onPresentationChange: (PortalPresentation) -> Unit,
    onOpenExternal: (Uri) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentOpenExternal by rememberUpdatedState(onOpenExternal)
    var pageTitle by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf(AI_MODE_URL) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var mainFrameError by remember { mutableStateOf(false) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingWebPermission by remember { mutableStateOf<PermissionRequest?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        fileCallback?.onReceiveValue(uris.takeIf { it.isNotEmpty() }?.toTypedArray())
        fileCallback = null
    }

    val webPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val request = pendingWebPermission ?: return@rememberLauncherForActivityResult
        val approved = request.resources.filter { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> grants[Manifest.permission.CAMERA] == true
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> grants[Manifest.permission.RECORD_AUDIO] == true
                else -> false
            }
        }
        if (approved.isEmpty()) request.deny() else request.grant(approved.toTypedArray())
        pendingWebPermission = null
    }

    val webView = remember {
        val portalWebView = WebView(context)
        portalWebView.apply {
            setBackgroundColor(AndroidColor.WHITE)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                allowFileAccess = false
                allowContentAccess = true
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mediaPlaybackRequiresUserGesture = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
            }
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(portalWebView, true)
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    currentUrl = url ?: currentUrl
                    mainFrameError = false
                    updateNavigation(view) { back, forward ->
                        canGoBack = back
                        canGoForward = forward
                    }
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    currentUrl = url ?: currentUrl
                    updateNavigation(view) { back, forward ->
                        canGoBack = back
                        canGoForward = forward
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) mainFrameError = true
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    return when (uri.scheme?.lowercase()) {
                        "https" -> false
                        "http", "mailto", "tel", "market" -> {
                            currentOpenExternal(uri)
                            true
                        }
                        else -> true
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress.coerceIn(0, 100)
                    updateNavigation(view) { back, forward ->
                        canGoBack = back
                        canGoForward = forward
                    }
                }

                override fun onReceivedTitle(view: WebView, title: String?) {
                    pageTitle = title.orEmpty()
                }

                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams?,
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = filePathCallback
                    val acceptedTypes = fileChooserParams?.acceptTypes
                        ?.filter { it.isNotBlank() && '/' in it }
                        ?.toTypedArray()
                        ?.takeIf { it.isNotEmpty() }
                        ?: arrayOf("*/*")
                    return runCatching {
                        filePicker.launch(acceptedTypes)
                        true
                    }.getOrElse {
                        fileCallback?.onReceiveValue(null)
                        fileCallback = null
                        false
                    }
                }

                override fun onPermissionRequest(request: PermissionRequest) {
                    val host = request.origin.host.orEmpty()
                    val trustedOrigin = request.origin.scheme == "https" &&
                        (host == "google.com" || host.endsWith(".google.com"))
                    if (!trustedOrigin) {
                        request.deny()
                        return
                    }
                    val supportedResources = request.resources.filter {
                        it == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                            it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                    }
                    val requiredPermissions = supportedResources.mapNotNull {
                        when (it) {
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                            else -> null
                        }
                    }.distinct()
                    if (supportedResources.isEmpty() || requiredPermissions.isEmpty()) {
                        request.deny()
                        return
                    }
                    val allGranted = requiredPermissions.all {
                        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (allGranted) {
                        request.grant(supportedResources.toTypedArray())
                    } else {
                        pendingWebPermission?.deny()
                        pendingWebPermission = request
                        webPermissionLauncher.launch(requiredPermissions.toTypedArray())
                    }
                }

                override fun onPermissionRequestCanceled(request: PermissionRequest) {
                    if (pendingWebPermission == request) pendingWebPermission = null
                }
            }
            loadUrl(AI_MODE_URL)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            fileCallback?.onReceiveValue(null)
            fileCallback = null
            pendingWebPermission?.deny()
            pendingWebPermission = null
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
    }

    BackHandler(enabled = presentation != PortalPresentation.Closed) {
        when {
            presentation == PortalPresentation.Minimized -> {
                onPresentationChange(PortalPresentation.Expanded)
            }
            webView.canGoBack() -> webView.goBack()
            else -> onPresentationChange(PortalPresentation.Closed)
        }
    }

    PortalMorphSurface(
        presentation = presentation,
        pageTitle = pageTitle,
        currentUrl = currentUrl,
        progress = progress,
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        mainFrameError = mainFrameError,
        webView = webView,
        onPresentationChange = onPresentationChange,
        onOpenExternal = { currentOpenExternal(Uri.parse(currentUrl)) },
        onBack = { webView.goBack() },
        onForward = { webView.goForward() },
        onReload = {
            mainFrameError = false
            webView.reload()
        },
    )
}

@Composable
private fun PortalMorphSurface(
    presentation: PortalPresentation,
    pageTitle: String,
    currentUrl: String,
    progress: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    mainFrameError: Boolean,
    webView: WebView,
    onPresentationChange: (PortalPresentation) -> Unit,
    onOpenExternal: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
) {
    var expansionArmed by remember { mutableStateOf(false) }
    LaunchedEffect(presentation) {
        if (presentation == PortalPresentation.Expanded) {
            delay(85)
            expansionArmed = true
        } else {
            expansionArmed = false
        }
    }
    val expanded = presentation == PortalPresentation.Expanded && expansionArmed
    val scrimAlpha by animateFloatAsState(
        targetValue = if (expanded) 0.62f else 0f,
        animationSpec = tween(360),
        label = "portalScrim",
    )
    val scrimInteraction = remember { MutableInteractionSource() }
    val scrimInput = if (expanded) {
        Modifier.clickable(
            interactionSource = scrimInteraction,
            indication = null,
            onClick = {},
        )
    } else {
        Modifier
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .then(scrimInput)
            .padding(WindowInsets.safeDrawing.asPaddingValues()),
    ) {
        val expandedWidth = minOf(maxWidth - 20.dp, 1120.dp)
        val compactWidth = minOf(maxWidth - 28.dp, 350.dp)
        val targetWidth = if (expanded) expandedWidth else compactWidth
        val targetHeight = if (expanded) maxHeight - 20.dp else 64.dp
        val width by animateDpAsState(
            targetValue = targetWidth,
            animationSpec = spring(
                dampingRatio = 0.84f,
                stiffness = Spring.StiffnessLow,
            ),
            label = "portalWidth",
        )
        val height by animateDpAsState(
            targetValue = targetHeight,
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = 130f,
            ),
            label = "portalHeight",
        )
        val radius by animateDpAsState(
            targetValue = if (expanded) 30.dp else 32.dp,
            animationSpec = tween(360),
            label = "portalRadius",
        )

        PortalAura(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(width)
                .height(height),
            expanded = expanded,
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(width)
                .height(height),
            shape = RoundedCornerShape(radius),
            color = Ink,
            contentColor = Paper,
            shadowElevation = if (expanded) 28.dp else 14.dp,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (expanded) Color.White.copy(alpha = 0.16f) else Acid.copy(alpha = 0.5f),
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = expanded,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(tween(durationMillis = 300, delayMillis = 170)),
                    exit = fadeOut(tween(90)),
                ) {
                    BrowserPortal(
                        pageTitle = pageTitle,
                        currentUrl = currentUrl,
                        progress = progress,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward,
                        mainFrameError = mainFrameError,
                        webView = webView,
                        onClose = { onPresentationChange(PortalPresentation.Closed) },
                        onMinimize = { onPresentationChange(PortalPresentation.Minimized) },
                        onOpenExternal = onOpenExternal,
                        onBack = onBack,
                        onForward = onForward,
                        onReload = onReload,
                    )
                }
                AnimatedVisibility(
                    visible = !expanded,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(100)),
                ) {
                    MinimizedPortal(
                        onRestore = { onPresentationChange(PortalPresentation.Expanded) },
                        onClose = { onPresentationChange(PortalPresentation.Closed) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PortalAura(modifier: Modifier, expanded: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (expanded) 0.62f else 0.85f,
        animationSpec = tween(420),
        label = "portalAura",
    )
    Canvas(modifier.padding(2.dp)) {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Solar.copy(alpha = 0.32f * alpha),
                    Acid.copy(alpha = 0.22f * alpha),
                    Color(0xFF606CFF).copy(alpha = 0.30f * alpha),
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(34.dp.toPx()),
        )
    }
}

@Composable
private fun MinimizedPortal(onRestore: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(32.dp))
            .clickable(onClick = onRestore)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        PortalMark()
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.portal_title),
                color = Paper,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.portal_session_alive),
                color = Paper.copy(alpha = 0.58f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Rounded.ExpandLess,
            contentDescription = stringResource(R.string.portal_restore),
            tint = Acid,
        )
        IconButton(onClick = onClose) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.portal_close),
                tint = Paper.copy(alpha = 0.74f),
            )
        }
    }
}

@Composable
private fun BrowserPortal(
    pageTitle: String,
    currentUrl: String,
    progress: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    mainFrameError: Boolean,
    webView: WebView,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onOpenExternal: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PortalTopBar(
            currentUrl = currentUrl,
            onClose = onClose,
            onMinimize = onMinimize,
            onOpenExternal = onOpenExternal,
        )
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Acid,
                trackColor = Color.Transparent,
            )
        } else {
            Spacer(Modifier.fillMaxWidth().height(2.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.White)) {
            AndroidView(
                factory = {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (mainFrameError) {
                PortalError(onRetry = onReload, onOpenExternal = onOpenExternal)
            }
        }
        PortalDock(
            pageTitle = pageTitle,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            onBack = onBack,
            onForward = onForward,
            onReload = onReload,
        )
    }
}

@Composable
private fun PortalTopBar(
    currentUrl: String,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onClose) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.portal_close),
                tint = Paper.copy(alpha = 0.82f),
            )
        }
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.075f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Acid, modifier = Modifier.size(14.dp))
                Text(
                    text = Uri.parse(currentUrl).host ?: stringResource(R.string.portal_address),
                    modifier = Modifier.weight(1f),
                    color = Paper.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.portal_secure),
                    color = Acid.copy(alpha = 0.78f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
        }
        IconButton(onClick = onMinimize) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.portal_minimize),
                tint = Paper.copy(alpha = 0.82f),
            )
        }
        IconButton(onClick = onOpenExternal) {
            Icon(
                Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = stringResource(R.string.portal_external),
                tint = Paper.copy(alpha = 0.82f),
            )
        }
    }
}

@Composable
private fun PortalDock(
    pageTitle: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
) {
    Column {
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Row(
            modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.portal_back),
                    tint = if (canGoBack) Paper else Paper.copy(alpha = 0.22f),
                )
            }
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = stringResource(R.string.portal_forward),
                    tint = if (canGoForward) Paper else Paper.copy(alpha = 0.22f),
                )
            }
            IconButton(onClick = onReload) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.portal_reload),
                    tint = Paper.copy(alpha = 0.72f),
                )
            }
            Spacer(Modifier.width(4.dp))
            Surface(
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                color = Acid.copy(alpha = 0.11f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Acid, modifier = Modifier.size(14.dp))
                    Text(
                        text = pageTitle.ifBlank { stringResource(R.string.portal_direct_ready) },
                        modifier = Modifier.weight(1f),
                        color = Paper.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PortalError(onRetry: () -> Unit, onOpenExternal: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Ink,
        contentColor = Paper,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PortalMark(modifier = Modifier.size(54.dp))
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.portal_error_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = stringResource(R.string.portal_error_body),
                color = Paper.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Solar),
                ) {
                    Text(stringResource(R.string.portal_retry))
                }
                Button(
                    onClick = onOpenExternal,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Paper,
                    ),
                ) {
                    Text(stringResource(R.string.portal_external))
                }
            }
        }
    }
}

@Composable
private fun PortalMark(modifier: Modifier = Modifier.size(44.dp)) {
    Canvas(modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = Acid, radius = size.minDimension * 0.11f, center = center)
        drawArc(
            brush = Brush.sweepGradient(listOf(Solar, Acid, Color(0xFF606CFF), Solar), center),
            startAngle = -55f,
            sweepAngle = 286f,
            useCenter = false,
            topLeft = Offset(size.width * 0.21f, size.height * 0.21f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.58f, size.height * 0.58f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = size.minDimension * 0.09f,
                cap = StrokeCap.Round,
            ),
        )
    }
}

private inline fun updateNavigation(
    webView: WebView,
    update: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
) = update(webView.canGoBack(), webView.canGoForward())

private const val AI_MODE_URL = "https://www.google.com/ai"
