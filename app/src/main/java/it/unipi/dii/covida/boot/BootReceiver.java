package it.unipi.dii.covida.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

import androidx.preference.PreferenceManager;
import it.unipi.dii.covida.R;
import it.unipi.dii.covida.activityrecognition.ActivityDetectionService;

/**
 * This class is responsible to start the location and activity recognition services on boot.
 * These services are started only if the user did not turn off them.
 */
public class BootReceiver extends BroadcastReceiver {

    private final String TAG = "BootReceiver";

    /**
     * Start the service on system boot
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive()");

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        boolean savedState = sharedPref.getBoolean(context.getString(R.string.backgroudServicesEnabled),true);
        if(savedState) {
            Intent intentForADS = new Intent(context, ActivityDetectionService.class);
            intentForADS.setAction(ActivityDetectionService.ACTION_START);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean(context.getString(R.string.backgroudServicesEnabled), true);
            editor.commit();
            ForegroundServicesScheduler.enqueueWork(context, intentForADS);
        }
    }

    public static class ForegroundServicesScheduler extends JobIntentService {

        public static int lastId = 0;

        public static void enqueueWork(Context context, Intent work) {
            ++lastId;
            enqueueWork(context, ForegroundServicesScheduler.class, lastId, work);
        }

        @Override
        protected void onHandleWork(@NonNull Intent intent) {
            getApplicationContext().startForegroundService(intent);
        }
    }

}
