package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.TimerMode
import com.example.ui.TimerViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Init App database dependencies
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = TimRepository(database.taskDao, database.focusSessionDao)
        val preferencesManager = PreferencesManager(applicationContext)

        // Init view model
        val viewModel: TimerViewModel = ViewModelProvider(
            this,
            TimerViewModel.Factory(application, repository, preferencesManager)
        )[TimerViewModel::class.java]

        setContent {
            MyApplicationTheme {
                MainLayout(viewModel)
            }
        }
    }
}

@Composable
fun MainLayout(viewModel: TimerViewModel) {
    val coroutineScope = rememberCoroutineScope()
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var toastJob by remember { mutableStateOf<Job?>(null) }

    // Helper lambda to show styled custom toasts
    val triggerToast = { msg: String ->
        toastJob?.cancel()
        toastMessage = msg
        toastJob = coroutineScope.launch {
            delay(2800)
            toastMessage = null
        }
    }

    val activeScreen by viewModel.activeScreen.collectAsStateWithLifecycle()
    val isTimerRunning by viewModel.isTimerRunning.collectAsStateWithLifecycle()
    val timerMode by viewModel.currentMode.collectAsStateWithLifecycle()
    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SlateDarkBg,
        topBar = {
            AppHeader(
                timerMode = timerMode,
                isTimerRunning = isTimerRunning,
                soundEnabled = soundEnabled,
                onToggleSound = {
                    viewModel.toggleSound()
                    triggerToast(if (soundEnabled) "Alert outputs muted" else "Alert outputs active")
                },
                onSeedData = {
                    viewModel.seedDemoHistory()
                    triggerToast("Demonstration heatmap loaded!")
                }
            )
        },
        bottomBar = {
            BottomNavBar(
                activeScreen = activeScreen,
                onNavigate = { screen ->
                    viewModel.navigateTo(screen)
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main views routing
            AnimatedContent(
                targetState = activeScreen,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ScreenTransitions"
            ) { screen ->
                when (screen) {
                    "timer" -> TimerScreen(viewModel, onNavigateToTasks = {
                        viewModel.navigateTo("tasks")
                    }, triggerToast = triggerToast)

                    "tasks" -> TasksScreen(viewModel, triggerToast = triggerToast)

                    "stats" -> StatsScreen(viewModel, triggerToast = triggerToast)

                    "settings" -> SettingsScreen(viewModel, triggerToast = triggerToast)
                }
            }

            // Custom sliding floating Toast overlay
            AnimatedVisibility(
                visible = toastMessage != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Surface(
                    color = BrandEmerald,
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 8.dp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Success",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = toastMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppHeader(
    timerMode: TimerMode,
    isTimerRunning: Boolean,
    soundEnabled: Boolean,
    onToggleSound: () -> Unit,
    onSeedData: () -> Unit
) {
    Surface(
        color = GrayDarkCard,
        border = BorderStroke(1.dp, GrayBorder),
        tonalElevation = 2.dp,
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Logo Branding block
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(BrandEmerald, Color(0xFF14B8A6))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Tim logo icon",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "Tim",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Text(
                        text = "ANDROID POMODORO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Header Control Tools
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Ping status dot
                Box(
                    modifier = Modifier.size(8.dp)
                ) {
                    val dotColor = when (timerMode) {
                        TimerMode.FOCUS -> BrandEmerald
                        TimerMode.SHORT_BREAK -> BreakBlue
                        TimerMode.LONG_BREAK -> BreakPurple
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }

                // Volume toggle button
                IconButton(
                    onClick = onToggleSound,
                    modifier = Modifier
                        .size(36.dp)
                        .background(GrayBorder, shape = RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = if (soundEnabled) Icons.Default.Notifications else Icons.Default.Clear,
                        contentDescription = "Toggle sound",
                        tint = TextWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Quick seed demo button
                Button(
                    onClick = onSeedData,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GrayBorder,
                        contentColor = TextWhite
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Load seed metrics",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Demo Data",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(
    activeScreen: String,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = GrayDarkCard,
        tonalElevation = 8.dp,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        val navItems = listOf(
            Triple("timer", "Timer", Icons.Default.Home),
            Triple("tasks", "Tasks", Icons.Default.List),
            Triple("stats", "Heatmap", Icons.Default.Check),
            Triple("settings", "Settings", Icons.Default.Settings)
        )

        navItems.forEach { (route, label, icon) ->
            val isSelected = activeScreen == route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(route) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(20.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BrandEmerald,
                    selectedTextColor = BrandEmerald,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor = BrandEmerald.copy(alpha = 0.12f)
                )
            )
        }
    }
}

@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    onNavigateToTasks: () -> Unit,
    triggerToast: (String) -> Unit
) {
    val currentMode by viewModel.currentMode.collectAsStateWithLifecycle()
    val timeLeftSeconds by viewModel.timeLeftSeconds.collectAsStateWithLifecycle()
    val isTimerRunning by viewModel.isTimerRunning.collectAsStateWithLifecycle()
    val selectedTask by viewModel.selectedTask.collectAsStateWithLifecycle()

    val totalModeDurationMin = when (currentMode) {
        TimerMode.FOCUS -> viewModel.focusDurationMin.collectAsStateWithLifecycle().value
        TimerMode.SHORT_BREAK -> viewModel.shortBreakDurationMin.collectAsStateWithLifecycle().value
        TimerMode.LONG_BREAK -> viewModel.longBreakDurationMin.collectAsStateWithLifecycle().value
    }

    val progress = if (totalModeDurationMin > 0) {
        (totalModeDurationMin * 60 - timeLeftSeconds).toFloat() / (totalModeDurationMin * 60)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Toggle capsule mode bar
        Surface(
            color = Color.Black.copy(alpha = 0.3f),
            border = BorderStroke(1.dp, GrayBorder),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(4.dp)
                    .width(300.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimerModeButton(
                    label = "Focus",
                    isActive = currentMode == TimerMode.FOCUS,
                    activeColor = BrandEmerald,
                    onClick = { viewModel.setTimerMode(TimerMode.FOCUS) }
                )
                TimerModeButton(
                    label = "Short Break",
                    isActive = currentMode == TimerMode.SHORT_BREAK,
                    activeColor = BreakBlue,
                    onClick = { viewModel.setTimerMode(TimerMode.SHORT_BREAK) }
                )
                TimerModeButton(
                    label = "Long Break",
                    isActive = currentMode == TimerMode.LONG_BREAK,
                    activeColor = BreakPurple,
                    onClick = { viewModel.setTimerMode(TimerMode.LONG_BREAK) }
                )
            }
        }

        // Circular countdown progress Canvas
        val formatTimeText = {
            val mins = timeLeftSeconds / 60
            val secs = timeLeftSeconds % 60
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }

        val ringColor = when (currentMode) {
            TimerMode.FOCUS -> BrandEmerald
            TimerMode.SHORT_BREAK -> BreakBlue
            TimerMode.LONG_BREAK -> BreakPurple
        }

        val displayTaskName = selectedTask?.title ?: "No active task selected"

        Box(
            modifier = Modifier
                .size(240.dp)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokePx = 10.dp.toPx()
                val radius = (size.minDimension - strokePx) / 2
                
                // Track back line
                drawCircle(
                    color = GrayBorder,
                    radius = radius,
                    style = Stroke(width = strokePx)
                )
                
                // Front active sweep line
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = progress * 360f,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatTimeText(),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, GrayBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = displayTaskName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Timer action triggers row
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            // Reset button
            IconButton(
                onClick = {
                    viewModel.resetTimer()
                    triggerToast("Timer reset")
                },
                modifier = Modifier
                    .size(50.dp)
                    .background(Color.Black.copy(alpha = 0.2f), shape = CircleShape)
                    .border(1.dp, GrayBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset countdown",
                    tint = TextWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Big Core play/pause key
            val triggerIcon = if (isTimerRunning) Icons.Default.Home else Icons.Default.PlayArrow
            IconButton(
                onClick = {
                    viewModel.toggleTimer()
                },
                modifier = Modifier
                    .size(80.dp)
                    .background(ringColor, shape = CircleShape)
            ) {
                Icon(
                    imageVector = if (isTimerRunning) Icons.Default.Check else Icons.Default.PlayArrow,
                    contentDescription = "Start pauses trigger",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Skip button
            IconButton(
                onClick = {
                    viewModel.skipTimer()
                    triggerToast("Period skipped")
                },
                modifier = Modifier
                    .size(50.dp)
                    .background(Color.Black.copy(alpha = 0.2f), shape = CircleShape)
                    .border(1.dp, GrayBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Skip mode",
                    tint = TextWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Active task descriptor card
        Surface(
            color = GrayDarkCard,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GrayBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TARGET ACTION TASK",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = "Change Task",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandEmerald,
                        modifier = Modifier
                            .clickable { onNavigateToTasks() }
                            .padding(4.dp)
                    )
                }

                if (selectedTask != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .border(1.dp, GrayBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(BrandEmerald, CircleShape)
                            )
                            Column {
                                Text(
                                    text = selectedTask!!.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Completed ${selectedTask!!.completedPomo} of ${selectedTask!!.targetPomo} sprints",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Surface(
                            color = BrandEmerald.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, BrandEmerald.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Target: ${selectedTask!!.targetPomo}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .border(1.dp, GrayBorder, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active focus goal set. Create or select a task inside Tasks tab!",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimerModeButton(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(96.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) activeColor else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (isActive) Color.White else TextMuted
        )
    }
}

@Composable
fun TasksScreen(
    viewModel: TimerViewModel,
    triggerToast: (String) -> Unit
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val selectedTaskId by viewModel.selectedTaskId.collectAsStateWithLifecycle()

    var taskTitle by remember { mutableStateOf("") }
    var targetPomo by remember { mutableStateOf(2) }

    val completedCount = tasks.count { it.isCompleted }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Manage Tasks",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Assign your goals before starting periods",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Surface(
                    color = GrayBorder,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "$completedCount/${tasks.size} Done",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandEmerald,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Add task card
        item {
            Surface(
                color = GrayDarkCard,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GrayBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Task Title",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        OutlinedTextField(
                            value = taskTitle,
                            onValueChange = { taskTitle = it },
                            placeholder = { Text("e.g. Write user database schemas...", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandEmerald,
                                unfocusedBorderColor = GrayBorder
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Estimate Target:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                                    .border(1.dp, GrayBorder, RoundedCornerShape(8.dp))
                                    .padding(2.dp)
                            ) {
                                IconButton(
                                    onClick = { if (targetPomo > 1) targetPomo-- },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh, // placeholder minus element standard representation
                                        contentDescription = "Decrement estimation",
                                        tint = TextWhite,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                                Text(
                                    text = targetPomo.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandEmerald,
                                    modifier = Modifier.width(18.dp),
                                    textAlign = TextAlign.Center
                                )
                                IconButton(
                                    onClick = { if (targetPomo < 12) targetPomo++ },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Increment estimation",
                                        tint = TextWhite,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }

                            Text(
                                text = "POMOS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = TextMuted
                            )
                        }

                        Button(
                            onClick = {
                                if (taskTitle.trim().isNotEmpty()) {
                                    viewModel.addTask(taskTitle.trim(), targetPomo)
                                    taskTitle = ""
                                    targetPomo = 2
                                    triggerToast("New goal added successfully!")
                                } else {
                                    triggerToast("Enter task title first!")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandEmerald,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Goal Icon", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Task", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Tasks block items
        if (tasks.isEmpty()) {
            item {
                Surface(
                    color = Color.Black.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, GrayBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Goals Empty placeholder",
                            tint = GrayBorder,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Goal book list is empty",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextMuted
                        )
                        Text(
                            text = "Define some sprints to drive your heatmap!",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(tasks) { task ->
                val isActive = task.id == selectedTaskId
                TaskCard(
                    task = task,
                    isActive = isActive,
                    onToggleCompletion = {
                        viewModel.toggleTaskCompletion(task)
                    },
                    onSelect = {
                        viewModel.selectTask(task.id)
                        triggerToast("Task focus target updated")
                    },
                    onDelete = {
                        viewModel.deleteTask(task)
                        triggerToast("Goal removed")
                    }
                )
            }
        }
    }
}

@Composable
fun TaskCard(
    task: Task,
    isActive: Boolean,
    onToggleCompletion: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = if (isActive) BrandEmerald.copy(alpha = 0.08f) else GrayDarkCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (isActive) BrandEmerald.copy(alpha = 0.5f) else GrayBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect() }
            ) {
                // Radio complete tick checkbox
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (task.isCompleted) BrandEmerald else Color.Transparent)
                        .border(2.dp, if (task.isCompleted) BrandEmerald else Color.Gray, CircleShape)
                        .clickable(onClick = onToggleCompletion),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed check indicator",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Title info details column
                Column(modifier = Modifier.padding(end = 4.dp)) {
                    Text(
                        text = task.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (task.isCompleted) Color.Gray else Color.White,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Completed ${task.completedPomo} of ${task.targetPomo} segments",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            // Side controls column
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isActive) {
                    Surface(
                        color = BrandEmerald,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove task card",
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatsScreen(
    viewModel: TimerViewModel,
    triggerToast: (String) -> Unit
) {
    val totalSessions by viewModel.totalSessionsCount.collectAsStateWithLifecycle()
    val streakDays by viewModel.streakDays.collectAsStateWithLifecycle()
    val heatmapData by viewModel.heatmapData.collectAsStateWithLifecycle()
    val inspectedDate by viewModel.selectedInspectionDate.collectAsStateWithLifecycle()

    val currentInspectedSessionCount = heatmapData[inspectedDate] ?: 0

    // Compute calendar weeks dataset once
    val weeksList = remember {
        val list = mutableListOf<List<String>>()
        val todayCalendar = Calendar.getInstance()
        
        val startPoint = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -364)
            val dayOfWeek = get(Calendar.DAY_OF_WEEK)
            val offset = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
            add(Calendar.DAY_OF_YEAR, -offset)
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        for (w in 0 until 53) {
            val days = mutableListOf<String>()
            for (d in 0 until 7) {
                days.add(sdf.format(startPoint.time))
                startPoint.add(Calendar.DAY_OF_YEAR, 1)
            }
            list.add(days)
        }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Stats header block
        Column {
            Text(
                text = "Productivity Analytics",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Tracks active Pomodoros across the calendar year",
                fontSize = 11.sp,
                color = TextMuted
            )
        }

        // KPI metrics boxes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatsMetricBox(
                title = "TOTAL FOCUS",
                value = totalSessions.toString(),
                unit = "sessions",
                color = BrandEmerald,
                modifier = Modifier.weight(1f)
            )

            StatsMetricBox(
                title = "ACTIVE STREAK",
                value = "$streakDays Days",
                unit = "consecutive",
                color = Color(0xFF2DD4BF),
                modifier = Modifier.weight(1f)
            )

            StatsMetricBox(
                title = "DAILY GOAL",
                value = "4",
                unit = "target sprints",
                color = Color(0xFF818CF8),
                modifier = Modifier.weight(1f)
            )
        }

        // Contributions Heatmap frame wrapper
        Surface(
            color = GrayDarkCard,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GrayBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Contribution Board",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Surface(
                        color = GrayBorder,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "2026",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = TextWhite,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Heatmap core display drawer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Monday Wed Fri indicators
                    Column(
                        modifier = Modifier
                            .height(98.dp)
                            .padding(end = 6.dp, top = 2.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Mon", color = TextMuted, fontSize = 9.sp)
                        Text("Wed", color = TextMuted, fontSize = 9.sp)
                        Text("Fri", color = TextMuted, fontSize = 9.sp)
                    }

                    // Horizontal Lazy list
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(weeksList) { daysList ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                daysList.forEach { dStr ->
                                    val count = heatmapData[dStr] ?: 0
                                    val cellColor = when {
                                        count == 0 -> HeatmapLvl0
                                        count in 1..2 -> HeatmapLvl1
                                        count in 3..4 -> HeatmapLvl2
                                        count in 5..6 -> HeatmapLvl3
                                        else -> HeatmapLvl4
                                    }

                                    val isInspected = dStr == inspectedDate
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(cellColor)
                                            .border(
                                                1.dp,
                                                if (isInspected) Color.White else Color.Transparent,
                                                RoundedCornerShape(2.dp)
                                            )
                                            .clickable {
                                                viewModel.selectInspectionDate(dStr)
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                // Heatmap key legend footer row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .border(
                            BorderStroke(0.1.dp, Color.Transparent) // placeholder spacing divider
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reset Heatmap",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF87171),
                        modifier = Modifier
                            .clickable {
                                viewModel.clearHeatmapHistory()
                                triggerToast("Heatmap board wiped")
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("Less", fontSize = 9.sp, color = TextMuted)
                        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(HeatmapLvl0))
                        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(HeatmapLvl1))
                        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(HeatmapLvl2))
                        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(HeatmapLvl3))
                        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(HeatmapLvl4))
                        Text("More", fontSize = 9.sp, color = TextMuted)
                    }
                }
            }
        }

        // Inspections block
        val parseAndFormatInspectionDate = { rawDate: String ->
            try {
                val inputSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = inputSdf.parse(rawDate)
                val outSdf = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
                if (date != null) outSdf.format(date) else rawDate
            } catch (e: Exception) {
                rawDate
            }
        }

        Surface(
            color = Color.Black.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, GrayBorder),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SELECTED INSPECTION DATE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = parseAndFormatInspectionDate(inspectedDate),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = BrandEmerald.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, BrandEmerald.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$currentInspectedSessionCount focuslogged",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandEmerald,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            viewModel.incrementFocusOnInspectedDate()
                            triggerToast("Logged manual focus sprint!")
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(GrayBorder, shape = RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Log manual focus",
                            tint = TextWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatsMetricBox(
    title: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = GrayDarkCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, GrayBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = unit,
                fontSize = 9.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: TimerViewModel,
    triggerToast: (String) -> Unit
) {
    var rawFocusMin by remember { mutableStateOf(viewModel.focusDurationMin.value.toString()) }
    var rawShortMin by remember { mutableStateOf(viewModel.shortBreakDurationMin.value.toString()) }
    var rawLongMin by remember { mutableStateOf(viewModel.longBreakDurationMin.value.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Headers details
        Column {
            Text(
                text = "Configurations",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Tailor the timers to match your workspace habits",
                fontSize = 11.sp,
                color = TextMuted
            )
        }

        // Adjustable timers inputs card
        Surface(
            color = GrayDarkCard,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GrayBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "TIMER DURATIONS (MINUTES)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Focus Period", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = rawFocusMin,
                            onValueChange = { rawFocusMin = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandEmerald,
                                unfocusedBorderColor = GrayBorder
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Short Break", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = rawShortMin,
                            onValueChange = { rawShortMin = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandEmerald,
                                unfocusedBorderColor = GrayBorder
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Long Break", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = rawLongMin,
                            onValueChange = { rawLongMin = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandEmerald,
                                unfocusedBorderColor = GrayBorder
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Button(
                    onClick = {
                        val focus = rawFocusMin.toIntOrNull() ?: 0
                        val short = rawShortMin.toIntOrNull() ?: 0
                        val long = rawLongMin.toIntOrNull() ?: 0

                        if (focus > 0 && short > 0 && long > 0) {
                            viewModel.updateDurations(focus, short, long)
                            triggerToast("Durations updated successfully!")
                        } else {
                            triggerToast("Provide positive integers!")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandEmerald,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply New Durations", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Test Desk Audio elements
        Surface(
            color = GrayDarkCard,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GrayBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "AUDIO TEST DESK",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "Trigger-play standard alert alerts on this device.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.testAlertSound(TimerMode.FOCUS) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black.copy(alpha = 0.2f),
                            contentColor = BrandEmerald
                        ),
                        border = BorderStroke(1.dp, GrayBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Notifications, contentDescription = "Ring", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Focus Ring", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.testAlertSound(TimerMode.SHORT_BREAK) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black.copy(alpha = 0.2f),
                            contentColor = BreakPurple
                        ),
                        border = BorderStroke(1.dp, GrayBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Notifications, contentDescription = "Ring", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Break Ring", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // About card
        Surface(
            color = GrayDarkCard,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GrayBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ABOUT TIM",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "Designed as a dedicated, companion Android-style ecosystem, \"Tim\" combines active work-task management alongside developer stats. The green contributions map serves as tangible feedback for your daily focus sprints.",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    lineHeight = 16.sp
                )

                Text(
                    text = "Engineered with zero external server dependencies. Built using Jetpack Compose, Room and native AudioTrack Synthesis.",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
