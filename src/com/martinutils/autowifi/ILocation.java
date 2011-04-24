package com.martinutils.autowifi;

import android.location.Location;

abstract class ILocation
{
    String ssid;

    public boolean hasSSID()
    {
        return ssid != null;
    }

    public void setSSID(String ssid)
    {
        this.ssid = ssid;
    }

    public abstract int getVicinity(ILocation myLocation);

    public abstract String asDbString();

    public static ILocation asLocation(String stringLoc, String ssid)
    {
        if (stringLoc.contains("|"))
        {
            String[] fields = stringLoc.split("\\|");
            Location loc = new Location(fields[0]);
            loc.setAccuracy(Float.parseFloat(fields[1]));
            loc.setAltitude(Double.parseDouble(fields[2]));
            loc.setLatitude(Double.parseDouble(fields[3]));
            loc.setLongitude(Double.parseDouble(fields[4]));
            return new GPSLocation(ssid, loc);
        }
        return null;
    }
}
