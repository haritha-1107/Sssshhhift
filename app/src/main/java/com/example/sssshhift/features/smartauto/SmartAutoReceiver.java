package com.example.sssshhift.features.smartauto;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.util.Date;

public class SmartAutoReceiver extends BroadcastReceiver {
    private static final String TAG = "SmartAutoReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            Log.e(TAG, "Received null intent");
            return;
        }

        try {
            boolean toSilent = intent.getBooleanExtra("to_silent", false);
            long eventStart = intent.getLongExtra("event_start", 0);

            if (eventStart == 0) {
                Log.e(TAG, "Invalid event start time");
                return;
            }

            Log.d(TAG, "Received alarm for event starting at: " + new Date(eventStart));
            Log.d(TAG, "Action: " + (toSilent ? "Activate silent mode" : "Revert to previous mode"));

            // Change the ringer mode
            SmartAutoAlarmManager.changeRingerMode(context, toSilent, eventStart);
            
            Log.d(TAG, "Successfully " + (toSilent ? "activated silent mode" : "reverted to previous mode"));

        } catch (Exception e) {
            Log.e(TAG, "Error processing ringer mode change", e);
        }
    }
} 