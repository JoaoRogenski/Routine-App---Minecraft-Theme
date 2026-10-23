package com.example.routineapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// Schema changes: bump version, add a Migration to MIGRATIONS (schemas/ keeps each version's JSON).
@Database(entities = [Task::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "routine.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
