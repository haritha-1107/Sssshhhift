package com.example.sssshhift.timer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class TimerBootReceiver extends BroadcastReceiver {
    private static final String PREFS_NAME = "TimerProfiles";
    private static final String PROFILES_KEY = "active_profiles";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && 
            intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            
            restoreTimerProfiles(context);
        }
    }

    private void restoreTimerProfiles(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String profilesJson = prefs.getString(PROFILES_KEY, "[]");

        try {
            JSONArray profiles = new JSONArray(profilesJson);
            TimerProfileManager profileManager = new TimerProfileManager(context);

            for (int i = 0; i < profiles.length(); i++) {
                JSONObject profile = profiles.getJSONObject(i);
                long startTime = profile.getLong("startTime");
                long endTime = profile.getLong("endTime");
                int ringerMode = profile.getInt("ringerMode");
                String profileName = profile.getString("name");

                // Only restore if the end time hasn't passed
                if (endTime > System.currentTimeMillis()) {
                    profileManager.scheduleProfile(startTime, endTime, ringerMode, profileName);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // Static methods to save/remove profiles
    public static void saveProfile(Context context, long startTime, long endTime, 
                                 int ringerMode, String profileName) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String profilesJson = prefs.getString(PROFILES_KEY, "[]");

        try {
            JSONArray profiles = new JSONArray(profilesJson);
            JSONObject newProfile = new JSONObject();
            newProfile.put("startTime", startTime);
            newProfile.put("endTime", endTime);
            newProfile.put("ringerMode", ringerMode);
            newProfile.put("name", profileName);

            profiles.put(newProfile);
            prefs.edit().putString(PROFILES_KEY, profiles.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void removeProfile(Context context, long startTime, long endTime) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String profilesJson = prefs.getString(PROFILES_KEY, "[]");

        try {
            JSONArray profiles = new JSONArray(profilesJson);
            JSONArray newProfiles = new JSONArray();

            for (int i = 0; i < profiles.length(); i++) {
                JSONObject profile = profiles.getJSONObject(i);
                if (profile.getLong("startTime") != startTime || 
                    profile.getLong("endTime") != endTime) {
                    newProfiles.put(profile);
                }
            }

            prefs.edit().putString(PROFILES_KEY, newProfiles.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
} 