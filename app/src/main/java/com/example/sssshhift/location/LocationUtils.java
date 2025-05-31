package com.example.sssshhift.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;

public class LocationUtils {
    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private static final String[] BACKGROUND_LOCATION_PERMISSION = {
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    };

    public static boolean checkLocationPermissions(Context context) {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        // Check for background location permission on Android 10 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }

    public static Location getLastKnownLocation(Context context) {
        if (!checkLocationPermissions(context)) {
            return null;
        }

        try {
            Task<Location> task = LocationServices.getFusedLocationProviderClient(context)
                .getLastLocation();
            
            // Wait for the result synchronously (not recommended for UI thread)
            while (!task.isComplete()) {
                Thread.sleep(100);
            }
            
            return task.getResult();
        } catch (Exception e) {
            return null;
        }
    }

    public static String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String[] permissions = new String[REQUIRED_PERMISSIONS.length + BACKGROUND_LOCATION_PERMISSION.length];
            System.arraycopy(REQUIRED_PERMISSIONS, 0, permissions, 0, REQUIRED_PERMISSIONS.length);
            System.arraycopy(BACKGROUND_LOCATION_PERMISSION, 0, permissions, REQUIRED_PERMISSIONS.length, BACKGROUND_LOCATION_PERMISSION.length);
            return permissions;
        }
        return REQUIRED_PERMISSIONS;
    }
} 