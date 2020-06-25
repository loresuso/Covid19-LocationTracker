package it.unipi.dii.covida.locationstore;

import android.location.Location;
import android.util.Pair;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;


/**
 * A class that represent a list of contiguous GPS points (Location).
 * An instance of this class will represent a continuous Polyline on the map.
 */
public class ContinuousTrace {

    /*
     * Data members
     */
    private final String tracePoolId;
    private final LinkedList<Location> locations;

    /*
     * Private methods
     */
    private ContinuousTrace(String tracePoolId, LinkedList<Location> locations) {
        this.tracePoolId = tracePoolId;
        this.locations = locations;
    }

    private static boolean corresponds(@NonNull Location location, @NonNull LatLng latLng) {
        return location.getLatitude() == latLng.latitude && location.getLongitude() == latLng.longitude;
    }

    public String getTracePoolId() {
        return tracePoolId;
    }

    /**
     * Default constructor
     */
    public ContinuousTrace(String tracePoolId) {
        this.tracePoolId = tracePoolId;
        this.locations = new LinkedList<>();
    }

    /**
     * Add a location to the ContinuousTrace
     * @param location the Location to add
     */
    public void addLocation(Location location) {
        locations.add(location);
    }

    /**
     * Return the list of locations added
     * @return an unmodifiableList containing all the Location(s) stored
     */
    public List<Location> getLocations() {
        return Collections.unmodifiableList(locations);
    }

    /**
     * Return the length (in meters) of the Path
     * @return the length in meters of the trace
     */
    public float getLengthMeter() {
        if(locations.size() <= 1)
            return 0.0f;
        float distance = 0.0f;
        Iterator<Location> iterator = locations.iterator();
        Location previous = iterator.next();
        while(iterator.hasNext()) {
            Location current = iterator.next();
            distance += previous.distanceTo(current);
            previous = current;
        }
        return distance;
    }

    /**
     * Returns the number of Locations
     * @return the number of Location(s) stored
     */
    public int getLocationCount() {
        return locations.size();
    }

    /**
     * Return true if the trace does not contain Locations
     * @return true if no Locations are stored
     */
    public boolean isEmpty() {
        return locations.size() == 0;
    }

    /**
     * Return true if this ContinuousTrace is valid.
     * A ContinuousTrace is valid if it contains at least 2 Locations: with only 1 Location (or 0)
     * there are no traces (so no ContinuousTraces).
     * @return true if valid
     */
    public boolean isValid() {
        return locations.size() >= 2;
    }

    /**
     * Remove the Location specified by the parameter from the trace and return a couple of
     * ContinuousTrace made by the two groups of Locations formed by the operation
     * @param location the "cut point"
     * @return
     */
    public Pair<ContinuousTrace,ContinuousTrace> removeAndSplit(Location location) {
        int index = locations.indexOf(location);
        if(index == -1) {
            throw new IllegalArgumentException("Impossible to remove location");
        }
        Pair<ContinuousTrace,ContinuousTrace> pair = new Pair<>(
                new ContinuousTrace(tracePoolId,new LinkedList<>(locations.subList(0,index))),
                new ContinuousTrace(tracePoolId,new LinkedList<>(locations.subList(index+1,locations.size())))
        );
        return pair;
    }

    /**
     * Remove the Location(s) specified by the parameter from the trace and return a couple of
     * ContinuousTrace made by the two groups of Locations formed by the operation
     * @param latLng the "cut point"
     * @return
     */
    public Pair<ContinuousTrace,ContinuousTrace> removeAndSplit(LatLng latLng) {
        int i = 0;
        for(Location location : locations) {
            if(corresponds(location,latLng))
                break;
            ++i;
        }
        if(i >= locations.size())
            throw new IllegalArgumentException("Impossible to remove location");
        return new Pair<>(
                new ContinuousTrace(tracePoolId,new LinkedList<>(locations.subList(0,i))),
                new ContinuousTrace(tracePoolId,new LinkedList<>(locations.subList(i+1,locations.size())))
        );
    }

    /**
     * Remove a set of locations that goes from the locationFrom to the locationTo (the users has
     * selected two markers that delimit a path to be removed), and return a couple of
     * ContinuousTrace made by the two groups of Locations formed by the operation
     * @param locationFrom the "starting point" (included)
     * @param locationTo the "end point" (excluded)
     * @return a pair od ContinuousTrace
     */
    public Pair<ContinuousTrace,ContinuousTrace> removeAndSplitRange(Location locationFrom, Location locationTo) {
        int indexFrom = locations.indexOf(locationFrom);
        int indexTo = locations.indexOf(locationTo);
        if (indexFrom == -1 || indexTo == -1) {
            throw new IllegalArgumentException("Impossible to remove range of locations");
        }
        Pair<ContinuousTrace,ContinuousTrace> pair = new Pair<>(
                new ContinuousTrace(tracePoolId,new LinkedList<>(locations.subList(0,indexFrom))),
                new ContinuousTrace(tracePoolId,new LinkedList<>(locations.subList(indexTo,locations.size())))
        );
        return pair;
    }

    /**
     * Returns true if the trace contains the Location specified by the parameter
     * @param location the location to check if it is stored
     * @return true if the trace contains location
     */
    public boolean contains(Location location) {
        return locations.contains(location);
    }

    /**
     * Return true if the trace contains a Location that geographically corresponds to the LatLng
     * passed as argument
     * @param latLng the LatLng to check
     * @return true if the Location if found
     */
    public boolean contains(LatLng latLng) {
        if(latLng == null)
            return false;
        for(Location location : locations) {
            if(corresponds(location, latLng))
                return true;
        }
        return false;
    }

    /**
     * Return a String with a JSON representation of this instance
     * @return a String with a JSON representation of this instance
     */
    public String toJsonString() {
        JSONObject root = new JSONObject();
        try {
            root.put("tracePoolId",tracePoolId);
            JSONArray array = new JSONArray();
            for (Location loc : locations) {
                JSONObject locJson = new JSONObject();
                locJson.put("provider", loc.getProvider());
                locJson.put("latitude", loc.getLatitude());
                locJson.put("longitude", loc.getLongitude());
                locJson.put("time", loc.getTime());
                array.put(locJson);
            }
            root.put("locations", array);
        }
        catch (JSONException ex) {
            ex.printStackTrace();
        }
        return root.toString();
    }

    /**
     * Return the first location of a ContinuousTrace
     * @return the first location of the current ContinuousTrace as a Location object
     */
    public Location getFrom() {
        if(locations.isEmpty()) {
            return null;
        }
        return locations.getFirst();
    }

    /**
     * Return the last location of a ContinuousTrace
     * @return the last location of the current ContinuousTrace as a Location object
     */
    public Location getTo() {
        if(locations.isEmpty()) {
            return null;
        }
        return locations.getLast();
    }

    /**
     * Returns all Locations stored in this ContinuousTrace converted in LatLngs
     * @return all Locations stored in this ContinuousTrace converted in LatLngs
     */
    public List<LatLng> getLatLngs(){
        List<LatLng> list = new LinkedList<>();
        for(Location location : locations)
            list.add(new LatLng(location.getLatitude(), location.getLongitude()));
        return list;
    }

    /**
     * Get the timestamp of the first Location (GPS sampling)
     * @return 0L if there are no Locations stored, otherwise the timestamp of the first Location
     */
    public long getFromTime() {
        if(locations.isEmpty()) {
            return 0L;
        }
        return locations.getFirst().getTime();
    }

    /**
     * Get the timestamp of the last Location (GPS sampling)
     * @return 0L if there are no Locations stored, otherwise the timestamp of the last Location
     */
    public long getToTime() {
        if(locations.isEmpty()) {
            return 0L;
        }
        return locations.getLast().getTime();
    }

    /**
     * Filter this ContinuousTrace in search of glitches to remove
     * @return the number of glitches removed
     */
    public int filter() {
        if(locations.size() < 3)
            return 0;
        int count = 0;
        final float distanceFactor = 100.0f;
        for(int i = 1; i < locations.size()-1; ++i) {
            final Location left = locations.get(i-1);
            final Location center = locations.get(i);
            final Location right = locations.get(i+1);
            final float referenceDistance = left.distanceTo(right);
            if(center.distanceTo(left) > referenceDistance*distanceFactor && center.distanceTo(right) > referenceDistance*distanceFactor) {
                locations.remove(i);
                ++count;
                --i;
            }
        }
        return count;
    }

    /**
     * Get the average speed of an entire ContinuousTrace in m/s
     * @return the average speed of an entire ContinuousTrace as a long
     */
    public float getAvgSpeed() {
        if(!this.isValid())
            return 0L;
        float totalDistance = this.getLengthMeter();
        long totalTime = (this.getToTime() - this.getFromTime())*1000;
        return totalDistance/totalTime;
    }

    /**
     * Get the average speed of the user within the last two sampled locations in m/s
     * @return the average speed within the last two locations as a long
     */
    public float getSpeed() {
        if(!this.isValid())
            return 0.0f;
        Iterator<Location> reverseIterator = locations.descendingIterator();
        Location lastLoc = reverseIterator.next();
        Location prevLastLoc = reverseIterator.next();
        float distance = prevLastLoc.distanceTo(lastLoc);
        float time = (float)((lastLoc.getTime() - prevLastLoc.getTime())/1000);
        return distance/time;
    }

}