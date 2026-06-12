package com.example

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.CareerRoadmap
import com.example.data.ChatMessage
import com.example.data.QuizHistory
import com.example.data.StudentStats
import com.example.ui.LocalQuizQuestion
import com.example.ui.TutorViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Native TextToSpeech for Voice AI companion mode
        tts = TextToSpeech(this, this)

        setContent {
            MyApplicationTheme {
                val vm: TutorViewModel = viewModel()
                val context = LocalContext.current

                // Listen to Voice AI message flow and speak replies
                LaunchedEffect(Unit) {
                    vm.lastSpeechTrigger.collectLatest { speechText ->
                        if (isTtsInitialized && tts != null) {
                            tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "SparkyVoiceMessage")
                        } else {
                            Toast.makeText(context, "Voice is initializing...", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                TutorAppScreen(vm)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            isTtsInitialized = true
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun TutorAppScreen(vm: TutorViewModel) {
    val stats by vm.statsState.collectAsState()
    val activeTab by vm.currentTab.collectAsState()
    val isLoggedIn by vm.isLoggedIn.collectAsState()
    val studentName by vm.studentName.collectAsState()
    val studentLang by vm.studentLanguage.collectAsState()

    var showPlannerOverlay by remember { mutableStateOf(false) }
    var showDoubtSolverOverlay by remember { mutableStateOf(false) }
    var showGkTriviaOverlay by remember { mutableStateOf(false) }
    var showAskAnythingOverlay by remember { mutableStateOf(false) }

    // Palette parameters matching our Material 3 frontend guideline
    val primaryColor = Color(0xFF6366F1) // Royal Indigo / Violet
    val darkSlate = Color(0xFF1E293B) // Dark background elements
    val secondaryColor = Color(0xFFF59E0B) // Golden Amber for XP / Levels / Stars
    val coralAccent = Color(0xFFEC4899) // Coral Pink

    val gradientBg = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFEEF2F6),
            Color(0xFFE2E8F0)
        )
    )

    if (!isLoggedIn) {
        OnboardingScreen(vm = vm, primaryColor = primaryColor, secondaryColor = secondaryColor)
    } else {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("app_scaffold"),
            topBar = {
                TutorAppBar(
                    stats = stats,
                    xpCap = 100,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    studentName = studentName,
                    studentLang = studentLang,
                    onLogout = { vm.logoutStudent() }
                )
            },
            bottomBar = {
                TutorBottomNavigation(activeTab = activeTab, onTabSelected = { vm.currentTab.value = it })
            },
            containerColor = Color(0xFFF8FAFC)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(gradientBg)
            ) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                    },
                    label = "TabTransition"
                ) { targetTab ->
                    when (targetTab) {
                        "dashboard" -> DashboardTab(
                            vm = vm,
                            stats = stats,
                            primaryColor = primaryColor,
                            secondaryColor = secondaryColor,
                            coralAccent = coralAccent,
                            onOpenPlanner = { showPlannerOverlay = true },
                            onOpenDoubtSolver = { showDoubtSolverOverlay = true },
                            onOpenGkTrivia = { showGkTriviaOverlay = true },
                            onOpenAskAnything = { showAskAnythingOverlay = true }
                        )
                        "tutor" -> StudyChatTab(vm = vm, primaryColor, darkSlate)
                        "quiz" -> QuizTab(vm = vm, primaryColor, secondaryColor)
                        "career" -> CareerTab(vm = vm, primaryColor, secondaryColor)
                        "voice" -> VoiceChatTab(vm = vm, primaryColor, secondaryColor)
                        "general" -> GeneralChatTab(vm = vm, primaryColor, darkSlate)
                    }
                }

                // Custom dynamic overlays for our Dashboard Hub rooms
                val activeStory by vm.activeStoryContent.collectAsState()
                if (activeStory != null) {
                    LessonStoryOverlay(
                        vm = vm,
                        story = activeStory!!,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor
                    )
                }

                val examQuestions by vm.examQuestionsContent.collectAsState()
                if (examQuestions != null) {
                    ExamQuestionsOverlay(
                        vm = vm,
                        content = examQuestions!!,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor
                    )
                }

                val newsVal by vm.newsContent.collectAsState()
                if (newsVal != null) {
                    NewsHubOverlay(
                        vm = vm,
                        content = newsVal!!,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor
                    )
                }

                if (showPlannerOverlay) {
                    StudyPlannerOverlay(
                        vm = vm,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        onClose = { showPlannerOverlay = false }
                    )
                }

                if (showDoubtSolverOverlay) {
                    DoubtSolverOverlay(
                        vm = vm,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        onClose = { showDoubtSolverOverlay = false }
                    )
                }

                if (showGkTriviaOverlay) {
                    GkTriviaOverlay(
                        vm = vm,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        onClose = { showGkTriviaOverlay = false }
                    )
                }

                if (showAskAnythingOverlay) {
                    AskAnythingOverlay(
                        vm = vm,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        onClose = { showAskAnythingOverlay = false }
                    )
                }
            }
        }
    }
}

// ---------------------- COMPOSABLES: HEADER & STATS BAR ----------------------

@Composable
fun TutorAppBar(
    stats: StudentStats?,
    xpCap: Int,
    primaryColor: Color,
    secondaryColor: Color,
    studentName: String = "Student",
    studentLang: String = "English",
    onLogout: () -> Unit = {}
) {
    val level = stats?.level ?: 1
    val xp = stats?.xp ?: 0
    val totalLevelXp = xp % xpCap
    val xpProgressFraction = totalLevelXp.toFloat() / xpCap.toFloat()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .testTag("app_bar"),
        tonalElevation = 6.dp,
        color = Color.White,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(primaryColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = "School launcher icon",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        val greeting = when (studentLang.lowercase()) {
                            "tamil" -> "வணக்கம், $studentName!"
                            "spanish" -> "¡Hola, $studentName!"
                            "french" -> "Bonjour, $studentName!"
                            else -> "Hello, $studentName!"
                        }
                        Text(
                            text = if (studentName != "Student" && studentName.isNotEmpty()) greeting else "Sparky AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color(0xFF1E293B),
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "Created by Kalaiselvan",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryColor
                        )
                    }
                }

                // Level Badge & XP indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFEEF2F6))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Achievements star label",
                            tint = secondaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Level $level",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = Color(0xFF1E293B)
                        )
                    }
                    if (studentName != "Student" && studentName.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onLogout,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Log out connection",
                                tint = Color.Red.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // XP Progression Line
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${totalLevelXp}/${xpCap} XP",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    modifier = Modifier.width(72.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { xpProgressFraction },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = secondaryColor,
                    trackColor = Color(0xFFE2E8F0)
                )
            }
        }
    }
}

@Composable
fun TutorBottomNavigation(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    NavigationBar(
        tonalElevation = 8.dp,
        containerColor = Color.White,
        modifier = Modifier.testTag("app_bottom_nav")
    ) {
        NavigationBarItem(
            selected = activeTab == "dashboard",
            onClick = { onTabSelected("dashboard") },
            icon = { Icon(Icons.Default.Dashboard, "Dashboard nav item") },
            label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF6366F1),
                unselectedIconColor = Color(0xFF64748B)
            )
        )
        NavigationBarItem(
            selected = activeTab == "tutor",
            onClick = { onTabSelected("tutor") },
            icon = { Icon(Icons.Default.MenuBook, "Tutor chat nav item") },
            label = { Text("Tutor", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF6366F1),
                unselectedIconColor = Color(0xFF64748B)
            )
        )
        NavigationBarItem(
            selected = activeTab == "quiz",
            onClick = { onTabSelected("quiz") },
            icon = { Icon(Icons.Default.FactCheck, "Quiz generator nav item") },
            label = { Text("Quizzes", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF6366F1),
                unselectedIconColor = Color(0xFF64748B)
            )
        )
        NavigationBarItem(
            selected = activeTab == "career",
            onClick = { onTabSelected("career") },
            icon = { Icon(Icons.Default.Work, "Career advice nav item") },
            label = { Text("Careers", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF6366F1),
                unselectedIconColor = Color(0xFF64748B)
            )
        )
        NavigationBarItem(
            selected = activeTab == "voice",
            onClick = { onTabSelected("voice") },
            icon = { Icon(Icons.Default.Mic, "Oral voice tts nav item") },
            label = { Text("Voice AI", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF6366F1),
                unselectedIconColor = Color(0xFF64748B)
            )
        )
        NavigationBarItem(
            selected = activeTab == "general",
            onClick = { onTabSelected("general") },
            icon = { Icon(Icons.Default.Assistant, "General helper nav item") },
            label = { Text("Assistant", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF6366F1),
                unselectedIconColor = Color(0xFF64748B)
            )
        )
    }
}

// ---------------------- TAB 1: STUDENT DASHBOARD HERO ----------------------

@Composable
fun DashboardTab(
    vm: TutorViewModel,
    stats: StudentStats?,
    primaryColor: Color,
    secondaryColor: Color,
    coralAccent: Color,
    onOpenPlanner: () -> Unit,
    onOpenDoubtSolver: () -> Unit,
    onOpenGkTrivia: () -> Unit,
    onOpenAskAnything: () -> Unit
) {
    val quizHistory by vm.quizHistoryState.collectAsState()
    val savedRoadmaps by vm.roadmapsState.collectAsState()
    val studentName by vm.studentName.collectAsState()
    val studentClass by vm.studentClass.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("dashboard_tab"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sparky Welcome Banner Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("welcome_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = primaryColor),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Sparky sparkles icon",
                                tint = secondaryColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (studentName.isNotEmpty()) "Welcome, $studentName!" else "Welcome Back, Student!",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            )
                            if (studentClass.isNotEmpty()) {
                                Text(
                                    text = "🎯 Goal: $studentClass",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "I'm Sparky! Check out our learning utilities: play a quiz, map out your future career roadmap, or talk to me about studies to level up your XP stats!",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Student Stats Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quizzes Done
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stats_card_quizzes"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FactCheck,
                            contentDescription = "Quizzes Finished Icon",
                            tint = coralAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${stats?.quizzesCompleted ?: 0}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "Quizzes Played",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // Questions Asked
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stats_card_questions"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "Questions Asked Icon",
                            tint = primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${stats?.questionsAsked ?: 0}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "Tasks Solved",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // Roadmaps Drawn
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stats_card_roadmaps"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Roadmaps count icon",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${stats?.roadmapsGenerated ?: 0}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "Roadmaps",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }

        // Quick launch direct actions
        item {
            Text(
                text = "💡 Interactive Learning Tools",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFF1E293B)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPlanner() }
                    .testTag("btn_planner_tile"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFEEF2F6), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CalendarToday, "Study planner icon", tint = Color(0xFF4F46E5))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("A.I. Smart Study Planner", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Build custom day-by-day study calendars and tick off completed slots!", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                    Icon(Icons.Default.ArrowForward, "Open", tint = Color(0xFF94A3B8))
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDoubtSolver() }
                    .testTag("btn_doubt_tile"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFEFF6FF), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FlipCameraIos, "Doubt solver photo scan icon", tint = Color(0xFF2563EB))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Doubt Solver (Scan & Type)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Solve assignments instantly by typing questions or uploading a homework photo!", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                    Icon(Icons.Default.ArrowForward, "Open", tint = Color(0xFF94A3B8))
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenGkTrivia() }
                    .testTag("btn_gk_tile"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFFEF3C7), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Trophy, "General Knowledge game icon", tint = Color(0xFFD97706))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Daily GK Trivia Arena", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Play dynamic, auto-refreshing school quiz categories and master charts!", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                    Icon(Icons.Default.ArrowForward, "Open", tint = Color(0xFF94A3B8))
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAskAnything() }
                    .testTag("btn_ask_anything_tile"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFFCE7F3), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.QuestionAnswer, "Ask general assistant icon", tint = Color(0xFFEC4899))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Instant Ask Anything Box", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Double-tap or ask Sparky quick random study details!", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                    Icon(Icons.Default.ArrowForward, "Open", tint = Color(0xFF94A3B8))
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.currentTab.value = "quiz" },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFFEF3C7), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Gamepad, "Play mini-quiz icon", tint = Color(0xFFD97706))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Instant Quiz Game", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Create multi-question school challenges and win XP stats!", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                    Icon(Icons.Default.ArrowForward, "Open", tint = Color(0xFF94A3B8))
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.currentTab.value = "career" },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFECFDF5), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.TrendingUp, "Career advisory tool icon", tint = Color(0xFF059669))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Career Guidance Center", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Consult roadmaps, daily exercises, and exams details.", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                    Icon(Icons.Default.ArrowForward, "Open", tint = Color(0xFF94A3B8))
                }
            }
        }

        // --- CUSTOM CARD 3: Lessons into Stories ---
        item {
            var storyQuery by remember { mutableStateOf("") }
            val isStoryGenerating by vm.isStoryGenerating.collectAsState()

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFFFFFAF0), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoStories, "story mode icon", tint = Color(0xFFDD6B20))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Lessons into Stories Storyteller", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Transform any textbook lesson topic into a fun audio story!", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = storyQuery,
                        onValueChange = { storyQuery = it },
                        modifier = Modifier.fillMaxWidth().testTag("story_lesson_input"),
                        placeholder = { Text("e.g. Life of Galaxies, Photosynthesis Process") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (storyQuery.trim().isNotEmpty()) {
                                vm.generateLessonStory(storyQuery)
                                storyQuery = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_build_story"),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = RoundedCornerShape(10.dp),
                        enabled = storyQuery.trim().isNotEmpty() && !isStoryGenerating
                    ) {
                        if (isStoryGenerating) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Transform Lesson to Story!", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- CUSTOM CARD 4: Exam Important Questions Prep ---
        item {
            var examQuery by remember { mutableStateOf("") }
            val isExamGenerating by vm.isExamQuestionsGenerating.collectAsState()

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFFEFF6FF), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Edit, "exam prep icon", tint = Color(0xFF2563EB))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Exam Board Prep Helper", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Generate critical syllabus questions and board highlights!", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = examQuery,
                        onValueChange = { examQuery = it },
                        modifier = Modifier.fillMaxWidth().testTag("exam_subject_input"),
                        placeholder = { Text("e.g. Newton's Laws, Gravitational Physics") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (examQuery.trim().isNotEmpty()) {
                                vm.generateExamQuestions(examQuery)
                                examQuery = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_build_exam"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        shape = RoundedCornerShape(10.dp),
                        enabled = examQuery.trim().isNotEmpty() && !isExamGenerating
                    ) {
                        if (isExamGenerating) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Extract Important Exam Questions!", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- CUSTOM CARD 5: News, Scholarships & Government Portal Updates ---
        item {
            val isNewsLoading by vm.isNewsLoading.collectAsState()

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFFFEF2F2), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Campaign, "news updates flash logo", tint = Color(0xFFEF4444))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("EduNews & Scholarship Hub", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Todays daily school news alerts, scholarships trackers & TNPSC Government exams details!", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3 visual clickable tag pills
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { vm.fetchEduNews("school") },
                                modifier = Modifier.weight(1f).testTag("btn_school_news"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Text("📰 School Today News", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                            }
                            Button(
                                onClick = { vm.fetchEduNews("scholarship") },
                                modifier = Modifier.weight(1f).testTag("btn_scholarships"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Text("🎓 Scholarships", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                            }
                        }
                        Button(
                            onClick = { vm.fetchEduNews("tnpsc") },
                            modifier = Modifier.fillMaxWidth().testTag("btn_tnpsc_exams"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEFF6FF)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text("📝 TNPSC Exams & Govt Updates", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                        }
                    }

                    if (isNewsLoading) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(color = primaryColor, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        // Quiz history header
        if (quizHistory.isNotEmpty()) {
            item {
                Text(
                    text = "🏆 Past Quiz Records",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF1E293B)
                )
            }

            items(quizHistory) { record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = record.topic,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "Earned +${record.xpEarned} XP",
                                fontSize = 11.sp,
                                color = secondaryColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${record.score}/5 Correct",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (record.score >= 4) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                        }
                    }
                }
            }
        }

        // Saved career roadmaps
        if (savedRoadmaps.isNotEmpty()) {
            item {
                Text(
                    text = "📂 Your Saved Career Files",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF1E293B)
                )
            }

            items(savedRoadmaps) { roadmap ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkOutline,
                            contentDescription = "Career bag",
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = roadmap.careerTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "Click to reopen",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        IconButton(
                            onClick = {
                                vm.loadHistoricalRoadmap(roadmap)
                                vm.currentTab.value = "career"
                            }
                        ) {
                            Icon(Icons.Default.ArrowForward, "Reopen", tint = primaryColor)
                        }
                        IconButton(
                            onClick = { vm.deleteHistoricalRoadmap(roadmap) }
                        ) {
                            Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------- TAB 2: PORTABLE SCHOOL TUTOR CHAT (MASTER AI TUTOR) ----------------------

@Composable
fun StudyChatTab(
    vm: TutorViewModel,
    primaryColor: Color,
    darkSlate: Color
) {
    val messages by vm.tutorMessages.collectAsState()
    val isModelLoading by vm.isTutorLoading.collectAsState()
    val textVal by vm.tutorInput.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("study_chat_tab")
    ) {
        // Conversation log head explanation banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
            border = BorderStroke(1.dp, Color(0xFFBFDBFE))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info panel",
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Studying a topic or got homework questions? Sparky speaks your study language and gives simple step-by-step school facts!",
                    fontSize = 11.sp,
                    color = Color(0xFF1D4ED8),
                    lineHeight = 15.sp
                )
            }
        }

        // Messages list
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "Blank homework dashboard",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No homework challenges loaded, ask away!",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { message ->
                        ChatBubbleItem(message = message, primaryColor, darkSlate)
                    }
                    if (isModelLoading) {
                        item {
                            ModelPulseLoader()
                        }
                    }
                }
            }
        }

        // Control buttons
        ChatInputBar(
            text = textVal,
            onTextChanged = { vm.tutorInput.value = it },
            onSend = { vm.sendTutorMessage() },
            onClear = { vm.clearTutorChat() },
            placeholder = "Ask about Math, Science, History..."
        )
    }
}

// ---------------------- TAB 5: VOICE TUTOR MODE (WITH TEXT TO SPEECH OUT LOUD) ----------------------

@Composable
fun VoiceChatTab(
    vm: TutorViewModel,
    primaryColor: Color,
    secondaryColor: Color
) {
    val messages by vm.voiceMessages.collectAsState()
    val isModelLoading by vm.isVoiceLoading.collectAsState()
    val textVal by vm.voiceInput.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("voice_chat_tab")
    ) {
        // Soundwave / speaker graphic card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = primaryColor),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "TTS listening icon",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Interactive Voice AI Mode",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Answers will play aloud automatically using TTS!",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pulsing speaker animations
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "Waves")
                    val h1 by infiniteTransition.animateFloat(
                        initialValue = 10f,
                        targetValue = 40f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(400, delayMillis = 100, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ), label = "h1"
                    )
                    val h2 by infiniteTransition.animateFloat(
                        initialValue = 15f,
                        targetValue = 60f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500, delayMillis = 0, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ), label = "h2"
                    )
                    val h3 by infiniteTransition.animateFloat(
                        initialValue = 8f,
                        targetValue = 35f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(350, delayMillis = 50, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ), label = "h3"
                    )

                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.size(6.dp, h1.dp).background(secondaryColor, RoundedCornerShape(3.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.size(6.dp, h2.dp).background(Color.White, RoundedCornerShape(3.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.size(6.dp, h3.dp).background(secondaryColor, RoundedCornerShape(3.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }

        // Response dialogue log
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Speaker indicator icon",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Say Hi to Sparky to hear the tutoring talk!",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { message ->
                        ChatBubbleItem(message = message, primaryColor, Color(0xFF334155))
                    }
                    if (isModelLoading) {
                        item {
                            ModelPulseLoader()
                        }
                    }
                }
            }
        }

        // Dialogue bar
        ChatInputBar(
            text = textVal,
            onTextChanged = { vm.voiceInput.value = it },
            onSend = { vm.sendVoiceMessage() },
            onClear = { vm.clearVoiceChat() },
            placeholder = "Hi Sparky! Tell me a fun puzzle!"
        )
    }
}

// ---------------------- TAB 6: GENERAL STUDENT COMPANION CHAT ----------------------

@Composable
fun GeneralChatTab(
    vm: TutorViewModel,
    primaryColor: Color,
    darkSlate: Color
) {
    val messages by vm.generalMessages.collectAsState()
    val isModelLoading by vm.isGeneralLoading.collectAsState()
    val textVal by vm.generalInput.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("general_chat_tab")
    ) {
        // Little info tip
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF2F8)),
            border = BorderStroke(1.dp, Color(0xFFFBCFE8))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = "General dialog tip icon",
                    tint = Color(0xFFDB2777),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Struggling with student motivations, career choosing doubts, general rules or basic knowledge questions? I'm your student counselor companion!",
                    fontSize = 11.sp,
                    color = Color(0xFFBE185D),
                    lineHeight = 15.sp
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = "Blank board icon",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Say anything: Study habits, routine methods, fun questions...",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { message ->
                        ChatBubbleItem(message = message, primaryColor, darkSlate)
                    }
                    if (isModelLoading) {
                        item {
                            ModelPulseLoader()
                        }
                    }
                }
            }
        }

        ChatInputBar(
            text = textVal,
            onTextChanged = { vm.generalInput.value = it },
            onSend = { vm.sendGeneralMessage() },
            onClear = { vm.clearGeneralChat() },
            placeholder = "Ask general doubts, study routines, life advices..."
        )
    }
}

// ---------------------- COMPOSABLE CHAT BUBBLES ----------------------

@Composable
fun ChatBubbleItem(
    message: ChatMessage,
    primaryColor: Color,
    darkSlate: Color
) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(primaryColor, CircleShape)
                    .align(Alignment.Bottom),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = "Sparky logo",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) primaryColor else Color.White,
            shadowElevation = 1.dp,
            border = if (isUser) null else BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                fontSize = 13.sp,
                color = if (isUser) Color.White else darkSlate,
                lineHeight = 18.sp
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFFE2E8F0), CircleShape)
                    .align(Alignment.Bottom),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User profile icon",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
    placeholder: String
) {
    Surface(
        tonalElevation = 8.dp,
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_input_container")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("chat_input"),
                    placeholder = { Text(placeholder, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    )
                )

                Button(
                    onClick = onSend,
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("chat_send_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send text prompt to sparky")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            TextButton(
                onClick = onClear,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.DeleteSweep, "Sweep dialog logs", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear History", fontSize = 11.sp, color = Color(0xFF64748B))
            }
        }
    }
}

@Composable
fun ModelPulseLoader() {
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ), label = "scaling"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(0xFF6366F1), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, "Sparky is typing", tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(scale)
                        .background(Color(0xFF6366F1), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sparky is calculating step-by-step...", fontSize = 11.sp, color = Color(0xFF64748B))
            }
        }
    }
}

// ---------------------- TAB 3: MCQ ROADMAP INTERACTIVE QUIZZES ----------------------

@Composable
fun QuizTab(
    vm: TutorViewModel,
    primaryColor: Color,
    secondaryColor: Color
) {
    val isGenerating by vm.isQuizGenerating.collectAsState()
    val activeQuiz by vm.activeQuiz.collectAsState()
    val currentQuestionIdx by vm.currentQuestionIndex.collectAsState()
    val selectedAns by vm.selectedAnswer.collectAsState()
    val isAnsChecked by vm.isAnswerChecked.collectAsState()
    val score by vm.quizScore.collectAsState()
    val isFinished by vm.isQuizFinished.collectAsState()
    val gamifyMsg by vm.gamificationMessage.collectAsState()
    val isGamifyLoading by vm.isGamificationLoading.collectAsState()
    val topicInput by vm.quizTopicInput.collectAsState()

    val recommendedTopics = listOf(
        "🦕 Dinosaurs & Fossils",
        "🌌 Solar Galaxies",
        "🧪 Chemical Elements",
        "💡 Inventions history",
        "🏔️ Earth Volcanoes",
        "💻 Coding Basics"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("quiz_tab")
    ) {
        if (isGenerating) {
            // Loading Overlay Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(primaryColor.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(56.dp),
                        color = primaryColor,
                        strokeWidth = 5.dp
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Sparky is building your quiz...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Preparing 5 high-fidelity school level MCQ questions with answer keys & student explanation blocks! Potential standard reward: +120 XP",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        } else if (isFinished) {
            // final statistics score sheet and AI generated celebration text
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color(0xFFFEF3C7), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Trophy award",
                        tint = secondaryColor,
                        modifier = Modifier.size(56.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Quiz Finished!",
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    color = Color(0xFF1E293B)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "SCORE CARD",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
                Text(
                    text = "$score / 5 Correct Answered",
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    color = primaryColor
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Rewards pill
                val xpEarnedSum = score * 20 + 20
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                    border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, "Earned", tint = Color(0xFF059669))
                        Text(
                            text = "$xpEarnedSum XP Added to Level Profile!",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669),
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Gamification feedback card (Prompt 6)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "🎉 Sparky's Encouragement:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = primaryColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (isGamifyLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retrieving XP speech...", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        } else {
                            Text(
                                text = gamifyMsg ?: "Hooray! Direct hit! You earned standard quiz completion rewards!",
                                fontSize = 13.sp,
                                color = Color(0xFF334155),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { vm.restartQuiz() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Try Another Quiz Topic", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else if (activeQuiz == null) {
            // Quiz Topic Selection Input
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FactCheck,
                                contentDescription = "Active target icon",
                                tint = primaryColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Create a School Quiz",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = Color(0xFF1E293B)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enter any homework subject or fun category. Sparky will draw 5 school challenges for your active testing!",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        OutlinedTextField(
                            value = topicInput,
                            onValueChange = { vm.quizTopicInput.value = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quiz_topic_input"),
                            placeholder = { Text("e.g. World War II, Photosynthesis, Fractions") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { vm.generateQuiz() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_generate_quiz"),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Generate MCQ Quiz", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "⭐ Suggested Study Topics",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Flex list of topics
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recommendedTopics.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { topic ->
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            vm.quizTopicInput.value = topic.substring(2)
                                            vm.generateQuiz()
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Text(
                                        text = topic,
                                        modifier = Modifier.padding(12.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF334155),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Interactive 5 card quiz UI
            val quiz = activeQuiz!!
            val qList = quiz.questions
            val currentQ = qList.getOrNull(currentQuestionIdx)

            if (currentQ != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header progress indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Topic: ${quiz.topic}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = primaryColor
                        )
                        Text(
                            text = "Question ${currentQuestionIdx + 1} of ${qList.size}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress indicators row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (i in qList.indices) {
                            val activeIndex = i == currentQuestionIdx
                            val passedIndex = i < currentQuestionIdx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (activeIndex) primaryColor
                                        else if (passedIndex) Color(0xFF10B981)
                                        else Color(0xFFCBD5E1)
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Question Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = currentQ.question,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF1E293B),
                                lineHeight = 22.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // MCQ Choices list
                    val options = listOf(
                        "A" to currentQ.A,
                        "B" to currentQ.B,
                        "C" to currentQ.C,
                        "D" to currentQ.D
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        options.forEach { (letter, valText) ->
                            val isSelected = selectedAns == letter
                            val customCardColor = if (isAnsChecked) {
                                if (letter == currentQ.answer) Color(0xFFD1FAE5) // light emerald correct
                                else if (isSelected) Color(0xFFFEE2E2) // light red error selection
                                else Color.White
                            } else {
                                if (isSelected) Color(0xFFEEF2F6) else Color.White
                            }

                            val customBorderColor = if (isAnsChecked) {
                                if (letter == currentQ.answer) Color(0xFF10B981)
                                else if (isSelected) Color(0xFFEF4444)
                                else Color(0xFFE2E8F0)
                            } else {
                                if (isSelected) primaryColor else Color(0xFFE2E8F0)
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(!isAnsChecked) { vm.selectQuizAnswer(letter) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = customCardColor),
                                border = BorderStroke(2.dp, customBorderColor)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(
                                                if (isSelected) primaryColor else Color(0xFFF1F5F9),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = letter,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isSelected) Color.White else Color(0xFF64748B)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = valText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Explanations Blocks
                    if (isAnsChecked) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                val isCorrect = selectedAns == currentQ.answer
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                        contentDescription = "Status",
                                        tint = if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isCorrect) "Excellent! Correct." else "That's incorrect. Right answer is ${currentQ.answer}.",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isCorrect) Color(0xFF065F46) else Color(0xFF991B1B)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = currentQ.explanation,
                                    fontSize = 12.sp,
                                    color = Color(0xFF475569),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action logic button
                    if (!isAnsChecked) {
                        Button(
                            onClick = { vm.checkQuizAnswer() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            enabled = selectedAns != null,
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Submit Answer", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    } else {
                        Button(
                            onClick = { vm.nextQuizQuestion() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (currentQuestionIdx == qList.size - 1) "Finish Quiz!" else "Next Question ->",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------- TAB 4: COMPLETE CAREER ROADMAPS COUNSELING ----------------------

@Composable
fun CareerTab(
    vm: TutorViewModel,
    primaryColor: Color,
    secondaryColor: Color
) {
    val isGenerating by vm.isRoadmapGenerating.collectAsState()
    val rawTitle by vm.activeRoadmapTitle.collectAsState()
    val activeContent by vm.activeRoadmapContent.collectAsState()
    val careerNameVal by vm.careerInput.collectAsState()

    val popularSuggestions = listOf(
        "🚀 Space Scientist",
        "🩺 Pediatric Doctor",
        "🎨 Game Designer",
        "👩‍🍳 Pastry Chef",
        "🏛️ Museum Historian",
        "✈️ Aviation Pilot"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("career_tab")
    ) {
        if (isGenerating) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp), color = primaryColor)
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Sparky's Career counselor is drawing your map...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Compiling necessary exams, secondary skills, and daily routines tailored for students (+30 XP potential reward)",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )
            }
        } else if (activeContent != null) {
            // View active roadmap content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Stars, "stars", tint = secondaryColor)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = rawTitle.uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = Color(0xFF1E293B)
                        )
                    }

                    IconButton(onClick = { vm.closeActiveRoadmap() }) {
                        Icon(Icons.Default.Close, "Close active handbook")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = activeContent!!,
                            fontSize = 13.sp,
                            color = Color(0xFF1E293B),
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else {
            // Option chooser page
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, "career icon", tint = primaryColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Career Map Builder",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Draft complete study steps, necessary skillsets, required test exams, and motivation logs to pursue any desired professional goal!",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 15.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        OutlinedTextField(
                            value = careerNameVal,
                            onValueChange = { vm.careerInput.value = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("career_topic_input"),
                            placeholder = { Text("e.g. Robot Engineer, Marine Biologist") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { vm.generateCareerRoadmap() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_generate_roadmap"),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Draw Study Roadmap", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "💡 Explore Professional Pathways",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    popularSuggestions.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { value ->
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            vm.careerInput.value = value.substring(2)
                                            vm.generateCareerRoadmap()
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Text(
                                        text = value,
                                        modifier = Modifier.padding(12.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF334155),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================= CUSTOM ADDITIONS: SIGN-UP & TOOL OVERLAYS =======================

@Composable
fun OnboardingScreen(
    vm: TutorViewModel,
    primaryColor: Color,
    secondaryColor: Color
) {
    var name by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf("Class 10") }
    var selectedLang by remember { mutableStateOf("English") }
    var isClassExpanded by remember { mutableStateOf(false) }
    var isLangExpanded by remember { mutableStateOf(false) }

    val classesList = listOf(
        "Class 1", "Class 2", "Class 3", "Class 4", "Class 5",
        "Class 6", "Class 7", "Class 8", "Class 9", "Class 10",
        "Class 11", "Class 12", "TNPSC Aspirant", "NEET General", "College Student", "General Learner"
    )

    val languagesList = listOf("English", "Tamil", "Spanish", "French", "Hindi")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(primaryColor.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "App Logo banner",
                        tint = primaryColor,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "Sparky AI Tutor",
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    color = Color(0xFF1E293B)
                )

                Text(
                    text = "An intelligent student companion & school study helper.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Name input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your Name / Student ID") },
                    placeholder = { Text("Enter your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_name"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    )
                )

                // Goal Selector dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedClass,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Class / Competitive Target") },
                        trailingIcon = {
                            IconButton(onClick = { isClassExpanded = !isClassExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, "dropdown class selector button")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { isClassExpanded = !isClassExpanded },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )
                    DropdownMenu(
                        expanded = isClassExpanded,
                        onDismissRequest = { isClassExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        classesList.forEach { cName ->
                            DropdownMenuItem(
                                text = { Text(cName) },
                                onClick = {
                                    selectedClass = cName
                                    isClassExpanded = false
                                }
                            )
                        }
                    }
                }

                // Preferred Language
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedLang,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Your Study Language preference") },
                        trailingIcon = {
                            IconButton(onClick = { isLangExpanded = !isLangExpanded }) {
                                Icon(Icons.Default.Language, "language selector button")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { isLangExpanded = !isLangExpanded },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )
                    DropdownMenu(
                        expanded = isLangExpanded,
                        onDismissRequest = { isLangExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        languagesList.forEach { lName ->
                            DropdownMenuItem(
                                text = { Text(lName) },
                                onClick = {
                                    selectedLang = lName
                                    isLangExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (name.trim().isNotEmpty()) {
                            vm.registerStudent(name.trim(), selectedClass, selectedLang)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_register"),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(12.dp),
                    enabled = name.trim().isNotEmpty()
                ) {
                    Text("Register & Access Sparky", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Text(
                    text = "Created by Kalaiselvan",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun LessonStoryOverlay(
    vm: TutorViewModel,
    story: String,
    primaryColor: Color,
    secondaryColor: Color
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = false) {}
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
            border = BorderStroke(2.dp, secondaryColor.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = "stories books icon indicator",
                            tint = primaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Lessons into Stories Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF1E293B)
                        )
                    }
                    IconButton(onClick = { vm.closeActiveStory() }) {
                        Icon(Icons.Default.Close, "close story view", tint = Color.Red)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📖 Story Lesson Built!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937)
                    )
                    Button(
                        onClick = { vm.speakText(story) },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, "speak outline icon", tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Speak Story", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = story,
                            fontSize = 14.sp,
                            color = Color(0xFF374151),
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Start
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Created by Kalaiselvan • Converted with Sparky Story Engine",
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { vm.closeActiveStory() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text("Close Story Chamber")
                }
            }
        }
    }
}

@Composable
fun ExamQuestionsOverlay(
    vm: TutorViewModel,
    content: String,
    primaryColor: Color,
    secondaryColor: Color
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = false) {}
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "exam outline sheet icon",
                            tint = primaryColor,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Exam Important Questions",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF1E293B)
                        )
                    }
                    IconButton(onClick = { vm.closeExamQuestions() }) {
                        Icon(Icons.Default.Close, "close exam sheet view", tint = Color.Red)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEFF6FF))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📝 Important Questions Configured!",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D4ED8)
                    )
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Exam Questions", content)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied Exam Questions to Clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, "copy board content", tint = primaryColor, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = content,
                            fontSize = 14.sp,
                            color = Color(0xFF1E293B),
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { vm.closeExamQuestions() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text("Close Exam Sheet")
                }
            }
        }
    }
}

@Composable
fun NewsHubOverlay(
    vm: TutorViewModel,
    content: String,
    primaryColor: Color,
    secondaryColor: Color
) {
    val selectedCategory by vm.selectedNewsCategory.collectAsState()
    val isNewsLoading by vm.isNewsLoading.collectAsState()
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = false) {}
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = "news updates notification icon",
                            tint = primaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Educational News & Scholarships Hub",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1E293B)
                        )
                    }
                    IconButton(onClick = { vm.closeEduNews() }) {
                        Icon(Icons.Default.Close, "close news center view", tint = Color.Red)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Selector chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("school" to "📰 News", "scholarship" to "🎓 Scholarship", "tnpsc" to "📝 TNPSC").forEach { (cat, label) ->
                        val isSelected = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) primaryColor else Color(0xFFF1F5F9))
                                .clickable { vm.fetchEduNews(cat) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color(0xFF64748B)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isNewsLoading) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = primaryColor)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Connecting to educational feed servers...", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        ) {
                            Text(
                                text = content,
                                fontSize = 13.sp,
                                color = Color(0xFF334155),
                                lineHeight = 19.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { vm.closeEduNews() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text("Close News Center")
                }
            }
        }
    }
}

@Composable
fun StudyPlannerOverlay(
    vm: TutorViewModel,
    primaryColor: Color,
    secondaryColor: Color,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val subjectsInput by vm.plannerSubjectsInput.collectAsState()
    val hoursInput by vm.plannerHoursInput.collectAsState()
    val durationInput by vm.plannerDurationInput.collectAsState()
    val isPlannerLoading by vm.isPlannerLoading.collectAsState()
    val plannerContent by vm.plannerContent.collectAsState()
    val completedSlots by vm.completedSlots.collectAsState()

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = false) {}
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(2.dp, primaryColor.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "planner head icon",
                            tint = primaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "A.I. Study Scheduler",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = primaryColor
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close Study planner", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (plannerContent == null && !isPlannerLoading) {
                        Text(
                            text = "Enter subjects you want to cover, how many hours you wish to devote daily, and let Sparky generate a custom step-by-step checklist timetable for you!",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 18.sp
                        )

                        Text("Topics / Subjects to Study:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        OutlinedTextField(
                            value = subjectsInput,
                            onValueChange = { vm.plannerSubjectsInput.value = it },
                            placeholder = { Text("e.g. Science physics, English story, Math algebra", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth().testTag("planner_subjects_tf"),
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Daily Study Hours:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                OutlinedTextField(
                                    value = hoursInput,
                                    onValueChange = { vm.plannerHoursInput.value = it },
                                    placeholder = { Text("e.g. 2, 4, 8", fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth().testTag("planner_hours_tf"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Duration:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                OutlinedTextField(
                                    value = durationInput,
                                    onValueChange = { vm.plannerDurationInput.value = it },
                                    placeholder = { Text("e.g. 3 Days, 1 Week", fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth().testTag("planner_duration_tf"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        Button(
                            onClick = { vm.generateStudyPlan() },
                            modifier = Modifier.fillMaxWidth().testTag("btn_build_plan"),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Generate A.I. Study Schedule Plan")
                        }
                    }

                    if (isPlannerLoading) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = primaryColor)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Analyzing topics, calibrating schedules & forming study milestones...",
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                                color = Color(0xFF475569)
                            )
                        }
                    }

                    if (plannerContent != null) {
                        // Extract Slot strings dynamically to build checkbox list items
                        val slotList = remember(plannerContent) {
                            val list = mutableListOf<String>()
                            val regex = Regex("\\[(.*?)\\]")
                            plannerContent?.lines()?.forEach { line ->
                                regex.findAll(line).forEach { matchResult ->
                                    val token = matchResult.groupValues[1]
                                    if (token.contains("Day", ignoreCase = true) && token.contains("Slot", ignoreCase = true)) {
                                        list.add(token)
                                    }
                                }
                            }
                            list.distinct()
                        }

                        if (slotList.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2F6)),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "🎯 Interactive Plan Day Checklist",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = primaryColor
                                    )
                                    Text(
                                        "Tick off each study milestone slot as you finish studying it to earn +5 XP!",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    slotList.forEach { slot ->
                                        val isChecked = completedSlots.contains(slot)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    vm.togglePlannerSlot(slot)
                                                    if (!isChecked) {
                                                        Toast.makeText(context, "🎉 Sparky Milestone! +5 XP for completing $slot!", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = {
                                                    vm.togglePlannerSlot(slot)
                                                    if (!isChecked) {
                                                        Toast.makeText(context, "🎉 Sparky Milestone! +5 XP for completing $slot!", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.testTag("checkbox_$slot")
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = slot,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                color = if (isChecked) Color(0xFF94A3B8) else Color(0xFF1E293B)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "📄 Full A.I. Reference Timetable Layout",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF475569),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = plannerContent ?: "",
                                    fontSize = 12.sp,
                                    color = Color(0xFF1E293B),
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { vm.clearStudyPlan() },
                                modifier = Modifier.weight(1f).testTag("btn_clear_plan"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Reset / New Plan")
                            }

                            Button(
                                onClick = { vm.speakText(plannerContent ?: "") },
                                modifier = Modifier.weight(1f).testTag("btn_speech_plan"),
                                colors = ButtonDefaults.buttonColors(containerColor = secondaryColor),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("🔊 Listen Plan")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onClose) {
                        Text("Close Planner", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun DoubtSolverOverlay(
    vm: TutorViewModel,
    primaryColor: Color,
    secondaryColor: Color,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val doubtInput by vm.doubtInput.collectAsState()
    val doubtImageBase by vm.doubtImageBase64.collectAsState()
    val isDoubtLoading by vm.isDoubtLoading.collectAsState()
    val scannerStatusMessage by vm.scannerStatusMessage.collectAsState()
    val doubtSolution by vm.doubtSolution.collectAsState()

    val scrollState = rememberScrollState()

    // File gallery photo selector
    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    val base64Str = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    vm.doubtImageBase64.value = base64Str
                    vm.doubtImageMimeType.value = mimeType
                    Toast.makeText(context, "Scanned photo loaded successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load Scanned page: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = false) {}
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(2.dp, primaryColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraIos,
                            contentDescription = "scanning doubts visual icon overlay",
                            tint = primaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Doubt Solver (Scan & Type)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = primaryColor
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close doubt Solver", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (doubtSolution == null && !isDoubtLoading) {
                        Text(
                            text = "Snap a photo of your textbook problem/homework page, or type it down directly. Sparky gives a detailed structured step-by-step breakdown!",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 17.sp
                        )

                        Text("Type Your Doubt / Question:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        OutlinedTextField(
                            value = doubtInput,
                            onValueChange = { vm.doubtInput.value = it },
                            placeholder = { Text("e.g. Integrate x * cos(x) dx, or type physics questions...", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth().testTag("doubt_solver_tf"),
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Upload scanned page buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF2F6), contentColor = primaryColor),
                                border = BorderStroke(1.dp, primaryColor),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("btn_select_gallery_photo")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CameraAlt, "Camera icon", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick Gallery Photo", fontSize = 12.sp)
                                }
                            }

                            if (doubtImageBase != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                                    border = BorderStroke(1.dp, Color(0xFF22C55E)),
                                    modifier = Modifier.testTag("attached_photo_badge")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Page Attached ✓", fontSize = 11.sp, color = Color(0xFF166534), fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.Close,
                                            "Clear Attach",
                                            tint = Color(0xFF166534),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    vm.doubtImageBase64.value = null
                                                    vm.doubtImageMimeType.value = null
                                                }
                                        )
                                    }
                                }
                            }
                        }

                        // Presets scanning simulator layout
                        Divider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFE2E8F0))
                        Text(
                            text = "💡 Simulate Graphical Scanned Sheets:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF475569)
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val sampleHWConfigs = listOf(
                                Triple("Mathematics Integral Calculus Equation 📝", "Evaluate critical integration: Integrate x * cos(x) dx showing integration-by-parts step-by-step.", "sample_math"),
                                Triple("Velocity Force Physics Problem 🔬", "Find the ultimate speed & time taken of a 2kg block drop down free-falling from 20 meters high.", "sample_phys"),
                                Triple("Oxygen Hydrocarbon Chemistry Bond 🧪", "Balance the dynamic combustion transaction and list formulas: C3H8 + O2 = CO2 + H2O.", "sample_chem"),
                                Triple("Cell Biology Cellular Differences 🧬", "Draw structured comparison charts detailing the main functional variations of Mitochondria vs Chloroplast.", "sample_bio")
                            )

                            sampleHWConfigs.forEach { (label, prompt, demoKey) ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            vm.doubtInput.value = prompt
                                            // Simulate loaded handwritten picture
                                            vm.doubtImageBase64.value = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="
                                            vm.doubtImageMimeType.value = "image/png"
                                            vm.solveStudyDoubt()
                                        }
                                        .testTag("sample_sheet_$demoKey"),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Photo, "sample graphic icon", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF334155))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { vm.solveStudyDoubt() },
                            modifier = Modifier.fillMaxWidth().testTag("btn_solve_doubt_prompt"),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Solve Doubt Step-by-Step")
                        }
                    }

                    if (isDoubtLoading) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = primaryColor)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = scannerStatusMessage,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryColor,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )

                            // Glowing scan Diagnostic Sweeper animation
                            Spacer(modifier = Modifier.height(14.dp))
                            val infiniteTransition = rememberInfiniteTransition(label = "scanning_laser")
                            val offsetValue by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 90f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scansweep"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(100.dp)
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Text(
                                    "TEXTBOOK PAGE DIGITIZER...", 
                                    fontSize = 10.sp, 
                                    color = Color.LightGray, 
                                    modifier = Modifier.padding(8.dp),
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .padding(horizontal = 4.dp)
                                        .graphicsLayer(translationY = offsetValue)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color.Transparent, Color(0xFF22C55E), Color.Transparent)
                                            )
                                        )
                                )
                            }
                        }
                    }

                    if (doubtSolution != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, Color(0xFFBBF7D0))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🎯 Unified Master Solution",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF166534)
                                    )

                                    IconButton(
                                        onClick = { vm.speakText(doubtSolution ?: "") },
                                        modifier = Modifier.size(36.dp).testTag("btn_speak_doubt_solution")
                                    ) {
                                        Icon(Icons.Default.VolumeUp, "Speak solution details aloud", tint = Color(0xFF166534))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = doubtSolution ?: "",
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = Color(0xFF1F2937)
                                )
                            }
                        }

                        Button(
                            onClick = { vm.clearDoubtSolver() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            modifier = Modifier.fillMaxWidth().testTag("btn_reset_doubt_solver"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Clear / Ask another Doubt")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onClose) {
                        Text("Close Solver", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun GkTriviaOverlay(
    vm: TutorViewModel,
    primaryColor: Color,
    secondaryColor: Color,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val gkCategory by vm.gkCategory.collectAsState()
    val isGkGenerating by vm.isGkGenerating.collectAsState()
    val activeQuestion by vm.activeGkQuestion.collectAsState()
    val selectedAnswer by vm.selectedGkAnswer.collectAsState()
    val isAnswerChecked by vm.isGkAnswerChecked.collectAsState()
    val gkFeedback by vm.gkAnswerFeedback.collectAsState()

    val totCount by vm.gkTotalCount.collectAsState()
    val corrCount by vm.gkCorrectCount.collectAsState()

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = false) {}
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(2.dp, secondaryColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Trophy,
                            contentDescription = "trivia game dynamic rotator trophy",
                            tint = secondaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Daily GK Trivia Arena",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = secondaryColor
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close GK dynamic trivia", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scoreboard details
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Sparky Trivia Statistics:", fontSize = 10.sp, color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                            Text("$corrCount / $totCount Challenges Mastered", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                        }
                        Button(
                            onClick = {
                                vm.gkCorrectCount.value = 0
                                vm.gkTotalCount.value = 0
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color(0xFF92400E)),
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("Reset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Choose a category below to load a dynamic high-school quiz question instantly. Challenge yourself offline or online!",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 17.sp
                    )

                    // Category Chips selector
                    val categories = listOf("Science", "History", "Geography", "Space & Cosmos", "Sports", "Literature")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            AssistChip(
                                onClick = { vm.loadGkTrivialQuestion(cat) },
                                label = { Text(cat, fontSize = 11.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (gkCategory == cat) Color(0xFFFEF3C7) else Color(0xFFF1F5F9),
                                    labelColor = if (gkCategory == cat) Color(0xFFD97706) else Color(0xFF475569)
                                ),
                                modifier = Modifier.testTag("gk_chip_$cat")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (activeQuestion == null && !isGkGenerating) {
                        Button(
                            onClick = { vm.loadGkTrivialQuestion("Science") },
                            modifier = Modifier.fillMaxWidth().testTag("btn_start_gk_trivia"),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Shuffle Fresh Category Trivia Game")
                        }
                    }

                    if (isGkGenerating) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = secondaryColor)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Cooking up high-quality master category trivia quiz...", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                    }

                    activeQuestion?.let { q ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFFFEECC), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(q.category.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2410C))
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = q.question,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                    lineHeight = 20.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Options (A, B, C, D)
                                val optionsList = listOf(
                                    "A" to q.optionA,
                                    "B" to q.optionB,
                                    "C" to q.optionC,
                                    "D" to q.optionD
                                )

                                optionsList.forEach { (code, text) ->
                                    val isUserSelected = selectedAnswer == code
                                    val isCorrOpt = code.equals(q.correctAnswer, ignoreCase = true)

                                    val borderCol = when {
                                        isAnswerChecked && isCorrOpt -> Color(0xFF22C55E) // Green for correct answer
                                        isAnswerChecked && isUserSelected -> Color(0xFFEF4444) // Red for wrong selection
                                        isUserSelected -> primaryColor
                                        else -> Color(0xFFCBD5E1)
                                    }

                                    val bgCol = when {
                                        isAnswerChecked && isCorrOpt -> Color(0xFFDCFCE7)
                                        isAnswerChecked && isUserSelected -> Color(0xFFFEE2E2)
                                        isUserSelected -> primaryColor.copy(alpha = 0.08f)
                                        else -> Color.White
                                    }

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable(enabled = !isAnswerChecked) { vm.submitGkAnswer(code) }
                                            .testTag("option_$code"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = bgCol),
                                        border = BorderStroke(1.5.dp, borderCol)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .background(
                                                        if (isUserSelected) primaryColor else Color(0xFFEEF2F6),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    code, 
                                                    fontSize = 11.sp, 
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isUserSelected) Color.White else Color(0xFF475569)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(text, fontSize = 12.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isAnswerChecked) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFAEC)),
                            border = BorderStroke(1.dp, Color(0xFFFEF3C7)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = gkFeedback,
                                    fontSize = 12.sp,
                                    color = Color(0xFF451A03),
                                    lineHeight = 17.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Button(
                            onClick = { vm.loadGkTrivialQuestion(gkCategory.value ?: "Science") },
                            modifier = Modifier.fillMaxWidth().testTag("btn_next_gk_trivia"),
                            colors = ButtonDefaults.buttonColors(containerColor = secondaryColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Refresh, "shuffle next trivia icon")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Load Next Trivia Question")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onClose) {
                        Text("Close GK Game", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun AskAnythingOverlay(
    vm: TutorViewModel,
    primaryColor: Color,
    secondaryColor: Color,
    onClose: () -> Unit
) {
    var queryText by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = false) {}
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(2.dp, primaryColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QuestionAnswer,
                            contentDescription = "floating ask query icon selector",
                            tint = primaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Instant Ask Anything Box",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = primaryColor
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close ask helper", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (resultText == null && !isLoading) {
                        Text(
                            text = "Have a random classroom curiosity or quick question? Type it down here and get clear, student-friendly analogies immediately!",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 17.sp
                        )

                        Text("What is your inquiry today?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        OutlinedTextField(
                            value = queryText,
                            onValueChange = { queryText = it },
                            placeholder = { Text("e.g. Why is the ocean blue? or explain gravity in 1 sentence.", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth().testTag("ask_anything_tf"),
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                if (queryText.trim().isNotEmpty()) {
                                    isLoading = true
                                    resultText = null
                                    vm.viewModelScope.launch {
                                        try {
                                            val prompt = "Give a simple, student friendly explanation with an example for: ${queryText.trim()}"
                                            val systemInstruction = Content(
                                                parts = listOf(
                                                    Part(text = "You are Sparky's floating micro explorer assistant. Keep answers to 3 easy bullets, with real world analogies.")
                                                )
                                            )
                                            val request = GenerateContentRequest(
                                                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                                                systemInstruction = systemInstruction,
                                                generationConfig = GenerationConfig(temperature = 0.7f)
                                            )
                                            val response = RetrofitClient.service.generateContent(vm.apiKey, request)
                                            val answer = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                                                ?: "Could not gather answer. Try typing again!"
                                            resultText = answer
                                            vm.repository.addXp(5)
                                        } catch (e: Exception) {
                                            resultText = "Trouble connecting to Sparky search index. Check network."
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("btn_submit_search"),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Inquire Sparky Aloud")
                        }
                    }

                    if (isLoading) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = primaryColor)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Consulting Sparky learning index...", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                    }

                    resultText?.let { res ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2F6)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🔍 Sparky's Micro Answer",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = primaryColor
                                    )

                                    IconButton(
                                        onClick = { vm.speakText(res) },
                                        modifier = Modifier.size(32.dp).testTag("btn_speak_search_solution")
                                    ) {
                                        Icon(Icons.Default.VolumeUp, "Speak search details aloud", tint = primaryColor)
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = res,
                                    fontSize = 12.sp,
                                    color = Color(0xFF1E293B),
                                    lineHeight = 17.sp
                                )
                            }
                        }

                        Button(
                            onClick = {
                                resultText = null
                                queryText = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            modifier = Modifier.fillMaxWidth().testTag("btn_reset_search"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Ask another Question")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onClose) {
                        Text("Close Search Box", color = Color.Gray)
                    }
                }
            }
        }
    }
}
