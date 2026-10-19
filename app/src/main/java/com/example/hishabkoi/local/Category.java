package com.example.hishabkoi.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "categories", indices = {@Index(value = {"name", "type"}, unique = true)})
public class Category {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    /** {@link Entry#INCOME} or {@link Entry#EXPENSE}. */
    @NonNull
    public String type = Entry.EXPENSE;
}
