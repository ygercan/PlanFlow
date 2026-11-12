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

enum class TaskPriority(val displayName: String, val color: Color, val containerColor: Color) {
    LOW("Düşük", Color(0xFF10B981), Color(0xFFE8F5E9)),
    NORMAL("Orta", Color(0xFF3B82F6), Color(0xFFEFF6FF)),
    HIGH("Yüksek", Color(0xFFF59E0B), Color(0xFFFEF3C7)),
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
    GENEL("Genel", Color(0xFF6366F1), Icons.Default.Bookmark),
    IS("İş", Color(0xFF2563EB), Icons.Default.Work),
    KISISEL("Kişisel", Color(0xFF8B5CF6), Icons.Default.Person),
    SAGLIK("Sağlık", Color(0xFF10B981), Icons.Default.FitnessCenter),
    ALISVERIS("Alışveriş", Color(0xFFF97316), Icons.Default.ShoppingCart),
    EGITIM("Eğitim", Color(0xFF06B6D4), Icons.Default.School);

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

