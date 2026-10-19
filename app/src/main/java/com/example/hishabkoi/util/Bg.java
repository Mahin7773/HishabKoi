package com.example.hishabkoi.util;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Runs blocking work (Room queries, awaited Firestore calls) off the main thread. */
public final class Bg {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);

    private Bg() {}

    /** Runs {@code work} in the background; on failure shows the error as a toast. */
    public static <T> void run(Activity activity, Callable<T> work, Consumer<T> onDone) {
        run(activity, work, onDone, null);
    }

    /** Like {@link #run(Activity, Callable, Consumer)}, also calling {@code onError} after the toast. */
    public static <T> void run(Activity activity, Callable<T> work, Consumer<T> onDone, Runnable onError) {
        POOL.execute(() -> {
            try {
                T result = work.call();
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) onDone.accept(result);
                });
            } catch (Exception e) {
                Log.w("HishabKoi", "Background task failed", e);
                activity.runOnUiThread(() -> {
                    if (activity.isDestroyed()) return;
                    Toast.makeText(activity, Ui.message(e), Toast.LENGTH_LONG).show();
                    if (onError != null) onError.run();
                });
            }
        });
    }

    public static void run(Runnable work) {
        POOL.execute(work);
    }
}
