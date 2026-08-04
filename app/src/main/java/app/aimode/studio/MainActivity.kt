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
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.FileProvider
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
                StudioScreen(
                    viewModel = studioViewModel,
                    onOpenAiMode = ::openAiMode,
                    onShareBoard = ::shareBoard,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        studioViewModel.acceptSharedIntent(intent)
    }

    private fun openAiMode() {
        val screenHeight = resources.displayMetrics.heightPixels
        val initialHeight = maxOf(screenHeight / 2, (screenHeight * 0.76f).toInt())
        try {
            CustomTabsIntent.Builder()
                .setInitialActivityHeightPx(initialHeight, CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE)
                .setToolbarColor(Color.rgb(21, 20, 17))
                .setNavigationBarColor(Color.rgb(12, 13, 15))
                .setNavigationBarDividerColor(Color.rgb(61, 60, 56))
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
                .setToolbarCornerRadiusDp(24)
                .setCloseButtonPosition(CustomTabsIntent.CLOSE_BUTTON_POSITION_END)
                .setBackgroundInteractionEnabled(true)
                .setActivitySideSheetBreakpointDp(760)
                .setActivitySideSheetMaximizationEnabled(true)
                .setActivitySideSheetPosition(CustomTabsIntent.ACTIVITY_SIDE_SHEET_POSITION_END)
                .setActivitySideSheetDecorationType(CustomTabsIntent.ACTIVITY_SIDE_SHEET_DECORATION_TYPE_SHADOW)
                .setActivitySideSheetRoundedCornersPosition(CustomTabsIntent.ACTIVITY_SIDE_SHEET_ROUNDED_CORNERS_POSITION_TOP)
                .setShowTitle(false)
                .setUrlBarHidingEnabled(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .build()
                .launchUrl(this, AI_MODE_URI)
        } catch (_: ActivityNotFoundException) {
            openFallbackBrowser()
        } catch (_: SecurityException) {
            openFallbackBrowser()
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun openFallbackBrowser() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, AI_MODE_URI))
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

    private companion object {
        val AI_MODE_URI: Uri = Uri.parse("https://www.google.com/ai")
    }
}
