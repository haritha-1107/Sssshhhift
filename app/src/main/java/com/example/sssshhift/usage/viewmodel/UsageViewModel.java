package com.example.sssshhift.usage.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.sssshhift.usage.data.*;
import com.example.sssshhift.usage.repository.UsageRepository;
import java.util.List;

public class UsageViewModel extends AndroidViewModel {
    private final UsageRepository repository;
    private final LiveData<List<UsageLog>> allLogs;
    private final LiveData<UsageModeCount> mostUsedMode;
    private final LiveData<PeakHourCount> peakHour;
    private final LiveData<Long> totalSilentDuration;

    public UsageViewModel(Application application) {
        super(application);
        repository = new UsageRepository(application);
        allLogs = repository.getAllLogs();
        mostUsedMode = repository.getMostUsedMode();
        peakHour = repository.getPeakHour();
        totalSilentDuration = repository.getTotalSilentDuration();
    }

    public void insert(UsageLog log) {
        repository.insert(log);
    }

    public LiveData<List<UsageLog>> getAllLogs() { return allLogs; }
    public LiveData<UsageModeCount> getMostUsedMode() { return mostUsedMode; }
    public LiveData<PeakHourCount> getPeakHour() { return peakHour; }
    public LiveData<Long> getTotalSilentDuration() { return totalSilentDuration; }
} 