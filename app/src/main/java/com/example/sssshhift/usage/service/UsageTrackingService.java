package com.example.sssshhift.usage.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import com.example.sssshhift.usage.data.UsageLog;
import com.example.sssshhift.usage.repository.UsageRepository;
import java.util.Date;

public class UsageTrackingService extends Service {
    private UsageRepository repository;
    private Date startTime;
    private String currentMode;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new UsageRepository(getApplication());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("mode")) {
            String newMode = intent.getStringExtra("mode");
            
            // Log previous session if exists
            if (startTime != null && currentMode != null) {
                Date endTime = new Date();
                long durationMinutes = (endTime.getTime() - startTime.getTime()) / (60 * 1000);
                
                UsageLog log = new UsageLog(currentMode, startTime, endTime, durationMinutes);
                repository.insert(log);
            }
            
            // Start new session
            startTime = new Date();
            currentMode = newMode;
        }
        
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Log final session
        if (startTime != null && currentMode != null) {
            Date endTime = new Date();
            long durationMinutes = (endTime.getTime() - startTime.getTime()) / (60 * 1000);
            
            UsageLog log = new UsageLog(currentMode, startTime, endTime, durationMinutes);
            repository.insert(log);
        }
    }
} 