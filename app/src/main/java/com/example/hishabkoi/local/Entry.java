package com.example.hishabkoi.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A personal income or expense. Lives only on this device (Room), never in Firebase. */
@Entity(tableName = "entries", indices = {@Index({"ownerUid", "timestamp"})})
public class Entry {
    public static final String INCOME = "INCOME";
    public static final String EXPENSE = "EXPENSE";

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Firebase uid of the account that created it, so two accounts on one phone stay separate. */
    @NonNull
    public String ownerUid = "";

    @NonNull
    public String type = EXPENSE;

    public long amountCents;

    @NonNull
    public String category = "";

    public String note;

    public long timestamp;

    public boolean isIncome() {
        return INCOME.equals(type);
    }
}
