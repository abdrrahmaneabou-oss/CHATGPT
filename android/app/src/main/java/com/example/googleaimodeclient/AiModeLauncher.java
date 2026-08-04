package com.example.googleaimodeclient;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;

final class AiModeLauncher {
    private static final Uri AI_MODE_URI = Uri.parse("https://www.google.com/ai");

    private AiModeLauncher() {
    }

    static boolean open(@NonNull Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int initialHeight = Math.max(
                (int) (480 * metrics.density),
                (int) (metrics.heightPixels * 0.82f)
        );
        initialHeight = Math.min(initialHeight, metrics.heightPixels);

        CustomTabColorSchemeParams colors = new CustomTabColorSchemeParams.Builder()
                .setToolbarColor(ContextCompat.getColor(context, R.color.app_surface))
                .setNavigationBarColor(ContextCompat.getColor(context, R.color.app_background))
                .setNavigationBarDividerColor(Color.TRANSPARENT)
                .build();

        try {
            CustomTabsIntent customTab = new CustomTabsIntent.Builder()
                    .setInitialActivityHeightPx(initialHeight)
                    .setShowTitle(false)
                    .setUrlBarHidingEnabled(true)
                    .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                    .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
                    .setDefaultColorSchemeParams(colors)
                    .build();
            customTab.launchUrl(context, AI_MODE_URI);
            return true;
        } catch (RuntimeException customTabError) {
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW, AI_MODE_URI);
                context.startActivity(fallback);
                return true;
            } catch (RuntimeException browserError) {
                return false;
            }
        }
    }
}
