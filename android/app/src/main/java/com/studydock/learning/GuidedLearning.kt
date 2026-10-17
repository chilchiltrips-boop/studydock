package com.studydock.learning

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Public StudyDock-authored lessons; never downloads or embeds textbook scans. */
data class GuidedConcept(val title: String, val body: String, val example: String)
data class GuidedQuestion(val id: String, val prompt: String, val options: List<String>,
    val correctIndex: Int, val explanation: String)
data class GuidedLevel(val number: Int, val title: String, val sourcePages: List<Int>,
    val summary: String, val concepts: List<GuidedConcept>, val quiz: List<GuidedQuestion>)
data class GuidedPath(val revision: Int, val clinicalNotice: String, val levels: List<GuidedLevel>)

class GuidedLearningSource {
    private val url = "https://raw.githubusercontent.com/chilchiltrips-boop/studydock/main/content/learning/v1.json"

    fun fetch(): GuidedPath {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 12_000
        connection.readTimeout = 18_000
        connection.setRequestProperty("Accept", "application/json")
        val bytes = try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub lessons HTTP " + connection.responseCode)
            }
            val sink = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (sink.size() + count > 512_000) throw IOException("Learning catalog exceeds 512 KB")
                    sink.write(buffer, 0, count)
                }
            }
            sink.toByteArray()
        } finally { connection.disconnect() }
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        if (root.optInt("schemaVersion") != 1 || root.optString("publisher") != "ZEBJUS LLP" ||
            root.optString("kind") != "original-concept-learning" || root.optInt("passingPercent") != 80) {
            throw IOException("Unexpected learning schema or publisher")
        }
        val revision = root.getInt("revision")
        if (revision < 1) throw IOException("Invalid learning revision")
        val arr = root.getJSONArray("levels")
        if (arr.length() != 10) throw IOException("Ten learning levels are required")
        val ids = HashSet<String>()
        val levels = (0 until arr.length()).map { i ->
            val l = arr.getJSONObject(i)
            val n = l.getInt("number")
            if (n != i + 1) throw IOException("Learning levels out of order")
            val conceptsArray = l.getJSONArray("concepts")
            val quizArray = l.getJSONArray("quiz")
            if (conceptsArray.length() != 3 || quizArray.length() != 5) {
                throw IOException("Level content must have three concepts and five questions")
            }
            val concepts = (0 until conceptsArray.length()).map { j ->
                val x = conceptsArray.getJSONObject(j)
                GuidedConcept(x.getString("title"), x.getString("body"), x.getString("example"))
            }
            val quizzes = (0 until quizArray.length()).map { j ->
                val x = quizArray.getJSONObject(j)
                val id = x.getString("id")
                val options = x.getJSONArray("options").toStrings()
                val correct = x.getInt("correctIndex")
                if (!ids.add(id) || options.size != 4 || options.any { it.isBlank() } ||
                    correct !in 0..3) throw IOException("Invalid question: " + id.take(40))
                GuidedQuestion(id, x.getString("prompt"), options, correct, x.getString("explanation"))
            }
            GuidedLevel(n, l.getString("title"), l.getJSONArray("sourcePages").toInts(),
                l.getString("summary"), concepts, quizzes)
        }
        return GuidedPath(revision, root.getString("clinicalNotice"), levels)
    }

    private fun JSONArray.toStrings(): List<String> =
        (0 until length()).map { getString(it) }

    private fun JSONArray.toInts(): List<Int> =
        (0 until length()).map { getInt(it) }
}

/** Local progress only; new remote revisions cannot silently inherit old pass marks. */
class GuidedLearningProgress(context: Context) {
    private val prefs = context.getSharedPreferences("studydock_guided_levels", Context.MODE_PRIVATE)
    private fun key(revision: Int, level: Int): String = "r" + revision + "-l" + level
    fun best(revision: Int, level: Int): Int = prefs.getInt(key(revision, level), 0)
    fun unlocked(revision: Int, level: Int): Boolean =
        level == 1 || (1 until level).all { best(revision, it) >= 80 }
    fun record(revision: Int, level: Int, percent: Int) {
        if (level !in 1..10 || percent !in 0..100) return
        if (percent > best(revision, level)) prefs.edit().putInt(key(revision, level), percent).apply()
    }
}

private val DeepBlue = Color(0xFF102A51)
private val LessonBlue = Color(0xFF0877E8)
private val LessonMuted = Color(0xFF65768C)
private val LessonGreen = Color(0xFF128060)

@Composable
fun GuidedLearningScreen(path: GuidedPath?, message: String, tracker: GuidedLearningProgress, onRefresh: () -> Unit) {
    var activeLevel by remember(path?.revision) { mutableStateOf<Int?>(null) }
    var phase by remember(path?.revision) { mutableStateOf("lesson") }
    var questionIndex by remember(path?.revision) { mutableIntStateOf(0) }
    var picked by remember(path?.revision) { mutableStateOf<Int?>(null) }
    var submitted by remember(path?.revision) { mutableStateOf(false) }
    var earned by remember(path?.revision) { mutableIntStateOf(0) }
    val level = path?.levels?.firstOrNull { it.number == activeLevel }
    if (path == null) {
        Column(Modifier.fillMaxSize().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(40.dp))
            Icon(Icons.Default.CloudDownload, null, Modifier.size(68.dp), tint = LessonBlue)
            Text("StudyDock Learning Path", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Learning lessons and quizzes are delivered directly from the official GitHub repository.",
                color = LessonMuted, textAlign = TextAlign.Center)
            if (message.isNotBlank()) Text(message, color = LessonMuted, textAlign = TextAlign.Center, fontSize = 13.sp)
            Button(onClick = onRefresh) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Retry GitHub connection") }
        }
        return
    }

    if (level == null) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text("Learn in 10 levels", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = DeepBlue)
            Text("Learn the concept → check your understanding → unlock the next level with 80%.",
                color = LessonMuted, fontSize = 13.sp)
            LinearProgressIndicator(
                progress = { path.levels.count { tracker.best(path.revision, it.number) >= 80 } / 10f },
                modifier = Modifier.fillMaxWidth().height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(path.levels, key = { it.number }) { item ->
                    val unlocked = tracker.unlocked(path.revision, item.number)
                    val best = tracker.best(path.revision, item.number)
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE5EDF6))) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (best >= 80) Icons.Default.CheckCircle else if (unlocked)
                                    Icons.Default.School else Icons.Default.Lock, null,
                                    tint = if (unlocked) LessonBlue else LessonMuted)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Level " + item.number + " · " + item.title,
                                        color = DeepBlue, fontWeight = FontWeight.Bold)
                                    Text("3 lessons · 5 concept checks · Best " + best + "%",
                                        color = LessonMuted, fontSize = 12.sp)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(progress = { best / 100f }, modifier = Modifier.fillMaxWidth().height(5.dp))
                            Spacer(Modifier.height(8.dp))
                            Button(enabled = unlocked, onClick = { activeLevel = item.number; phase = "lesson";
                                questionIndex = 0; picked = null; submitted = false; earned = 0 },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(if (!unlocked) "Complete previous level" else if (best >= 80) "Review level" else "Start learning")
                            }
                        }
                    }
                }
            }
        }
        return
    }

    if (phase == "lesson") {
        Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            TextButton(onClick = { activeLevel = null }) { Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text("All levels") }
            Text("LEVEL " + level.number, color = LessonBlue, fontWeight = FontWeight.ExtraBold)
            Text(level.title, color = DeepBlue, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(level.summary, color = LessonMuted, fontSize = 14.sp)
            level.concepts.forEachIndexed { i, concept ->
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE5EDF6))) {
                    Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("STEP " + (i + 1), color = LessonBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(concept.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = DeepBlue)
                        Text(concept.body, fontSize = 15.sp, lineHeight = 23.sp)
                        HorizontalDivider()
                        Text("Remember: " + concept.example, fontSize = 13.sp, color = LessonMuted)
                    }
                }
            }
            Text("StudyDock original learning notes · Source concept pages " + level.sourcePages.joinToString(", "),
                color = LessonMuted, fontSize = 11.sp)
            Button(onClick = { phase = "quiz"; questionIndex = 0; picked = null; submitted = false; earned = 0 },
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                Text("Start 5-question level quiz"); Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ArrowForward, null)
            }
            Spacer(Modifier.height(16.dp))
        }
        return
    }

    if (phase == "quiz") {
        val item = level.quiz[questionIndex]
        Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { phase = "lesson" }) { Icon(Icons.Default.ArrowBack, null); Text("Back to lesson") }
            Text("LEVEL " + level.number + " · QUESTION " + (questionIndex + 1) + " / " + level.quiz.size,
                color = LessonBlue, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { (questionIndex + 1f) / level.quiz.size }, modifier = Modifier.fillMaxWidth().height(7.dp))
            Text(item.prompt, color = DeepBlue, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp)
            item.options.forEachIndexed { i, option ->
                val chosen = picked == i
                val good = submitted && i == item.correctIndex
                val tint = when {
                    good -> LessonGreen
                    chosen && submitted -> Color(0xFFB74747)
                    chosen -> LessonBlue
                    else -> Color(0xFFE0EAF4)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !submitted) { picked = i },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(if (chosen) 2.dp else 1.dp, tint),
                    color = if (good) Color(0xFFEAF7F0) else Color.White
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(('A' + i).toString(), fontWeight = FontWeight.Bold, color = LessonBlue,
                            modifier = Modifier.width(35.dp))
                        Text(option, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        if (good) Icon(Icons.Default.CheckCircle, null, tint = LessonGreen)
                    }
                }
            }
            if (submitted) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFF0F5FC)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (picked == item.correctIndex) "Correct!" else "Review the concept",
                            color = if (picked == item.correctIndex) LessonGreen else DeepBlue, fontWeight = FontWeight.Bold)
                        Text(item.explanation, fontSize = 14.sp, lineHeight = 21.sp)
                    }
                }
                Button(onClick = {
                    if (questionIndex == level.quiz.lastIndex) {
                        tracker.record(path.revision, level.number, earned * 100 / level.quiz.size)
                        phase = "results"
                    } else { questionIndex++; picked = null; submitted = false }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (questionIndex == level.quiz.lastIndex) "See results" else "Next question")
                }
            } else {
                Button(enabled = picked != null,
                    onClick = { if (picked != null) { if (picked == item.correctIndex) earned++; submitted = true } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Check answer") }
            }
        }
        return
    }

    val percentage = earned * 100 / level.quiz.size
    val passed = percentage >= 80
    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(35.dp))
        Icon(if (passed) Icons.Default.EmojiEvents else Icons.Default.AutoStories, null,
            Modifier.size(70.dp), tint = if (passed) LessonGreen else LessonBlue)
        Text(if (passed) "Level mastered!" else "Keep practicing", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        Text(percentage.toString() + "%", fontSize = 51.sp, fontWeight = FontWeight.ExtraBold, color = DeepBlue)
        Text(earned.toString() + " of " + level.quiz.size + " correct · 80% required to unlock",
            color = LessonMuted, textAlign = TextAlign.Center)
        if (!passed) Text("Review the three lesson steps and try again. Your best result is saved.",
            textAlign = TextAlign.Center, color = LessonMuted)
        Button(onClick = { activeLevel = null; phase = "lesson" },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (passed) "Continue learning" else "Return to levels")
        }
        OutlinedButton(onClick = { phase = "lesson"; questionIndex = 0; picked = null; submitted = false; earned = 0 },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Review lesson and retry") }
        Text(path.clinicalNotice, color = LessonMuted, textAlign = TextAlign.Center, fontSize = 11.sp)
    }
}
