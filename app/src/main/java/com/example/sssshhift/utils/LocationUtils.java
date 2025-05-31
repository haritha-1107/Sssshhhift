package com.example.sssshhift.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.Task;
import java.util.List;
import java.util.ArrayList;

public class LocationUtils {

    // Permission request codes
    public static final int REQUEST_LOCATION_PERMISSIONS = 1001;
    public static final int REQUEST_BACKGROUND_LOCATION_PERMISSION = 1002;

    // Required permissions for different Android versions
    private static final String[] LOCATION_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private static final String BACKGROUND_LOCATION_PERMISSION =
            Manifest.permission.ACCESS_BACKGROUND_LOCATION;

    /**
     * Check if basic location permissions are granted
     */
    public static boolean hasLocationPermissions(Context context) {
        for (String permission : LOCATION_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if background location permission is granted (Android 10+)
     */
    public static boolean hasBackgroundLocationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(context, BACKGROUND_LOCATION_PERMISSION)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Not required for older Android versions
    }

    /**
     * Check if all required location permissions are granted
     */
    public static boolean hasAllLocationPermissions(Context context) {
        return hasLocationPermissions(context) && hasBackgroundLocationPermission(context);
    }

    /**
     * Request basic location permissions
     */
    public static void requestLocationPermissions(Activity activity) {
        ActivityCompat.requestPermissions(
                activity,
                LOCATION_PERMISSIONS,
                REQUEST_LOCATION_PERMISSIONS
        );
    }

    /**
     * Request background location permission (should be called after basic permissions are granted)
     */
    public static void requestBackgroundLocationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{BACKGROUND_LOCATION_PERMISSION},
                    REQUEST_BACKGROUND_LOCATION_PERMISSION
            );
        }
    }

    /**
     * Get list of denied location permissions
     */
    public static String[] getDeniedLocationPermissions(Context context) {
        List<String> deniedPermissions = new ArrayList<>();

        for (String permission : LOCATION_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                deniedPermissions.add(permission);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !hasBackgroundLocationPermission(context)) {
            deniedPermissions.add(BACKGROUND_LOCATION_PERMISSION);
        }

        return deniedPermissions.toArray(new String[0]);
    }

    /**
     * Check if location services are enabled
     */
    public static boolean isLocationEnabled(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager != null &&
                (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    /**
     * Check if the app should show rationale for location permissions
     */
    public static boolean shouldShowLocationPermissionRationale(Activity activity) {
        for (String permission : LOCATION_PERMISSIONS) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the app should show rationale for background location permission
     */
    public static boolean shouldShowBackgroundLocationPermissionRationale(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, BACKGROUND_LOCATION_PERMISSION);
        }
        return false;
    }

    /**
     * Calculate distance between two points in meters
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth's radius in meters

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * Format distance for display
     */
    public static String formatDistance(double distanceInMeters) {
        if (distanceInMeters < 1000) {
            return String.format("%.0f m", distanceInMeters);
        } else {
            return String.format("%.1f km", distanceInMeters / 1000);
        }
    }

    /**
     * Check if coordinates are valid
     */
    public static boolean areCoordinatesValid(double latitude, double longitude) {
        return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    public static Location getLastKnownLocation(Context context) {
        if (!hasLocationPermissions(context)) {
            return null;
        }

        try {
            FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
            Task<Location> task = fusedLocationClient.getLastLocation();
            
            // Wait for the result synchronously (not recommended for production)
            while (!task.isComplete()) {
                Thread.sleep(100);
            }
            
            return task.getResult();
        } catch (SecurityException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static float calculateDistance(LatLng point1, LatLng point2) {
        float[] results = new float[1];
        Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        );
        return results[0];
    }

    public static boolean isInGeofence(LatLng currentLocation, LatLng center, float radiusMeters) {
        return calculateDistance(currentLocation, center) <= radiusMeters;
    }
}