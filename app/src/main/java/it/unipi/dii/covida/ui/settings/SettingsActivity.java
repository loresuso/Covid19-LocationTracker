package it.unipi.dii.covida.ui.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import it.unipi.dii.covida.locationservice.LocationService;
import it.unipi.dii.covida.R;
import it.unipi.dii.covida.activityrecognition.ActivityDetectionService;
import it.unipi.dii.covida.ui.home_setting.HomeSetting;


/**
 * This Activity is responsible to show the settings menu to the user and let him modify them
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    public SettingsActivity(){}


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment(), "settingfragment")
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        private Long lastClick = 0L;

        private void startOrStop(boolean start) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean(getString(R.string.backgroudServicesEnabled), start);
            editor.commit();
            if (start) {
                Intent intent = new Intent(getContext(), ActivityDetectionService.class);
                intent.setAction(ActivityDetectionService.ACTION_START);
                getContext().startForegroundService(intent);
            } else {
                Intent intentActivityDetection = new Intent(getContext(), ActivityDetectionService.class);
                intentActivityDetection.setAction(ActivityDetectionService.ACTION_STOP);
                getContext().startService(intentActivityDetection);
                Intent intentLocationServiceSave = new Intent(getContext(), LocationService.class);
                intentLocationServiceSave.setAction(LocationService.ACTION_SAVE_ON_DATABASE);
                getContext().startService(intentLocationServiceSave);
                Intent intentLocationServiceStop = new Intent(getContext(), LocationService.class);
                intentLocationServiceStop.setAction(LocationService.ACTION_STOP_FOREGROUND_SERVICE);
                getContext().startService(intentLocationServiceStop);
            }
        }

        public SettingsFragment() {
            super();
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        public void updateCurrentHome(){
            Preference preference = findPreference("home_set");
            if(preference == null ) return;
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());

            String home_address = sharedPreferences.getString("home_address", getString(R.string.default_home_address));
            String latitude = sharedPreferences.getString("home_latitude", "");
            String longitude = sharedPreferences.getString("home_longitude", "");

            preference.setSummary(home_address);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            SwitchPreferenceCompat servicePreference = findPreference("service");
            // fare un set checked in base allo STATO ATTUALE DEL SERVIZIO
            servicePreference.setChecked(ActivityDetectionService.getState());
            servicePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Log.d(TAG, "click");
                    Log.d(TAG, newValue.getClass().getSimpleName());
                    Boolean b = (Boolean) newValue;
                    Log.d(TAG, b.toString());
                    SwitchPreferenceCompat switchPreferenceCompat = (SwitchPreferenceCompat) preference;
                    switchPreferenceCompat.setChecked(b.booleanValue());
                    startOrStop(b.booleanValue());
                    return true;
                }
            });

            Preference preference = findPreference("home_set");
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());

            String home_address = sharedPreferences.getString("home_address", getString(R.string.default_home_address));
            preference.setSummary(home_address);

            preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    if(SystemClock.elapsedRealtime() - lastClick < 1000){
                        return true;
                    }
                    lastClick = SystemClock.elapsedRealtime();
                    Intent intent = new Intent(getContext(), HomeSetting.class);

                    startActivity(intent);
                    return true;
                }
            });

            preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());

                    String home_address = sharedPreferences.getString("home_address", getString(R.string.default_home_address));
                    preference.setSummary(home_address);

                    return true;
                }
            });
        }
        
    }

}



