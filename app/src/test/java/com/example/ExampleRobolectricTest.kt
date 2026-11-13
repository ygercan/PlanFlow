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

    @Test
    fun `github release manager compares semantic versions and extracts apk asset`() {
        assertTrue(com.example.data.repository.GitHubReleaseManager.isRemoteVersionNewer("1.0", "v1.0.1"))
        assertTrue(com.example.data.repository.GitHubReleaseManager.isRemoteVersionNewer("1.0.2", "v1.1.0"))
        org.junit.Assert.assertFalse(com.example.data.repository.GitHubReleaseManager.isRemoteVersionNewer("1.2.0", "v1.2.0"))
        org.junit.Assert.assertFalse(com.example.data.repository.GitHubReleaseManager.isRemoteVersionNewer("1.3.0", "v1.2.9"))

        val sampleReleaseJson = org.json.JSONObject(
            """
            {
              "tag_name": "v1.0.5",
              "name": "PlanFlow v1.0.5",
              "body": "- Yeni ozellik eklendi\n- Atomik guncelleme",
              "published_at": "2026-11-12T10:00:00Z",
              "html_url": "https://github.com/example-owner/planflow/releases/tag/v1.0.5",
              "assets": [
                {
                  "name": "PlanFlow-v1.0.5.apk",
                  "browser_download_url": "https://github.com/example-owner/planflow/releases/download/v1.0.5/PlanFlow-v1.0.5.apk",
                  "size": 10485760
                }
              ]
            }
            """.trimIndent()
        )

        val info = com.example.data.repository.GitHubReleaseManager.parseReleaseJson(sampleReleaseJson, "1.0")
        assertTrue(info.isNewerThanInstalled)
        assertEquals("v1.0.5", info.tagName)
        assertEquals(
            "https://github.com/example-owner/planflow/releases/download/v1.0.5/PlanFlow-v1.0.5.apk",
            info.preferredDownloadOrReleaseUrl
        )
    }

    @Test
    fun `atomic pre-update checkpoint normalizes and snapshots local tasks inside transaction`() = runTest {
        val dao = database.taskDao()
        dao.insertTasks(
            listOf(
                TodoItem(title = "Atomic Task 1", dueDateMillis = 1000L),
                TodoItem(title = "Atomic Task 2", dueDateMillis = 2000L)
            )
        )

        val result = repository.prepareAtomicPreUpdateCheckpoint()
        assertTrue(result.success)
        assertEquals(2, dao.getAllTasksDirect().size)
    }

    @Test
    fun `subtask codec encodes, parses, and resets completion flags`() {
        val items = listOf(
            com.example.model.SubtaskItem(id = "s1", title = "Su al", isDone = true),
            com.example.model.SubtaskItem(id = "s2", title = "Ekmek al", isDone = false)
        )
        val encoded = com.example.model.SubtaskCodec.encode(items)
        val parsed = com.example.model.SubtaskCodec.parse(encoded)
        assertEquals(2, parsed.size)
        assertEquals("Su al", parsed[0].title)
        assertTrue(parsed[0].isDone)
        org.junit.Assert.assertFalse(parsed[1].isDone)

        val reset = com.example.model.SubtaskCodec.parse(com.example.model.SubtaskCodec.resetAll(encoded))
        assertTrue(reset.all { !it.isDone })
    }

    @Test
    fun `completing daily recurring task creates next day occurrence with reset subtasks`() = runTest {
        val dao = database.taskDao()
        val todayStart = TaskRepository.getDayStartMillis(System.currentTimeMillis())
        val subtasksJson = com.example.model.SubtaskCodec.encode(
            listOf(com.example.model.SubtaskItem(id = "a1", title = "Kontrol", isDone = true))
        )

        val taskId = dao.insertTask(
            TodoItem(
                title = "Günlük Standup",
                dueDateMillis = todayStart,
                dueHour = 9,
                dueMinute = 30,
                recurrenceRule = "DAILY",
                subtasksJson = subtasksJson,
                isCompleted = false
            )
        )

        repository.toggleTaskCompletion(taskId)

        val all = dao.getAllTasksDirect()
        assertEquals(2, all.size)
        val original = all.first { it.id == taskId }
        val next = all.first { it.id != taskId }
        assertTrue(original.isCompleted)
        org.junit.Assert.assertFalse(next.isCompleted)
        assertEquals("Günlük Standup", next.title)
        assertEquals("DAILY", next.recurrenceRule)
        assertTrue(next.dueDateMillis > todayStart)

        val nextSubtasks = com.example.model.SubtaskCodec.parse(next.subtasksJson)
        assertEquals(1, nextSubtasks.size)
        org.junit.Assert.assertFalse(nextSubtasks.first().isDone)
    }

    @Test
    fun `rescheduleOverdueTasksToToday moves only incomplete past tasks`() = runTest {
        val dao = database.taskDao()
        val todayStart = TaskRepository.getDayStartMillis(System.currentTimeMillis())
        val twoDaysAgo = todayStart - 2 * 24L * 60L * 60L * 1000L

        val overduePendingId = dao.insertTask(
            TodoItem(title = "Gecikmiş Görev", dueDateMillis = twoDaysAgo, isCompleted = false)
        )
        val overdueCompletedId = dao.insertTask(
            TodoItem(title = "Geçmiş Tamamlanan", dueDateMillis = twoDaysAgo, isCompleted = true)
        )

        val moved = repository.rescheduleOverdueTasksToToday()
        assertEquals(1, moved)

        val movedTask = dao.getTaskByIdDirect(overduePendingId)
        val untouchedTask = dao.getTaskByIdDirect(overdueCompletedId)
        assertEquals(todayStart, movedTask?.dueDateMillis)
        assertEquals(twoDaysAgo, untouchedTask?.dueDateMillis)
    }
}

