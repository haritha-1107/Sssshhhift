package com.example.sssshhift.features.smartauto;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.example.sssshhift.R;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SmartAutoSettingsFragment extends Fragment {
    private static final int CALENDAR_PERMISSION_REQUEST_CODE = 100;
    private static final String PREF_AUTO_MODE_ENABLED = "auto_mode_enabled";
    private static final String PREF_KEYWORDS = "auto_mode_keywords";
    private static final String PREF_PRE_EVENT_OFFSET = "auto_mode_pre_event_offset";
    private static final String PREF_REVERT_AFTER_EVENT = "auto_mode_revert_after_event";
    private static final String PREF_BUSY_EVENTS_ONLY = "auto_mode_busy_events_only";

    private SharedPreferences prefs;
    private SwitchMaterial switchEnableAutoMode;
    private ChipGroup chipGroupKeywords;
    private TextInputEditText editTextNewKeyword;
    private TextInputEditText editTextPreEventTime;
    private SwitchMaterial switchRevertAfterEvent;
    private SwitchMaterial switchBusyEventsOnly;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_smart_auto_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        
        initializeViews(view);
        loadSettings();
        setupListeners();
        checkCalendarPermission();

        // Add check now button
        view.findViewById(R.id.button_check_now).setOnClickListener(v -> {
            if (switchEnableAutoMode.isChecked()) {
                Log.d("SmartAutoSettings", "Forcing immediate calendar check");
                SmartAutoWorker.scheduleWork(requireContext());
                Toast.makeText(requireContext(), "Checking calendar events...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Please enable Smart Auto Mode first", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initializeViews(View view) {
        switchEnableAutoMode = view.findViewById(R.id.switch_enable_auto_mode);
        chipGroupKeywords = view.findViewById(R.id.chip_group_keywords);
        editTextNewKeyword = view.findViewById(R.id.edit_text_new_keyword);
        editTextPreEventTime = view.findViewById(R.id.edit_text_pre_event_time);
        switchRevertAfterEvent = view.findViewById(R.id.switch_revert_after_event);
        switchBusyEventsOnly = view.findViewById(R.id.switch_busy_events_only);
    }

    private void loadSettings() {
        // Load switch states
        switchEnableAutoMode.setChecked(prefs.getBoolean(PREF_AUTO_MODE_ENABLED, false));
        switchRevertAfterEvent.setChecked(prefs.getBoolean(PREF_REVERT_AFTER_EVENT, true));
        switchBusyEventsOnly.setChecked(prefs.getBoolean(PREF_BUSY_EVENTS_ONLY, true));

        // Load pre-event time
        editTextPreEventTime.setText(String.valueOf(prefs.getInt(PREF_PRE_EVENT_OFFSET, 5)));

        // Load keywords
        Set<String> keywords = prefs.getStringSet(PREF_KEYWORDS, null);
        if (keywords == null || keywords.isEmpty()) {
            // Add default keywords if none exist
            keywords = new HashSet<>();
            keywords.add("meeting");
            keywords.add("team");
            prefs.edit().putStringSet(PREF_KEYWORDS, keywords).apply();
        }

        // Clear existing chips and add loaded keywords
        chipGroupKeywords.removeAllViews();
        for (String keyword : keywords) {
            addKeywordChip(keyword);
        }

        Log.d("SmartAutoSettings", "Loaded keywords: " + keywords);
    }

    private void setupListeners() {
        switchEnableAutoMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PREF_AUTO_MODE_ENABLED, isChecked).apply();
            if (isChecked) {
                SmartAutoWorker.scheduleWork(requireContext());
            } else {
                SmartAutoWorker.cancelWork(requireContext());
            }
        });

        editTextNewKeyword.setOnEditorActionListener((v, actionId, event) -> {
            String keyword = editTextNewKeyword.getText().toString().trim();
            if (!TextUtils.isEmpty(keyword)) {
                addKeywordChip(keyword);
                editTextNewKeyword.setText("");
                saveKeywords();
                return true;
            }
            return false;
        });

        editTextPreEventTime.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    int minutes = Integer.parseInt(editTextPreEventTime.getText().toString());
                    prefs.edit().putInt(PREF_PRE_EVENT_OFFSET, minutes).apply();
                } catch (NumberFormatException e) {
                    editTextPreEventTime.setText("5");
                    prefs.edit().putInt(PREF_PRE_EVENT_OFFSET, 5).apply();
                }
            }
        });

        switchRevertAfterEvent.setOnCheckedChangeListener((buttonView, isChecked) ->
            prefs.edit().putBoolean(PREF_REVERT_AFTER_EVENT, isChecked).apply());

        switchBusyEventsOnly.setOnCheckedChangeListener((buttonView, isChecked) ->
            prefs.edit().putBoolean(PREF_BUSY_EVENTS_ONLY, isChecked).apply());
    }

    private void addKeywordChip(String keyword) {
        if (TextUtils.isEmpty(keyword)) return;
        
        // Convert to lowercase for consistency
        keyword = keyword.toLowerCase().trim();
        
        // Check if chip already exists
        for (int i = 0; i < chipGroupKeywords.getChildCount(); i++) {
            Chip existingChip = (Chip) chipGroupKeywords.getChildAt(i);
            if (existingChip.getText().toString().equalsIgnoreCase(keyword)) {
                return; // Skip if keyword already exists
            }
        }

        Chip chip = new Chip(requireContext());
        chip.setText(keyword);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> {
            chipGroupKeywords.removeView(chip);
            saveKeywords();
        });
        chipGroupKeywords.addView(chip);
        Log.d("SmartAutoSettings", "Added keyword chip: " + keyword);
    }

    private void saveKeywords() {
        Set<String> keywords = new HashSet<>();
        for (int i = 0; i < chipGroupKeywords.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupKeywords.getChildAt(i);
            keywords.add(chip.getText().toString().toLowerCase().trim());
        }
        prefs.edit().putStringSet(PREF_KEYWORDS, keywords).apply();
        Log.d("SmartAutoSettings", "Saved keywords: " + keywords);
    }

    private void checkCalendarPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.READ_CALENDAR},
                    CALENDAR_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CALENDAR_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (switchEnableAutoMode.isChecked()) {
                    SmartAutoWorker.scheduleWork(requireContext());
                }
            } else {
                Toast.makeText(requireContext(), "Calendar permission is required for Smart Auto Mode", 
                        Toast.LENGTH_LONG).show();
                switchEnableAutoMode.setChecked(false);
            }
        }
    }
} 