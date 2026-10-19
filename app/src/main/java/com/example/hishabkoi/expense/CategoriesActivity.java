package com.example.hishabkoi.expense;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import com.example.hishabkoi.R;
import com.example.hishabkoi.local.AppDatabase;
import com.example.hishabkoi.local.Category;
import com.example.hishabkoi.local.CategoryDao;
import com.example.hishabkoi.local.Entry;
import com.example.hishabkoi.ui.ListActivity;
import com.example.hishabkoi.ui.Row;
import com.example.hishabkoi.util.Bg;
import com.example.hishabkoi.util.Ui;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/** Add or remove local income/expense categories. Existing entries keep their category name. */
public class CategoriesActivity extends ListActivity {
    private MaterialButtonToggleGroup toggle;
    private TextInputLayout tilName;
    private EditText etName;

    @Override
    protected int titleRes() {
        return R.string.tile_categories;
    }

    @Override
    protected int emptyRes() {
        return R.string.no_categories;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View header = addHeader(R.layout.view_category_header);
        toggle = header.findViewById(R.id.toggle_type);
        tilName = header.findViewById(R.id.til_name);
        etName = header.findViewById(R.id.et_name);
        toggle.check(R.id.btn_type_expense);
        toggle.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked) load();
        });
        header.findViewById(R.id.btn_add).setOnClickListener(v -> add());
    }

    private String type() {
        return toggle.getCheckedButtonId() == R.id.btn_type_income ? Entry.INCOME : Entry.EXPENSE;
    }

    @Override
    protected void load() {
        CategoryDao dao = AppDatabase.get(this).categories();
        Bg.run(this, () -> dao.ofType(type()), list -> {
            List<Row> rows = new ArrayList<>();
            for (Category c : list) {
                rows.add(solid(Row.card(R.drawable.ic_category, c.name, null), R.color.solid_blue)
                        .secondary(getString(R.string.delete), () -> Bg.run(this, () -> {
                            dao.delete(c);
                            return true;
                        }, ok -> load())));
            }
            show(rows);
        });
    }

    private void add() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) return;
        Category c = new Category();
        c.name = name;
        c.type = type();
        Bg.run(this, () -> AppDatabase.get(this).categories().insert(c), id -> {
            if (id == -1) {
                tilName.setError(getString(R.string.category_exists));
            } else {
                tilName.setError(null);
                etName.setText("");
                load();
            }
        });
    }
}
