package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ActiveTab
import com.example.ui.components.TopNavigationBar
import com.example.ui.dialogs.AddEditTaskDialog
import com.example.ui.dialogs.CloudSyncDialog
import com.example.ui.dialogs.ViewSwitcherBottomSheet
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.DayFlowScreen
import com.example.ui.screens.OverviewScreen
import com.example.ui.screens.TodoScreen
import com.example.ui.theme.PlanFlowTheme
import com.example.ui.viewmodel.PlanFlowViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: PlanFlowViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            // Request Notification permission on Android 13+
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* Handled */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasPermission) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            PlanFlowTheme(themeMode = themeMode) {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: PlanFlowViewModel) {
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val filteredTasks by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val selectedCalendarDateMillis by viewModel.selectedCalendarDateMillis.collectAsStateWithLifecycle()
    val calendarMonth by viewModel.calendarMonth.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val selectedPriority by viewModel.selectedPriorityFilter.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
    val accountKey by viewModel.accountKey.collectAsStateWithLifecycle()
    val isSupabaseConfigured by viewModel.isSupabaseConfigured.collectAsStateWithLifecycle()

    val showAddEditDialog by viewModel.showAddEditDialog.collectAsStateWithLifecycle()
    val editingTask by viewModel.editingTask.collectAsStateWithLifecycle()
    val showCloudSyncDialog by viewModel.showCloudSyncDialog.collectAsStateWithLifecycle()
    val showMenuSheet by viewModel.showMenuSheet.collectAsStateWithLifecycle()
    val todayProgress by viewModel.todayProgressState.collectAsStateWithLifecycle()

    val showGitHubUpdateDialog by viewModel.showGitHubUpdateDialog.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val githubOwner by viewModel.githubOwner.collectAsStateWithLifecycle()
    val githubRepo by viewModel.githubRepo.collectAsStateWithLifecycle()
    val githubToken by viewModel.githubToken.collectAsStateWithLifecycle()
    val lastUpdateCheckTime by viewModel.lastUpdateCheckTime.collectAsStateWithLifecycle()
    val updateStatusMessage by viewModel.updateStatusMessage.collectAsStateWithLifecycle()
    val latestReleaseInfo by viewModel.latestReleaseInfo.collectAsStateWithLifecycle()
    val hasUpdateAvailable = latestReleaseInfo?.isNewerThanInstalled == true

    val pendingCount = allTasks.count { !it.isCompleted }

    // Back handling: If on secondary tab, return to FLOW (main agenda)
    if (activeTab != ActiveTab.FLOW) {
        BackHandler {
            viewModel.setActiveTab(ActiveTab.FLOW)
        }
    }

    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopNavigationBar(
                currentTab = activeTab,
                pendingTaskCount = pendingCount,
                dailyProgress = todayProgress,
                themeMode = themeMode,
                isSyncing = isSyncing,
                hasUpdateAvailable = hasUpdateAvailable,
                statusBarTopPadding = statusBarTopPadding,
                onTabSelected = { viewModel.setActiveTab(it) },
                onThemeToggle = { viewModel.toggleTheme() },
                onCloudSyncClick = { viewModel.openCloudSyncDialog() },
                onOpenMenu = { viewModel.openMenuSheet() }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab_content_transition"
            ) { currentTab ->
                when (currentTab) {
                    ActiveTab.FLOW -> {
                        DayFlowScreen(
                            tasks = allTasks,
                            onToggleTask = { viewModel.toggleTaskCompletion(it) },
                            onEditTask = { viewModel.openEditTask(it) },
                            onDeleteTask = { viewModel.deleteTask(it) },
                            onAddTaskForDate = { dateMillis -> viewModel.openAddTask(dateMillis) },
                            onOpenMenu = { viewModel.openMenuSheet() }
                        )
                    }

                    ActiveTab.CALENDAR -> {
                        CalendarScreen(
                            tasks = allTasks,
                            selectedDateMillis = selectedCalendarDateMillis,
                            currentMonthCalendar = calendarMonth,
                            onDateSelected = { viewModel.setSelectedCalendarDate(it) },
                            onPrevMonth = { viewModel.prevMonth() },
                            onNextMonth = { viewModel.nextMonth() },
                            onJumpToToday = { viewModel.jumpToToday() },
                            onAddTaskForDate = { dateMillis -> viewModel.openAddTask(dateMillis) },
                            onToggleTask = { viewModel.toggleTaskCompletion(it) },
                            onEditTask = { viewModel.openEditTask(it) },
                            onDeleteTask = { viewModel.deleteTask(it) }
                        )
                    }

                    ActiveTab.TODO -> {
                        TodoScreen(
                            tasks = filteredTasks,
                            searchQuery = searchQuery,
                            selectedCategory = selectedCategory,
                            selectedPriority = selectedPriority,
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            onCategorySelect = { viewModel.setCategoryFilter(it) },
                            onPrioritySelect = { viewModel.setPriorityFilter(it) },
                            onAddTask = { viewModel.openAddTask() },
                            onToggleTask = { viewModel.toggleTaskCompletion(it) },
                            onEditTask = { viewModel.openEditTask(it) },
                            onDeleteTask = { viewModel.deleteTask(it) }
                        )
                    }

                    ActiveTab.OVERVIEW -> {
                        OverviewScreen(
                            tasks = allTasks,
                            lastSyncTime = lastSyncTime,
                            onNavigateTab = { viewModel.setActiveTab(it) },
                            onAddTask = { viewModel.openAddTask() },
                            onToggleTask = { viewModel.toggleTaskCompletion(it) },
                            onEditTask = { viewModel.openEditTask(it) },
                            onDeleteTask = { viewModel.deleteTask(it) },
                            onOpenSyncDialog = { viewModel.openCloudSyncDialog() }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Task Dialog
    if (showAddEditDialog) {
        AddEditTaskDialog(
            initialTask = editingTask,
            defaultDateMillis = selectedCalendarDateMillis,
            onDismiss = { viewModel.dismissAddEditDialog() },
            onSave = { title, desc, dueDate, hour, min, cat, pri, hasReminder, reminderMinutes ->
                viewModel.saveTask(
                    id = editingTask?.id ?: 0L,
                    title = title,
                    description = desc,
                    dueDateMillis = dueDate,
                    dueHour = hour,
                    dueMinute = min,
                    category = cat,
                    priority = pri,
                    hasReminder = hasReminder,
                    reminderMinutesBefore = reminderMinutes
                )
            }
        )
    }

    // Cloud Sync Dialog
    if (showCloudSyncDialog) {
        CloudSyncDialog(
            lastSyncTime = lastSyncTime,
            accountKey = accountKey,
            isSupabaseConfigured = isSupabaseConfigured,
            isSyncing = isSyncing,
            syncMessage = syncMessage,
            onDismiss = {
                viewModel.clearSyncMessage()
                viewModel.dismissCloudSyncDialog()
            },
            onUpdateAccountKey = { viewModel.updateAccountKey(it) },
            onSyncToCloud = { viewModel.syncToCloud() },
            onRestoreFromCloud = { viewModel.restoreFromCloud() },
            onGetExportJson = { viewModel.getExportJson() },
            onImportJson = { json, onComplete ->
                viewModel.importBackupJson(json, onComplete)
            }
        )
    }

    // GitHub Atomic Update & Release Manager Dialog
    if (showGitHubUpdateDialog) {
        com.example.ui.dialogs.GitHubUpdateDialog(
            installedVersion = viewModel.installedVersionName,
            githubOwner = githubOwner,
            githubRepo = githubRepo,
            githubToken = githubToken,
            lastCheckTime = lastUpdateCheckTime,
            isCheckingUpdate = isCheckingUpdate,
            updateStatusMessage = updateStatusMessage,
            latestRelease = latestReleaseInfo,
            onDismiss = { viewModel.dismissGitHubUpdateDialog() },
            onSaveRepoConfig = { owner, repo, token -> viewModel.saveGitHubRepoConfig(owner, repo, token) },
            onCheckForUpdates = { viewModel.checkForGitHubUpdates(silentOnNoUpdate = false) },
            onExecuteAtomicCheckpointAndDownload = { targetUrl, onReady ->
                viewModel.executeAtomicCheckpointAndDownload(targetUrl, onReady)
            },
            onRunAtomicDbCheckpointOnly = { viewModel.runAtomicDbCheckpointOnly() }
        )
    }

    // View Switcher Menu Sheet (triggered by the top ≡ button)
    if (showMenuSheet) {
        ViewSwitcherBottomSheet(
            currentTab = activeTab,
            themeMode = themeMode,
            hasUpdateAvailable = hasUpdateAvailable,
            latestReleaseTag = latestReleaseInfo?.tagName,
            installedVersion = viewModel.installedVersionName,
            onSelectTab = { viewModel.setActiveTab(it) },
            onToggleTheme = { viewModel.toggleTheme() },
            onOpenCloudSync = { viewModel.openCloudSyncDialog() },
            onOpenGitHubUpdate = { viewModel.openGitHubUpdateDialog() },
            onDismiss = { viewModel.dismissMenuSheet() }
        )
    }
}
