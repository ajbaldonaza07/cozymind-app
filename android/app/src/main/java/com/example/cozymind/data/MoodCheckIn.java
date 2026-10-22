package com.example.cozymind.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "mood_trackers",
        foreignKeys = @ForeignKey(
                entity = User.class,
                parentColumns = "id",
                childColumns = "user_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = @Index(value = {"user_id"})
)
public class MoodCheckIn {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "user_id")
    public long userId;

    @ColumnInfo(name = "datetime_mood")
    public long datetimeMood;

    @ColumnInfo(name = "severity")
    public int severity;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public MoodCheckIn(long userId, long datetimeMood, int severity, long createdAt) {
        this.userId = userId;
        this.datetimeMood = datetimeMood;
        this.severity = severity;
        this.createdAt = createdAt;
    }
}
