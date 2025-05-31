package com.example.sssshhift.features.smartauto;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.util.Log;
import androidx.preference.PreferenceManager;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class SmartAutoReceiver extends BroadcastReceiver {
    private static final String TAG = "SmartAutoReceiver";
    private static final long WAKE_LOCK_TIMEOUT = 30000; // 30 seconds
    private static final String PREF_LAST_ALARM_HANDLED = "last_alarm_handled";
    private static final long DUPLICATE_THRESHOLD = 10000; // 10 seconds

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            Log.e(TAG, "Received null intent");
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        // Acquire wake lock to ensure we complete our work
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        
        try {
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Sssshhhift:SmartAutoWakeLock"
                );
                wakeLock.acquire(WAKE_LOCK_TIMEOUT);
                Log.d(TAG, "Acquired wake lock");
            }

            boolean toSilent = intent.getBooleanExtra("to_silent", false);
            long eventStart = intent.getLongExtra("event_start", 0);
            long triggerTime = intent.getLongExtra("trigger_time", 0);
            boolean isBackup = intent.getBooleanExtra("is_backup", false);
            boolean isEarlyWarning = intent.getBooleanExtra("is_early_warning", false);
            boolean isWindow = intent.getBooleanExtra("is_window", false);

            if (eventStart == 0 || triggerTime == 0) {
                Log.e(TAG, "Invalid event parameters");
                return;
            }

            // Check for duplicate alarms within threshold
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String alarmKey = eventStart + "_" + toSilent + "_" + triggerTime;
            long lastHandled = prefs.getLong(PREF_LAST_ALARM_HANDLED + "_" + alarmKey, 0);
            long currentTime = System.currentTimeMillis();

            if (currentTime - lastHandled < DUPLICATE_THRESHOLD) {
                Log.d(TAG, "Skipping duplicate alarm within " + DUPLICATE_THRESHOLD + "ms");
                return;
            }

            // Update last handled time
            prefs.edit()
                .putLong(PREF_LAST_ALARM_HANDLED + "_" + alarmKey, currentTime)
                .apply();

            Log.d(TAG, "Received alarm for event starting at: " + new Date(eventStart));
            Log.d(TAG, "Action: " + (toSilent ? "Activate silent mode" : "Revert to previous mode"));
            Log.d(TAG, "Alarm type: " + (isBackup ? "Backup" : isEarlyWarning ? "Early Warning" : isWindow ? "Window" : "Primary"));
            Log.d(TAG, "Trigger time: " + new Date(triggerTime));

            // For early warning alarms, just ensure the main alarm is scheduled
            if (isEarlyWarning) {
                Log.d(TAG, "Early warning alarm received");
                return;
            }

            // Change the ringer mode
            boolean success = false;
            int retryCount = 0;
            Exception lastError = null;

            while (!success && retryCount < 3) {
                try {
                    if (!toSilent) {
                        // For revert alarms, verify the event is actually over
                        Set<String> activeEvents = SmartAutoAlarmManager.getActiveEvents(context);
                        Log.d(TAG, "Current active events before revert: " + activeEvents);
                        
                        // Remove any events for this start time that have ended
                        Set<String> updatedEvents = new HashSet<>(activeEvents);
                        boolean hasActiveEvent = false;
                        
                        for (String eventKey : activeEvents) {
                            if (eventKey.startsWith(eventStart + "_")) {
                                String[] parts = eventKey.split("_");
                                long eventEnd = Long.parseLong(parts[1]);
                                if (currentTime >= eventEnd) {
                                    updatedEvents.remove(eventKey);
                                    Log.d(TAG, "Removed ended event: " + eventKey);
                                } else {
                                    hasActiveEvent = true;
                                    Log.d(TAG, "Found active event that hasn't ended: " + eventKey);
                                }
                            }
                        }
                        
                        SmartAutoAlarmManager.updateActiveEvents(context, updatedEvents);
                        
                        if (hasActiveEvent) {
                            Log.d(TAG, "Event is still active or other events exist, skipping revert");
                            success = true;
                            continue;
                        }
                    }

                    SmartAutoAlarmManager.changeRingerMode(context, toSilent, eventStart);
                    success = true;
                    Log.d(TAG, "Successfully " + (toSilent ? "activated silent mode" : "reverted to previous mode"));
                } catch (Exception e) {
                    lastError = e;
                    retryCount++;
                    Log.e(TAG, "Error changing ringer mode (attempt " + retryCount + "): " + e.getMessage());
                    if (retryCount < 3) {
                        try {
                            Thread.sleep(1000); // Wait 1 second before retry
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            if (!success) {
                Log.e(TAG, "Failed to change ringer mode after " + retryCount + " attempts", lastError);
                if (!toSilent) {
                    // For revert failures, schedule another attempt in 1 minute
                    SmartAutoAlarmManager.scheduleRingerModeChange(context, eventStart, triggerTime + 60000);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing ringer mode change", e);
            // Schedule a retry through the worker
            SmartAutoWorker.scheduleWork(context);
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                try {
                    wakeLock.release();
                    Log.d(TAG, "Released wake lock");
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing wake lock", e);
                }
            }
        }
    }
} 