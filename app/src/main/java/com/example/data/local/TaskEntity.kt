package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val dueDateMillis: Long, // Start of the selected day in UTC/epoch ms
    val dueHour: Int = -1,   // 0-23 or -1 for all day
    val dueMinute: Int = -1, // 0-59 or -1 for all day
    val category: String = "Genel",
    val priority: String = "NORMAL", // LOW, NORMAL, HIGH, URGENT
    val isCompleted: Boolean = false,
    val completedAtMillis: Long? = null,
    val hasReminder: Boolean = false,
    val reminderMinutesBefore: Int = 15, // 0, 5, 15, 30, 60
    val reminderScheduledTimeMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)
