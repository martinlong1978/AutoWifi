package com.martinutils.autowifi;

import java.util.ArrayList;
import java.util.List;

import android.app.Service;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.wifi.WifiManager;
import android.util.Log;

public abstract class ALocationService extends Service
{
    List<ILocation>           locations = new ArrayList<ILocation>();

    private DBHelper          helper;

    private SharedPreferences prefs;

    private WifiMode          mode;

    protected boolean         wasInZone;

    protected ILocation       lastKnown;

    class NetInfo
    {
        public int    vicinity = VICINITY_OUT;
        public String ssid;
    }

    public static final int VICINITY_OUT  = 0;
    public static final int VICINITY_NEAR = 1;
    public static final int VICINITY_IN   = 2;

    @Override
    public void onCreate()
    {
        super.onCreate();

        helper = new DBHelper(this);

    }

    protected WifiManager getWifiService()
    {
        return (WifiManager) this.getSystemService(WIFI_SERVICE);
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

    protected void reload()
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
                    ILocation loc = ILocation.asLocation(data, ssid);
                    if (loc != null)
                    {
                        locations.add(loc);
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

    protected void logLocation(ILocation location)
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
                    new Object[] { location.ssid, location.asDbString() });
        }
        finally
        {
            db.close();
        }

    }

    protected boolean isInvicinity(ILocation location)
    {
        final NetInfo info = getVicinity(location);
        return (location.hasSSID())
                ? info.vicinity > VICINITY_NEAR
                : info.vicinity > VICINITY_OUT;
    }

    protected NetInfo getVicinity(ILocation inLocation)
    {

        NetInfo out = new NetInfo();
        out.vicinity = VICINITY_OUT;
        if (inLocation == null)
        {
            return out;
        }

        for (ILocation myLocation : locations)
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

    public NetInfo getVicinity()
    {
        if (lastKnown == null)
        {
            return new NetInfo();
        }
        return getVicinity(lastKnown);
    }

    public boolean isInvicinity()
    {
        return isInvicinity(lastKnown);
    }

}
