package com.example.cozymind.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.migration.Migration;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {User.class, MoodCheckIn.class, JournalEntry.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;

    public abstract MoodCheckInDao moodCheckInDao();

    public abstract JournalEntryDao journalEntryDao();

    public abstract UserDao userDao();

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS users ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "name TEXT NOT NULL, username TEXT NOT NULL, password TEXT NOT NULL, "
                    + "email TEXT NOT NULL, phone_number TEXT NOT NULL, is_admin INTEGER NOT NULL, "
                    + "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_username ON users (username)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_email ON users (email)");
            database.execSQL("CREATE TABLE IF NOT EXISTS journals ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, user_id INTEGER NOT NULL, "
                    + "title TEXT NOT NULL, body TEXT NOT NULL, created_at INTEGER NOT NULL, "
                    + "updated_at INTEGER NOT NULL, FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_journals_user_id ON journals (user_id)");
            database.execSQL("CREATE TABLE IF NOT EXISTS mood_trackers ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, user_id INTEGER NOT NULL, "
                    + "datetime_mood INTEGER NOT NULL, severity INTEGER NOT NULL, created_at INTEGER NOT NULL, "
                    + "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_mood_trackers_user_id ON mood_trackers (user_id)");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class,
                            "cozymind_database")
                            .addMigrations(MIGRATION_1_2)
                            // If an older test build left a database Room can't migrate,
                            // rebuild it instead of crashing on launch. Real data lives on the server.
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
