package it.unipi.dii.covida.ui.maps;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.BounceInterpolator;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.LinkedList;
import java.util.List;

import it.unipi.dii.covida.R;
import it.unipi.dii.covida.localdb.LocationDatabaseManager;
import it.unipi.dii.covida.locationstore.ContinuousTrace;
import it.unipi.dii.covida.locationstore.TracePool;
import it.unipi.dii.covida.ui.tracks.TrackFragment;


/**
 * This Fragment is responsible to offer the user a Google Map view to edit his sessions
 */
public class EditOnMapFragment extends Fragment implements OnMapReadyCallback, GoogleMap.OnPolylineClickListener, GoogleMap.OnMarkerClickListener, GoogleMap.OnMapLoadedCallback {

    /*
     * Constants
     */
    private static final String TAG = EditOnMapFragment.class.getSimpleName();

    /*
     * Private data members
     */
    private String tracePoolId;
    private GoogleMap googleMap;
    private List<Polyline> lines;
    private Polyline selectedLine;
    private int status = 0;
    private LatLng start;
    private LatLng end;
    private Marker markerStart;
    private Marker markerEnd;
    private TracePool tracePool;
    private MapView mapView;
    private Handler handler;
    private Runnable markerAnimation;
    private static class BounceAnimation implements Runnable {

        private final long start, duration;
        private final BounceInterpolator interpolator;
        private final Handler handler;
        private final Marker marker;

        private BounceAnimation(long start, long duration, Marker marker, Handler handler) {
            this.start = start;
            this.duration = duration;
            this.marker = marker;
            this.handler = handler;
            this.interpolator = new BounceInterpolator();
        }

        @Override
        public void run() {
            long elapsed = SystemClock.uptimeMillis() - start;
            float t = Math.max(1 - interpolator.getInterpolation((float) elapsed / duration), 0f);
            marker.setAnchor(0.5f, 1.0f + 0.4f * t);

            if (t > 0.0) {
                // Post again later.
                handler.postDelayed(this, 12L);
            }
        }
    }

    private Context context;

    public EditOnMapFragment(String tracePoolId){
        this.tracePoolId = tracePoolId;
    }

    public EditOnMapFragment() {}

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_map, container, false);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mapView = view.findViewById(R.id.map_view);
        if(mapView != null) {
            mapView.onCreate(null);
            mapView.onResume();
            mapView.getMapAsync(this);
        }
        else {
            Log.d(TAG, "NULL");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate() della editOnMap");
        handler = new Handler();
        if(savedInstanceState != null && savedInstanceState.getString("tracePoolId") != null)
            tracePoolId = savedInstanceState.getString("tracePoolId");
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        MapsInitializer.initialize(getContext());
        this.googleMap = googleMap;
        googleMap.setOnMarkerClickListener(this);
        googleMap.setOnPolylineClickListener(this);
        googleMap.setOnMapLoadedCallback(this);
    }

    private void zoomToFitEverythingOnMap(List<LatLng> list){
        if(list == null || list.isEmpty()) return;
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng latLng : list)
            builder.include(latLng);
        int padding = 100;
        LatLngBounds latLngBounds = builder.build();
        try {
            final CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(latLngBounds, padding);
            googleMap.animateCamera(cameraUpdate);
        } catch(Exception e) {
            Log.d(TAG, "Detected change needing for editing the map; function moveCamera() is used");
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(list.get(0), 17));
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        marker.showInfoWindow();
        if (status == 0) {
            handler.removeCallbacks(markerAnimation);
            markerAnimation = new BounceAnimation(SystemClock.uptimeMillis(), 800L, marker, handler);
            handler.post(markerAnimation);
            marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
            start = marker.getPosition();
            markerStart = marker;
            status = 1;
            Log.d(TAG, "status: " + status);
        } else {
            marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
            end = marker.getPosition();
            markerEnd = marker;
            showDialog();
            status = 0;
        }
        return true;
    }

    @Override
    public void onPolylineClick(Polyline polyline) {
        List<LatLng> list = polyline.getPoints();
        for (LatLng latLng : list)
            googleMap.addMarker(new MarkerOptions().position(latLng)
                     .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        for(Polyline p : lines)
            p.setClickable(false);
        selectedLine = polyline;
    }

    private List<LatLng> getSubList(Polyline line, LatLng start, LatLng end){
        List<LatLng> list = line.getPoints();
        List<LatLng> toDelete;
        int s = list.indexOf(start);
        int e = list.indexOf(end);
        if(e <= s){
            int tmp = e;
            e = s;
            s = tmp;
        }
        /*if(s == 0)
            s = s - 1;
        if(e == list.size() - 1)
            e = e + 1;
        toDelete = list.subList(s + 1, e);*/
        toDelete = list.subList(s,e);
        return toDelete;
    }

    private void showDialog(){
        AlertDialog alertDialog = new AlertDialog.Builder(getContext())
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        deletePartOfTrack();
                        dialog.dismiss();
                    }})
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        status = 0;
                        drawAll();
                        dialog.dismiss();
                    }})
                .create();
        alertDialog.setTitle("Deleting part of a track");
        alertDialog.setMessage("You're going to delete from the green to the red marker. Are you sure ?");
        WindowManager.LayoutParams wmlp = alertDialog.getWindow().getAttributes();

        wmlp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        alertDialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
        alertDialog.show();
    }

    private void deletePartOfTrack(){
        List<LatLng> listOfPointToDelete;
        if(start.equals(end)) {
            listOfPointToDelete = new LinkedList<>();
            listOfPointToDelete.add(start);
        } else {
            listOfPointToDelete = getSubList(selectedLine, start, end);
            if (listOfPointToDelete.isEmpty()) {
                status = 0;
                markerStart.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
                markerEnd.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
                return;
            }
        }

        LocationDatabaseManager locationDatabaseManager = LocationDatabaseManager.getInstance(context);
        tracePool.removeLocations(listOfPointToDelete);
        locationDatabaseManager.deleteTracePoolById(tracePoolId);
        if(tracePool.getTraces() != null && !tracePool.getTraces().isEmpty()) {
            locationDatabaseManager.addTracePool(tracePool);
        }
        TrackFragment parentFrag = ((TrackFragment)this.getParentFragment());
        parentFrag.refreshListView();

        drawAll();

        for(Polyline p : lines)
            p.setClickable(true);
    }

    private void drawAll() {
        selectedLine = null;
        googleMap.clear();
        LinkedList<LatLng> allLatLngs = new LinkedList<>();
        lines = new LinkedList<>();
        for(ContinuousTrace continuousTrace : tracePool.getTraces()){
            List<LatLng> tmp = continuousTrace.getLatLngs();
            allLatLngs.addAll(tmp);
            Polyline line = googleMap.addPolyline(new PolylineOptions()
                    .clickable(true)
                    .color(context.getColor(R.color.colorAccent))
                    .width(15)
                    .addAll(tmp));
            lines.add(line);
        }
        zoomToFitEverythingOnMap(allLatLngs);
    }

    @Override
    public void onMapLoaded() {
        if(googleMap == null) return;
        AsyncDraw asyncDraw = new AsyncDraw();
        asyncDraw.execute(tracePoolId);
    }


    private final class AsyncDraw extends AsyncTask<String, Void, TracePool> {

        @Override
        protected TracePool doInBackground(String... params) {
            String tracePoolId = params[0];
            LocationDatabaseManager locationDatabaseManager = LocationDatabaseManager.getInstance(context);
            tracePool = locationDatabaseManager.getTracePoolById(tracePoolId);
            return tracePool;
        }

        @Override
        protected void onPostExecute(final TracePool tracePool_) {
            tracePool = tracePool_;
            drawAll();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("tracePoolId", tracePoolId);
    }

}
