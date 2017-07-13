package com.example.lux1410.testproject;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import java.util.Calendar;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import static android.content.ContentValues.TAG;

public class MainActivity extends Activity implements OnMapReadyCallback {

    final int LONG_REFRESH_TIME = 5 * 60 * 1000; // 5 min => ms
    final int SHORT_REFRESH_TIME = 15 * 1000; // 15 s => ms
    final int REFRESH_DISTANCE = 100;
    final float ZOOM_LEVEL = 5;
    final float ACCURACY_THRESHOLD = 100;
    final double DISTANCE_UPDATE_THRESHOLD = 343 * SHORT_REFRESH_TIME / 1000;

    long nextPeriodicUpdateTime;
    double currentDistance = 0;

    DatabaseHandler dbHandler;

    GoogleMap googleMap;
    MapFragment mapFragment;

    Button btnStartDay, btnSendNote;
    EditText noteText;
    TextView textViewDistance, textViewDB;

    boolean dayStarted = false;

    Location lastLocationPeriodic = null, lastLocationConstant = null;
    LocationManager locationManagerConstant, locationManagerSingle;
    LocationListener locationListenerConstant, locationListenerSingle;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // init map
        mapFragment = (MapFragment) getFragmentManager()
                .findFragmentById(R.id.mapFragment);
        mapFragment.getMapAsync(this);

        // init views
        btnStartDay = (Button) findViewById(R.id.btnStartDay);
        btnSendNote = (Button) findViewById(R.id.btnSendNote);
        noteText = (EditText) findViewById(R.id.noteText);
        textViewDB = (TextView) findViewById(R.id.textViewDB);
        textViewDistance = (TextView) findViewById(R.id.textViewDistance);

        // init database
        dbHandler = new DatabaseHandler(this);
        dbHandler.deleteAll(); // TEST


        // ask permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.INTERNET}
                        , 10);
            }
            return;
        }

        initLocations();
        btnConfig();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case 10:
                initLocations();
                btnConfig();
                break;
            default:
                break;
        }
    }

    private void initLocations() {

        locationManagerSingle = (LocationManager) getSystemService(LOCATION_SERVICE);

        locationListenerSingle = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                
                // TODO
                if(locationIsAcceptable(location)) {
                    lastLocationPeriodic = location;
                    // TODO zatrazi novi update
                }
                else {
                    //noinspection MissingPermission
                    locationManagerSingle.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListenerSingle, null);
                }

            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        };

        locationManagerConstant = (LocationManager) getSystemService(LOCATION_SERVICE);

        locationListenerConstant = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {

                // update time
                Calendar calendar = Calendar.getInstance();

                // if 1st periodic location || time >= nextPeriodicUpdateTime => new periodic location
                if(lastLocationPeriodic == null || calendar.getTimeInMillis() >= nextPeriodicUpdateTime ) {
                    lastLocationPeriodic = location;
                    nextPeriodicUpdateTime = calendar.getTimeInMillis() + LONG_REFRESH_TIME;
                }

                // calculate new distance
                double tempDistance = newDistance(location, lastLocationConstant);

                if (tempDistance < DISTANCE_UPDATE_THRESHOLD) {
                    // update distance
                    currentDistance += tempDistance;
                    textViewDistance.setText(getString(R.string.current_distance) + "\t" + String.valueOf(currentDistance) + " m");
                    // update last location
                    lastLocationConstant = location;
                }

            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        };
    }

    private boolean locationIsAcceptable(Location location) { // acceptable = not null & accuracy < 100 m
        if (location == null || location.getAccuracy() < ACCURACY_THRESHOLD) return false;
        return true;
    }

    private double newDistance(Location newLoc, Location lastLoc) {
        if(lastLoc == null) { // 1. point => 0
            return 0;
        }
        else { // not 1. point => distance (lastLoc, newLoc)
            return (double) lastLoc.distanceTo(newLoc);
        }
    }


    private void btnConfig(){
        // btnStartDay - toggle tracking
        btnStartDay.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                dayStartedToggle();
            }
        });

        // btnSendNote - sends (saves to db) note with the last periodic location
        btnSendNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendNote(lastLocationPeriodic);
            }
        });
    }

    private void dayStartedToggle() {
        if (dayStarted) { // turn off tracking
            dayStarted = false;

            btnSendNote.setEnabled(false);
            btnStartDay.setText(getString(R.string.start_day));

            locationManagerSingle.removeUpdates(locationListenerSingle);
            locationManagerConstant.removeUpdates(locationListenerConstant);
        }
        else {
            dayStarted = true;

            btnSendNote.setEnabled(true);
            btnStartDay.setText(getString(R.string.stop_day));

            //noinspection MissingPermission
            locationManagerSingle.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListenerSingle, null);
            //noinspection MissingPermission
            locationManagerConstant.requestLocationUpdates(LocationManager.GPS_PROVIDER, SHORT_REFRESH_TIME, REFRESH_DISTANCE, locationListenerConstant); // 15 s & 100 m
        }
    }


    private void sendNote(Location location) {
        // location ok?
        if (!locationIsAcceptable(location)) {
            Toast.makeText(this, "Accurate location not available", Toast.LENGTH_SHORT).show();
            return;
        }

        Note note = new Note(noteText.getText().toString(), location);

        if(dbHandler.addNote(note)) {
            Toast.makeText(this, "Successfully inserted: " + note.toString(), Toast.LENGTH_SHORT).show();
        }
        else {
            Toast.makeText(this, "Insertion failed" + note.toString(), Toast.LENGTH_SHORT).show();
        }

        // display data
        textViewDB.setText(dbHandler.databaseToString());

        // place marker on map
        setMarker(note);
    }


    private void setMarker(Note note) {
        LatLng locationAsLatLng = new LatLng(note.getLatitude(), note.getLongitude());

        googleMap.addMarker(new MarkerOptions().position(locationAsLatLng)
                .title(note.getText()));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(locationAsLatLng, ZOOM_LEVEL));
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
    }
}
