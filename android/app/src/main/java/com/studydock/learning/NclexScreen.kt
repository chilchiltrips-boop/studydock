package com.studydock.learning

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Review-only viewer. Original historical answers are intentionally hidden. */
@Composable
fun NclexScreen(archive: NclexArchive?, status: String, importing: Boolean, onImport: () -> Unit) {
    val blue = Color(0xFF0B3B73)
    var exam by remember { mutableStateOf("All") }
    var mode by remember { mutableStateOf("All") }
    var subject by remember { mutableStateOf("All subjects") }
    var query by remember { mutableStateOf("") }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val subjects = remember(archive) {
        listOf("All subjects") + archive?.questions.orEmpty().map { it.subject }.distinct().sorted()
    }
    val found = remember(archive, exam, mode, subject, query) {
        archive?.questions.orEmpty().filter { q ->
            (exam == "All" || exam == q.exam) && (mode == "All" || mode == q.mode)
                && (subject == "All subjects" || subject == q.subject)
                && (query.isBlank() || listOf(q.id, q.topic, q.heading, q.stem)
                    .any { it.contains(query, ignoreCase = true) })
        }.orEmpty()
    }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("NCLEX · INC Curriculum", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = blue)
        Text("Private working archive. No item is independently clinically signed off. Not a scored quiz or medical protocol.",
            fontSize = 12.sp, color = Color(0xFF925826))
        Button(onClick = onImport, enabled = !importing) {
            Text(if (importing) "Validating NCLEX pack…" else "Import / replace private NCLEX ZIP")
        }
        if (status.isNotBlank()) Text(status, fontSize = 12.sp)
        if (archive == null) {
            Text("Import StudyDock_NCLEX_2894_PRIVATE_Local_Import.zip from this device. The public GitHub repository does not contain the copyrighted books.")
        } else {
            Text(archive.questions.size.toString() + " total · " + archive.draftCount + " draft entries · "
                + archive.historicalCount + " historical source entries · 0 final clinical clearances",
                fontSize = 12.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Search question / ID / topic") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (label in listOf("All", "NCLEX-RN", "NCLEX-PN"))
                    FilterChip(selected = exam == label, onClick = { exam = label }, label = { Text(label, fontSize = 11.sp) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (key in listOf("All", "working_rewrite", "historical_source"))
                    FilterChip(selected = mode == key, onClick = { mode = key },
                        label = { Text(when (key) {
                            "working_rewrite" -> "Drafts"; "historical_source" -> "Historical"; else -> "All"
                        }, fontSize = 11.sp) })
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(subjects) { value ->
                    FilterChip(selected = subject == value, onClick = { subject = value },
                        label = { Text(value, maxLines = 1, fontSize = 11.sp) })
                }
            }
            Text(found.size.toString() + " matching questions", fontSize = 12.sp, color = blue)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp)) {
                items(found, key = { it.id }) { q ->
                    Card(shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().clickable {
                            expandedId = if (expandedId == q.id) null else q.id
                        }.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(q.id + " · " + q.exam + " · " +
                                if (q.mode == "working_rewrite") "DRAFT · QA PENDING" else "HISTORICAL SOURCE",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = blue)
                            Text(q.subject + " › " + q.unit + " › " + q.topic, fontSize = 12.sp)
                            Text(q.heading.ifBlank { q.topic }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(q.stem, fontSize = 14.sp,
                                maxLines = if (expandedId == q.id) Int.MAX_VALUE else 3)
                            if (q.mappingNeedsReview)
                                Text("INC topic mapping pending human review", color = Color(0xFF97601C), fontSize = 11.sp)
                            if (expandedId == q.id) {
                                q.options.forEach { o -> Text(o.id + ". " + o.text, fontSize = 13.sp) }
                                q.imagePaths.forEach { path ->
                                    val imageFile = archive.imageFile(path)
                                    val bitmap = remember(imageFile?.absolutePath) {
                                        if (imageFile == null) null else try {
                                            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                                            BitmapFactory.decodeFile(imageFile.absolutePath, opts)?.asImageBitmap()
                                        } catch (_: Exception) { null }
                                    }
                                    if (bitmap == null) Text("Image unavailable: " + path, fontSize = 11.sp)
                                    else Image(bitmap, "Source image (not independently clinically verified)",
                                        Modifier.fillMaxWidth().heightIn(max = 280.dp), contentScale = ContentScale.Fit)
                                }
                                if (q.mode == "working_rewrite") {
                                    Text("Provisional draft answer: " + q.draftAnswers.joinToString(", "),
                                        fontWeight = FontWeight.Bold, color = Color(0xFF925826))
                                    if (q.draftRationale.isNotBlank()) Text(q.draftRationale, fontSize = 13.sp)
                                    Text("QA: " + q.qaStatus + " · Final clinical signoff NOT obtained", fontSize = 11.sp)
                                    if (q.reference.isNotBlank()) Text("Reference (review scope varies): " + q.reference,
                                        fontSize = 11.sp)
                                } else {
                                    Text("Historical source key withheld. This question does not have a verified current-guidance answer.",
                                        fontSize = 12.sp, color = Color(0xFF925826))
                                }
                            } else Text("Tap to expand", color = blue, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
