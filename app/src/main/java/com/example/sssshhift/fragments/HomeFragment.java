package com.example.sssshhift.fragments;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.sssshhift.R;
import com.example.sssshhift.adapters.ProfileAdapter;
import com.example.sssshhift.models.Profile;
import com.example.sssshhift.provider.ProfileContentProvider;
import com.example.sssshhift.utils.NotificationUtils;
import com.example.sssshhift.utils.ProfileUtils;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements ProfileAdapter.OnProfileInteractionListener {

    private RecyclerView recyclerView;
    private ProfileAdapter adapter;
    private List<Profile> profileList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        initViews(view);
        loadProfiles();

        return view;
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.profiles_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        profileList = new ArrayList<>();
        adapter = new ProfileAdapter(profileList, this);
        recyclerView.setAdapter(adapter);
    }

    private void loadProfiles() {
        profileList.clear();

        try {
            Cursor cursor = requireContext().getContentResolver().query(
                    ProfileContentProvider.CONTENT_URI,
                    null,
                    null,
                    null,
                    "created_at DESC"
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
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

                    profileList.add(profile);
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Error loading profiles", Toast.LENGTH_SHORT).show();
        }

        adapter.notifyDataSetChanged();
    }

    public void refreshProfiles() {
        loadProfiles();
    }

    // FIXED: Toggle only updates database status and schedules/cancels alarms (NO immediate action)
// FIXED: Update HomeFragment onProfileToggle to handle geofences

    @Override
    public void onProfileToggle(Profile profile, boolean isActive) {
        try {
            // Update database status
            ContentValues values = new ContentValues();
            values.put("is_active", isActive ? 1 : 0);

            String selection = "_id = ?";
            String[] selectionArgs = {String.valueOf(profile.getId())};

            int updatedRows = requireContext().getContentResolver().update(
                    ProfileContentProvider.CONTENT_URI,
                    values,
                    selection,
                    selectionArgs
            );

            if (updatedRows > 0) {
                // Update local profile object
                profile.setActive(isActive);

                if (isActive) {
                    // Register/schedule the profile based on type
                    if ("time".equals(profile.getTriggerType())) {
                        // Time-based: Schedule alarms
                        ProfileUtils.scheduleProfile(requireContext(), profile.getName(), true, profile.getTriggerValue(), profile.getEndTime());
                    } else if ("location".equals(profile.getTriggerType())) {
                        // Location-based: Register geofences
                        ProfileUtils.scheduleProfile(requireContext(), profile.getName(), false, profile.getTriggerValue(), profile.getEndTime());
                    }

                    Toast.makeText(getContext(), "Profile '" + profile.getName() + "' enabled", Toast.LENGTH_SHORT).show();
                } else {
                    // Cancel/unregister the profile based on type
                    if ("time".equals(profile.getTriggerType())) {
                        // Time-based: Cancel alarms
                        ProfileUtils.cancelProfile(requireContext(), profile.getName());
                    } else if ("location".equals(profile.getTriggerType())) {
                        // Location-based: Remove geofences
                        ProfileUtils.cancelLocationProfile(requireContext(), profile.getName());
                    }

                    Toast.makeText(getContext(), "Profile '" + profile.getName() + "' disabled", Toast.LENGTH_SHORT).show();
                }

                // Refresh the adapter to update UI
                adapter.notifyDataSetChanged();
            } else {
                Toast.makeText(getContext(), "Failed to update profile", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Error updating profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onProfileEdit(Profile profile) {
        // TODO: Implement edit functionality
        Toast.makeText(getContext(), "Edit functionality not implemented yet", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProfileDelete(Profile profile) {
        try {
            // First cancel any scheduled alarms
            ProfileUtils.cancelProfile(requireContext(), profile.getName());

            // Then delete from database
            String selection = "_id = ?";
            String[] selectionArgs = {String.valueOf(profile.getId())};

            int deletedRows = requireContext().getContentResolver().delete(
                    ProfileContentProvider.CONTENT_URI,
                    selection,
                    selectionArgs
            );

            if (deletedRows > 0) {
                Toast.makeText(getContext(), "Profile deleted: " + profile.getName(), Toast.LENGTH_SHORT).show();
                loadProfiles(); // Refresh the list
            } else {
                Toast.makeText(getContext(), "Failed to delete profile", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Error deleting profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onProfileDetails(Profile profile) {
        // TODO: Implement details functionality
        Toast.makeText(getContext(), "Details functionality not implemented yet", Toast.LENGTH_SHORT).show();
    }
}