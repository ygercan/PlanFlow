package com.example.data.local

import androidx.room.TypeConverter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Room TypeConverter for converting between [Date], epoch milliseconds ([Long]),
 * and ISO-8601 `updated_at` timestamp strings used by Supabase/PostgreSQL.
 */
class TimestampConverters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    companion object {
        private val isoFormats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )

        fun toIsoString(millis: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return sdf.format(Date(millis))
        }

        fun toIsoString(date: Date): String = toIsoString(date.time)

        fun parseIsoToMillis(isoString: String?): Long? {
            if (isoString.isNullOrBlank()) return null
            val trimmed = isoString.trim()
            // Numeric epoch string fallback
            trimmed.toLongOrNull()?.let { return it }

            for (pattern in isoFormats) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val parsed = sdf.parse(trimmed)
                    if (parsed != null) return parsed.time
                } catch (_: Exception) {
                }
            }
            return null
        }
    }
}
