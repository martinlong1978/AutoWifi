package com.martinutils.autowifi;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class AdjustPointActivity extends Activity implements
        OnSeekBarChangeListener,
        OnClickListener
{

    DBHelper                   helper;
    private Location           loc;
    private String             id;
    private TextView           accView;
    public static final double ACC_CONSTANT = 333333;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.adjust);
        helper = new DBHelper(this);
        id = "" + this.getIntent().getIntExtra("pointId", 0);
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cur = null;
        try
        {
            cur = db.rawQuery("SELECT id,ssid,point FROM location WHERE id=?;",
                    new String[] { id });
            cur.moveToFirst();
            String point = cur.getString(2);
            String ssid = cur.getString(1);
            TextView ssidView = (TextView) findViewById(R.id.ssid);
            TextView latView = (TextView) findViewById(R.id.latitude);
            TextView longView = (TextView) findViewById(R.id.longitude);
            Button deleteButton = (Button) findViewById(R.id.Button01);
            deleteButton.setOnClickListener(this);
            accView = (TextView) findViewById(R.id.accuracy);
            SeekBar seekBar = (SeekBar) findViewById(R.id.accuracyBar);
            loc = LocationService.stringToLocation(point);
            ssidView.setText(ssid);
            latView.setText("" + loc.getLatitude());
            longView.setText("" + loc.getLongitude());
            accView.setText("" + loc.getAccuracy());
            double acc = (Math.log10(loc.getAccuracy()) - 1) * ACC_CONSTANT;
            seekBar.setProgress((int) acc);
            seekBar.setOnSeekBarChangeListener(this);
        }
        finally
        {
            if (db != null)
            {
                db.close();
            }
            if (cur != null)
            {
                cur.close();
            }
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar,
            int progress,
            boolean fromUser)
    {
        acc = (Math.pow(10, ((double) progress / ACC_CONSTANT) + 1));
        accView.setText("" + (int) acc);
    }

    double acc;

    @Override
    public void onStartTrackingTouch(SeekBar seekBar)
    {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar)
    {
        loc.setAccuracy((float) acc);
        SQLiteDatabase db = helper.getWritableDatabase();
        try
        {
            db.execSQL("UPDATE location SET point = ? WHERE id = ?",
                    new Object[] { LocationService.locationToString(loc), id });
        }
        finally
        {
            if (db != null)
            {
                db.close();
            }
        }

    }

    @Override
    public void onClick(View v)
    {
        SQLiteDatabase db = helper.getWritableDatabase();
        try
        {
            db.execSQL("DELETE FROM location WHERE id = ?", new Object[] { id });
        }
        finally
        {
            if (db != null)
            {
                db.close();
            }
        }
        this.finish();
    }
}
