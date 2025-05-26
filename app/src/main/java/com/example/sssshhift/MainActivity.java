package com.example.sssshhift;

        import android.content.Intent;
        import android.os.Bundle;
        import android.view.MenuItem;
        import androidx.annotation.NonNull;
        import androidx.appcompat.app.AppCompatActivity;
        import androidx.fragment.app.Fragment;
        import androidx.fragment.app.FragmentManager;
        import androidx.fragment.app.FragmentTransaction;
        import com.google.android.material.bottomnavigation.BottomNavigationView;
        import com.google.android.material.floatingactionbutton.FloatingActionButton;
        import com.example.sssshhift.R;
        import com.example.sssshhift.fragments.HomeFragment;
        import com.example.sssshhift.fragments.SettingsFragment;
        import com.example.sssshhift.utils.PermissionUtils;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    private BottomNavigationView bottomNavigationView;
    private FloatingActionButton fabAddProfile;
    private FragmentManager fragmentManager;

    // Fragments
    private HomeFragment homeFragment;
    private SettingsFragment settingsFragment;

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
            Intent intent = new Intent(com.example.sssshhift.MainActivity.this, com.example.sssshhift.activities.AddProfileActivity.class);
            startActivity(intent);
        });
    }

    private void checkPermissions() {
        if (!PermissionUtils.hasNotificationPolicyPermission(this)) {
            PermissionUtils.requestNotificationPolicyPermission(this);
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