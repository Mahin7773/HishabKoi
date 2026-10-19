package com.example.hishabkoi.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Current Firebase user plus the cached username, so screens don't re-read it from Firestore. */
public final class Session {
    private Session() {}

    public static String uid() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        return u == null ? null : u.getUid();
    }

    public static boolean isLoggedIn() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        return u != null && u.isEmailVerified();
    }

    public static String username(Context c) {
        return prefs(c).getString("username_" + uid(), null);
    }

    public static void setUsername(Context c, String username) {
        prefs(c).edit().putString("username_" + uid(), username).apply();
    }

    public static long lastCycleScan(Context c) {
        return prefs(c).getLong("cycle_scan_" + uid(), 0);
    }

    public static void setLastCycleScan(Context c, long millis) {
        prefs(c).edit().putLong("cycle_scan_" + uid(), millis).apply();
    }

    /**
     * Time of the last check for answered requests. The first call starts the clock at "now",
     * so a fresh install or new phone doesn't replay old answers as notifications.
     */
    public static long answersCheckedAt(Context c) {
        String key = "answers_checked_" + uid();
        if (!prefs(c).contains(key)) prefs(c).edit().putLong(key, System.currentTimeMillis()).apply();
        return prefs(c).getLong(key, 0);
    }

    public static void setAnswersCheckedAt(Context c, long millis) {
        prefs(c).edit().putLong("answers_checked_" + uid(), millis).apply();
    }

    /**
     * Last loan figures shown on the home card: {owe, owed, walletLoans, incomingRequests,
     * proposalsForMe}. Shown instantly on launch while Firestore is asked for fresh ones.
     */
    public static long[] cachedSummary(Context c) {
        SharedPreferences p = prefs(c);
        String k = "summary_" + uid() + "_";
        return new long[]{p.getLong(k + "owe", 0), p.getLong(k + "owed", 0), p.getLong(k + "loans", 0),
                p.getLong(k + "requests", 0), p.getLong(k + "proposals", 0)};
    }

    public static void cacheSummary(Context c, long owe, long owed, long walletLoans, int requests, int proposals) {
        String k = "summary_" + uid() + "_";
        prefs(c).edit().putLong(k + "owe", owe).putLong(k + "owed", owed).putLong(k + "loans", walletLoans)
                .putLong(k + "requests", requests).putLong(k + "proposals", proposals).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("session", Context.MODE_PRIVATE);
    }
}
