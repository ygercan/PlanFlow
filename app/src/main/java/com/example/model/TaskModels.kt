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

enum class ActiveTab(val title: String) {
    FLOW("Akış"),
    CALENDAR("Takvim"),
    TODO("Görevler"),
    OVERVIEW("Özet")
}

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
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
