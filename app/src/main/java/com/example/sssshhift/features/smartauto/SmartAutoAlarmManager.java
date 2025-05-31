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
import java.util.HashSet;
import java.util.Set;

public class SmartAutoAlarmManager {
    private static final String TAG = "SmartAutoAlarmManager";
    private static final String PREF_PREVIOUS_RINGER_MODE = "previous_ringer_mode";
    private static final Object lock = new Object();
    public static final String PREF_ACTIVE_EVENTS = "active_calendar_events";

    /**
     * Get the set of active calendar events
     * @param context Application context
     * @return Set of active event keys in format "eventStart_eventEnd"
     */
    public static Set<String> getActiveEvents(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getStringSet(PREF_ACTIVE_EVENTS, new HashSet<>());
    }

    /**
     * Update the set of active calendar events
     * @param context Application context
     * @param activeEvents Updated set of active events
     */
    public static void updateActiveEvents(Context context, Set<String> activeEvents) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putStringSet(PREF_ACTIVE_EVENTS, activeEvents).apply();
    }

    /**
     * Clean up the stored ringer mode preference for a specific event
     * @param context Application context
     * @param eventStartTime Event start time in milliseconds
     */
    public static void cleanupRingerModePreference(Context context, long eventStartTime) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
            .remove(PREF_PREVIOUS_RINGER_MODE + "_" + eventStartTime)
            .apply();
        Log.d(TAG, "Cleaned up ringer mode preference for event at: " + new Date(eventStartTime));
    }

    public static void scheduleRingerModeChange(Context context, long eventStart, long eventEnd) {
        synchronized (lock) {
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

            // Get current active events
            Set<String> activeEvents = getActiveEvents(context);
            String eventKey = eventStart + "_" + eventEnd;

            Log.d(TAG, "Pre-event offset: " + preEventOffset + " minutes");
            Log.d(TAG, "Revert after event: " + revertAfterEvent);
            Log.d(TAG, "Active events: " + activeEvents);

            // Get current time and calculate silent mode time
            long currentTime = System.currentTimeMillis();
            long silentTime = eventStart - (preEventOffset * 60 * 1000);
            
            Log.d(TAG, "Current time: " + new Date(currentTime));
            Log.d(TAG, "Event start: " + new Date(eventStart));
            Log.d(TAG, "Event end: " + new Date(eventEnd));
            Log.d(TAG, "Silent mode time: " + new Date(silentTime));
            
            // Cancel any existing alarms for this event first
            cancelScheduledChanges(context, eventStart);

            // Add event to active events
            activeEvents.add(eventKey);
            updateActiveEvents(context, activeEvents);

            // Handle silent mode scheduling
            if (silentTime > currentTime && currentTime < eventStart) {
                // If we're before the silent time, schedule the silent mode activation
                Log.d(TAG, "Scheduling silent mode activation");
                scheduleAlarm(context, silentTime, true, eventStart);
            } else if (currentTime < eventEnd) {
                // If we're between start and end time, activate silent mode immediately
                Log.d(TAG, "Past silent time but before event end, activating silent mode now");
                changeRingerMode(context, true, eventStart);
            }

            // Always schedule the revert alarm if the event hasn't ended yet and revert is enabled
            if (revertAfterEvent && currentTime < eventEnd) {
                Log.d(TAG, "Scheduling revert to previous mode at: " + new Date(eventEnd));
                // Schedule multiple revert alarms for redundancy
                scheduleAlarm(context, eventEnd, false, eventStart);
                // Schedule a backup revert alarm 1 minute after the event end
                scheduleAlarm(context, eventEnd + 60000, false, eventStart);
            } else if (currentTime >= eventEnd) {
                // If we're past the event end time, revert immediately if no other events are active
                Log.d(TAG, "Past event end time, checking if revert is needed");
                activeEvents.remove(eventKey);
                updateActiveEvents(context, activeEvents);
                
                if (activeEvents.isEmpty()) {
                    Log.d(TAG, "No active events, reverting to previous mode");
                    changeRingerMode(context, false, eventStart);
                } else {
                    Log.d(TAG, "Other events still active, maintaining silent mode");
                }
            }
        }
    }

    private static void scheduleAlarm(Context context, long triggerTime, boolean toSilent, long eventStart) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, SmartAutoReceiver.class);
        intent.putExtra("to_silent", toSilent);
        intent.putExtra("event_start", eventStart);
        intent.putExtra("trigger_time", triggerTime);
        
        // Add action to make intent more specific
        intent.setAction("com.example.sssshhift.SMART_AUTO_ALARM_" + (toSilent ? "SILENT" : "REVERT") + "_" + eventStart + "_" + triggerTime);
        
        // Add flags to ensure delivery
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        // Create unique request code based on event start time and action
        int requestCode = (int) ((eventStart + triggerTime) % Integer.MAX_VALUE);
        requestCode = toSilent ? requestCode * 2 : (requestCode * 2 + 1);

        Log.d(TAG, "Scheduling alarm with request code: " + requestCode + 
                " for " + new Date(triggerTime) + 
                " (toSilent: " + toSilent + ")");

        // Create primary pending intent
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Schedule multiple alarms with different mechanisms for redundancy
        try {
            // 1. Primary alarm using setAlarmClock (most reliable, will wake device)
            alarmManager.setAlarmClock(
                    new AlarmManager.AlarmClockInfo(triggerTime, null),
                    pendingIntent
            );
            Log.d(TAG, "Scheduled primary alarm using setAlarmClock");

            // 2. Early warning alarm (1 minute before) using setExactAndAllowWhileIdle
            if (!toSilent) {  // Only for revert alarms
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent earlyIntent = new Intent(context, SmartAutoReceiver.class);
                    earlyIntent.putExtra("to_silent", toSilent);
                    earlyIntent.putExtra("event_start", eventStart);
                    earlyIntent.putExtra("trigger_time", triggerTime);
                    earlyIntent.putExtra("is_early_warning", true);
                    earlyIntent.setAction("com.example.sssshhift.SMART_AUTO_ALARM_EARLY_" + 
                            (toSilent ? "SILENT" : "REVERT") + "_" + eventStart + "_" + triggerTime);

                    PendingIntent earlyPendingIntent = PendingIntent.getBroadcast(
                            context,
                            requestCode + 2000000,
                            earlyIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );

                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime - 60000, // 1 minute early
                            earlyPendingIntent
                    );
                    Log.d(TAG, "Scheduled early warning alarm for revert");
                }
            }

            // Store alarm info in SharedPreferences for recovery
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
            String alarmKey = "alarm_" + requestCode;
            editor.putLong(alarmKey + "_time", triggerTime);
            editor.putBoolean(alarmKey + "_silent", toSilent);
            editor.putLong(alarmKey + "_event_start", eventStart);
            editor.putLong(alarmKey + "_scheduled_at", System.currentTimeMillis());
            editor.apply();

            Log.d(TAG, "Successfully scheduled alarm for " + new Date(triggerTime) + 
                    " (to silent: " + toSilent + ")");

        } catch (Exception e) {
            Log.e(TAG, "Error scheduling alarms: " + e.getMessage(), e);
            // Try fallback method
            try {
                alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );
                Log.d(TAG, "Scheduled fallback alarm after error");
            } catch (Exception fallbackError) {
                Log.e(TAG, "Error scheduling fallback alarm: " + fallbackError.getMessage(), fallbackError);
            }
        }
    }

    public static void changeRingerMode(Context context, boolean toSilent, long eventStart) {
        synchronized (lock) {
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
                        // Check if there are any active events before reverting
                        Set<String> activeEvents = getActiveEvents(context);
                        Log.d(TAG, "Current active events when attempting to revert: " + activeEvents);
                        
                        // Remove this event from active events since it's ending
                        Set<String> updatedEvents = new HashSet<>(activeEvents);
                        for (String eventKey : activeEvents) {
                            if (eventKey.startsWith(eventStart + "_")) {
                                updatedEvents.remove(eventKey);
                                Log.d(TAG, "Removed ending event from active events: " + eventKey);
                            }
                        }
                        updateActiveEvents(context, updatedEvents);
                        
                        if (updatedEvents.isEmpty()) {
                            // Revert to previous mode only if no other events are active
                            int previousMode = prefs.getInt(PREF_PREVIOUS_RINGER_MODE + "_" + eventStart, 
                                    AudioManager.RINGER_MODE_NORMAL);
                            Log.d(TAG, "Retrieved previous ringer mode: " + getRingerModeName(previousMode));
                            
                            audioManager.setRingerMode(previousMode);
                            Log.d(TAG, "Reverted to previous mode: " + getRingerModeName(previousMode));
                            
                            // Clean up the stored preference
                            cleanupRingerModePreference(context, eventStart);
                        } else {
                            Log.d(TAG, "Other events still active, maintaining silent mode. Active events: " + updatedEvents);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error changing ringer mode", e);
                }
            } else {
                Log.e(TAG, "AudioManager is null");
            }
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

        // Calculate request codes using the same logic as scheduling
        int silentRequestCode = (int) (eventStart % Integer.MAX_VALUE) * 2;
        int revertRequestCode = (int) (eventStart % Integer.MAX_VALUE) * 2 + 1;

        // Cancel silent mode alarm
        Intent silentIntent = new Intent(context, SmartAutoReceiver.class);
        silentIntent.putExtra("to_silent", true);
        silentIntent.putExtra("event_start", eventStart);
        PendingIntent silentPendingIntent = PendingIntent.getBroadcast(
                context, silentRequestCode, silentIntent, 
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (silentPendingIntent != null) {
            alarmManager.cancel(silentPendingIntent);
            silentPendingIntent.cancel();
            Log.d(TAG, "Cancelled silent mode alarm for event at: " + new Date(eventStart));
        }

        // Cancel revert alarm
        Intent revertIntent = new Intent(context, SmartAutoReceiver.class);
        revertIntent.putExtra("to_silent", false);
        revertIntent.putExtra("event_start", eventStart);
        PendingIntent revertPendingIntent = PendingIntent.getBroadcast(
                context, revertRequestCode, revertIntent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (revertPendingIntent != null) {
            alarmManager.cancel(revertPendingIntent);
            revertPendingIntent.cancel();
            Log.d(TAG, "Cancelled revert alarm for event at: " + new Date(eventStart));
        }

        // Clean up stored preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
            .remove(PREF_PREVIOUS_RINGER_MODE + "_" + eventStart)
            .apply();
        Log.d(TAG, "Cleaned up stored preferences for event at: " + new Date(eventStart));
    }
} 