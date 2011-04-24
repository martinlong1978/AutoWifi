package com.martinutils.autowifi;

import android.location.Location;
import android.util.Log;

public class GPSLocation extends ILocation
{

    Location loc;

    public GPSLocation(Location loc)
    {
        this.loc = loc;
    }

    public GPSLocation(String ssid, Location loc)
    {
        this.ssid = ssid;
        this.loc = loc;
    }

    public int getVicinity(ILocation myLocation)
    {
        if (myLocation instanceof GPSLocation)
        {
            GPSLocation gpsLocation = (GPSLocation) myLocation;
            Location wifiLocation = gpsLocation.loc;
            float accIn = Math.max(wifiLocation.getAccuracy(),
                    loc.getAccuracy());
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
        else
        {
            return LocationService.VICINITY_OUT;
        }
    }

    public String asDbString()
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

}
