package com.example.sssshhift.usage.repository;

import android.app.Application;
import androidx.lifecycle.LiveData;
import com.example.sssshhift.usage.data.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UsageRepository {
    private final UsageLogDao usageLogDao;
    private final ExecutorService executorService;

    public UsageRepository(Application application) {
        UsageDatabase db = UsageDatabase.getDatabase(application);
        usageLogDao = db.usageLogDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void insert(UsageLog log) {
        executorService.execute(() -> usageLogDao.insert(log));
    }

    public LiveData<List<UsageLog>> getAllLogs() {
        return usageLogDao.getAllLogs();
    }

    public LiveData<UsageModeCount> getMostUsedMode() {
        return usageLogDao.getMostUsedMode();
    }

    public LiveData<PeakHourCount> getPeakHour() {
        return usageLogDao.getPeakHour();
    }

    public LiveData<Long> getTotalSilentDuration() {
        return usageLogDao.getTotalSilentDuration();
    }
} 