package com.martinutils.autowifi;

import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapView;

public class WifiOverlay extends ItemizedOverlay<RadiusItem>
{

    private ArrayList<RadiusItem> mOverlays = new ArrayList<RadiusItem>();
    private Context               context;
    int                           maxLat    = 0, minLat = 0, maxLong = 0,
            minLong = 0;

    public WifiOverlay(Drawable defaultMarker, Context context)
    {
        super(boundCenterBottom(defaultMarker));
        this.context = context;
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow)
    {
        if (shadow)
        {
            return;
        }
        super.draw(canvas, mapView, false);
    }

    public void addOverlay(RadiusItem overlay)
    {
        mOverlays.add(overlay);
        rePopulate();
    }

    @Override
    protected RadiusItem createItem(int i)
    {
        return mOverlays.get(i);
    }

    @Override
    public int size()
    {
        return mOverlays.size();
    }

    private void rePopulate()
    {
        calcMaxMin();
        populate();
    }

    @Override
    public GeoPoint getCenter()
    {
        int x = ((maxLat - minLat) / 2) + minLat;
        int y = ((maxLong - minLong) / 2) + minLong;
        return new GeoPoint(x, y);
    }

    @Override
    public int getLatSpanE6()
    {
        return maxLat - minLat;
    }

    @Override
    public int getLonSpanE6()
    {
        return maxLong - minLong;
    }

    private void calcMaxMin()
    {
        RadiusItem first = mOverlays.get(0);
        if (first != null)
        {
            maxLat = minLat = first.getPoint().getLatitudeE6();
            maxLong = minLong = first.getPoint().getLongitudeE6();
        }
        for (RadiusItem item : mOverlays)
        {
            GeoPoint loc = item.getPoint();
            float rad = item.getRadius();
            int radLat = metresToDegLat(rad);
            int radLong = metresToDegLong(rad, loc.getLatitudeE6());
            maxLat = Math.max(maxLat, loc.getLatitudeE6() + radLat);
            minLat = Math.min(minLat, loc.getLatitudeE6() - radLat);
            maxLong = Math.max(maxLong, loc.getLongitudeE6() + radLong);
            minLong = Math.min(minLong, loc.getLongitudeE6() - radLong);
        }
    }

    @Override
    protected boolean onTap(int index)
    {
        Intent intent = new Intent(context, AdjustPointActivity.class);
        intent.putExtra("pointId", mOverlays.get(index).getId());
        context.startActivity(intent);
        return true;
    }

    public void clear()
    {
        mOverlays.clear();
        rePopulate();
    }

    private static final double EQUATOR_METERS = 111122;

    private static int metresToDegLat(double metres)
    {
        return (int) (1000000 * metres / EQUATOR_METERS);
    }

    private static int metresToDegLong(double metres, int latE6)
    {
        latE6 = Math.abs(latE6);
        double latMetres = Math.cos(Math.toRadians(((double) latE6) / 1000000))
                * EQUATOR_METERS;
        return (int) (1000000 * metres / latMetres);
    }

}
