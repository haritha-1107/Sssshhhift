package com.example.sssshhift.utils;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;

public class PermissionHelper {

    private static final String TAG = "PermissionHelper";

    /**
     * Check if Do Not Disturb permission is granted
     */
    public static boolean isDoNotDisturbPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            return notificationManager != null && notificationManager.isNotificationPolicyAccessGranted();
        }
        return true; // Not required for older versions
    }

    /**
     * Request Do Not Disturb permission with explanation dialog
     */
    public static void requestDoNotDisturbPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isDoNotDisturbPermissionGranted(activity)) {
                showDoNotDisturbPermissionDialog(activity);
            }
        }
    }

    /**
     * Show dialog explaining Do Not Disturb permission requirement
     */
    private static void showDoNotDisturbPermissionDialog(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Do Not Disturb Permission Required")
                .setMessage("This app needs access to Do Not Disturb settings to automatically " +
                        "change your phone to silent mode when you enter specific locations. " +
                        "Please grant this permission in the next screen.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    openDoNotDisturbSettings(activity);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Log.w(TAG, "User cancelled Do Not Disturb permission request");
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Open Do Not Disturb settings page
     */
    public static void openDoNotDisturbSettings(Activity activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                activity.startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening Do Not Disturb settings: " + e.getMessage());
        }
    }

    /**
     * Check all required permissions for location-based profiles
     */
    public static boolean areAllPermissionsGranted(Context context) {
        return LocationUtils.hasAllLocationPermissions(context) &&
                isDoNotDisturbPermissionGranted(context);
    }

    /**
     * Show comprehensive permission request dialog
     */
    public static void showPermissionRequiredDialog(Activity activity) {
        StringBuilder message = new StringBuilder();
        message.append("This app requires the following permissions to work properly:\n\n");

        if (!LocationUtils.hasLocationPermissions(activity)) {
            message.append("• Location Permission - To detect when you enter/exit specific areas\n");
        }

        if (!LocationUtils.hasBackgroundLocationPermission(activity)) {
            message.append("• Background Location - To work even when the app is closed\n");
        }

        if (!isDoNotDisturbPermissionGranted(activity)) {
            message.append("• Do Not Disturb Access - To automatically change sound settings\n");
        }

        new AlertDialog.Builder(activity)
                .setTitle("Permissions Required")
                .setMessage(message.toString())
                .setPositiveButton("Grant Permissions", (dialog, which) -> {
                    // Request location permissions first
                    if (!LocationUtils.hasLocationPermissions(activity)) {
                        LocationUtils.requestLocationPermissions(activity);
                    } else if (!LocationUtils.hasBackgroundLocationPermission(activity)) {
                        LocationUtils.requestBackgroundLocationPermission(activity);
                    } else if (!isDoNotDisturbPermissionGranted(activity)) {
                        requestDoNotDisturbPermission(activity);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}