package com.martinutils.autowifi;

import android.content.Intent;
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

public class LocationService extends ALocationService implements
        LocationListener
{

    GPSLocation netLocation;

    // mins

    @Override
    public void onCreate()
    {
        Log.i("WIFI", "onCreate");
        super.onCreate();

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

    public ILocation getLocation()
    {
        Log.i("WIFI", "getLocation");
        return new GPSLocation(getLocationService().getLastKnownLocation(LocationManager.NETWORK_PROVIDER));
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

    @Override
    public void onLocationChanged(Location location)
    {
        GPSLocation previousLocation = (GPSLocation) lastKnown;
        lastKnown = new GPSLocation(location);

        final WifiManager wifiService = getWifiService();
        if (wifiService.isWifiEnabled())
        {
            final WifiInfo connectionInfo = wifiService.getConnectionInfo();
            final SupplicantState supplicantState = connectionInfo.getSupplicantState();

            ILocation activeLocation;

            if (netLocation != null
                    && netLocation.loc.getAccuracy() > ((GPSLocation) lastKnown).loc.getAccuracy())
            {
                activeLocation = netLocation;
            }
            else
            {
                activeLocation = lastKnown;
            }

            // If wifi still connected, log new locations
            if (supplicantState == SupplicantState.COMPLETED
                    || supplicantState == SupplicantState.ASSOCIATED)
            {
                activeLocation.setSSID(connectionInfo.getSSID());
                logLocation(activeLocation);
            }

            // If wifi has become disconnected, or is rescanning
            else if (supplicantState == SupplicantState.DISCONNECTED
                    || supplicantState == SupplicantState.SCANNING)
            {
                boolean invicinity = isInvicinity();
                // If moving out of zone, and scanning, disable.
                if (!invicinity && wasInZone)
                {
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
            if (invicinity && !wasInZone)
            {
                setWifiEnabled(true);
                wasInZone = true;
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

}
