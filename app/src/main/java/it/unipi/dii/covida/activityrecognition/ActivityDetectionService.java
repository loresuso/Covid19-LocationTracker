package it.unipi.dii.covida.activityrecognition;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import it.unipi.dii.covida.R;
import it.unipi.dii.covida.notificationsystem.NotificationSystem;

/**
 * This class initialize the service that takes care of the activity recognition.
 * It defines two commands (START and STOP) that are used as actions for the service.
 */
public class ActivityDetectionService extends Service {

    /*
     * Constants
     */
    private static final String TAG = ActivityDetectionService.class.getSimpleName();
    /** the desired time between activity detections. Larger values will result in fewer activity
     * detections while improving battery life. A value of 0 will result in activity detections
     * at the fastest possible rate.
     */
    private static final long DETECTION_INTERVAL_IN_MILLISECONDS = 1000;

    /*
     * Commands (passed as action in the intent)
     */
    public static final String ACTION_START = "START";
    public static final String ACTION_STOP = "STOP";

    /*
     * Static private data members
     */
    private static boolean state = false;

    /*
     * Private data members
     */
    private PendingIntent mPendingIntent;
    private ActivityRecognitionClient mActivityRecognitionClient;



    /*
     * Private methods
     */

    /**
     * Request updates and set up callbacks for success or failure
     */
    private void requestActivityUpdatesHandler() {
        if(mActivityRecognitionClient != null){
            Task<Void> task = mActivityRecognitionClient.requestActivityUpdates(
                    DETECTION_INTERVAL_IN_MILLISECONDS,
                    mPendingIntent
            );

            // Adds a listener that is called if the Task completes successfully.
            task.addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Log.d(TAG, "Successfully requested activity updates");
                }
            });
            // Adds a listener that is called if the Task fails.
            task.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    boolean activityRecognition = getApplicationContext().checkSelfPermission("com.google.android.gms.permission.ACTIVITY_RECOGNITION") == PackageManager.PERMISSION_GRANTED;
                    e.printStackTrace();
                    Log.e(TAG, "Requesting activity updates failed to start, activityRecognition: " + activityRecognition);
                }
            });
        }
    }

    /**
     * Remove updates and set up callbacks for success or failure
     */
    private void removeActivityUpdatesHandler() {
        if(mActivityRecognitionClient != null){
            Task<Void> task = mActivityRecognitionClient.removeActivityUpdates(
                    mPendingIntent);
            // Adds a listener that is called if the Task completes successfully.
            task.addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Log.d(TAG, "Removed activity updates successfully!");
                }
            });
            // Adds a listener that is called if the Task fails.
            task.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "Failed to remove activity updates!");
                }
            });
        }
    }

    private static synchronized void setState(boolean b) {
        state = b;
    }



    /*
     * Public methods
     */

    public static synchronized boolean getState() {
        return state;
    }

    public ActivityDetectionService() {
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if(intent == null){ // Operating system restarted the service with a null intent after being killed
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            boolean savedState = sharedPref.getBoolean(getString(R.string.backgroudServicesEnabled),true);
            if(savedState){
                intent = new Intent(ACTION_START);
            }
            else {
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        String action = intent.getAction();
        Log.d(TAG, "onStartCommand(...) -> action is " + action);
        switch (action) {
            case ACTION_START:
                // When the service is started a new ActivityRecognitionClient must be created in order to
                // receive updates on the activity recognized.
                // Also a new intent (mIntentService) must be created in order to handle the result.
                // FLAG_UPDATE_CURRENT indicates that if the described PendingIntent already exists,
                // then keep it but replace its extra data with what is in this new Intent.
                mActivityRecognitionClient = new ActivityRecognitionClient(this);

                Intent mIntentService = new Intent(this, DetectedActivityIntentService.class);
                mPendingIntent = PendingIntent.getService(this, 1, mIntentService, PendingIntent.FLAG_UPDATE_CURRENT);

                requestActivityUpdatesHandler();

                NotificationSystem.init(getApplicationContext());
                startForeground(NotificationSystem.getMainNotificationId(), NotificationSystem.getMainNotification());

                setState(true);
                break;

            case ACTION_STOP:
                removeActivityUpdatesHandler();
                setState(false);
                stopSelf();
                stopForeground(true);
                break;

            default:
                Log.e(TAG, "ERROR: action is not ACTION_START nor ACTION_STOP");
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Need to remove the request to Google play services.
        removeActivityUpdatesHandler();
        Log.d(TAG, "onDestroy()");
    }

}
