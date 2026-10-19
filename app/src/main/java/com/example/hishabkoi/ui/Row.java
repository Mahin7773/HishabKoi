package com.example.hishabkoi.ui;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;

/** One item in a {@link RowAdapter} list: either a section header or a card. */
public class Row {
    public boolean header;
    @DrawableRes
    public int icon;
    @ColorInt
    public int iconTint;
    /** Solid fill for the icon circle; 0 keeps the default tonal circle. */
    @ColorInt
    public int iconBg;
    public CharSequence title;
    public CharSequence subtitle;
    public CharSequence status;
    public CharSequence amount;
    @ColorInt
    public int amountColor;
    /** Card fill; 0 keeps the default surface colour. */
    @ColorInt
    public int cardColor;
    /** Shown struck through next to {@link #amount}, e.g. the debt before a circular settlement. */
    public CharSequence oldAmount;
    public CharSequence action1;
    public Runnable onAction1;
    public CharSequence action2;
    public Runnable onAction2;
    public Runnable onLongClick;

    public static Row header(CharSequence title) {
        Row r = new Row();
        r.header = true;
        r.title = title;
        return r;
    }

    public static Row card(@DrawableRes int icon, CharSequence title, CharSequence subtitle) {
        Row r = new Row();
        r.icon = icon;
        r.title = title;
        r.subtitle = subtitle;
        return r;
    }

    public Row amount(CharSequence text, @ColorInt int color) {
        amount = text;
        amountColor = color;
        return this;
    }

    public Row card(@ColorInt int color) {
        cardColor = color;
        return this;
    }

    /** Icon on a solid circle of this colour (icon drawn in {@code onColor}). */
    public Row solid(@ColorInt int color, @ColorInt int onColor) {
        iconBg = color;
        iconTint = onColor;
        return this;
    }

    public Row tint(@ColorInt int color) {
        iconTint = color;
        return this;
    }

    public Row status(CharSequence text) {
        status = text;
        return this;
    }

    public Row primary(CharSequence label, Runnable action) {
        action1 = label;
        onAction1 = action;
        return this;
    }

    public Row secondary(CharSequence label, Runnable action) {
        action2 = label;
        onAction2 = action;
        return this;
    }
}
