package com.example.sssshhift.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.Geofence;
import com.example.sssshhift.R;

public class ProfileService extends Service {

    private static final String TAG = "ProfileService";
    private static final String CHANNEL_ID = "profile_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    // Actions
    public static final String ACTION_GEOFENCE_TRANSITION = "com.example.sssshhift.action.GEOFENCE_TRANSITION";
    public static final String ACTION_APPLY_PROFILE = "com.example.sssshhift.action.APPLY_PROFILE";
    public static final String ACTION_STOP_PROFILE = "com.example.sssshhift.action.STOP_PROFILE";

    // Extras
    public static final String EXTRA_GEOFENCE_ID = "geofence_id";
    public static final String EXTRA_TRANSITION_TYPE = "transition_type";
    public static final String EXTRA_PROFILE_ID = "profile_id";

    private AudioManager audioManager;
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ProfileService created");

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "ProfileService started");

        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (action == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Start as foreground service
        startForeground(NOTIFICATION_ID, createForegroundNotification("Processing profile changes..."));

        switch (action) {
            case ACTION_GEOFENCE_TRANSITION:
                handleGeofenceTransition(intent);
                break;
            case ACTION_APPLY_PROFILE:
                handleApplyProfile(intent);
                break;
            case ACTION_STOP_PROFILE:
                handleStopProfile(intent);
                break;
            default:
                Log.w(TAG, "Unknown action: " + action);
                break;
        }

        // Don't stop immediately - let it run for a few seconds to ensure processing
        return START_NOT_STICKY;
    }

    private void handleGeofenceTransition(Intent intent) {
        String geofenceId = intent.getStringExtra(EXTRA_GEOFENCE_ID);
        int transitionType = intent.getIntExtra(EXTRA_TRANSITION_TYPE, -1);

        Log.d(TAG, "Handling geofence transition - ID: " + geofenceId + ", Type: " + transitionType);

        if (geofenceId == null || transitionType == -1) {
            Log.e(TAG, "Invalid geofence transition data");
            stopSelf();
            return;
        }

        switch (transitionType) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                handleGeofenceEnter(geofenceId);
                break;
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                handleGeofenceExit(geofenceId);
                break;
            default:
                Log.w(TAG, "Unknown transition type: " + transitionType);
                break;
        }

        // Stop service after a delay to ensure processing is complete
        postDelayed(() -> stopSelf(), 2000);
    }

    private void handleGeofenceEnter(String geofenceId) {
        Log.d(TAG, "Entered geofence: " + geofenceId);

        // Extract profile ID from geofence ID (assuming format includes "profile_")
        String profileId = extractProfileIdFromGeofenceId(geofenceId);
        if (profileId != null) {
            applyProfileSettings(profileId);
            updateForegroundNotification("Profile activated: " + profileId);
        } else {
            Log.w(TAG, "Could not extract profile ID from: " + geofenceId);
            // Apply silent mode as default
            applySilentMode();
            updateForegroundNotification("Silent mode activated");
        }
    }

    private void handleGeofenceExit(String geofenceId) {
        Log.d(TAG, "Exited geofence: " + geofenceId);

        // Extract profile ID from geofence ID
        String profileId = extractProfileIdFromGeofenceId(geofenceId);
        if (profileId != null) {
            revertProfileSettings(profileId);
            updateForegroundNotification("Profile deactivated: " + profileId);
        } else {
            // Revert to normal mode as default
            revertToNormalMode();
            updateForegroundNotification("Normal mode restored");
        }
    }

    private void handleApplyProfile(Intent intent) {
        String profileId = intent.getStringExtra(EXTRA_PROFILE_ID);
        Log.d(TAG, "Applying profile: " + profileId);

        if (profileId != null) {
            applyProfileSettings(profileId);
            updateForegroundNotification("Applied profile: " + profileId);
        }

        postDelayed(() -> stopSelf(), 1000);
    }

    private void handleStopProfile(Intent intent) {
        String profileId = intent.getStringExtra(EXTRA_PROFILE_ID);
        Log.d(TAG, "Stopping profile: " + profileId);

        if (profileId != null) {
            revertProfileSettings(profileId);
            updateForegroundNotification("Stopped profile: " + profileId);
        }

        postDelayed(() -> stopSelf(), 1000);
    }

    private String extractProfileIdFromGeofenceId(String geofenceId) {
        // Handle different geofence ID formats
        if (geofenceId.contains("profile_")) {
            try {
                // Split by "profile_" and get the part after it
                String[] parts = geofenceId.split("profile_");
                if (parts.length > 1) {
                    // Remove timestamp if present (format: profile_123_timestamp)
                    String profilePart = parts[1];
                    if (profilePart.contains("_")) {
                        return profilePart.split("_")[0];
                    }
                    return profilePart;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error extracting profile ID: " + e.getMessage());
            }
        }
        return null; // Return null if pattern doesn't match
    }

    private void applyProfileSettings(String profileId) {
        Log.d(TAG, "Applying settings for profile: " + profileId);
        applySilentMode();
    }

    private void applySilentMode() {
        try {
            if (audioManager == null) {
                Log.e(TAG, "AudioManager is null");
                return;
            }

            // Check for Do Not Disturb permission on Android 6.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager != null && !notificationManager.isNotificationPolicyAccessGranted()) {
                    Log.e(TAG, "Do Not Disturb access not granted - trying AudioManager only");
                    // Still try to set ringer mode even without DND permission
                }
            }

            // Save current ringer mode for restoration
            int currentRingerMode = audioManager.getRingerMode();
            Log.d(TAG, "Current ringer mode: " + currentRingerMode);

            // Apply silent mode
            audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            Log.d(TAG, "Silent mode applied successfully");

            // For Android 6.0+, also try to set Do Not Disturb if we have permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager != null && notificationManager.isNotificationPolicyAccessGranted()) {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
                    Log.d(TAG, "Do Not Disturb mode applied");
                }
            }

            storePreviousRingerMode(currentRingerMode);

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when applying silent mode: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error applying silent mode: " + e.getMessage());
        }
    }

    private void revertProfileSettings(String profileId) {
        Log.d(TAG, "Reverting settings for profile: " + profileId);
        revertToNormalMode();
    }

    private void revertToNormalMode() {
        try {
            if (audioManager == null) {
                Log.e(TAG, "AudioManager is null");
                return;
            }

            // Restore previous ringer mode
            int previousRingerMode = getPreviousRingerMode();
            audioManager.setRingerMode(previousRingerMode);
            Log.d(TAG, "Ringer mode restored to: " + previousRingerMode);

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when reverting to normal mode: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error reverting to normal mode: " + e.getMessage());
        }
    }

    private void storePreviousRingerMode(int ringerMode) {
        // Store in SharedPreferences for persistence
        getSharedPreferences("profile_settings", MODE_PRIVATE)
                .edit()
                .putInt("previous_ringer_mode", ringerMode)
                .apply();
    }

    private int getPreviousRingerMode() {
        // Get from SharedPreferences, default to NORMAL if not found
        return getSharedPreferences("profile_settings", MODE_PRIVATE)
                .getInt("previous_ringer_mode", AudioManager.RINGER_MODE_NORMAL);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Profile Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Service for managing profile changes");
            channel.setSound(null, null); // Disable notification sound

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createForegroundNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Profile Service")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setSound(null) // Disable notification sound
                .setOngoing(true)
                .build();
    }

    private void updateForegroundNotification(String text) {
        if (notificationManager != null) {
            try {
                Notification notification = createForegroundNotification(text);
                notificationManager.notify(NOTIFICATION_ID, notification);
                Log.d(TAG, "Notification updated: " + text);
            } catch (Exception e) {
                Log.e(TAG, "Error updating notification: " + e.getMessage());
            }
        }
    }

    private void postDelayed(Runnable runnable, long delayMillis) {
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(runnable, delayMillis);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // We don't provide binding
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "ProfileService destroyed");
        super.onDestroy();
    }
}