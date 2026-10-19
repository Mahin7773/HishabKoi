package com.example.hishabkoi;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.example.hishabkoi.auth.LoginActivity;
import com.example.hishabkoi.auth.SignupActivity;
import com.example.hishabkoi.cloud.Cloud;
import com.example.hishabkoi.cloud.Cycle;
import com.example.hishabkoi.cloud.Debt;
import com.example.hishabkoi.cloud.Proposal;
import com.example.hishabkoi.cloud.Txn;
import com.example.hishabkoi.expense.AddEntryActivity;
import com.example.hishabkoi.expense.CategoriesActivity;
import com.example.hishabkoi.expense.EntriesActivity;
import com.example.hishabkoi.expense.ReportsActivity;
import com.example.hishabkoi.loans.BalancesActivity;
import com.example.hishabkoi.loans.CircularActivity;
import com.example.hishabkoi.loans.HistoryActivity;
import com.example.hishabkoi.loans.RequestActivity;
import com.example.hishabkoi.loans.RequestsActivity;
import com.example.hishabkoi.local.AppDatabase;
import com.example.hishabkoi.local.Entry;
import com.example.hishabkoi.local.EntryDao;
import com.example.hishabkoi.util.Bg;
import com.example.hishabkoi.util.Money;
import com.example.hishabkoi.util.Months;
import com.example.hishabkoi.util.Notifier;
import com.example.hishabkoi.util.Session;
import com.example.hishabkoi.util.Ui;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Home: balance summary plus a grid with every feature. Also runs the on-open sync checks. */
public class MainActivity extends AppCompatActivity {
    /** Loop detection costs a few Firestore reads, so it runs at most this often. */
    private static final long CYCLE_SCAN_INTERVAL_MS = 10 * 60 * 1000;

    private TextView tvUser, tvWallet;
    private TextView statIncome, statExpense, statOwe, statOwed;
    private TextView badgeRequests, badgeCircular;
    private String me;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});

    /** Everything the summary card needs from Firestore, gathered in one background call. */
    private static class CloudSummary {
        long owe, owed, walletLoans;
        int incomingRequests, proposalsForMe;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Session.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        me = Session.uid();
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_refresh) {
                Session.setLastCycleScan(this, 0);
                refresh();
            } else if (item.getItemId() == R.id.action_logout) {
                confirmLogout();
            }
            return true;
        });

        tvUser = findViewById(R.id.tv_user);
        tvWallet = findViewById(R.id.tv_wallet);
        statIncome = stat(R.id.stat_income, R.drawable.ic_arrow_upward, R.string.this_month_income);
        statExpense = stat(R.id.stat_expense, R.drawable.ic_arrow_downward, R.string.this_month_expense);
        statOwe = stat(R.id.stat_owe, R.drawable.ic_payments, R.string.you_owe);
        statOwed = stat(R.id.stat_owed, R.drawable.ic_group, R.string.owed_to_you);

        section(R.id.section_personal, R.drawable.ic_smartphone, R.string.section_personal,
                R.string.section_personal_sub, R.color.tile_local_icon);
        section(R.id.section_shared, R.drawable.ic_cloud, R.string.section_shared,
                R.string.section_shared_sub, R.color.tile_synced_icon);

        GridLayout personal = findViewById(R.id.grid_personal);
        tile(personal, R.drawable.ic_remove_circle, R.string.tile_add_expense,
                () -> AddEntryActivity.start(this, Entry.EXPENSE));
        tile(personal, R.drawable.ic_add_circle, R.string.tile_add_income,
                () -> AddEntryActivity.start(this, Entry.INCOME));
        tile(personal, R.drawable.ic_receipt_long, R.string.tile_transactions, () -> open(EntriesActivity.class));
        tile(personal, R.drawable.ic_bar_chart, R.string.tile_reports, () -> open(ReportsActivity.class));
        tile(personal, R.drawable.ic_category, R.string.tile_categories, () -> open(CategoriesActivity.class));

        GridLayout shared = findViewById(R.id.grid_shared);
        tile(shared, R.drawable.ic_call_received, R.string.tile_borrow,
                () -> RequestActivity.start(this, Txn.BORROW));
        tile(shared, R.drawable.ic_payments, R.string.tile_repay,
                () -> RequestActivity.start(this, Txn.REPAY));
        badgeRequests = tile(shared, R.drawable.ic_inbox, R.string.tile_requests, () -> open(RequestsActivity.class));
        tile(shared, R.drawable.ic_account_balance_wallet, R.string.tile_balances, () -> open(BalancesActivity.class));
        tile(shared, R.drawable.ic_history, R.string.tile_history, () -> open(HistoryActivity.class));
        findViewById(R.id.card_circular).setOnClickListener(v -> open(CircularActivity.class));
        badgeCircular = findViewById(R.id.circular_badge);

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
        ensureUsername();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (me != null) refresh();
    }

    private void refresh() {
        String username = Session.username(this);
        tvUser.setText(username == null ? "" : getString(R.string.hello_user, username));
        loadSummary();
        maybeScanForLoops();
    }

    private void loadSummary() {
        EntryDao dao = AppDatabase.get(this).entries();
        long from = Months.current(), to = Months.add(from, 1);
        Bg.run(this, () -> new long[]{
                dao.sum(me, Entry.INCOME, from, to), dao.sum(me, Entry.EXPENSE, from, to), dao.net(me)
        }, local -> {
            statIncome.setText(Money.number(local[0]));
            statExpense.setText(Money.number(local[1]));
            // Last known loan figures first, so the card never flashes ৳0 while Firestore answers.
            long[] last = Session.cachedSummary(this);
            showLoans(local[2], last[0], last[1], last[2], (int) last[3], (int) last[4]);
            Bg.run(this, this::loadCloudSummary, cloud -> {
                Session.cacheSummary(this, cloud.owe, cloud.owed, cloud.walletLoans,
                        cloud.incomingRequests, cloud.proposalsForMe);
                showLoans(local[2], cloud.owe, cloud.owed, cloud.walletLoans,
                        cloud.incomingRequests, cloud.proposalsForMe);
            });
        });
    }

    private void showLoans(long localNet, long owe, long owed, long walletLoans, int requests, int proposals) {
        tvWallet.setText(Money.format(localNet + walletLoans));
        statOwe.setText(Money.number(owe));
        statOwed.setText(Money.number(owed));
        setBadge(badgeRequests, requests);
        setBadge(badgeCircular, proposals);
    }

    /** Runs in the background. Also posts notifications for anything new. */
    private CloudSummary loadCloudSummary() throws Exception {
        CloudSummary s = new CloudSummary();
        for (Debt d : Cloud.debtsOf(me)) {
            long owed = d.owedBy(me);
            if (owed > 0) s.owe += owed;
            else s.owed -= owed;
        }
        long answersSince = Session.answersCheckedAt(this);
        long checkedAt = System.currentTimeMillis();
        for (Txn t : Cloud.history(me)) {
            s.walletLoans += t.walletEffect(me);
            if (t.isPending() && me.equals(t.respondTo)) {
                s.incomingRequests++;
                String what = Txn.BORROW.equals(t.type)
                        ? "@" + t.borrowerName + " wants to borrow " + Money.format(t.amount)
                        : "@" + t.borrowerName + " paid you back " + Money.format(t.amount);
                Notifier.notifyOnce(this, "txn_" + t.id, getString(R.string.tile_requests), what,
                        RequestsActivity.class);
            } else if (me.equals(t.requestedBy) && !Txn.CYCLE.equals(t.type)
                    && (Txn.ACCEPTED.equals(t.status) || Txn.DECLINED.equals(t.status))
                    && t.respondedAt > answersSince) {
                // Tell the requester how their request was answered.
                boolean accepted = Txn.ACCEPTED.equals(t.status);
                String what = "@" + t.lenderName + (accepted ? " accepted " : " declined ")
                        + Money.format(t.amount) + (Txn.BORROW.equals(t.type) ? " loan" : " repayment");
                Notifier.notifyOnce(this, "answer_" + t.id, getString(R.string.tile_history), what,
                        HistoryActivity.class);
            }
        }
        Session.setAnswersCheckedAt(this, checkedAt);
        for (Proposal p : Cloud.openProposals(me)) {
            if (p.approvedBy(me)) continue;
            s.proposalsForMe++;
            Notifier.notifyOnce(this, "proposal_" + p.id + "_" + p.createdAt, getString(R.string.tile_circular),
                    "Settle a loop with " + p.asCycle().others(me) + "?", CircularActivity.class);
        }
        return s;
    }

    /** Looks for debt loops through me, at most every few minutes, and notifies about new ones. */
    private void maybeScanForLoops() {
        long now = System.currentTimeMillis();
        if (now - Session.lastCycleScan(this) < CYCLE_SCAN_INTERVAL_MS) return;
        Session.setLastCycleScan(this, now);
        Bg.run(() -> {
            try {
                Set<String> proposed = new HashSet<>();
                for (Proposal p : Cloud.openProposals(me)) proposed.add(p.id);
                for (Cycle c : Cloud.findCycles(me)) {
                    if (proposed.contains(c.key())) continue;
                    Notifier.notifyOnce(this, "cycle_" + c.key() + "_" + c.minAmount(),
                            getString(R.string.tile_circular),
                            "Loop found with " + c.others(me) + " · −" + Money.format(c.minAmount()),
                            CircularActivity.class);
                }
            } catch (Exception ignored) {
                // Best effort; the user can always scan manually from the Circular settle screen.
            }
        });
    }

    /** Accounts created before the profile was written (or if it failed) pick a username here. */
    private void ensureUsername() {
        if (Session.username(this) != null) return;
        Bg.run(this, () -> {
            String name = Cloud.usernameOf(me);
            return name == null ? "" : name;
        }, name -> {
            if (!name.isEmpty()) {
                Session.setUsername(this, name);
                tvUser.setText(getString(R.string.hello_user, name));
            } else {
                askForUsername();
            }
        });
    }

    private void askForUsername() {
        View view = getLayoutInflater().inflate(R.layout.dialog_username, null);
        TextInputLayout til = view.findViewById(R.id.til_username);
        EditText et = view.findViewById(R.id.et_username);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.choose_username)
                .setView(view)
                .setCancelable(false)
                .setPositiveButton(R.string.save, null)
                .show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = et.getText().toString().trim().toLowerCase(Locale.US);
            if (!name.matches(SignupActivity.USERNAME_PATTERN)) {
                til.setError(getString(R.string.err_username));
                return;
            }
            String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            Bg.run(this, () -> {
                Cloud.reserveUsername(me, name, email);
                return name;
            }, saved -> {
                Session.setUsername(this, saved);
                tvUser.setText(getString(R.string.hello_user, saved));
                dialog.dismiss();
            });
        });
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.log_out)
                .setMessage(R.string.log_out_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.log_out, (d, w) -> {
                    FirebaseAuth.getInstance().signOut();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                })
                .show();
    }

    private TextView stat(int includeId, @DrawableRes int icon, @StringRes int label) {
        View v = findViewById(includeId);
        ((ImageView) v.findViewById(R.id.stat_icon)).setImageResource(icon);
        ((TextView) v.findViewById(R.id.stat_label)).setText(label);
        TextView value = v.findViewById(R.id.stat_value);
        value.setText(Money.number(0));
        return value;
    }

    /** Fills a section panel's header: icon on a solid circle, coloured title, explanation. */
    private void section(int headerId, @DrawableRes int icon, @StringRes int title, @StringRes int sub,
                         int colorRes) {
        Ui.section(findViewById(headerId), icon, getString(title), getString(sub), getColor(colorRes));
    }

    /**
     * Adds a feature tile to the grid and returns its badge view. Personal tiles get round chips,
     * synced ones rounded squares, so the two sections read as different kinds of thing.
     */
    private TextView tile(GridLayout grid, @DrawableRes int icon, @StringRes int label, Runnable action) {
        boolean local = grid.getId() == R.id.grid_personal;
        View v = getLayoutInflater().inflate(R.layout.item_tile, grid, false);
        ImageView iv = v.findViewById(R.id.tile_icon);
        iv.setImageResource(icon);
        iv.setBackgroundResource(local ? R.drawable.bg_glass_circle : R.drawable.bg_glass_square);
        iv.setImageTintList(ColorStateList.valueOf(getColor(local ? R.color.tile_local_icon : R.color.tile_synced_icon)));
        ((TextView) v.findViewById(R.id.tile_label)).setText(label);
        v.setOnClickListener(x -> action.run());
        grid.addView(v);
        return v.findViewById(R.id.tile_badge);
    }

    private void setBadge(TextView badge, int count) {
        badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        badge.setText(String.valueOf(count));
    }

    private void open(Class<?> screen) {
        startActivity(new Intent(this, screen));
    }
}
