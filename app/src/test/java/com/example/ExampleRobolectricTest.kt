package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.TimestampConverters
import com.example.data.local.TodoItem
import com.example.data.repository.TaskRepository
import com.example.notification.NotificationHelper
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: TaskRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TaskRepository(
            taskDao = database.taskDao(),
            notificationHelper = NotificationHelper(context),
            context = context
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `read string from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("PlanFlow", appName)
    }

    @Test
    fun `timestamp converter converts between Date, Long, and ISO-8601`() {
        val converters = TimestampConverters()
        val nowMillis = 1730800000000L
        val date = converters.fromTimestamp(nowMillis)
        assertNotNull(date)
        assertEquals(nowMillis, converters.dateToTimestamp(date))

        val iso = TimestampConverters.toIsoString(date!!)
        val parsedMillis = TimestampConverters.parseIsoToMillis(iso)
        assertEquals(nowMillis, parsedMillis)
    }

    @Test
    fun `room crud operations persist TodoItem with Date timestamps`() = runTest {
        val dao = database.taskDao()
        val item = TodoItem(
            title = "Supabase Sync Test",
            description = "Primary local storage check",
            dueDateMillis = 1730800000000L,
            createdAtMillis = 1730800000000L,
            updatedAtMillis = 1730800005000L,
            createdAt = Date(1730800000000L),
            updatedAt = Date(1730800005000L)
        )

        val id = dao.insertTask(item)
        val loaded = dao.getTaskByIdDirect(id)
        assertNotNull(loaded)
        assertEquals("Supabase Sync Test", loaded?.title)
        assertEquals(1730800005000L, loaded?.updatedAt?.time)
    }

    @Test
    fun `conflict resolution favors newer updated_at timestamp`() {
        val localOlder = TodoItem(
            id = 1L,
            title = "Old Local Version",
            dueDateMillis = 1000L,
            updatedAtMillis = 1000L,
            updatedAt = Date(1000L)
        )
        val remoteNewer = TodoItem(
            id = 1L,
            title = "Newer Supabase Version",
            dueDateMillis = 1000L,
            updatedAtMillis = 2000L,
            updatedAt = Date(2000L)
        )

        val localNewer = TodoItem(
            id = 2L,
            title = "Newer Local Version",
            dueDateMillis = 1000L,
            updatedAtMillis = 5000L,
            updatedAt = Date(5000L)
        )
        val remoteOlder = TodoItem(
            id = 2L,
            title = "Older Supabase Version",
            dueDateMillis = 1000L,
            updatedAtMillis = 3000L,
            updatedAt = Date(3000L)
        )

        val localOnly = TodoItem(
            id = 3L,
            title = "Local Only Item",
            dueDateMillis = 1000L,
            updatedAtMillis = 4000L,
            updatedAt = Date(4000L)
        )

        val plan = repository.resolveConflictsByUpdatedAt(
            localTasks = listOf(localOlder, localNewer, localOnly),
            remoteTasks = listOf(remoteNewer, remoteOlder)
        )

        assertEquals(1, plan.itemsToSaveLocally.size)
        assertEquals("Newer Supabase Version", plan.itemsToSaveLocally.first().title)

        assertEquals(2, plan.itemsToPushToSupabase.size)
        assertTrue(plan.itemsToPushToSupabase.any { it.id == 2L && it.title == "Newer Local Version" })
        assertTrue(plan.itemsToPushToSupabase.any { it.id == 3L && it.title == "Local Only Item" })
    }

    @Test
    fun `daily progress bar calculates percentage from SQLite tasks for current day`() = runTest {
        val dao = database.taskDao()
        val todayStart = TaskRepository.getDayStartMillis(System.currentTimeMillis())
        val todayEnd = TaskRepository.getDayEndMillis(System.currentTimeMillis())

        dao.insertTasks(
            listOf(
                TodoItem(title = "Task 1", dueDateMillis = todayStart, isCompleted = true),
                TodoItem(title = "Task 2", dueDateMillis = todayStart, isCompleted = true),
                TodoItem(title = "Task 3", dueDateMillis = todayStart, isCompleted = false),
                TodoItem(title = "Task 4", dueDateMillis = todayStart, isCompleted = false)
            )
        )

        val todayTasks = dao.getAllTasksDirect().filter { it.dueDateMillis in todayStart..todayEnd }
        val progress = TaskRepository.calculateDailyProgress(todayTasks)

        assertEquals(4, progress.totalCount)
        assertEquals(2, progress.completedCount)
        assertEquals(50, progress.percentage)
        assertEquals(0.5f, progress.progressFraction, 0.001f)
    }
}

