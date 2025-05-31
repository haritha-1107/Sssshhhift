package com.example.sssshhift.features.smartauto;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.preference.PreferenceManager;

public class SmartAutoBootReceiver extends BroadcastReceiver {
    private static final String TAG = "SmartAutoBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.d(TAG, "Received boot completed broadcast");
            
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean isEnabled = prefs.getBoolean("auto_mode_enabled", false);
            
            if (isEnabled) {
                Log.d(TAG, "Smart Auto Mode is enabled, scheduling work");
                SmartAutoWorker.scheduleWork(context);
            } else {
                Log.d(TAG, "Smart Auto Mode is disabled, not scheduling work");
            }
        }
    }
} 