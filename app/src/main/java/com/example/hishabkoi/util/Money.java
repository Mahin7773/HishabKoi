package com.example.hishabkoi.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** All amounts are stored as whole poisha (1/100 taka) in a long, so arithmetic is exact. */
public final class Money {
    private Money() {}

    public static String format(long cents) {
        return (cents < 0 ? "-" : "") + "৳" + number(Math.abs(cents));
    }

    /** "1,870.50" — grouped amount without the ৳ sign, for figures shown next to a ৳ headline. */
    public static String number(long cents) {
        long abs = Math.abs(cents);
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(abs % 100 == 0 ? 0 : 2);
        nf.setMaximumFractionDigits(2);
        return (cents < 0 ? "-" : "") + nf.format(abs / 100.0);
    }

    public static String signed(long cents) {
        return (cents > 0 ? "+" : "") + format(cents);
    }

    /** Parses user input like "120" or "99.5" into poisha. Returns -1 if invalid or not positive. */
    public static long parse(CharSequence text) {
        if (text == null) return -1;
        try {
            BigDecimal value = new BigDecimal(text.toString().trim());
            if (value.signum() <= 0 || value.scale() > 2) return -1;
            return value.movePointRight(2).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return -1;
        }
    }

    public static String plain(long cents) {
        return new BigDecimal(cents).movePointLeft(2).stripTrailingZeros().toPlainString();
    }
}
