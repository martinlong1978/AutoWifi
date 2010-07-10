package com.martinutils.autowifi;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

import com.martinutils.autowifi.LocationService.LocalBinder;

public class LocationActivity extends Activity implements OnItemClickListener
{

    ListView             lv;
    ArrayAdapter<String> arrayAdapter;
    private DBHelper     helper;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, LocationService.class);
        this.startService(intent);
        this.setContentView(R.layout.main);
        lv = (ListView) this.findViewById(R.id.ListView01);
        arrayAdapter = new ArrayAdapter<String>(this, R.layout.listitem);
        lv.setAdapter(arrayAdapter);
        helper = new DBHelper(this);
        lv.setOnItemClickListener(this);
        lv.setOnCreateContextMenuListener(this);
        Intent serviceIntent = new Intent(this, LocationService.class);
        bindService(serviceIntent, serviceConn, BIND_AUTO_CREATE);
        rePopulate();
    }

    @Override
    protected void onRestart()
    {
        super.onRestart();
        rePopulate();
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

                                            }

                                            @Override
                                            public void onServiceConnected(ComponentName name,
                                                    IBinder service)
                                            {
                                                LocationActivity.this.service = ((LocalBinder) service).getService();
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
}
