package com.example.cozymind.data;

import androidx.room.ColumnInfo;

public class MoodStatistic {
    @ColumnInfo(name = "period_start")
    public long periodStart;

    @ColumnInfo(name = "average_severity")
    public double averageSeverity;

    @ColumnInfo(name = "entry_count")
    public int entryCount;
}
