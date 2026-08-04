package com.example.googleaimodeclient;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class BoardStore {
    private BoardStore() {
    }

    @NonNull
    static Uri save(@NonNull Context context, @NonNull Bitmap board) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = context.getString(R.string.collage_filename, timestamp);
        String album = context.getString(R.string.collage_album);
        ContentResolver resolver = context.getContentResolver();

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + File.separator + album
            );
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        } else {
            File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File directory = new File(pictures, album);
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("Unable to create output directory");
            }
            values.put(MediaStore.Images.Media.DATA, new File(directory, fileName).getAbsolutePath());
        }

        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("MediaStore rejected image");
        }

        boolean complete = false;
        try (OutputStream stream = resolver.openOutputStream(uri, "w")) {
            if (stream == null || !board.compress(Bitmap.CompressFormat.JPEG, 94, stream)) {
                throw new IOException("Unable to encode image board");
            }
            complete = true;
        } finally {
            if (!complete) {
                resolver.delete(uri, null, null);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues publish = new ContentValues();
            publish.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, publish, null, null);
        }
        return uri;
    }
}
