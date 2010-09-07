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
    Location                  lastKnown;
    Location                  netLocation;

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
        Location previousLocation = lastKnown;
        lastKnown = location;
        boolean invicinity = isInvicinity();

        Log.i("WIFI", "onLocationChanged invicinity: "
                + invicinity
                + " wasInZone: "
                + wasInZone);

        final WifiManager wifiService = getWifiService();
        if (wifiService.isWifiEnabled())
        {
            final WifiInfo connectionInfo = wifiService.getConnectionInfo();
            final SupplicantState supplicantState = connectionInfo.getSupplicantState();
            Log.i("WIFI", "Is Enabled, connection state: "
                    + supplicantState.name());

            // If wifi still connected, log new locations
            if (supplicantState == SupplicantState.COMPLETED
                    || supplicantState == SupplicantState.ASSOCIATED)
            {
                if (netLocation != null
                        && netLocation.getAccuracy() > lastKnown.getAccuracy())
                {
                    Log.i("WIFI", "Logging a netLocation: " + netLocation);
                    logLocation(connectionInfo.getSSID(), netLocation);
                    netLocation = null;
                }
                else
                {
                    Log.i("WIFI", "Logging a location: " + lastKnown);
                    logLocation(connectionInfo.getSSID());
                }
            }

            // If wifi has become disconnected, or is rescanning
            else if (supplicantState == SupplicantState.DISCONNECTED
                    || supplicantState == SupplicantState.SCANNING)
            {
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
        Log.i("WIFI", "onStart: " + connectedTo);
        if (connectedTo != null)
        {
            logLocation(connectedTo);
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

    public boolean isInvicinity()
    {
        return isInvicinity(lastKnown, null);
    }

    private static final int VICINITY_IN   = 2;
    private static final int VICINITY_OUT  = 0;
    private static final int VICINITY_NEAR = 1;

    private boolean isInvicinity(Location location, String ssid)
    {
        final NetInfo info = getVicinity(location, ssid);
        return (ssid == null)
                ? info.vicinity > VICINITY_OUT
                : info.vicinity > VICINITY_NEAR;
    }

    public NetInfo getVicinity()
    {
        if (lastKnown == null)
        {
            return new NetInfo();
        }
        return getVicinity(lastKnown, null);
    }

    private NetInfo getVicinity(Location location, String ssid)
    {
        NetInfo out = new NetInfo();
        out.vicinity = VICINITY_OUT;

        Log.i("WIFI", "isInvicinity");
        Log.i("WIFI", "Location: "
                + location.getLatitude()
                + ","
                + location.getLongitude()
                + " acc: "
                + location.getAccuracy());
        for (MyLocation myLocation : locations)
        {
            Location wifiLocation = myLocation.loc;
            float accIn = Math.max(wifiLocation.getAccuracy(),
                    location.getAccuracy());
            float accNear = wifiLocation.getAccuracy() + location.getAccuracy();
            Log.i("WIFI", "Compare to: "
                    + wifiLocation.getLatitude()
                    + ","
                    + wifiLocation.getLongitude()
                    + " acc: "
                    + wifiLocation.getAccuracy());
            final float distanceTo = wifiLocation.distanceTo(location);
            Log.i("WIFI", "distance: "
                    + distanceTo
                    + " accIn: "
                    + accIn
                    + " accNear: "
                    + accNear);
            if (distanceTo < accIn
                    && (ssid == null || ssid.equals(myLocation.ssid)))
            {
                out.vicinity = VICINITY_IN;
                out.ssid = myLocation.ssid;
                return out;
            }
            if (distanceTo < accNear
                    && (ssid == null || ssid.equals(myLocation.ssid)))
            {
                out.vicinity = VICINITY_NEAR;
                out.ssid = myLocation.ssid;
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
