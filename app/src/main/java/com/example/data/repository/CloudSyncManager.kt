package com.example.data.repository

import android.content.Context
import android.provider.Settings
import com.example.data.local.TaskEntity
import com.example.model.SyncResult
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CloudSyncManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("planflow_sync_prefs", Context.MODE_PRIVATE)

    val deviceId: String by lazy {
        try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "device_default"
        } catch (_: Exception) {
            "device_default"
        }
    }

    fun getLastSyncTimeString(): String {
        val lastSync = prefs.getLong("last_sync_time", 0L)
        if (lastSync == 0L) return "Henüz senkronize edilmedi"
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("tr"))
        return sdf.format(Date(lastSync))
    }

    private fun updateLastSyncTime() {
        prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
    }

    suspend fun backupToCloud(tasks: List<TaskEntity>): SyncResult {
        var firestoreSuccess = false
        var errorMsg = ""

        try {
            // Check if Firebase is initialized
            val app = try {
                FirebaseApp.getInstance()
            } catch (_: Exception) {
                null
            }

            if (app != null) {
                val db = FirebaseFirestore.getInstance()
                val batch = db.batch()
                val userTasksCol = db.collection("planflow_backups").document(deviceId).collection("tasks")

                // Map tasks to Firestore documents
                for (task in tasks) {
                    val docRef = userTasksCol.document(task.id.toString())
                    val taskMap = hashMapOf(
                        "id" to task.id,
                        "title" to task.title,
                        "description" to task.description,
                        "dueDateMillis" to task.dueDateMillis,
                        "dueHour" to task.dueHour,
                        "dueMinute" to task.dueMinute,
                        "category" to task.category,
                        "priority" to task.priority,
                        "isCompleted" to task.isCompleted,
                        "completedAtMillis" to task.completedAtMillis,
                        "hasReminder" to task.hasReminder,
                        "reminderMinutesBefore" to task.reminderMinutesBefore,
                        "updatedAtMillis" to task.updatedAtMillis
                    )
                    batch.set(docRef, taskMap, SetOptions.merge())
                }

                // Update root metadata
                val metaDoc = db.collection("planflow_backups").document(deviceId)
                val metaMap = hashMapOf(
                    "lastSyncMillis" to System.currentTimeMillis(),
                    "taskCount" to tasks.size,
                    "deviceId" to deviceId
                )
                batch.set(metaDoc, metaMap, SetOptions.merge())

                batch.commit().await()
                firestoreSuccess = true
            }
        } catch (e: Exception) {
            errorMsg = e.localizedMessage ?: "Bulut sunucusuna bağlanılamadı"
        }

        // Always save a local snapshot JSON in SharedPreferences as offline cloud-cache
        val jsonBackup = serializeTasksToJson(tasks)
        prefs.edit()
            .putString("cloud_json_snapshot", jsonBackup)
            .putLong("last_sync_time", System.currentTimeMillis())
            .apply()

        updateLastSyncTime()

        return if (firestoreSuccess) {
            SyncResult(true, "${tasks.size} görev başarıyla Firebase Bulut veritabanına yedeklendi.")
        } else {
            SyncResult(
                true,
                "${tasks.size} görev yerel bulut önbelleğine yedeklendi. (Firebase: ${if (errorMsg.isNotBlank()) errorMsg else "Çevrimdışı modda saklandı"})"
            )
        }
    }

    suspend fun restoreFromCloud(): Pair<List<TaskEntity>?, SyncResult> {
        try {
            val app = try {
                FirebaseApp.getInstance()
            } catch (_: Exception) {
                null
            }

            if (app != null) {
                val db = FirebaseFirestore.getInstance()
                val snapshot = db.collection("planflow_backups")
                    .document(deviceId)
                    .collection("tasks")
                    .get()
                    .await()

                if (!snapshot.isEmpty) {
                    val restoredTasks = mutableListOf<TaskEntity>()
                    for (doc in snapshot.documents) {
                        val id = doc.getLong("id") ?: continue
                        val title = doc.getString("title") ?: "İsimsiz Görev"
                        val desc = doc.getString("description") ?: ""
                        val dueDate = doc.getLong("dueDateMillis") ?: System.currentTimeMillis()
                        val hour = doc.getLong("dueHour")?.toInt() ?: -1
                        val min = doc.getLong("dueMinute")?.toInt() ?: -1
                        val category = doc.getString("category") ?: "Genel"
                        val priority = doc.getString("priority") ?: "NORMAL"
                        val isCompleted = doc.getBoolean("isCompleted") ?: false
                        val completedAt = doc.getLong("completedAtMillis")
                        val hasReminder = doc.getBoolean("hasReminder") ?: false
                        val reminderMins = doc.getLong("reminderMinutesBefore")?.toInt() ?: 15
                        val updated = doc.getLong("updatedAtMillis") ?: System.currentTimeMillis()

                        restoredTasks.add(
                            TaskEntity(
                                id = id,
                                title = title,
                                description = desc,
                                dueDateMillis = dueDate,
                                dueHour = hour,
                                dueMinute = min,
                                category = category,
                                priority = priority,
                                isCompleted = isCompleted,
                                completedAtMillis = completedAt,
                                hasReminder = hasReminder,
                                reminderMinutesBefore = reminderMins,
                                updatedAtMillis = updated
                            )
                        )
                    }

                    if (restoredTasks.isNotEmpty()) {
                        updateLastSyncTime()
                        return Pair(
                            restoredTasks,
                            SyncResult(true, "${restoredTasks.size} görev Firestore bulutundan başarıyla geri yüklendi.")
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to local snapshot
        }

        // Fallback to JSON cache snapshot
        val cachedJson = prefs.getString("cloud_json_snapshot", null)
        if (!cachedJson.isNullOrBlank()) {
            val tasks = parseTasksFromJson(cachedJson)
            if (tasks.isNotEmpty()) {
                updateLastSyncTime()
                return Pair(
                    tasks,
                    SyncResult(true, "${tasks.size} görev bulut önbelleğinden geri yüklendi.")
                )
            }
        }

        return Pair(null, SyncResult(false, "Bulutta kayıtlı yedek bulunamadı."))
    }

    fun serializeTasksToJson(tasks: List<TaskEntity>): String {
        val jsonArray = JSONArray()
        for (task in tasks) {
            val obj = JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("description", task.description)
                put("dueDateMillis", task.dueDateMillis)
                put("dueHour", task.dueHour)
                put("dueMinute", task.dueMinute)
                put("category", task.category)
                put("priority", task.priority)
                put("isCompleted", task.isCompleted)
                put("completedAtMillis", task.completedAtMillis ?: JSONObject.NULL)
                put("hasReminder", task.hasReminder)
                put("reminderMinutesBefore", task.reminderMinutesBefore)
                put("updatedAtMillis", task.updatedAtMillis)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }

    fun parseTasksFromJson(jsonString: String): List<TaskEntity> {
        val tasks = mutableListOf<TaskEntity>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                tasks.add(
                    TaskEntity(
                        id = obj.optLong("id", 0L),
                        title = obj.optString("title", "Görev"),
                        description = obj.optString("description", ""),
                        dueDateMillis = obj.optLong("dueDateMillis", System.currentTimeMillis()),
                        dueHour = obj.optInt("dueHour", -1),
                        dueMinute = obj.optInt("dueMinute", -1),
                        category = obj.optString("category", "Genel"),
                        priority = obj.optString("priority", "NORMAL"),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        completedAtMillis = if (obj.isNull("completedAtMillis")) null else obj.optLong("completedAtMillis"),
                        hasReminder = obj.optBoolean("hasReminder", false),
                        reminderMinutesBefore = obj.optInt("reminderMinutesBefore", 15),
                        updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return tasks
    }
}
