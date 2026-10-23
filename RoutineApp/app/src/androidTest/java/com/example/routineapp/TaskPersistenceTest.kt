package com.example.routineapp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.routineapp.data.AppDatabase
import com.example.routineapp.data.Recurrence
import com.example.routineapp.data.Task
import com.example.routineapp.data.TaskSource
import com.example.routineapp.data.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Real SQLite file: data must survive closing and reopening the database (= restarting the app). */
@RunWith(AndroidJUnit4::class)
class TaskPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "persistence-test.db"

    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(*AppDatabase.MIGRATIONS).build()

    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun tasksSurviveReopenWithAllFields() = runBlocking {
        val day = LocalDate.of(2026, 10, 23).toEpochDay()
        var db = open()
        val id = db.taskDao().insert(Task(title = "Prova de matemática", description = "Funções", epochDay = day,
            startMinute = 9 * 60, durationMinutes = 90, category = "Estudos", priority = 2, location = "Sala 3",
            notes = "Levar calculadora", recurrence = Recurrence.WEEKLY, createdAt = 1, updatedAt = 1,
            source = TaskSource.CHAT))
        db.close()

        db = open()
        val t = db.taskDao().get(id)!!
        assertEquals("Prova de matemática", t.title)
        assertEquals(9 * 60, t.startMinute)
        assertEquals(Recurrence.WEEKLY, t.recurrence)
        assertEquals(TaskSource.CHAT, t.source)
        assertEquals("Sala 3", t.location)

        db.taskDao().update(t.copy(status = TaskStatus.DONE, completedAt = 5))
        db.close()

        db = open()
        assertEquals(TaskStatus.DONE, db.taskDao().get(id)!!.status)
        assertEquals(emptyList<Task>(), db.taskDao().allPending())
        assertEquals(listOf(id), db.taskDao().observeRange(day, day).first().map { it.id })
        db.taskDao().delete(db.taskDao().get(id)!!)
        assertNull(db.taskDao().get(id))
        db.close()
    }

    @Test fun untimedTaskKeepsNullTime() = runBlocking {
        val db = open()
        val id = db.taskDao().insert(Task(title = "Lavar o carro", epochDay = 20_000, createdAt = 1, updatedAt = 1))
        val t = db.taskDao().get(id)!!
        assertNull(t.startMinute)
        assertNull(t.durationMinutes)
        assertEquals(TaskSource.MANUAL, t.source)
        db.close()
    }
}
