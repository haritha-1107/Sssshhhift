package com.example.sssshhift.fragments;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
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

import com.example.sssshhift.MainActivity;
import com.example.sssshhift.R;
import com.example.sssshhift.activities.EditProfileActivity;
import com.example.sssshhift.adapters.ProfileAdapter;
import com.example.sssshhift.models.Profile;
import com.example.sssshhift.provider.ProfileContentProvider;
import com.example.sssshhift.utils.NotificationUtils;
import com.example.sssshhift.utils.PhoneSettingsManager;
import com.example.sssshhift.utils.ProfileUtils;
import java.util.ArrayList;
import java.util.List;

public class ProfilesFragment extends Fragment implements ProfileAdapter.OnProfileInteractionListener {

    private RecyclerView recyclerView;
    private ProfileAdapter adapter;
    private List<Profile> profileList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profiles, container, false);

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
    @Override
    public void onProfileToggle(Profile profile, boolean isActive) {
        try {
            // Update database first
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

                // Handle based on profile type
                if ("location".equals(profile.getTriggerType())) {
                    handleLocationProfileToggle(profile, isActive);
                } else if ("time".equals(profile.getTriggerType())) {
                    handleTimeProfileToggle(profile, isActive);
                }

                adapter.notifyDataSetChanged();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Error toggling profile", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleLocationProfileToggle(Profile profile, boolean isActive) {
        if (!isActive) {
            // Deactivate the profile if it was the active one
            String activeProfile = requireContext().getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
                .getString("active_location_profile", "");
            
            if (profile.getName().equals(activeProfile)) {
                // Clear the active profile
                requireContext().getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .remove("active_location_profile")
                    .apply();
                
                // Deactivate the profile settings
                PhoneSettingsManager.deactivateProfile(requireContext(), profile.getActions());
                Toast.makeText(requireContext(), "Location profile deactivated", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleTimeProfileToggle(Profile profile, boolean isActive) {
        if (isActive) {
            ProfileUtils.scheduleProfile(requireContext(), profile.getName(), true, profile.getTriggerValue(), profile.getEndTime());
            String message = "Timer profile enabled for " + profile.getTriggerValue();
            if (profile.getEndTime() != null && !profile.getEndTime().isEmpty()) {
                message += " until " + profile.getEndTime();
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        } else {
            ProfileUtils.cancelProfileAlarms(requireContext(), profile.getName());
            Toast.makeText(requireContext(), "Timer profile disabled", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onProfileEdit(Profile profile) {
        try {
            // Launch EditProfileActivity with the profile ID
            Intent editIntent = new Intent(getContext(), EditProfileActivity.class);
            editIntent.putExtra(EditProfileActivity.EXTRA_PROFILE_ID, profile.getId());
            startActivity(editIntent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Error opening edit screen: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onProfileDelete(Profile profile) {
        try {
            // First cancel any scheduled alarms
            ProfileUtils.cancelProfileAlarms(requireContext(), profile.getName());

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
    public void onResume() {
        super.onResume();
        // Refresh profiles when returning to this fragment
        loadProfiles();
    }

    @Override
    public void onProfileDetails(Profile profile) {
        try {
            // Create and show ProfileDetailsFragment
            ProfileDetailsFragment detailsFragment = ProfileDetailsFragment.newInstance(profile);

            // Navigate to details fragment
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, detailsFragment)
                        .addToBackStack("profile_details")
                        .commit();

                // Hide the FAB when viewing details
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).findViewById(R.id.fab_add_profile).setVisibility(View.GONE);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Error opening profile details: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }
}