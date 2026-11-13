package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PlanFlowApplication
import com.example.data.local.TaskEntity
import com.example.data.repository.TaskRepository
import com.example.model.ActiveTab
import com.example.model.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class PlanFlowViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TaskRepository = (application as PlanFlowApplication).repository

    private val _activeTab = MutableStateFlow(ActiveTab.FLOW)
    val activeTab: StateFlow<ActiveTab> = _activeTab.asStateFlow()

    private val _showMenuSheet = MutableStateFlow(false)
    val showMenuSheet: StateFlow<Boolean> = _showMenuSheet.asStateFlow()

    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _selectedCalendarDateMillis = MutableStateFlow(
        TaskRepository.getDayStartMillis(System.currentTimeMillis())
    )
    val selectedCalendarDateMillis: StateFlow<Long> = _selectedCalendarDateMillis.asStateFlow()

    private val _calendarMonth = MutableStateFlow(Calendar.getInstance())
    val calendarMonth: StateFlow<Calendar> = _calendarMonth.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    private val _selectedPriorityFilter = MutableStateFlow<String?>(null)
    val selectedPriorityFilter: StateFlow<String?> = _selectedPriorityFilter.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(repository.cloudSyncManager.getLastSyncTimeString())
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    private val _accountKey = MutableStateFlow(repository.cloudSyncManager.getAccountKey())
    val accountKey: StateFlow<String> = _accountKey.asStateFlow()

    private val _isSupabaseConfigured = MutableStateFlow(repository.cloudSyncManager.isSupabaseConfigured())
    val isSupabaseConfigured: StateFlow<Boolean> = _isSupabaseConfigured.asStateFlow()

    private val _editingTask = MutableStateFlow<TaskEntity?>(null)
    val editingTask: StateFlow<TaskEntity?> = _editingTask.asStateFlow()

    private val _showAddEditDialog = MutableStateFlow(false)
    val showAddEditDialog: StateFlow<Boolean> = _showAddEditDialog.asStateFlow()

    private val _showCloudSyncDialog = MutableStateFlow(false)
    val showCloudSyncDialog: StateFlow<Boolean> = _showCloudSyncDialog.asStateFlow()

    // GitHub Atomic Release Update States
    private val _showGitHubUpdateDialog = MutableStateFlow(false)
    val showGitHubUpdateDialog: StateFlow<Boolean> = _showGitHubUpdateDialog.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _githubOwner = MutableStateFlow(repository.gitHubReleaseManager.getGitHubOwner())
    val githubOwner: StateFlow<String> = _githubOwner.asStateFlow()

    private val _githubRepo = MutableStateFlow(repository.gitHubReleaseManager.getGitHubRepo())
    val githubRepo: StateFlow<String> = _githubRepo.asStateFlow()

    private val _githubToken = MutableStateFlow(repository.gitHubReleaseManager.getGitHubToken())
    val githubToken: StateFlow<String> = _githubToken.asStateFlow()

    private val _lastUpdateCheckTime = MutableStateFlow(repository.gitHubReleaseManager.getLastCheckTimeString())
    val lastUpdateCheckTime: StateFlow<String> = _lastUpdateCheckTime.asStateFlow()

    private val _updateStatusMessage = MutableStateFlow<String?>(null)
    val updateStatusMessage: StateFlow<String?> = _updateStatusMessage.asStateFlow()

    private val _latestReleaseInfo = MutableStateFlow<com.example.data.repository.GitHubReleaseInfo?>(null)
    val latestReleaseInfo: StateFlow<com.example.data.repository.GitHubReleaseInfo?> = _latestReleaseInfo.asStateFlow()

    val installedVersionName: String = repository.gitHubReleaseManager.getInstalledVersionName()

    val allTasks: StateFlow<List<TaskEntity>> = repository.allTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val todayStartMillis = TaskRepository.getDayStartMillis(System.currentTimeMillis())
    private val todayEndMillis = TaskRepository.getDayEndMillis(System.currentTimeMillis())

    val todayProgressState: StateFlow<com.example.model.DailyProgressState> =
        repository.getTasksForDateRange(todayStartMillis, todayEndMillis)
            .combine(_activeTab) { todayTasks, _ ->
                TaskRepository.calculateDailyProgress(todayTasks)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = com.example.model.DailyProgressState()
            )

    val filteredTasks: StateFlow<List<TaskEntity>> = combine(
        allTasks,
        _searchQuery,
        _selectedCategoryFilter,
        _selectedPriorityFilter
    ) { tasks, query, cat, pri ->
        tasks.filter { task ->
            val matchesQuery = query.isBlank() ||
                    task.title.contains(query, ignoreCase = true) ||
                    task.description.contains(query, ignoreCase = true)

            val matchesCat = cat == null || task.category.equals(cat, ignoreCase = true)
            val matchesPri = pri == null || task.priority.equals(pri, ignoreCase = true)

            matchesQuery && matchesCat && matchesPri
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                // 1. Önce yerel veritabanı boşsa Supabase'den çek veya örnek veri yükle
                repository.seedInitialDataIfEmpty()

                // 2. İlk açılışta otomatik olarak Supabase'den son halini çek ve updatedAtMillis ile atomik eşitle
                if (repository.cloudSyncManager.isSupabaseConfigured()) {
                    val result = repository.syncOnStartupOrDemand()
                    _syncMessage.value = result.message
                }
                _lastSyncTime.value = repository.cloudSyncManager.getLastSyncTimeString()
            } catch (_: Exception) {
            } finally {
                _isSyncing.value = false
            }
        }

        // 3. İlk açılışta GitHub repo tanımlıysa otomatik olarak yeni APK sürümü var mı kontrol et
        viewModelScope.launch {
            if (repository.gitHubReleaseManager.isRepositoryConfigured()) {
                checkForGitHubUpdates(silentOnNoUpdate = true)
            }
        }
    }

    fun setActiveTab(tab: ActiveTab) {
        _activeTab.value = tab
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
    }

    fun toggleTheme() {
        _themeMode.value = when (_themeMode.value) {
            AppThemeMode.LIGHT -> AppThemeMode.DARK
            AppThemeMode.DARK -> AppThemeMode.SYSTEM
            AppThemeMode.SYSTEM -> AppThemeMode.LIGHT
        }
    }

    fun setSelectedCalendarDate(dateMillis: Long) {
        _selectedCalendarDateMillis.value = TaskRepository.getDayStartMillis(dateMillis)
    }

    fun nextMonth() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = _calendarMonth.value.timeInMillis
            add(Calendar.MONTH, 1)
        }
        _calendarMonth.value = cal
    }

    fun prevMonth() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = _calendarMonth.value.timeInMillis
            add(Calendar.MONTH, -1)
        }
        _calendarMonth.value = cal
    }

    fun jumpToToday() {
        val todayMillis = TaskRepository.getDayStartMillis(System.currentTimeMillis())
        _selectedCalendarDateMillis.value = todayMillis
        _calendarMonth.value = Calendar.getInstance()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = if (_selectedCategoryFilter.value == category) null else category
    }

    fun setPriorityFilter(priority: String?) {
        _selectedPriorityFilter.value = if (_selectedPriorityFilter.value == priority) null else priority
    }

    fun openAddTask(presetDateMillis: Long? = null) {
        _editingTask.value = null
        if (presetDateMillis != null) {
            _selectedCalendarDateMillis.value = TaskRepository.getDayStartMillis(presetDateMillis)
        }
        _showAddEditDialog.value = true
    }

    fun openMenuSheet() {
        _showMenuSheet.value = true
    }

    fun dismissMenuSheet() {
        _showMenuSheet.value = false
    }

    fun openEditTask(task: TaskEntity) {
        _editingTask.value = task
        _showAddEditDialog.value = true
    }

    fun dismissAddEditDialog() {
        _showAddEditDialog.value = false
        _editingTask.value = null
    }

    fun saveTask(
        id: Long = 0,
        title: String,
        description: String,
        dueDateMillis: Long,
        dueHour: Int,
        dueMinute: Int,
        category: String,
        priority: String,
        hasReminder: Boolean,
        reminderMinutesBefore: Int
    ) {
        viewModelScope.launch {
            if (id == 0L) {
                repository.insertTask(
                    TaskEntity(
                        title = title.trim(),
                        description = description.trim(),
                        dueDateMillis = TaskRepository.getDayStartMillis(dueDateMillis),
                        dueHour = dueHour,
                        dueMinute = dueMinute,
                        category = category,
                        priority = priority,
                        hasReminder = hasReminder,
                        reminderMinutesBefore = reminderMinutesBefore
                    )
                )
            } else {
                val existing = _editingTask.value ?: return@launch
                repository.updateTask(
                    existing.copy(
                        title = title.trim(),
                        description = description.trim(),
                        dueDateMillis = TaskRepository.getDayStartMillis(dueDateMillis),
                        dueHour = dueHour,
                        dueMinute = dueMinute,
                        category = category,
                        priority = priority,
                        hasReminder = hasReminder,
                        reminderMinutesBefore = reminderMinutesBefore
                    )
                )
            }
            _lastSyncTime.value = repository.cloudSyncManager.getLastSyncTimeString()
            dismissAddEditDialog()
        }
    }

    fun toggleTaskCompletion(taskId: Long) {
        viewModelScope.launch {
            repository.toggleTaskCompletion(taskId)
            _lastSyncTime.value = repository.cloudSyncManager.getLastSyncTimeString()
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
            _lastSyncTime.value = repository.cloudSyncManager.getLastSyncTimeString()
        }
    }

    fun openCloudSyncDialog() {
        _isSupabaseConfigured.value = repository.cloudSyncManager.isSupabaseConfigured()
        _accountKey.value = repository.cloudSyncManager.getAccountKey()
        _lastSyncTime.value = repository.cloudSyncManager.getLastSyncTimeString()
        _showCloudSyncDialog.value = true
    }

    fun dismissCloudSyncDialog() {
        _showCloudSyncDialog.value = false
    }

    fun updateAccountKey(newKey: String) {
        repository.cloudSyncManager.setAccountKey(newKey)
        _accountKey.value = repository.cloudSyncManager.getAccountKey()
        restoreFromCloud()
    }

    fun syncToCloud() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Supabase'e anlık yedekleniyor..."
            try {
                val result = repository.syncToCloud()
                _syncMessage.value = result.message
                _lastSyncTime.value = repository.cloudSyncManager.getLastSyncTimeString()
            } catch (e: Exception) {
                _syncMessage.value = "Senkronizasyon hatası: ${e.localizedMessage}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun restoreFromCloud() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Supabase ile eşitleniyor (updatedAtMillis)..."
            try {
                val result = repository.restoreFromCloud()
                _syncMessage.value = result.message
                _lastSyncTime.value = repository.cloudSyncManager.getLastSyncTimeString()
            } catch (e: Exception) {
                _syncMessage.value = "Eşitleme hatası: ${e.localizedMessage}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    suspend fun getExportJson(): String {
        return repository.exportJson()
    }

    fun importBackupJson(json: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.importJson(json)
            if (success) {
                _syncMessage.value = "Yedek başarıyla içe aktarıldı ve Supabase'e gönderildi."
                _lastSyncTime.value = repository.cloudSyncManager.getLastSyncTimeString()
            } else {
                _syncMessage.value = "Geçersiz yedek verisi."
            }
            onComplete(success)
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun openGitHubUpdateDialog() {
        _githubOwner.value = repository.gitHubReleaseManager.getGitHubOwner()
        _githubRepo.value = repository.gitHubReleaseManager.getGitHubRepo()
        _githubToken.value = repository.gitHubReleaseManager.getGitHubToken()
        _lastUpdateCheckTime.value = repository.gitHubReleaseManager.getLastCheckTimeString()
        _showGitHubUpdateDialog.value = true
    }

    fun dismissGitHubUpdateDialog() {
        _showGitHubUpdateDialog.value = false
    }

    fun saveGitHubRepoConfig(owner: String, repo: String, token: String? = null) {
        repository.gitHubReleaseManager.setGitHubRepository(owner, repo, token)
        _githubOwner.value = repository.gitHubReleaseManager.getGitHubOwner()
        _githubRepo.value = repository.gitHubReleaseManager.getGitHubRepo()
        _githubToken.value = repository.gitHubReleaseManager.getGitHubToken()
        checkForGitHubUpdates(silentOnNoUpdate = false)
    }

    fun checkForGitHubUpdates(silentOnNoUpdate: Boolean = false) {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            if (!silentOnNoUpdate) {
                _updateStatusMessage.value = "GitHub Releases kontrol ediliyor..."
            }
            try {
                val result = repository.gitHubReleaseManager.checkLatestRelease()
                _latestReleaseInfo.value = result.releaseInfo
                _lastUpdateCheckTime.value = repository.gitHubReleaseManager.getLastCheckTimeString()
                if (!silentOnNoUpdate || result.releaseInfo?.isNewerThanInstalled == true) {
                    _updateStatusMessage.value = result.message
                }
            } catch (e: Exception) {
                if (!silentOnNoUpdate) {
                    _updateStatusMessage.value = "Denetim hatası: ${e.localizedMessage}"
                }
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    fun executeAtomicCheckpointAndDownload(
        targetUrl: String,
        onReadyToOpenUrl: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            try {
                val checkpointResult = repository.prepareAtomicPreUpdateCheckpoint()
                _updateStatusMessage.value = checkpointResult.message
                _lastSyncTime.value = repository.cloudSyncManager.getLastSyncTimeString()
                if (checkpointResult.success) {
                    onReadyToOpenUrl(targetUrl)
                }
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    fun runAtomicDbCheckpointOnly() {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            try {
                val checkpointResult = repository.prepareAtomicPreUpdateCheckpoint()
                _updateStatusMessage.value = checkpointResult.message
                _lastSyncTime.value = repository.cloudSyncManager.getLastSyncTimeString()
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }
}
