package com.example.sssshhift.activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.example.sssshhift.R;

public class LocationPickerActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = "LocationPickerActivity";
    private GoogleMap map;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_picker);

        try {
            // Initialize map
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map_fragment);
            if (mapFragment == null) {
                throw new IllegalStateException("Map fragment not found");
            }
            Log.d(TAG, "Getting map async");
            mapFragment.getMapAsync(this);
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing map view: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        try {
            Log.d(TAG, "onMapReady called");
            map = googleMap;
            map.getUiSettings().setZoomControlsEnabled(true);
            Log.d(TAG, "Map initialization complete");
        } catch (Exception e) {
            Log.e(TAG, "Error in onMapReady: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing map: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}