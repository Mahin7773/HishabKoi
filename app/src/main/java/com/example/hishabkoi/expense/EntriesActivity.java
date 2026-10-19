package com.example.hishabkoi.expense;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.example.hishabkoi.R;
import com.example.hishabkoi.local.AppDatabase;
import com.example.hishabkoi.local.Entry;
import com.example.hishabkoi.ui.ListActivity;
import com.example.hishabkoi.ui.Row;
import com.example.hishabkoi.util.Bg;
import com.example.hishabkoi.util.Money;
import com.example.hishabkoi.util.Months;
import com.example.hishabkoi.util.Session;
import com.example.hishabkoi.util.Ui;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** Personal income and expenses for one month, newest first. */
public class EntriesActivity extends ListActivity {
    private long month = Months.current();
    private TextView tvMonth, tvSummary;

    @Override
    protected int titleRes() {
        return R.string.tile_transactions;
    }

    @Override
    protected int emptyRes() {
        return R.string.no_entries;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View bar = findViewById(R.id.month_bar);
        bar.setVisibility(View.VISIBLE);
        tvMonth = bar.findViewById(R.id.tv_month);
        tvSummary = bar.findViewById(R.id.tv_month_summary);
        bar.findViewById(R.id.btn_prev).setOnClickListener(v -> changeMonth(-1));
        bar.findViewById(R.id.btn_next).setOnClickListener(v -> changeMonth(1));
    }

    private void changeMonth(int delta) {
        month = Months.add(month, delta);
        load();
    }

    @Override
    protected void load() {
        tvMonth.setText(Months.label(month));
        String me = Session.uid();
        long from = month, to = Months.add(month, 1);
        Bg.run(this, () -> AppDatabase.get(this).entries().between(me, from, to), entries -> {
            long in = 0, out = 0;
            List<Row> rows = new ArrayList<>();
            for (Entry e : entries) {
                if (e.isIncome()) in += e.amountCents;
                else out += e.amountCents;
                rows.add(row(e));
            }
            tvSummary.setText(Ui.inOut(this, in, out));
            show(rows);
        });
    }

    private Row row(Entry e) {
        String subtitle = Ui.date(e.timestamp) + (TextUtils.isEmpty(e.note) ? "" : " · " + e.note);
        Row r = Row.card(e.isIncome() ? R.drawable.ic_arrow_upward : R.drawable.ic_arrow_downward,
                        e.category, subtitle)
                .amount(Money.signed(e.isIncome() ? e.amountCents : -e.amountCents),
                        e.isIncome() ? Ui.moneyIn(this) : Ui.moneyOut(this));
        solid(r, e.isIncome() ? R.color.solid_green : R.color.solid_red);
        r.onLongClick = () -> confirmDelete(e);
        return r.secondary(getString(R.string.delete), () -> confirmDelete(e));
    }

    private void confirmDelete(Entry e) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_entry_q)
                .setMessage(e.category + " · " + Money.format(e.amountCents))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> Bg.run(this, () -> {
                    AppDatabase.get(this).entries().delete(e);
                    return true;
                }, ok -> load()))
                .show();
    }
}
