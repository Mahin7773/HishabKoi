package com.example.hishabkoi.loans;

import android.text.TextUtils;

import com.example.hishabkoi.R;
import com.example.hishabkoi.cloud.Cloud;
import com.example.hishabkoi.cloud.Txn;
import com.example.hishabkoi.ui.ListActivity;
import com.example.hishabkoi.ui.Row;
import com.example.hishabkoi.util.Bg;
import com.example.hishabkoi.util.Money;
import com.example.hishabkoi.util.Session;
import com.example.hishabkoi.util.Ui;

import java.util.ArrayList;
import java.util.List;

/** Pending borrow/repay requests: ones I must answer, and ones I sent. */
public class RequestsActivity extends ListActivity {
    private final String me = Session.uid();

    @Override
    protected int titleRes() {
        return R.string.tile_requests;
    }

    @Override
    protected int emptyRes() {
        return R.string.no_requests;
    }

    @Override
    protected void load() {
        setLoading(true);
        Bg.run(this, () -> Cloud.history(me), all -> {
            List<Row> incoming = new ArrayList<>();
            List<Row> outgoing = new ArrayList<>();
            for (Txn t : all) {
                if (!t.isPending()) continue;
                if (me.equals(t.respondTo)) incoming.add(incomingRow(t));
                else outgoing.add(outgoingRow(t));
            }
            List<Row> rows = new ArrayList<>();
            if (!incoming.isEmpty()) {
                rows.add(Row.header(getString(R.string.waiting_for_you)));
                rows.addAll(incoming);
            }
            if (!outgoing.isEmpty()) {
                rows.add(Row.header(getString(R.string.sent_by_you)));
                rows.addAll(outgoing);
            }
            show(rows);
        }, () -> setLoading(false));
    }

    private Row incomingRow(Txn t) {
        boolean borrow = Txn.BORROW.equals(t.type);
        String title = borrow ? "@" + t.borrowerName + " wants to borrow"
                : "@" + t.borrowerName + " paid you back";
        return solid(Row.card(borrow ? R.drawable.ic_call_received : R.drawable.ic_payments, title, subtitle(t)),
                borrow ? R.color.solid_green : R.color.solid_blue)
                .amount(Money.format(t.amount), 0)
                .primary(getString(borrow ? R.string.accept : R.string.confirm), () -> respond(t, true))
                .secondary(getString(R.string.decline), () -> respond(t, false));
    }

    private Row outgoingRow(Txn t) {
        boolean borrow = Txn.BORROW.equals(t.type);
        String title = borrow ? "Loan from @" + t.lenderName : "Repaid @" + t.lenderName;
        return solid(Row.card(borrow ? R.drawable.ic_call_received : R.drawable.ic_payments, title, subtitle(t)),
                R.color.solid_amber)
                .amount(Money.format(t.amount), 0)
                .status("Pending")
                .secondary(getString(R.string.cancel_request), () -> act(() -> Cloud.cancelRequest(t.id),
                        getString(R.string.toast_cancelled, t.lenderName, Money.format(t.amount))));
    }

    private String subtitle(Txn t) {
        return Ui.dateTime(this, t.createdAt) + (TextUtils.isEmpty(t.note) ? "" : "\n“" + t.note + "”");
    }

    private void respond(Txn t, boolean accept) {
        String amount = Money.format(t.amount);
        String message;
        if (Txn.BORROW.equals(t.type)) {
            message = accept ? getString(R.string.toast_loan_accepted, amount, t.borrowerName)
                    : getString(R.string.toast_loan_declined, t.borrowerName, amount);
        } else {
            message = accept ? getString(R.string.toast_repay_confirmed, amount, t.borrowerName)
                    : getString(R.string.toast_repay_declined, t.borrowerName, amount);
        }
        act(() -> Cloud.respond(t.id, me, accept), message);
    }

    private interface CloudCall {
        void run() throws Exception;
    }

    /** Runs the call, then shows {@code doneMessage} and reloads. Errors are toasted by Bg. */
    private void act(CloudCall call, String doneMessage) {
        setLoading(true);
        Bg.run(this, () -> {
            call.run();
            return true;
        }, ok -> {
            Ui.toast(this, doneMessage);
            load();
        }, () -> setLoading(false));
    }
}
