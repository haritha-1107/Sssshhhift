package com.example.sssshhift.geofencing;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.example.sssshhift.receivers.GeofenceReceiver;
import java.util.ArrayList;
import java.util.List;
import com.google.android.gms.location.GeofenceStatusCodes;


public class GeofenceHelper {
    private static final String TAG = "GeofenceHelper";
    private static final float GEOFENCE_RADIUS_IN_METERS = 100f;
    private static final long GEOFENCE_EXPIRATION_TIME = Geofence.NEVER_EXPIRE;

    private GeofencingClient geofencingClient;
    private Context context;
    private PendingIntent geofencePendingIntent;

    public interface GeofenceCallback {
        void onSuccess(String message);
        void onFailure(String error);
    }

    public GeofenceHelper(Context context) {
        this.context = context;
        this.geofencingClient = LocationServices.getGeofencingClient(context);
    }

    /**
     * Creates a geofence request
     */
    private GeofencingRequest getGeofencingRequest(List<Geofence> geofenceList) {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofences(geofenceList);
        return builder.build();
    }

    /**
     * Creates a PendingIntent for geofence transitions
     */
    private PendingIntent getGeofencePendingIntent() {
        if (geofencePendingIntent != null) {
            return geofencePendingIntent;
        }
        Intent intent = new Intent(context, GeofenceReceiver.class);
        geofencePendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );
        return geofencePendingIntent;
    }

    /**
     * Creates a single geofence
     */
    public Geofence createGeofence(String geofenceId, double latitude, double longitude, float radius) {
        return new Geofence.Builder()
                .setRequestId(geofenceId)
                .setCircularRegion(latitude, longitude, radius)
                .setExpirationDuration(GEOFENCE_EXPIRATION_TIME)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();
    }

    /**
     * Creates a geofence with default radius
     */
    public Geofence createGeofence(String geofenceId, double latitude, double longitude) {
        return createGeofence(geofenceId, latitude, longitude, GEOFENCE_RADIUS_IN_METERS);
    }

    /**
     * Adds a single geofence
     */
    public void addGeofence(String geofenceId, double latitude, double longitude, GeofenceCallback callback) {
        addGeofence(geofenceId, latitude, longitude, GEOFENCE_RADIUS_IN_METERS, callback);
    }

    /**
     * Adds a single geofence with custom radius
     */
    public void addGeofence(String geofenceId, double latitude, double longitude, float radius, GeofenceCallback callback) {
        List<Geofence> geofenceList = new ArrayList<>();
        geofenceList.add(createGeofence(geofenceId, latitude, longitude, radius));
        addGeofences(geofenceList, callback);
    }

    /**
     * Adds multiple geofences
     */
    public void addGeofences(List<Geofence> geofenceList, GeofenceCallback callback) {
        try {
            geofencingClient.addGeofences(getGeofencingRequest(geofenceList), getGeofencePendingIntent())
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "Geofences added successfully");
                                if (callback != null) {
                                    callback.onSuccess("Geofences added successfully");
                                }
                            } else {
                                String error = "Failed to add geofences: " + task.getException();
                                Log.e(TAG, error);
                                if (callback != null) {
                                    callback.onFailure(error);
                                }
                            }
                        }
                    });
        } catch (SecurityException e) {
            String error = "Location permission not granted: " + e.getMessage();
            Log.e(TAG, error);
            if (callback != null) {
                callback.onFailure(error);
            }
        }
    }

    /**
     * Removes geofences by their IDs
     */
    public void removeGeofences(List<String> geofenceIds, GeofenceCallback callback) {
        geofencingClient.removeGeofences(geofenceIds)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Geofences removed successfully");
                            if (callback != null) {
                                callback.onSuccess("Geofences removed successfully");
                            }
                        } else {
                            String error = "Failed to remove geofences: " + task.getException();
                            Log.e(TAG, error);
                            if (callback != null) {
                                callback.onFailure(error);
                            }
                        }
                    }
                });
    }

    /**
     * Removes a single geofence
     */
    public void removeGeofence(String geofenceId, GeofenceCallback callback) {
        List<String> geofenceIds = new ArrayList<>();
        geofenceIds.add(geofenceId);
        removeGeofences(geofenceIds, callback);
    }

    /**
     * Removes all geofences
     */
    public void removeAllGeofences(GeofenceCallback callback) {
        geofencingClient.removeGeofences(getGeofencePendingIntent())
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "All geofences removed successfully");
                            if (callback != null) {
                                callback.onSuccess("All geofences removed successfully");
                            }
                        } else {
                            String error = "Failed to remove all geofences: " + task.getException();
                            Log.e(TAG, error);
                            if (callback != null) {
                                callback.onFailure(error);
                            }
                        }
                    }
                });
    }

    /**
     * Gets error message for geofence error codes
     */
    public static String getErrorString(int errorCode) {
        switch (errorCode) {
            case GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE:

                return "Geofence not available";
            case GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES:
                return "Too many geofences";
            case GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS:
                return "Too many pending intents";
            default:
                return "Unknown error";
        }
    }
}

