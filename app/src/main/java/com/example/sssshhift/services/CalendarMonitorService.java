package com.example.sssshhift.services;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.example.sssshhift.utils.CalendarUtils;
import com.example.sssshhift.utils.NotificationUtils;

public class CalendarMonitorService extends Service {
    private static final String TAG = "CalendarMonitorService";
    private static final String PREF_PREVIOUS_RINGER_MODE = "previous_ringer_mode";
    private static final String PREF_CALENDAR_MODE_ACTIVE = "calendar_mode_active";
    private static final long CHECK_INTERVAL = 30000; // Check every 30 seconds

    private final Handler handler = new Handler();
    private Runnable calendarCheckRunnable;
    private AudioManager audioManager;
    private NotificationManager notificationManager;
    private SharedPreferences sharedPreferences;
    private boolean wasInCalendarMode = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Calendar Monitor Service created");

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Create notification channel
        NotificationUtils.createNotificationChannel(this);

        initializeCalendarCheck();
    }

    private void initializeCalendarCheck() {
        calendarCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkCalendarAndUpdateRingerMode();
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };
        handler.post(calendarCheckRunnable);
    }

    private void checkCalendarAndUpdateRingerMode() {
        try {
            boolean hasOngoingEvent = CalendarUtils.isEventOngoing(getApplicationContext());

            if (hasOngoingEvent && !wasInCalendarMode) {
                // Event started - activate silent mode
                activateCalendarSilentMode();
            } else if (!hasOngoingEvent && wasInCalendarMode) {
                // Event ended - restore previous mode
                deactivateCalendarSilentMode();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in calendar check: " + e.getMessage());
        }
    }

    private void activateCalendarSilentMode() {
        if (audioManager == null) return;

        try {
            // Check if we have Do Not Disturb permission for Android 6.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!notificationManager.isNotificationPolicyAccessGranted()) {
                    Log.w(TAG, "Do Not Disturb permission not granted");
                    // Show notification to user about permission
                    NotificationUtils.showPermissionRequiredNotification(this);
                    return;
                }
            }

            // Save current ringer mode
            int currentRingerMode = audioManager.getRingerMode();
            sharedPreferences.edit()
                    .putInt(PREF_PREVIOUS_RINGER_MODE, currentRingerMode)
                    .putBoolean(PREF_CALENDAR_MODE_ACTIVE, true)
                    .apply();

            // Set to silent mode
            audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            wasInCalendarMode = true;

            // Get event details for notification
            String eventDetails = CalendarUtils.getCurrentEventDetails(this);
            String eventName = eventDetails != null ? eventDetails : "Calendar Event";

            // Show notification
            NotificationUtils.showProfileActivatedNotification(this, "Calendar - " + eventName);

            Log.d(TAG, "Calendar silent mode activated for: " + eventName);

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception setting ringer mode: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error activating calendar silent mode: " + e.getMessage());
        }
    }

    private void deactivateCalendarSilentMode() {
        if (audioManager == null) return;

        try {
            // Restore previous ringer mode
            int previousRingerMode = sharedPreferences.getInt(PREF_PREVIOUS_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL);
            audioManager.setRingerMode(previousRingerMode);

            // Clear calendar mode flag
            sharedPreferences.edit()
                    .putBoolean(PREF_CALENDAR_MODE_ACTIVE, false)
                    .apply();

            wasInCalendarMode = false;

            // Show notification
            NotificationUtils.showProfileEndedNotification(this, "Calendar Event");

            Log.d(TAG, "Calendar silent mode deactivated, restored to mode: " + previousRingerMode);

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception restoring ringer mode: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error deactivating calendar silent mode: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Calendar Monitor Service started");
        return START_STICKY; // Restart service if killed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Calendar Monitor Service destroyed");

        if (handler != null && calendarCheckRunnable != null) {
            handler.removeCallbacks(calendarCheckRunnable);
        }

        // If service is destroyed while in calendar mode, restore normal mode
        if (wasInCalendarMode) {
            deactivateCalendarSilentMode();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // This is an unbound service
    }

    /**
     * Check if calendar monitoring is currently active
     */
    public static boolean isCalendarModeActive(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREF_CALENDAR_MODE_ACTIVE, false);
    }

    /**
     * Force check calendar events (can be called from UI)
     */
    public void forceCalendarCheck() {
        if (calendarCheckRunnable != null) {
            handler.removeCallbacks(calendarCheckRunnable);
            handler.post(calendarCheckRunnable);
        }
    }
}