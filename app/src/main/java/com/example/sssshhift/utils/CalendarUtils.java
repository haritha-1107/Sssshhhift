package com.example.sssshhift.utils;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;
import androidx.core.content.ContextCompat;

import java.util.Calendar;

public class CalendarUtils {
    private static final String TAG = "CalendarUtils";

    /**
     * Check if there's an ongoing calendar event
     * @param context Application context
     * @return true if there's an ongoing event, false otherwise
     */
    public static boolean isEventOngoing(Context context) {
        // Check for calendar permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Calendar permission not granted");
            return false;
        }

        try {
            ContentResolver contentResolver = context.getContentResolver();
            long currentTime = System.currentTimeMillis();

            // Query for events happening now
            Uri.Builder eventsUriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(eventsUriBuilder, currentTime - 60000); // 1 minute before now
            ContentUris.appendId(eventsUriBuilder, currentTime + 60000); // 1 minute after now

            String[] projection = {
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY
            };

            // Only get events from calendars that are visible/selected
            String selection = CalendarContract.Instances.VISIBLE + " = 1";

            Cursor cursor = contentResolver.query(
                    eventsUriBuilder.build(),
                    projection,
                    selection,
                    null,
                    CalendarContract.Instances.BEGIN + " ASC"
            );

            if (cursor == null) {
                Log.w(TAG, "Calendar query returned null cursor");
                return false;
            }

            boolean hasOngoingEvent = false;

            while (cursor.moveToNext()) {
                long eventStart = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN));
                long eventEnd = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.END));
                int allDay = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE));

                // Check if current time is between event start and end
                if (currentTime >= eventStart && currentTime <= eventEnd) {
                    Log.d(TAG, "Ongoing event found: " + title);
                    hasOngoingEvent = true;
                    break;
                }
            }

            cursor.close();
            return hasOngoingEvent;

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception accessing calendar: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking calendar events: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get details of current ongoing events
     * @param context Application context
     * @return String with event details, or null if no events
     */
    public static String getCurrentEventDetails(Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        try {
            ContentResolver contentResolver = context.getContentResolver();
            long currentTime = System.currentTimeMillis();

            Uri.Builder eventsUriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(eventsUriBuilder, currentTime - 60000);
            ContentUris.appendId(eventsUriBuilder, currentTime + 60000);

            String[] projection = {
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END
            };

            String selection = CalendarContract.Instances.VISIBLE + " = 1";

            Cursor cursor = contentResolver.query(
                    eventsUriBuilder.build(),
                    projection,
                    selection,
                    null,
                    CalendarContract.Instances.BEGIN + " ASC"
            );

            if (cursor == null) return null;

            StringBuilder eventDetails = new StringBuilder();

            while (cursor.moveToNext()) {
                long eventStart = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN));
                long eventEnd = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.END));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE));

                if (currentTime >= eventStart && currentTime <= eventEnd) {
                    if (eventDetails.length() > 0) eventDetails.append(", ");
                    eventDetails.append(title != null ? title : "Untitled Event");
                }
            }

            cursor.close();
            return eventDetails.length() > 0 ? eventDetails.toString() : null;

        } catch (Exception e) {
            Log.e(TAG, "Error getting event details: " + e.getMessage());
            return null;
        }
    }
}