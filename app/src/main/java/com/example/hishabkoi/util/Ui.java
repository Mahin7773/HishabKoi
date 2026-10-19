package com.example.hishabkoi.util;

import android.content.Context;
import android.text.format.DateFormat;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatActivity;

import com.example.hishabkoi.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;

import java.util.concurrent.ExecutionException;

public final class Ui {
    private Ui() {}

    public static void toast(Context c, CharSequence text) {
        Toast.makeText(c, text, Toast.LENGTH_LONG).show();
    }

    public static String message(Throwable e) {
        while ((e instanceof ExecutionException || e instanceof RuntimeException) && e.getCause() != null) {
            e = e.getCause();
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    public static MaterialToolbar toolbar(AppCompatActivity a, CharSequence title) {
        MaterialToolbar toolbar = a.findViewById(R.id.toolbar);
        toolbar.setTitle(title);
        toolbar.setNavigationOnClickListener(v -> a.finish());
        return toolbar;
    }

    public static String dateTime(Context c, long millis) {
        String pattern = DateFormat.is24HourFormat(c) ? "d MMM yyyy, HH:mm" : "d MMM yyyy, h:mm a";
        return DateFormat.format(pattern, millis).toString();
    }

    public static String date(long millis) {
        return DateFormat.format("d MMM yyyy", millis).toString();
    }

    /** "In ৳5,000 · Out ৳1,870.50" with the amounts in bold green / red. */
    public static CharSequence inOut(Context c, long in, long out) {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder("In ");
        appendColored(sb, Money.format(in), moneyIn(c));
        sb.append("  ·  Out ");
        appendColored(sb, Money.format(out), moneyOut(c));
        return sb;
    }

    private static void appendColored(android.text.SpannableStringBuilder sb, String text, @ColorInt int color) {
        int start = sb.length();
        sb.append(text);
        sb.setSpan(new android.text.style.ForegroundColorSpan(color), start, sb.length(), 0);
        sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, sb.length(), 0);
    }

    /**
     * Fills a {@code view_section_header}: icon on a solid circle, coloured bold title, one-line
     * explanation. Used by the home panels and the form screens.
     */
    public static void section(android.view.View header, @androidx.annotation.DrawableRes int icon,
                               CharSequence title, CharSequence sub, @ColorInt int color) {
        Context c = header.getContext();
        android.widget.ImageView iv = header.findViewById(R.id.section_icon);
        iv.setImageResource(icon);
        iv.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        iv.setImageTintList(android.content.res.ColorStateList.valueOf(c.getColor(R.color.on_solid)));
        android.widget.TextView tvTitle = header.findViewById(R.id.section_title);
        tvTitle.setText(title);
        tvTitle.setTextColor(color);
        ((android.widget.TextView) header.findViewById(R.id.section_sub)).setText(sub);
    }

    @ColorInt
    public static int moneyIn(Context c) {
        return c.getColor(R.color.money_in);
    }

    @ColorInt
    public static int moneyOut(Context c) {
        return c.getColor(R.color.money_out);
    }

    @ColorInt
    public static int attr(Context c, int attr) {
        return MaterialColors.getColor(c, attr, 0);
    }
}
