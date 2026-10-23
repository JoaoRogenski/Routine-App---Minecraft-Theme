package com.example.routineapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE epochDay BETWEEN :fromDay AND :toDay ORDER BY epochDay, startMinute IS NOT NULL, startMinute")
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: Long): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE epochDay = :day")
    suspend fun onDay(day: Long): List<Task>

    @Query("SELECT * FROM tasks WHERE status = 'PENDING' AND epochDay >= :fromDay ORDER BY epochDay, startMinute")
    suspend fun pendingFrom(fromDay: Long): List<Task>

    @Query("SELECT * FROM tasks WHERE status = 'PENDING'")
    suspend fun allPending(): List<Task>

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)
}
