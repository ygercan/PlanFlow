package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TaskEntity
import com.example.data.repository.TaskRepository
import com.example.ui.theme.CoralToday
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
    onDeleteTask: (Long) -> Unit,
    onAddTaskForDate: (Long) -> Unit,
    onOpenMenu: () -> Unit
) {
    val haptics = rememberHapticController()
    val todayMillis = remember { TaskRepository.getDayStartMillis(System.currentTimeMillis()) }

    // Generate continuous days (e.g., 3 days in the past, today, and next 17 days)
    val dayItems = remember {
        val list = mutableListOf<FlowDayData>()
        val cal = Calendar.getInstance().apply {
            timeInMillis = todayMillis
            add(Calendar.DAY_OF_YEAR, -2) // 2 days before
        }
        for (i in 0..20) {
            val dateMs = TaskRepository.getDayStartMillis(cal.timeInMillis)
            list.add(
                FlowDayData(
                    dateMillis = dateMs,
                    dayNumber = cal.get(Calendar.DAY_OF_MONTH),
                    dayOfWeekName = SimpleDateFormat("EEEE", Locale("en")).format(cal.time),
                    monthName = SimpleDateFormat("MMMM", Locale("en")).format(cal.time),
                    isToday = (dateMs == todayMillis)
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    val listState = rememberLazyListState()

    // Scroll to today on first launch
    LaunchedEffect(Unit) {
        val todayIndex = dayItems.indexOfFirst { it.isToday }
        if (todayIndex > 0) {
            listState.animateScrollToItem(todayIndex)
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
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
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
                .background(MaterialTheme.colorScheme.onBackground)
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
                tint = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(24.dp)
            )
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

    // Day number color
    val numberColor = when {
        day.isToday -> CoralToday
        tasks.isNotEmpty() -> MaterialTheme.colorScheme.onBackground
        else -> ghostColor
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAddTask() }
    ) {
        // Day Header: Big Day Number on left, Weekday & Month on right
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

        // Tasks List
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

    // Format time or status
    val tagText = when {
        isCompleted -> "DONE"
        task.dueHour >= 0 && task.dueMinute >= 0 -> {
            val hour = if (task.dueHour % 12 == 0) 12 else task.dueHour % 12
            val amPm = if (task.dueHour >= 12) "P" else "A"
            if (task.dueMinute == 0) "$hour:00 $amPm" else String.format(Locale.getDefault(), "%d:%02d %s", hour, task.dueMinute, amPm)
        }
        else -> "TODO"
    }

    val tagColor by animateColorAsState(
        targetValue = if (isCompleted) Color(0xFF9CA3AF) else CoralToday,
        label = "tag_color"
    )

    val textColor by animateColorAsState(
        targetValue = if (isCompleted) Color(0xFF9CA3AF) else MaterialTheme.colorScheme.onBackground,
        label = "text_color"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.performTaskCompletion(isCompleting = !isCompleted)
                onToggle()
            }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tag Column (DONE, TODO, 4:00 P, etc.)
        Text(
            text = tagText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = tagColor,
            modifier = Modifier.width(62.dp),
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Task Title
        Text(
            text = task.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = textColor,
            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f)
        )
    }
}

data class FlowDayData(
    val dateMillis: Long,
    val dayNumber: Int,
    val dayOfWeekName: String,
    val monthName: String,
    val isToday: Boolean
)
