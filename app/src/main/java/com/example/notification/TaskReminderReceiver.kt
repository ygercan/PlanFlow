package com.example.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, -1L)

        if (intent.action == NotificationHelper.ACTION_MARK_COMPLETED && taskId != -1L) {
            // Dismiss notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(taskId.toInt())

            // Mark task completed in database and push to Supabase
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                val task = db.taskDao().getTaskByIdDirect(taskId)
                if (task != null) {
                    val now = System.currentTimeMillis()
                    val updated = task.copy(
                        isCompleted = true,
                        completedAtMillis = now,
                        updatedAtMillis = now,
                        updatedAt = java.util.Date(now)
                    )
                    db.taskDao().updateTask(updated)
                    com.example.data.repository.CloudSyncManager(context).pushSingleTaskToSupabase(updated)
                }
            }
            return
        }

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule pending tasks with reminders
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                val helper = NotificationHelper(context)
                val pendingTasks = db.taskDao().getAllTasksDirect().filter { !it.isCompleted && it.hasReminder }
                for (task in pendingTasks) {
                    val reminderTime = task.reminderScheduledTimeMillis ?: continue
                    if (reminderTime > System.currentTimeMillis()) {
                        helper.scheduleTaskReminder(task, reminderTime)
                    }
                }
            }
            return
        }

        val title = intent.getStringExtra(NotificationHelper.EXTRA_TASK_TITLE) ?: "Görev Hatırlatıcı"
        val desc = intent.getStringExtra(NotificationHelper.EXTRA_TASK_DESC) ?: ""
        val category = intent.getStringExtra(NotificationHelper.EXTRA_TASK_CATEGORY) ?: "Genel"

        val helper = NotificationHelper(context)
        helper.showTaskNotification(
            taskId = if (taskId != -1L) taskId else System.currentTimeMillis(),
            title = title,
            description = desc,
            category = category
        )
    }
}
