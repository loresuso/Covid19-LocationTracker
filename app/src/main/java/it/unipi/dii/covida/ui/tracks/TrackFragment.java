package it.unipi.dii.covida.ui.tracks;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import it.unipi.dii.covida.R;
import it.unipi.dii.covida.localdb.LocationDatabaseManager;
import it.unipi.dii.covida.locationstore.TracePool;
import it.unipi.dii.covida.ui.maps.EditOnMapFragment;


/**
 * This fragment is the fragment responsible to show the list of track (TracePool) stored on the DB.
 * It is used by the MainActivity.
 */
public class TrackFragment extends Fragment {

    /*
     * Constants
     */
    private static final String TAG = TrackFragment.class.getSimpleName();

    /*
     * Private data members
     */
    private ArrayAdapter adapter;
    private ListView listView;
    private View root;
    private Long LastClickTimeFast = 0L;
    private Long LastClickTimeLong = 0L;




    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");

        AsyncGetFromDb asyncGetFromDb = new AsyncGetFromDb();

        TrackViewModel trackViewModel = ViewModelProviders.of(this).get(TrackViewModel.class);
        root = inflater.inflate(R.layout.fragment_track, container, false);
        root.setClickable(true);

        listView = root.findViewById(R.id.fragment_list_view);
        listView.setDivider(null);
        trackViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {

            }
        });

        asyncGetFromDb.execute();

        return root;
    }

    /**
     * This function is needed to let the execution of the AsyncTask from other classes (EditOnMapFragment)
     */
    public void refreshListView() {
        AsyncGetFromDb asyncGetFromDb = new AsyncGetFromDb();
        asyncGetFromDb.execute();
    }

    private void updateListView(final List<Track> tracks) {
        if(adapter != null ) adapter.clear();
        adapter = new ArrayAdapter<Track>(getContext(), R.layout.track_card, tracks){
            @Override
            public View getView(int position, View convertView, ViewGroup parent){
                // Get the view
                LayoutInflater inflater = getLayoutInflater();
                View itemView = inflater.inflate(R.layout.track_card,null,true);

                // Get current package name
                Track t = tracks.get(position);

                TextView time = itemView.findViewById(R.id.time);
                TextView distance = itemView.findViewById(R.id.distance);
                TextView fromTo = itemView.findViewById(R.id.fromTo);
                TextView date = itemView.findViewById(R.id.dateTrack);
                time.setText(t.getTime());
                distance.setText(t.getDistance());
                fromTo.setText(t.getFromTo());
                date.setText(t.getDate());
                CardView cardView = itemView.findViewById(R.id.cardView);
                if (position % 2 == 1) {
                    cardView.setBackgroundColor(Color.parseColor("#FFFFFF"));
                } else {
                    cardView.setBackgroundColor(Color.parseColor("#f5f5f5"));
                }
                return itemView;
            }
        };
        listView.setAdapter(adapter);

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int positionToRemove, long id) {
                if (SystemClock.elapsedRealtime() - LastClickTimeLong < 1000) {
                    return true;
                }
                LastClickTimeLong = SystemClock.elapsedRealtime();

                if(getChildFragmentManager().findFragmentByTag("mapfragment") != null){
                    return true;
                }
                AlertDialog.Builder alertDialog=new AlertDialog.Builder(getContext());
                alertDialog.setTitle("Delete?");
                alertDialog.setMessage("Are you sure you want to delete this element ?");
                alertDialog.setNegativeButton("Cancel", null);
                alertDialog.setPositiveButton("Ok", new AlertDialog.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Track track = tracks.get(positionToRemove);
                        tracks.remove(positionToRemove);
                        adapter.notifyDataSetChanged();
                        // delete also on DB
                        LocationDatabaseManager locationDatabaseManager = LocationDatabaseManager.getInstance(getContext());
                        locationDatabaseManager.deleteTracePoolById(track.getTracePoolId());
                    }});
                alertDialog.show();
                return true;
            }
        });

        // Set an item click listener for list view
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                LastClickTimeLong = SystemClock.elapsedRealtime();
                if (SystemClock.elapsedRealtime() - LastClickTimeFast < 1000) {
                    return;
                }
                LastClickTimeFast = SystemClock.elapsedRealtime();
                LastClickTimeLong = SystemClock.elapsedRealtime();

                if(getChildFragmentManager().findFragmentByTag("mapfragment") != null){
                    return;
                }

                String tracePoolId = tracks.get(i).getTracePoolId();
                FragmentManager fragmentManager = getChildFragmentManager();
                FragmentTransaction transaction = fragmentManager.beginTransaction();
                EditOnMapFragment editOnMapFragment = new EditOnMapFragment(tracePoolId);
                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);

                transaction.replace(R.id.relativeLayoutTrackList, editOnMapFragment, "mapfragment");
                transaction.addToBackStack(null);
                transaction.commit();
            }
        });
    }


    /**
     * This class extends AsyncTask to retrieve data from the DB asynchronously
     */
    private final class AsyncGetFromDb extends AsyncTask<Void, Void, List<Track>> {

        @Override
        protected List<Track> doInBackground(Void... params) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return createTrackList();
        }

        @Override
        protected void onPostExecute(final List<Track> tracks) {
            ProgressBar progressBar = root.findViewById(R.id.progressBar);
            progressBar.setVisibility(View.GONE);
            updateListView(tracks);
        }

        private List<Track> createTrackList() {
            List<Track> tracks = new LinkedList<>();

            LocationDatabaseManager locationDatabaseManager = LocationDatabaseManager.getInstance(getContext());
            List<TracePool> tracePools = locationDatabaseManager.getTracePoolByStartingTime(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 7); // one week ago

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

            Geocoder geocoder = new Geocoder(getContext(), Locale.getDefault());

            for(TracePool tracePool : tracePools) {
                // retrieve the date
                Date date = new Date(tracePool.getTimestamp());
                String dateString = sdf.format(date);

                // retrieve the distance
                String distance = tracePool.getLengthMeter() > 1000.0f ?
                        String.format(Locale.getDefault(), "%.3f Km", tracePool.getLengthMeter() / 1000.0f) :
                        String.format(Locale.getDefault(), "%.2f m", tracePool.getLengthMeter());

                // retrieve duration
                String duration = String.format(Locale.getDefault(), "%.2f min", ((float) tracePool.getTimeDuration()) / 60000.0f);

                // retrieve fromTo
                String to, from;
                try {
                    List<Address> addresses = geocoder.getFromLocation(tracePool.getFrom().getLatitude(), tracePool.getFrom().getLongitude(), 1);
                    from = addresses.get(0).getLocality();
                } catch (IndexOutOfBoundsException | IOException ex) {
                    from = "Unknown";
                }
                try {
                    List<Address> addresses = geocoder.getFromLocation(tracePool.getTo().getLatitude(), tracePool.getTo().getLongitude(), 1);
                    to = addresses.get(0).getLocality();
                } catch (IndexOutOfBoundsException | IOException ex) {
                    to = "Unknown";
                }
                String fromTo = String.format("From %s\nTo %s", from, to);

                Track trackToAdd = new Track(tracePool.getId(), duration, distance, fromTo, dateString);
                tracks.add(trackToAdd);
            }

            return tracks;
        }

    }

}