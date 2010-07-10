package com.martinutils.autowifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;

public class LocationReceiver extends BroadcastReceiver
{

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
        {
            startService(context);
        }
        else if (android.net.wifi.WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(intent.getAction()))
        {
            SupplicantState extra = intent.getParcelableExtra(android.net.wifi.WifiManager.EXTRA_NEW_STATE);
            if (extra.equals(SupplicantState.COMPLETED))
            {
                WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                startService(context, manager.getConnectionInfo().getSSID());
            }
        }
    }

    private void startService(Context context)
    {
        startService(context, null);
    }

    private void startService(Context context, String connectedTo)
    {
        Intent serviceIntent = new Intent(context, LocationService.class);
        if (connectedTo != null)
        {
            serviceIntent.putExtra("connectedTo", connectedTo);
        }
        context.startService(serviceIntent);
    }
}
