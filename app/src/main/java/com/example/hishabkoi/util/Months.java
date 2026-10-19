package com.example.hishabkoi.util;

import android.text.format.DateFormat;

import java.util.Calendar;

/** Month boundaries in local time. A month is identified by the millis of its first instant. */
public final class Months {
    private Months() {}

    public static long startOf(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static long current() {
        return startOf(System.currentTimeMillis());
    }

    public static long add(long monthStart, int months) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(monthStart);
        c.add(Calendar.MONTH, months);
        return c.getTimeInMillis();
    }

    public static String label(long monthStart) {
        return DateFormat.format("MMMM yyyy", monthStart).toString();
    }

    public static String shortLabel(long monthStart) {
        return DateFormat.format("MMM", monthStart).toString();
    }
}
