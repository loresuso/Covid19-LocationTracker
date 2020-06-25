package it.unipi.dii.covida.localdb;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import it.unipi.dii.covida.locationstore.ContinuousTrace;
import it.unipi.dii.covida.locationstore.TracePool;

/**
 * This class is responsible to establish a connection and manage the local SQL database
 */
public class LocationDatabaseManager extends SQLiteOpenHelper {

    private final static String TAG = LocationDatabaseManager.class.getSimpleName();
    private final static String DB_NAME = "locations.db";
    private final static String CONTINUOUSTRACE_TABLE_NAME = "continuoustraces";
    private final static String TRACEPOOL_TABLE_NAME = "tracepools";

    private Context ctx;
    private static LocationDatabaseManager instance = null;

    /**
     * Create a new database instance
     * @param context the application context
     * Context appContext = context.getApplicationContext();
     */
    private LocationDatabaseManager(Context context) {
        super(context, DB_NAME, null , 1);
        this.ctx = context;
    }

    /**
     * Return the Database instance
     * @param context the context
     */
    public static LocationDatabaseManager getInstance(Context context){
        if(instance == null) {
            synchronized (LocationDatabaseManager.class) {
                if (instance == null) {
                    Context appContext = context.getApplicationContext();
                    instance = new LocationDatabaseManager(appContext);
                }
            }
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // called only once when database is created for the first time.
        Log.d(TAG, "onCreate()");
        db.execSQL( " CREATE TABLE IF NOT EXISTS " + CONTINUOUSTRACE_TABLE_NAME + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT , " +
                "tracePoolId TEXT REFERENCES "+ TRACEPOOL_TABLE_NAME + " ON DELETE CASCADE, " +
                "locations TEXT NOT NULL" +
                ") "
        );

        db.execSQL( " CREATE TABLE IF NOT EXISTS " + TRACEPOOL_TABLE_NAME + " (" +
                "tracePoolId TEXT PRIMARY KEY, " +
                "tracePoolName TEXT, " +
                "tracePoolTimestamp BIGINT NOT NULL" +
                ") "
        );

        db.execSQL("CREATE UNIQUE INDEX index_timestamp ON " + TRACEPOOL_TABLE_NAME + " (tracePoolTimestamp)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // called when database needs to be upgraded.
        Log.d(TAG, "onUpgrade");
        db.execSQL("DROP TABLE IF EXISTS "+ CONTINUOUSTRACE_TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS "+ TRACEPOOL_TABLE_NAME);
        onCreate(db);
    }


    /* ################# ADD ################### */

    /**
     * Add the TracePool to the database
     * @param tracePool the TracePool
     * @return true if the database is updated
     */
    public boolean addTracePool(TracePool tracePool) {
        Log.d(TAG, "addTracePool() called for a TracePool with tracePoolId = " + tracePool.getId());
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            db.beginTransaction();

            ContentValues newValues_tracePool = new ContentValues();
            newValues_tracePool.put("tracePoolId", tracePool.getId());
            newValues_tracePool.put("tracePoolName", tracePool.getName());
            newValues_tracePool.put("tracePoolTimestamp", tracePool.getTimestamp());
            db.insert(TRACEPOOL_TABLE_NAME, null, newValues_tracePool);

            List<ContinuousTrace> list = tracePool.getTraces();
            for (ContinuousTrace ct : list) {
                ContentValues newValues_ct = new ContentValues();
                newValues_ct.put("tracePoolId", tracePool.getId());
                JSONObject j = new JSONObject(ct.toJsonString());
                String loc_str = j.get("locations").toString();
                newValues_ct.put("locations", loc_str);
                db.insert(CONTINUOUSTRACE_TABLE_NAME, null, newValues_ct);
            }

            db.setTransactionSuccessful();

        }catch(SQLException e) {
            e.printStackTrace();
            if(db != null){
                db.close();
            }
            return false;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }finally {
            if(db != null) {
                db.endTransaction();
            }
        }
        return true;
    }

    /* ################# GET ################### */

    /**
     * Return the TracePool by the TracePool ID
     * @param tracePoolId_ the TracePool ID
     * @return the TracePool
     */
    public TracePool getTracePoolById(String tracePoolId_) {
        Log.d(TAG, "getTracePoolById() called with tracePoolId = " + tracePoolId_);
        SQLiteDatabase db = null;
        TracePool tracePool = null;
        try {
            db = this.getReadableDatabase();
            String query = "SELECT * FROM " + TRACEPOOL_TABLE_NAME + " AS tp JOIN " + CONTINUOUSTRACE_TABLE_NAME + " AS cp " +
                    "ON tp.tracePoolId = cp.tracePoolId " +
                    "WHERE tp.tracePoolId = ?";

            if(tracePoolId_ == null) return null;
            Cursor cursor = db.rawQuery(query, new String[]{tracePoolId_});

            if (cursor.moveToFirst()) {
                List<ContinuousTrace> continuousTraceList = new ArrayList<>();
                Long tracePoolTimestamp;
                String tracePoolName;
                String tracePoolId;
                do {
                    tracePoolId = cursor.getString(cursor.getColumnIndex("tracePoolId"));
                    tracePoolName = cursor.getString(cursor.getColumnIndex("tracePoolName"));
                    tracePoolTimestamp = cursor.getLong(cursor.getColumnIndex("tracePoolTimestamp"));
                    ContinuousTrace ct = new ContinuousTrace(cursor.getString(cursor.getColumnIndex("tracePoolId")));
                    JSONArray arrayLocations = new JSONArray(cursor.getString(cursor.getColumnIndex("locations")));
                    for (int i = 0; i < arrayLocations.length(); ++i) {
                        JSONObject json_loc = (JSONObject) arrayLocations.get(i);
                        Double longitude = json_loc.getDouble("longitude");
                        Double latitude = json_loc.getDouble("latitude");
                        String provider = json_loc.getString("provider");
                        Long time = json_loc.getLong("time");
                        Location loc = new Location(provider);
                        loc.setLongitude(longitude);
                        loc.setLatitude(latitude);
                        loc.setTime(time);
                        ct.addLocation(loc);
                    }
                    continuousTraceList.add(ct);
                } while (cursor.moveToNext());
                tracePool = new TracePool(tracePoolTimestamp, tracePoolName, tracePoolId, continuousTraceList, true);
            }
            cursor.close();
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }catch (SQLException e) {
            e.printStackTrace();
            if(db != null) {
                db.close();
            }
            return null;
        }
        return tracePool;
    }

    /**
     * Return the TracePool list by starting time
     * @param startMills the starting time
     * @return the list of TracePool
     */
    public List<TracePool> getTracePoolByStartingTime(Long startMills) {
        Log.d(TAG, "getTracePoolByStartingTime() called with startMillis = " + startMills);
        SQLiteDatabase db = null;
        ArrayList<TracePool> tracePoolList = new ArrayList<>();
        try {
            db = this.getReadableDatabase();
            db.beginTransaction();
            String query = "SELECT * FROM " + TRACEPOOL_TABLE_NAME + " WHERE tracePoolTimestamp > ? ORDER BY tracePoolId";
            Cursor cursor = db.rawQuery(query, new String[]{Long.toString(startMills)});
            db.setTransactionSuccessful();
            db.endTransaction();
            if(cursor.moveToFirst()) {
                do {
                    String tracePoolId = cursor.getString(cursor.getColumnIndex("tracePoolId"));
                    TracePool tp_tmp = getTracePoolById(tracePoolId);
                    if(tp_tmp != null) {
                        tracePoolList.add(tp_tmp);
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        } catch (SQLException e) {
            e.printStackTrace();
            if(db != null){
                db.close();
            }
        }
        return tracePoolList;
    }


    /**
     * Return the TracePool list by starting date
     * @param date the starting date
     * @return the list of TracePool
     */
    public List<TracePool> getTracePoolByStartingTime(Date date){
        return getTracePoolByStartingTime(date.getTime());
    }

    /* ###############  DELETE ################### */

    public boolean deleteTracePoolById(String tracePoolId) {
        Log.d(TAG, "deleteTracePoolById() called with tracePoolId = " + tracePoolId);
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();

            String query = "DELETE FROM " + TRACEPOOL_TABLE_NAME + " WHERE tracePoolId = \"" + tracePoolId + "\"";
            db.execSQL(query);

            query = "DELETE FROM " + CONTINUOUSTRACE_TABLE_NAME + " WHERE tracePoolId = \"" + tracePoolId + "\"";
            db.execSQL(query);

        } catch (SQLException e) {
            e.printStackTrace();
            if(db != null) {
                db.close();
            }
            return false;
        }
        return true;
    }

    /**
     * Delete all the ContinuousTrace record from the database
     * @return true if the database is updated
     */
    public boolean deleteAll() {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            String query = "DELETE FROM " + TRACEPOOL_TABLE_NAME;
            db.execSQL(query);
        }catch (SQLException e) {
            e.printStackTrace();
            if(db != null) {
                db.close();
            }
            return false;
        }
        return true;
    }

}

