package com.example.sssshhift.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.example.sssshhift.provider.ProfileContentProvider;
import com.example.sssshhift.models.Profile;
import com.example.sssshhift.utils.NotificationUtils;
import android.media.AudioManager;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import java.util.List;

public class GeofenceReceiver extends BroadcastReceiver {

    private static final String TAG = "GeofenceReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "GeofenceReceiver triggered");

        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent is null");
            return;
        }

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error: " + geofencingEvent.getErrorCode());
            return;
        }

        // Get the transition type
        int geofenceTransition = geofencingEvent.getGeofenceTransition();

        // Test that the reported transition was of interest
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
                geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {

            // Get the geofences that were triggered
            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();

            if (triggeringGeofences != null && !triggeringGeofences.isEmpty()) {
                for (Geofence geofence : triggeringGeofences) {
                    String geofenceId = geofence.getRequestId();
                    Log.d(TAG, "Geofence triggered: " + geofenceId + ", Transition: " + geofenceTransition);

                    // Extract profile ID from geofence ID (format: "profile_ID_timestamp")
                    String profileId = extractProfileIdFromGeofenceId(geofenceId);
                    if (profileId != null) {
                        Profile profile = getProfileById(context, profileId);
                        if (profile != null && profile.isActive()) {
                            if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                                Log.d(TAG, "Entering geofence - applying profile: " + profile.getName());
                                applyProfile(context, profile);
                                NotificationUtils.showProfileActivatedNotification(context, profile.getName());
                            } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                                Log.d(TAG, "Exiting geofence - reverting profile: " + profile.getName());
                                revertProfile(context, profile);
                                NotificationUtils.showProfileEndedNotification(context, profile.getName());
                            }
                        } else {
                            Log.w(TAG, "Profile not found or inactive: " + profileId);
                        }
                    } else {
                        Log.e(TAG, "Could not extract profile ID from geofence ID: " + geofenceId);
                    }
                }
            }
        } else {
            Log.e(TAG, "Geofence transition error: Invalid transition type: " + geofenceTransition);
        }
    }

    private String extractProfileIdFromGeofenceId(String geofenceId) {
        // Geofence ID format: "profile_ID_timestamp"
        if (geofenceId != null && geofenceId.startsWith("profile_")) {
            String[] parts = geofenceId.split("_");
            if (parts.length >= 3) {
                return parts[1]; // The profile ID part
            }
        }
        return null;
    }

    private Profile getProfileById(Context context, String profileId) {
        try {
            String selection = "_id = ?";
            String[] selectionArgs = {profileId};

            Cursor cursor = context.getContentResolver().query(
                    ProfileContentProvider.CONTENT_URI,
                    null,
                    selection,
                    selectionArgs,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                Profile profile = new Profile();
                profile.setId(cursor.getLong(cursor.getColumnIndexOrThrow("_id")));
                profile.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
                profile.setTriggerType(cursor.getString(cursor.getColumnIndexOrThrow("trigger_type")));
                profile.setTriggerValue(cursor.getString(cursor.getColumnIndexOrThrow("trigger_value")));
                profile.setRingerMode(cursor.getString(cursor.getColumnIndexOrThrow("ringer_mode")));
                profile.setActions(cursor.getString(cursor.getColumnIndexOrThrow("actions")));
                profile.setActive(cursor.getInt(cursor.getColumnIndexOrThrow("is_active")) == 1);
                profile.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));

                // Load end time if available
                int endTimeIndex = cursor.getColumnIndex("end_time");
                if (endTimeIndex != -1) {
                    profile.setEndTime(cursor.getString(endTimeIndex));
                }

                cursor.close();
                return profile;
            }

            if (cursor != null) {
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting profile from database", e);
        }
        return null;
    }

    private void applyProfile(Context context, Profile profile) {
        Log.d(TAG, "Applying profile settings: " + profile.getName());

        // Apply ringer mode
        applyRingerMode(context, profile.getRingerMode());

        // Apply other actions
        applyActions(context, profile.getActions());
    }

    private void revertProfile(Context context, Profile profile) {
        Log.d(TAG, "Reverting profile settings: " + profile.getName());

        // Revert to normal ringer mode
        applyRingerMode(context, "normal");

        // Revert other actions
        revertActions(context, profile.getActions());
    }

    private void applyRingerMode(Context context, String ringerMode) {
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

            if (audioManager != null) {
                switch (ringerMode.toLowerCase()) {
                    case "silent":
                        audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                        Log.d(TAG, "Set to silent mode");
                        break;
                    case "vibrate":
                        audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                        Log.d(TAG, "Set to vibrate mode");
                        break;
                    case "normal":
                        audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        Log.d(TAG, "Set to normal mode");
                        break;
                    default:
                        Log.w(TAG, "Unknown ringer mode: " + ringerMode);
                }
            } else {
                Log.e(TAG, "AudioManager is null!");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying ringer mode", e);
        }
    }

    private void applyActions(Context context, String actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }

        String[] actionArray = actions.split(",");

        for (String action : actionArray) {
            action = action.trim();
            switch (action.toLowerCase()) {
                case "wifi":
                    toggleWifi(context, true);
                    break;
                case "bluetooth":
                    toggleBluetooth(context, true);
                    break;
                case "data":
                    toggleMobileData(context, true);
                    break;
                case "dnd":
                    toggleDoNotDisturb(context, true);
                    break;
            }
        }
    }

    private void revertActions(Context context, String actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }

        String[] actionArray = actions.split(",");

        for (String action : actionArray) {
            action = action.trim();
            switch (action.toLowerCase()) {
                case "wifi":
                    toggleWifi(context, false);
                    break;
                case "bluetooth":
                    toggleBluetooth(context, false);
                    break;
                case "data":
                    toggleMobileData(context, false);
                    break;
                case "dnd":
                    toggleDoNotDisturb(context, false);
                    break;
            }
        }
    }

    private void toggleWifi(Context context, boolean enable) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    wifiManager.setWifiEnabled(enable);
                    Log.d(TAG, "WiFi " + (enable ? "enabled" : "disabled"));
                } else {
                    // For Android 10+, show notification to user
                    Toast.makeText(context, "Please " + (enable ? "enable" : "disable") + " WiFi manually", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error toggling WiFi", e);
        }
    }

    private void toggleBluetooth(Context context, boolean enable) {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    if (enable) {
                        bluetoothAdapter.enable();
                    } else {
                        bluetoothAdapter.disable();
                    }
                    Log.d(TAG, "Bluetooth " + (enable ? "enabled" : "disabled"));
                } else {
                    // For Android 13+, show notification to user
                    Toast.makeText(context, "Please " + (enable ? "enable" : "disable") + " Bluetooth manually", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error toggling Bluetooth", e);
        }
    }

    private void toggleMobileData(Context context, boolean enable) {
        try {
            // For modern Android, open settings
            Intent intent = new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.d(TAG, "Opened mobile data settings");
        } catch (Exception e) {
            Log.e(TAG, "Error toggling mobile data", e);
        }
    }

    private void toggleDoNotDisturb(Context context, boolean enable) {
        try {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager.isNotificationPolicyAccessGranted()) {
                    if (enable) {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
                        Log.d(TAG, "Do Not Disturb enabled");
                    } else {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
                        Log.d(TAG, "Do Not Disturb disabled");
                    }
                } else {
                    // Request DND permission
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    Log.d(TAG, "Requesting Do Not Disturb permission");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error toggling Do Not Disturb", e);
        }
    }
}