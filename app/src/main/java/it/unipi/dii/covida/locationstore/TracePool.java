package it.unipi.dii.covida.locationstore;

import android.location.Location;
import android.location.LocationManager;
import android.util.Pair;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * A TracePool is a store and manager for a group of Location(s).
 * You can add and remove Locations from the pool and specify which one of them compose a contiguous
 * trace and which one not.
 * A TracePool has a status: stopped or not-stopped. Normally is not-stopped, so new Location added
 * the pool represent a point linked to the previous (it is a continuous trace). When you call
 * TracePool::stop() you change the status to stopped and the next added Location is unlinked from
 * the previous. When a location is added, if the status is stopped, it is reverted to non-stopped.
 */
public class TracePool {

    private static long lastIdTime = 0L;
    private static long lastIdCount = 0L;

    private final String id;
    private long timestamp;
    private final LinkedList<ContinuousTrace> traces;
    private String name;
    private boolean stopped;

    /**
     * Create a TracePool
     */
    public TracePool() {
        //Generate an unique id
        timestamp = System.currentTimeMillis();
        long count = (timestamp != lastIdTime ? 0 : lastIdCount+1);
        id = timestamp + "-" + count;
        lastIdTime = timestamp;
        lastIdCount = count;

        name = "";

        traces = new LinkedList<>();
        stopped = true;
    }

    /**
     * Create a TracePool (DB USAGE ONLY)
     */
    public TracePool(long timestamp, String name, String id, List<ContinuousTrace> traces, boolean stopped) {
        this.timestamp = timestamp;
        this.id = id;
        this.traces = new LinkedList<ContinuousTrace>(traces);
        try {
            Collections.sort(this.traces, new Comparator<ContinuousTrace>() {
                @Override
                public int compare(ContinuousTrace ct1, ContinuousTrace ct2) {
                    if (ct1.getFromTime() - ct2.getToTime() <= 0)
                        return -1;
                    else
                        return 1;
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        this.stopped = stopped;
        this.name = name;
    }

    /**
     * Return the TracePool ID
     * @return the TracePool ID
     */
    public String getId() {
        return this.id;
    }

    /**
     * Add a new Location to the pool
     * @param location the location to add
     */
    public void addLocation(Location location) {
        if(traces.isEmpty() || traces.getLast().isEmpty()) {
            timestamp = getTimestamp();
        }
        if(stopped) {
            traces.add(new ContinuousTrace(id));
            stopped = false;
        }
        traces.getLast().addLocation(location);
    }

    /**
     * Add a new Location to the pool
     * @param latlng the location to add
     */
    public void addLocation(LatLng latlng) {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(latlng.latitude);
        location.setLongitude(latlng.longitude);
        location.setTime(System.currentTimeMillis());
        addLocation(location);
    }

    /**
     * Set the status of the pool to stopped.
     * When a pool is non-stopped, the next Location that is added is considered contiguous to the
     * previous. When, instead, the status is stopped, the next Location that is added is considered
     * not contiguous to the previous.
     */
    public void stop() {
        if(stopped)
            return;
        if(traces.isEmpty()) {
            stopped = true;
            return;
        }
        ContinuousTrace lastCt = traces.getLast();
        if(lastCt != null && !lastCt.isValid()) {
            traces.removeLast();
        }
        stopped = true;
    }

    /**
     * Return the status of the pool (stopped or non-stopped).
     * For more information, see the javadoc of TracePool::stop().
     * @return true if stopped else false
     */
    public boolean isStopped() {
        return stopped;
    }

    /**
     * Remove the location passed as parameter from the pool.
     * If the location is part of a contiguous trace, the trace is splitted in two distinct traces.
     * @param location the location to remove
     */
    public void removeLocation(Location location) {
        int i = 0;
        for(ContinuousTrace trace : traces) {
            if(trace.contains(location)) {
                Pair<ContinuousTrace, ContinuousTrace> pair = trace.removeAndSplit(location);
                if(pair.second.isValid())
                    traces.add(i,pair.second);
                if(pair.first.isValid())
                    traces.add(i,pair.first);
                traces.remove(trace);
                return;
            }
            ++i;
        }
    }

    /**
     * Remove the location passed as parameter from the pool.
     * If the location is part of a contiguous trace, the trace is splitted in two distinct traces.
     * @param latLng the LatLng that corresponds to the Location to remove
     */
    public void removeLocation(LatLng latLng) {
        int i = 0;
        for (ContinuousTrace trace : traces) {
            if (trace.contains(latLng)) {
                Pair<ContinuousTrace, ContinuousTrace> pair = trace.removeAndSplit(latLng);
                if(pair.second.isValid())
                    traces.add(i,pair.second);
                if(pair.first.isValid())
                    traces.add(i,pair.first);
                traces.remove(trace);
                return;
            }
            ++i;
        }
    }

    ///**
    // * Remove the Locations passed as parameter and the Locations between them from the pool.
    // * If the Locations are both part of the same ContinuousTrace, the trace is splitted in two
    // * distinct ContinuousTraces.
    // * @param locationFrom the "starting point" (included)
    // * @param locationTo the "end point" (excluded)
    // */
    /*public void removeLocation(Location locationFrom, Location locationTo) {
        ContinuousTrace toRemove = null;
        int i = 0;
        for(ContinuousTrace trace : traces) {
            if(trace.contains(locationFrom) && trace.contains((locationTo))) {
                Pair<ContinuousTrace, ContinuousTrace> pair = trace.removeAndSplitRange(locationFrom,locationTo);
                if(!pair.second.isEmpty())
                    traces.add(i,pair.second);
                if(!pair.first.isEmpty())
                    traces.add(i,pair.first);
                toRemove = trace;
                break;
            }
            ++i;
        }
        if(toRemove == null) {
            throw new IllegalArgumentException("Location to remove not found");
        }
        traces.remove(toRemove);
    }*/

    /**
     * Remove all locations in a given radius starting from a given center
     * @param center The center of the search
     * @param radius The radius of the search
     * @return The number of locations removed
     */
    public int removeLocationsInGivenRadius(Location center, double radius) {
        List<Location> constantLocationList = getLocations();
        int removeCount = 0;
        for(Location loc : constantLocationList) {
            if(loc.distanceTo(center) <= radius) {
                removeLocation(loc);
                ++removeCount;
            }
        }
        return removeCount;
    }

    /**
     * Remove the Location(s) identified by the LatLng(s) passed as parameter from the pool.
     * During the removal process, if a location is part of a contiguous trace, the trace is
     * splitted in two distinct traces.
     * @param latLngs the list of LatLngs that identify the Locations to remove
     */
    public void removeLocations(List<LatLng> latLngs) {
        for(LatLng latLng : latLngs)
            removeLocation(latLng);
    }

    /**
     * Return the full list of contiguous traces that compose the TracePool
     * @return the full list of contiguous traces that compose the TracePool
     */
    public List<ContinuousTrace> getTraces() {
        return Collections.unmodifiableList(traces);
    }

    /**
     * It returns an unmodifiable list of all Locations stored inside the TracePool without any
     * information about continuity.
     * If you need to retrieve the traces (contiguous GPS points), use getTraces().
     * @return a list of all Locations stored inside the TracePool
     */
    public List<Location> getLocations() {
        LinkedList<Location> pool = new LinkedList<>();
        for(ContinuousTrace trace : traces) {
            pool.addAll(trace.getLocations());
        }
        return Collections.unmodifiableList(pool);
    }

    /**
     * Return the number of contiguous traces that compose the TracePool
     * @return the number of contiguous traces that compose the TracePool
     */
    public int getContinuousTraceCount() {
        return traces.size();
    }

    /**
     * Get the number of Location(s) stored inside the TracePool
     * @return the number of Location(s) stored inside the TracePool
     */
    public int getLocationCount() {
        int count = 0;
        for(ContinuousTrace trace : traces)
            count += trace.getLocationCount();
        return count;
    }

    /**
     * Check if there are no Locations stored in this ThreadPool
     * @return true if there are no Locations stored in this ThreadPool
     */
    public boolean isEmpty() {
        return getLocationCount() == 0;
    }

    /**
     * Return a String with a JSON representation of this instance
     * @return a String with a JSON representation of this instance
     */
    public String toJsonString() {
        JSONObject root = new JSONObject();
        try {
            root.put("stopped", stopped);
            root.put("timestamp", timestamp);
            root.put("name", name);
            root.put("id", id);
            JSONArray tracesJson = new JSONArray();
            for(ContinuousTrace continuousTrace : traces) {
                tracesJson.put(new JSONObject(continuousTrace.toJsonString()));
            }
            root.put("traces",tracesJson);
        }
        catch (JSONException ex) {
            ex.printStackTrace();
        }
        return root.toString();
    }

    /**
     * Get the creation timestamp
     * @return the creation timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Set the name of this TracePool
     * @param name the new name for this TracePool
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the name of this TracePool
     * @return the name of this TracePool
     */
    public String getName(){
        return name;
    }

    /**
     * Return the length (in meters) of the Path
     * @return the length in meters of the trace
     */
    public float getLengthMeter() {
        if(traces.isEmpty())
            return 0.0f;
        float distance = 0.0f;
        for(ContinuousTrace continuousTrace : traces) {
            distance += continuousTrace.getLengthMeter();
        }
        return distance;
    }

    /**
     * Return how long the TracePool lasted from the first to the last Location in milliseconds
     * @return how long the TracePool lasted from the first to the last Location in milliseconds
     */
    public long getTimeDuration() {
        if(traces.isEmpty())
            return 0L;
        return traces.getLast().getLocations().get(traces.getLast().getLocations().size() - 1).getTime() -
                traces.getFirst().getLocations().get(0).getTime();
    }

    /**
     * Return the first Location of an entire TracePool
     * @return the first location of the current TracePool as a Location object
     */
    public Location getFrom() {
        if(traces.isEmpty())
            return null;
        return traces.getFirst().getFrom();
    }

    /**
     * Return the last Location of an entire TracePool
     * @return the last location of the current TracePool as a Location object
     */
    public Location getTo() {
        if(traces.isEmpty())
            return null;
        return traces.getLast().getTo();
    }

    /**
     * Filter this TracePool in search of glitches to remove and remove all ContinuousTraces that
     * are no more valid after this operation.
     * @return the number of Locations removed
     */
    public int filter() {
        if(traces.isEmpty())
            return 0;
        int count = 0;
        final LinkedList<ContinuousTrace> toRemove = new LinkedList<>();
        for(ContinuousTrace continuousTrace : traces) {
            count += continuousTrace.filter();
            if(!continuousTrace.isValid()) {
                toRemove.add(continuousTrace);
                count += continuousTrace.getLocationCount();
            }
        }
        traces.removeAll(toRemove);
        return count;
    }

    /**
     * Get an estimation of the speed of the last sampled locations of the user
     * @return the average speed of the last two locations as a long (0 if not enough samples were collected)
     */
    public float getLastSpeed() {
        if(traces.isEmpty())
            return 0;
        float speed = traces.getLast().getSpeed();
        return speed;
    }

}
