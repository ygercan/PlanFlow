package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Primary Room entity representing a TodoItem / Task stored locally in SQLite
 * and synchronized with Supabase using `updated_at` timestamps.
 */
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
    val updatedAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date(createdAtMillis),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Date = Date(updatedAtMillis)
) {
    /**
     * Returns the authoritative last-modified epoch timestamp in milliseconds,
     * keeping `updatedAt` ([Date]) and `updatedAtMillis` ([Long]) strictly aligned.
     */
    val effectiveUpdatedAtMillis: Long
        get() = maxOf(updatedAtMillis, updatedAt.time)
}

/**
 * Typealias so code referencing `TodoItem` or `TaskEntity` works seamlessly
 * against the same Room primary table.
 */
typealias TodoItem = TaskEntity
