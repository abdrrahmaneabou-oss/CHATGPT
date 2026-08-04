package com.example.googleaimodeclient;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int MAX_IMAGES = 5;
    private static final int PICK_IMAGES = 1001;
    private static final int STORAGE_PERMISSION = 1002;

    private final ArrayList<Uri> selectedImages = new ArrayList<>();
    private final ExecutorService imageWorker = Executors.newSingleThreadExecutor();

    private LinearLayout imageStrip;
    private TextView status;
    private EditText prompt;
    private Button createCollageButton;
    private AiPortalController aiPortal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageStrip = findViewById(R.id.imageStrip);
        status = findViewById(R.id.status);
        prompt = findViewById(R.id.prompt);
        createCollageButton = findViewById(R.id.createCollage);

        Button openAiMode = findViewById(R.id.openAiMode);
        Button pickImages = findViewById(R.id.pickImages);
        Button clearImages = findViewById(R.id.clearImages);
        Button copyPrompt = findViewById(R.id.copyPrompt);

        aiPortal = new AiPortalController(this, openAiMode, prompt, savedInstanceState);
        openAiMode.setOnClickListener(aiPortal::open);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (aiPortal != null && aiPortal.handleBackPressed()) return;
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
        pickImages.setOnClickListener(view -> launchImagePicker());
        clearImages.setOnClickListener(view -> {
            selectedImages.clear();
            renderImages();
        });
        copyPrompt.setOnClickListener(view -> copyPrompt(true));
        createCollageButton.setOnClickListener(view -> ensurePermissionAndCreateCollage());

        handleIncomingIntent(getIntent());
        renderImages();
        reveal(openAiMode, 20f, 700L);
    }

    private void reveal(View view, float offsetDp, long duration) {
        view.setAlpha(0f);
        view.setTranslationY(dp(offsetDp));
        view.animate().alpha(1f).translationY(0f).setDuration(duration).start();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
        renderImages();
    }

    private void launchImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_IMAGES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (aiPortal != null && aiPortal.handleActivityResult(requestCode, resultCode, data)) return;
        if (requestCode != PICK_IMAGES || resultCode != RESULT_OK || data == null) return;

        selectedImages.clear();
        ClipData clip = data.getClipData();
        if (clip != null) {
            int count = Math.min(clip.getItemCount(), MAX_IMAGES);
            for (int i = 0; i < count; i++) {
                Uri uri = clip.getItemAt(i).getUri();
                persistPermission(data, uri);
                selectedImages.add(uri);
            }
            if (clip.getItemCount() > MAX_IMAGES) {
                Toast.makeText(this, "تم اعتماد أول خمس صور فقط.", Toast.LENGTH_LONG).show();
            }
        } else if (data.getData() != null) {
            persistPermission(data, data.getData());
            selectedImages.add(data.getData());
        }
        renderImages();
    }

    @SuppressLint("WrongConstant")
    private void persistPermission(Intent data, Uri uri) {
        try {
            int persistableFlags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (persistableFlags != 0) {
                getContentResolver().takePersistableUriPermission(uri, persistableFlags);
            }
        } catch (SecurityException ignored) {
        }
    }

    private void renderImages() {
        imageStrip.removeAllViews();
        if (selectedImages.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("أضف صورك لتبدأ تجربة بصرية أقوى");
            empty.setTextColor(Color.parseColor("#7F8AAA"));
            empty.setTextSize(13f);
            empty.setPadding(dp(16), 0, dp(16), 0);
            imageStrip.addView(empty);
        } else {
            for (int i = 0; i < selectedImages.size(); i++) {
                int index = i;
                ImageView image = new ImageView(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(92), dp(92));
                params.setMargins(0, 0, dp(10), 0);
                image.setLayoutParams(params);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImageURI(selectedImages.get(i));
                image.setBackground(roundedDrawable(Color.rgb(20, 26, 45), 18f));
                image.setClipToOutline(true);
                image.setElevation(dp(6));
                image.setContentDescription("الصورة " + (i + 1));
                image.setOnLongClickListener(view -> {
                    selectedImages.remove(index);
                    renderImages();
                    return true;
                });
                imageStrip.addView(image);
            }
        }
        status.setText(selectedImages.size() + " / " + MAX_IMAGES);
    }

    private GradientDrawable roundedDrawable(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private void copyPrompt(boolean showFeedback) {
        String text = prompt.getText().toString().trim();
        if (text.isEmpty()) {
            if (showFeedback) Toast.makeText(this, "اكتب السؤال أولًا.", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("AI Mode prompt", text));
        if (showFeedback) Toast.makeText(this, "تم نسخ السؤال ✓", Toast.LENGTH_SHORT).show();
    }

    private void ensurePermissionAndCreateCollage() {
        if (selectedImages.isEmpty()) {
            Toast.makeText(this, "اختر صورة واحدة على الأقل.", Toast.LENGTH_SHORT).show();
        } else if (Build.VERSION.SDK_INT > 28 ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            createCollage();
        } else {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (aiPortal != null && aiPortal.handlePermissionResult(requestCode)) return;
        if (requestCode == STORAGE_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            createCollage();
        }
    }

    private void createCollage() {
        setWorking(true);
        imageWorker.execute(() -> {
            ArrayList<Bitmap> bitmaps = new ArrayList<>();
            try {
                for (Uri uri : new ArrayList<>(selectedImages)) {
                    Bitmap bitmap = decodeScaled(uri, 1600);
                    if (bitmap != null) bitmaps.add(bitmap);
                }
                if (bitmaps.isEmpty()) throw new IllegalStateException("تعذّرت قراءة الصور.");

                Bitmap collage = buildCollage(bitmaps);
                saveCollage(collage);
                for (Bitmap bitmap : bitmaps) bitmap.recycle();
                collage.recycle();

                runOnUiThread(() -> {
                    setWorking(false);
                    status.setText("تم ✓");
                    copyPrompt(false);
                    Toast.makeText(this, "لوحتك جاهزة داخل Pictures/AI Mode Collages", Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                for (Bitmap bitmap : bitmaps) if (!bitmap.isRecycled()) bitmap.recycle();
                runOnUiThread(() -> {
                    setWorking(false);
                    status.setText("خطأ");
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setWorking(boolean working) {
        createCollageButton.setEnabled(!working);
        createCollageButton.setAlpha(working ? 0.65f : 1f);
        createCollageButton.setText(working ? "جارٍ الإنشاء…" : "إنشاء اللوحة  ◈");
        if (working) status.setText("•••");
    }

    private Bitmap decodeScaled(Uri uri, int maxSide) throws Exception {
        Bitmap original;
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            original = BitmapFactory.decodeStream(stream);
        }
        if (original == null) return null;
        int largest = Math.max(original.getWidth(), original.getHeight());
        if (largest <= maxSide) return original;
        float ratio = (float) maxSide / largest;
        Bitmap scaled = Bitmap.createScaledBitmap(original,
                Math.max(1, Math.round(original.getWidth() * ratio)),
                Math.max(1, Math.round(original.getHeight() * ratio)), true);
        original.recycle();
        return scaled;
    }

    private Bitmap buildCollage(List<Bitmap> bitmaps) {
        int count = bitmaps.size();
        int columns = count == 1 ? 1 : count <= 4 ? 2 : 3;
        int rows = (int) Math.ceil((double) count / columns);
        int tile = 900;
        int label = 86;
        int gap = 24;
        int header = 150;
        int footer = 82;
        int width = columns * tile + (columns + 1) * gap;
        int height = header + rows * (tile + label + gap) + footer;

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(new LinearGradient(0, 0, width, height,
                new int[]{Color.rgb(8, 10, 22), Color.rgb(21, 22, 52), Color.rgb(10, 18, 38)},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setColor(Color.rgb(240, 243, 255));
        title.setTextSize(54f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        canvas.drawText("AI MODE STUDIO", gap, 80, title);
        title.setTextSize(26f);
        title.setColor(Color.rgb(137, 151, 196));
        canvas.drawText("VISUAL CONTEXT • " + count + " IMAGES", gap, 118, title);

        Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tilePaint.setColor(Color.rgb(17, 23, 41));
        Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.rgb(226, 231, 255));
        labelPaint.setTextSize(38f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        for (int i = 0; i < count; i++) {
            Bitmap bitmap = bitmaps.get(i);
            int left = gap + (tile + gap) * (i % columns);
            int top = header + gap + (tile + label + gap) * (i / columns);
            RectF tileRect = new RectF(left, top, left + tile, top + tile);
            canvas.drawRoundRect(tileRect, 34, 34, tilePaint);

            float scale = Math.min((float) (tile - 24) / bitmap.getWidth(), (float) (tile - 24) / bitmap.getHeight());
            int imageWidth = Math.round(bitmap.getWidth() * scale);
            int imageHeight = Math.round(bitmap.getHeight() * scale);
            int imageLeft = left + (tile - imageWidth) / 2;
            int imageTop = top + (tile - imageHeight) / 2;
            canvas.drawBitmap(bitmap, null,
                    new Rect(imageLeft, imageTop, imageLeft + imageWidth, imageTop + imageHeight), imagePaint);
            canvas.drawText("الصورة " + (i + 1), left + tile / 2f, top + tile + 58, labelPaint);
        }

        labelPaint.setTextSize(25f);
        labelPaint.setColor(Color.rgb(106, 121, 166));
        canvas.drawText("PREPARED LOCALLY • READY FOR GOOGLE AI", width / 2f, height - 30, labelPaint);
        return output;
    }

    private Uri saveCollage(Bitmap bitmap) throws Exception {
        String name = "AI_Mode_Studio_" + System.currentTimeMillis() + ".jpg";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AI Mode Collages");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("تعذّر حفظ الصورة.");
            try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                if (stream == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)) {
                    throw new IllegalStateException("تعذّرت كتابة الصورة.");
                }
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
            return uri;
        }

        File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AI Mode Collages");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("تعذّر إنشاء مجلد الحفظ.");
        File output = new File(directory, name);
        try (FileOutputStream stream = new FileOutputStream(output)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)) {
                throw new IllegalStateException("تعذّرت كتابة الصورة.");
            }
        }
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(output)));
        return Uri.fromFile(output);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action)) {
            if (type != null && type.startsWith("image/")) {
                addIncomingImage(intent.getParcelableExtra(Intent.EXTRA_STREAM));
            } else if ("text/plain".equals(type)) {
                String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (text != null) prompt.setText(text);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null && type.startsWith("image/")) {
            ArrayList<Uri> images = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (images != null) {
                selectedImages.clear();
                selectedImages.addAll(images.subList(0, Math.min(images.size(), MAX_IMAGES)));
            }
        }
    }

    private void addIncomingImage(Uri uri) {
        if (uri == null) return;
        if (selectedImages.size() >= MAX_IMAGES) {
            Toast.makeText(this, "الحد الأقصى خمس صور.", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedImages.add(uri);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onPause() {
        if (aiPortal != null) aiPortal.onHostPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (aiPortal != null) aiPortal.onHostResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (aiPortal != null) aiPortal.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (aiPortal != null) aiPortal.destroy();
        imageWorker.shutdownNow();
        super.onDestroy();
    }
}
