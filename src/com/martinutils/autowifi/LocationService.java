package com.martinutils.autowifi;

import java.util.ArrayList;
import java.util.List;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
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

    List<MyLocation>          locations = new ArrayList<MyLocation>();

    DBHelper                  helper;
    MyLocation                lastKnown;
    MyLocation                netLocation;

    private SharedPreferences prefs;
    private WifiMode          mode;
    private boolean           wasInZone;

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
                120000,
                100,
                this);

        // If possible get last state
        lastKnown = getLocation();
        wasInZone = lastKnown == null ? false : isInvicinity();

        // And setup wifi to match
        setWifiEnabled(wasInZone);
    }

    public Location getLocation()
    {
        Log.i("WIFI", "getLocation");
        return getLocationService().getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
    }

    private LocationManager getLocationService()
    {
        final LocationManager systemService = (LocationManager) this.getSystemService(LOCATION_SERVICE);
        return systemService;
    }

    public boolean isLocationEnabled()
    {
        return getLocationService().isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private WifiManager getWifiService()
    {
        return (WifiManager) this.getSystemService(WIFI_SERVICE);
    }

    @Override
    public void onLocationChanged(Location location)
    {
        MyLocation previousLocation = lastKnown;
        lastKnown = new MyLocation(location);

        final WifiManager wifiService = getWifiService();
        if (wifiService.isWifiEnabled())
        {
            final WifiInfo connectionInfo = wifiService.getConnectionInfo();
            final SupplicantState supplicantState = connectionInfo.getSupplicantState();
            Log.i("WIFI",
                    "Is Enabled, connection state: " + supplicantState.name());

            // If wifi still connected, log new locations
            if (supplicantState == SupplicantState.COMPLETED
                    || supplicantState == SupplicantState.ASSOCIATED)
            {
                if (netLocation != null
                        && netLocation.loc.getAccuracy() > lastKnown.loc.getAccuracy())
                {
                    netLocation.setSSID(connectionInfo.getSSID());
                    logLocation(netLocation);
                    netLocation = null;
                }
                else
                {
                    lastKnown.setSSID(connectionInfo.getSSID());
                    logLocation(lastKnown);
                }
            }

            // If wifi has become disconnected, or is rescanning
            else if (supplicantState == SupplicantState.DISCONNECTED
                    || supplicantState == SupplicantState.SCANNING)
            {
                boolean invicinity = isInvicinity();
                // If moving out of zone, and scanning, disable.
                if (!invicinity && wasInZone)
                {
                    Log.i("WIFI", "Leaving zone");
                    Log.i("WIFI", "Auto Disabling");
                    setWifiEnabled(false);
                    wasInZone = false;
                }
                netLocation = null;
            }
            else
            {
                Log.i("WIFI", "Saving a netLocation");
                // We can get a new location as soon as we see a new AP
                // so store away the original
                netLocation = previousLocation;
            }
        }
        else
        {
            boolean invicinity = isInvicinity();
            Log.i("WIFI", "Is Disabled");
            if (invicinity && !wasInZone)
            {
                Log.i("WIFI", "Entering zone");
                Log.i("WIFI", "Auto Enabling");
                setWifiEnabled(true);
                wasInZone = true;
            }
            else
            {
                Log.i("WIFI", "Doing nothing");
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
        lastKnown.setSSID(connectedTo);
        Log.i("WIFI", "onStart: " + connectedTo);
        if (connectedTo != null)
        {
            logLocation(lastKnown);
        }
    }

    // private void logLocation(String connectedTo)
    // {
    // if (lastKnown != null)
    // {
    // logLocation(connectedTo, lastKnown);
    // }
    // }

    private void logLocation(MyLocation location)
    {
        if (isInvicinity(location))
        {
            Log.i("WIFI", "Already logged");
            return;
        }
        if (location != null)
        {
            locations.add(location);
        }
        Log.i("WIFI", "Locations: " + locations.size());
        SQLiteDatabase db = helper.getWritableDatabase();
        try
        {
            db.execSQL("INSERT INTO location (ssid, point) VALUES (?,?)",
                    new Object[] { location.ssid,
                            locationToString(location.loc) });
        }
        finally
        {
            db.close();
        }

    }

    public boolean isInvicinity()
    {
        return isInvicinity(lastKnown);
    }

    public static final int VICINITY_OUT  = 0;
    public static final int VICINITY_NEAR = 1;
    public static final int VICINITY_IN   = 2;

    private boolean isInvicinity(MyLocation location)
    {
        final NetInfo info = getVicinity(location);
        return (location.hasSSID())
                ? info.vicinity > VICINITY_NEAR
                : info.vicinity > VICINITY_OUT;
    }

    public NetInfo getVicinity()
    {
        if (lastKnown == null)
        {
            return new NetInfo();
        }
        return getVicinity(lastKnown);
    }

    private NetInfo getVicinity(MyLocation inLocation)
    {

        NetInfo out = new NetInfo();
        out.vicinity = VICINITY_OUT;
        if (inLocation == null)
        {
            return out;
        }

        for (MyLocation myLocation : locations)
        {
            out.vicinity = inLocation.getVicinity(myLocation);
            if (out.vicinity > VICINITY_OUT)
            {
                out.ssid = myLocation.ssid;
                return out;
            }
        }
        return out;
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

    public void setWifiEnabled(boolean state)
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

    public String getStatus()
    {
        StringBuilder builder = new StringBuilder();
        final WifiManager wifiService = getWifiService();
        String state = wifiService.isWifiEnabled()
                ? "<font color='green'><b>enabled</b></font>"
                : "<font color='red'><b>disabled</b></font>";
        if (mode != WifiMode.AUTO)
        {
            state = "permanently " + state;
        }
        String proximity = "";
        final NetInfo vicinity = getVicinity();
        String ap = vicinity.ssid;
        switch (vicinity.vicinity)
        {
            case VICINITY_OUT:
                proximity = "<font color='red'><b>outside</b></font>";
                break;
            case VICINITY_NEAR:
                proximity = "<font color='orange'><b>near</b></font>";
                break;
            case VICINITY_IN:
                proximity = "<font color='green'><b>inside</b></font>";
                break;
        }
        ap = (ap == null) ? "" : " <b>" + ap + "</b>";
        builder.append("<html><body style='background:black; color:#BBB;'>");
        builder.append("You are "
                + proximity
                + " an active zone"
                + ap
                + ".<br>");
        String override = "";
        if (wifiService.isWifiEnabled() != wasInZone)
        {
            override = "manually overridden";
        }
        builder.append("Wifi is currently " + state + " " + override);
        builder.append("</body></html>");
        return builder.toString();
    }

    class NetInfo
    {
        public int    vicinity = VICINITY_OUT;
        public String ssid;
    }

}
