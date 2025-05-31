package com.example.sssshhift.usage.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.Date;

@Entity(tableName = "usage_logs")
public class UsageLog {
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private String mode;
    private Date startTime;
    private Date endTime;
    private long durationMinutes;

    public UsageLog(String mode, Date startTime, Date endTime, long durationMinutes) {
        this.mode = mode;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMinutes = durationMinutes;
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    
    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }
    
    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }
    
    public long getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(long durationMinutes) { this.durationMinutes = durationMinutes; }
} 