package com.example.hishabkoi.expense;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatActivity;

import com.example.hishabkoi.R;
import com.example.hishabkoi.local.AppDatabase;
import com.example.hishabkoi.local.CategoryTotal;
import com.example.hishabkoi.local.Entry;
import com.example.hishabkoi.local.EntryDao;
import com.example.hishabkoi.util.Bg;
import com.example.hishabkoi.util.Money;
import com.example.hishabkoi.util.Months;
import com.example.hishabkoi.util.Session;
import com.example.hishabkoi.util.Ui;

import java.util.List;

/** Income vs expense for the last six months, and spending by category for the chosen month. */
public class ReportsActivity extends AppCompatActivity {
    private static final int MONTHS_SHOWN = 6;

    private long month = Months.current();
    private TextView tvMonth, tvSummary;
    private BarChartView barChart;
    private DonutChartView donutChart;
    private LinearLayout barLegend, donutLegend;

    /** Everything one screen refresh needs, loaded in a single background call. */
    private static class Report {
        String[] labels = new String[MONTHS_SHOWN];
        long[] income = new long[MONTHS_SHOWN];
        long[] expense = new long[MONTHS_SHOWN];
        List<CategoryTotal> byCategory;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);
        Ui.toolbar(this, getString(R.string.tile_reports));
        View bar = findViewById(R.id.month_bar);
        tvMonth = bar.findViewById(R.id.tv_month);
        tvSummary = bar.findViewById(R.id.tv_month_summary);
        bar.findViewById(R.id.btn_prev).setOnClickListener(v -> changeMonth(-1));
        bar.findViewById(R.id.btn_next).setOnClickListener(v -> changeMonth(1));
        barChart = findViewById(R.id.bar_chart);
        donutChart = findViewById(R.id.donut_chart);
        barLegend = findViewById(R.id.bar_legend);
        donutLegend = findViewById(R.id.donut_legend);

        addLegend(barLegend, barChart.incomeColor(), getString(R.string.income), null);
        addLegend(barLegend, barChart.expenseColor(), getString(R.string.expense), null);
        load();
    }

    private void changeMonth(int delta) {
        month = Months.add(month, delta);
        load();
    }

    private void load() {
        tvMonth.setText(Months.label(month));
        String me = Session.uid();
        EntryDao dao = AppDatabase.get(this).entries();
        long selected = month;
        Bg.run(this, () -> {
            Report r = new Report();
            for (int i = 0; i < MONTHS_SHOWN; i++) {
                long start = Months.add(selected, i - (MONTHS_SHOWN - 1));
                long end = Months.add(start, 1);
                r.labels[i] = Months.shortLabel(start);
                r.income[i] = dao.sum(me, Entry.INCOME, start, end);
                r.expense[i] = dao.sum(me, Entry.EXPENSE, start, end);
            }
            r.byCategory = dao.byCategory(me, Entry.EXPENSE, selected, Months.add(selected, 1));
            return r;
        }, this::render);
    }

    private void render(Report r) {
        int last = MONTHS_SHOWN - 1;
        tvSummary.setText(Ui.inOut(this, r.income[last], r.expense[last]));
        barChart.setData(r.labels, r.income, r.expense);

        long[] values = new long[r.byCategory.size()];
        donutLegend.removeAllViews();
        long total = 0;
        for (CategoryTotal c : r.byCategory) total += c.total;
        for (int i = 0; i < values.length; i++) {
            CategoryTotal c = r.byCategory.get(i);
            values[i] = c.total;
            int percent = (int) Math.round(100.0 * c.total / total);
            addDonutLegend(DonutChartView.PALETTE[i % DonutChartView.PALETTE.length], c.name,
                    percent + "% of spending", Money.format(c.total));
        }
        donutChart.setValues(values);
        if (values.length == 0) {
            TextView none = new TextView(this);
            none.setText(R.string.no_spending);
            none.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            donutLegend.addView(none);
        }
    }

    /** Full-width legend row beside the donut: dot, name over share, amount on the right. */
    private void addDonutLegend(@ColorInt int color, String name, String share, String amount) {
        View v = getLayoutInflater().inflate(R.layout.item_donut_legend, donutLegend, false);
        v.findViewById(R.id.legend_swatch).setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        ((TextView) v.findViewById(R.id.legend_label)).setText(name);
        ((TextView) v.findViewById(R.id.legend_percent)).setText(share);
        TextView tvAmount = v.findViewById(R.id.legend_amount);
        tvAmount.setText(amount);
        donutLegend.addView(v);
    }

    private void addLegend(LinearLayout parent, @ColorInt int color, String label, String value) {
        View v = getLayoutInflater().inflate(R.layout.item_legend, parent, false);
        View swatch = v.findViewById(R.id.legend_swatch);
        swatch.setBackgroundResource(R.drawable.bg_circle);
        swatch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        ((TextView) v.findViewById(R.id.legend_label)).setText(label);
        TextView tvValue = v.findViewById(R.id.legend_value);
        tvValue.setText(value);
        tvValue.setVisibility(value == null ? View.GONE : View.VISIBLE);
        if (value == null) v.setPaddingRelative(v.getPaddingStart(), 0, 0, 0);
        if (value == null && parent.getChildCount() > 0) {
            ((LinearLayout.LayoutParams) v.getLayoutParams()).setMarginStart(
                    (int) (12 * getResources().getDisplayMetrics().density));
        }
        parent.addView(v);
    }
}
