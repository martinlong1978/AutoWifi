package com.martinutils.autowifi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

import com.martinutils.autowifi.LocationService.LocalBinder;

public class LocationActivity extends Activity implements
        OnItemClickListener,
        View.OnClickListener
{

    private ListView             lv;
    private ArrayAdapter<String> arrayAdapter;
    private DBHelper             helper;
    private TextView             tv;
    private Button               modeButtonOn;
    private Button               modeButtonOff;
    private Button               modeButtonAuto;
    private SharedPreferences    prefs;
    private WifiMode             mode;
    private Editor               edit;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("wifiSettings", MODE_PRIVATE);
        edit = prefs.edit();

        mode = WifiMode.valueOf(prefs.getString("mode", "AUTO"));

        Intent intent = new Intent(this, LocationService.class);
        this.startService(intent);
        this.setContentView(R.layout.main);

        modeButtonOn = (Button) findViewById(R.id.ButtonON);
        modeButtonOff = (Button) findViewById(R.id.ButtonOFF);
        modeButtonAuto = (Button) findViewById(R.id.ButtonAUTO);

        modeButtonOn.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v)
            {
                onModeButtonClick(v, WifiMode.ON);

            }
        });

        modeButtonOff.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v)
            {
                onModeButtonClick(v, WifiMode.OFF);

            }
        });

        modeButtonAuto.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v)
            {
                onModeButtonClick(v, WifiMode.AUTO);

            }
        });

        updateButtons();

        lv = (ListView) this.findViewById(R.id.ListView01);
        arrayAdapter = new ArrayAdapter<String>(this, R.layout.listitem);
        lv.setAdapter(arrayAdapter);
        helper = new DBHelper(this);
        lv.setOnItemClickListener(this);
        lv.setOnCreateContextMenuListener(this);
        Intent serviceIntent = new Intent(this, LocationService.class);
        bindService(serviceIntent, serviceConn, BIND_AUTO_CREATE);
        rePopulate();
        tv = (TextView) findViewById(R.id.TextView03);
        tv.setOnClickListener(this);

    }

    private void updateButtons()
    {
        this.modeButtonAuto.setBackgroundColor(Color.GRAY);
        this.modeButtonOn.setBackgroundColor(Color.GRAY);
        this.modeButtonOff.setBackgroundColor(Color.GRAY);
        switch (mode)
        {
            case ON:
                modeButtonOn.setBackgroundColor(Color.GREEN);
                break;
            case OFF:
                modeButtonOff.setBackgroundColor(Color.GREEN);
                break;
            case AUTO:
                modeButtonAuto.setBackgroundColor(Color.GREEN);
                break;
        }
    }

    @Override
    protected void onDestroy()
    {
        unbindService(serviceConn);
        super.onDestroy();
    }

    @Override
    protected void onRestart()
    {
        super.onRestart();
        rePopulate();
    }

    private void onModeButtonClick(View v, WifiMode mode)
    {
        this.mode = mode;
        edit.putString("mode", mode.name());
        updateButtons();
        edit.commit();
        service.reload();
    }

    private void rePopulate()
    {
        arrayAdapter.clear();
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cur = null;
        try
        {
            cur = db.rawQuery("SELECT ssid FROM location GROUP BY ssid", null);
            if (cur.moveToFirst())
            {
                do
                {
                    arrayAdapter.add(cur.getString(0));
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

    private LocationService service;

    ServiceConnection       serviceConn = new ServiceConnection() {

                                            @Override
                                            public void onServiceDisconnected(ComponentName name)
                                            {
                                                LocationActivity.this.service = null;
                                            }

                                            @Override
                                            public void onServiceConnected(ComponentName name,
                                                    IBinder service)
                                            {
                                                LocationActivity.this.service = ((LocalBinder) service).getService();
                                                if (!LocationActivity.this.service.isLocationEnabled())
                                                {
                                                    showDialog(1);
                                                }
                                            }
                                        };

    @Override
    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3)
    {
        final String networkName = lv.getItemAtPosition(arg2).toString();
        openNetwork(networkName);
    }

    private void openNetwork(final String networkName)
    {
        Intent intent = new Intent(this, PlotActivity.class);
        intent.putExtra("ssid", networkName);
        startActivity(intent);
    }

    MenuItem view;
    MenuItem delete;
    String   selection;

    @Override
    public void onCreateContextMenu(ContextMenu menu,
            View v,
            ContextMenuInfo menuInfo)
    {
        AdapterContextMenuInfo aMenuInfo = (AdapterContextMenuInfo) menuInfo;
        selection = (String) lv.getItemAtPosition(aMenuInfo.position);
        view = menu.add("View locations");
        delete = menu.add("Forget locations for this network");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        if (item == view)
        {
            openNetwork(selection);
        }
        else if (item == delete)
        {
            SQLiteDatabase db = helper.getWritableDatabase();
            try
            {
                db.execSQL("DELETE FROM location WHERE ssid = ?",
                        new Object[] { selection });
                service.reload();
                rePopulate();
            }
            finally
            {
                if (db != null)
                {
                    db.close();
                }
            }
        }
        return true;
    }

    @Override
    public void onClick(View v)
    {
        try
        {
            sendEmail();
        }
        catch (IOException e)
        {
            Log.e("WIFI", e.getMessage(), e);
            e.printStackTrace();
        }
    }

    private void sendEmail() throws IOException
    {
        Process proc = Runtime.getRuntime().exec(new String[] { "logcat",
                "-v",
                "time",
                "-d",
                "-s",
                "WIFI" });

        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()),
                1024);

        try
        {
            proc.waitFor();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }

        StringBuilder builder = new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null)
        {
            builder.append(line + "\n");
        }

        Intent intent = new Intent(Intent.ACTION_SENDTO,
                Uri.parse("mailto:martin@longhome.co.uk"));

        intent.putExtra("subject", "Auto Wifi support");
        intent.putExtra("body", builder.toString());

        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == R.id.Help)
        {
            showDialog(0);
        }
        if (item.getItemId() == R.id.Support)
        {
            try
            {
                sendEmail();
            }
            catch (IOException e)
            {
                Log.e("WIFI", e.getMessage(), e);
                e.printStackTrace();
            }
        }
        if (item.getItemId() == R.id.Reload)
        {
            rePopulate();
        }
        if (item.getItemId() == R.id.Off)
        {
            service.setWifiEnabled(false);
        }
        if (item.getItemId() == R.id.On)
        {
            service.setWifiEnabled(true);
        }
        return true;
        // android.R.drawable.ic_menu_send
    }

    @Override
    protected Dialog onCreateDialog(int id)
    {
        Dialog dialog;
        switch (id)
        {
            case 0:
                dialog = new Dialog(this);

                dialog.setContentView(R.layout.help);
                dialog.setTitle("Help");
                // dialog.show();
                break;

            case 1:
                dialog = new Dialog(this);

                dialog.setContentView(R.layout.enable);
                dialog.setTitle("Change settings");

                Button but = (Button) dialog.findViewById(R.id.GoSettings);
                but.setOnClickListener(dialogClickListener);
                // dialog.show();
                break;

            default:
                dialog = null;
        }
        return dialog;
    }

    OnClickListener dialogClickListener = new OnClickListener() {

                                            @Override
                                            public void onClick(View v)
                                            {
                                                Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                                startActivity(intent);
                                            }
                                        };

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.wifi_menu, menu);
        return true;
    }

}
