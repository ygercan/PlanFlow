package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TaskEntity
import com.example.data.repository.TaskRepository
import com.example.ui.components.TaskItemCard
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CalendarScreen(
    tasks: List<TaskEntity>,
    selectedDateMillis: Long,
    currentMonthCalendar: Calendar,
    onDateSelected: (Long) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onJumpToToday: () -> Unit,
    onAddTaskForDate: (Long) -> Unit,
    onToggleTask: (Long) -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onDeleteTask: (Long) -> Unit
) {
    val monthTitle = remember(currentMonthCalendar.timeInMillis) {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale("tr"))
        sdf.format(currentMonthCalendar.time).replaceFirstChar { it.uppercase() }
    }

    val selectedDateString = remember(selectedDateMillis) {
        val sdf = SimpleDateFormat("d MMMM, EEEE", Locale("tr"))
        sdf.format(Date(selectedDateMillis)).replaceFirstChar { it.uppercase() }
    }

    val selectedDayStart = TaskRepository.getDayStartMillis(selectedDateMillis)
    val selectedDayEnd = TaskRepository.getDayEndMillis(selectedDateMillis)

    val tasksOnSelectedDate = tasks.filter {
        it.dueDateMillis in selectedDayStart..selectedDayEnd
    }

    val todayMillis = remember { TaskRepository.getDayStartMillis(System.currentTimeMillis()) }

    val daysInGrid = remember(currentMonthCalendar.timeInMillis) {
        calculateCalendarDays(currentMonthCalendar)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("calendar_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Month Header & Navigation
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = monthTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Bugün",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onJumpToToday() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = onPrevMonth,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                                contentDescription = "Önceki Ay",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                        }

                        IconButton(
                            onClick = onNextMonth,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "Sonraki Ay",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Weekday initials: P, S, Ç, P, C, C, P
                val weekDays = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    weekDays.forEach { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Calendar Grid
                val chunkedWeeks = daysInGrid.chunked(7)
                chunkedWeeks.forEach { week ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        week.forEach { dayInfo ->
                            val isSelected = dayInfo.dateMillis == selectedDayStart
                            val isToday = dayInfo.dateMillis == todayMillis

                            val dayTasks = tasks.filter {
                                it.dueDateMillis in dayInfo.dateMillis..TaskRepository.getDayEndMillis(dayInfo.dateMillis)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .border(
                                        width = if (isToday && !isSelected) 1.dp else 0.dp,
                                        color = if (isToday && !isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { onDateSelected(dayInfo.dateMillis) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "${dayInfo.dayNumber}",
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White
                                        else if (!dayInfo.isCurrentMonth) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                        else MaterialTheme.colorScheme.onSurface
                                    )

                                    if (dayTasks.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) Color.White
                                                    else MaterialTheme.colorScheme.primary
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Agenda for Selected Day
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = selectedDateString,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (tasksOnSelectedDate.isEmpty()) "Planlanan görev yok" else "${tasksOnSelectedDate.size} görev planlandı",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { onAddTaskForDate(selectedDateMillis) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Görev Ekle",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Tasks on Selected Date
        if (tasksOnSelectedDate.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bu tarihte herhangi bir plan bulunmuyor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(tasksOnSelectedDate, key = { it.id }) { task ->
                TaskItemCard(
                    task = task,
                    onToggleComplete = { onToggleTask(task.id) },
                    onEdit = { onEditTask(task) },
                    onDelete = { onDeleteTask(task.id) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

data class CalendarDayInfo(
    val dateMillis: Long,
    val dayNumber: Int,
    val isCurrentMonth: Boolean
)

fun calculateCalendarDays(monthCalendar: Calendar): List<CalendarDayInfo> {
    val days = mutableListOf<CalendarDayInfo>()
    val cal = Calendar.getInstance().apply {
        timeInMillis = monthCalendar.timeInMillis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val currentMonth = cal.get(Calendar.MONTH)

    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val leadingDays = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - Calendar.MONDAY

    cal.add(Calendar.DAY_OF_MONTH, -leadingDays)
    for (i in 0 until leadingDays) {
        days.add(
            CalendarDayInfo(
                dateMillis = cal.timeInMillis,
                dayNumber = cal.get(Calendar.DAY_OF_MONTH),
                isCurrentMonth = false
            )
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }

    while (cal.get(Calendar.MONTH) == currentMonth) {
        days.add(
            CalendarDayInfo(
                dateMillis = cal.timeInMillis,
                dayNumber = cal.get(Calendar.DAY_OF_MONTH),
                isCurrentMonth = true
            )
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }

    while (days.size % 7 != 0) {
        days.add(
            CalendarDayInfo(
                dateMillis = cal.timeInMillis,
                dayNumber = cal.get(Calendar.DAY_OF_MONTH),
                isCurrentMonth = false
            )
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }

    return days
}
