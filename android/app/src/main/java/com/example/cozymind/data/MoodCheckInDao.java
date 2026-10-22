package com.example.cozymind.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MoodCheckInDao {
    @Insert
    long insert(MoodCheckIn checkIn);

    @Query("SELECT * FROM mood_trackers WHERE user_id = :userId ORDER BY datetime_mood DESC LIMIT :limit")
    List<MoodCheckIn> getLatestForUser(long userId, int limit);

    @Query("SELECT (datetime_mood / 21600000) * 21600000 AS period_start, "
            + "AVG(severity) AS average_severity, COUNT(*) AS entry_count "
            + "FROM mood_trackers WHERE user_id = :userId "
            + "GROUP BY (datetime_mood / 21600000) HAVING COUNT(*) >= 3 "
            + "ORDER BY period_start DESC")
    List<MoodStatistic> getSixHourStatistics(long userId);
}
