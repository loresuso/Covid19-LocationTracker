package it.unipi.dii.covida.activityrecognition;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import it.unipi.dii.covida.locationservice.LocationService;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/**
 * This IntentService is responsible to receive intents from Google's activity recognition system.
 * It has to process the intent (onHandleIntent(Intent intent)) to extract information about the
 * user activity and to activate or deactive the LocationService that is responsible to take GPS
 * points.
 */
public class DetectedActivityIntentService extends IntentService {

    /*
     * Constants
     */
    private static final String TAG = DetectedActivityIntentService.class.getSimpleName();


    /*
     * Private and protected methods
     */

    @Override
    protected void onHandleIntent(Intent intent) {
        ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);

        // Get the list of the probable activities associated with the current state of the
        // device. Each activity is associated with a confidence level, which is an int between
        // 0 and 100.
        List<DetectedActivity> detectedActivities = result.getProbableActivities();

        // This should never happen. but a double-check is preferred :)
        if(detectedActivities.isEmpty()) {
            Log.d(TAG, "detectedActivities is empty");
            return;
        }

        // The list can have more than one element.
        // A crude approach could be simple take the DetectedActivity with the higher confidence.
        // Anyway to leave us the possibility to perform a more fine approach, the list is sorted.
        Collections.sort(
                detectedActivities,
                new Comparator<DetectedActivity>() {
                    @Override
                    public int compare(DetectedActivity o1, DetectedActivity o2) {
                        return o2.getConfidence() - o1.getConfidence();
                    }
                }
        );

        changeServiceStates(detectedActivities.get(0));
    }


    private void changeServiceStates(DetectedActivity activity) {
        Log.d(TAG, "changeServiceStates(DetectedActivity activity) called with " + activity.toString());
        if(activity.getType() != DetectedActivity.STILL) {
            Intent intent = new Intent(DetectedActivityIntentService.this, LocationService.class);
            intent.setAction(LocationService.ACTION_START);
            startService(intent);
        } else {
            Intent intent = new Intent(DetectedActivityIntentService.this, LocationService.class);
            intent.setAction(LocationService.ACTION_STOP);
            startService(intent);
        }
    }



    /*
     * Public methods
     */

    public DetectedActivityIntentService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

}
