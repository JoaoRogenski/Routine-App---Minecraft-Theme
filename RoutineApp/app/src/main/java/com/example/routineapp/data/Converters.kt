package com.example.routineapp.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun statusToString(v: TaskStatus) = v.name
    @TypeConverter fun stringToStatus(v: String) = TaskStatus.valueOf(v)
    @TypeConverter fun recurrenceToString(v: Recurrence) = v.name
    @TypeConverter fun stringToRecurrence(v: String) = Recurrence.valueOf(v)
    @TypeConverter fun sourceToString(v: TaskSource) = v.name
    @TypeConverter fun stringToSource(v: String) = TaskSource.valueOf(v)
}
