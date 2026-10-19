package com.example.hishabkoi.util;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import com.example.hishabkoi.HishabApp;
import com.example.hishabkoi.R;

import java.util.HashSet;
import java.util.Set;

/**
 * Local notifications. There is no push server: the app checks Firestore when it is opened and
 * notifies about anything new it finds. Each key is only notified once.
 */
public final class Notifier {
    private Notifier() {}

    public static void notifyOnce(Context ctx, String key, String title, String text, Class<?> target) {
        SharedPreferences prefs = ctx.getSharedPreferences("notified", Context.MODE_PRIVATE);
        Set<String> seen = new HashSet<>(prefs.getStringSet("keys", new HashSet<>()));
        if (!seen.add(key)) return;
        prefs.edit().putStringSet("keys", seen).apply();
        post(ctx, key.hashCode(), title, text, target);
    }

    private static void post(Context ctx, int id, String title, String text, Class<?> target) {
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        Intent intent = new Intent(ctx, target)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, id, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(ctx, HishabApp.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        ctx.getSystemService(NotificationManager.class).notify(id, n);
    }
}
