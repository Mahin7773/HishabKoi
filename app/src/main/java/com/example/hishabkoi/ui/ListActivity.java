package com.example.hishabkoi.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hishabkoi.R;
import com.example.hishabkoi.util.Ui;

import java.util.List;

/** Base for the list screens: toolbar, optional header, progress bar, list and empty message. */
public abstract class ListActivity extends AppCompatActivity {
    protected final RowAdapter adapter = new RowAdapter();
    private View progress;
    private TextView empty;
    private RecyclerView list;

    @StringRes
    protected abstract int titleRes();

    @StringRes
    protected abstract int emptyRes();

    /** Reload data and call {@link #show}. */
    protected abstract void load();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        Ui.toolbar(this, getString(titleRes()));
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty);
        empty.setText(emptyRes());
        list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        load();
    }

    /** Row icon on a solid circle in one of the palette colours. */
    protected Row solid(Row row, @ColorRes int color) {
        return row.solid(getColor(color), getColor(R.color.on_solid));
    }

    protected View addHeader(@LayoutRes int layout) {
        LinearLayout header = findViewById(R.id.header);
        return getLayoutInflater().inflate(layout, header, true);
    }

    protected void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    protected void show(List<Row> rows) {
        setLoading(false);
        adapter.submit(rows);
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
