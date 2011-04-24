package com.martinutils.autowifi;

import android.location.Location;
import android.util.Log;

class MyLocation
{
    public MyLocation(Location loc)
    {
        this.loc = loc;
    }

    public MyLocation(String ssid, Location loc)
    {
        this.ssid = ssid;
        this.loc = loc;
    }

    String   ssid;
    Location loc;

    public boolean hasSSID()
    {
        return ssid != null;
    }

    public void setSSID(String ssid)
    {
        this.ssid = ssid;
    }

    public int getVicinity(MyLocation myLocation)
    {
        Location wifiLocation = myLocation.loc;
        float accIn = Math.max(wifiLocation.getAccuracy(), loc.getAccuracy());
        float accNear = wifiLocation.getAccuracy() + loc.getAccuracy();
        Log.i("WIFI", "Compare to: "
                + wifiLocation.getLatitude()
                + ","
                + wifiLocation.getLongitude()
                + " acc: "
                + wifiLocation.getAccuracy());
        final float distanceTo = wifiLocation.distanceTo(loc);
        Log.i("WIFI", "distance: "
                + distanceTo
                + " accIn: "
                + accIn
                + " accNear: "
                + accNear);
        if (distanceTo < accIn
                && (ssid == null || ssid.equals(myLocation.ssid)))
        {
            return LocationService.VICINITY_IN;
        }
        if (distanceTo < accNear
                && (ssid == null || ssid.equals(myLocation.ssid)))
        {
            return LocationService.VICINITY_NEAR;
        }
        return LocationService.VICINITY_OUT;
    }
}
