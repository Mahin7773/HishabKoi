package com.example.hishabkoi.loans;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import com.example.hishabkoi.R;
import com.example.hishabkoi.cloud.Cloud;
import com.example.hishabkoi.cloud.Txn;
import com.example.hishabkoi.ui.ListActivity;
import com.example.hishabkoi.ui.Row;
import com.example.hishabkoi.util.Bg;
import com.example.hishabkoi.util.Money;
import com.example.hishabkoi.util.Session;
import com.example.hishabkoi.util.Ui;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bank-statement style list of every loan event I am part of, filterable by kind. */
public class HistoryActivity extends ListActivity {
    private static final String ALL = "All";
    private static final String LOANS = "Loans";
    private static final String REPAYMENTS = "Repayments";
    private static final String PENDING = "Pending";
    private static final String DECLINED = "Declined";
    private static final String CANCELLED = "Cancelled";
    private static final String CIRCULAR = "Circular";
    private static final String[] CATEGORIES = {ALL, LOANS, REPAYMENTS, PENDING, DECLINED, CANCELLED, CIRCULAR};

    private final String me = Session.uid();
    private ChipGroup chips;
    private List<Txn> all = new ArrayList<>();
    private String filter = ALL;

    @Override
    protected int titleRes() {
        return R.string.tile_history;
    }

    @Override
    protected int emptyRes() {
        return R.string.no_history;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        chips = addHeader(R.layout.view_history_filter).findViewById(R.id.filter_chips);
    }

    @Override
    protected void load() {
        setLoading(true);
        Bg.run(this, () -> Cloud.history(me), list -> {
            all = list;
            renderChips();
            render();
        }, () -> setLoading(false));
    }

    /** One chip per category that has something in it, with its count. "All" is always there. */
    private void renderChips() {
        Map<String, Integer> counts = new HashMap<>();
        for (Txn t : all) counts.merge(category(t), 1, Integer::sum);
        counts.put(ALL, all.size());
        if (!counts.containsKey(filter)) filter = ALL;
        chips.setOnCheckedStateChangeListener(null);
        chips.removeAllViews();
        for (String c : CATEGORIES) {
            Integer n = counts.get(c);
            if (n == null) continue;
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, chips, false);
            chip.setId(View.generateViewId());
            chip.setText(c + " · " + n);
            chip.setTag(c);
            chips.addView(chip);
            if (c.equals(filter)) chip.setChecked(true);
        }
        chips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            filter = (String) group.findViewById(ids.get(0)).getTag();
            render();
        });
    }

    private void render() {
        List<Row> rows = new ArrayList<>();
        for (Txn t : all) {
            if (ALL.equals(filter) || filter.equals(category(t))) rows.add(row(t));
        }
        show(rows);
    }

    /** Which filter chip an event belongs to. Loans and repayments count only once accepted. */
    private static String category(Txn t) {
        switch (t.status) {
            case Txn.PENDING:
                return PENDING;
            case Txn.DECLINED:
                return DECLINED;
            case Txn.CANCELLED:
                return CANCELLED;
        }
        if (Txn.CYCLE.equals(t.type)) return CIRCULAR;
        return Txn.REPAY.equals(t.type) ? REPAYMENTS : LOANS;
    }

    private Row row(Txn t) {
        String other = "@" + t.otherName(me);
        boolean borrower = t.iAmBorrower(me);
        String title;
        int icon;
        // Requests that never went through describe the request, not a money movement.
        boolean done = Txn.ACCEPTED.equals(t.status);
        switch (t.type) {
            case Txn.BORROW:
                if (done) title = borrower ? "Borrowed from " + other : "Lent to " + other;
                else title = borrower ? "Loan from " + other : "Loan to " + other;
                icon = R.drawable.ic_call_received;
                break;
            case Txn.REPAY:
                if (done) title = borrower ? "Paid back " + other : "Received from " + other;
                else title = borrower ? "Repayment to " + other : "Repayment from " + other;
                icon = R.drawable.ic_payments;
                break;
            default:
                title = borrower ? "Debt to " + other + " cancelled" : other + "'s debt cancelled";
                icon = R.drawable.ic_cycle;
        }
        String subtitle = Ui.dateTime(this, t.createdAt) + (TextUtils.isEmpty(t.note) ? "" : " · " + t.note);
        Row r = Row.card(icon, title, subtitle);

        long cash = Txn.ACCEPTED.equals(t.status) ? t.walletEffect(me) : 0;
        if (Txn.CYCLE.equals(t.type)) {
            r.amount(Money.format(t.amount), 0).status(getString(R.string.no_money_moved));
        } else if (Txn.ACCEPTED.equals(t.status)) {
            r.amount(Money.signed(cash), cash >= 0 ? Ui.moneyIn(this) : Ui.moneyOut(this));
        } else {
            r.amount(Money.format(t.amount), 0).status(statusLabel(t.status));
        }
        return solid(r, solidColor(t)).card(getColor(cardColor(t)));
    }

    /** Simple fill per kind of event: borrowed/lent green, repaid blue, pending amber,
     *  declined red, cancelled grey, circular settlement purple. */
    private static int cardColor(Txn t) {
        switch (t.status) {
            case Txn.PENDING:
                return R.color.card_pending;
            case Txn.DECLINED:
                return R.color.card_declined;
            case Txn.CANCELLED:
                return R.color.card_cancelled;
        }
        if (Txn.CYCLE.equals(t.type)) return R.color.card_cycle;
        return Txn.REPAY.equals(t.type) ? R.color.card_repay : R.color.card_borrow;
    }

    /** Strong colour for the icon circle, same hue as {@link #cardColor}. */
    private static int solidColor(Txn t) {
        switch (t.status) {
            case Txn.PENDING:
                return R.color.solid_amber;
            case Txn.DECLINED:
                return R.color.solid_red;
            case Txn.CANCELLED:
                return R.color.solid_grey;
        }
        if (Txn.CYCLE.equals(t.type)) return R.color.solid_purple;
        return Txn.REPAY.equals(t.type) ? R.color.solid_blue : R.color.solid_green;
    }

    private static String statusLabel(String status) {
        switch (status) {
            case Txn.PENDING:
                return "Pending";
            case Txn.DECLINED:
                return "Declined";
            case Txn.CANCELLED:
                return "Cancelled";
            default:
                return status;
        }
    }
}
