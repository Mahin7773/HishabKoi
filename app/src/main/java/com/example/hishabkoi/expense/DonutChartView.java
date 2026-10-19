package com.example.hishabkoi.expense;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.example.hishabkoi.R;
import com.example.hishabkoi.util.Money;
import com.example.hishabkoi.util.Ui;

/** Donut chart of category totals, in the app's blue → indigo → purple family, total in the middle. */
public class DonutChartView extends View {
    /** Alternates dark and light steps of blue / indigo / purple so neighbouring slices stay distinct. */
    public static final int[] PALETTE = {
            0xFF0B57D0, 0xFF9C6ADE, 0xFF4F8CF5, 0xFF5B3FD0, 0xFF8AB4F8,
            0xFF7B2CBF, 0xFFB9A6F5, 0xFF1F6FEB, 0xFFD7AEFB, 0xFF3F51B5};

    private static final float GAP_DEGREES = 2.5f;

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint totalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float density;
    private long[] values = new long[0];

    public DonutChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        float stroke = 22 * density;
        Typeface font = ResourcesCompat.getFont(context, R.font.google_sans);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(stroke);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(stroke);
        trackPaint.setColor(context.getColor(R.color.tile_local));

        totalPaint.setTextAlign(Paint.Align.CENTER);
        totalPaint.setTextSize(17 * density);
        totalPaint.setColor(context.getColor(R.color.tile_local_icon));
        totalPaint.setTypeface(Typeface.create(font, Typeface.BOLD));

        captionPaint.setTextAlign(Paint.Align.CENTER);
        captionPaint.setTextSize(11 * density);
        captionPaint.setColor(Ui.attr(context, com.google.android.material.R.attr.colorOnSurfaceVariant));
        captionPaint.setTypeface(font);
    }

    public void setValues(long[] values) {
        this.values = values;
        long total = 0;
        for (long v : values) total += v;
        setContentDescription("Total " + Money.format(total));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight()) - arcPaint.getStrokeWidth();
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        oval.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        canvas.drawArc(oval, 0, 360, false, trackPaint);

        long total = 0;
        int nonZero = 0;
        for (long v : values) {
            total += v;
            if (v > 0) nonZero++;
        }
        if (total > 0) {
            float gap = nonZero > 1 ? GAP_DEGREES : 0;
            float start = -90;
            for (int i = 0; i < values.length; i++) {
                float sweep = 360f * values[i] / total;
                if (sweep <= 0) continue;
                arcPaint.setColor(PALETTE[i % PALETTE.length]);
                canvas.drawArc(oval, start + gap / 2, Math.max(sweep - gap, 0.5f), false, arcPaint);
                start += sweep;
            }
        }
        float lineGap = 4 * density;
        canvas.drawText(Money.format(total), cx, cy - lineGap / 2 - totalPaint.descent(), totalPaint);
        canvas.drawText("Spent", cx, cy + lineGap / 2 - captionPaint.ascent(), captionPaint);
    }
}
