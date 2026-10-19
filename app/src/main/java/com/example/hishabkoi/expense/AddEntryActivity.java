package com.example.hishabkoi.expense;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hishabkoi.R;
import com.example.hishabkoi.local.AppDatabase;
import com.example.hishabkoi.local.Category;
import com.example.hishabkoi.local.Entry;
import com.example.hishabkoi.util.Bg;
import com.example.hishabkoi.util.Money;
import com.example.hishabkoi.util.Session;
import com.example.hishabkoi.util.Ui;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Calendar;
import java.util.TimeZone;

/** Add a personal income or expense. Saved locally only. */
public class AddEntryActivity extends AppCompatActivity {
    private static final String EXTRA_TYPE = "type";

    private MaterialButtonToggleGroup toggle;
    private ChipGroup chips;
    private TextInputLayout tilAmount;
    private EditText etAmount, etNote;
    private MaterialButton btnDate;
    private long timestamp = System.currentTimeMillis();

    public static void start(Context c, String type) {
        c.startActivity(new Intent(c, AddEntryActivity.class).putExtra(EXTRA_TYPE, type));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_entry);
        Ui.toolbar(this, getString(R.string.tile_add_expense));
        toggle = findViewById(R.id.toggle_type);
        chips = findViewById(R.id.chips_category);
        tilAmount = findViewById(R.id.til_amount);
        etAmount = findViewById(R.id.et_amount);
        etNote = findViewById(R.id.et_note);
        btnDate = findViewById(R.id.btn_date);

        toggle.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) onTypeChanged();
        });
        toggle.check(Entry.INCOME.equals(getIntent().getStringExtra(EXTRA_TYPE))
                ? R.id.btn_type_income : R.id.btn_type_expense);

        btnDate.setOnClickListener(v -> pickDate());
        updateDateLabel();
        findViewById(R.id.btn_save).setOnClickListener(v -> save());
    }

    private String type() {
        return toggle.getCheckedButtonId() == R.id.btn_type_income ? Entry.INCOME : Entry.EXPENSE;
    }

    private void onTypeChanged() {
        String type = type();
        boolean income = Entry.INCOME.equals(type);
        String title = getString(income ? R.string.tile_add_income : R.string.tile_add_expense);
        Ui.toolbar(this, title);
        Ui.section(findViewById(R.id.form_header), income ? R.drawable.ic_add_circle : R.drawable.ic_remove_circle,
                getString(R.string.section_personal), getString(R.string.section_personal_sub),
                getColor(R.color.tile_local_icon));
        Bg.run(this, () -> AppDatabase.get(this).categories().ofType(type), list -> {
            chips.removeAllViews();
            for (Category c : list) {
                Chip chip = (Chip) getLayoutInflater().inflate(
                        R.layout.item_choice_chip, chips, false);
                chip.setText(c.name);
                chips.addView(chip);
            }
            if (chips.getChildCount() > 0) chips.check(chips.getChildAt(0).getId());
        });
    }

    private void pickDate() {
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setSelection(utcMidnightOf(timestamp))
                .build();
        picker.addOnPositiveButtonClickListener(utcMillis -> {
            // Picker returns UTC midnight; keep the current local time of day on the chosen date.
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc.setTimeInMillis(utcMillis);
            Calendar local = Calendar.getInstance();
            local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
            timestamp = local.getTimeInMillis();
            updateDateLabel();
        });
        picker.show(getSupportFragmentManager(), "date");
    }

    private static long utcMidnightOf(long localMillis) {
        Calendar local = Calendar.getInstance();
        local.setTimeInMillis(localMillis);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }

    private void updateDateLabel() {
        btnDate.setText(getString(R.string.date) + ": " + Ui.date(timestamp));
    }

    private void save() {
        long amount = Money.parse(etAmount.getText());
        tilAmount.setError(amount > 0 ? null : getString(R.string.err_amount));
        Chip chip = chips.findViewById(chips.getCheckedChipId());
        if (amount <= 0) return;
        if (chip == null) {
            Ui.toast(this, getString(R.string.err_category));
            return;
        }
        Entry e = new Entry();
        e.ownerUid = Session.uid();
        e.type = type();
        e.amountCents = amount;
        e.category = chip.getText().toString();
        e.note = etNote.getText().toString().trim();
        e.timestamp = timestamp;
        Bg.run(this, () -> AppDatabase.get(this).entries().insert(e), id -> {
            Ui.toast(this, getString(R.string.saved));
            finish();
        });
    }
}
