package com.example.sssshhift.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class ProfileDatabaseHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "profiles.db";
    public static final int DATABASE_VERSION = 2; // Incremented for schema change

    public static final String TABLE_PROFILES = "profiles";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_TRIGGER_TYPE = "trigger_type";
    public static final String COLUMN_TRIGGER_VALUE = "trigger_value";
    public static final String COLUMN_END_TIME = "end_time"; // NEW COLUMN
    public static final String COLUMN_RINGER_MODE = "ringer_mode";
    public static final String COLUMN_ACTIONS = "actions";
    public static final String COLUMN_IS_ACTIVE = "is_active";
    public static final String COLUMN_CREATED_AT = "created_at";

    private static final String CREATE_TABLE_PROFILES =
            "CREATE TABLE " + TABLE_PROFILES + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_NAME + " TEXT NOT NULL, " +
                    COLUMN_TRIGGER_TYPE + " TEXT NOT NULL, " +
                    COLUMN_TRIGGER_VALUE + " TEXT NOT NULL, " +
                    COLUMN_END_TIME + " TEXT, " + // NEW COLUMN
                    COLUMN_RINGER_MODE + " TEXT NOT NULL, " +
                    COLUMN_ACTIONS + " TEXT, " +
                    COLUMN_IS_ACTIVE + " INTEGER DEFAULT 0, " +
                    COLUMN_CREATED_AT + " INTEGER NOT NULL" +
                    ");";

    public ProfileDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_PROFILES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Add the end_time column to existing tables
            db.execSQL("ALTER TABLE " + TABLE_PROFILES + " ADD COLUMN " + COLUMN_END_TIME + " TEXT");
        }
    }
}