package com.example.hishabkoi;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.hishabkoi.ui.GlassBackdrop;

public class HishabApp extends Application {
    public static final String CHANNEL_ID = "hishab_updates";

    @Override
    public void onCreate() {
        super.onCreate();
        // Soft colour glows behind every screen, for the frosted panels to show through.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPostCreated(@NonNull Activity activity, @Nullable Bundle state) {
                GlassBackdrop.attach(activity);
                keepAboveKeyboard(activity);
            }

            @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle s) {}
            @Override public void onActivityStarted(@NonNull Activity a) {}
            @Override public void onActivityResumed(@NonNull Activity a) {}
            @Override public void onActivityPaused(@NonNull Activity a) {}
            @Override public void onActivityStopped(@NonNull Activity a) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle s) {}
            @Override public void onActivityDestroyed(@NonNull Activity a) {}
        });

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(getString(R.string.channel_desc));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    /**
     * Shrinks the screen above the on-screen keyboard. Android 15+ draws apps edge to edge and
     * ignores adjustResize, so without this the keyboard covers fields and buttons and the page
     * can't scroll to them.
     */
    private static void keepAboveKeyboard(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            v.setPadding(0, 0, 0, ime);
            // Once the screen has shrunk, scroll the field being typed in fully into view,
            // with some room below it for the field's border and helper text.
            if (ime > 0) {
                v.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        v.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        View focused = activity.getCurrentFocus();
                        if (focused == null) return;
                        Rect r = new Rect();
                        focused.getDrawingRect(r);
                        r.bottom += (int) (24 * v.getResources().getDisplayMetrics().density);
                        focused.requestRectangleOnScreen(r, false);
                    }
                });
            }
            // The keyboard is handled here; children must not pad for it a second time.
            // The keyboard is handled here, so children must not pad for it again. While it is open
            // it also covers the navigation bar, so children drop their bottom bar padding too;
            // otherwise a ScrollView would stop short of the field being typed in.
            WindowInsetsCompat.Builder out = new WindowInsetsCompat.Builder(insets)
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE);
            if (ime > 0) {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                out.setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(bars.left, bars.top, bars.right, 0));
            }
            return out.build();
        });
    }
}
