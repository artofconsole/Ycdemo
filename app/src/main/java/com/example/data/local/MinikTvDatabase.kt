package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [VideoEntity::class], version = 2, exportSchema = false)
abstract class MinikTvDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao

    companion object {
        @Volatile
        private var INSTANCE: MinikTvDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE videos ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'LOCAL'")
                db.execSQL("ALTER TABLE videos ADD COLUMN remoteThumbnailUrl TEXT")
                db.execSQL("ALTER TABLE videos ADD COLUMN cloudDocId TEXT")
                db.execSQL("ALTER TABLE videos ADD COLUMN localCachePath TEXT")
                db.execSQL("ALTER TABLE videos ADD COLUMN syncedFromCloud INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): MinikTvDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MinikTvDatabase::class.java,
                    "miniktv_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
