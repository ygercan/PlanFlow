package com.example.ui.dialogs

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.TaskEntity
import com.example.model.RecurrenceRule
import com.example.model.SubtaskCodec
import com.example.model.SubtaskItem
import com.example.model.TaskCategory
import com.example.model.TaskPriority
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskDialog(
    initialTask: TaskEntity?,
    defaultDateMillis: Long,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        dueDateMillis: Long,
        dueHour: Int,
        dueMinute: Int,
        category: String,
        priority: String,
        hasReminder: Boolean,
        reminderMinutesBefore: Int,
        recurrenceRule: String,
        subtasksJson: String
    ) -> Unit
) {
    var title by remember { mutableStateOf(initialTask?.title ?: "") }
    var description by remember { mutableStateOf(initialTask?.description ?: "") }
    var dueDateMillis by remember { mutableLongStateOf(initialTask?.dueDateMillis ?: defaultDateMillis) }
    var dueHour by remember { mutableIntStateOf(initialTask?.dueHour ?: 12) }
    var dueMinute by remember { mutableIntStateOf(initialTask?.dueMinute ?: 0) }
    var hasSpecificTime by remember { mutableStateOf((initialTask?.dueHour ?: -1) >= 0) }
    var selectedCategory by remember { mutableStateOf(initialTask?.category ?: "Genel") }
    var selectedPriority by remember { mutableStateOf(initialTask?.priority ?: "NORMAL") }
    var hasReminder by remember { mutableStateOf(initialTask?.hasReminder ?: false) }
    var reminderMinutesBefore by remember { mutableIntStateOf(initialTask?.reminderMinutesBefore ?: 15) }
    var selectedRecurrence by remember {
        mutableStateOf(RecurrenceRule.fromString(initialTask?.recurrenceRule).name)
    }
    var subtasks by remember { mutableStateOf(SubtaskCodec.parse(initialTask?.subtasksJson)) }
    var newSubtaskTitle by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dueDateMillis
    )

    val timePickerState = rememberTimePickerState(
        initialHour = if (dueHour >= 0) dueHour else 12,
        initialMinute = if (dueMinute >= 0) dueMinute else 0,
        is24Hour = true
    )

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = primaryColor,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = primaryColor,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = primaryColor,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(22.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Editorial Header with Accent Dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(primaryColor)
                    )
                    Text(
                        text = if (initialTask == null) "Yeni Görev Ekle" else "Görevi Düzenle",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.4).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) titleError = false
                    },
                    label = { Text("Görev Başlığı *") },
                    isError = titleError,
                    supportingText = { if (titleError) Text("Lütfen bir başlık girin") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = textFieldColors
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Description Input
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Açıklama (İsteğe bağlı)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_desc_input"),
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = textFieldColors
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Subtasks (Checklist) Builder
                Text(
                    text = "Alt Görevler (Kontrol Listesi)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newSubtaskTitle,
                        onValueChange = { newSubtaskTitle = it },
                        placeholder = { Text("Alt adım ekle...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("subtask_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = textFieldColors
                    )
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(primaryColor)
                            .clickable {
                                val trimmed = newSubtaskTitle.trim()
                                if (trimmed.isNotEmpty()) {
                                    subtasks = subtasks + SubtaskItem(title = trimmed)
                                    newSubtaskTitle = ""
                                }
                            }
                            .testTag("add_subtask_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Alt Görev Ekle",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (subtasks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        subtasks.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(if (item.isDone) primaryColor else Color.Transparent)
                                        .border(
                                            width = 1.5.dp,
                                            color = if (item.isDone) primaryColor else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            subtasks = subtasks.map {
                                                if (it.id == item.id) it.copy(isDone = !it.isDone) else it
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.isDone) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        subtasks = subtasks.filterNot { it.id == item.id }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Alt görevi kaldır",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Category Selection
                Text(
                    text = "Kategori",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TaskCategory.entries.forEach { cat ->
                        val isSelected = selectedCategory.equals(cat.title, ignoreCase = true)
                        val pillBg by animateColorAsState(
                            targetValue = if (isSelected) primaryColor else MaterialTheme.colorScheme.surfaceVariant,
                            label = "cat_bg_${cat.name}"
                        )
                        val pillContentColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            label = "cat_fg_${cat.name}"
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(pillBg)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) primaryColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedCategory = cat.title }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = cat.icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else primaryColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = cat.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = pillContentColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Priority Selection
                Text(
                    text = "Öncelik Derecesi",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TaskPriority.entries.forEach { pri ->
                        val isSelected = selectedPriority.equals(pri.name, ignoreCase = true)
                        val activeColor = if (pri == TaskPriority.URGENT) Color(0xFFEF4444) else primaryColor
                        val pillBg by animateColorAsState(
                            targetValue = if (isSelected) activeColor else MaterialTheme.colorScheme.surfaceVariant,
                            label = "pri_bg_${pri.name}"
                        )
                        val pillText by animateColorAsState(
                            targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            label = "pri_fg_${pri.name}"
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(pillBg)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedPriority = pri.name }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pri.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = pillText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Recurrence Selector (Tekrarlayan Görevler)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tekrarlama",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RecurrenceRule.entries.forEach { rule ->
                        val isSelected = selectedRecurrence == rule.name
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) primaryColor
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) primaryColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedRecurrence = rule.name }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("recurrence_${rule.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = rule.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Date & Time Pickers Row
                Text(
                    text = "Tarih ve Saat",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val dateFormatted = remember(dueDateMillis) {
                        val sdf = SimpleDateFormat("dd MMM yyyy", Locale("tr"))
                        sdf.format(Date(dueDateMillis))
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 12.dp, vertical = 11.dp)
                            .testTag("pick_date_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = dateFormatted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (hasSpecificTime) primaryColor.copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                width = 1.dp,
                                color = if (hasSpecificTime) primaryColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                hasSpecificTime = true
                                showTimePicker = true
                            }
                            .padding(horizontal = 12.dp, vertical = 11.dp)
                            .testTag("pick_time_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasSpecificTime) String.format(Locale.getDefault(), "%02d:%02d", dueHour, dueMinute)
                            else "Saat Seç",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasSpecificTime) primaryColor else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reminder / Notification Toggle Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (hasReminder) primaryColor.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surface
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (hasReminder) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Bildirim & Hatırlatıcı",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Görevin zamanında bildirim al",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = hasReminder,
                        onCheckedChange = {
                            hasReminder = it
                            if (it && !hasSpecificTime) {
                                hasSpecificTime = true
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = primaryColor,
                            checkedBorderColor = primaryColor,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.testTag("reminder_switch")
                    )
                }

                if (hasReminder) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Hatırlatma Zamanı:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            0 to "Tam vaktinde",
                            5 to "5 dk önce",
                            15 to "15 dk önce",
                            30 to "30 dk önce",
                            60 to "1 saat önce"
                        ).forEach { (mins, label) ->
                            val isSelectedOffset = reminderMinutesBefore == mins
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelectedOffset) primaryColor
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelectedOffset) primaryColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { reminderMinutesBefore = mins }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelectedOffset) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelectedOffset) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions: Cancel & Save Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "İptal",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                titleError = true
                            } else {
                                // Also include pending typed subtask if user forgot to tap +
                                val pendingSub = newSubtaskTitle.trim()
                                val finalSubtasks = if (pendingSub.isNotEmpty()) {
                                    subtasks + SubtaskItem(title = pendingSub)
                                } else {
                                    subtasks
                                }
                                onSave(
                                    title,
                                    description,
                                    dueDateMillis,
                                    if (hasSpecificTime) dueHour else -1,
                                    if (hasSpecificTime) dueMinute else -1,
                                    selectedCategory,
                                    selectedPriority,
                                    hasReminder,
                                    reminderMinutesBefore,
                                    selectedRecurrence,
                                    SubtaskCodec.encode(finalSubtasks)
                                )
                            }
                        },
                        modifier = Modifier.testTag("save_task_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Kaydet",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            dueDateMillis = it
                        }
                        showDatePicker = false
                    }
                ) { Text("Seç", color = primaryColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("İptal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = primaryColor,
                    todayDateBorderColor = primaryColor,
                    todayContentColor = primaryColor
                )
            )
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dueHour = timePickerState.hour
                        dueMinute = timePickerState.minute
                        hasSpecificTime = true
                        showTimePicker = false
                    }
                ) { Text("Tamam", color = primaryColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("İptal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Görev Saati Seçin",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    TimePicker(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            selectorColor = primaryColor,
                            timeSelectorSelectedContainerColor = primaryColor.copy(alpha = 0.18f),
                            timeSelectorSelectedContentColor = primaryColor
                        )
                    )
                }
            }
        )
    }
}
