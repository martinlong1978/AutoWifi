package com.martinutils.autowifi;

import java.util.List;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.martinutils.autowifi.LocationService.LocalBinder;

public class PlotActivity extends MapActivity
{

    DBHelper              dbh;
    private MapView       mapView;
    private String        ssid;
    private List<Overlay> overlays;
    private WifiOverlay   ovl;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.map);
        mapView = (MapView) findViewById(R.id.mapView);
        mapView.setBuiltInZoomControls(true);
        mapView.displayZoomControls(false);
        mapView.setSatellite(false);
        ssid = this.getIntent().getStringExtra("ssid");
        dbh = new DBHelper(this);

        overlays = mapView.getOverlays();

        Intent serviceIntent = new Intent(this, LocationService.class);
        bindService(serviceIntent, serviceConn, BIND_AUTO_CREATE);

        refreshMapFromDB();

    }

    @Override
    protected void onRestart()
    {
        super.onRestart();
        refreshMapFromDB();
        service.reload();
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
                                                PlotActivity.this.service = ((LocalBinder) service).getService();
                                            }
                                        };

    private void refreshMapFromDB()
    {
        ovl = new WifiOverlay(getResources().getDrawable(R.drawable.icon), this);
        overlays.clear();
        overlays.add(ovl);
        SQLiteDatabase db = dbh.getReadableDatabase();
        Cursor cur = null;
        try
        {
            cur = db.rawQuery("SELECT point,id FROM location WHERE ssid=?;",
                    new String[] { ssid });
            if (cur.moveToFirst())
            {
                do
                {
                    Location loc = LocationService.stringToLocation(cur.getString(0));
                    GeoPoint point = new GeoPoint((int) (1000000 * loc.getLatitude()),
                            (int) (1000000 * loc.getLongitude()));
                    RadiusItem overlayitem = new RadiusItem(cur.getInt(1),
                            point,
                            mapView,
                            loc.getAccuracy());
                    ovl.addOverlay(overlayitem);
                } while (cur.moveToNext());
            }
            final MapController controller = mapView.getController();
            controller.setCenter(ovl.getCenter());
            controller.zoomToSpan(ovl.getLatSpanE6(), ovl.getLonSpanE6());
            mapView.postInvalidate();
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

    @Override
    protected boolean isRouteDisplayed()
    {
        return true;
    }

}
