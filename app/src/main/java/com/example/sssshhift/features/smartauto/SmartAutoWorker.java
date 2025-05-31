package com.example.sssshhift.features.smartauto;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.Date;

public class SmartAutoWorker extends Worker {
    private static final String TAG = "SmartAutoWorker";
    private static final String WORK_NAME = "smart_auto_calendar_check";

    public SmartAutoWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // Check if feature is enabled
        boolean isEnabled = prefs.getBoolean("auto_mode_enabled", false);
        Log.d(TAG, "Smart Auto Mode enabled: " + isEnabled);
        if (!isEnabled) {
            return Result.success();
        }

        // Check calendar permission
        boolean hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "Calendar permission granted: " + hasPermission);
        if (!hasPermission) {
            return Result.failure();
        }

        try {
            checkUpcomingEvents(context);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Error checking calendar events", e);
            return Result.retry();
        }
    }

    private void checkUpcomingEvents(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> keywords = prefs.getStringSet("auto_mode_keywords", new HashSet<>());
        int preEventOffset = prefs.getInt("auto_mode_pre_event_offset", 5);
        boolean busyEventsOnly = prefs.getBoolean("auto_mode_busy_events_only", true);

        Log.d(TAG, "Checking events with keywords: " + keywords);
        Log.d(TAG, "Pre-event offset: " + preEventOffset + " minutes");
        Log.d(TAG, "Busy events only: " + busyEventsOnly);

        // Calculate time window
        long now = System.currentTimeMillis();
        long windowStart = now;
        long windowEnd = now + TimeUnit.MINUTES.toMillis(preEventOffset + 30);

        ContentResolver contentResolver = context.getContentResolver();
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, windowStart);
        ContentUris.appendId(builder, windowEnd);

        // Define the columns we want
        String[] projection = new String[]{
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.AVAILABILITY
        };

        // Query calendar events
        try (Cursor cursor = contentResolver.query(builder.build(), projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                Log.d(TAG, "Found calendar events to check");
                do {
                    String title = cursor.getString(cursor.getColumnIndex(CalendarContract.Instances.TITLE));
                    long begin = cursor.getLong(cursor.getColumnIndex(CalendarContract.Instances.BEGIN));
                    long end = cursor.getLong(cursor.getColumnIndex(CalendarContract.Instances.END));
                    int availability = cursor.getInt(cursor.getColumnIndex(CalendarContract.Instances.AVAILABILITY));

                    Log.d(TAG, "Checking event: " + title + ", Start: " + new Date(begin) + ", End: " + new Date(end));

                    // Check if event matches our criteria
                    if (isEventMatch(title, availability, busyEventsOnly, keywords)) {
                        Log.d(TAG, "Event matches criteria, scheduling ringer mode change");
                        scheduleRingerModeChange(context, begin, end);
                    } else {
                        Log.d(TAG, "Event does not match criteria");
                    }
                } while (cursor.moveToNext());
            } else {
                Log.d(TAG, "No calendar events found in the time window");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying calendar", e);
        }
    }

    private boolean isEventMatch(String title, int availability, boolean busyEventsOnly, Set<String> keywords) {
        if (busyEventsOnly && availability != CalendarContract.Events.AVAILABILITY_BUSY) {
            Log.d(TAG, "Event skipped: not marked as busy");
            return false;
        }

        if (title == null) {
            Log.d(TAG, "Event skipped: null title");
            return false;
        }

        if (keywords == null || keywords.isEmpty()) {
            Log.d(TAG, "Event skipped: no keywords defined");
            return false;
        }

        String titleLower = title.toLowerCase().trim();
        Log.d(TAG, "Checking title '" + titleLower + "' against keywords: " + keywords);

        for (String keyword : keywords) {
            String keywordLower = keyword.toLowerCase().trim();
            if (titleLower.contains(keywordLower)) {
                Log.d(TAG, "Event matched keyword: " + keyword);
                return true;
            }
        }
        
        Log.d(TAG, "Event did not match any keywords");
        return false;
    }

    private void scheduleRingerModeChange(Context context, long eventStart, long eventEnd) {
        // Schedule the ringer mode changes using SmartAutoAlarmManager
        SmartAutoAlarmManager.scheduleRingerModeChange(context, eventStart, eventEnd);
    }

    public static void scheduleWork(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build();

        // Schedule periodic work every 15 minutes
        PeriodicWorkRequest periodicWork = new PeriodicWorkRequest.Builder(
                SmartAutoWorker.class,
                15, TimeUnit.MINUTES)  // Changed from 30 to 15 minutes
                .setConstraints(constraints)
                .build();

        // Schedule immediate one-time work
        OneTimeWorkRequest immediateWork = new OneTimeWorkRequest.Builder(SmartAutoWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager workManager = WorkManager.getInstance(context);
        
        // Enqueue immediate check
        workManager.enqueue(immediateWork);
        
        // Schedule periodic checks
        workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                periodicWork);
        
        Log.d(TAG, "Scheduled immediate and periodic work for calendar checks");
    }

    public static void cancelWork(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }
} 