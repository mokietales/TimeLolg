package com.mokie.timelogdemo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        TrackInheritanceEntity::class,
        SessionEntity::class,
        SessionTrackAllocationEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class TimeLogDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: TimeLogDatabase? = null

        fun getInstance(context: Context): TimeLogDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TimeLogDatabase::class.java,
                    "time_log.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
