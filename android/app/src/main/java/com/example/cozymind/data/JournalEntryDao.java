package com.example.cozymind.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface JournalEntryDao {
    @Insert
    long insert(JournalEntry entry);

    @Query("SELECT * FROM journals WHERE user_id = :userId ORDER BY created_at DESC LIMIT :limit")
    List<JournalEntry> getLatestForUser(long userId, int limit);
}
