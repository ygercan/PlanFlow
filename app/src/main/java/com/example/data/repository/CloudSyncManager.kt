package com.example.data.repository

import android.content.Context
import com.example.BuildConfig
import com.example.data.local.TaskEntity
import com.example.model.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CloudSyncManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("planflow_supabase_prefs", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        const val TABLE_NAME = "planflow_tasks"
        const val DEFAULT_ACCOUNT_KEY = "planflow-workspace"

        val SUPABASE_SCHEMA_SQL = """
            CREATE TABLE IF NOT EXISTS public.planflow_tasks (
                account_key TEXT NOT NULL,
                id BIGINT NOT NULL,
                title TEXT NOT NULL,
                description TEXT DEFAULT '',
                due_date_millis BIGINT NOT NULL,
                due_hour INT DEFAULT -1,
                due_minute INT DEFAULT -1,
                category TEXT DEFAULT 'Genel',
                priority TEXT DEFAULT 'NORMAL',
                is_completed BOOLEAN DEFAULT FALSE,
                completed_at_millis BIGINT,
                has_reminder BOOLEAN DEFAULT FALSE,
                reminder_minutes_before INT DEFAULT 15,
                created_at_millis BIGINT DEFAULT 0,
                updated_at_millis BIGINT NOT NULL,
                PRIMARY KEY (account_key, id)
            );

            ALTER TABLE public.planflow_tasks ENABLE ROW LEVEL SECURITY;

            CREATE POLICY "Allow anon access by account_key"
            ON public.planflow_tasks
            FOR ALL
            TO anon, authenticated
            USING (true)
            WITH CHECK (true);
        """.trimIndent()
    }

    private fun getConfiguredDefaultAccountKey(): String {
        val fromBuildConfig = try {
            BuildConfig.SUPABASE_ACCOUNT_KEY.trim()
        } catch (_: Exception) {
            ""
        }
        return if (fromBuildConfig.isNotBlank()) fromBuildConfig else DEFAULT_ACCOUNT_KEY
    }

    fun getAccountKey(): String {
        val saved = prefs.getString("account_key", null)
        return if (!saved.isNullOrBlank()) saved.trim() else getConfiguredDefaultAccountKey()
    }

    fun setAccountKey(newKey: String) {
        val cleaned = newKey.trim().ifBlank { getConfiguredDefaultAccountKey() }
        prefs.edit().putString("account_key", cleaned).apply()
    }

    fun isSupabaseConfigured(): Boolean {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_ANON_KEY.trim()
        return url.isNotEmpty() &&
                key.isNotEmpty() &&
                !url.contains("your-project.supabase.co") &&
                !key.contains("your-supabase-anon-key") &&
                url.startsWith("http")
    }

    private fun getBaseRestUrl(): String {
        val rawUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        return "$rawUrl/rest/v1/$TABLE_NAME"
    }

    fun getLastSyncTimeString(): String {
        val lastSync = prefs.getLong("last_sync_time", 0L)
        if (lastSync == 0L) return "Henüz senkronize edilmedi"
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale("tr"))
        return sdf.format(Date(lastSync))
    }

    private fun updateLastSyncTime() {
        prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
    }

    private fun Request.Builder.addSupabaseHeaders(): Request.Builder {
        val key = BuildConfig.SUPABASE_ANON_KEY.trim()
        addHeader("apikey", key)
        // Standard JWT anon keys require Bearer Authorization header; sb_publishable_ keys work with apikey header (and Bearer if JWT)
        if (!key.startsWith("sb_publishable_")) {
            addHeader("Authorization", "Bearer $key")
        }
        return this
    }

    /**
     * Anlık tekil görev yedekleme (ekleme / güncelleme / tamamlama sonrası otomatik çağrılır)
     */
    suspend fun pushSingleTaskToSupabase(task: TaskEntity): SyncResult = withContext(Dispatchers.IO) {
        if (!isSupabaseConfigured()) {
            return@withContext SyncResult(
                false,
                "Supabase bilgileri tanımlanmamış. AI Studio Secrets panelinden SUPABASE_URL ve SUPABASE_ANON_KEY ekleyin."
            )
        }

        val accountKey = getAccountKey()
        val payloadArray = JSONArray().apply {
            put(taskToSupabaseJson(task, accountKey))
        }

        val url = "${getBaseRestUrl()}?on_conflict=account_key,id"
        val request = Request.Builder()
            .url(url)
            .addSupabaseHeaders()
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(payloadArray.toString().toRequestBody(jsonMediaType))
            .build()

        return@withContext try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    updateLastSyncTime()
                    SyncResult(true, "Anlık Supabase yedeği güncellendi.")
                } else {
                    val errBody = response.body?.string().orEmpty()
                    SyncResult(false, "Supabase hatası (${response.code}): $errBody")
                }
            }
        } catch (e: Exception) {
            SyncResult(false, "Supabase bağlantı hatası: ${e.localizedMessage ?: "Ağ hatası"}")
        }
    }

    /**
     * Görev silindiğinde anlık olarak Supabase'den de siler
     */
    suspend fun deleteSingleTaskFromSupabase(taskId: Long): SyncResult = withContext(Dispatchers.IO) {
        if (!isSupabaseConfigured()) {
            return@withContext SyncResult(false, "Supabase yapılandırılmamış.")
        }

        val encodedAccount = URLEncoder.encode(getAccountKey(), "UTF-8")
        val url = "${getBaseRestUrl()}?account_key=eq.$encodedAccount&id=eq.$taskId"

        val request = Request.Builder()
            .url(url)
            .addSupabaseHeaders()
            .delete()
            .build()

        return@withContext try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    updateLastSyncTime()
                    SyncResult(true, "Görev Supabase'den silindi.")
                } else {
                    SyncResult(false, "Supabase silme hatası (${response.code})")
                }
            }
        } catch (e: Exception) {
            SyncResult(false, "Supabase silme bağlantı hatası: ${e.localizedMessage}")
        }
    }

    /**
     * Tüm görev listesini toplu olarak Supabase'e UPSERT eder
     */
    suspend fun backupToSupabase(tasks: List<TaskEntity>): SyncResult = withContext(Dispatchers.IO) {
        // Her durumda yerel JSON önbelleğini de güncel tut
        val jsonBackup = serializeTasksToJson(tasks)
        prefs.edit().putString("cloud_json_snapshot", jsonBackup).apply()

        if (!isSupabaseConfigured()) {
            return@withContext SyncResult(
                false,
                "Supabase bağlantı anahtarları eksik. Lütfen AI Studio Secrets panelinden SUPABASE_URL ve SUPABASE_ANON_KEY değerlerini girin."
            )
        }

        if (tasks.isEmpty()) {
            updateLastSyncTime()
            return@withContext SyncResult(true, "Yedeklenecek görev bulunmuyor.")
        }

        val accountKey = getAccountKey()
        val payloadArray = JSONArray()
        for (task in tasks) {
            payloadArray.put(taskToSupabaseJson(task, accountKey))
        }

        val url = "${getBaseRestUrl()}?on_conflict=account_key,id"
        val request = Request.Builder()
            .url(url)
            .addSupabaseHeaders()
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(payloadArray.toString().toRequestBody(jsonMediaType))
            .build()

        return@withContext try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    updateLastSyncTime()
                    SyncResult(
                        true,
                        "${tasks.size} görev Supabase ($accountKey) hesabına anlık yedeklendi."
                    )
                } else {
                    val errBody = response.body?.string().orEmpty()
                    SyncResult(
                        false,
                        "Supabase yedekleme hatası (${response.code}): ${errBody.take(120)}"
                    )
                }
            }
        } catch (e: Exception) {
            SyncResult(
                false,
                "Supabase sunucusuna ulaşılamadı: ${e.localizedMessage ?: "İnternet bağlantınızı kontrol edin"}"
            )
        }
    }

    /**
     * Supabase'den mevcut hesaba (account_key) ait tüm görevleri çeker
     */
    suspend fun fetchFromSupabase(): Pair<List<TaskEntity>?, SyncResult> = withContext(Dispatchers.IO) {
        if (!isSupabaseConfigured()) {
            return@withContext Pair(
                null,
                SyncResult(
                    false,
                    "Supabase yapılandırılmamış (SUPABASE_URL / SUPABASE_ANON_KEY bekleniyor)."
                )
            )
        }

        val accountKey = getAccountKey()
        val encodedAccount = URLEncoder.encode(accountKey, "UTF-8")
        val url = "${getBaseRestUrl()}?account_key=eq.$encodedAccount&select=*&order=updated_at_millis.desc"

        val request = Request.Builder()
            .url(url)
            .addSupabaseHeaders()
            .addHeader("Accept", "application/json")
            .get()
            .build()

        return@withContext try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: "[]"
                    val remoteTasks = parseTasksFromSupabaseJson(bodyStr)
                    updateLastSyncTime()
                    Pair(
                        remoteTasks,
                        SyncResult(
                            true,
                            "Supabase ($accountKey) üzerinden ${remoteTasks.size} görev senkronize edildi."
                        )
                    )
                } else {
                    val errBody = response.body?.string().orEmpty()
                    Pair(
                        null,
                        SyncResult(
                            false,
                            "Supabase veri çekme hatası (${response.code}): ${errBody.take(120)}"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Pair(
                null,
                SyncResult(
                    false,
                    "Supabase bağlantısı kurulamadı: ${e.localizedMessage ?: "Çevrimdışı yerel mod aktif"}"
                )
            )
        }
    }

    private fun taskToSupabaseJson(task: TaskEntity, accountKey: String): JSONObject {
        val effectiveUpdated = task.effectiveUpdatedAtMillis
        return JSONObject().apply {
            put("account_key", accountKey)
            put("id", task.id)
            put("title", task.title)
            put("description", task.description)
            put("due_date_millis", task.dueDateMillis)
            put("due_hour", task.dueHour)
            put("due_minute", task.dueMinute)
            put("category", task.category)
            put("priority", task.priority)
            put("is_completed", task.isCompleted)
            put("completed_at_millis", task.completedAtMillis ?: JSONObject.NULL)
            put("has_reminder", task.hasReminder)
            put("reminder_minutes_before", task.reminderMinutesBefore)
            put("created_at_millis", task.createdAtMillis)
            put("updated_at_millis", effectiveUpdated)
        }
    }

    private fun parseTasksFromSupabaseJson(jsonString: String): List<TaskEntity> {
        val tasks = mutableListOf<TaskEntity>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val now = System.currentTimeMillis()
                val createdMillis = when {
                    obj.has("created_at_millis") && !obj.isNull("created_at_millis") ->
                        obj.optLong("created_at_millis", now)
                    obj.has("created_at") && !obj.isNull("created_at") ->
                        com.example.data.local.TimestampConverters.parseIsoToMillis(obj.optString("created_at")) ?: now
                    else -> obj.optLong("createdAtMillis", now)
                }
                val updatedMillis = when {
                    obj.has("updated_at_millis") && !obj.isNull("updated_at_millis") ->
                        obj.optLong("updated_at_millis", now)
                    obj.has("updated_at") && !obj.isNull("updated_at") ->
                        com.example.data.local.TimestampConverters.parseIsoToMillis(obj.optString("updated_at")) ?: now
                    else -> obj.optLong("updatedAtMillis", now)
                }

                tasks.add(
                    TaskEntity(
                        id = obj.optLong("id", 0L),
                        title = obj.optString("title", obj.optString("name", "Görev")),
                        description = obj.optString("description", ""),
                        dueDateMillis = obj.optLong(
                            "due_date_millis",
                            obj.optLong("dueDateMillis", now)
                        ),
                        dueHour = obj.optInt("due_hour", obj.optInt("dueHour", -1)),
                        dueMinute = obj.optInt("due_minute", obj.optInt("dueMinute", -1)),
                        category = obj.optString("category", "Genel"),
                        priority = obj.optString("priority", "NORMAL"),
                        isCompleted = obj.optBoolean(
                            "is_completed",
                            obj.optBoolean("isCompleted", false)
                        ),
                        completedAtMillis = when {
                            !obj.isNull("completed_at_millis") -> obj.optLong("completed_at_millis")
                            !obj.isNull("completedAtMillis") -> obj.optLong("completedAtMillis")
                            else -> null
                        },
                        hasReminder = obj.optBoolean(
                            "has_reminder",
                            obj.optBoolean("hasReminder", false)
                        ),
                        reminderMinutesBefore = obj.optInt(
                            "reminder_minutes_before",
                            obj.optInt("reminderMinutesBefore", 15)
                        ),
                        createdAtMillis = createdMillis,
                        updatedAtMillis = updatedMillis,
                        createdAt = Date(createdMillis),
                        updatedAt = Date(updatedMillis)
                    )
                )
            }
        } catch (_: Exception) {
        }
        return tasks
    }

    fun serializeTasksToJson(tasks: List<TaskEntity>): String {
        val jsonArray = JSONArray()
        val accountKey = getAccountKey()
        for (task in tasks) {
            jsonArray.put(taskToSupabaseJson(task, accountKey))
        }
        return jsonArray.toString(2)
    }

    fun parseTasksFromJson(jsonString: String): List<TaskEntity> {
        return parseTasksFromSupabaseJson(jsonString)
    }
}
