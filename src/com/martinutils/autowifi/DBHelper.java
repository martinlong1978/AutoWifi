package com.martinutils.autowifi;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHelper extends SQLiteOpenHelper
{

    private static final int DB_VERSION = 1;

    public DBHelper(Context context)
    {
        super(context, "netLocations", null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
        onUpgrade(db, 0, DB_VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        switch (oldVersion)
        {
            case 0: // Create
                db.execSQL("CREATE TABLE location(id INTEGER PRIMARY KEY, ssid VARCHAR(1024), point TEXT);");
            default:
                // Do nothing... will this ever happen?
        }
    }

}
