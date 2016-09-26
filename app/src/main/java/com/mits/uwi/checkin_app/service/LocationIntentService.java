package com.mits.uwi.checkin_app.service;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.List;
import com.mits.uwi.checkin_app.LocationReport;

/**
 * Created by Danuel on 12/9/2016.
 */
public class LocationIntentService extends IntentService {
    final static String TAG = "LocationIntentService";
    public static final String broadcastId =
            "com.mits.uwi.checkin_app.service.LocationIntentService.BROADCAST";
    static Handler activityHandler;
    int[] buttonsToActivate;
    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */

    public LocationIntentService(){
        super("GeofenceLocationService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        activityHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        buttonsToActivate = intent.getIntArrayExtra("buttonIds");

        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()){
            String errorMessage = String.valueOf(geofencingEvent.getErrorCode());
            Log.e(TAG, errorMessage);
            return;
        }

        // Geofence Transition Type
        int gfTransitionType = geofencingEvent.getGeofenceTransition();
        String gfTransitionString;

        switch (gfTransitionType){
            case Geofence.GEOFENCE_TRANSITION_DWELL:
                gfTransitionString = "GEOFENCE_TRANSITION_DWELL";
                break;
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                gfTransitionString = "GEOFENCE_TRANSITION_ENTER";
                break;
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                gfTransitionString = "GEOFENCE_TRANSITION_EXIT";
                break;
            default:
                gfTransitionString = "None";
        }

        if (gfTransitionType == Geofence.GEOFENCE_TRANSITION_ENTER ||
                gfTransitionType == Geofence.GEOFENCE_TRANSITION_DWELL){
            List triggeredGfs = geofencingEvent.getTriggeringGeofences();
            for (Geofence gf: geofencingEvent.getTriggeringGeofences()){
                postToast(gf.getRequestId() + " geofence triggered -- " + gfTransitionString);
                Intent triggeredGeoFenceBroadcast = new Intent(broadcastId);
                triggeredGeoFenceBroadcast.putExtra(LocationReport.WHERE, gf.getRequestId());
                LocalBroadcastManager
                        .getInstance(this)
                        .sendBroadcast(triggeredGeoFenceBroadcast);

            }
        }
        if (gfTransitionType == Geofence.GEOFENCE_TRANSITION_EXIT){
            postToast("Exiting Geofence");
        }

    }


    private void postToast(final String message){

        activityHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LocationIntentService.this,
                        message,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

}
