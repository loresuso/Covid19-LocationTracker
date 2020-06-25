package it.unipi.dii.covida.ui.tracks;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Object shown in the list view containing all the tracks in TrackFragment
 */
public class Track {
    private String tracePoolId;
    private String time;
    private String distance;
    private String fromTo;
    private String date;

    public String getTracePoolId() {
        return tracePoolId;
    }

    public Track(){
        this.time = "10 min";
        this.distance = "10km";
        this.fromTo = "From Pisa to Livorno";
        SimpleDateFormat sdf = new SimpleDateFormat();
        sdf.applyPattern("dd/MM/yyyy");
        this.date = sdf.format(new Date());
    }

    public Track(String id, String time, String distance, String fromTo, String date) {
        this.tracePoolId = id;
        this.time = time;
        this.distance = distance;
        this.fromTo = fromTo;
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getDistance() {
        return distance;
    }

    public void setDistance(String distance) {
        this.distance = distance;
    }

    public String getFromTo() {
        return fromTo;
    }

    public void setFromTo(String fromTo) {
        this.fromTo = fromTo;
    }

    public String getDate() {
        return date;
    }

}
