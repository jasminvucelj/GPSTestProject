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
import android.os.CountDownTimer;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
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

public class MainActivity extends Activity implements OnMapReadyCallback {

    int test = 0;

    final int LONG_REFRESH_TIME = 5 * 60 * 1000; // 5 min => ms
    final int SHORT_REFRESH_TIME = 15 * 1000; // 15 s => ms
    final int REFRESH_DISTANCE = 100;
    final int TIMER_INTERVAL = 5 * 1000;
    final float ZOOM_LEVEL = 5;
    final float ACCURACY_THRESHOLD = 100;
    final double DISTANCE_UPDATE_THRESHOLD = 343 * SHORT_REFRESH_TIME / 1000;

    long nextUpdateTime;
    double currentDistance = 0;

    DatabaseHandler dbHandler;

    CountDownTimer countDownTimer;

    GoogleMap googleMap;
    MapFragment mapFragment;

    Button btnStartDay, btnSendNote;
    EditText noteText;
    TextView textViewDistance, textViewDB;

    boolean dayStarted = false;

    Location lastLocationPeriodic = null, lastLocationConstant = null;
    LocationManager locationManagerConstant, locationManagerPeriodic;
    LocationListener locationListenerConstant, locationListenerPeriodic;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initMap();
        initViews();
        initDatabaseHandler();

        // ask permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                }
                , 10);
            }
            return;
        }

        initCountDownTimer();
        initLocations();
        btnConfig();

    }


    /**
     * Initializes the map fragment.
     */
    private void initMap() {
        mapFragment = (MapFragment) getFragmentManager()
                .findFragmentById(R.id.mapFragment);
        mapFragment.getMapAsync(this);
    }


    /**
     * Assigns views on the layout to variables.
     */
    private void initViews() {
        btnStartDay = (Button) findViewById(R.id.btnStartDay);
        btnSendNote = (Button) findViewById(R.id.btnSendNote);
        noteText = (EditText) findViewById(R.id.noteText);
        textViewDB = (TextView) findViewById(R.id.textViewDB);
        textViewDistance = (TextView) findViewById(R.id.textViewDistance);
    }


    /**
     * Initializes the DatabaseHandler.
     */
    private void initDatabaseHandler() {
        dbHandler = new DatabaseHandler(this);
        dbHandler.deleteAll(); // TEST
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case 10:
                initCountDownTimer();
                initLocations();
                btnConfig();
                break;
            default:
                break;
        }
    }


    /**
     * Sets up the countdown timer.
     */
    private void initCountDownTimer() {
        countDownTimer = new CountDownTimer(LONG_REFRESH_TIME, TIMER_INTERVAL) {
            @Override
            public void onTick(long millisUntilFinished) { // check if time has been changed
                if (Calendar.getInstance().getTimeInMillis() > nextUpdateTime) { // time changed => request update, restart timer
                    stopTimer();
                }
            }

            @Override
            public void onFinish() { // timer expires, request update, set next update time
                finishTimer();
            }
        };
    };


    /**
     * Requests a single update, and interrupts the timer.
     */
    private void stopTimer() {
        countDownTimer.cancel();
        //noinspection MissingPermission
        locationManagerPeriodic.requestSingleUpdate(LocationManager.GPS_PROVIDER,
                locationListenerPeriodic,
                null);
    }


    /**
     * On timer expiring, requests a single update and sets a new update time.
     */
    private void finishTimer() {
        //noinspection MissingPermission
        locationManagerPeriodic.requestSingleUpdate(LocationManager.GPS_PROVIDER,
                locationListenerPeriodic,
                null);
        nextUpdateTime = Calendar.getInstance().getTimeInMillis() + LONG_REFRESH_TIME;
    }


    /**
     * Sets up the locationManagers and locationListeners.
     */
    private void initLocations() {

        locationManagerPeriodic = (LocationManager) getSystemService(LOCATION_SERVICE);

        locationListenerPeriodic = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                periodicLocationChanged(location);
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
                constantLocationChanged(location);
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


    /**
     * Stores location if acceptable (and restarts the timer), otherwise requests a new one.
     * @param location last received periodic location.
     */
    private void periodicLocationChanged(Location location) {

        if(location.getAccuracy() < ACCURACY_THRESHOLD) {
            lastLocationPeriodic = location;
            nextUpdateTime = Calendar.getInstance().getTimeInMillis() + LONG_REFRESH_TIME;
            countDownTimer.start();

            Toast.makeText(this, "Received location: " + location.getLatitude() + "\t" + location.getLongitude(), Toast.LENGTH_SHORT).show();
        }
        else {
            //noinspection MissingPermission
            locationManagerPeriodic.requestSingleUpdate(LocationManager.GPS_PROVIDER,
                    locationListenerPeriodic,
                    null);
        }
    }


    /**
     * Updates total distance if new location is valid (within threshold).
     * @param location last received constant location.
     */
    private void constantLocationChanged(Location location) {
        // calculate new distance
        double tempDistance = newDistance(location, lastLocationConstant);

        if (tempDistance < DISTANCE_UPDATE_THRESHOLD) {
            // update distance
            currentDistance += tempDistance;
            textViewDistance.setText(getString(R.string.current_distance) + "\t" +
                    String.valueOf(currentDistance) + " m");
            // update last location
            lastLocationConstant = location;
        }
    }


    /**
     * Checks whether or not a location is acceptable. A location is considered acceptable
     * if it's not null, and the accuracy is below a defined threshold.
     * @param location the location to be checked.
     * @return true if location is acceptable, false if not.
     */
    private boolean locationIsAcceptable(Location location) { // acceptable = not null & accuracy < 100 m
        if (location == null || location.getAccuracy() < ACCURACY_THRESHOLD) return false;
        return true;
    }


    /**
     * Returns the distance between two locations.
     * @param newLoc new location.
     * @param lastLoc last saved location.
     * @return 0 if last location is undefined, otherwise the distance from last location to the
     * new one.
     */
    private double newDistance(Location newLoc, Location lastLoc) {
        if(newLoc == null || lastLoc == null) {
            return 0;
        }
        else {
            return (double) lastLoc.distanceTo(newLoc);
        }
    }


    /**
     * Sets up the onClickListeners for btnStartDay (toggle tracking) and btnSendNote
     * (sends/saves to DB the note with the last saved periodic location).
     */
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
                sendNote(noteText.getText().toString(), lastLocationPeriodic);
            }
        });
    }


    /**
     * Toggles both constant and periodic tracking.
     */
    private void dayStartedToggle() {
        if (dayStarted) { // turn off tracking
            dayStarted = false;

            btnSendNote.setEnabled(false);
            btnStartDay.setText(getString(R.string.start_day));

            countDownTimer.cancel();

            locationManagerPeriodic.removeUpdates(locationListenerPeriodic);
            locationManagerConstant.removeUpdates(locationListenerConstant);
        }

        else {
            dayStarted = true;

            btnSendNote.setEnabled(true);
            btnStartDay.setText(getString(R.string.stop_day));

            //noinspection MissingPermission
            locationManagerPeriodic.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListenerPeriodic, null);
            //noinspection MissingPermission
            locationManagerConstant.requestLocationUpdates(LocationManager.GPS_PROVIDER, SHORT_REFRESH_TIME, REFRESH_DISTANCE, locationListenerConstant); // 15 s & 100 m
        }
    }


    /**
     * Creates a Note from a given text and location, and sends it (saves to DB) if the location is
     * acceptable.
     * @param text note text.
     * @param location note location.
     */
    private void sendNote(String text, Location location) {
        // location ok?
        if (!locationIsAcceptable(location)) {
            Toast.makeText(this, R.string.location_bad, Toast.LENGTH_SHORT).show();
            return;
        }

        Note note = new Note(text, location);

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


    /**
     * Places a marker for a location of a note on the map.
     * @param note Note with a location to be marked.
     */
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
