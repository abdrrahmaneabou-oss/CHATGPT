package com.example.googleaimodeclient;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Owns the in-app Google AI surface and its morphing portal interaction.
 *
 * The controller deliberately keeps Android WebView concerns outside MainActivity: browser state,
 * file handoff, origin-scoped media permissions, navigation, and motion all live in one place.
 */
public final class AiPortalController {
    private static final Uri AI_MODE_URI = Uri.parse("https://www.google.com/ai");
    private static final int FILE_CHOOSER_REQUEST = 1701;
    private static final int WEB_PERMISSION_REQUEST = 1702;
    private static final String STATE_OPEN = "ai_portal_open";
    private static final String STATE_MINIMIZED = "ai_portal_minimized";

    private final AppCompatActivity activity;
    private final FrameLayout root;
    private final ScrollView studioScroll;
    private final View launchButton;
    private final EditText prompt;
    private final View scrim;
    private final LinearLayout stage;
    private final View dragHandle;
    private final LinearLayout pill;
    private final WebView webView;
    private final ProgressBar progress;
    private final View loading;
    private final TextView loadingOrb;
    private final View error;
    private final TextView errorMessage;
    private final TextView address;
    private final TextView securityIcon;
    private final TextView browserState;
    private final TextView promptTitle;
    private final TextView promptPreview;
    private final TextView copyPromptButton;
    private final TextView backButton;

    private final PathInterpolator portalInterpolator = new PathInterpolator(0.16f, 1f, 0.30f, 1f);

    private ValueCallback<Uri[]> fileChooserCallback;
    private PermissionRequest pendingWebPermission;
    private ValueAnimator loadingPulse;
    private boolean portalOpen;
    private boolean portalMinimized;
    private boolean pageInitialized;
    private boolean firstSuccessfulLoad;
    private boolean mainFrameFailed;
    private boolean destroyed;
    private float dragStartY;

    public AiPortalController(AppCompatActivity activity, View launchButton, EditText prompt, Bundle savedState) {
        this.activity = activity;
        this.launchButton = launchButton;
        this.prompt = prompt;
        root = activity.findViewById(R.id.studioRoot);
        studioScroll = activity.findViewById(R.id.studioScroll);
        scrim = activity.findViewById(R.id.portalScrim);
        stage = activity.findViewById(R.id.portalStage);
        stage.setClipToOutline(true);
        ViewCompat.setAccessibilityPaneTitle(stage, "Google AI Portal");
        dragHandle = activity.findViewById(R.id.portalDragHandle);
        pill = activity.findViewById(R.id.portalPill);
        webView = activity.findViewById(R.id.aiWebView);
        progress = activity.findViewById(R.id.portalProgress);
        loading = activity.findViewById(R.id.portalLoading);
        loadingOrb = activity.findViewById(R.id.portalLoadingOrb);
        error = activity.findViewById(R.id.portalError);
        errorMessage = activity.findViewById(R.id.portalErrorMessage);
        address = activity.findViewById(R.id.portalAddress);
        securityIcon = activity.findViewById(R.id.portalSecurityIcon);
        browserState = activity.findViewById(R.id.portalState);
        promptTitle = activity.findViewById(R.id.portalPromptTitle);
        promptPreview = activity.findViewById(R.id.portalPromptPreview);
        copyPromptButton = activity.findViewById(R.id.portalCopyPrompt);
        backButton = activity.findViewById(R.id.portalBack);

        configureWebView(savedState);
        wireControls();
        watchPrompt();
        updatePromptBridge(false);

        boolean restoreOpen = savedState != null && savedState.getBoolean(STATE_OPEN, false);
        boolean restoreMinimized = savedState != null && savedState.getBoolean(STATE_MINIMIZED, false);
        if (restoreOpen) {
            root.post(this::showImmediately);
        } else if (restoreMinimized) {
            portalMinimized = true;
            root.post(() -> showPill(false));
        }
    }

    public void open(View source) {
        if (destroyed || portalOpen) return;
        hideKeyboard();
        updatePromptBridge(true);
        webView.onResume();
        portalOpen = true;
        portalMinimized = false;

        pill.animate().cancel();
        pill.setVisibility(View.GONE);
        stage.animate().cancel();
        stage.animate().setListener(null);
        stage.setVisibility(View.INVISIBLE);
        scrim.setVisibility(View.VISIBLE);
        scrim.setAlpha(0f);
        setStudioBackdrop(true, true);

        View morphSource = source == null ? launchButton : source;
        root.post(() -> {
            if (!portalOpen || destroyed) return;
            Morph morph = calculateMorph(morphSource);
            stage.setPivotX(stage.getWidth() / 2f);
            stage.setPivotY(stage.getHeight() / 2f);
            stage.setTranslationX(morph.translationX);
            stage.setTranslationY(morph.translationY);
            stage.setScaleX(morph.scaleX);
            stage.setScaleY(morph.scaleY);
            stage.setAlpha(0.22f);
            stage.setVisibility(View.VISIBLE);
            stage.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);

            stage.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(680L)
                    .setInterpolator(portalInterpolator)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            stage.animate().setListener(null);
                        }
                    })
                    .start();
            scrim.animate().alpha(0.78f).setDuration(420L).setInterpolator(portalInterpolator).start();
        });

        if (!pageInitialized) {
            pageInitialized = true;
            root.postDelayed(() -> {
                if (!destroyed) loadAiMode();
            }, 180L);
        }
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != FILE_CHOOSER_REQUEST) return false;
        ValueCallback<Uri[]> callback = fileChooserCallback;
        fileChooserCallback = null;
        if (callback == null) return true;

        Uri[] result = null;
        if (resultCode == Activity.RESULT_OK && data != null) {
            ClipData clips = data.getClipData();
            if (clips != null && clips.getItemCount() > 0) {
                result = new Uri[clips.getItemCount()];
                for (int i = 0; i < clips.getItemCount(); i++) {
                    result[i] = clips.getItemAt(i).getUri();
                }
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            } else {
                result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            }
        }
        callback.onReceiveValue(result);
        return true;
    }

    public boolean handlePermissionResult(int requestCode) {
        if (requestCode != WEB_PERMISSION_REQUEST) return false;
        PermissionRequest request = pendingWebPermission;
        pendingWebPermission = null;
        if (request == null) return true;
        activity.runOnUiThread(() -> grantSupportedWebResources(request));
        return true;
    }

    public boolean handleBackPressed() {
        if (portalOpen) {
            if (webView.canGoBack()) webView.goBack();
            else minimize();
            return true;
        }
        if (portalMinimized) {
            close();
            return true;
        }
        return false;
    }

    public void saveState(Bundle outState) {
        outState.putBoolean(STATE_OPEN, portalOpen);
        outState.putBoolean(STATE_MINIMIZED, portalMinimized);
        if (pageInitialized) webView.saveState(outState);
    }

    public void onHostPause() {
        webView.onPause();
    }

    public void onHostResume() {
        if (portalOpen) webView.onResume();
    }

    public void destroy() {
        destroyed = true;
        if (loadingPulse != null) loadingPulse.cancel();
        stage.animate().cancel();
        scrim.animate().cancel();
        pill.animate().cancel();
        studioScroll.animate().cancel();
        if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
        fileChooserCallback = null;
        if (pendingWebPermission != null) pendingWebPermission.deny();
        pendingWebPermission = null;
        webView.stopLoading();
        webView.onPause();
        ViewGroup parent = (ViewGroup) webView.getParent();
        if (parent != null) parent.removeView(webView);
        webView.destroy();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(Bundle savedState) {
        WebView.setWebContentsDebuggingEnabled(false);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        settings.setOffscreenPreRaster(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);
        settings.setUserAgentString(settings.getUserAgentString() + " AI-Mode-Studio/1.1");

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new PortalWebViewClient());
        webView.setWebChromeClient(new PortalChromeClient());
        webView.setBackgroundColor(Color.rgb(12, 15, 24));

        if (savedState != null && webView.restoreState(savedState) != null) {
            pageInitialized = true;
            firstSuccessfulLoad = true;
        }
    }

    private void wireControls() {
        activity.findViewById(R.id.closePortal).setOnClickListener(view -> close());
        activity.findViewById(R.id.minimizePortal).setOnClickListener(view -> minimize());
        activity.findViewById(R.id.openPortalExternal).setOnClickListener(view -> openExternally());
        activity.findViewById(R.id.portalReload).setOnClickListener(view -> {
            hideError();
            if (webView.getUrl() == null) loadAiMode();
            else webView.reload();
        });
        activity.findViewById(R.id.retryPortal).setOnClickListener(view -> {
            hideError();
            loadAiMode();
        });
        backButton.setOnClickListener(view -> {
            if (webView.canGoBack()) webView.goBack();
            else minimize();
        });
        copyPromptButton.setOnClickListener(view -> copyPromptToClipboard(true));
        scrim.setOnClickListener(view -> minimize());
        pill.setOnClickListener(this::open);
        dragHandle.setOnTouchListener(this::handlePortalDrag);
    }

    private void watchPrompt() {
        prompt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                updatePromptBridge(false);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    private void showImmediately() {
        if (destroyed) return;
        portalOpen = true;
        portalMinimized = false;
        pill.setVisibility(View.GONE);
        scrim.setVisibility(View.VISIBLE);
        scrim.setAlpha(0.78f);
        stage.setVisibility(View.VISIBLE);
        resetStageTransform();
        setStudioBackdrop(true, false);
        webView.onResume();
        if (!pageInitialized) {
            pageInitialized = true;
            loadAiMode();
        }
    }

    private void minimize() {
        if (!portalOpen || destroyed) return;
        portalOpen = false;
        portalMinimized = true;
        webView.onPause();
        stage.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        stage.animate().cancel();
        stage.animate().setListener(null);
        float targetY = Math.max(dp(220), root.getHeight() * 0.43f);
        stage.animate()
                .alpha(0f)
                .translationX(0f)
                .translationY(targetY)
                .scaleX(0.72f)
                .scaleY(0.08f)
                .setDuration(460L)
                .setInterpolator(portalInterpolator)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        stage.setVisibility(View.GONE);
                        resetStageTransform();
                        stage.animate().setListener(null);
                        if (portalMinimized) showPill(true);
                    }
                })
                .start();
        restoreStudioBackdrop();
    }

    private void close() {
        if (destroyed) return;
        boolean wasVisible = portalOpen;
        portalOpen = false;
        portalMinimized = false;
        webView.onPause();
        pill.animate().cancel();
        if (pill.getVisibility() == View.VISIBLE) {
            pill.animate()
                    .alpha(0f)
                    .scaleX(0.78f)
                    .scaleY(0.78f)
                    .translationY(dp(16))
                    .setDuration(220L)
                    .withEndAction(() -> {
                        pill.setVisibility(View.GONE);
                        pill.setAlpha(1f);
                        pill.setScaleX(1f);
                        pill.setScaleY(1f);
                        pill.setTranslationY(0f);
                    })
                    .start();
        }
        if (!wasVisible || stage.getVisibility() != View.VISIBLE) {
            restoreStudioBackdrop();
            return;
        }

        Morph morph = calculateMorph(launchButton);
        stage.animate().cancel();
        stage.animate().setListener(null);
        stage.animate()
                .alpha(0f)
                .translationX(morph.translationX)
                .translationY(morph.translationY)
                .scaleX(morph.scaleX)
                .scaleY(morph.scaleY)
                .setDuration(480L)
                .setInterpolator(portalInterpolator)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        stage.setVisibility(View.GONE);
                        resetStageTransform();
                        stage.animate().setListener(null);
                    }
                })
                .start();
        restoreStudioBackdrop();
    }

    private void showPill(boolean animated) {
        if (destroyed || !portalMinimized) return;
        pill.animate().cancel();
        pill.setVisibility(View.VISIBLE);
        if (!animated) {
            pill.setAlpha(1f);
            pill.setScaleX(1f);
            pill.setScaleY(1f);
            pill.setTranslationY(0f);
            return;
        }
        pill.setAlpha(0f);
        pill.setScaleX(0.78f);
        pill.setScaleY(0.78f);
        pill.setTranslationY(dp(18));
        pill.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(420L)
                .setInterpolator(new OvershootInterpolator(0.85f))
                .start();
    }

    private boolean handlePortalDrag(View view, MotionEvent event) {
        if (!portalOpen) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartY = event.getRawY();
                stage.animate().cancel();
                return true;
            case MotionEvent.ACTION_MOVE:
                float distance = Math.max(0f, event.getRawY() - dragStartY);
                float progressValue = Math.min(1f, distance / Math.max(1f, root.getHeight() * 0.36f));
                stage.setTranslationY(distance * 0.78f);
                stage.setScaleX(1f - 0.07f * progressValue);
                stage.setScaleY(1f - 0.07f * progressValue);
                scrim.setAlpha(0.78f * (1f - 0.62f * progressValue));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float releasedDistance = Math.max(0f, event.getRawY() - dragStartY);
                if (releasedDistance > dp(96)) {
                    minimize();
                } else {
                    stage.animate()
                            .translationY(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(360L)
                            .setInterpolator(portalInterpolator)
                            .start();
                    scrim.animate().alpha(0.78f).setDuration(260L).start();
                    view.performClick();
                }
                return true;
            default:
                return false;
        }
    }

    private void setStudioBackdrop(boolean dimmed, boolean animated) {
        studioScroll.animate().cancel();
        studioScroll.setImportantForAccessibility(dimmed
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        float alpha = dimmed ? 0.58f : 1f;
        float scale = dimmed ? 0.972f : 1f;
        float translation = dimmed ? dp(7) : 0f;
        if (animated) {
            studioScroll.animate()
                    .alpha(alpha)
                    .scaleX(scale)
                    .scaleY(scale)
                    .translationY(translation)
                    .setDuration(dimmed ? 520L : 380L)
                    .setInterpolator(portalInterpolator)
                    .start();
        } else {
            studioScroll.setAlpha(alpha);
            studioScroll.setScaleX(scale);
            studioScroll.setScaleY(scale);
            studioScroll.setTranslationY(translation);
        }
    }

    private void restoreStudioBackdrop() {
        setStudioBackdrop(false, true);
        scrim.animate().cancel();
        scrim.animate()
                .alpha(0f)
                .setDuration(340L)
                .withEndAction(() -> {
                    if (!portalOpen) scrim.setVisibility(View.GONE);
                })
                .start();
    }

    private void loadAiMode() {
        if (destroyed) return;
        mainFrameFailed = false;
        error.setVisibility(View.GONE);
        showLoading();
        webView.loadUrl(AI_MODE_URI.toString());
    }

    private void showLoading() {
        loading.animate().cancel();
        loading.animate().setListener(null);
        loading.setAlpha(1f);
        loading.setVisibility(View.VISIBLE);
        error.setVisibility(View.GONE);
        if (loadingPulse == null) {
            loadingPulse = ValueAnimator.ofFloat(0f, 1f);
            loadingPulse.setDuration(1050L);
            loadingPulse.setRepeatCount(ValueAnimator.INFINITE);
            loadingPulse.setRepeatMode(ValueAnimator.REVERSE);
            loadingPulse.addUpdateListener(animation -> {
                float value = (float) animation.getAnimatedValue();
                float scale = 0.94f + value * 0.09f;
                loadingOrb.setScaleX(scale);
                loadingOrb.setScaleY(scale);
                loadingOrb.setRotation(-4f + value * 8f);
                loadingOrb.setAlpha(0.78f + value * 0.22f);
            });
        }
        if (!loadingPulse.isStarted()) loadingPulse.start();
    }

    private void hideLoading() {
        if (loadingPulse != null) loadingPulse.cancel();
        loadingOrb.setScaleX(1f);
        loadingOrb.setScaleY(1f);
        loadingOrb.setRotation(0f);
        loadingOrb.setAlpha(1f);
        loading.animate().cancel();
        loading.animate()
                .alpha(0f)
                .setDuration(280L)
                .withEndAction(() -> loading.setVisibility(View.GONE))
                .start();
    }

    private void showError(String message) {
        mainFrameFailed = true;
        if (loadingPulse != null) loadingPulse.cancel();
        loading.setVisibility(View.GONE);
        progress.setVisibility(View.INVISIBLE);
        errorMessage.setText(message == null || message.trim().isEmpty()
                ? "تحقق من اتصالك ثم أعد المحاولة."
                : message);
        error.setAlpha(0f);
        error.setVisibility(View.VISIBLE);
        error.animate().alpha(1f).setDuration(260L).start();
        browserState.setText("CONNECTION PAUSED • TAP RETRY");
        browserState.setTextColor(Color.parseColor("#FFFF8FA3"));
    }

    private void hideError() {
        mainFrameFailed = false;
        error.animate().cancel();
        error.setVisibility(View.GONE);
    }

    private void updateAddress(Uri uri) {
        if (uri == null) return;
        String host = uri.getHost();
        if (host == null) host = uri.toString();
        if (host.startsWith("www.")) host = host.substring(4);
        String path = uri.getPath();
        if (path == null || "/".equals(path)) path = "";
        String label = host + path;
        if (label.length() > 42) label = label.substring(0, 39) + "…";
        address.setText(label);
        boolean secure = "https".equalsIgnoreCase(uri.getScheme());
        securityIcon.setText(secure ? "◆" : "!");
        securityIcon.setTextColor(Color.parseColor(secure ? "#FF61E8B5" : "#FFFF8FA3"));
    }

    private void updateNavigationState() {
        boolean canGoBack = webView.canGoBack();
        backButton.setAlpha(canGoBack ? 1f : 0.58f);
        backButton.setContentDescription(canGoBack ? "رجوع داخل الصفحة" : "تصغير بوابة الذكاء");
    }

    private void updatePromptBridge(boolean copyForLaunch) {
        String text = prompt.getText().toString().trim();
        if (text.isEmpty()) {
            promptTitle.setText("ابدأ مباشرة أو جهّز سؤالًا");
            promptPreview.setText("يمكنك تصغير البوابة والعودة للاستوديو في أي لحظة");
            copyPromptButton.setText("بدء فارغ");
            return;
        }
        if (copyForLaunch) copyPromptToClipboard(false);
        promptTitle.setText(copyForLaunch ? "تم نسخ السؤال تلقائيًا ✓" : "السؤال جاهز للصق");
        String preview = text.replace('\n', ' ').replaceAll("\\s+", " ");
        if (preview.length() > 92) preview = preview.substring(0, 89) + "…";
        promptPreview.setText(preview);
        copyPromptButton.setText(copyForLaunch ? "نسخ مجددًا" : "نسخ السؤال");
    }

    private boolean copyPromptToClipboard(boolean showFeedback) {
        String text = prompt.getText().toString().trim();
        if (text.isEmpty()) {
            if (showFeedback) Toast.makeText(activity, "لا يوجد سؤال لنسخه بعد.", Toast.LENGTH_SHORT).show();
            return false;
        }
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AI Mode prompt", text));
        promptTitle.setText("السؤال في الحافظة ✓");
        copyPromptButton.setText("تم النسخ");
        copyPromptButton.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        if (showFeedback) Toast.makeText(activity, "تم نسخ السؤال — الصقه داخل Google AI.", Toast.LENGTH_SHORT).show();
        return true;
    }

    private void openExternally() {
        Uri uri = AI_MODE_URI;
        String current = webView.getUrl();
        if (current != null && (current.startsWith("https://") || current.startsWith("http://"))) {
            uri = Uri.parse(current);
        }
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(activity, "لا يوجد متصفح قادر على فتح هذه الصفحة.", Toast.LENGTH_LONG).show();
        }
    }

    private void launchFileChooser(ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
        if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
        fileChooserCallback = callback;
        try {
            Intent intent = params.createIntent();
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE);
            activity.startActivityForResult(intent, FILE_CHOOSER_REQUEST);
        } catch (ActivityNotFoundException | SecurityException error) {
            fileChooserCallback = null;
            callback.onReceiveValue(null);
            Toast.makeText(activity, "تعذّر فتح منتقي الملفات.", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestWebPermission(PermissionRequest request) {
        activity.runOnUiThread(() -> {
            if (!isTrustedGoogleOrigin(request.getOrigin())) {
                request.deny();
                return;
            }
            if (pendingWebPermission != null) pendingWebPermission.deny();
            ArrayList<String> missing = new ArrayList<>();
            for (String resource : request.getResources()) {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                        && ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    missing.add(Manifest.permission.CAMERA);
                }
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)
                        && ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    missing.add(Manifest.permission.RECORD_AUDIO);
                }
            }
            if (missing.isEmpty()) {
                grantSupportedWebResources(request);
            } else {
                pendingWebPermission = request;
                activity.requestPermissions(missing.toArray(new String[0]), WEB_PERMISSION_REQUEST);
            }
        });
    }

    private void grantSupportedWebResources(PermissionRequest request) {
        ArrayList<String> granted = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                    && ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                granted.add(resource);
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)
                    && ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                granted.add(resource);
            }
        }
        if (granted.isEmpty()) request.deny();
        else request.grant(granted.toArray(new String[0]));
    }

    private boolean isTrustedGoogleOrigin(Uri origin) {
        if (origin == null || !"https".equalsIgnoreCase(origin.getScheme())) return false;
        String host = origin.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.US);
        return "google.com".equals(host) || host.endsWith(".google.com");
    }

    private void hideKeyboard() {
        View focused = activity.getCurrentFocus();
        if (focused == null) return;
        InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        focused.clearFocus();
    }

    private Morph calculateMorph(View source) {
        View resolved = source != null && source.isShown() && source.getWidth() > 0 ? source : launchButton;
        int[] sourceLocation = new int[2];
        int[] stageLocation = new int[2];
        resolved.getLocationOnScreen(sourceLocation);
        stage.getLocationOnScreen(stageLocation);
        float sourceCenterX = sourceLocation[0] + resolved.getWidth() / 2f;
        float sourceCenterY = sourceLocation[1] + resolved.getHeight() / 2f;
        float stageCenterX = stageLocation[0] + stage.getWidth() / 2f;
        float stageCenterY = stageLocation[1] + stage.getHeight() / 2f;
        float scaleX = clamp((float) resolved.getWidth() / Math.max(1, stage.getWidth()), 0.18f, 0.88f);
        float scaleY = clamp((float) resolved.getHeight() / Math.max(1, stage.getHeight()), 0.07f, 0.20f);
        return new Morph(sourceCenterX - stageCenterX, sourceCenterY - stageCenterY, scaleX, scaleY);
    }

    private void resetStageTransform() {
        stage.setAlpha(1f);
        stage.setScaleX(1f);
        stage.setScaleY(1f);
        stage.setTranslationX(0f);
        stage.setTranslationY(0f);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private final class PortalWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme();
            if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) return false;
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException | SecurityException ignored) {
                Toast.makeText(activity, "لا يوجد تطبيق مناسب لهذا الرابط.", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            mainFrameFailed = false;
            hideError();
            updateAddress(Uri.parse(url));
            browserState.setText("CONNECTING • SECURE WEB SESSION");
            browserState.setTextColor(Color.parseColor("#FF7180AA"));
            if (!firstSuccessfulLoad) showLoading();
            updateNavigationState();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            if (!mainFrameFailed) {
                firstSuccessfulLoad = true;
                hideLoading();
                browserState.setText("LIVE • INSIDE AI MODE STUDIO");
                browserState.setTextColor(Color.parseColor("#FF61D9B0"));
            }
            updateAddress(Uri.parse(url));
            updateNavigationState();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError webError) {
            super.onReceivedError(view, request, webError);
            if (request.isForMainFrame()) {
                CharSequence description = webError.getDescription();
                showError(description == null ? null : description.toString());
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
            super.onReceivedHttpError(view, request, response);
            if (request.isForMainFrame() && response.getStatusCode() >= 400) {
                showError("تعذّر فتح الصفحة الآن (" + response.getStatusCode() + ").");
            }
        }
    }

    private final class PortalChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            if (newProgress >= 100) {
                progress.setProgress(100);
                progress.animate().alpha(0f).setDuration(220L).withEndAction(() -> {
                    progress.setVisibility(View.INVISIBLE);
                    progress.setAlpha(1f);
                }).start();
            } else {
                progress.animate().cancel();
                progress.setAlpha(1f);
                progress.setVisibility(View.VISIBLE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) progress.setProgress(newProgress, true);
                else progress.setProgress(newProgress);
            }
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            launchFileChooser(callback, params);
            return true;
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            requestWebPermission(request);
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
            if (pendingWebPermission == request) pendingWebPermission = null;
        }
    }

    private static final class Morph {
        final float translationX;
        final float translationY;
        final float scaleX;
        final float scaleY;

        Morph(float translationX, float translationY, float scaleX, float scaleY) {
            this.translationX = translationX;
            this.translationY = translationY;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
        }
    }
}
