package com.studydock.learning

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.net.Uri
import org.json.JSONObject
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Navy = Color(0xFF0B1F41)
private val Sky = Color(0xFF0877E8)
private val Aqua = Color(0xFF00BBD4)
private val Background = Color(0xFFF6F8FC)
private val Muted = Color(0xFF66758B)
private val Outline = Color(0xFFE3EAF5)
private val Green = Color(0xFF0C8062)

private enum class Screen(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Notes("Notes", Icons.Filled.MenuBook),
    Practice("Practice", Icons.Filled.Quiz),
    Images("Images", Icons.Filled.Image),
    Progress("Progress", Icons.Filled.Insights),
    Library("Library", Icons.Filled.LibraryBooks),
    Levels("Levels", Icons.Filled.EmojiEvents)
}

class MainActivity : ComponentActivity() {
    private val library by lazy { StudyLibrary(this) }
    private val progress by lazy { StudyProgress(this) }
    private val github by lazy { RemoteStudySource(this, library) }
    private val learningSource by lazy { GuidedLearningSource() }
    private val learningProgress by lazy { GuidedLearningProgress(this) }
    private var onlinePath by mutableStateOf<GuidedPath?>(null)
    private var learningMessage by mutableStateOf("Loading learning modules from GitHub…")
    private var part by mutableStateOf<StudyPart?>(null)
    private var importMessage by mutableStateOf("")
    private var importing by mutableStateOf(false)
    private var remoteMessage by mutableStateOf("Checking GitHub…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.rgb(11,31,65)
        window.navigationBarColor = AndroidColor.rgb(246,248,252)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        part = try { library.readPart() } catch (e: Exception) { importMessage = e.message ?: "Cannot read imported content"; null }
        setContent {
            StudyDockTheme {
                var splash by remember { mutableStateOf(savedInstanceState == null) }
                LaunchedEffect(Unit) {
                    delay(5000)
                    splash = false
                }
                val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) importLocalZip(uri)
                }
                if (splash) NativeSplash(onSkip = { splash = false }) else NativeApp(
                    part = part, progress = progress, importing = importing,
                    importMessage = remoteMessage, onImport = { syncRemote() },
                    onImportZip = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                    learningPath = onlinePath, learningStatus = learningMessage, learningProgress = learningProgress,
                    onRefreshLessons = { syncLessons() }
                )
            }
        }
        syncLessons()
        syncRemote()
    }

    private fun syncLessons() {
        learningMessage = "Loading learning modules from GitHub…"
        Thread {
            try {
                val result = learningSource.fetch()
                runOnUiThread {
                    onlinePath = result
                    learningMessage = "10 StudyDock learning levels loaded."
                }
            } catch (error: Exception) {
                runOnUiThread {
                    learningMessage = "GitHub lessons unavailable: " +
                        (error.message ?: "Network error").take(160)
                }
            }
        }.start()
    }

    private fun importLocalZip(uri: Uri) {
        if (importing) return
        importing = true
        remoteMessage = "Verifying local textbook ZIP and original images…"
        Thread {
            try {
                val result = library.importZip(uri)
                runOnUiThread {
                    part = result
                    remoteMessage = "Verified local import complete. ${result.questions.size} scoreable questions across installed parts."
                    importing = false
                }
            } catch (error: Exception) {
                runOnUiThread {
                    remoteMessage = "Local import failed: " + (error.message ?: "unknown error").take(180) +
                        ". Previously installed parts were retained."
                    importing = false
                }
            }
        }.start()
    }

    private fun syncRemote() {
        if (importing) return
        importing = true
        remoteMessage = "Contacting GitHub content repository…"
        Thread {
            try {
                val result = github.sync()
                runOnUiThread {
                    part = result.part
                    remoteMessage = result.message
                    importing = false
                }
            } catch (error: Exception) {
                runOnUiThread {
                    remoteMessage = "GitHub sync unavailable: " + (error.message ?: "network error").take(170) +
                        ". Previously verified content is retained."
                    importing = false
                }
            }
        }.start()
    }
}

@Composable
private fun StudyDockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Sky, onPrimary = Color.White, background = Background,
            surface = Color.White, onSurface = Navy, onBackground = Navy,
            outline = Outline, secondary = Aqua
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(26.dp)
        ),
        content = content
    )
}

@Composable
private fun NativeSplash(onSkip: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Navy, Color(0xFF10366D), Color(0xFF0B1F41)))
        )
    ) {
        Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.studydock_logo),
                contentDescription = "StudyDock open-book logo",
                modifier = Modifier.size(132.dp).clip(RoundedCornerShape(32.dp))
            )
            Spacer(Modifier.height(24.dp))
            Text("StudyDock", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 37.sp)
            Spacer(Modifier.height(5.dp))
            Text("Learn · Practice · Succeed", color = Color(0xFFBCD9FF), fontSize = 14.sp)
            Spacer(Modifier.height(42.dp))
            LinearProgressIndicator(modifier = Modifier.width(140.dp).height(4.dp), color = Aqua, trackColor = Color.White.copy(alpha = 0.16f))
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("A ZEBJUS LLP Product", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            TextButton(onClick = onSkip) { Text("Skip intro", color = Color(0xFFB7DBFF)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeApp(part: StudyPart?, progress: StudyProgress, importing: Boolean, importMessage: String,
    onImport: () -> Unit, onImportZip: () -> Unit, learningPath: GuidedPath?, learningStatus: String,
    learningProgress: GuidedLearningProgress, onRefreshLessons: () -> Unit) {
    var screen by remember { mutableStateOf(Screen.Home) }
    var practiceAsImage by remember { mutableStateOf(false) }
    var selectedLevel by remember { mutableStateOf<Int?>(null) }
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        Scaffold(
            containerColor = Background,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.studydock_logo), contentDescription = null,
                                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("StudyDock", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, lineHeight = 23.sp)
                                Text("A ZEBJUS LLP Product", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { screen = Screen.Library }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Open library", tint = Sky)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = {
                if (!wide) {
                    NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                        listOf(Screen.Home, Screen.Levels, Screen.Notes, Screen.Practice, Screen.Progress).forEach { tab ->
                            NavigationBarItem(
                                selected = screen == tab, onClick = {
                                    practiceAsImage = tab == Screen.Images
                                    selectedLevel = null
                                    screen = tab
                                },
                                icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(23.dp)) },
                                label = { Text(tab.label, fontSize = 10.sp, maxLines = 1) },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    NavigationRail(containerColor = Color.White) {
                        Spacer(Modifier.height(14.dp))
                        Screen.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = screen == tab,
                                onClick = { practiceAsImage = tab == Screen.Images; selectedLevel = null; screen = tab },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.fillMaxWidth().widthIn(max = 1000.dp)) {
                        when (screen) {
                            Screen.Home -> HomeScreen(part, progress,
                                onNavigate = { practiceAsImage = it == Screen.Images; selectedLevel = null; screen = it },
                                onImport = onImport, path = learningPath)
                            Screen.Notes -> if (part == null) GuidedLearningScreen(learningPath,
                                learningStatus, learningProgress, onRefreshLessons) else NotesScreen(part)
                            Screen.Practice, Screen.Images -> if (part == null) GuidedLearningScreen(
                                learningPath, learningStatus, learningProgress, onRefreshLessons)
                                else QuizScreen(part, progress, practiceAsImage, selectedLevel)
                            Screen.Levels -> GuidedLearningScreen(learningPath, learningStatus,
                                learningProgress, onRefreshLessons)
                            Screen.Progress -> ProgressScreen(part, progress, learningPath, learningProgress)
                            Screen.Library -> LibraryScreen(part, importing, importMessage,
                                onImport, onImportZip, learningPath, learningStatus, onRefreshLessons)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, shape = RoundedCornerShape(22.dp), color = Color.White,
        shadowElevation = 1.dp, border = androidx.compose.foundation.BorderStroke(1.dp, Outline)) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Navy)
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(part: StudyPart?, progress: StudyProgress, onNavigate: (Screen) -> Unit,
    onImport: () -> Unit, path: GuidedPath?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        Surface(shape = RoundedCornerShape(26.dp), color = Navy) {
            Column(
                Modifier.background(Brush.linearGradient(listOf(Navy, Color(0xFF1456A2)))).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("YOUR LEARNING COMPANION", color = Color(0xFF80DCFF), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("Make every study session count.", color = Color.White,
                    fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, lineHeight = 33.sp)
                Text("Learn nursing concepts in 10 short levels, then master a quiz after each lesson.",
                    color = Color(0xFFDBE9FC), fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(7.dp))
                Button(onClick = { onNavigate(Screen.Levels) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Navy),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("Start learning", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
        if (part != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Questions", "${part.questions.size}", Modifier.weight(1f))
                MetricCard("Notes", "${part.notes.size}", Modifier.weight(1f))
                MetricCard("Images", "${part.images.size}", Modifier.weight(1f))
            }
        }
        if (path != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Levels", "10", Modifier.weight(1f))
                MetricCard("Concepts", "30", Modifier.weight(1f))
                MetricCard("Quiz checks", "50", Modifier.weight(1f))
            }
        }
        SectionHeader("Your learning space")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tileWidth = (maxWidth - 12.dp) / 2
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 2) {
                HomeAction("Learn topics", "Short explanations and examples", Icons.Default.MenuBook, Color(0xFFE8F1FF), Sky, tileWidth) { onNavigate(Screen.Notes) }
                HomeAction("Levels 1–10", "Master each stage", Icons.Default.EmojiEvents, Color(0xFFEDF5FF), Sky, tileWidth) { onNavigate(Screen.Levels) }
                HomeAction("Level quizzes", "Five checks with feedback", Icons.Default.Quiz, Color(0xFFF0EAFF), Color(0xFF7255BD), tileWidth) { onNavigate(Screen.Practice) }
                HomeAction("Learning levels", "Unlock levels 1–10", Icons.Default.EmojiEvents, Color(0xFFE6FAF6), Green, tileWidth) { onNavigate(Screen.Levels) }
                HomeAction("My progress", "Track your growth", Icons.Default.ShowChart, Color(0xFFFFF2E5), Color(0xFFB97D27), tileWidth) { onNavigate(Screen.Progress) }
            }
        }
        ContentCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LibraryBooks, contentDescription = null, tint = Sky)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("StudyDock learning content", fontWeight = FontWeight.Bold)
                    Text(if (part != null) "Additional reference material installed" else "StudyDock lessons load from GitHub", fontSize = 12.sp, color = Muted)
                }
                TextButton(onClick = { onNavigate(Screen.Library) }) { Text("Open") }
            }
        }
        Text("A ZEBJUS LLP Product", Modifier.fillMaxWidth(), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MetricCard(name: String, number: String, modifier: Modifier = Modifier) {
    ContentCard(modifier) {
        Text(number, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Navy)
        Text(name, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun HomeAction(title: String, detail: String, icon: ImageVector, bg: Color, tint: Color, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(width).heightIn(min = 143.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(21.dp), color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Outline)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(44.dp).background(bg, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint)
            }
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(detail, fontSize = 11.sp, color = Muted, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun EmptyLibrary(onImport: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.Inventory2, null, Modifier.size(54.dp), tint = Sky)
        Text("Waiting for GitHub content", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("Connect to GitHub to download verified textbook questions, images and notes when published.",
            color = Muted, textAlign = TextAlign.Center, fontSize = 13.sp)
        if (onImport != null) Button(onClick = onImport) { Text("Sync from GitHub") }
    }
}

@Composable
private fun NotesScreen(part: StudyPart?) {
    if (part == null) { EmptyLibrary(); return }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<StudyNote?>(null) }
    if (selected != null) {
        val note = selected!!
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            TextButton(onClick = { selected = null }) { Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("All study notes") }
            ContentCard {
                Text(note.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(14.dp))
                Text(note.body, fontSize = 15.sp, lineHeight = 24.sp)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Study notes", fontWeight = FontWeight.ExtraBold, fontSize = 27.sp)
            Text("Textbook-linked learning, chapter by chapter", color = Muted, fontSize = 13.sp)
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search topics") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            val filtered = remember(query, part) { part.notes.filter {
                it.title.contains(query, true) || part.chapters.firstOrNull { c -> c.id == it.chapterId }?.title?.contains(query, true) == true
            }}
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(filtered, key = { it.id }) { note ->
                    ContentCard(Modifier.fillMaxWidth().clickable { selected = note }) {
                        Text(note.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(note.body.replace('\n', ' ').take(160), fontSize = 12.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(7.dp))
                        Text("Read notes  →", fontSize = 12.sp, color = Sky, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizScreen(part: StudyPart?, progress: StudyProgress, imageOnly: Boolean, levelNumber: Int? = null) {
    if (part == null) { EmptyLibrary(); return }
    var session by remember(part, imageOnly, levelNumber) { mutableStateOf<List<StudyQuestion>?>(null) }
    var count by remember { mutableIntStateOf(10) }
    var difficulty by remember { mutableStateOf("All") }
    var source by remember { mutableStateOf("All") }
    var topicQuery by remember { mutableStateOf("") }
    var reviewOnly by remember { mutableStateOf(false) }
    val selectedLevelIds = levelNumber?.let { number ->
        LevelEngine.levels(part, progress).getOrNull(number - 1)?.takeIf { it.unlocked }
            ?.questions?.map { it.id }?.toSet()
    }
    if (session == null) {
        val available = part.questions.filter { q ->
            (!imageOnly || q.isImage) && (levelNumber == null || selectedLevelIds?.contains(q.id) == true) &&
                (difficulty == "All" || q.difficulty.equals(difficulty, true)) &&
                (source == "All" || (source == "Original" && q.origin == "textbook") || (source == "Practice" && q.origin != "textbook")) &&
                (!reviewOnly || q.id in progress.incorrectIds()) &&
                (topicQuery.isBlank() || q.topic.contains(topicQuery, true) ||
                    part.chapters.firstOrNull { it.id == q.chapterId }?.title?.contains(topicQuery, true) == true)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (levelNumber != null) "Level $levelNumber · Practice" else if (imageOnly) "Image-based quiz" else "Practice MCQs", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (imageOnly) "Identify original textbook images and interpret diagrams." else "Practice with textbook-verified answers and explanations.", color = Muted, fontSize = 13.sp)
            ContentCard {
                Text("Difficulty", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ChoiceChips(listOf("All", "Easy", "Medium", "Hard"), difficulty) { difficulty = it }
                Spacer(Modifier.height(14.dp))
                Text("Question type", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ChoiceChips(listOf("All", "Original", "Practice"), source) { source = it }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(value = topicQuery, onValueChange = { topicQuery = it },
                    label = { Text("Filter by chapter or topic") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Text("Number of questions", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ChoiceChips(listOf("5", "10", "20", "50", "All"), if (count == 0) "All" else "$count") {
                    count = it.toIntOrNull() ?: 0
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = reviewOnly, onCheckedChange = { reviewOnly = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Review incorrect answers", fontSize = 13.sp)
                }
            }
            Text("${available.size} eligible questions available", color = Muted, fontSize = 12.sp)
            Button(onClick = { session = available.shuffled().take(if (count == 0) available.size else count) },
                enabled = available.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Start practice", fontWeight = FontWeight.Bold)
            }
        }
    } else {
        QuizSession(session!!, part, progress, onExit = { session = null })
    }
}

@Composable
private fun ChoiceChips(choices: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(choices) { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(option) })
        }
    }
}

@Composable
private fun QuizSession(questions: List<StudyQuestion>, part: StudyPart, progress: StudyProgress, onExit: () -> Unit) {
    if (questions.isEmpty()) { EmptyLibrary(); return }
    var index by remember(questions) { mutableIntStateOf(0) }
    var finished by remember(questions) { mutableStateOf(false) }
    val selections = remember(questions) { mutableStateMapOf<String, String>() }
    val submissions = remember(questions) { mutableStateMapOf<String, Boolean>() }
    val hints = remember(questions) { mutableStateMapOf<String, Boolean>() }
    if (finished) {
        val answered = submissions.count { it.value }
        val earned = questions.count { q -> submissions[q.id] == true && selections[q.id] == q.correct }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Default.EmojiEvents, null, Modifier.size(60.dp), tint = Sky)
            Text("Practice complete", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("$earned / ${questions.size}", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = Navy)
            Text("${(earned * 100f / questions.size).toInt()}% score · ${questions.size - answered} unanswered", color = Muted)
            LinearProgressIndicator(progress = { earned.toFloat() / questions.size }, modifier = Modifier.fillMaxWidth().height(10.dp))
            Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Return to practice") }
        }
        return
    }
    val question = questions[index]
    val selected = selections[question.id]
    val submitted = submissions[question.id] == true
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onExit) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(5.dp)); Text("End") }
            Text("Question ${index + 1} of ${questions.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
        }
        LinearProgressIndicator(progress = { (index + 1f) / questions.size }, modifier = Modifier.fillMaxWidth().height(6.dp))
        ContentCard {
            Text("${question.topic} · PDF p. ${question.sourcePage}", color = Sky, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(question.stem, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp)
            if (question.images.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                question.images.forEach { id ->
                    val imageFile = part.imageFile(id)
                    if (imageFile != null) OriginalFigure(imageFile.absolutePath)
                    else Text("Required source image unavailable — not suitable for scoring", color = Color.Red)
                }
            }
            Spacer(Modifier.height(12.dp))
            question.options.forEach { option ->
                val correctAnswer = submitted && option.id == question.correct
                val incorrectSelected = submitted && selected == option.id && !correctAnswer
                val border = when { correctAnswer -> Green; incorrectSelected -> Color(0xFFBD4C48); selected == option.id -> Sky; else -> Outline }
                Surface(
                    onClick = { if (!submitted) selections[question.id] = option.id },
                    enabled = !submitted, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(15.dp), color = if (correctAnswer) Color(0xFFE5F7F0) else Color.White,
                    border = androidx.compose.foundation.BorderStroke(if (selected == option.id) 2.dp else 1.dp, border)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(option.id, fontWeight = FontWeight.Bold, color = border, modifier = Modifier.width(31.dp))
                        Text(option.text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                        if (correctAnswer) Icon(Icons.Default.CheckCircle, null, tint = Green)
                    }
                }
            }
            if (!submitted && question.hint.isNotBlank()) {
                TextButton(onClick = { hints[question.id] = !(hints[question.id] ?: false) }) {
                    Icon(Icons.Default.Lightbulb, null); Spacer(Modifier.width(5.dp)); Text("Hint")
                }
                if (hints[question.id] == true) Text(question.hint, color = Muted, fontSize = 13.sp)
            }
            AnimatedVisibility(submitted) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Spacer(Modifier.height(10.dp))
                    Text(if (selected == question.correct) "Correct answer" else "Correct answer: ${question.correct}",
                        color = if (selected == question.correct) Green else Color(0xFFAD554B), fontWeight = FontWeight.Bold)
                    Text(question.explanation, fontSize = 13.sp, lineHeight = 20.sp)
                    if (selected != question.correct && selected != null) {
                        question.incorrectExplanation[selected]?.let { Text(it, color = Muted, fontSize = 12.sp) }
                    }
                }
            }
        }
        if (!submitted) {
            Button(
                onClick = {
                    if (selected != null) {
                        submissions[question.id] = true
                        progress.record(question, selected)
                    }
                }, enabled = selected != null && question.images.all { id -> part.imageFile(id)?.isFile == true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text("Submit answer") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { index-- }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("Previous") }
            Button(onClick = { if (index == questions.lastIndex) finished = true else index++ },
                modifier = Modifier.weight(1f)) { Text(if (index == questions.lastIndex) "Results" else "Next") }
        }
    }
}

@Composable
private fun OriginalFigure(filePath: String) {
    val img = remember(filePath) { loadBitmap(filePath) }
    var enlarged by remember { mutableStateOf(false) }
    if (img == null) { Text("Original image unavailable", color = Color.Red); return }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(bitmap = img, contentDescription = "Original textbook question image. Tap to enlarge.",
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 310.dp).clickable { enlarged = true },
            contentScale = ContentScale.Fit)
        TextButton(onClick = { enlarged = true }) { Icon(Icons.Default.ZoomIn, null); Spacer(Modifier.width(5.dp)); Text("Enlarge original image") }
    }
    if (enlarged) {
        AlertDialog(onDismissRequest = { enlarged = false }, confirmButton = { TextButton(onClick = { enlarged = false }) { Text("Close") } },
            title = { Text("Original textbook image") },
            text = { Image(bitmap = img, contentDescription = "Enlarged original textbook image", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth()) })
    }
}

private fun loadBitmap(path: String): ImageBitmap? = try {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)
    var sample = 1
    while (options.outWidth / sample > 1400 || options.outHeight / sample > 1400) sample *= 2
    options.inJustDecodeBounds = false
    options.inSampleSize = sample
    BitmapFactory.decodeFile(path, options)?.asImageBitmap()
} catch (_: Exception) { null }

@Composable
private fun LevelsScreen(part: StudyPart?, progress: StudyProgress, onStart: (Int) -> Unit) {
    val levels = LevelEngine.levels(part, progress)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Levels 1–10", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        Text("Master 80% of a level's verified questions to unlock the next.", color = Muted, fontSize = 13.sp)
        if (part == null) Text("No levels until verified GitHub content is published and synced.", color = Muted, fontSize = 13.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(levels, key = { it.number }) { level ->
                ContentCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (level.unlocked) Icons.Default.EmojiEvents else Icons.Default.Lock, null,
                            tint = if (level.unlocked) Sky else Muted)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Level " + level.number, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(level.mastered.toString() + " / " + level.questions.size + " mastered", fontSize = 12.sp, color = Muted)
                        }
                        if (level.unlocked) Button(onClick = { onStart(level.number) }) { Text("Start") }
                        else Text("Locked", color = Muted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { if (level.questions.isEmpty()) 0f else level.mastered.toFloat() / level.questions.size },
                        modifier = Modifier.fillMaxWidth().height(7.dp))
                    if (level.unlocked && level.number < 10) {
                        Spacer(Modifier.height(5.dp))
                        Text("Master " + level.neededToUnlockNext + " answers to unlock Level " + (level.number + 1),
                            color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressScreen(part: StudyPart?, progress: StudyProgress,
    path: GuidedPath?, learningProgress: GuidedLearningProgress) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("My progress", fontWeight = FontWeight.ExtraBold, fontSize = 27.sp)
        Text("Your learning progress stays privately on this device.", color = Muted, fontSize = 13.sp)
        if (path != null) {
            val mastered = path.levels.count { learningProgress.best(path.revision, it.number) >= 80 }
            ContentCard {
                Text("Guided learning levels", fontWeight = FontWeight.Bold)
                Text(mastered.toString() + " of 10 levels mastered", color = Sky,
                    fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                LinearProgressIndicator(progress = { mastered / 10f },
                    modifier = Modifier.fillMaxWidth().height(8.dp))
            }
        }
        ContentCard {
            Text("Lifetime accuracy", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(9.dp))
            val attempted = progress.attempts()
            val accuracy = if (attempted == 0) 0f else progress.correct().toFloat() / attempted
            Text("${(accuracy * 100).toInt()}%", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Sky)
            LinearProgressIndicator(progress = { accuracy }, modifier = Modifier.fillMaxWidth().height(9.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Answered", "${progress.attempts()}", Modifier.weight(1f))
            MetricCard("Correct", "${progress.correct()}", Modifier.weight(1f))
        }
        ContentCard {
            Text("Questions to revise", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("${progress.incorrectIds().size} incorrect-answer IDs ready for revision in Practice.", color = Muted, fontSize = 13.sp)
            if (part == null) Text("Sync published Part 1 from GitHub to begin.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LibraryScreen(part: StudyPart?, importing: Boolean, importMessage: String,
    onImport: () -> Unit, onImportZip: () -> Unit, learningPath: GuidedPath?,
    learningStatus: String, onRefreshLessons: () -> Unit) {
    var showUnresolved by remember { mutableStateOf(false) }
    if (showUnresolved && part != null) {
        val originals = remember(part) {
            val parent = part.directory.parentFile
            (1..8).flatMap { partId ->
                runCatching {
                    val file = File(File(parent, "part$partId"), "part$partId.json")
                    if (!file.isFile) emptyList() else {
                        val json = JSONObject(file.readText(Charsets.UTF_8))
                        val items = json.optJSONArray("unresolvedOriginalQuestions")
                        (0 until (items?.length() ?: 0)).map { i ->
                            val q = items!!.getJSONObject(i)
                            "Part $partId · ${q.optString("id")} · PDF p. ${q.optInt("sourcePage")}\\n${q.optString("stem", q.optString("question_text"))}"
                        }
                    }
                }.getOrDefault(emptyList())
            }
        }
        AlertDialog(onDismissRequest = { showUnresolved = false },
            confirmButton = { TextButton(onClick = { showUnresolved = false }) { Text("Close") } },
            title = { Text("Source questions · Reference only") },
            text = {
                LazyColumn(Modifier.heightIn(max = 430.dp)) {
                    item { Text("All unresolved originals from installed parts are retained as reference only.", color = Muted, fontSize = 12.sp) }
                    items(originals) { text ->
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text(text, fontSize = 13.sp)
                    }
                }
            })
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text("Content & updates", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        Text("StudyDock lessons load directly from GitHub. No textbook PDF is required.", color = Muted, fontSize = 13.sp)
        ContentCard {
            Text("StudyDock Learning Path", fontWeight = FontWeight.Bold)
            Text(if (learningPath == null) learningStatus else "10 levels · 30 concepts · 50 level quiz checks",
                color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRefreshLessons, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Refresh lessons")
            }
        }
        ContentCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoStories, null, tint = Sky, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("TARGET HIGH · Parts 1–8", fontWeight = FontWeight.Bold)
                    Text(if (part == null) "No textbook content installed" else "${part.questions.size} scoreable MCQs · ${part.chapters.size} chapters", color = Muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = onImport, enabled = !importing, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Default.CloudDownload, null); Spacer(Modifier.width(8.dp))
                Text(if (part == null) "Check authorized GitHub content" else "Check GitHub for updates")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onImportZip, enabled = !importing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp))
                Text("Import Part 1–8 ZIP from phone")
            }
            if (importing) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (importMessage.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(importMessage, color = Muted, fontSize = 12.sp)
            }
        }
        if (part != null) {
            ContentCard {
                Text("Content integrity", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                Text("${part.chapters.size} sections · ${part.notes.size} notes · ${part.images.size} checked images", fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { showUnresolved = true }) { Text("View ${part.unresolved} unresolved originals · Reference only") }
                Text("${part.multiAnswerReference} unsupported multi-answer questions (reference only)", fontSize = 12.sp, color = Muted)
            }
        }
        ContentCard {
            Text("About StudyDock", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text("A ZEBJUS LLP Product", color = Sky, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("Native Android · GitHub-hosted content · No WebView", color = Muted, fontSize = 12.sp)
            Text("Clinical material from the historical source is for examination study, not current treatment guidance.", color = Muted, fontSize = 12.sp)
        }
    }
}
