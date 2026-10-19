package com.example.hishabkoi.loans;

import com.example.hishabkoi.R;
import com.example.hishabkoi.cloud.Cloud;
import com.example.hishabkoi.cloud.Debt;
import com.example.hishabkoi.ui.ListActivity;
import com.example.hishabkoi.ui.Row;
import com.example.hishabkoi.util.Bg;
import com.example.hishabkoi.util.Money;
import com.example.hishabkoi.util.Session;
import com.example.hishabkoi.util.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Who owes whom. After a circular settlement the old amount is shown struck through next to the
 * new one, so everyone can see what changed.
 */
public class BalancesActivity extends ListActivity {
    private final String me = Session.uid();

    @Override
    protected int titleRes() {
        return R.string.tile_balances;
    }

    @Override
    protected int emptyRes() {
        return R.string.no_balances;
    }

    @Override
    protected void load() {
        setLoading(true);
        Bg.run(this, () -> Cloud.debtsOf(me), debts -> {
            debts.sort((a, b) -> Long.compare(Math.abs(b.net), Math.abs(a.net)));
            List<Row> rows = new ArrayList<>();
            for (Debt d : debts) rows.add(row(d));
            show(rows);
        }, () -> setLoading(false));
    }

    private Row row(Debt d) {
        String other = "@" + d.name(d.other(me));
        long owed = d.owedBy(me);
        String title;
        int color;
        if (owed > 0) {
            title = "You owe " + other;
            color = Ui.moneyOut(this);
        } else if (owed < 0) {
            title = other + " owes you";
            color = Ui.moneyIn(this);
        } else {
            title = getString(R.string.settled) + " with " + other;
            color = 0;
        }
        String subtitle = Ui.dateTime(this, d.updatedAt);
        Row r = Row.card(R.drawable.ic_group, title, subtitle).amount(Money.format(Math.abs(owed)), color);
        solid(r, owed > 0 ? R.color.solid_red : owed < 0 ? R.color.solid_green : R.color.solid_grey);
        if (d.changedByCycle()) {
            Long before = d.previousOwedBy(me);
            r.oldAmount = Money.format(Math.abs(before));
            r.status(getString(R.string.cycle_settled));
        }
        return r;
    }
}
