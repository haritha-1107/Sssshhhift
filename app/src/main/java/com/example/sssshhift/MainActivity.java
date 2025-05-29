package com.example.sssshhift;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.app.NotificationManager;
import com.example.sssshhift.fragments.ProfilesFragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.sssshhift.geofencing.GeofenceManager;
import com.example.sssshhift.services.CalendarMonitorService;
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
    private ProfilesFragment profilesFragment;

    // Fragments
    private HomeFragment homeFragment;
    private SettingsFragment settingsFragment;
    private GeofenceManager geofenceManager;

    // Permission tracking
    private boolean isFirstTimeSetup = false;
    private SharedPreferences prefs;

    // Calendar permission constants
    private static final int REQUEST_CALENDAR_PERMISSION = 100;
    private static final int REQUEST_DND_PERMISSION = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

        // Check if this is first time setup after onboarding
        isFirstTimeSetup = getIntent().getBooleanExtra("request_permissions_on_start", false);

        initViews();
        setupFragments();
        setupFab();

        // Set default fragment
        if (savedInstanceState == null) {
            showHomeFragment();
        }

        geofenceManager = GeofenceManager.getInstance(this);

        // Only request permissions if this is first time setup or permissions are missing
        if (isFirstTimeSetup || !areEssentialPermissionsGranted()) {
            // Start permission flow after a short delay to let UI settle
            bottomNavigationView.postDelayed(this::startPermissionFlow, 500);
        } else {
            // All permissions already granted, setup services
            setupServicesIfReady();
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
        profilesFragment = new ProfilesFragment();
    }

    private void setupFab() {
        fabAddProfile.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, com.example.sssshhift.activities.AddProfileActivity.class);
            startActivity(intent);
        });
    }

    private boolean areEssentialPermissionsGranted() {
        // Check if basic required permissions are granted
        boolean hasNotificationPolicy = PermissionUtils.hasNotificationPolicyPermission(this);
        boolean hasLocation = LocationUtils.hasLocationPermissions(this);
        boolean hasCalendar = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;

        return hasNotificationPolicy && hasLocation && hasCalendar;
    }

    private void startPermissionFlow() {
        // Start with a welcome dialog explaining permissions
        showPermissionWelcomeDialog();
    }

    private void showPermissionWelcomeDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Welcome to Sssshhhift!")
                .setMessage("To provide you with the best experience, we need to set up a few permissions. " +
                        "These allow the app to automatically manage your phone's settings based on your location, " +
                        "calendar events, and other triggers.")
                .setPositiveButton("Let's Get Started", (dialog, which) -> {
                    checkAllPermissions();
                })
                .setNegativeButton("Maybe Later", (dialog, which) -> {
                    Toast.makeText(this, "You can enable permissions anytime in Settings", Toast.LENGTH_LONG).show();
                })
                .setCancelable(false)
                .show();
    }

    private void checkAllPermissions() {
        // Check notification policy permission first
        if (!PermissionUtils.hasNotificationPolicyPermission(this)) {
            requestNotificationPolicyPermission();
        } else {
            // Move to location permissions
            checkLocationPermissions();
        }
    }

    private void requestNotificationPolicyPermission() {
        new AlertDialog.Builder(this)
                .setTitle("Do Not Disturb Access Required")
                .setMessage("This app needs access to Do Not Disturb settings to automatically control silent mode " +
                        "during calendar events and in specific locations.")
                .setPositiveButton("Grant Access", (dialog, which) -> {
                    PermissionUtils.requestNotificationPolicyPermission(this);
                })
                .setNegativeButton("Skip", (dialog, which) -> {
                    Toast.makeText(this, "Silent mode features will be limited", Toast.LENGTH_LONG).show();
                    checkLocationPermissions();
                })
                .setCancelable(false)
                .show();
    }

    private void checkLocationPermissions() {
        if (!LocationUtils.hasLocationPermissions(this)) {
            showLocationPermissionRationale();
        } else if (!LocationUtils.hasBackgroundLocationPermission(this)) {
            showBackgroundLocationPermissionRationale();
        } else {
            // Location permissions done, check calendar
            checkCalendarPermission();
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
                .setNegativeButton("Skip", (dialog, which) -> {
                    Toast.makeText(this, "Location features will not be available", Toast.LENGTH_LONG).show();
                    checkCalendarPermission();
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
                    checkCalendarPermission();
                })
                .setCancelable(false)
                .show();
    }

    private void checkCalendarPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            showCalendarPermissionRationale();
        } else {
            // All permissions checked, setup services
            completePermissionSetup();
        }
    }

    private void showCalendarPermissionRationale() {
        new AlertDialog.Builder(this)
                .setTitle("Calendar Permission Required")
                .setMessage("This app needs calendar access to automatically activate silent mode during your calendar events. " +
                        "This ensures your phone stays silent during meetings and important events.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.READ_CALENDAR},
                            REQUEST_CALENDAR_PERMISSION);
                })
                .setNegativeButton("Skip", (dialog, which) -> {
                    Toast.makeText(this, "Calendar-based silent mode will not be available", Toast.LENGTH_LONG).show();
                    completePermissionSetup();
                })
                .setCancelable(false)
                .show();
    }

    private void completePermissionSetup() {
        // Mark first time setup as complete
        if (isFirstTimeSetup) {
            prefs.edit().putBoolean("permissions_setup_completed", true).apply();
            isFirstTimeSetup = false;
        }

        // Setup services with granted permissions
        setupServicesIfReady();

        // Show completion message
        if (areEssentialPermissionsGranted()) {
            Toast.makeText(this, "Setup complete! Your app is ready to use.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Setup complete! You can enable more permissions in Settings.", Toast.LENGTH_LONG).show();
        }

        // Add example geofence if location permissions are granted
        if (LocationUtils.hasLocationPermissions(this)) {
            addSilentZoneGeofence();
        }
    }

    private void setupServicesIfReady() {
        // Start calendar monitoring if both calendar and DND permissions are granted
        boolean hasCalendarPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;

        boolean hasDNDPermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            hasDNDPermission = notificationManager.isNotificationPolicyAccessGranted();
        }

        if (hasCalendarPermission && hasDNDPermission) {
            startCalendarMonitorService();
        }
    }

    private void startCalendarMonitorService() {
        Intent serviceIntent = new Intent(this, CalendarMonitorService.class);
        startService(serviceIntent);
        Log.d(TAG, "Calendar monitor service started");
    }

    private void stopCalendarMonitorService() {
        Intent serviceIntent = new Intent(this, CalendarMonitorService.class);
        stopService(serviceIntent);
        Log.d(TAG, "Calendar monitor service stopped");
    }

    private void addSilentZoneGeofence() {
        if (!PermissionHelper.areAllPermissionsGranted(this)) {
            return; // Skip if permissions not granted
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
                            Toast.makeText(MainActivity.this, "Example silent zone added!", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onGeofenceRemoved(String geofenceId, boolean success, String message) {
                        Log.d(TAG, "Geofence removed - ID: " + geofenceId + ", Success: " + success);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Geofence error: " + error);
                    }
                }
        );
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
            case REQUEST_CALENDAR_PERMISSION:
                handleCalendarPermissionResult(grantResults);
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
            } else {
                // Location permissions complete, move to calendar
                checkCalendarPermission();
            }
        } else {
            Toast.makeText(this, "Location permissions denied", Toast.LENGTH_SHORT).show();
            // Continue to next permission even if location denied
            checkCalendarPermission();
        }
    }

    private void handleBackgroundLocationPermissionResult(int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Background location access granted", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Background location denied", Toast.LENGTH_SHORT).show();
        }
        // Continue to calendar permission regardless
        checkCalendarPermission();
    }

    private void handleCalendarPermissionResult(int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Calendar permission granted", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Calendar permission denied", Toast.LENGTH_SHORT).show();
        }
        // Complete setup regardless of result
        completePermissionSetup();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_DND_PERMISSION) {
            // DND permission result - continue to location permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (notificationManager.isNotificationPolicyAccessGranted()) {
                    Toast.makeText(this, "Do Not Disturb access granted", Toast.LENGTH_SHORT).show();
                }
            }
            // Continue to next permission
            checkLocationPermissions();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.nav_home) {
            showHomeFragment();
            return true;
        } else if (itemId == R.id.nav_profiles) {
            showProfilesFragment();
            return true;
        } else if (itemId == R.id.nav_settings) {
            showSettingsFragment();
            return true;
        }

        return false;
    }

    private void showProfilesFragment() {
        showFragment(profilesFragment, "PROFILES");
        bottomNavigationView.getMenu().findItem(R.id.nav_profiles).setChecked(true);
        fabAddProfile.show();
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
        if (profilesFragment != null && profilesFragment.isAdded()) {
            transaction.hide(profilesFragment);
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
        // Refresh the appropriate fragment
        if (homeFragment != null && homeFragment.isVisible()) {
            homeFragment.refreshDashboard();
        }
        if (profilesFragment != null && profilesFragment.isVisible()) {
            profilesFragment.refreshProfiles();
        }

        // Check if services should be started (in case permissions were granted outside the app)
        setupServicesIfReady();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't stop the calendar service here - let it run in background
    }

    // Public method that can be called from settings to toggle calendar monitoring
    public void toggleCalendarMonitoring(boolean enable) {
        if (enable) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                    == PackageManager.PERMISSION_GRANTED) {
                startCalendarMonitorService();
                Toast.makeText(this, "Calendar monitoring enabled", Toast.LENGTH_SHORT).show();
            } else {
                checkCalendarPermission();
            }
        } else {
            stopCalendarMonitorService();
            Toast.makeText(this, "Calendar monitoring disabled", Toast.LENGTH_SHORT).show();
        }
    }

    // Check if calendar monitoring is currently active
    public boolean isCalendarMonitoringActive() {
        return CalendarMonitorService.isCalendarModeActive(this);
    }
}