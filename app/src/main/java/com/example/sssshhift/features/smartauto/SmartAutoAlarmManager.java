package com.example.sssshhift.features.smartauto;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import androidx.preference.PreferenceManager;

import java.util.Date;

public class SmartAutoAlarmManager {
    private static final String TAG = "SmartAutoAlarmManager";
    private static final String PREF_PREVIOUS_RINGER_MODE = "previous_ringer_mode";

    public static void scheduleRingerModeChange(Context context, long eventStart, long eventEnd) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager is null");
            return;
        }

        // Check if app can schedule exact alarms (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean canSchedule = alarmManager.canScheduleExactAlarms();
            Log.d(TAG, "Can schedule exact alarms: " + canSchedule);
            if (!canSchedule) {
                Log.e(TAG, "Cannot schedule exact alarms");
                return;
            }
        }

        // Check if app has Do Not Disturb access
        NotificationManager notificationManager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean hasDndAccess = notificationManager != null && notificationManager.isNotificationPolicyAccessGranted();
        Log.d(TAG, "Has DND access: " + hasDndAccess);
        
        if (!hasDndAccess) {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int preEventOffset = prefs.getInt("auto_mode_pre_event_offset", 5);
        boolean revertAfterEvent = prefs.getBoolean("auto_mode_revert_after_event", true);

        Log.d(TAG, "Pre-event offset: " + preEventOffset + " minutes");
        Log.d(TAG, "Revert after event: " + revertAfterEvent);

        // Get current time and calculate silent mode time
        long currentTime = System.currentTimeMillis();
        long silentTime = eventStart - (preEventOffset * 60 * 1000);
        
        Log.d(TAG, "Current time: " + new Date(currentTime));
        Log.d(TAG, "Event start: " + new Date(eventStart));
        Log.d(TAG, "Silent mode time: " + new Date(silentTime));
        
        // Only schedule silent mode if it's in the future and we're not already in the event
        if (silentTime > currentTime && currentTime < eventStart) {
            Log.d(TAG, "Scheduling silent mode activation");
            scheduleAlarm(context, silentTime, true, eventStart);
        } else if (currentTime < eventStart) {
            // If we're past the silent time but before event start, activate silent mode immediately
            Log.d(TAG, "Past silent time but before event, activating silent mode now");
            changeRingerMode(context, true, eventStart);
        } else {
            Log.d(TAG, "Event already started or silent time passed");
        }

        // Schedule revert to previous mode if enabled and event hasn't ended
        if (revertAfterEvent && currentTime < eventEnd) {
            Log.d(TAG, "Scheduling revert to previous mode at: " + new Date(eventEnd));
            scheduleAlarm(context, eventEnd, false, eventStart);
        }
    }

    private static void scheduleAlarm(Context context, long triggerTime, boolean toSilent, long eventStart) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, SmartAutoReceiver.class);
        intent.putExtra("to_silent", toSilent);
        intent.putExtra("event_start", eventStart);

        // Create unique request code based on event start time and action
        int requestCode = (int) ((eventStart / 1000) % Integer.MAX_VALUE);
        if (toSilent) {
            requestCode = requestCode * 2;  // Even numbers for silent mode
        } else {
            requestCode = requestCode * 2 + 1;  // Odd numbers for revert
        }

        Log.d(TAG, "Scheduling alarm with request code: " + requestCode + 
                " for " + new Date(triggerTime) + 
                " (toSilent: " + toSilent + ")");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAlarmClock(
                    new AlarmManager.AlarmClockInfo(triggerTime, null),
                    pendingIntent
            );
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
            );
        }

        Log.d(TAG, "Successfully scheduled alarm for " + new Date(triggerTime) + 
                " (to silent: " + toSilent + ")");
    }

    public static void changeRingerMode(Context context, boolean toSilent, long eventStart) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (audioManager != null) {
            try {
                if (toSilent) {
                    // Store current ringer mode before changing to silent
                    int currentMode = audioManager.getRingerMode();
                    Log.d(TAG, "Current ringer mode: " + getRingerModeName(currentMode));
                    Log.d(TAG, "Storing current ringer mode for event at: " + new Date(eventStart));
                    
                    prefs.edit()
                        .putInt(PREF_PREVIOUS_RINGER_MODE + "_" + eventStart, currentMode)
                        .apply();

                    // Change to silent mode
                    audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    Log.d(TAG, "Changed to silent mode");
                } else {
                    // Revert to previous mode
                    int previousMode = prefs.getInt(PREF_PREVIOUS_RINGER_MODE + "_" + eventStart, 
                            AudioManager.RINGER_MODE_NORMAL);
                    Log.d(TAG, "Retrieved previous ringer mode: " + getRingerModeName(previousMode));
                    
                    audioManager.setRingerMode(previousMode);
                    Log.d(TAG, "Reverted to previous mode: " + getRingerModeName(previousMode));
                    
                    // Clean up the stored preference
                    prefs.edit()
                        .remove(PREF_PREVIOUS_RINGER_MODE + "_" + eventStart)
                        .apply();
                    Log.d(TAG, "Cleaned up stored preference for event at: " + new Date(eventStart));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error changing ringer mode", e);
            }
        } else {
            Log.e(TAG, "AudioManager is null");
        }
    }

    private static String getRingerModeName(int mode) {
        switch (mode) {
            case AudioManager.RINGER_MODE_SILENT:
                return "SILENT";
            case AudioManager.RINGER_MODE_VIBRATE:
                return "VIBRATE";
            case AudioManager.RINGER_MODE_NORMAL:
                return "NORMAL";
            default:
                return "UNKNOWN";
        }
    }

    public static void cancelScheduledChanges(Context context, long eventStart) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // Cancel both the silent mode and revert mode alarms
        Intent intent = new Intent(context, SmartAutoReceiver.class);
        intent.putExtra("event_start", eventStart);

        // Calculate the same request codes used when scheduling
        long silentTime = eventStart - (PreferenceManager.getDefaultSharedPreferences(context)
                .getInt("auto_mode_pre_event_offset", 5) * 60 * 1000);
        int silentRequestCode = (int) ((silentTime / 1000) % Integer.MAX_VALUE) + 1;
        int revertRequestCode = (int) ((eventStart / 1000) % Integer.MAX_VALUE);

        PendingIntent silentPendingIntent = PendingIntent.getBroadcast(
                context, silentRequestCode, intent, 
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (silentPendingIntent != null) {
            alarmManager.cancel(silentPendingIntent);
            silentPendingIntent.cancel();
        }

        PendingIntent revertPendingIntent = PendingIntent.getBroadcast(
                context, revertRequestCode, intent, 
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (revertPendingIntent != null) {
            alarmManager.cancel(revertPendingIntent);
            revertPendingIntent.cancel();
        }
    }
} 