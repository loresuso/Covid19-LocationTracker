package it.unipi.dii.covida;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Menu;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.navigation.NavigationView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.util.List;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import it.unipi.dii.covida.activityrecognition.ActivityDetectionService;
import it.unipi.dii.covida.ui.settings.SettingsActivity;

import static androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE;

public class MainActivity extends AppCompatActivity /*implements NavigationView.OnNavigationItemSelectedListener*/{

    private AppBarConfiguration mAppBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // UI
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow, R.id.nav_track)
                .setDrawerLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);
        // SERVICE AND PERMISSIONS CONFIGURATION
        if(!checkMyPermissions()) {
            askPermissions();
        } else {
            startActivityDetectionService();
        }


    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("MainActivity", "onStart()");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        if(item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    private boolean checkMyPermissions() {
        boolean fineLocation = getApplicationContext().checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED;
        boolean coarseLocation =  getApplicationContext().checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION") == PackageManager.PERMISSION_GRANTED;
        boolean internet = getApplicationContext().checkSelfPermission("android.permission.INTERNET") == PackageManager.PERMISSION_GRANTED;
        Log.d("Permissions", "check result: " + (fineLocation || coarseLocation || internet));
        if(Build.VERSION.SDK_INT < 29)
            return fineLocation && coarseLocation && internet;
        else {
            boolean activityRecognition = getApplicationContext().checkSelfPermission("com.google.android.gms.permission.ACTIVITY_RECOGNITION") == PackageManager.PERMISSION_GRANTED;
            return fineLocation && coarseLocation && internet && activityRecognition;
        }
    }

    // Dexter library used to handle permissions easily
    //@RequiresApi(api = Build.VERSION_CODES.Q)
    protected void askPermissions(){
        MultiplePermissionsListener dialogMultiplePermissionsListener = new MultiplePermissionsListener() {
            @Override
            public void onPermissionsChecked(MultiplePermissionsReport multiplePermissionsReport) {
                if(multiplePermissionsReport.areAllPermissionsGranted()) {
                    Log.d("Permissions", "All permissions are granted");
                    startActivityDetectionService();
                }
                else {
                    Log.d("Permissions", "At least one permission is denied");
                    List<PermissionDeniedResponse> list = multiplePermissionsReport.getDeniedPermissionResponses();
                    for(PermissionDeniedResponse p : list){
                        Log.d("Permissions", "denied" + p.getPermissionName());
                    }

                }
            }

            @Override
            public void onPermissionRationaleShouldBeShown(List<PermissionRequest> list, PermissionToken permissionToken) {
                permissionToken.continuePermissionRequest();
            }
        };

        if(Build.VERSION.SDK_INT < 29)
            Dexter.withContext(this)
                    .withPermissions(
                            Manifest.permission.INTERNET,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    ).withListener(dialogMultiplePermissionsListener).check();
        else
            Dexter.withContext(this)
                    .withPermissions(
                            Manifest.permission.INTERNET,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACTIVITY_RECOGNITION
                    ).withListener(dialogMultiplePermissionsListener).check();
    }

    public void startActivityDetectionService() {
        Log.d("MainActivity","startActivityDetectionService()");
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean savedState = sharedPref.getBoolean(getString(R.string.backgroudServicesEnabled),true);
        if(savedState) {
            boolean displayToast = !ActivityDetectionService.getState();
            Intent intent = new Intent(MainActivity.this, ActivityDetectionService.class);
            intent.setAction(ActivityDetectionService.ACTION_START);
            startForegroundService(intent);
            if(displayToast)
                Toast.makeText(getApplicationContext(), "Service started", Toast.LENGTH_SHORT).show();
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean(getString(R.string.backgroudServicesEnabled), true);
            editor.commit();
        }
    }

    public void stopActivityDetectionService() {
        Log.d("MainActivity","stopActivityDetectionService()");
        Intent intent = new Intent(MainActivity.this, ActivityDetectionService.class);
        intent.setAction(ActivityDetectionService.ACTION_STOP);
        stopService(intent);
        Toast.makeText(getApplicationContext(), "Service stopped", Toast.LENGTH_SHORT).show();
    }
}
