package com.example.sssshhift.usage.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;
import java.util.Date;

@Dao
public interface UsageLogDao {
    @Insert
    void insert(UsageLog log);

    @Query("SELECT * FROM usage_logs ORDER BY startTime DESC")
    LiveData<List<UsageLog>> getAllLogs();

    @Query("SELECT mode, COUNT(*) as count FROM usage_logs GROUP BY mode ORDER BY count DESC LIMIT 1")
    LiveData<UsageModeCount> getMostUsedMode();

    @Query("SELECT strftime('%H', startTime / 1000, 'unixepoch', 'localtime') as hour, " +
           "COUNT(*) as count FROM usage_logs GROUP BY hour ORDER BY count DESC LIMIT 1")
    LiveData<PeakHourCount> getPeakHour();

    @Query("SELECT SUM(durationMinutes) FROM usage_logs WHERE mode = 'SILENT'")
    LiveData<Long> getTotalSilentDuration();
} 