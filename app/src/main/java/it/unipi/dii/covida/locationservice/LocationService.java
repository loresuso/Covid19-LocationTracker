package it.unipi.dii.covida.locationservice;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import java.text.DateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import androidx.preference.PreferenceManager;
import it.unipi.dii.covida.R;
import it.unipi.dii.covida.localdb.LocationDatabaseManager;
import it.unipi.dii.covida.locationstore.TracePool;
import it.unipi.dii.covida.notificationsystem.NotificationSystem;


/**
 * This Service is responsible to take periodic GPS sampling and store them using the methods
 * provided by TracePool.
 * This Service supports commands sent as intent actions to start or stop the tracking or to save
 * all the tracking data on the database. Normally these commands should be sent only by
 * DetectedActivityIntentService because the GPS tracking is dependent on the user's activity.
 * This service is also responsible to send a broadcast intent when the user goes back home.
 */
public class LocationService extends Service {

    /*
     * Commands (passed as action in the intent)
     */
    public static final String ACTION_START = "START";
    public static final String ACTION_STOP = "STOP";
    public static final String ACTION_STOP_FOREGROUND_SERVICE = "STOP_FOREGROUND_SERVICE";
    public static final String ACTION_SAVE_ON_DATABASE = "SAVE";

    /*
     * Constants
     */
    private static final String TAG = LocationService.class.getSimpleName();
    private static final int THRESHOLD_LOCATIONS = 17280; // 24 hour of continuous sampling (worst case: one each 5s) at most can be store in the tracepool
    private static final float SENSITIVITY = 7.0f; //7 meters
    private static final long USER_TIMEOUT = 1800000L;
    private static final double HOUSE_RADIUS = 15.0d;

    /*
     * Private static data members
     */
    private static boolean running = false;

    /*
     * Private data members
     */
    private LocationRequest locationRequest;
    private int lastSpeedClassification = -1;
    private Timer userTimeoutTimer;
    // Location stuff + db
    private FusedLocationProviderClient fusedLocationProviderClient; // it uses a mixture of GPS and network based localization in order to achieve max accuracy with the min power consumption for instance. https://developers.google.com/location-context/fused-location-provider
    private LocationCallback locationCallback; // callback used when a new location result is arrived from the fusedLocationProviderClient
    private LocationDatabaseManager db;
    private TracePool tracePool;
    private Location house;
    private boolean userIsInHouse;

    /*
     * Private methods
     */

    private static synchronized void setRunning(boolean b){
        running = b;
    }

    /**
     * This function defined the actions to be performed every time a new location
     * update is received from the fusedLocationProviderClient. In particular, it updates
     * the service notification with the received locationResult and add the location
     * to the TracePool.
     */
    private void initLocationCallback(){
        locationCallback = new LocationCallback(){
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                String actualLocation = "Lat: " + locationResult.getLastLocation().getLatitude() + " " + "Long: " + locationResult.getLastLocation().getLongitude();
                Log.d(TAG, actualLocation);
                String now = DateFormat.getTimeInstance().format(new Date());
                NotificationSystem.updateNotificationContent("Last update: " + now + "\n" + actualLocation, LocationService.this);

                Location newLocation = locationResult.getLastLocation();

                // Identify if user is at home. If so, do not store location data ...

                // double check is better!
                if(!tracePool.isEmpty() && tracePool.getTo().distanceTo(newLocation) < SENSITIVITY)
                    return;

                if(house != null && newLocation.distanceTo(house) <= HOUSE_RADIUS && !userIsInHouse) {
                    userIsInHouse = true;
                    Intent brInt = new Intent();
                    brInt.setAction("UserInHome");
                    sendBroadcast(brInt);
                } else if(house != null && newLocation.distanceTo(house) > HOUSE_RADIUS && userIsInHouse) {
                    userIsInHouse = false;
                }

                tracePool.addLocation(locationResult.getLastLocation());

                // set an interval and a the fastest rate for the sampling, based on the estimated speed of the user
                setSamplingInterval();

                if(tracePool.getLocationCount() > THRESHOLD_LOCATIONS) {
                    saveOnDatabase();
                }
            }
        };
    }

    /**
     * This function is responsible for starting the service in foreground
     */
// https://developer.android.com/guide/components/services#Foreground
    private void startForegroundService(){
        Log.d(TAG, "Start foreground service.");
        NotificationSystem.updateNotificationContent("Foreground service started",this);
        startForeground(NotificationSystem.getMainNotificationId(), NotificationSystem.getMainNotification());
    }

    /**
     * This function is responsible for stopping the service in foreground
     */
    private void stopForegroundService() {
        Log.d(TAG, "Stop foreground service.");

        //Stop location updates
        stopLocationUpdates();

        // Stop foreground service and remove the notification.
        stopForeground(true);
        stopSelf();
    }

    /**
     * This function can be called whenever the application needs location updates.
     *
     * The setPriority function tells the fusedLocationProviderClient the accuracy that our app needs. (accuracy)
     * In this case it is HIGH_ACCURACY, that corresponds to GPS usage.
     */
    private void startLocationUpdates(){
        lastSpeedClassification = -1;
        locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(1000);
        locationRequest.setSmallestDisplacement(SENSITIVITY);
        // Register the location request and the callback to the fusedLocationProviderClient
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
        setRunning(true);
        NotificationSystem.updateNotificationContent("Tracking started", this);
    }

    /**
     * This function can be called whenever the application needs to stop the incoming
     * flow of location updates.
     */
    private void stopLocationUpdates() {
        // Removing location updates = stop service (?)
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        lastSpeedClassification = -1;
        setRunning(false);
        NotificationSystem.updateNotificationContent("Tracking stopped", this);
    }

    /**
     * This function is responsible for saving the TracePool in the database. Then, it creates
     * a new one
     */
    private void saveOnDatabase() {
        int locationCount = tracePool.getLocationCount();
        if(locationCount <= 1) {
            Log.d(TAG,"saveOnDatabase() called, but tracePool was not savable (locationCount: " + locationCount + ")");
            return;
        }
        Log.d(TAG,"saveOnDatabase() called and tracePool was savable");
        tracePool.stop(); // stop only if not already stopped
        //double check
        if(tracePool.getLocationCount() >= 2)
            db.addTracePool(tracePool);
        tracePool = new TracePool();
    }

    /**
     * Check the user's speed of the current tracking and handle the sampling based on it
     */
    private void setSamplingInterval() {
        float currentSpeed =  tracePool.getLastSpeed();
        if(currentSpeed >= 14.0f || currentSpeed == 0.0f) { // greater or equal than 14m/s (~50km/h)
            if(lastSpeedClassification == 0)
                return;
            locationRequest.setInterval(1000); // sampling at least every 14 meters
            locationRequest.setFastestInterval(1000);
            lastSpeedClassification = 0;
        }
        else if(currentSpeed < 1.0f) { // less than 1m/s
            if(lastSpeedClassification == 1)
                return;
            locationRequest.setInterval(15000); // sampling within less than 15 meters
            locationRequest.setFastestInterval(10000);
            lastSpeedClassification = 1;
        }
        else if(currentSpeed >= 1.0f && currentSpeed <= 5.5) { // between 1m/s and 5.5m/s (approximates a normal walk/run; max speed < 20km/h)
            if(lastSpeedClassification == 2)
                return;
            locationRequest.setInterval(10000); // sampling at least every 10 seconds
            locationRequest.setFastestInterval(5000);
            lastSpeedClassification = 2;
        }
        else { // approximates movement inside an urban area
            if(lastSpeedClassification == 3)
                return;
            locationRequest.setInterval(5000); // sampling every 5 seconds
            locationRequest.setFastestInterval(2000); // if speed gets larger it samples every 2 seconds
            lastSpeedClassification = 3;
        }
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
    }

    private void stopUserTimeout() {
        if(userTimeoutTimer != null) {
            userTimeoutTimer.cancel();
            userTimeoutTimer = null;
        }
    }

    private void startUserTimeout() {
        if(userTimeoutTimer == null) {
            userTimeoutTimer = new Timer(true);
            userTimeoutTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    saveOnDatabase();
                    userTimeoutTimer = null;
                }
            }, USER_TIMEOUT);
        }
    }



    /*
     * Public methods
     */

    /**
     * this function can be used to know if the service is running or not
     * @return the state of the service (running or not running)
     */
    public static synchronized boolean isRunning(){
        return running;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * When the service is created, we need to initialize the notification, the fusedLocationProviderClient,
     * the database, the TracePool and the location callback
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onCreate() {
        /*
        When service is created, initialize fusedLocationProviderClient and set the callback function
         */
        super.onCreate();
        NotificationSystem.init(getApplicationContext());
        Log.d(TAG,"onCreate()");
        userIsInHouse = false;
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String homeAddress = shPref.getString("home_address",getString(R.string.default_home_address));
        if(homeAddress.equals(getString(R.string.default_home_address))) {
            house = null;
        } else {
            try {
                house = new Location("");
                house.setLatitude(Double.parseDouble(
                        shPref.getString("home_latitude", "0.0")
                ));
                house.setLongitude(Double.parseDouble(
                        shPref.getString("home_longitude", "0.0")
                ));
            }
            catch (NumberFormatException ex) {
                Log.e(TAG,"Error parsing house preferences");
                house = null;
            }
        }
        db = LocationDatabaseManager.getInstance(getApplicationContext());
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        initLocationCallback();
        tracePool = new TracePool();
    }

    /**
     * This function checks the intent received and, by looking at its action, start or stop the service
     * and / or the location updates.
     * In particular:
     * ACTION_START starts both the foreground service and the location updates
     * ACTION_STOP stops the location updates and the tracepool (service keep going)
     * ACTION_STOP_FOREGROUND_SERVICE as ACTION_STOP, but it also stops the the service
     * ACTION_SAVE_ON_DATABASE is the command to store the tracepool in the database
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent == null){ // Operating system restarted the service with a null intent after being killed
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            boolean savedState = sharedPref.getBoolean(getString(R.string.backgroudServicesEnabled),true);
            if(savedState){
                stopUserTimeout();
                startForegroundService();
                startLocationUpdates();
                return START_STICKY;
            }
            else {
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        String action = intent.getAction();
        if(action.equals(ACTION_START) && !running) {
            stopUserTimeout();
            startForegroundService(); // it starts the service if not already started
            startLocationUpdates();
        } else if(action.equals(ACTION_STOP) && running) {
            stopLocationUpdates();
            tracePool.stop();
            startUserTimeout();
        } else if(action.equals(ACTION_STOP_FOREGROUND_SERVICE)) {
            if(running) {
                stopLocationUpdates();
                tracePool.stop();
            }
            stopUserTimeout();
            stopForegroundService();
        } else if(action.equals(ACTION_SAVE_ON_DATABASE)) {
            saveOnDatabase();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
    }

}

