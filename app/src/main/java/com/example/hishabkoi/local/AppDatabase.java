package com.example.hishabkoi.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Entry.class, Category.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static final String[] EXPENSE_CATEGORIES = {
            "Food", "Transport", "Shopping", "Bills", "Health", "Education", "Entertainment", "Other"};
    private static final String[] INCOME_CATEGORIES = {"Salary", "Business", "Gift", "Other"};

    private static volatile AppDatabase instance;

    public abstract EntryDao entries();

    public abstract CategoryDao categories();

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "hishabkoi.db")
                            .addCallback(new Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    seed(db, EXPENSE_CATEGORIES, Entry.EXPENSE);
                                    seed(db, INCOME_CATEGORIES, Entry.INCOME);
                                }
                            })
                            .build();
                }
            }
        }
        return instance;
    }

    private static void seed(SupportSQLiteDatabase db, String[] names, String type) {
        for (String name : names) {
            db.execSQL("INSERT OR IGNORE INTO categories (name, type) VALUES (?, ?)",
                    new Object[]{name, type});
        }
    }
}
