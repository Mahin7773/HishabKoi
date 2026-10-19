package com.example.hishabkoi.loans;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.example.hishabkoi.R;
import com.example.hishabkoi.cloud.Cloud;
import com.example.hishabkoi.cloud.Cycle;
import com.example.hishabkoi.cloud.Proposal;
import com.example.hishabkoi.ui.ListActivity;
import com.example.hishabkoi.ui.Row;
import com.example.hishabkoi.util.Bg;
import com.example.hishabkoi.util.Money;
import com.example.hishabkoi.util.Session;
import com.example.hishabkoi.util.Ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Circular settlement. Shows open proposals (agree / reject) and loops found by scanning
 * (propose). When the last member agrees, the loop's smallest debt is cancelled on every edge.
 * Each person sees who is in the loop but only their own debts in it, never debts between others.
 */
public class CircularActivity extends ListActivity {
    private final String me = Session.uid();
    private Button btnScan;

    private static class State {
        List<Proposal> proposals;
        List<Cycle> loops = new ArrayList<>();
    }

    @Override
    protected int titleRes() {
        return R.string.tile_circular;
    }

    @Override
    protected int emptyRes() {
        return R.string.no_loops;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View header = addHeader(R.layout.view_circular_header);
        btnScan = header.findViewById(R.id.btn_scan);
        btnScan.setOnClickListener(v -> load());
    }

    @Override
    protected void load() {
        setLoading(true);
        btnScan.setEnabled(false);
        btnScan.setText(R.string.scanning);
        Bg.run(this, () -> {
            State s = new State();
            s.proposals = Cloud.openProposals(me);
            Set<String> proposed = new HashSet<>();
            for (Proposal p : s.proposals) proposed.add(p.id);
            for (Cycle c : Cloud.findCycles(me)) {
                if (!proposed.contains(c.key())) s.loops.add(c);
            }
            Session.setLastCycleScan(this, System.currentTimeMillis());
            return s;
        }, this::render, this::resetScanButton);
    }

    private void render(State s) {
        resetScanButton();
        List<Row> rows = new ArrayList<>();
        if (!s.proposals.isEmpty()) {
            rows.add(Row.header(getString(R.string.proposals)));
            for (Proposal p : s.proposals) rows.add(proposalRow(p));
        }
        if (!s.loops.isEmpty()) {
            rows.add(Row.header(getString(R.string.loops_found)));
            for (Cycle c : s.loops) rows.add(loopRow(c));
        }
        show(rows);
    }

    private void resetScanButton() {
        setLoading(false);
        btnScan.setEnabled(true);
        btnScan.setText(R.string.scan_loops);
    }

    private Row proposalRow(Proposal p) {
        Cycle c = p.asCycle();
        Row r = solid(Row.card(R.drawable.ic_cycle, c.withOthers(me), c.describeFor(me)), R.color.solid_purple)
                .amount("−" + Money.format(c.minAmount()), 0)
                .status(p.approvals.size() + "/" + p.members.size() + " agreed · by "
                        + (me.equals(p.createdBy) ? "you" : c.name(p.createdBy)));
        if (!p.approvedBy(me)) {
            r.primary(getString(R.string.agree), () -> approve(p.id, null))
                    .secondary(getString(R.string.reject), () -> reject(p.id));
        } else {
            r.secondary(getString(R.string.reject), () -> reject(p.id));
        }
        return r;
    }

    private Row loopRow(Cycle c) {
        return solid(Row.card(R.drawable.ic_cycle, c.withOthers(me), c.describeFor(me)), R.color.solid_purple)
                .amount("−" + Money.format(c.minAmount()), 0)
                .primary(getString(R.string.propose), () -> approve(c.key(), c));
    }

    private void approve(String proposalId, Cycle fresh) {
        setLoading(true);
        Bg.run(this, () -> Cloud.approve(proposalId, me, fresh), outcome -> {
            switch (outcome) {
                case Cloud.OUTCOME_APPLIED:
                    Ui.toast(this, getString(R.string.proposal_applied));
                    break;
                case Cloud.OUTCOME_STALE:
                    Ui.toast(this, getString(R.string.proposal_stale));
                    break;
                default:
                    Ui.toast(this, getString(fresh != null ? R.string.proposal_sent : R.string.agreed_waiting));
            }
            load();
        }, () -> setLoading(false));
    }

    private void reject(String proposalId) {
        setLoading(true);
        Bg.run(this, () -> {
            Cloud.reject(proposalId, me);
            return true;
        }, ok -> load(), () -> setLoading(false));
    }
}
