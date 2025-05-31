package com.example.sssshhift.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sssshhift.utils.LocationUtils;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.example.sssshhift.R;
import com.example.sssshhift.adapters.LocationSearchAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LocationPickerActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "LocationPickerActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    // UI Components
    private GoogleMap googleMap;
    private TextInputEditText searchEditText;
    private RecyclerView searchResultsRecycler;
    private MaterialButton confirmLocationBtn;
    private LocationSearchAdapter searchAdapter;

    // Data
    private LatLng selectedLocation;
    private Marker selectedMarker;
    private String selectedLocationName;
    private Geocoder geocoder;

    // Search results
    private List<LocationSearchAdapter.LocationItem> searchResults = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_picker);

        setupToolbar();
        initViews();
        setupMap();
        setupSearch();
        checkLocationPermission();

        checkLocationServicesAndPermissions();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Select Location");
        }
    }

    @SuppressLint("WrongViewCast")
    private void initViews() {
        searchEditText = findViewById(R.id.search_edit_text);
        searchResultsRecycler = findViewById(R.id.search_results_recycler);
        confirmLocationBtn = findViewById(R.id.confirm_location_btn);

        geocoder = new Geocoder(this, Locale.getDefault());

        // Setup RecyclerView
        searchResultsRecycler.setLayoutManager(new LinearLayoutManager(this));
        searchAdapter = new LocationSearchAdapter(searchResults, this::onLocationSelected);
        searchResultsRecycler.setAdapter(searchAdapter);

        // Initially hide confirm button
        confirmLocationBtn.setVisibility(View.GONE);

        // Confirm button listener
        confirmLocationBtn.setOnClickListener(v -> confirmSelectedLocation());
    }

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 2) {
                    searchLocations(s.toString());
                } else {
                    clearSearchResults();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;

        // Set default location (you can change this to user's current location)
        LatLng defaultLocation = new LatLng(37.7749, -122.4194); // San Francisco
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10));

        // Set map click listener
        googleMap.setOnMapClickListener(this::onMapClick);

        // Enable location if permission is granted
        enableMyLocationIfPermitted();
    }

    private void onMapClick(LatLng latLng) {
        selectLocationOnMap(latLng);
        getLocationName(latLng);
    }

    private void selectLocationOnMap(LatLng location) {
        selectedLocation = location;

        // Remove previous marker
        if (selectedMarker != null) {
            selectedMarker.remove();
        }

        // Add new marker
        selectedMarker = googleMap.addMarker(new MarkerOptions()
                .position(location)
                .title("Selected Location"));

        // Show confirm button
        confirmLocationBtn.setVisibility(View.VISIBLE);
        confirmLocationBtn.setText("Confirm Location");
    }

    private void getLocationName(LatLng location) {
        try {
            List<Address> addresses = geocoder.getFromLocation(
                    location.latitude, location.longitude, 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                selectedLocationName = formatAddress(address);
                confirmLocationBtn.setText("Confirm: " + selectedLocationName);
            } else {
                selectedLocationName = "Unknown Location";
                confirmLocationBtn.setText("Confirm: " +
                        String.format(Locale.getDefault(), "%.6f, %.6f",
                                location.latitude, location.longitude));
            }
        } catch (IOException e) {
            Log.e(TAG, "Geocoding failed", e);
            selectedLocationName = "Unknown Location";
            confirmLocationBtn.setText("Confirm Location");
        }
    }

    private void searchLocations(String query) {
        try {
            List<Address> addresses = geocoder.getFromLocationName(query, 5);

            searchResults.clear();
            if (addresses != null) {
                for (Address address : addresses) {
                    LocationSearchAdapter.LocationItem item = new LocationSearchAdapter.LocationItem(
                            formatAddress(address),
                            address.getLatitude(),
                            address.getLongitude()
                    );
                    searchResults.add(item);
                }
            }

            searchAdapter.notifyDataSetChanged();
            searchResultsRecycler.setVisibility(searchResults.isEmpty() ? View.GONE : View.VISIBLE);

        } catch (IOException e) {
            Log.e(TAG, "Search failed", e);
            Toast.makeText(this, "Search failed. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearSearchResults() {
        searchResults.clear();
        searchAdapter.notifyDataSetChanged();
        searchResultsRecycler.setVisibility(View.GONE);
    }

    private void onLocationSelected(LocationSearchAdapter.LocationItem location) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        // Move camera to selected location
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));

        // Select location on map
        selectLocationOnMap(latLng);
        selectedLocationName = location.getName();

        // Clear search results and search text
        clearSearchResults();
        searchEditText.setText("");

        // Update confirm button
        confirmLocationBtn.setText("Confirm: " + selectedLocationName);
    }

    private String formatAddress(Address address) {
        StringBuilder sb = new StringBuilder();

        if (address.getFeatureName() != null) {
            sb.append(address.getFeatureName()).append(", ");
        }
        if (address.getLocality() != null) {
            sb.append(address.getLocality()).append(", ");
        }
        if (address.getAdminArea() != null) {
            sb.append(address.getAdminArea()).append(", ");
        }
        if (address.getCountryName() != null) {
            sb.append(address.getCountryName());
        }

        String result = sb.toString();
        if (result.endsWith(", ")) {
            result = result.substring(0, result.length() - 2);
        }

        return result.isEmpty() ? "Unknown Location" : result;
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            enableMyLocationIfPermitted();
        }
    }

    private void enableMyLocationIfPermitted() {
        if (googleMap != null && ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
            googleMap.getUiSettings().setMyLocationButtonEnabled(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocationIfPermitted();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // FIXED: Simplified confirmSelectedLocation method
    private void confirmSelectedLocation() {
        if (selectedLocation != null) {
            Log.d(TAG, "Confirming location: " + selectedLocation.latitude + ", " + selectedLocation.longitude);

            // Create result intent with selected location data
            Intent resultIntent = new Intent();
            resultIntent.putExtra("latitude", selectedLocation.latitude);
            resultIntent.putExtra("longitude", selectedLocation.longitude);
            resultIntent.putExtra("locationName", selectedLocationName != null ? selectedLocationName : "Selected Location");

            // Set result and finish
            setResult(RESULT_OK, resultIntent);
            finish();
        } else {
            Toast.makeText(this, "Please select a location first", Toast.LENGTH_SHORT).show();
        }
    }
    private void checkLocationServicesAndPermissions() {
        // Check if location services are enabled
        if (!LocationUtils.isLocationEnabled(this)) {
            showLocationServicesDialog();
            return;
        }

        // Then check permissions
        checkLocationPermission();
    }
    private void showLocationServicesDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Location Services Required")
                .setMessage("Please enable location services to use this feature.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(this, "Location services are required", Toast.LENGTH_SHORT).show();
                    finish(); // Close the activity if location is required
                })
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Also check when returning to the activity
        checkLocationServicesAndPermissions();
    }
}