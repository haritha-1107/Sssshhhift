package com.example.sssshhift.usage.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {UsageLog.class}, version = 1)
@TypeConverters({DateConverter.class})
public abstract class UsageDatabase extends RoomDatabase {
    private static volatile UsageDatabase INSTANCE;
    public abstract UsageLogDao usageLogDao();

    public static UsageDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (UsageDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        UsageDatabase.class,
                        "usage_database"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
} 