package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.TaskRepository
import com.example.notification.NotificationHelper

class PlanFlowApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: TaskRepository
        private set

    lateinit var notificationHelper: NotificationHelper
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        notificationHelper = NotificationHelper(this)
        repository = TaskRepository(database.taskDao(), notificationHelper, this)
    }
}
