package com.martinutils.autowifi;

import java.util.ArrayList;
import java.util.List;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class LocationService extends Service implements LocationListener
{

    boolean                   manualConnection = true;

    List<MyLocation>          locations        = new ArrayList<MyLocation>();

    DBHelper                  helper;
    Location                  lastKnown;
    Location                  netLocation;

    // Allow some attempts before disconnecting
    long                      scantime         = 0;

    public static final long  SCANTIME         = 1000 * 60 * 5;              // 5

    private SharedPreferences prefs;
    private WifiMode          mode;
    private Editor            edit;

    // mins

    @Override
    public void onCreate()
    {
        Log.i("WIFI", "onCreate");
        super.onCreate();

        helper = new DBHelper(this);

        reload();

        LocationManager service = getLocationService();
        service.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                180000,
                50,
                this);
        lastKnown = getLocation();
    }

    private Location getLocation()
    {
        Log.i("WIFI", "getLocation");
        return getLocationService().getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
    }

    private LocationManager getLocationService()
    {
        return (LocationManager) this.getSystemService(LOCATION_SERVICE);
    }

    private WifiManager getWifiService()
    {
        return (WifiManager) this.getSystemService(WIFI_SERVICE);
    }

    @Override
    public void onLocationChanged(Location location)
    {
        Log.i("WIFI", "onLocationChanged");
        Location tempLocation = lastKnown;
        lastKnown = location;
        final WifiManager wifiService = getWifiService();
        if (wifiService.isWifiEnabled())
        {
            final WifiInfo connectionInfo = wifiService.getConnectionInfo();
            Log.i("WIFI", "Connection state: "
                    + connectionInfo.getSupplicantState().name());
            // is wifi still connected
            if (connectionInfo.getSupplicantState() == SupplicantState.COMPLETED)
            {
                scantime = 0;
                if (netLocation != null
                        && netLocation.getAccuracy() > lastKnown.getAccuracy())
                {
                    logLocation(connectionInfo.getSSID(), netLocation);
                    netLocation = null;
                }
                else
                {
                    logLocation(connectionInfo.getSSID());
                }
            }
            else if (connectionInfo.getSupplicantState() == SupplicantState.DISCONNECTED
                    || connectionInfo.getSupplicantState() == SupplicantState.SCANNING)
            {
                if (scantime == 0)
                {
                    scantime = System.currentTimeMillis() + SCANTIME;
                }
                if (!isInvicinity(location)
                        && scantime < System.currentTimeMillis())
                {
                    manualConnection = true;
                    Log.i("WIFI", "Auto Disabling");
                    setWifiEnabled(false);
                }
            }
            else
            {
                scantime = 0;
                // We can get a new location as soon as we connect to the wifi,
                // so store away the original
                netLocation = tempLocation;
            }
        }
        else
        {
            scantime = 0;
            if (isInvicinity(location))
            {
                manualConnection = false;
                Log.i("WIFI", "Auto Enabling");
                setWifiEnabled(true);
            }
        }
    }

    @Override
    public void onProviderDisabled(String provider)
    {
    }

    @Override
    public void onProviderEnabled(String provider)
    {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras)
    {
        Log.i("WIFI", provider);
    }

    public class LocalBinder extends Binder
    {
        LocationService getService()
        {
            return LocationService.this;
        }
    }

    private LocalBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent)
    {
        Log.i("WIFI", "onBind");
        return mBinder;
    }

    @Override
    public void onStart(Intent intent, int startId)
    {
        Log.i("WIFI", "OnStart");
        super.onStart(intent, startId);
        lastKnown = getLocation();
        String connectedTo = intent.getStringExtra("connectedTo");
        Log.i("WIFI", "onStart: " + connectedTo);
        if (connectedTo != null && manualConnection)
        {
            logLocation(connectedTo);
            // Subsequent connections are automatic, until we go out of range
            manualConnection = false;
        }
    }

    private void logLocation(String connectedTo)
    {
        if (lastKnown != null)
        {
            logLocation(connectedTo, lastKnown);
        }
    }

    private void logLocation(String ssid, final Location location)
    {
        Log.i("WIFI", "Connected to: "
                + ssid
                + " at: "
                + location.getLatitude()
                + ","
                + location.getLongitude()
                + " acc: "
                + location.getAccuracy());
        if (isInvicinity(location, ssid))
        {
            Log.i("WIFI", "Already logged");
            return;
        }
        if (location != null)
        {
            locations.add(new MyLocation(ssid, location));
        }
        Log.i("WIFI", "Locations: " + locations.size());
        SQLiteDatabase db = helper.getWritableDatabase();
        try
        {
            db.execSQL("INSERT INTO location (ssid, point) VALUES (?,?)",
                    new Object[] { ssid, locationToString(location) });
        }
        finally
        {
            db.close();
        }

    }

    private boolean isInvicinity(Location location)
    {
        return isInvicinity(location, null);
    }

    private boolean isInvicinity(Location location, String ssid)
    {
        Log.i("WIFI", "isInvicinity");
        for (MyLocation myLocation : locations)
        {
            Location wifiLocation = myLocation.loc;
            float acc = wifiLocation.getAccuracy() + location.getAccuracy();
            Log.i("WIFI", "Compare to: "
                    + wifiLocation.getLatitude()
                    + ","
                    + wifiLocation.getLongitude()
                    + " acc: "
                    + wifiLocation.getAccuracy());
            Log.i("WIFI", "distance: "
                    + wifiLocation.distanceTo(location)
                    + " acc: "
                    + acc);
            if (wifiLocation.distanceTo(location) < acc
                    && (ssid == null || ssid.equals(myLocation.ssid)))
            {
                return true;
            }
        }
        return false;
    }

    public static String locationToString(Location loc)
    {
        StringBuilder builder = new StringBuilder();
        builder.append(loc.getProvider());
        builder.append('|');
        builder.append(loc.getAccuracy());
        builder.append('|');
        builder.append(loc.getAltitude());
        builder.append('|');
        builder.append(loc.getLatitude());
        builder.append('|');
        builder.append(loc.getLongitude());
        return builder.toString();
    }

    public static Location stringToLocation(String stringLoc)
    {
        String[] fields = stringLoc.split("\\|");
        Location loc = new Location(fields[0]);
        loc.setAccuracy(Float.parseFloat(fields[1]));
        loc.setAltitude(Double.parseDouble(fields[2]));
        loc.setLatitude(Double.parseDouble(fields[3]));
        loc.setLongitude(Double.parseDouble(fields[4]));
        return loc;
    }

    private void setWifiEnabled(boolean state)
    {
        switch (mode)
        {
            case ON:
                getWifiService().setWifiEnabled(true);
                break;
            case OFF:
                getWifiService().setWifiEnabled(false);
                break;
            case AUTO:
                getWifiService().setWifiEnabled(state);
                break;
        }

    }

    public void reload()
    {

        prefs = getSharedPreferences("wifiSettings", MODE_PRIVATE);
        edit = prefs.edit();

        mode = WifiMode.valueOf(prefs.getString("mode", "AUTO"));
        switch (mode)
        {
            case ON:
                setWifiEnabled(true);
                break;
            case OFF:
                setWifiEnabled(false);
                break;
            case AUTO:
                break;
        }

        Log.i("WIFI", "Reloading data.");
        locations.clear();
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cur = null;
        try
        {
            cur = db.query("location",
                    new String[] { "point", "ssid" },
                    null,
                    null,
                    null,
                    null,
                    null);
            if (cur.moveToFirst())
            {
                do
                {
                    String ssid = cur.getString(cur.getColumnIndex("ssid"));
                    String data = cur.getString(cur.getColumnIndex("point"));
                    Location loc = stringToLocation(data);
                    if (loc != null)
                    {
                        Log.i("WIFI", "Loading: "
                                + loc.getLatitude()
                                + ","
                                + loc.getLongitude()
                                + " acc: "
                                + loc.getAccuracy());
                        locations.add(new MyLocation(ssid, loc));
                    }
                } while (cur.moveToNext());
            }
        }
        finally
        {
            if (cur != null)
            {
                cur.close();
            }
            if (db != null)
            {
                db.close();
            }
        }

    }

    private class MyLocation
    {
        public MyLocation(String ssid, Location loc)
        {
            this.ssid = ssid;
            this.loc = loc;
        }

        String   ssid;
        Location loc;
    }

}
