package com.example.hishabkoi.loans;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hishabkoi.R;
import com.example.hishabkoi.cloud.Cloud;
import com.example.hishabkoi.cloud.Debt;
import com.example.hishabkoi.cloud.Txn;
import com.example.hishabkoi.util.Bg;
import com.example.hishabkoi.util.Money;
import com.example.hishabkoi.util.Session;
import com.example.hishabkoi.util.Ui;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.Locale;

/** Send a borrow request, or tell someone you paid them back. Both need the other person's OK. */
public class RequestActivity extends AppCompatActivity {
    private static final String EXTRA_TYPE = "type";

    private String type;
    private String me;
    private TextInputLayout tilUsername, tilAmount;
    private EditText etUsername, etAmount, etNote;
    private View progress;
    private Button btnSend;

    public static void start(Context c, String type) {
        c.startActivity(new Intent(c, RequestActivity.class).putExtra(EXTRA_TYPE, type));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request);
        type = Txn.REPAY.equals(getIntent().getStringExtra(EXTRA_TYPE)) ? Txn.REPAY : Txn.BORROW;
        me = Session.uid();
        boolean repay = Txn.REPAY.equals(type);
        Ui.toolbar(this, getString(repay ? R.string.tile_repay : R.string.tile_borrow));
        Ui.section(findViewById(R.id.form_header), repay ? R.drawable.ic_payments : R.drawable.ic_call_received,
                getString(R.string.section_shared),
                getString(repay ? R.string.repay_explain : R.string.borrow_explain), getColor(R.color.tile_synced_icon));

        tilUsername = findViewById(R.id.til_username);
        tilAmount = findViewById(R.id.til_amount);
        etUsername = findViewById(R.id.et_username);
        etAmount = findViewById(R.id.et_amount);
        etNote = findViewById(R.id.et_note);
        progress = findViewById(R.id.progress);
        btnSend = findViewById(R.id.btn_send);
        btnSend.setOnClickListener(v -> send());
        loadPeople();
    }

    /** Quick-pick chips for people I already have a balance with. For repayments, prefills what I owe. */
    private void loadPeople() {
        Bg.run(this, () -> Cloud.debtsOf(me), debts -> showPeople(debts));
    }

    private void showPeople(List<Debt> debts) {
        ChipGroup group = findViewById(R.id.chips_people);
        boolean repay = Txn.REPAY.equals(type);
        for (Debt d : debts) {
            long owed = d.owedBy(me);
            if (repay && owed <= 0) continue;
            String other = d.name(d.other(me));
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_people_chip, group, false);
            chip.setText(repay ? "@" + other + " · " + Money.format(owed) : "@" + other);
            chip.setOnClickListener(v -> {
                etUsername.setText(other);
                if (repay) etAmount.setText(Money.plain(owed));
            });
            group.addView(chip);
        }
        findViewById(R.id.tv_people).setVisibility(group.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void send() {
        String other = etUsername.getText().toString().trim().toLowerCase(Locale.US);
        if (other.startsWith("@")) other = other.substring(1);
        long amount = Money.parse(etAmount.getText());
        String myName = Session.username(this);
        tilUsername.setError(other.isEmpty() ? getString(R.string.err_username) : null);
        tilAmount.setError(amount > 0 ? null : getString(R.string.err_amount));
        if (other.isEmpty() || amount <= 0) return;
        if (myName == null) {
            // Username is set up on the home screen; it should always exist by the time we get here.
            Ui.toast(this, getString(R.string.choose_username));
            return;
        }
        if (other.equals(myName)) {
            tilUsername.setError(getString(R.string.err_self));
            return;
        }
        String otherName = other;
        String note = etNote.getText().toString().trim();
        setBusy(true);
        Bg.run(this, () -> {
            String otherUid = Cloud.uidOf(otherName);
            if (otherUid == null) return false;
            Cloud.createRequest(type, me, myName, otherUid, otherName, amount, note);
            return true;
        }, sent -> {
            setBusy(false);
            if (!sent) {
                tilUsername.setError(getString(R.string.err_user_not_found, otherName));
                return;
            }
            Ui.toast(this, getString(R.string.request_sent, otherName));
            finish();
        }, () -> setBusy(false));
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        btnSend.setEnabled(!busy);
    }
}
