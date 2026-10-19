package com.example.hishabkoi.local;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CategoryDao {
    /** Returns -1 when a category with the same name and type already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(Category category);

    @Delete
    void delete(Category category);

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY name COLLATE NOCASE")
    List<Category> ofType(String type);
}
