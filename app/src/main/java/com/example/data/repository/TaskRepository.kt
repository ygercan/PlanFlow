package com.example.data.repository

import android.content.Context
import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
import com.example.model.AppAccentTheme
import com.example.model.AppThemeMode
import com.example.model.RecurrenceRule
import com.example.model.SubtaskCodec
import com.example.model.SyncResult
import com.example.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/**
 * Repository that coordinates local Room SQLite persistence ([TaskDao]) and
 * remote Supabase synchronization ([CloudSyncManager]), resolving conflicts
 * by comparing `updated_at` timestamps (last-write-wins).
 */
class TaskRepository(
    private val taskDao: TaskDao,
    private val notificationHelper: NotificationHelper,
    context: Context
) {
    val cloudSyncManager = CloudSyncManager(context)
    val gitHubReleaseManager = GitHubReleaseManager(context)
    private val appContext = context.applicationContext
    private val uiPrefs = appContext.getSharedPreferences("planflow_ui_prefs", Context.MODE_PRIVATE)
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getSavedThemeMode(): AppThemeMode {
        return AppThemeMode.fromString(uiPrefs.getString("theme_mode", AppThemeMode.SYSTEM.name))
    }

    fun saveThemeMode(mode: AppThemeMode) {
        uiPrefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun getSavedAccentTheme(): AppAccentTheme {
        return AppAccentTheme.fromString(uiPrefs.getString("accent_theme", AppAccentTheme.CORAL.name))
    }

    fun saveAccentTheme(accent: AppAccentTheme) {
        uiPrefs.edit().putString("accent_theme", accent.name).apply()
    }

    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()
    val pendingTasks: Flow<List<TaskEntity>> = taskDao.getPendingTasks()
    val completedTasks: Flow<List<TaskEntity>> = taskDao.getCompletedTasks()

    fun getTasksForDateRange(startMillis: Long, endMillis: Long): Flow<List<TaskEntity>> {
        return taskDao.getTasksForDateRange(startMillis, endMillis)
    }

    suspend fun insertTask(task: TaskEntity, syncImmediately: Boolean = true): Long {
        val now = System.currentTimeMillis()
        val createdMs = if (task.createdAtMillis > 0) task.createdAtMillis else now
        val taskWithTimestamp = task.copy(
            createdAtMillis = createdMs,
            updatedAtMillis = now,
            createdAt = Date(createdMs),
            updatedAt = Date(now)
        )
        val insertedId = taskDao.insertTask(taskWithTimestamp)
        var savedTask = taskWithTimestamp.copy(id = insertedId)

        if (savedTask.hasReminder && !savedTask.isCompleted) {
            val reminderTime = calculateReminderTime(savedTask)
            if (reminderTime > System.currentTimeMillis()) {
                savedTask = savedTask.copy(reminderScheduledTimeMillis = reminderTime)
                taskDao.updateTask(savedTask)
                notificationHelper.scheduleTaskReminder(savedTask, reminderTime)
            }
        }

        if (syncImmediately) {
            val finalTask = savedTask
            syncScope.launch {
                cloudSyncManager.pushSingleTaskToSupabase(finalTask)
            }
        }
        return insertedId
    }

    suspend fun updateTask(task: TaskEntity) {
        val currentTask = taskDao.getTaskByIdDirect(task.id)
        if (currentTask != null) {
            notificationHelper.cancelTaskReminder(currentTask.id)
        }

        val now = System.currentTimeMillis()
        var taskToSave = task.copy(
            updatedAtMillis = now,
            updatedAt = Date(now)
        )

        if (taskToSave.hasReminder && !taskToSave.isCompleted) {
            val reminderTime = calculateReminderTime(taskToSave)
            if (reminderTime > now) {
                taskToSave = taskToSave.copy(reminderScheduledTimeMillis = reminderTime)
                notificationHelper.scheduleTaskReminder(taskToSave, reminderTime)
            }
        }

        taskDao.updateTask(taskToSave)

        val finalTask = taskToSave
        syncScope.launch {
            cloudSyncManager.pushSingleTaskToSupabase(finalTask)
        }
    }

    suspend fun toggleTaskCompletion(taskId: Long) {
        val task = taskDao.getTaskByIdDirect(taskId) ?: return
        val newStatus = !task.isCompleted
        val now = System.currentTimeMillis()
        val completedAt = if (newStatus) now else null

        if (newStatus) {
            notificationHelper.cancelTaskReminder(taskId)
        } else if (task.hasReminder) {
            val reminderTime = calculateReminderTime(task)
            if (reminderTime > now) {
                notificationHelper.scheduleTaskReminder(task, reminderTime)
            }
        }

        val updatedTask = task.copy(
            isCompleted = newStatus,
            completedAtMillis = completedAt,
            updatedAtMillis = now,
            updatedAt = Date(now)
        )

        val rule = RecurrenceRule.fromString(task.recurrenceRule)
        if (newStatus && rule != RecurrenceRule.NONE) {
            val nextDueDateMillis = calculateNextRecurrenceMillis(task.dueDateMillis, rule)
            val nextDraft = task.copy(
                id = 0,
                dueDateMillis = nextDueDateMillis,
                isCompleted = false,
                completedAtMillis = null,
                subtasksJson = SubtaskCodec.resetAll(task.subtasksJson),
                reminderScheduledTimeMillis = null,
                createdAtMillis = now,
                updatedAtMillis = now,
                createdAt = Date(now),
                updatedAt = Date(now)
            )
            val nextId = taskDao.completeRecurringTaskAtomically(updatedTask, nextDraft)
            var savedNext = nextDraft.copy(id = nextId)
            if (savedNext.hasReminder) {
                val reminderTime = calculateReminderTime(savedNext)
                if (reminderTime > now) {
                    savedNext = savedNext.copy(reminderScheduledTimeMillis = reminderTime)
                    taskDao.updateTask(savedNext)
                    notificationHelper.scheduleTaskReminder(savedNext, reminderTime)
                }
            }
            val nextToSync = savedNext
            syncScope.launch {
                cloudSyncManager.pushSingleTaskToSupabase(updatedTask)
                cloudSyncManager.pushSingleTaskToSupabase(nextToSync)
            }
        } else {
            taskDao.updateTask(updatedTask)
            syncScope.launch {
                cloudSyncManager.pushSingleTaskToSupabase(updatedTask)
            }
        }
    }

    suspend fun toggleSubtask(taskId: Long, subtaskId: String) {
        val task = taskDao.getTaskByIdDirect(taskId) ?: return
        val items = SubtaskCodec.parse(task.subtasksJson)
        if (items.isEmpty()) return
        val updatedItems = items.map { item ->
            if (item.id == subtaskId) item.copy(isDone = !item.isDone) else item
        }
        val now = System.currentTimeMillis()
        val updatedTask = task.copy(
            subtasksJson = SubtaskCodec.encode(updatedItems),
            updatedAtMillis = now,
            updatedAt = Date(now)
        )
        taskDao.updateTask(updatedTask)
        syncScope.launch {
            cloudSyncManager.pushSingleTaskToSupabase(updatedTask)
        }
    }

    suspend fun rescheduleOverdueTasksToToday(): Int {
        val todayStart = getDayStartMillis(System.currentTimeMillis())
        val now = System.currentTimeMillis()
        val overdue = taskDao.getAllTasksDirect().filter {
            !it.isCompleted && it.dueDateMillis < todayStart
        }
        if (overdue.isEmpty()) return 0

        val rescheduled = overdue.map { task ->
            notificationHelper.cancelTaskReminder(task.id)
            var updated = task.copy(
                dueDateMillis = todayStart,
                updatedAtMillis = now,
                updatedAt = Date(now)
            )
            if (updated.hasReminder) {
                val reminderTime = calculateReminderTime(updated)
                if (reminderTime > now) {
                    updated = updated.copy(reminderScheduledTimeMillis = reminderTime)
                    notificationHelper.scheduleTaskReminder(updated, reminderTime)
                }
            }
            updated
        }

        val count = taskDao.rescheduleTasksAtomically(rescheduled)
        syncScope.launch {
            cloudSyncManager.backupToSupabase(rescheduled)
        }
        return count
    }

    suspend fun rescheduleSingleTaskToToday(taskId: Long) {
        val task = taskDao.getTaskByIdDirect(taskId) ?: return
        val todayStart = getDayStartMillis(System.currentTimeMillis())
        val now = System.currentTimeMillis()
        notificationHelper.cancelTaskReminder(task.id)
        var updated = task.copy(
            dueDateMillis = todayStart,
            updatedAtMillis = now,
            updatedAt = Date(now)
        )
        if (updated.hasReminder) {
            val reminderTime = calculateReminderTime(updated)
            if (reminderTime > now) {
                updated = updated.copy(reminderScheduledTimeMillis = reminderTime)
                notificationHelper.scheduleTaskReminder(updated, reminderTime)
            }
        }
        taskDao.updateTask(updated)
        syncScope.launch {
            cloudSyncManager.pushSingleTaskToSupabase(updated)
        }
    }

    suspend fun clearCompletedTasks(): Int {
        val completed = taskDao.getAllTasksDirect().filter { it.isCompleted }
        if (completed.isEmpty()) return 0
        completed.forEach { notificationHelper.cancelTaskReminder(it.id) }
        val deletedCount = taskDao.deleteCompletedTasks()
        syncScope.launch {
            completed.forEach { cloudSyncManager.deleteSingleTaskFromSupabase(it.id) }
        }
        return deletedCount
    }

    suspend fun deleteTask(taskId: Long) {
        notificationHelper.cancelTaskReminder(taskId)
        taskDao.deleteTaskById(taskId)

        syncScope.launch {
            cloudSyncManager.deleteSingleTaskFromSupabase(taskId)
        }
    }

    /**
     * Pure deterministic conflict resolution comparing `updated_at` timestamps
     * between local Room items and remote Supabase items.
     */
    data class ConflictResolutionPlan(
        val itemsToSaveLocally: List<TaskEntity>,
        val itemsToPushToSupabase: List<TaskEntity>
    )

    fun resolveConflictsByUpdatedAt(
        localTasks: List<TaskEntity>,
        remoteTasks: List<TaskEntity>
    ): ConflictResolutionPlan {
        val localMap = localTasks.associateBy { it.id }
        val remoteMap = remoteTasks.associateBy { it.id }

        val toSaveLocally = mutableListOf<TaskEntity>()
        val toPushRemotely = mutableListOf<TaskEntity>()

        // 1. Evaluate remote items against local items
        for (remote in remoteTasks) {
            val local = localMap[remote.id]
            if (local == null) {
                toSaveLocally.add(remote)
            } else {
                val remoteUpdated = remote.effectiveUpdatedAtMillis
                val localUpdated = local.effectiveUpdatedAtMillis
                if (remoteUpdated > localUpdated) {
                    toSaveLocally.add(remote)
                } else if (localUpdated > remoteUpdated) {
                    toPushRemotely.add(local)
                }
            }
        }

        // 2. Any local item missing from remote should be pushed to Supabase
        for (local in localTasks) {
            if (!remoteMap.containsKey(local.id)) {
                toPushRemotely.add(local)
            }
        }

        return ConflictResolutionPlan(
            itemsToSaveLocally = toSaveLocally,
            itemsToPushToSupabase = toPushRemotely
        )
    }

    /**
     * Bidirectional Room <-> Supabase synchronization using [resolveConflictsByUpdatedAt]
     * inside an atomic SQLite `@Transaction` block.
     */
    suspend fun syncOnStartupOrDemand(): SyncResult {
        val (remoteTasks, fetchResult) = cloudSyncManager.fetchFromSupabase()
        val localTasks = taskDao.getAllTasksDirect()

        if (!fetchResult.success || remoteTasks == null) {
            return fetchResult
        }

        val resolution = resolveConflictsByUpdatedAt(localTasks, remoteTasks)

        // Apply newer remote records into local Room SQLite atomically via @Transaction
        val totalMerged = taskDao.applyConflictResolutionAtomically(resolution.itemsToSaveLocally)
        for (task in resolution.itemsToSaveLocally) {
            if (task.hasReminder && !task.isCompleted) {
                val reminderTime = calculateReminderTime(task)
                if (reminderTime > System.currentTimeMillis()) {
                    notificationHelper.scheduleTaskReminder(task, reminderTime)
                }
            } else {
                notificationHelper.cancelTaskReminder(task.id)
            }
        }

        // Push newer or local-only records to Supabase
        if (resolution.itemsToPushToSupabase.isNotEmpty()) {
            cloudSyncManager.backupToSupabase(resolution.itemsToPushToSupabase)
        }

        return SyncResult(
            true,
            "Atomik senkronizasyon tamamlandı ($totalMerged görev güncel, ${resolution.itemsToSaveLocally.size} indirildi, ${resolution.itemsToPushToSupabase.size} gönderildi)."
        )
    }

    /**
     * Executes an atomic SQLite transaction (`@Transaction`) to normalize and snapshot
     * all local tasks before initiating an APK update download from GitHub Releases.
     */
    suspend fun prepareAtomicPreUpdateCheckpoint(): SyncResult {
        return try {
            val verifiedTasks = taskDao.checkpointAndNormalizeTasksAtomically()
            val snapshotJson = cloudSyncManager.serializeTasksToJson(verifiedTasks)
            appContext.getSharedPreferences("planflow_atomic_checkpoint", Context.MODE_PRIVATE)
                .edit()
                .putString("pre_update_snapshot_json", snapshotJson)
                .putLong("pre_update_timestamp", System.currentTimeMillis())
                .apply()

            if (cloudSyncManager.isSupabaseConfigured() && verifiedTasks.isNotEmpty()) {
                cloudSyncManager.backupToSupabase(verifiedTasks)
            }

            SyncResult(
                success = true,
                message = "Atomik veritabanı kontrol noktası oluşturuldu (${verifiedTasks.size} görev güvenceye alındı)."
            )
        } catch (e: Exception) {
            SyncResult(
                success = false,
                message = "Atomik işlem geri alındı (Rollback): ${e.localizedMessage}"
            )
        }
    }

    suspend fun syncToCloud(): SyncResult {
        val tasks = taskDao.getAllTasksDirect()
        return cloudSyncManager.backupToSupabase(tasks)
    }

    suspend fun restoreFromCloud(): SyncResult {
        return syncOnStartupOrDemand()
    }

    suspend fun exportJson(): String {
        val tasks = taskDao.getAllTasksDirect()
        return cloudSyncManager.serializeTasksToJson(tasks)
    }

    suspend fun importJson(json: String): Boolean {
        val tasks = cloudSyncManager.parseTasksFromJson(json)
        if (tasks.isNotEmpty()) {
            taskDao.applyConflictResolutionAtomically(tasks)
            syncScope.launch {
                cloudSyncManager.backupToSupabase(taskDao.getAllTasksDirect())
            }
            return true
        }
        return false
    }

    private fun calculateReminderTime(task: TaskEntity): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = task.dueDateMillis

        if (task.dueHour >= 0 && task.dueMinute >= 0) {
            cal.set(Calendar.HOUR_OF_DAY, task.dueHour)
            cal.set(Calendar.MINUTE, task.dueMinute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        } else {
            cal.set(Calendar.HOUR_OF_DAY, 9)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }

        cal.add(Calendar.MINUTE, -task.reminderMinutesBefore)
        return cal.timeInMillis
    }

    suspend fun seedInitialDataIfEmpty() {
        val existing = taskDao.getAllTasksDirect()
        if (existing.isNotEmpty()) return

        if (cloudSyncManager.isSupabaseConfigured()) {
            val (remoteTasks, fetchResult) = cloudSyncManager.fetchFromSupabase()
            if (fetchResult.success && !remoteTasks.isNullOrEmpty()) {
                taskDao.upsertTasks(remoteTasks)
                for (task in remoteTasks) {
                    if (task.hasReminder && !task.isCompleted) {
                        val reminderTime = calculateReminderTime(task)
                        if (reminderTime > System.currentTimeMillis()) {
                            notificationHelper.scheduleTaskReminder(task, reminderTime)
                        }
                    }
                }
                return
            }
        }

        val cal = Calendar.getInstance()
        val todayMillis = getDayStartMillis(cal.timeInMillis)

        cal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowMillis = getDayStartMillis(cal.timeInMillis)

        cal.add(Calendar.DAY_OF_YEAR, 1)
        val nextDayMillis = getDayStartMillis(cal.timeInMillis)

        val sampleTasks = listOf(
            TaskEntity(
                title = "Haftalık Proje Planlaması",
                description = "Sprint hedeflerini belirle ve ekip takvimini güncelle.",
                dueDateMillis = todayMillis,
                dueHour = 10,
                dueMinute = 30,
                category = "İş",
                priority = "HIGH",
                isCompleted = false,
                hasReminder = true,
                reminderMinutesBefore = 15
            ),
            TaskEntity(
                title = "Öğle Yürüyüşü ve Su Takibi",
                description = "En az 30 dakika tempolu yürüyüş yap ve 2 litre su tüket.",
                dueDateMillis = todayMillis,
                dueHour = 12,
                dueMinute = 45,
                category = "Sağlık",
                priority = "NORMAL",
                isCompleted = true,
                completedAtMillis = System.currentTimeMillis() - 3600000
            ),
            TaskEntity(
                title = "Haftalık Market Alışverişi",
                description = "Sebze, meyve, yulaf ve süt ürünleri listesini tamamla.",
                dueDateMillis = todayMillis,
                dueHour = 18,
                dueMinute = 0,
                category = "Alışveriş",
                priority = "NORMAL",
                isCompleted = false,
                hasReminder = true,
                reminderMinutesBefore = 30
            ),
            TaskEntity(
                title = "Kotlin & Compose Mimarisi İncelemesi",
                description = "StateFlow ve Room best practice dokümanlarını oku.",
                dueDateMillis = tomorrowMillis,
                dueHour = 14,
                dueMinute = 0,
                category = "Eğitim",
                priority = "HIGH",
                isCompleted = false,
                hasReminder = true,
                reminderMinutesBefore = 15
            ),
            TaskEntity(
                title = "Diş Hekimi Randevusu",
                description = "Rutin kontrol ve temizlik randevusu.",
                dueDateMillis = nextDayMillis,
                dueHour = 11,
                dueMinute = 0,
                category = "Sağlık",
                priority = "URGENT",
                isCompleted = false,
                hasReminder = true,
                reminderMinutesBefore = 60
            )
        )

        for (task in sampleTasks) {
            insertTask(task, syncImmediately = false)
        }

        if (cloudSyncManager.isSupabaseConfigured()) {
            syncScope.launch {
                cloudSyncManager.backupToSupabase(taskDao.getAllTasksDirect())
            }
        }
    }

    companion object {
        fun getDayStartMillis(timestamp: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = timestamp
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun getDayEndMillis(timestamp: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = timestamp
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            return cal.timeInMillis
        }

        fun calculateNextRecurrenceMillis(currentDueDateMillis: Long, rule: RecurrenceRule): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = getDayStartMillis(currentDueDateMillis)
            }
            when (rule) {
                RecurrenceRule.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                RecurrenceRule.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                RecurrenceRule.MONTHLY -> cal.add(Calendar.MONTH, 1)
                RecurrenceRule.NONE -> {}
            }
            return getDayStartMillis(cal.timeInMillis)
        }

        fun calculateDailyProgress(todayTasks: List<TaskEntity>): com.example.model.DailyProgressState {
            val total = todayTasks.size
            val completed = todayTasks.count { it.isCompleted }
            val fraction = if (total > 0) completed.toFloat() / total.toFloat() else 0f
            val pct = (fraction * 100f).toInt().coerceIn(0, 100)
            return com.example.model.DailyProgressState(
                completedCount = completed,
                totalCount = total,
                progressFraction = fraction.coerceIn(0f, 1f),
                percentage = pct
            )
        }
    }
}

typealias TodoRepository = TaskRepository
