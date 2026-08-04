package com.example.googleaimodeclient;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

final class CollageComposer {
    private static final int BOARD_START = Color.rgb(11, 14, 22);
    private static final int BOARD_END = Color.rgb(23, 28, 43);
    private static final int TILE_BACKGROUND = Color.rgb(31, 37, 52);
    private static final int PRIMARY_TEXT = Color.rgb(244, 246, 255);
    private static final int SECONDARY_TEXT = Color.rgb(174, 185, 255);
    private static final int ACCENT = Color.rgb(110, 231, 183);

    private CollageComposer() {
    }

    @NonNull
    static Bitmap createBoard(@NonNull Context context, @NonNull List<Uri> imageUris)
            throws IOException {
        CollageLayout layout = CollageLayout.forCount(imageUris.size());
        Bitmap output = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setShader(new LinearGradient(
                0,
                0,
                layout.width,
                layout.height,
                BOARD_START,
                BOARD_END,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, layout.width, layout.height, backgroundPaint);

        drawHeader(context, canvas, layout);

        Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tilePaint.setColor(TILE_BACKGROUND);
        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(PRIMARY_TEXT);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        labelPaint.setTextSize(Math.max(28f, layout.labelHeight * 0.43f));

        try {
            for (int index = 0; index < imageUris.size(); index++) {
                CollageLayout.Tile tile = layout.tileAt(index);
                float corner = Math.max(22f, layout.tileSize * 0.035f);
                RectF tileRect = new RectF(tile.left, tile.top, tile.right, tile.bottom);
                canvas.drawRoundRect(tileRect, corner, corner, tilePaint);

                Bitmap source = decodeImage(context, imageUris.get(index), layout.tileSize * 2);
                try {
                    drawContainedImage(canvas, source, tileRect, corner, imagePaint);
                } finally {
                    source.recycle();
                }

                String label = context.getString(R.string.collage_image_label, index + 1);
                float labelY = tile.bottom + layout.labelHeight * 0.67f;
                canvas.drawText(label, tileRect.centerX(), labelY, labelPaint);
            }
        } catch (IOException | RuntimeException error) {
            output.recycle();
            throw error;
        }

        return output;
    }

    private static void drawHeader(Context context, Canvas canvas, CollageLayout layout) {
        Paint brandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        brandPaint.setColor(SECONDARY_TEXT);
        brandPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        brandPaint.setTextSize(Math.max(24f, layout.headerHeight * 0.29f));
        brandPaint.setTextAlign(Paint.Align.LEFT);

        float baseline = layout.headerHeight * 0.65f;
        canvas.drawText(context.getString(R.string.collage_brand), layout.gap, baseline, brandPaint);

        Paint countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        countPaint.setColor(ACCENT);
        countPaint.setTextSize(Math.max(26f, layout.headerHeight * 0.34f));
        countPaint.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL));
        countPaint.setTextAlign(Paint.Align.END);
        canvas.drawText(String.format(Locale.US, "%02d", layout.count), layout.width - layout.gap, baseline, countPaint);
    }

    private static void drawContainedImage(
            Canvas canvas,
            Bitmap bitmap,
            RectF tile,
            float corner,
            Paint paint
    ) {
        float scale = Math.min(tile.width() / bitmap.getWidth(), tile.height() / bitmap.getHeight());
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        RectF destination = new RectF(
                tile.centerX() - width / 2f,
                tile.centerY() - height / 2f,
                tile.centerX() + width / 2f,
                tile.centerY() + height / 2f
        );

        int saveCount = canvas.save();
        Path clip = new Path();
        clip.addRoundRect(tile, corner, corner, Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.drawBitmap(bitmap, null, destination, paint);
        canvas.restoreToCount(saveCount);
    }

    @NonNull
    static Bitmap decodeImage(@NonNull Context context, @NonNull Uri uri, int maxSide)
            throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            if (stream == null) {
                throw new IOException("Unable to open image");
            }
            BitmapFactory.decodeStream(stream, null, bounds);
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Invalid image dimensions");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxSide);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap decoded;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            if (stream == null) {
                throw new IOException("Unable to open image");
            }
            decoded = BitmapFactory.decodeStream(stream, null, options);
        }
        if (decoded == null) {
            throw new IOException("Unable to decode image");
        }

        int orientation = readOrientation(context, uri);
        Matrix matrix = orientationMatrix(orientation);
        if (matrix.isIdentity()) {
            return decoded;
        }

        try {
            Bitmap oriented = Bitmap.createBitmap(
                    decoded,
                    0,
                    0,
                    decoded.getWidth(),
                    decoded.getHeight(),
                    matrix,
                    true
            );
            if (oriented != decoded) {
                decoded.recycle();
            }
            return oriented;
        } catch (RuntimeException error) {
            decoded.recycle();
            throw error;
        }
    }

    private static int calculateSampleSize(int width, int height, int maxSide) {
        int largest = Math.max(width, height);
        int sample = 1;
        while (largest / (sample * 2) >= maxSide) {
            sample *= 2;
        }
        return sample;
    }

    private static int readOrientation(Context context, Uri uri) {
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            if (stream == null) {
                return ExifInterface.ORIENTATION_NORMAL;
            }
            ExifInterface exif = new ExifInterface(stream);
            return exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
        } catch (IOException | RuntimeException ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Matrix orientationMatrix(int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setScale(1f, -1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90f);
                break;
            default:
                break;
        }
        return matrix;
    }
}
