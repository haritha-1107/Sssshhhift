package com.example.sssshhift.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.example.sssshhift.services.ProfileService;
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
                    Log.d(TAG, "Geofence triggered: " + geofence.getRequestId() +
                            ", Transition: " + geofenceTransition);

                    // Start ProfileService to handle the geofence transition
                    Intent serviceIntent = new Intent(context, ProfileService.class);
                    serviceIntent.setAction(ProfileService.ACTION_GEOFENCE_TRANSITION);
                    serviceIntent.putExtra(ProfileService.EXTRA_GEOFENCE_ID, geofence.getRequestId());
                    serviceIntent.putExtra(ProfileService.EXTRA_TRANSITION_TYPE, geofenceTransition);

                    // Start as foreground service for Android 8.0+
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                }
            }
        } else {
            Log.e(TAG, "Geofence transition error: Invalid transition type: " + geofenceTransition);
        }
    }
}