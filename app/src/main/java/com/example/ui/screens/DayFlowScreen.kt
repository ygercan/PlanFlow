package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TaskEntity
import com.example.data.repository.TaskRepository
import com.example.model.RecurrenceRule
import com.example.model.SubtaskCodec
import com.example.ui.theme.GhostDayNumber
import com.example.ui.theme.GhostDayNumberDark
import com.example.util.rememberHapticController
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DayFlowScreen(
    tasks: List<TaskEntity>,
    onToggleTask: (Long) -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onRescheduleOverdue: () -> Unit = {},
    onRescheduleSingleTask: (Long) -> Unit = {},
    onAddTaskForDate: (Long) -> Unit
) {
    val haptics = rememberHapticController()
    val todayMillis = remember { TaskRepository.getDayStartMillis(System.currentTimeMillis()) }

    val overdueTasks = remember(tasks, todayMillis) {
        tasks.filter { !it.isCompleted && it.dueDateMillis < todayMillis }
    }

    // Generate continuous days in Turkish (2 days past, today, next 18 days)
    val dayItems = remember(todayMillis) {
        val list = mutableListOf<FlowDayData>()
        val trLocale = Locale("tr")
        val dayFormat = SimpleDateFormat("EEEE", trLocale)
        val monthFormat = SimpleDateFormat("MMMM", trLocale)
        val cal = Calendar.getInstance().apply {
            timeInMillis = todayMillis
            add(Calendar.DAY_OF_YEAR, -2)
        }
        for (i in 0..20) {
            val dateMs = TaskRepository.getDayStartMillis(cal.timeInMillis)
            list.add(
                FlowDayData(
                    dateMillis = dateMs,
                    dayNumber = cal.get(Calendar.DAY_OF_MONTH),
                    dayOfWeekName = dayFormat.format(cal.time).replaceFirstChar { it.uppercase(trLocale) },
                    monthName = monthFormat.format(cal.time).replaceFirstChar { it.uppercase(trLocale) },
                    isToday = (dateMs == todayMillis)
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    val listState = rememberLazyListState()

    // Scroll to today on first launch (accounting for optional overdue banner item)
    LaunchedEffect(Unit) {
        val todayIndex = dayItems.indexOfFirst { it.isToday }
        if (todayIndex > 0) {
            val offset = if (overdueTasks.isNotEmpty()) 1 else 0
            listState.animateScrollToItem(todayIndex + offset)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag("day_flow_screen"),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            if (overdueTasks.isNotEmpty()) {
                item(key = "overdue_rescue_banner") {
                    OverdueRescueCard(
                        overdueTasks = overdueTasks,
                        onRescheduleAll = {
                            haptics.performTaskCompletion(isCompleting = true)
                            onRescheduleOverdue()
                        },
                        onRescheduleSingle = { id ->
                            haptics.performNavigationTap()
                            onRescheduleSingleTask(id)
                        },
                        onToggleTask = onToggleTask,
                        onEditTask = onEditTask
                    )
                }
            }

            items(dayItems, key = { it.dateMillis }) { day ->
                val dayStart = day.dateMillis
                val dayEnd = TaskRepository.getDayEndMillis(day.dateMillis)
                val dayTasks = tasks.filter { it.dueDateMillis in dayStart..dayEnd }

                DayBlock(
                    day = day,
                    tasks = dayTasks,
                    onToggleTask = onToggleTask,
                    onEditTask = onEditTask,
                    onAddTask = {
                        haptics.performNavigationTap()
                        onAddTaskForDate(day.dateMillis)
                    }
                )
            }
        }

        // Solid Circular Floating Add Button (+)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 28.dp)
                .size(54.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable {
                    haptics.performNavigationTap()
                    onAddTaskForDate(todayMillis)
                }
                .testTag("floating_add_task_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Yeni Görev Ekle",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun OverdueRescueCard(
    overdueTasks: List<TaskEntity>,
    onRescheduleAll: () -> Unit,
    onRescheduleSingle: (Long) -> Unit,
    onToggleTask: (Long) -> Unit,
    onEditTask: (TaskEntity) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trLocale = Locale("tr")
    val dateFmt = remember { SimpleDateFormat("d MMM", trLocale) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            .border(
                width = 1.dp,
                color = primaryColor.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
            .testTag("overdue_rescue_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.EventRepeat,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gecikmiş Görevler (${overdueTasks.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(primaryColor)
                    .clickable { onRescheduleAll() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("reschedule_all_overdue_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Today,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Tümünü Bugüne Aktar",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            overdueTasks.take(5).forEach { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateFmt.format(Date(task.dueDateMillis)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { onToggleTask(task.id) }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = task.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onEditTask(task) }
                    )
                    Text(
                        text = "Bugüne Al",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onRescheduleSingle(task.id) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DayBlock(
    day: FlowDayData,
    tasks: List<TaskEntity>,
    onToggleTask: (Long) -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onAddTask: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val ghostColor = if (isDark) GhostDayNumberDark else GhostDayNumber
    val primaryColor = MaterialTheme.colorScheme.primary

    val numberColor = when {
        day.isToday -> primaryColor
        tasks.isNotEmpty() -> MaterialTheme.colorScheme.onBackground
        else -> ghostColor
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAddTask() }
    ) {
        // Day Header: Big Day Number on left, Turkish Weekday & Month on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${day.dayNumber}",
                fontSize = 44.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Bold,
                color = numberColor,
                letterSpacing = (-1.5).sp
            )

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = day.dayOfWeekName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (day.isToday) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = day.monthName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        if (tasks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                tasks.forEach { task ->
                    DayFlowTaskRow(
                        task = task,
                        onToggle = { onToggleTask(task.id) },
                        onEdit = { onEditTask(task) }
                    )
                }
            }
        }
    }
}

@Composable
fun DayFlowTaskRow(
    task: TaskEntity,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    val haptics = rememberHapticController()
    val isCompleted = task.isCompleted
    val primaryColor = MaterialTheme.colorScheme.primary
    val recurrence = RecurrenceRule.fromString(task.recurrenceRule)
    val subtasks = remember(task.subtasksJson) { SubtaskCodec.parse(task.subtasksJson) }
    val doneSubtasks = subtasks.count { it.isDone }

    // Turkish labels & 24-hour time formatting
    val tagText = when {
        isCompleted -> "BİTTİ"
        task.dueHour >= 0 && task.dueMinute >= 0 ->
            String.format(Locale.getDefault(), "%02d:%02d", task.dueHour, task.dueMinute)
        else -> "YAPILACAK"
    }

    val tagColor by animateColorAsState(
        targetValue = if (isCompleted) Color(0xFF9CA3AF) else primaryColor,
        label = "tag_color"
    )

    val textColor by animateColorAsState(
        targetValue = if (isCompleted) Color(0xFF9CA3AF) else MaterialTheme.colorScheme.onBackground,
        label = "text_color"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Tag Column — Tap to toggle completion
        Box(
            modifier = Modifier
                .width(74.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    haptics.performTaskCompletion(isCompleting = !isCompleted)
                    onToggle()
                }
                .padding(vertical = 4.dp)
                .testTag("dayflow_toggle_${task.id}"),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = tagText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = tagColor,
                letterSpacing = 0.4.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Task Title & Indicators — Tap to edit
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    haptics.performNavigationTap()
                    onEdit()
                }
                .padding(vertical = 4.dp)
                .testTag("dayflow_edit_${task.id}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = task.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = textColor,
                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.weight(1f)
            )

            if (recurrence != RecurrenceRule.NONE) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = recurrence.displayName,
                    tint = if (isCompleted) Color(0xFF9CA3AF) else primaryColor,
                    modifier = Modifier.size(13.dp)
                )
            }

            if (subtasks.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$doneSubtasks/${subtasks.size}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

data class FlowDayData(
    val dateMillis: Long,
    val dayNumber: Int,
    val dayOfWeekName: String,
    val monthName: String,
    val isToday: Boolean
)
