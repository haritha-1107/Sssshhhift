package com.example.sssshhift;

import static android.content.ContentValues.TAG;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.sssshhift.geofencing.GeofenceManager;
import com.example.sssshhift.utils.PermissionHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.example.sssshhift.R;
import com.example.sssshhift.fragments.HomeFragment;
import com.example.sssshhift.fragments.SettingsFragment;
import com.example.sssshhift.utils.PermissionUtils;
import com.example.sssshhift.utils.LocationUtils;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    private BottomNavigationView bottomNavigationView;
    private FloatingActionButton fabAddProfile;
    private FragmentManager fragmentManager;

    // Fragments
    private HomeFragment homeFragment;
    private SettingsFragment settingsFragment;
    private GeofenceManager geofenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);



        setContentView(R.layout.activity_main);

        initViews();
        setupFragments();
        setupFab();
        checkPermissions();

        // Set default fragment
        if (savedInstanceState == null) {
            showHomeFragment();
        }
        geofenceManager = GeofenceManager.getInstance(this);
        checkPermissions();

        // Example usage: Add a geofence for silent mode
        addSilentZoneGeofence();


        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !notificationManager.isNotificationPolicyAccessGranted()) {

            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            startActivity(intent);
        }


    }

    private void initViews() {
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        fabAddProfile = findViewById(R.id.fab_add_profile);
        fragmentManager = getSupportFragmentManager();

        bottomNavigationView.setOnNavigationItemSelectedListener(this);
    }

    private void setupFragments() {
        homeFragment = new HomeFragment();
        settingsFragment = new SettingsFragment();
    }

    private void setupFab() {
        fabAddProfile.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, com.example.sssshhift.activities.AddProfileActivity.class);
            startActivity(intent);
        });
    }

    private void checkPermissions() {
        // Check notification policy permission
        if (!PermissionUtils.hasNotificationPolicyPermission(this)) {
            PermissionUtils.requestNotificationPolicyPermission(this);
        }

        // Check location permissions
        checkLocationPermissions();
    }

    private void addSilentZoneGeofence() {
        if (!PermissionHelper.areAllPermissionsGranted(this)) {
            Toast.makeText(this, "Please grant all required permissions first", Toast.LENGTH_LONG).show();
            return;
        }

        // Example: Add geofence for office location
        double latitude = 12.9716; // Bangalore coordinates (example)
        double longitude = 77.5946;
        float radius = 100; // 100 meters

        geofenceManager.addProfileGeofence(
                "office_profile", // profile ID
                "Office Location", // location name
                latitude,
                longitude,
                radius,
                new GeofenceManager.GeofenceManagerCallback() {
                    @Override
                    public void onGeofenceAdded(String geofenceId, boolean success, String message) {
                        Log.d(TAG, "Geofence added - ID: " + geofenceId + ", Success: " + success + ", Message: " + message);
                        if (success) {
                            Toast.makeText(MainActivity.this, "Silent zone added successfully!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Failed to add silent zone: " + message, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onGeofenceRemoved(String geofenceId, boolean success, String message) {
                        Log.d(TAG, "Geofence removed - ID: " + geofenceId + ", Success: " + success);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Geofence error: " + error);
                        Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }



    private void checkLocationPermissions() {
        if (!LocationUtils.hasLocationPermissions(this)) {
            // Show rationale if needed
            if (LocationUtils.shouldShowLocationPermissionRationale(this)) {
                showLocationPermissionRationale();
            } else {
                LocationUtils.requestLocationPermissions(this);
            }
        } else if (!LocationUtils.hasBackgroundLocationPermission(this)) {
            // Basic permissions granted, now request background location
            showBackgroundLocationPermissionRationale();
        }
    }

    private void showLocationPermissionRationale() {
        new AlertDialog.Builder(this)
                .setTitle("Location Permission Required")
                .setMessage("This app needs location access to automatically switch profiles based on your location. " +
                        "This helps provide a seamless experience by activating the right profile when you arrive at specific places.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    LocationUtils.requestLocationPermissions(this);
                })
                .setNegativeButton("Not Now", (dialog, which) -> {
                    Toast.makeText(this, "Location features will not be available", Toast.LENGTH_LONG).show();
                })
                .setCancelable(false)
                .show();
    }

    private void showBackgroundLocationPermissionRationale() {
        new AlertDialog.Builder(this)
                .setTitle("Background Location Access")
                .setMessage("To automatically switch profiles even when the app is closed, we need background location access. " +
                        "This ensures your profiles activate correctly when you enter or leave designated areas.")
                .setPositiveButton("Allow", (dialog, which) -> {
                    LocationUtils.requestBackgroundLocationPermission(this);
                })
                .setNegativeButton("Skip", (dialog, which) -> {
                    Toast.makeText(this, "Profile switching will only work when app is open", Toast.LENGTH_LONG).show();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case LocationUtils.REQUEST_LOCATION_PERMISSIONS:
                handleLocationPermissionResult(grantResults);
                break;
            case LocationUtils.REQUEST_BACKGROUND_LOCATION_PERMISSION:
                handleBackgroundLocationPermissionResult(grantResults);
                break;
        }
    }

    private void handleLocationPermissionResult(int[] grantResults) {
        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            Toast.makeText(this, "Location permissions granted", Toast.LENGTH_SHORT).show();
            // Now request background location permission
            if (!LocationUtils.hasBackgroundLocationPermission(this)) {
                showBackgroundLocationPermissionRationale();
            }
        } else {
            Toast.makeText(this, "Location permissions denied. Location features will not work.", Toast.LENGTH_LONG).show();
        }
    }

    private void handleBackgroundLocationPermissionResult(int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Background location access granted", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Background location denied. Profiles will only switch when app is open.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.nav_home) {
            showHomeFragment();
            return true;
        } else if (itemId == R.id.nav_settings) {
            showSettingsFragment();
            return true;
        }

        return false;
    }

    private void showHomeFragment() {
        showFragment(homeFragment, "HOME");
        bottomNavigationView.getMenu().findItem(R.id.nav_home).setChecked(true);
        fabAddProfile.show();
    }

    private void showSettingsFragment() {
        showFragment(settingsFragment, "SETTINGS");
        bottomNavigationView.getMenu().findItem(R.id.nav_settings).setChecked(true);
        fabAddProfile.hide();
    }

    private void showFragment(Fragment fragment, String tag) {
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        // Hide all fragments first
        if (homeFragment != null && homeFragment.isAdded()) {
            transaction.hide(homeFragment);
        }
        if (settingsFragment != null && settingsFragment.isAdded()) {
            transaction.hide(settingsFragment);
        }

        // Show or add the target fragment
        if (fragment.isAdded()) {
            transaction.show(fragment);
        } else {
            transaction.add(R.id.fragment_container, fragment, tag);
        }

        transaction.commit();
    }

    @Override
    public void onBackPressed() {
        // If not on home fragment, go to home
        if (bottomNavigationView.getSelectedItemId() != R.id.nav_home) {
            showHomeFragment();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the home fragment to show updated profiles
        if (homeFragment != null && homeFragment.isVisible()) {
            homeFragment.refreshProfiles();
        }
    }




}