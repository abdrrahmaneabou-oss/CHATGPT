package com.example.googleaimodeclient;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ThumbnailLoader {
    private final Context context;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<>(12 * 1024) {
        @Override
        protected int sizeOf(@NonNull String key, @NonNull Bitmap value) {
            return Math.max(1, value.getAllocationByteCount() / 1024);
        }
    };

    ThumbnailLoader(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    void load(@NonNull Uri uri, @NonNull ImageView target) {
        String key = uri.toString();
        target.setTag(key);
        Bitmap cached = cache.get(key);
        if (cached != null && !cached.isRecycled()) {
            target.setImageBitmap(cached);
            return;
        }

        target.setImageDrawable(null);
        executor.execute(() -> {
            try {
                Bitmap thumbnail;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    thumbnail = context.getContentResolver().loadThumbnail(
                            uri,
                            new Size(420, 420),
                            null
                    );
                } else {
                    thumbnail = CollageComposer.decodeImage(context, uri, 520);
                }
                cache.put(key, thumbnail);
                mainHandler.post(() -> {
                    if (key.equals(target.getTag())) {
                        target.setImageBitmap(thumbnail);
                    }
                });
            } catch (Exception ignored) {
                mainHandler.post(() -> {
                    if (key.equals(target.getTag())) {
                        target.setImageResource(R.drawable.ic_gallery);
                    }
                });
            }
        });
    }

    void shutdown() {
        executor.shutdownNow();
    }
}

