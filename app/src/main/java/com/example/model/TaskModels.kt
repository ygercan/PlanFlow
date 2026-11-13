package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.CoralToday
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class TaskPriority(val displayName: String, val color: Color, val containerColor: Color) {
    LOW("Düşük", Color(0xFF6B7280), Color(0xFFF3F4F6)),
    NORMAL("Orta", CoralToday, Color(0xFFFFE5E3)),
    HIGH("Yüksek", Color(0xFFF97316), Color(0xFFFFEDD5)),
    URGENT("Acil", Color(0xFFEF4444), Color(0xFFFEE2E2));

    companion object {
        fun fromString(value: String): TaskPriority {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NORMAL
        }
    }
}

enum class TaskCategory(
    val title: String,
    val color: Color,
    val icon: ImageVector
) {
    GENEL("Genel", CoralToday, Icons.Default.Bookmark),
    IS("İş", Color(0xFFE05A50), Icons.Default.Work),
    KISISEL("Kişisel", Color(0xFFF97316), Icons.Default.Person),
    SAGLIK("Sağlık", Color(0xFF10B981), Icons.Default.FitnessCenter),
    ALISVERIS("Alışveriş", Color(0xFFF59E0B), Icons.Default.ShoppingCart),
    EGITIM("Eğitim", Color(0xFF6366F1), Icons.Default.School);

    companion object {
        fun fromTitle(title: String): TaskCategory {
            return entries.firstOrNull { it.title.equals(title, ignoreCase = true) } ?: GENEL
        }
    }
}

enum class RecurrenceRule(val displayName: String, val shortLabel: String) {
    NONE("Tekrar Yok", ""),
    DAILY("Günlük", "Günlük"),
    WEEKLY("Haftalık", "Haftalık"),
    MONTHLY("Aylık", "Aylık");

    companion object {
        fun fromString(value: String?): RecurrenceRule {
            if (value.isNullOrBlank()) return NONE
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: NONE
        }
    }
}

data class SubtaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isDone: Boolean = false
)

object SubtaskCodec {
    fun parse(json: String?): List<SubtaskItem> {
        if (json.isNullOrBlank() || json.trim() == "[]") return emptyList()
        val result = mutableListOf<SubtaskItem>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val title = obj.optString("title", "").trim()
                if (title.isBlank()) continue
                val id = obj.optString("id", "").ifBlank { "sub_$i" }
                val isDone = obj.optBoolean("isDone", false)
                result.add(SubtaskItem(id = id, title = title, isDone = isDone))
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun encode(items: List<SubtaskItem>): String {
        val arr = JSONArray()
        items.filter { it.title.isNotBlank() }.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title.trim())
                    put("isDone", item.isDone)
                }
            )
        }
        return arr.toString()
    }

    fun resetAll(json: String?): String {
        val parsed = parse(json)
        if (parsed.isEmpty()) return "[]"
        return encode(parsed.map { it.copy(isDone = false) })
    }
}

enum class AppAccentTheme(
    val displayName: String,
    val primary: Color,
    val darkAccent: Color,
    val mutedLight: Color,
    val mutedDark: Color
) {
    CORAL(
        displayName = "Mercan",
        primary = Color(0xFFFA7268),
        darkAccent = Color(0xFFE05A50),
        mutedLight = Color(0xFFFFE0DC),
        mutedDark = Color(0xFF3A1D1B)
    ),
    OCEAN(
        displayName = "Okyanus",
        primary = Color(0xFF3B82F6),
        darkAccent = Color(0xFF2563EB),
        mutedLight = Color(0xFFDBEAFE),
        mutedDark = Color(0xFF172554)
    ),
    EMERALD(
        displayName = "Zümrüt",
        primary = Color(0xFF10B981),
        darkAccent = Color(0xFF059669),
        mutedLight = Color(0xFFD1FAE5),
        mutedDark = Color(0xFF064E3B)
    ),
    LAVENDER(
        displayName = "Lavanta",
        primary = Color(0xFF8B5CF6),
        darkAccent = Color(0xFF7C3AED),
        mutedLight = Color(0xFFEDE9FE),
        mutedDark = Color(0xFF2E1065)
    ),
    AMBER(
        displayName = "Kehribar",
        primary = Color(0xFFF59E0B),
        darkAccent = Color(0xFFD97706),
        mutedLight = Color(0xFFFEF3C7),
        mutedDark = Color(0xFF451A03)
    );

    companion object {
        fun fromString(value: String?): AppAccentTheme {
            if (value.isNullOrBlank()) return CORAL
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: CORAL
        }
    }
}

enum class ActiveTab(val title: String) {
    FLOW("Akış"),
    CALENDAR("Takvim"),
    TODO("Görevler"),
    OVERVIEW("Özet")
}

enum class AppThemeMode(val displayName: String) {
    SYSTEM("Sistem"),
    LIGHT("Açık"),
    DARK("Karanlık");

    companion object {
        fun fromString(value: String?): AppThemeMode {
            if (value.isNullOrBlank()) return SYSTEM
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: SYSTEM
        }
    }
}

data class SyncResult(
    val success: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class DailyProgressState(
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val progressFraction: Float = 0f,
    val percentage: Int = 0
)
