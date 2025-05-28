package com.example.sssshhift.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.Geofence;
import com.example.sssshhift.R;
import com.example.sssshhift.data.ProfileDatabaseHelper;

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
    private ProfileDatabaseHelper dbHelper;
    private Handler handler;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ProfileService created");

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        dbHelper = new ProfileDatabaseHelper(this);
        handler = new Handler();

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
        String profileName = extractProfileNameFromGeofenceId(geofenceId);
        if (profileName != null) {
            // Check if profile is active in database
            if (isProfileActive(profileName)) {
                applyProfileSettings(profileName);
                updateForegroundNotification("Profile activated: " + profileName);
            } else {
                Log.d(TAG, "Profile " + profileName + " is not active, skipping");
                updateForegroundNotification("Profile " + profileName + " is inactive");
            }
        } else {
            Log.w(TAG, "Could not extract profile name from: " + geofenceId);
            updateForegroundNotification("Unknown geofence entered");
        }
    }

    private void handleGeofenceExit(String geofenceId) {
        Log.d(TAG, "Exited geofence: " + geofenceId);

        // Extract profile ID from geofence ID
        String profileName = extractProfileNameFromGeofenceId(geofenceId);
        if (profileName != null) {
            // Check if profile is active in database
            if (isProfileActive(profileName)) {
                revertProfileSettings(profileName);
                updateForegroundNotification("Profile deactivated: " + profileName);
            } else {
                Log.d(TAG, "Profile " + profileName + " is not active, skipping");
                updateForegroundNotification("Profile " + profileName + " is inactive");
            }
        } else {
            Log.w(TAG, "Could not extract profile name from: " + geofenceId);
            updateForegroundNotification("Unknown geofence exited");
        }
    }

    private void handleApplyProfile(Intent intent) {
        String profileId = intent.getStringExtra(EXTRA_PROFILE_ID);
        Log.d(TAG, "Applying profile: " + profileId);

        if (profileId != null) {
            // Check if profile is active in database
            if (isProfileActive(profileId)) {
                applyProfileSettings(profileId);
                updateForegroundNotification("Applied profile: " + profileId);
            } else {
                Log.d(TAG, "Profile " + profileId + " is not active, skipping");
                updateForegroundNotification("Profile " + profileId + " is inactive");
            }
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

    private String extractProfileNameFromGeofenceId(String geofenceId) {
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
                Log.e(TAG, "Error extracting profile name: " + e.getMessage());
            }
        }
        return null; // Return null if pattern doesn't match
    }

    private boolean isProfileActive(String profileName) {
        try {
            Cursor cursor = dbHelper.getReadableDatabase().query(
                    "profiles",
                    new String[]{"is_active"},
                    "name = ?",
                    new String[]{profileName},
                    null, null, null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int isActiveIndex = cursor.getColumnIndex("is_active");
                if (isActiveIndex != -1) {
                    boolean isActive = cursor.getInt(isActiveIndex) == 1;
                    cursor.close();
                    Log.d(TAG, "Profile " + profileName + " active status: " + isActive);
                    return isActive;
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking profile active status: " + e.getMessage(), e);
        }

        Log.d(TAG, "Profile " + profileName + " not found or error occurred, assuming inactive");
        return false;
    }

    private void applyProfileSettings(String profileName) {
        Log.d(TAG, "Applying settings for profile: " + profileName);

        try {
            // Get profile settings from database
            Cursor cursor = dbHelper.getReadableDatabase().query(
                    "profiles",
                    new String[]{"silent_mode", "vibration_mode", "do_not_disturb"},
                    "name = ?",
                    new String[]{profileName},
                    null, null, null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int silentModeIndex = cursor.getColumnIndex("silent_mode");
                int vibrationModeIndex = cursor.getColumnIndex("vibration_mode");
                int dndModeIndex = cursor.getColumnIndex("do_not_disturb");

                if (silentModeIndex != -1) {
                    boolean silentMode = cursor.getInt(silentModeIndex) == 1;
                    boolean vibrationMode = vibrationModeIndex != -1 ? cursor.getInt(vibrationModeIndex) == 1 : false;
                    boolean dndMode = dndModeIndex != -1 ? cursor.getInt(dndModeIndex) == 1 : false;

                    Log.d(TAG, "Profile settings - Silent: " + silentMode + ", Vibration: " + vibrationMode + ", DND: " + dndMode);

                    if (silentMode) {
                        applySilentMode();
                    } else if (vibrationMode) {
                        applyVibrationMode();
                    }

                    if (dndMode) {
                        applyDoNotDisturbMode();
                    }
                }
                cursor.close();
            } else {
                Log.w(TAG, "Profile " + profileName + " not found in database, applying default silent mode");
                applySilentMode();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting profile settings: " + e.getMessage(), e);
            // Fallback to silent mode
            applySilentMode();
        }
    }

    private void applySilentMode() {
        try {
            if (audioManager == null) {
                Log.e(TAG, "AudioManager is null");
                return;
            }

            // Save current ringer mode for restoration
            int currentRingerMode = audioManager.getRingerMode();
            Log.d(TAG, "Current ringer mode: " + currentRingerMode);

            // Apply silent mode
            audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            Log.d(TAG, "Silent mode applied successfully");

            storePreviousRingerMode(currentRingerMode);

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when applying silent mode: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error applying silent mode: " + e.getMessage());
        }
    }

    private void applyVibrationMode() {
        try {
            if (audioManager == null) {
                Log.e(TAG, "AudioManager is null");
                return;
            }

            // Save current ringer mode for restoration
            int currentRingerMode = audioManager.getRingerMode();
            Log.d(TAG, "Current ringer mode: " + currentRingerMode);

            // Apply vibration mode
            audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
            Log.d(TAG, "Vibration mode applied successfully");

            storePreviousRingerMode(currentRingerMode);

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when applying vibration mode: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error applying vibration mode: " + e.getMessage());
        }
    }

    private void applyDoNotDisturbMode() {
        try {
            // For Android 6.0+, set Do Not Disturb if we have permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager != null && notificationManager.isNotificationPolicyAccessGranted()) {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
                    Log.d(TAG, "Do Not Disturb mode applied");
                } else {
                    Log.w(TAG, "Do Not Disturb access not granted");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying Do Not Disturb mode: " + e.getMessage());
        }
    }

    private void revertProfileSettings(String profileName) {
        Log.d(TAG, "Reverting settings for profile: " + profileName);
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

            // Restore Do Not Disturb mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager != null && notificationManager.isNotificationPolicyAccessGranted()) {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
                    Log.d(TAG, "Do Not Disturb mode restored");
                }
            }

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
        if (handler != null) {
            handler.postDelayed(runnable, delayMillis);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "ProfileService destroyed");
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}