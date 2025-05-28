package com.example.sssshhift.utils;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.example.sssshhift.receivers.AlarmReceiver;
import com.example.sssshhift.receivers.GeofenceReceiver;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ProfileUtils {
    private static final String TAG = "ProfileUtils";

    // Updated method with end time support
    public static void scheduleProfile(Context context, String profileName, boolean isTimeBased, String triggerValue, String endTime) {
        if (isTimeBased) {
            scheduleTimeBasedProfile(context, profileName, triggerValue, endTime);
        } else {
            scheduleLocationBasedProfile(context, profileName, triggerValue);
        }
    }

    // Legacy method for backward compatibility
    public static void scheduleProfile(Context context, String profileName, boolean isTimeBased, String triggerValue) {
        scheduleProfile(context, profileName, isTimeBased, triggerValue, null);
    }

    private static void scheduleTimeBasedProfile(Context context, String profileName, String startTime, String endTime) {
        try {
            // Check if we have permission to schedule exact alarms (Android 12+)
            if (!canScheduleExactAlarms(context)) {
                Toast.makeText(context, "Please enable exact alarm permission in settings", Toast.LENGTH_LONG).show();
                return;
            }

            // Schedule START alarm
            scheduleAlarm(context, profileName, startTime, true);

            // Schedule END alarm if end time is provided
            if (endTime != null && !endTime.isEmpty()) {
                scheduleAlarm(context, profileName, endTime, false);
                Toast.makeText(context, "Profile scheduled: " + startTime + " to " + endTime + " daily", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Profile scheduled for " + startTime + " daily", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error scheduling profile: " + e.getMessage(), e);
            Toast.makeText(context, "Error scheduling profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void scheduleAlarm(Context context, String profileName, String time, boolean isStartAlarm) {
        try {
            String[] timeParts = time.split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            // If the time has already passed today, schedule for tomorrow
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1);
            }

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                Log.e(TAG, "AlarmManager is null");
                return;
            }

            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.putExtra("profile_name", profileName);
            intent.putExtra("is_start_alarm", isStartAlarm);

            // Create unique action for start/end alarms
            String action = isStartAlarm ? "PROFILE_START_" + profileName : "PROFILE_END_" + profileName;
            intent.setAction(action);

            // Create unique request code for start/end alarms
            int requestCode = (profileName + (isStartAlarm ? "_START" : "_END")).hashCode();

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Cancel any existing alarm first
            alarmManager.cancel(pendingIntent);

            // Use setExactAndAllowWhileIdle for better reliability on modern Android
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            } else {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            }

            String alarmType = isStartAlarm ? "START" : "END";
            Log.d(TAG, alarmType + " alarm scheduled for " + profileName + " at " + time +
                    " (timestamp: " + calendar.getTimeInMillis() + ")");

        } catch (Exception e) {
            Log.e(TAG, "Error scheduling alarm: " + e.getMessage(), e);
        }
    }

    private static void scheduleLocationBasedProfile(Context context, String profileName, String location) {
        try {
            // Check location permissions
            if (!hasLocationPermissions(context)) {
                Toast.makeText(context, "Location permissions required for geofencing", Toast.LENGTH_LONG).show();
                return;
            }

            // Parse location string (format: "latitude,longitude,radius")
            String[] locationParts = location.split(",");
            if (locationParts.length != 3) {
                Toast.makeText(context, "Invalid location format. Use: latitude,longitude,radius", Toast.LENGTH_LONG).show();
                return;
            }

            double latitude = Double.parseDouble(locationParts[0].trim());
            double longitude = Double.parseDouble(locationParts[1].trim());
            float radius = Float.parseFloat(locationParts[2].trim());

            // Create geofence
            String geofenceId = "profile_" + profileName + "_" + System.currentTimeMillis();

            Geofence geofence = new Geofence.Builder()
                    .setRequestId(geofenceId)
                    .setCircularRegion(latitude, longitude, radius)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build();

            // Create geofencing request
            GeofencingRequest geofencingRequest = new GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofence(geofence)
                    .build();

            // Create PendingIntent for geofence transitions
            Intent intent = new Intent(context, GeofenceReceiver.class);
            PendingIntent geofencePendingIntent = PendingIntent.getBroadcast(
                    context,
                    geofenceId.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
            );

            // Add geofence
            GeofencingClient geofencingClient = LocationServices.getGeofencingClient(context);

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "Fine location permission required", Toast.LENGTH_LONG).show();
                return;
            }

            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.d(TAG, "Geofence added successfully for profile: " + profileName);
                            Toast.makeText(context, "Location-based profile registered: " + profileName, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            Log.e(TAG, "Failed to add geofence: " + e.getMessage(), e);
                            Toast.makeText(context, "Failed to register geofence: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });

        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid location format: " + e.getMessage(), e);
            Toast.makeText(context, "Invalid location coordinates", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling location-based profile: " + e.getMessage(), e);
            Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Check if app can schedule exact alarms (required for Android 12+)
    private static boolean canScheduleExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            return alarmManager != null && alarmManager.canScheduleExactAlarms();
        }
        return true; // Always allowed on older Android versions
    }

    // Check if location permissions are granted
    private static boolean hasLocationPermissions(Context context) {
        boolean fineLocation = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseLocation = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        // For Android 10+ (API 29+), also check background location
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            boolean backgroundLocation = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
            return fineLocation && coarseLocation && backgroundLocation;
        }

        return fineLocation && coarseLocation;
    }

    // Method to cancel all alarms for a profile (both start and end)
    public static void cancelProfile(Context context, String profileName) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            // Cancel START alarm
            cancelAlarm(context, alarmManager, profileName, true);

            // Cancel END alarm
            cancelAlarm(context, alarmManager, profileName, false);

            Log.d(TAG, "Cancelled all alarms for profile: " + profileName);
        } catch (Exception e) {
            Log.e(TAG, "Error cancelling profile: " + e.getMessage(), e);
        }
    }

    // Method to cancel geofences for a profile
    public static void cancelLocationProfile(Context context, String profileName) {
        try {
            GeofencingClient geofencingClient = LocationServices.getGeofencingClient(context);

            // Create list of geofence IDs to remove (we'll remove all that start with profile name)
            List<String> geofenceIds = new ArrayList<>();
            String geofencePrefix = "profile_" + profileName;
            geofenceIds.add(geofencePrefix); // This is a simplified approach

            geofencingClient.removeGeofences(geofenceIds)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.d(TAG, "Geofences removed successfully for profile: " + profileName);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            Log.e(TAG, "Failed to remove geofences: " + e.getMessage(), e);
                        }
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error cancelling location profile: " + e.getMessage(), e);
        }
    }

    private static void cancelAlarm(Context context, AlarmManager alarmManager, String profileName, boolean isStartAlarm) {
        try {
            Intent intent = new Intent(context, AlarmReceiver.class);
            String action = isStartAlarm ? "PROFILE_START_" + profileName : "PROFILE_END_" + profileName;
            intent.setAction(action);

            int requestCode = (profileName + (isStartAlarm ? "_START" : "_END")).hashCode();

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            alarmManager.cancel(pendingIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error cancelling alarm: " + e.getMessage(), e);
        }
    }
}