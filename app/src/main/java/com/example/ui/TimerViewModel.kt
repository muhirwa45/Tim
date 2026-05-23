package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundSynthesizer
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class TimerMode {
    FOCUS,
    SHORT_BREAK,
    LONG_BREAK
}

class TimerViewModel(
    application: Application,
    private val repository: TimRepository,
    private val preferencesManager: PreferencesManager
) : AndroidViewModel(application) {

    // Sound alert generator
    private val soundSynthesizer = SoundSynthesizer()

    // Screen navigation state
    private val _activeScreen = MutableStateFlow("timer")
    val activeScreen: StateFlow<String> = _activeScreen.asStateFlow()

    // Timer states
    private val _currentMode = MutableStateFlow(TimerMode.FOCUS)
    val currentMode: StateFlow<TimerMode> = _currentMode.asStateFlow()

    private val _timeLeftSeconds = MutableStateFlow(preferencesManager.focusDuration * 60)
    val timeLeftSeconds: StateFlow<Int> = _timeLeftSeconds.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    private var timerJob: Job? = null

    // Room tasks flow
    val tasks: StateFlow<List<Task>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Room focus sessions flow
    val sessions: StateFlow<List<FocusSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active bounds task
    private val _selectedTaskId = MutableStateFlow<Int?>(preferencesManager.selectedTaskId)
    val selectedTaskId: StateFlow<Int?> = _selectedTaskId.asStateFlow()

    val selectedTask: StateFlow<Task?> = combine(tasks, selectedTaskId) { taskList, id ->
        taskList.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Statistics calendar inspection state
    private val _selectedInspectionDate = MutableStateFlow(getTodayDateString())
    val selectedInspectionDate: StateFlow<String> = _selectedInspectionDate.asStateFlow()

    // Preferences configuration
    private val _focusDurationMin = MutableStateFlow(preferencesManager.focusDuration)
    val focusDurationMin: StateFlow<Int> = _focusDurationMin.asStateFlow()

    private val _shortBreakDurationMin = MutableStateFlow(preferencesManager.shortBreakDuration)
    val shortBreakDurationMin: StateFlow<Int> = _shortBreakDurationMin.asStateFlow()

    private val _longBreakDurationMin = MutableStateFlow(preferencesManager.longBreakDuration)
    val longBreakDurationMin: StateFlow<Int> = _longBreakDurationMin.asStateFlow()

    private val _soundEnabled = MutableStateFlow(preferencesManager.soundEnabled)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    init {
        // Expose correct time initial state
        updateTimeLeft()
    }

    fun navigateTo(screen: String) {
        _activeScreen.value = screen
    }

    private fun updateTimeLeft() {
        if (!_isTimerRunning.value) {
            val minutes = when (_currentMode.value) {
                TimerMode.FOCUS -> _focusDurationMin.value
                TimerMode.SHORT_BREAK -> _shortBreakDurationMin.value
                TimerMode.LONG_BREAK -> _longBreakDurationMin.value
            }
            _timeLeftSeconds.value = minutes * 60
        }
    }

    fun setTimerMode(mode: TimerMode) {
        pauseTimer()
        _currentMode.value = mode
        updateTimeLeft()
    }

    fun toggleTimer() {
        if (_isTimerRunning.value) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        _isTimerRunning.value = true
        timerJob = viewModelScope.launch {
            while (_timeLeftSeconds.value > 0) {
                delay(1000)
                _timeLeftSeconds.value--
            }
            onTimerComplete()
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        _isTimerRunning.value = false
    }

    fun resetTimer() {
        pauseTimer()
        updateTimeLeft()
    }

    fun skipTimer() {
        val nextMode = when (_currentMode.value) {
            TimerMode.FOCUS -> TimerMode.SHORT_BREAK
            TimerMode.SHORT_BREAK -> TimerMode.FOCUS
            TimerMode.LONG_BREAK -> TimerMode.FOCUS
        }
        setTimerMode(nextMode)
    }

    private suspend fun onTimerComplete() {
        _isTimerRunning.value = false
        val finishedMode = _currentMode.value
        
        if (finishedMode == TimerMode.FOCUS) {
            // Sound focus alert
            if (_soundEnabled.value) {
                soundSynthesizer.playFocusCompletion()
            }
            
            // Log Completed Session in Database
            val activeTaskId = _selectedTaskId.value
            val todayStr = getTodayDateString()
            repository.insertSession(
                FocusSession(
                    dateStr = todayStr,
                    taskId = activeTaskId
                )
            )

            // Update associated active task if valid
            if (activeTaskId != null) {
                repository.getTaskById(activeTaskId)?.let { task ->
                    val newCompleted = task.completedPomo + 1
                    val isDone = newCompleted >= task.targetPomo
                    repository.updateTask(
                        task.copy(
                            completedPomo = newCompleted,
                            isCompleted = isDone || task.isCompleted
                        )
                    )
                }
            }

            // Auto switch to short break period
            setTimerMode(TimerMode.SHORT_BREAK)

        } else {
            // Sound break completion alert
            if (_soundEnabled.value) {
                soundSynthesizer.playBreakCompletion()
            }
            // Auto swap to focused sessions
            setTimerMode(TimerMode.FOCUS)
        }
    }

    // Task Interactions
    fun addTask(title: String, targetPomo: Int) {
        viewModelScope.launch {
            val task = Task(title = title, targetPomo = targetPomo)
            val newId = repository.insertTask(task).toInt()
            
            // Auto bind selected task if none active
            if (_selectedTaskId.value == null) {
                selectTask(newId)
            }
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val nextCompleted = !task.isCompleted
            val completedCount = if (nextCompleted && task.completedPomo == 0) task.targetPomo else task.completedPomo
            repository.updateTask(
                task.copy(
                    isCompleted = nextCompleted,
                    completedPomo = completedCount
                )
            )
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
            if (_selectedTaskId.value == task.id) {
                _selectedTaskId.value = null
                preferencesManager.selectedTaskId = null
            }
        }
    }

    fun selectTask(taskId: Int) {
        _selectedTaskId.value = taskId
        preferencesManager.selectedTaskId = taskId
    }

    // Settings adjustments
    fun updateDurations(focus: Int, short: Int, long: Int) {
        preferencesManager.focusDuration = focus
        preferencesManager.shortBreakDuration = short
        preferencesManager.longBreakDuration = long
        
        _focusDurationMin.value = focus
        _shortBreakDurationMin.value = short
        _longBreakDurationMin.value = long

        updateTimeLeft()
    }

    fun toggleSound() {
        val nextVal = !_soundEnabled.value
        preferencesManager.soundEnabled = nextVal
        _soundEnabled.value = nextVal
    }

    fun testAlertSound(mode: TimerMode) {
        if (mode == TimerMode.FOCUS) {
            soundSynthesizer.playFocusCompletion()
        } else {
            soundSynthesizer.playBreakCompletion()
        }
    }

    fun selectInspectionDate(dateStr: String) {
        _selectedInspectionDate.value = dateStr
    }

    fun incrementFocusOnInspectedDate() {
        viewModelScope.launch {
            repository.insertSession(
                FocusSession(
                    dateStr = _selectedInspectionDate.value,
                    taskId = null
                )
            )
        }
    }

    fun clearHeatmapHistory() {
        viewModelScope.launch {
            repository.clearAllSessions()
        }
    }

    // Demo Data seeding helper
    fun seedDemoHistory() {
        viewModelScope.launch {
            repository.clearAllSessions()
            val list = mutableListOf<FocusSession>()
            val calendar = Calendar.getInstance()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val random = Random()
            
            // Seed back spans 180 days
            for (i in 0 until 180) {
                val checkCalendar = Calendar.getInstance().apply {
                    time = calendar.time
                    add(Calendar.DATE, -i)
                }
                
                // seed 55% likelihood of completion activity on any given day for beautiful heatmap UI representation
                if (random.nextDouble() > 0.45) {
                    val count = random.nextInt(6) + 1
                    val dateStr = sdf.format(checkCalendar.time)
                    for (j in 0 until count) {
                        list.add(
                            FocusSession(
                                dateStr = dateStr,
                                timestamp = checkCalendar.timeInMillis - (j * 30 * 60 * 1000)
                            )
                        )
                    }
                }
            }
            repository.insertSessions(list)
        }
    }

    // Helper statistics logic
    val heatmapData: StateFlow<Map<String, Int>> = sessions.map { sessionList ->
        sessionList.groupBy { it.dateStr }
            .mapValues { entry -> entry.value.size }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val totalSessionsCount: StateFlow<Int> = sessions.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val streakDays: StateFlow<Int> = sessions.map { sessionList ->
        val activeDates = sessionList.map { it.dateStr }.toSet()
        calculateStreak(activeDates)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private fun calculateStreak(dates: Set<String>): Int {
        if (dates.isEmpty()) return 0
        var streak = 0
        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        var dateStrToCheck = sdf.format(calendar.time)
        // A streak can start today or yesterday
        if (!dates.contains(dateStrToCheck)) {
            calendar.add(Calendar.DATE, -1)
            dateStrToCheck = sdf.format(calendar.time)
            if (!dates.contains(dateStrToCheck)) {
                return 0
            }
        }

        while (dates.contains(dateStrToCheck)) {
            streak++
            calendar.add(Calendar.DATE, -1)
            dateStrToCheck = sdf.format(calendar.time)
        }
        return streak
    }

    override fun onCleared() {
        super.onCleared()
        soundSynthesizer.release()
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    // Factory Class pattern
    class Factory(
        private val application: Application,
        private val repository: TimRepository,
        private val preferencesManager: PreferencesManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TimerViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return TimerViewModel(application, repository, preferencesManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
