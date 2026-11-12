package com.example.data.repository

import android.content.Context
import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
import com.example.model.SyncResult
import com.example.notification.NotificationHelper
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class TaskRepository(
    private val taskDao: TaskDao,
    private val notificationHelper: NotificationHelper,
    context: Context
) {
    val cloudSyncManager = CloudSyncManager(context)

    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()
    val pendingTasks: Flow<List<TaskEntity>> = taskDao.getPendingTasks()
    val completedTasks: Flow<List<TaskEntity>> = taskDao.getCompletedTasks()

    fun getTasksForDateRange(startMillis: Long, endMillis: Long): Flow<List<TaskEntity>> {
        return taskDao.getTasksForDateRange(startMillis, endMillis)
    }

    suspend fun insertTask(task: TaskEntity): Long {
        val insertedId = taskDao.insertTask(task)
        val savedTask = task.copy(id = insertedId)

        if (savedTask.hasReminder && !savedTask.isCompleted) {
            val reminderTime = calculateReminderTime(savedTask)
            if (reminderTime > System.currentTimeMillis()) {
                val updatedWithReminderTime = savedTask.copy(reminderScheduledTimeMillis = reminderTime)
                taskDao.updateTask(updatedWithReminderTime)
                notificationHelper.scheduleTaskReminder(updatedWithReminderTime, reminderTime)
            }
        }
        return insertedId
    }

    suspend fun updateTask(task: TaskEntity) {
        val currentTask = taskDao.getTaskByIdDirect(task.id)
        if (currentTask != null) {
            notificationHelper.cancelTaskReminder(currentTask.id)
        }

        var taskToSave = task.copy(updatedAtMillis = System.currentTimeMillis())

        if (taskToSave.hasReminder && !taskToSave.isCompleted) {
            val reminderTime = calculateReminderTime(taskToSave)
            if (reminderTime > System.currentTimeMillis()) {
                taskToSave = taskToSave.copy(reminderScheduledTimeMillis = reminderTime)
                notificationHelper.scheduleTaskReminder(taskToSave, reminderTime)
            }
        }

        taskDao.updateTask(taskToSave)
    }

    suspend fun toggleTaskCompletion(taskId: Long) {
        val task = taskDao.getTaskByIdDirect(taskId) ?: return
        val newStatus = !task.isCompleted
        val completedAt = if (newStatus) System.currentTimeMillis() else null

        if (newStatus) {
            notificationHelper.cancelTaskReminder(taskId)
        } else if (task.hasReminder) {
            val reminderTime = calculateReminderTime(task)
            if (reminderTime > System.currentTimeMillis()) {
                notificationHelper.scheduleTaskReminder(task, reminderTime)
            }
        }

        taskDao.updateTask(
            task.copy(
                isCompleted = newStatus,
                completedAtMillis = completedAt,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteTask(taskId: Long) {
        notificationHelper.cancelTaskReminder(taskId)
        taskDao.deleteTaskById(taskId)
    }

    suspend fun syncToCloud(): SyncResult {
        val tasks = taskDao.getAllTasksDirect()
        return cloudSyncManager.backupToCloud(tasks)
    }

    suspend fun restoreFromCloud(): SyncResult {
        val (restoredTasks, result) = cloudSyncManager.restoreFromCloud()
        if (result.success && !restoredTasks.isNullOrEmpty()) {
            taskDao.insertTasks(restoredTasks)
            // Schedule alarms for restored tasks
            for (task in restoredTasks) {
                if (task.hasReminder && !task.isCompleted) {
                    val reminderTime = calculateReminderTime(task)
                    if (reminderTime > System.currentTimeMillis()) {
                        notificationHelper.scheduleTaskReminder(task, reminderTime)
                    }
                }
            }
        }
        return result
    }

    suspend fun exportJson(): String {
        val tasks = taskDao.getAllTasksDirect()
        return cloudSyncManager.serializeTasksToJson(tasks)
    }

    suspend fun importJson(json: String): Boolean {
        val tasks = cloudSyncManager.parseTasksFromJson(json)
        if (tasks.isNotEmpty()) {
            taskDao.insertTasks(tasks)
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
            // Default 09:00 morning for all-day events
            cal.set(Calendar.HOUR_OF_DAY, 9)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }

        // Subtract reminderMinutesBefore
        cal.add(Calendar.MINUTE, -task.reminderMinutesBefore)
        return cal.timeInMillis
    }

    suspend fun seedInitialDataIfEmpty() {
        val existing = taskDao.getAllTasksDirect()
        if (existing.isNotEmpty()) return

        val cal = Calendar.getInstance()
        // Today
        val todayMillis = getDayStartMillis(cal.timeInMillis)

        // Tomorrow
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowMillis = getDayStartMillis(cal.timeInMillis)

        // Day after tomorrow
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
            insertTask(task)
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
    }
}
