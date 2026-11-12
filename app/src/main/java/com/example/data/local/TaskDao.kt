package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, dueDateMillis ASC, dueHour ASC, dueMinute ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE dueDateMillis >= :startOfDayMillis AND dueDateMillis <= :endOfDayMillis ORDER BY isCompleted ASC, dueHour ASC, dueMinute ASC")
    fun getTasksForDateRange(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY dueDateMillis ASC, dueHour ASC, dueMinute ASC")
    fun getPendingTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 1 ORDER BY completedAtMillis DESC")
    fun getCompletedTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    fun getTaskById(taskId: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskByIdDirect(taskId: Long): TaskEntity?

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksDirect(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>): List<Long>

    @Upsert
    suspend fun upsertTask(task: TaskEntity): Long

    @Upsert
    suspend fun upsertTasks(tasks: List<TaskEntity>)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Long)

    @Query("DELETE FROM tasks")
    suspend fun clearAllTasks()

    /**
     * Atomically applies resolved conflict items into SQLite and returns the updated total count.
     * If any insert/upsert fails, the entire transaction is rolled back.
     */
    @Transaction
    suspend fun applyConflictResolutionAtomically(itemsToUpsert: List<TaskEntity>): Int {
        if (itemsToUpsert.isNotEmpty()) {
            upsertTasks(itemsToUpsert)
        }
        return getAllTasksDirect().size
    }

    /**
     * Atomically replaces all tasks in the database (all-or-nothing).
     */
    @Transaction
    suspend fun replaceAllTasksAtomically(tasks: List<TaskEntity>): Int {
        clearAllTasks()
        if (tasks.isNotEmpty()) {
            upsertTasks(tasks)
        }
        return getAllTasksDirect().size
    }

    /**
     * Atomically verifies and normalizes timestamps across all local tasks before an app update.
     */
    @Transaction
    suspend fun checkpointAndNormalizeTasksAtomically(): List<TaskEntity> {
        val current = getAllTasksDirect()
        val now = System.currentTimeMillis()
        val normalized = current.map { task ->
            val validCreated = if (task.createdAtMillis > 0) task.createdAtMillis else now
            val validUpdated = if (task.updatedAtMillis > 0) task.updatedAtMillis else validCreated
            task.copy(
                createdAtMillis = validCreated,
                updatedAtMillis = validUpdated,
                createdAt = Date(validCreated),
                updatedAt = Date(validUpdated)
            )
        }
        if (normalized.isNotEmpty()) {
            upsertTasks(normalized)
        }
        return getAllTasksDirect()
    }
}

typealias TodoDao = TaskDao
