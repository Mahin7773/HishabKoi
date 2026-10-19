package com.example.hishabkoi.ui;

import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hishabkoi.R;
import com.example.hishabkoi.util.Ui;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/** Generic list adapter used by every list screen. */
public class RowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROW = 1;

    private final List<Row> rows = new ArrayList<>();

    public void submit(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).header ? TYPE_HEADER : TYPE_ROW;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new RecyclerView.ViewHolder(inf.inflate(R.layout.item_header, parent, false)) {};
        }
        return new Holder(inf.inflate(R.layout.item_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof Holder) ((Holder) holder).bind(row);
        else ((TextView) holder.itemView).setText(row.title);
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title, subtitle, status, amount, oldAmount;
        final View actions;
        final Button action1, action2, inlineAction;

        Holder(View v) {
            super(v);
            icon = v.findViewById(R.id.row_icon);
            title = v.findViewById(R.id.row_title);
            subtitle = v.findViewById(R.id.row_subtitle);
            status = v.findViewById(R.id.row_status);
            amount = v.findViewById(R.id.row_amount);
            oldAmount = v.findViewById(R.id.row_old_amount);
            actions = v.findViewById(R.id.row_actions);
            action1 = v.findViewById(R.id.row_action1);
            action2 = v.findViewById(R.id.row_action2);
            inlineAction = v.findViewById(R.id.row_inline_action);
            oldAmount.setPaintFlags(oldAmount.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }

        void bind(Row row) {
            icon.setImageResource(row.icon);
            int tint = row.iconTint != 0 ? row.iconTint
                    : Ui.attr(itemView.getContext(), com.google.android.material.R.attr.colorOnSecondaryContainer);
            icon.setImageTintList(ColorStateList.valueOf(tint));
            icon.setBackgroundTintList(row.iconBg != 0 ? ColorStateList.valueOf(row.iconBg) : null);
            title.setText(row.title);
            show(subtitle, row.subtitle);
            show(status, row.status);
            show(amount, row.amount);
            show(oldAmount, row.oldAmount);
            ((MaterialCardView) itemView).setCardBackgroundColor(row.cardColor != 0 ? row.cardColor
                    : itemView.getContext().getColor(R.color.glass_fill));
            amount.setTextColor(row.amountColor != 0 ? row.amountColor
                    : Ui.attr(itemView.getContext(), com.google.android.material.R.attr.colorOnSurface));
            // A card with only a secondary action shows it inline, so it doesn't need a second row.
            boolean inline = row.action1 == null && row.action2 != null;
            bindAction(action1, row.action1, row.onAction1);
            bindAction(action2, inline ? null : row.action2, row.onAction2);
            bindAction(inlineAction, inline ? row.action2 : null, row.onAction2);
            // Trash icon only for actual deletes; Cancel / Reject keep a plain pill.
            boolean isDelete = inline && itemView.getContext().getString(R.string.delete).contentEquals(row.action2);
            ((com.google.android.material.button.MaterialButton) inlineAction)
                    .setIconResource(isDelete ? R.drawable.ic_delete : 0);
            actions.setVisibility(row.action1 == null ? View.GONE : View.VISIBLE);
            if (row.onLongClick != null) {
                itemView.setOnLongClickListener(v -> {
                    row.onLongClick.run();
                    return true;
                });
            } else {
                itemView.setOnLongClickListener(null);
                itemView.setLongClickable(false);
            }
        }

        private static void show(TextView tv, CharSequence text) {
            tv.setText(text);
            tv.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        }

        private static void bindAction(Button b, CharSequence label, Runnable action) {
            show(b, label);
            b.setOnClickListener(action == null ? null : v -> action.run());
        }
    }
}
