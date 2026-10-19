package com.example.hishabkoi.expense;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
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

import java.util.Locale;

/**
 * Grouped bar chart: income and expense per month, in the app's brand blue and purple.
 * The last month is the one selected on the Reports screen and gets a highlighted column.
 */
public class BarChartView extends View {
    private static final int GRID_LINES = 3;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;

    private String[] labels = new String[0];
    private long[] income = new long[0];
    private long[] expense = new long[0];
    private final int incomeColor, expenseColor;

    public BarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        incomeColor = context.getColor(R.color.chart_income);
        expenseColor = context.getColor(R.color.chart_expense);
        Typeface font = ResourcesCompat.getFont(context, R.font.google_sans);

        labelPaint.setTextSize(12 * density);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(font);
        labelPaint.setColor(Ui.attr(context, com.google.android.material.R.attr.colorOnSurfaceVariant));

        selectedLabelPaint.set(labelPaint);
        selectedLabelPaint.setColor(context.getColor(R.color.tile_local_icon));
        selectedLabelPaint.setTypeface(Typeface.create(font, Typeface.BOLD));

        gridPaint.setColor(context.getColor(R.color.panel_local_stroke));
        gridPaint.setStrokeWidth(density);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{4 * density, 4 * density}, 0));

        basePaint.setColor(context.getColor(R.color.panel_local_stroke));
        basePaint.setStrokeWidth(1.5f * density);

        valuePaint.setTextSize(10 * density);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Typeface.create(font, Typeface.BOLD));

        // Soft column behind the selected month.
        highlightPaint.setColor(context.getColor(R.color.tile_local));
    }

    public void setData(String[] labels, long[] income, long[] expense) {
        this.labels = labels;
        this.income = income;
        this.expense = expense;
        setContentDescription(describe());
        invalidate();
    }

    public int incomeColor() {
        return incomeColor;
    }

    public int expenseColor() {
        return expenseColor;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = labels.length;
        if (n == 0) return;
        long max = 1;
        for (int i = 0; i < n; i++) max = Math.max(max, Math.max(income[i], expense[i]));

        float labelSpace = 22 * density;
        float chartTop = 18 * density;
        float chartBottom = getHeight() - labelSpace;
        float chartHeight = chartBottom - chartTop;
        float slot = (float) getWidth() / n;
        float barWidth = Math.min(slot * 0.28f, 22 * density);
        float gap = 4 * density;
        float radius = 6 * density;

        // Selected month column
        float hx = slot * (n - 1);
        rect.set(hx + 2 * density, 0, hx + slot - 2 * density, getHeight());
        canvas.drawRoundRect(rect, 12 * density, 12 * density, highlightPaint);

        for (int g = 1; g <= GRID_LINES; g++) {
            float y = chartBottom - chartHeight * g / GRID_LINES;
            canvas.drawLine(0, y, getWidth(), y, gridPaint);
        }
        canvas.drawLine(0, chartBottom, getWidth(), chartBottom, basePaint);

        for (int i = 0; i < n; i++) {
            float center = slot * i + slot / 2;
            drawBar(canvas, center - gap / 2 - barWidth, barWidth, income[i], max, chartBottom, chartHeight, incomeColor, radius);
            drawBar(canvas, center + gap / 2, barWidth, expense[i], max, chartBottom, chartHeight, expenseColor, radius);
            canvas.drawText(labels[i], center, getHeight() - 6 * density,
                    i == n - 1 ? selectedLabelPaint : labelPaint);
        }
    }

    private void drawBar(Canvas c, float left, float width, long value, long max, float bottom,
                         float height, int color, float radius) {
        if (value <= 0) return;
        float top = bottom - height * value / max;
        valuePaint.setColor(color);
        c.drawText(shortAmount(value), left + width / 2, top - 4 * density, valuePaint);
        barPaint.setColor(color);
        rect.set(left, top, left + width, bottom);
        c.drawRoundRect(rect, radius, radius, barPaint);
        // square off the bottom corners so bars sit flat on the baseline
        rect.set(left, Math.max(top, bottom - radius), left + width, bottom);
        c.drawRect(rect, barPaint);
    }

    /** ৳800, ৳1.9k, ৳12k: short enough to sit above a bar. */
    private static String shortAmount(long cents) {
        double taka = cents / 100.0;
        if (taka < 1000) return "৳" + Math.round(taka);
        double k = taka / 1000;
        return "৳" + (k < 10 ? String.format(Locale.US, "%.1f", k).replace(".0", "") : String.valueOf(Math.round(k))) + "k";
    }

    private String describe() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < labels.length; i++) {
            sb.append(labels[i]).append(": income ").append(Money.format(income[i]))
                    .append(", expense ").append(Money.format(expense[i])).append(". ");
        }
        return sb.toString();
    }
}
