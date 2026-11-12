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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TaskEntity
import com.example.data.repository.TaskRepository
import com.example.model.ActiveTab
import com.example.ui.components.TaskItemCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OverviewScreen(
    tasks: List<TaskEntity>,
    lastSyncTime: String,
    onNavigateTab: (ActiveTab) -> Unit,
    onAddTask: () -> Unit,
    onToggleTask: (Long) -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onOpenSyncDialog: () -> Unit
) {
    val todayStart = remember { TaskRepository.getDayStartMillis(System.currentTimeMillis()) }
    val todayEnd = remember { TaskRepository.getDayEndMillis(System.currentTimeMillis()) }

    val todayTasks = tasks.filter { it.dueDateMillis in todayStart..todayEnd }
    val pendingToday = todayTasks.filter { !it.isCompleted }
    val completedToday = todayTasks.filter { it.isCompleted }

    val pendingTotal = tasks.count { !it.isCompleted }
    val urgentCount = tasks.count { !it.isCompleted && (it.priority == "URGENT" || it.priority == "HIGH") }

    val dateFormat = SimpleDateFormat("d MMMM, EEEE", Locale("tr"))
    val todayFormatted = dateFormat.format(Date()).replaceFirstChar { it.uppercase() }

    // Group today's tasks by time of day
    val morningTasks = todayTasks.filter { it.dueHour in 0..11 }
    val afternoonTasks = todayTasks.filter { it.dueHour in 12..17 }
    val eveningTasks = todayTasks.filter { it.dueHour in 18..23 }
    val allDayTasks = todayTasks.filter { it.dueHour < 0 }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("overview_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Calm Date Header
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = todayFormatted,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        todayTasks.isEmpty() -> "Bugün için kayıtlı görev bulunmuyor"
                        pendingToday.isEmpty() -> "Bugünün tüm görevleri tamamlandı ✓"
                        else -> "${pendingToday.size} görev tamamlanmayı bekliyor"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Minimalist Motivational Progress & Daily Stats Card
        item {
            com.example.ui.components.MotivationalProgressCard(
                completedCount = completedToday.size,
                totalCount = todayTasks.size
            )
        }

        // Functional Quick Add Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onAddTask() }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("quick_add_task_bar"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Yeni bir görev ekle...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Compact 1-line Summary Strip
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "$pendingTotal Bekleyen",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "·",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${completedToday.size} Biten",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF10B981)
                    )
                    if (urgentCount > 0) {
                        Text(
                            text = "·",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$urgentCount Acil",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFEF4444)
                        )
                    }
                }

                Text(
                    text = "Tümü →",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onNavigateTab(ActiveTab.TODO) }
                )
            }
        }

        // Empty state
        if (todayTasks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Bugün için görev yok",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Gününüzü planlamak için yukarıdaki alana dokunun.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section: Morning (Sabah)
        if (morningTasks.isNotEmpty()) {
            item {
                SectionHeader(title = "Sabah", timeRange = "06:00 - 12:00")
            }
            items(morningTasks, key = { it.id }) { task ->
                TaskItemCard(
                    task = task,
                    onToggleComplete = { onToggleTask(task.id) },
                    onEdit = { onEditTask(task) },
                    onDelete = { onDeleteTask(task.id) }
                )
            }
        }

        // Section: Afternoon (Öğleden Sonra)
        if (afternoonTasks.isNotEmpty()) {
            item {
                SectionHeader(title = "Öğle & İkindi", timeRange = "12:00 - 18:00")
            }
            items(afternoonTasks, key = { it.id }) { task ->
                TaskItemCard(
                    task = task,
                    onToggleComplete = { onToggleTask(task.id) },
                    onEdit = { onEditTask(task) },
                    onDelete = { onDeleteTask(task.id) }
                )
            }
        }

        // Section: Evening (Akşam)
        if (eveningTasks.isNotEmpty()) {
            item {
                SectionHeader(title = "Akşam", timeRange = "18:00 - 24:00")
            }
            items(eveningTasks, key = { it.id }) { task ->
                TaskItemCard(
                    task = task,
                    onToggleComplete = { onToggleTask(task.id) },
                    onEdit = { onEditTask(task) },
                    onDelete = { onDeleteTask(task.id) }
                )
            }
        }

        // Section: All day / No specific time
        if (allDayTasks.isNotEmpty()) {
            item {
                SectionHeader(title = "Günün Diğer Görevleri", timeRange = "Saat belirtilmemiş")
            }
            items(allDayTasks, key = { it.id }) { task ->
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

@Composable
fun SectionHeader(title: String, timeRange: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = timeRange,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
