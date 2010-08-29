package com.martinutils.autowifi;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;

public class RadiusItem extends OverlayItem
{

    ShapeDrawable pic;
    LayerDrawable layers;
    OvalShape     circle;
    OvalShape     dot;
    float         radius;
    MapView       mapView;
    ShapeDrawable dotDraw;
    int           id;

    public RadiusItem(int id, GeoPoint point, MapView view, float radius)
    {
        this(id, point, view, radius, Color.GREEN);
    }

    public RadiusItem(int id,
                      GeoPoint point,
                      MapView view,
                      float radius,
                      int color)
    {
        super(point, "", "");
        dot = new OvalShape();
        dot.resize(6, 6);
        dotDraw = new ShapeDrawable(dot);
        circle = new OvalShape();
        pic = new ShapeDrawable(circle);
        pic.getPaint().setColor(color);
        pic.setAlpha(80);
        layers = new LayerDrawable(new Drawable[] { pic, dotDraw });
        this.mapView = view;
        this.radius = radius;
        this.id = id;
    }

    public int getId()
    {
        return id;
    }

    @Override
    public Drawable getMarker(int stateBitset)
    {
        int size = metersToRadius(radius,
                ((double) getPoint().getLatitudeE6()) / 1000000);
        size = Math.max(size, 10);
        // Size is radius... l x w must be double this
        circle.resize(size * 2, size * 2);
        pic.setBounds(-size, -size, size, size);
        layers.setBounds(-size, -size, size, size);
        dotDraw.setBounds(-3, -3, 3, 3);
        return layers;
    }

    public int metersToRadius(float meters, double latitude)
    {
        return (int) (mapView.getProjection().metersToEquatorPixels(meters) * (1 / Math.cos(Math.toRadians(latitude))));
    }

    public float getRadius()
    {
        return radius;
    }

}
