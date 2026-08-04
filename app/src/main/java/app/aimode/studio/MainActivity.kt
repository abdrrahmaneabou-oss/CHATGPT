package app.aimode.studio

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.content.FileProvider
import app.aimode.studio.ui.AiPortalHost
import app.aimode.studio.ui.PortalPresentation
import app.aimode.studio.ui.StudioScreen
import app.aimode.studio.ui.StudioViewModel
import app.aimode.studio.ui.theme.AIModeTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val studioViewModel by viewModels<StudioViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) studioViewModel.acceptSharedIntent(intent)

        setContent {
            AIModeTheme {
                var savedPresentation by rememberSaveable {
                    mutableStateOf(PortalPresentation.Closed.name)
                }
                val portalPresentation = PortalPresentation.valueOf(savedPresentation)
                val workspaceScale by animateFloatAsState(
                    targetValue = if (portalPresentation == PortalPresentation.Expanded) 0.965f else 1f,
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 180f),
                    label = "workspacePortalScale",
                )
                val workspaceAlpha by animateFloatAsState(
                    targetValue = if (portalPresentation == PortalPresentation.Expanded) 0.62f else 1f,
                    label = "workspacePortalAlpha",
                )

                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = workspaceScale
                                scaleY = workspaceScale
                                alpha = workspaceAlpha
                            },
                    ) {
                        StudioScreen(
                            viewModel = studioViewModel,
                            onOpenAiMode = { savedPresentation = PortalPresentation.Expanded.name },
                            onShareBoard = ::shareBoard,
                        )
                    }
                    AiPortalHost(
                        presentation = portalPresentation,
                        onPresentationChange = { savedPresentation = it.name },
                        onOpenExternal = ::openExternal,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        studioViewModel.acceptSharedIntent(intent)
    }

    private fun openExternal(uri: Uri) {
        if (uri.scheme != "https" && uri.scheme != "http") {
            openFallbackBrowser(uri)
            return
        }
        val colorScheme = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(Color.rgb(21, 20, 17))
            .setNavigationBarColor(Color.rgb(12, 13, 15))
            .setNavigationBarDividerColor(Color.rgb(61, 60, 56))
            .build()
        try {
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colorScheme)
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
                .setCloseButtonPosition(CustomTabsIntent.CLOSE_BUTTON_POSITION_END)
                .setShowTitle(false)
                .setUrlBarHidingEnabled(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .build()
                .launchUrl(this, uri)
        } catch (_: ActivityNotFoundException) {
            openFallbackBrowser(uri)
        } catch (_: SecurityException) {
            openFallbackBrowser(uri)
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun openFallbackBrowser(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun shareBoard(uriString: String) {
        val parsed = Uri.parse(uriString)
        val shareUri = if (parsed.scheme == "file") {
            FileProvider.getUriForFile(this, "$packageName.files", File(requireNotNull(parsed.path)))
        } else {
            parsed
        }
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(share, getString(R.string.share_board))) }
            .onFailure { Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show() }
    }
}
