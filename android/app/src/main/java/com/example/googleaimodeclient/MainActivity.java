package com.example.googleaimodeclient;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private static final int MAX_IMAGES = 5;
    private static final String STATE_IMAGES = "selected_images";
    private static final String STATE_LAST_BOARD = "last_board";

    private final ArrayList<Uri> selectedImages = new ArrayList<>();
    private final ExecutorService boardExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private View root;
    private androidx.core.widget.NestedScrollView contentScroll;
    private EditText prompt;
    private TextView headerCount;
    private TextView imageCount;
    private TextView dragHint;
    private TextView statusText;
    private View statusDot;
    private LinearLayout emptyImages;
    private LinearLayout boardActions;
    private RecyclerView imageList;
    private ProgressBar progress;
    private MaterialButton pickImages;
    private MaterialButton clearImages;
    private MaterialButton copyPrompt;
    private MaterialButton saveBoard;
    private MaterialButton viewBoard;
    private MaterialButton shareBoard;
    private MaterialButton prepareOpen;
    private com.google.android.material.card.MaterialCardView bottomBar;

    private ThumbnailLoader thumbnailLoader;
    private SelectedImageAdapter imageAdapter;
    private ActivityResultLauncher<Intent> pickerLauncher;
    private ActivityResultLauncher<String> storagePermissionLauncher;

    private boolean busy;
    private boolean destroyed;
    private boolean openAfterPermission;
    private Uri lastBoardUri;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        configureSystemBars();
        restoreState(savedInstanceState);
        registerLaunchers();
        configureImageList();
        configureActions();

        handleIncomingIntent(getIntent(), savedInstanceState == null);
        updateUi();
    }

    private void bindViews() {
        root = findViewById(R.id.root);
        contentScroll = findViewById(R.id.contentScroll);
        prompt = findViewById(R.id.prompt);
        headerCount = findViewById(R.id.headerCount);
        imageCount = findViewById(R.id.imageCount);
        dragHint = findViewById(R.id.dragHint);
        statusText = findViewById(R.id.statusText);
        statusDot = findViewById(R.id.statusDot);
        emptyImages = findViewById(R.id.emptyImages);
        boardActions = findViewById(R.id.boardActions);
        imageList = findViewById(R.id.imageList);
        progress = findViewById(R.id.progress);
        pickImages = findViewById(R.id.pickImages);
        clearImages = findViewById(R.id.clearImages);
        copyPrompt = findViewById(R.id.copyPrompt);
        saveBoard = findViewById(R.id.saveBoard);
        viewBoard = findViewById(R.id.viewBoard);
        shareBoard = findViewById(R.id.shareBoard);
        prepareOpen = findViewById(R.id.prepareOpen);
        bottomBar = findViewById(R.id.bottomBar);
    }

    private void configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean darkMode = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(),
                getWindow().getDecorView()
        );
        controller.setAppearanceLightStatusBars(!darkMode);
        controller.setAppearanceLightNavigationBars(!darkMode);

        int contentBottomPadding = contentScroll.getPaddingBottom();
        int baseBottomMargin = dp(12);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            contentScroll.setPadding(
                    contentScroll.getPaddingLeft(),
                    bars.top,
                    contentScroll.getPaddingRight(),
                    contentBottomPadding + bars.bottom
            );
            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams params =
                    (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) bottomBar.getLayoutParams();
            params.bottomMargin = baseBottomMargin + bars.bottom;
            bottomBar.setLayoutParams(params);
            return windowInsets;
        });
    }

    private void restoreState(@Nullable Bundle state) {
        if (state == null) {
            return;
        }
        ArrayList<String> savedImages = state.getStringArrayList(STATE_IMAGES);
        if (savedImages != null) {
            for (String value : savedImages) {
                if (selectedImages.size() == MAX_IMAGES) {
                    break;
                }
                selectedImages.add(Uri.parse(value));
            }
        }
        String lastBoard = state.getString(STATE_LAST_BOARD);
        if (lastBoard != null && !lastBoard.trim().isEmpty()) {
            lastBoardUri = Uri.parse(lastBoard);
        }
    }

    private void registerLaunchers() {
        pickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        handlePickerResult(result.getData());
                    }
                }
        );
        storagePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        createBoard(openAfterPermission);
                    } else {
                        setBusy(false);
                        showMessage(R.string.storage_permission_denied);
                    }
                }
        );
    }

    private void configureImageList() {
        thumbnailLoader = new ThumbnailLoader(this);
        imageAdapter = new SelectedImageAdapter(selectedImages, this::removeImage, thumbnailLoader);
        imageList.setLayoutManager(new LinearLayoutManager(
                this,
                LinearLayoutManager.HORIZONTAL,
                false
        ));
        imageList.setAdapter(imageAdapter);
        imageList.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT,
                0
        ) {
            @Override
            public boolean onMove(
                    @NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder source,
                    @NonNull RecyclerView.ViewHolder target
            ) {
                return !busy && imageAdapter.move(
                        source.getBindingAdapterPosition(),
                        target.getBindingAdapterPosition()
                );
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // Swipe removal is intentionally disabled; the visible × control is safer.
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return !busy && selectedImages.size() > 1;
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (viewHolder != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder.itemView.animate().scaleX(1.04f).scaleY(1.04f).alpha(0.92f).setDuration(120).start();
                }
            }

            @Override
            public void clearView(
                    @NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder
            ) {
                super.clearView(recyclerView, viewHolder);
                viewHolder.itemView.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
                updateStatus();
            }
        });
        helper.attachToRecyclerView(imageList);
    }

    private void configureActions() {
        emptyImages.setOnClickListener(view -> launchImagePicker());
        pickImages.setOnClickListener(view -> launchImagePicker());
        clearImages.setOnClickListener(view -> clearImagesWithUndo());
        copyPrompt.setOnClickListener(view -> copyPrompt(true));
        saveBoard.setOnClickListener(view -> ensurePermissionAndCreateBoard(false));
        viewBoard.setOnClickListener(view -> viewLastBoard());
        shareBoard.setOnClickListener(view -> shareLastBoard());
        prepareOpen.setOnClickListener(view -> prepareAndOpen());

        prompt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                updateUi();
            }
        });
    }

    private void launchImagePicker() {
        if (busy) {
            showMessage(R.string.busy_message);
            return;
        }
        if (selectedImages.size() >= MAX_IMAGES) {
            showMessage(R.string.max_images_reached);
            return;
        }
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType("image/*")
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        pickerLauncher.launch(picker);
    }

    private void handlePickerResult(@NonNull Intent data) {
        List<Uri> incoming = collectUris(data);
        int added = addUris(incoming, data.getFlags());
        if (incoming.size() > added && selectedImages.size() == MAX_IMAGES) {
            showMessage(R.string.only_first_five);
        }
    }

    private List<Uri> collectUris(@NonNull Intent data) {
        ArrayList<Uri> uris = new ArrayList<>();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int index = 0; index < clipData.getItemCount(); index++) {
                Uri uri = clipData.getItemAt(index).getUri();
                if (uri != null && !uris.contains(uri)) {
                    uris.add(uri);
                }
            }
        }
        Uri single = data.getData();
        if (single != null && !uris.contains(single)) {
            uris.add(single);
        }
        return uris;
    }

    private int addUris(@NonNull List<Uri> incoming, int grantFlags) {
        int added = 0;
        for (Uri uri : incoming) {
            if (selectedImages.size() >= MAX_IMAGES) {
                break;
            }
            if (uri == null || selectedImages.contains(uri)) {
                continue;
            }
            selectedImages.add(uri);
            persistReadPermission(uri, grantFlags);
            added++;
        }
        if (added > 0) {
            imageAdapter.notifyDataSetChanged();
            updateUi();
        }
        return added;
    }

    private void persistReadPermission(@NonNull Uri uri, int grantFlags) {
        if ((grantFlags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) == 0) {
            return;
        }
        int takeFlags = grantFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (takeFlags == 0) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (SecurityException ignored) {
            // Some providers advertise persistable access but do not actually grant it.
        }
    }

    private void clearImagesWithUndo() {
        if (busy || selectedImages.isEmpty()) {
            return;
        }
        ArrayList<Uri> previous = new ArrayList<>(selectedImages);
        selectedImages.clear();
        imageAdapter.notifyDataSetChanged();
        updateUi();

        Snackbar snackbar = anchoredSnackbar(R.string.images_cleared, Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.undo, view -> {
            selectedImages.clear();
            selectedImages.addAll(previous.subList(0, Math.min(MAX_IMAGES, previous.size())));
            imageAdapter.notifyDataSetChanged();
            updateUi();
        });
        snackbar.show();
    }

    private void removeImage(int position) {
        if (busy) {
            showMessage(R.string.busy_message);
            return;
        }
        if (position < 0 || position >= selectedImages.size()) {
            return;
        }
        Uri removed = selectedImages.remove(position);
        imageAdapter.notifyItemRemoved(position);
        imageAdapter.notifyItemRangeChanged(position, selectedImages.size() - position);
        updateUi();

        Snackbar snackbar = anchoredSnackbar(R.string.image_removed, Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.undo, view -> {
            int restoredPosition = Math.min(position, selectedImages.size());
            selectedImages.add(restoredPosition, removed);
            imageAdapter.notifyItemInserted(restoredPosition);
            imageAdapter.notifyItemRangeChanged(
                    restoredPosition,
                    selectedImages.size() - restoredPosition
            );
            updateUi();
        });
        snackbar.show();
    }

    private void prepareAndOpen() {
        if (busy) {
            showMessage(R.string.busy_message);
            return;
        }
        if (selectedImages.isEmpty()) {
            boolean copied = copyPrompt(false);
            if (copied) {
                Toast.makeText(this, R.string.prompt_copied, Toast.LENGTH_SHORT).show();
            }
            openAiMode();
            return;
        }
        ensurePermissionAndCreateBoard(true);
    }

    private void ensurePermissionAndCreateBoard(boolean openAfter) {
        if (selectedImages.isEmpty()) {
            showMessage(R.string.image_required);
            return;
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            openAfterPermission = openAfter;
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        createBoard(openAfter);
    }

    private void createBoard(boolean openAfter) {
        if (busy) {
            return;
        }
        ArrayList<Uri> snapshot = new ArrayList<>(selectedImages);
        setBusy(true);
        boardExecutor.execute(() -> {
            Bitmap board = null;
            try {
                board = CollageComposer.createBoard(this, snapshot);
                Uri saved = BoardStore.save(this, board);
                mainHandler.post(() -> onBoardSaved(saved, openAfter));
            } catch (Exception | OutOfMemoryError error) {
                mainHandler.post(this::onBoardFailed);
            } finally {
                if (board != null && !board.isRecycled()) {
                    board.recycle();
                }
            }
        });
    }

    private void onBoardSaved(@NonNull Uri uri, boolean openAfter) {
        if (destroyed) {
            return;
        }
        lastBoardUri = uri;
        setBusy(false);
        boardActions.setVisibility(View.VISIBLE);
        statusText.setText(R.string.saved_status);

        if (openAfter) {
            boolean copied = copyPrompt(false);
            Toast.makeText(
                    this,
                    copied ? R.string.board_ready_for_ai : R.string.board_ready_images_only,
                    Toast.LENGTH_LONG
            ).show();
            mainHandler.postDelayed(this::openAiMode, 260L);
        } else {
            showMessage(R.string.board_saved);
        }
    }

    private void onBoardFailed() {
        if (destroyed) {
            return;
        }
        setBusy(false);
        showMessage(R.string.board_failed);
    }

    private boolean copyPrompt(boolean showFeedback) {
        String text = prompt.getText() == null ? "" : prompt.getText().toString().trim();
        if (text.isEmpty()) {
            if (showFeedback) {
                showMessage(R.string.prompt_required);
                prompt.requestFocus();
            }
            return false;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("AI Mode prompt", text));
        if (showFeedback) {
            showMessage(R.string.prompt_copied);
        }
        return true;
    }

    private void openAiMode() {
        if (!AiModeLauncher.open(this)) {
            showMessage(R.string.browser_unavailable);
        }
    }

    private void viewLastBoard() {
        if (lastBoardUri == null) {
            return;
        }
        Intent view = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(lastBoardUri, "image/jpeg")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(view, getString(R.string.view_board_chooser)));
        } catch (RuntimeException error) {
            showMessage(R.string.open_board_failed);
        }
    }

    private void shareLastBoard() {
        if (lastBoardUri == null) {
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND)
                .setType("image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, lastBoardUri)
                .setClipData(ClipData.newRawUri("AI Mode board", lastBoardUri))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(share, getString(R.string.share_board_chooser)));
        } catch (RuntimeException error) {
            showMessage(R.string.share_board_failed);
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        statusDot.setVisibility(value ? View.GONE : View.VISIBLE);
        imageList.setAlpha(value ? 0.62f : 1f);
        prompt.setEnabled(!value);
        pickImages.setEnabled(!value && selectedImages.size() < MAX_IMAGES);
        clearImages.setEnabled(!value && !selectedImages.isEmpty());
        copyPrompt.setEnabled(!value && !currentPrompt().isEmpty());
        saveBoard.setEnabled(!value && !selectedImages.isEmpty());
        prepareOpen.setEnabled(!value);
        if (value) {
            statusText.setText(R.string.preparing_status);
        } else {
            updateUi();
        }
    }

    private void updateUi() {
        if (imageAdapter == null) {
            return;
        }
        int count = selectedImages.size();
        String counter = getString(R.string.images_counter, count);
        headerCount.setText(counter);
        imageCount.setText(counter);

        boolean hasImages = count > 0;
        emptyImages.setVisibility(hasImages ? View.GONE : View.VISIBLE);
        imageList.setVisibility(hasImages ? View.VISIBLE : View.GONE);
        dragHint.setVisibility(count > 1 ? View.VISIBLE : View.GONE);
        boardActions.setVisibility(lastBoardUri == null ? View.GONE : View.VISIBLE);

        pickImages.setText(hasImages ? R.string.add_more_images : R.string.choose_images);
        pickImages.setEnabled(!busy && count < MAX_IMAGES);
        clearImages.setEnabled(!busy && hasImages);
        saveBoard.setEnabled(!busy && hasImages);
        copyPrompt.setEnabled(!busy && !currentPrompt().isEmpty());
        prepareOpen.setText(
                hasImages || !currentPrompt().isEmpty()
                        ? R.string.prepare_and_open
                        : R.string.open_without_preparing
        );
        if (!busy) {
            updateStatus();
        }
    }

    private void updateStatus() {
        if (selectedImages.isEmpty()) {
            statusText.setText(R.string.ready_status);
        } else {
            statusText.setText(getString(R.string.selected_status, selectedImages.size()));
        }
    }

    private String currentPrompt() {
        return prompt.getText() == null ? "" : prompt.getText().toString().trim();
    }

    private Snackbar anchoredSnackbar(int message, int duration) {
        return Snackbar.make(root, message, duration).setAnchorView(bottomBar);
    }

    private void showMessage(int message) {
        anchoredSnackbar(message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent, true);
    }

    private void handleIncomingIntent(@Nullable Intent intent, boolean showFeedback) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            CharSequence sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && !sharedText.toString().trim().isEmpty()) {
                prompt.setText(sharedText.toString());
                prompt.setSelection(prompt.length());
                if (showFeedback) {
                    showMessage(R.string.incoming_text_ready);
                }
            }
            return;
        }

        if (type == null || !type.startsWith("image/")) {
            return;
        }

        ArrayList<Uri> incoming = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri single = getStreamExtra(intent);
            if (single != null) {
                incoming.add(single);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            incoming.addAll(getStreamExtras(intent));
        }
        if (intent.getClipData() != null) {
            for (int index = 0; index < intent.getClipData().getItemCount(); index++) {
                Uri uri = intent.getClipData().getItemAt(index).getUri();
                if (uri != null && !incoming.contains(uri)) {
                    incoming.add(uri);
                }
            }
        }

        int added = addUris(incoming, intent.getFlags());
        if (incoming.size() > added && selectedImages.size() == MAX_IMAGES) {
            showMessage(R.string.only_first_five);
        } else if (added > 0 && showFeedback) {
            showMessage(R.string.incoming_images_ready);
        }
    }

    @SuppressWarnings("deprecation")
    @Nullable
    private Uri getStreamExtra(@NonNull Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        }
        return intent.getParcelableExtra(Intent.EXTRA_STREAM);
    }

    @SuppressWarnings("deprecation")
    @NonNull
    private ArrayList<Uri> getStreamExtras(@NonNull Intent intent) {
        ArrayList<Uri> values;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            values = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
        } else {
            values = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        }
        return values == null ? new ArrayList<>() : values;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        ArrayList<String> images = new ArrayList<>();
        for (Uri uri : selectedImages) {
            images.add(uri.toString());
        }
        outState.putStringArrayList(STATE_IMAGES, images);
        if (lastBoardUri != null) {
            outState.putString(STATE_LAST_BOARD, lastBoardUri.toString());
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        boardExecutor.shutdownNow();
        if (thumbnailLoader != null) {
            thumbnailLoader.shutdown();
        }
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
