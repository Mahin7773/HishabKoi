package com.example.hishabkoi.local;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface EntryDao {
    @Insert
    long insert(Entry entry);

    @Delete
    void delete(Entry entry);

    @Query("SELECT * FROM entries WHERE ownerUid = :uid AND timestamp >= :from AND timestamp < :to "
            + "ORDER BY timestamp DESC")
    List<Entry> between(String uid, long from, long to);

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM entries WHERE ownerUid = :uid AND type = :type "
            + "AND timestamp >= :from AND timestamp < :to")
    long sum(String uid, String type, long from, long to);

    /** All-time income minus expenses. */
    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END), 0) "
            + "FROM entries WHERE ownerUid = :uid")
    long net(String uid);

    @Query("SELECT category AS name, SUM(amountCents) AS total FROM entries WHERE ownerUid = :uid "
            + "AND type = :type AND timestamp >= :from AND timestamp < :to "
            + "GROUP BY category ORDER BY total DESC")
    List<CategoryTotal> byCategory(String uid, String type, long from, long to);
}
