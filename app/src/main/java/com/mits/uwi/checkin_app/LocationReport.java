package com.mits.uwi.checkin_app;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.mits.uwi.checkin_app.service.LocationIntentService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class LocationReport extends AppCompatActivity implements
        ResultCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "LocationReport";
    public static final String WHERE = "com.mits.uwi.checkin_app.LocationReport.WHERE";
    public static final String LEFT = "com.mits.uwi.checkin_app.LocationReport.LEFT";

    PendingIntent geoFencePendingIntent;
    ArrayList<Geofence> workGeofenceLocations = new ArrayList<>();
    private static GoogleApiClient apiClient;

    private final int[] checkin_list = {R.id.mits_check_in,
            R.id.ms_check_in,
            R.id.hu_check_in,
            R.id.so_check_in,
            R.id.st_check_in};

    final static float GEOFENCE_RADIUS = 50f;

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "Connected to Locations API");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Connection Suspende " + String.valueOf(i));
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        Log.d(TAG, "Connection Failed: " +
                result.getErrorMessage() +
                " " + String.valueOf(result.getErrorCode()));
    }

    class ConnectionThread extends Thread {
        GoogleApiClient apiClient;

        public ConnectionThread(GoogleApiClient apiClient) {
            this.apiClient = apiClient;
        }

        private void removeProgressBar(){
            Handler uiHandler = new Handler(getMainLooper());

            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    ProgressBar pBar = (ProgressBar)findViewById(R.id.pBar);
                    TextView isConnectedText = (TextView)findViewById(R.id.isConnected);
                    isConnectedText.setVisibility(View.VISIBLE);

                }
            });
        }

        @Override
        public void run() {
            Looper.prepare();
            while (this.apiClient.isConnecting()) {
                Log.d(TAG, "Waiting for connection");
            }

            if (!this.apiClient.isConnected()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(LocationReport.this,
                                "API Client hasn't connecteed",
                                Toast.LENGTH_LONG)
                                .show();
                    }
                });
            } else if ((isPermitted("android.permission.INTERNET") ||
                    isPermitted("android.permission.ACCESS_FINE_LOCATION"))) {
                if (ActivityCompat
                        .checkSelfPermission(LocationReport.this,
                                android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                // Remove the Progress Bar Spinner
                removeProgressBar();

                LocationServices.GeofencingApi.addGeofences(this.apiClient,
                        getGeofencingRequest(),
                        getGeofencePendingIntent()).setResultCallback(LocationReport.this);
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(LocationReport.this,
                                "Please permit the app to access your location to move forward",
                                Toast.LENGTH_LONG)
                                .show();
                    }
                });
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        apiClient.connect();

        Thread connectionThread = new ConnectionThread(apiClient);
        connectionThread.setPriority(Thread.MIN_PRIORITY);
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    @Override
    protected void onStop() {
        super.onStop();

        apiClient.disconnect();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_report);

        buildGeofences();

        buildGoogleApiClient();

        IntentFilter iF = new IntentFilter();
        iF.addAction(LocationIntentService.broadcastId);

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(new LocationReciever(), iF);

        View.OnClickListener checkin_request = new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                RequestQueue request_queue = Volley.newRequestQueue(v.getContext());
                String url = "https://checkin-appuwi-codeinja.c9users.io/checkin";

                StringRequest checkinConfirm = new StringRequest(Request.Method.POST,
                        url,
                        new Response.Listener<String>() {
                            @Override
                            public void onResponse(String response) {
                                if (response.equals("Check-In Logged")) {
                                    v.setBackgroundColor(Color.rgb(0, 255, 0));
                                    Toast.makeText(v.getContext(),
                                            response,
                                            Toast.LENGTH_LONG)
                                            .show();
                                }
                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                v.setBackgroundColor(Color.rgb(255, 0, 0));
                                Toast.makeText(v.getContext(),
                                        "Check-in Failed",
                                        Toast.LENGTH_LONG)
                                        .show();
                            }
                        }){
                    @Override
                    protected Map<String, String> getParams(){
                        Map<String, String> params = new HashMap<>();
                        String value = null;
                        switch (v.getId()) {
                            case R.id.mits_check_in:
                                value = "MITS";
                                break;
                            case R.id.ms_check_in:
                                value = "MEDS";
                                break;
                            case R.id.st_check_in:
                                value = "SCIT";
                                break;
                            case R.id.so_check_in:
                                value = "SOCS";
                                break;
                            case R.id.hu_check_in:
                                value = "HUME";
                                break;
                            default:
                                break;
                        }
                        params.put("loc", value);
                        return params;
                    }
                };

                checkinConfirm.setTag("CHECKIN");

                request_queue.add(checkinConfirm);
            }
        };

        for (int button : checkin_list) {
            Button check_in_button = (Button) findViewById(button);
            check_in_button.setOnClickListener(checkin_request);
        }
    }

    private PendingIntent getGeofencePendingIntent(){
        if (geoFencePendingIntent != null){
            return geoFencePendingIntent;
        }
        Intent intent = new Intent(this, LocationIntentService.class);
        intent.putExtra("buttonIds", new int[]{R.id.mits_check_in,
                                               R.id.ms_check_in,
                                               R.id.st_check_in,
                                               R.id.so_check_in,
                                               R.id.hu_check_in});

        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private GeofencingRequest getGeofencingRequest() {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL);
        builder.addGeofences(workGeofenceLocations);
        return builder.build();
    }

    private synchronized void buildGoogleApiClient(){
        apiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addOnConnectionFailedListener(this)
                .build();

    }

    private void buildGeofences(){
        // Build the Geofences
        Geofence.Builder geofenceBuilder = new Geofence.Builder();
        // Sets the responsiveness of the geofence to a 1 seconds
        // TODO: increase responsiveness time (to a minute) after testing
        geofenceBuilder.setNotificationResponsiveness(1000);
        geofenceBuilder.setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL |
                Geofence.GEOFENCE_TRANSITION_ENTER |
                Geofence.GEOFENCE_TRANSITION_EXIT);
        geofenceBuilder.setExpirationDuration(Geofence.NEVER_EXPIRE);
        // User to stay in location for 5 seconds before dwell
        // TODO: increase responsiveness time (to 5 minutes after testing)
        geofenceBuilder.setLoiteringDelay(5000);

        // -- MITS --
        geofenceBuilder.setCircularRegion(18.003201, -76.745106, GEOFENCE_RADIUS);
        geofenceBuilder.setRequestId("MITS");
        workGeofenceLocations.add(geofenceBuilder.build());

        // -- Medical Sciences --
        geofenceBuilder.setCircularRegion(18.009745, -76.746828, GEOFENCE_RADIUS);
        geofenceBuilder.setRequestId("MEDS");
        workGeofenceLocations.add(geofenceBuilder.build());

        // -- Science & Technology --
        geofenceBuilder.setCircularRegion(18.005297, -76.749824, GEOFENCE_RADIUS);
        geofenceBuilder.setRequestId("SCIT");
        workGeofenceLocations.add(geofenceBuilder.build());

        geofenceBuilder.setCircularRegion(18.005173, -76.746022, GEOFENCE_RADIUS);
        geofenceBuilder.setRequestId("HUME");
        workGeofenceLocations.add(geofenceBuilder.build());

        geofenceBuilder.setCircularRegion(18.007109, -76.747231, GEOFENCE_RADIUS);
        geofenceBuilder.setRequestId("SOCS");
        workGeofenceLocations.add(geofenceBuilder.build());
    }

    private Location generateMockLocations(){
        Location mockLocation = new Location("network");
        mockLocation.setLongitude(18.007109);
        mockLocation.setLatitude(-76.747331);
        mockLocation.setAccuracy(20f);

        return mockLocation;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean isPermitted(String permission){
        return checkCallingOrSelfPermission(permission) == PermissionChecker.PERMISSION_GRANTED;
    }

    @Override
    public void onResult(@NonNull Result result) {
        Log.d(TAG, "Geofence request result: " + String.valueOf(result.getStatus().getStatusCode()));
    }

    private class LocationReciever extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String location = intent.getStringExtra(LocationReport.WHERE);
            ProgressBar pBar = (ProgressBar)findViewById(R.id.pBar);
            pBar.setVisibility(View.GONE);
            Button buttonToActivate;
            switch(location){
                case "MITS":
                    buttonToActivate = (Button) findViewById(R.id.mits_check_in);
                    break;
                case "MEDS":
                    buttonToActivate = (Button) findViewById(R.id.ms_check_in);
                    break;
                case "SCIT":
                    buttonToActivate = (Button) findViewById(R.id.st_check_in);
                    break;
                case "HUME":
                    buttonToActivate = (Button) findViewById(R.id.hu_check_in);
                    break;
                case "SOCS":
                    buttonToActivate = (Button) findViewById(R.id.so_check_in);
                    break;
                default:
                    buttonToActivate = null;
                    break;
            }
            if (buttonToActivate != null)
                buttonToActivate.setEnabled(true);
        }
    }
}


