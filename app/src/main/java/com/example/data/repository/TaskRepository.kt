package com.example.data.repository

import android.content.Context
import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
import com.example.model.SyncResult
import com.example.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Calendar

class TaskRepository(
    private val taskDao: TaskDao,
    private val notificationHelper: NotificationHelper,
    context: Context
) {
    val cloudSyncManager = CloudSyncManager(context)
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()
    val pendingTasks: Flow<List<TaskEntity>> = taskDao.getPendingTasks()
    val completedTasks: Flow<List<TaskEntity>> = taskDao.getCompletedTasks()

    fun getTasksForDateRange(startMillis: Long, endMillis: Long): Flow<List<TaskEntity>> {
        return taskDao.getTasksForDateRange(startMillis, endMillis)
    }

    suspend fun insertTask(task: TaskEntity, syncImmediately: Boolean = true): Long {
        val now = System.currentTimeMillis()
        val taskWithTimestamp = task.copy(
            createdAtMillis = if (task.createdAtMillis > 0) task.createdAtMillis else now,
            updatedAtMillis = now
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

        var taskToSave = task.copy(updatedAtMillis = System.currentTimeMillis())

        if (taskToSave.hasReminder && !taskToSave.isCompleted) {
            val reminderTime = calculateReminderTime(taskToSave)
            if (reminderTime > System.currentTimeMillis()) {
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
            updatedAtMillis = now
        )
        taskDao.updateTask(updatedTask)

        syncScope.launch {
            cloudSyncManager.pushSingleTaskToSupabase(updatedTask)
        }
    }

    suspend fun deleteTask(taskId: Long) {
        notificationHelper.cancelTaskReminder(taskId)
        taskDao.deleteTaskById(taskId)

        syncScope.launch {
            cloudSyncManager.deleteSingleTaskFromSupabase(taskId)
        }
    }

    /**
     * İlk açılışta ve manuel senkronizasyonda çalışır:
     * 1. Supabase'den son verileri çeker.
     * 2. Yerel SQLite verileri ile updatedAtMillis karşılaştırması yapar (Son güncellenen kazanır).
     * 3. Yerelde daha yeni olan veya Supabase'de eksik olan kayıtları Supabase'e gönderir.
     */
    suspend fun syncOnStartupOrDemand(): SyncResult {
        val (remoteTasks, fetchResult) = cloudSyncManager.fetchFromSupabase()
        val localTasks = taskDao.getAllTasksDirect()

        if (!fetchResult.success || remoteTasks == null) {
            return fetchResult
        }

        val localMap = localTasks.associateBy { it.id }
        val remoteMap = remoteTasks.associateBy { it.id }

        val tasksToSaveLocally = mutableListOf<TaskEntity>()
        val tasksToPushRemotely = mutableListOf<TaskEntity>()

        // 1. Uzaktaki görevleri yerel ile karşılaştır (Son güncellenen kazanır)
        for (remoteTask in remoteTasks) {
            val localTask = localMap[remoteTask.id]
            if (localTask == null) {
                tasksToSaveLocally.add(remoteTask)
            } else if (remoteTask.updatedAtMillis > localTask.updatedAtMillis) {
                tasksToSaveLocally.add(remoteTask)
            } else if (localTask.updatedAtMillis > remoteTask.updatedAtMillis) {
                tasksToPushRemotely.add(localTask)
            }
        }

        // 2. Yerelde olup Supabase'de hiç olmayanları Supabase'e gönderilecekler listesine ekle
        for (localTask in localTasks) {
            if (!remoteMap.containsKey(localTask.id)) {
                tasksToPushRemotely.add(localTask)
            }
        }

        // 3. Yerel SQLite güncellemelerini uygula ve bildirim alarmlarını kur
        if (tasksToSaveLocally.isNotEmpty()) {
            taskDao.insertTasks(tasksToSaveLocally)
            for (task in tasksToSaveLocally) {
                if (task.hasReminder && !task.isCompleted) {
                    val reminderTime = calculateReminderTime(task)
                    if (reminderTime > System.currentTimeMillis()) {
                        notificationHelper.scheduleTaskReminder(task, reminderTime)
                    }
                } else {
                    notificationHelper.cancelTaskReminder(task.id)
                }
            }
        }

        // 4. Yerelde daha güncel veya yeni olan görevleri Supabase'e gönder
        if (tasksToPushRemotely.isNotEmpty()) {
            cloudSyncManager.backupToSupabase(tasksToPushRemotely)
        }

        val totalMerged = taskDao.getAllTasksDirect().size
        return SyncResult(
            true,
            "Supabase senkronizasyonu tamamlandı ($totalMerged görev güncel, ${tasksToSaveLocally.size} indirildi, ${tasksToPushRemotely.size} gönderildi)."
        )
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
            taskDao.insertTasks(tasks)
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

        // İlk açılışta önce Supabase'de kayıtlı görev var mı kontrol et
        if (cloudSyncManager.isSupabaseConfigured()) {
            val (remoteTasks, fetchResult) = cloudSyncManager.fetchFromSupabase()
            if (fetchResult.success && !remoteTasks.isNullOrEmpty()) {
                taskDao.insertTasks(remoteTasks)
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

        // İlk örnek verileri de Supabase yapılandırılmışsa anında yedekle
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
    }
}
