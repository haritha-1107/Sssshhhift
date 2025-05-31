package com.example.sssshhift.location;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import java.util.List;

public class LocationReceiver extends BroadcastReceiver {
    private static final String TAG = "LocationReceiver";
    private static final String CHANNEL_ID = "location_notification_channel";
    private static final int NOTIFICATION_ID = 2001;
    private static final String ACTION_PROCESS_GEOFENCE = "com.example.sssshhift.ACTION_PROCESS_GEOFENCE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        if (!ACTION_PROCESS_GEOFENCE.equals(intent.getAction())) {
            return;
        }

        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent == null) {
            Log.e(TAG, "Null GeofencingEvent received");
            return;
        }

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "GeofencingEvent error: " + geofencingEvent.getErrorCode());
            return;
        }

        // Get the transition type
        int geofenceTransition = geofencingEvent.getGeofenceTransition();
        
        // Get the geofences that triggered this event
        List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();
        
        if (triggeringGeofences == null || triggeringGeofences.isEmpty()) {
            Log.e(TAG, "No geofence trigger found");
            return;
        }

        for (Geofence geofence : triggeringGeofences) {
            String profileId = geofence.getRequestId();
            handleGeofenceTransition(context, profileId, geofenceTransition);
        }
    }

    private void handleGeofenceTransition(Context context, String profileId, int transition) {
        SharedPreferences prefs = context.getSharedPreferences("location_profiles", Context.MODE_PRIVATE);
        
        String name = prefs.getString(profileId + "_name", "");
        int mode = prefs.getInt(profileId + "_mode", AudioManager.RINGER_MODE_NORMAL);

        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            // Change to profile's ringer mode when entering
            LocationProfileManager manager = new LocationProfileManager(context);
            if (manager.changeRingerMode(mode, name)) {
                showNotification(context, name, mode, true);
            }
        } else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            // Change back to normal mode when exiting
            LocationProfileManager manager = new LocationProfileManager(context);
            if (manager.changeRingerMode(AudioManager.RINGER_MODE_NORMAL, name)) {
                showNotification(context, name, AudioManager.RINGER_MODE_NORMAL, false);
            }
        }
    }

    private void showNotification(Context context, String profileName, int ringerMode, boolean isEntering) {
        NotificationManager notificationManager = 
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (notificationManager == null) return;

        createNotificationChannel(context);

        String title = isEntering ? "Entered Location Profile" : "Left Location Profile";
        String message = String.format("%s - Phone is now in %s mode", 
            profileName, getRingerModeName(ringerMode));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private String getRingerModeName(int ringerMode) {
        switch (ringerMode) {
            case AudioManager.RINGER_MODE_SILENT:
                return "Silent";
            case AudioManager.RINGER_MODE_VIBRATE:
                return "Vibrate";
            case AudioManager.RINGER_MODE_NORMAL:
                return "Normal";
            default:
                return "Unknown";
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Location Profile Notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for location-based profile changes");
            
            NotificationManager notificationManager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    public static PendingIntent getPendingIntent(Context context) {
        Intent intent = new Intent(context, LocationReceiver.class);
        intent.setAction(ACTION_PROCESS_GEOFENCE);
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }
} 