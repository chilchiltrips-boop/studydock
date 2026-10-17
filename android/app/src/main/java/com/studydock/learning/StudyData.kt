package com.studydock.learning

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipFile

/** A source-backed local library. No dummy content or network fetches. */
data class StudyChapter(val id: String, val title: String, val subject: String)
data class StudyNote(val id: String, val chapterId: String, val title: String, val body: String)
data class StudyImage(val id: String, val path: String, val sourcePage: Int, val alt: String)
data class StudyOption(val id: String, val text: String)
data class StudyQuestion(
    val id: String, val chapterId: String, val topic: String, val stem: String,
    val options: List<StudyOption>, val correct: String, val images: List<String>,
    val sourcePage: Int, val hint: String, val explanation: String,
    val difficulty: String, val origin: String, val isImage: Boolean,
    val incorrectExplanation: Map<String, String>
)
data class StudyPart(
    val id: Int, val chapters: List<StudyChapter>, val notes: List<StudyNote>,
    val images: Map<String, StudyImage>, val questions: List<StudyQuestion>,
    val unresolved: Int, val multiAnswerReference: Int, val directory: File,
    val imageDirectories: Map<String, File> = emptyMap()
) {
    fun imageFile(id: String): File? = images[id]?.let { File(imageDirectories[id] ?: directory, it.path) }
}

private fun JSONArray.stringItems(): List<String> = (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }

class StudyLibrary(private val context: Context) {
    private val root = File(context.filesDir, "studydock-native/parts").apply { mkdirs() }

    /** Read one part by ID, or merge all installed parts without overwriting earlier content. */
    fun readPart(partId: Int = 0): StudyPart? {
        val ids = if (partId in 1..8) listOf(partId) else (1..8).toList()
        val parts = ids.mapNotNull { id ->
            val dir = File(root, "part$id")
            val manifest = File(dir, "part$id.json")
            if (manifest.isFile) parse(JSONObject(manifest.readText(Charsets.UTF_8)), dir) else null
        }
        if (parts.isEmpty()) return null
        if (parts.size == 1) return parts.first()
        val chapters = linkedMapOf<String, StudyChapter>()
        val notes = linkedMapOf<String, StudyNote>()
        val images = linkedMapOf<String, StudyImage>()
        val questions = linkedMapOf<String, StudyQuestion>()
        val imageDirectories = mutableMapOf<String, File>()
        for (part in parts) {
            for (c in part.chapters) if (!chapters.containsKey(c.id)) chapters[c.id] = c
            for (n in part.notes) {
                if (notes.containsKey(n.id)) throw IOException("Duplicate note ID across parts: ${n.id}")
                notes[n.id] = n
            }
            for ((id, img) in part.images) {
                if (images.containsKey(id)) throw IOException("Duplicate image ID across parts: $id")
                images[id] = img
                imageDirectories[id] = part.directory
            }
            for (q in part.questions) {
                if (questions.containsKey(q.id)) throw IOException("Duplicate question ID across parts: ${q.id}")
                questions[q.id] = q
            }
        }
        return StudyPart(0, chapters.values.toList(), notes.values.toList(), images,
            questions.values.toList(), parts.sumOf { it.unresolved },
            parts.sumOf { it.multiAnswerReference }, parts.first().directory, imageDirectories)
    }

    private fun parse(payload: JSONObject, directory: File): StudyPart {
        val chapters = payload.getJSONArray("chapters").let { arr -> (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            StudyChapter(o.getString("id"), o.optString("title"), o.optString("subject"))
        }}
        val notes = payload.getJSONArray("notes").let { arr -> (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            StudyNote(o.getString("id"), o.getString("chapterId"), o.optString("title"), o.optString("body"))
        }}
        val images = payload.getJSONArray("images").let { arr -> (0 until arr.length()).associate { i ->
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            id to StudyImage(id, o.getString("path"), o.optInt("sourcePage"), o.optString("alt"))
        }}
        val questions = payload.getJSONArray("questions").let { arr -> (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val answers = o.optJSONArray("correct")?.stringItems().orEmpty()
            val options = o.optJSONArray("options")?.let { opt -> (0 until opt.length()).map { j ->
                val v = opt.getJSONObject(j); StudyOption(v.optString("id"), v.optString("label"))
            }}.orEmpty()
            // The native assessment uses ONLY unique 4-option, single-correct, verified originals/adaptations.
            if (answers.size != 1 || options.size != 4 || options.map { it.id }.toSet().size != 4 ||
                answers.first() !in options.map { it.id } || o.optString("status") != "verified") return@mapNotNull null
            val why = o.optJSONObject("incorrectOptionExplanations")
            val descriptions = why?.let { obj -> obj.keys().asSequence().associateWith { key -> obj.optString(key) } }.orEmpty()
            StudyQuestion(
                id = o.getString("id"), chapterId = o.getString("chapterId"), topic = o.optString("topic"),
                stem = o.optString("stem"), options = options, correct = answers.first(),
                images = o.optJSONArray("imageIds")?.stringItems().orEmpty(),
                sourcePage = o.optInt("sourcePage"), hint = o.optString("hint"),
                explanation = o.optString("explanation"), difficulty = o.optString("difficulty", "medium"),
                origin = o.optString("origin"), isImage = o.optString("type") == "image" ||
                    o.optJSONArray("imageIds")?.length()?.let { it > 0 } == true,
                incorrectExplanation = descriptions
            )
        }}
        return StudyPart(
            payload.optInt("partId"), chapters, notes, images, questions,
            payload.optJSONArray("unresolvedOriginalQuestions")?.length() ?: 0,
            payload.optJSONArray("unsupportedMultiAnswerQuestions")?.length() ?: 0,
            directory
        )
    }

    /** Only manifest-declared image assets are accepted; streaming SHA-256 and ZIP size limits. */
    fun importZip(uri: Uri): StudyPart {
        val incoming = File.createTempFile("incoming-", ".zip", context.cacheDir)
        val stage = File(root, "part-new-${System.nanoTime()}")
        var backup: File? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(incoming).use { output ->
                    var total = 0L; val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer); if (n == -1) break
                        total += n
                        if (total > 250_000_000L) throw IOException("ZIP exceeds 250 MB")
                        output.write(buffer, 0, n)
                    }
                }
            } ?: throw IOException("Cannot open selected ZIP")
            ZipFile(incoming).use { zip ->
                val available = (1..8).mapNotNull { i -> zip.getEntry("part$i.json")?.let { i to it } }
                if (available.size != 1) throw IOException("ZIP must include exactly one root partN.json (N=1..8)")
                val (partId, jsonEntry) = available.single()
                if (jsonEntry.size !in 1..8_000_000) throw IOException("Invalid part manifest size")
                val json = zip.getInputStream(jsonEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val data = JSONObject(json)
                val firstPage = data.optInt("firstPage", -1)
                val lastPage = data.optInt("lastPage", -1)
                if (data.optInt("schemaVersion") != 1 || data.optInt("partId") != partId ||
                    firstPage !in 1..1317 || lastPage !in firstPage..1317 ||
                    (partId == 1 && (firstPage != 1 || lastPage != 150)) ||
                    (partId == 2 && (firstPage != 151 || lastPage != 300))) {
                    throw IOException("Invalid StudyDock part number, schema or page range")
                }
                val chapters = data.getJSONArray("chapters")
                val notes = data.getJSONArray("notes")
                val questions = data.getJSONArray("questions")
                val images = data.getJSONArray("images")
                if (chapters.length() > 1000 || notes.length() > 10000 ||
                    questions.length() > 20000 || images.length() > 4000) throw IOException("Content exceeds safety limits")
                val chapterIds = (0 until chapters.length()).map { chapters.getJSONObject(it).getString("id") }.toSet()
                val imgIds = (0 until images.length()).map { images.getJSONObject(it).getString("id") }.toSet()
                if (chapterIds.size != chapters.length() || imgIds.size != images.length()) throw IOException("Duplicate chapter or image IDs")
                val questionIds = HashSet<String>()
                for (i in 0 until questions.length()) {
                    val q = questions.getJSONObject(i)
                    if (!questionIds.add(q.getString("id"))) throw IOException("Duplicate question ID")
                    if (q.getString("chapterId") !in chapterIds) throw IOException("Question has an unknown chapter")
                    q.optJSONArray("imageIds")?.stringItems()?.forEach { if (it !in imgIds) throw IOException("Question image not in manifest") }
                    val correct = q.optJSONArray("correct")?.stringItems().orEmpty()
                    val options = q.getJSONArray("options")
                    if (correct.size != 1 || options.length() != 4) throw IOException("Non-single-answer question in scored bank")
                    val ids = (0 until options.length()).map { options.getJSONObject(it).getString("id") }
                    if (correct[0] !in ids || ids.toSet().size != 4) throw IOException("Question answer/options invalid")
                    if (q.optString("status") != "verified") throw IOException("Non-verified question in scored bank")
                }
                stage.mkdirs()
                var totalUnpacked = 0L
                val buffer = ByteArray(64 * 1024)
                for (i in 0 until images.length()) {
                    val record = images.getJSONObject(i)
                    val path = record.getString("path")
                    if (!Regex("assets/images/[A-Za-z0-9_./-]+\\.(jpg|jpeg|png|webp)", RegexOption.IGNORE_CASE).matches(path) ||
                        path.split('/').any { it == ".." || it == "." }) throw IOException("Unsafe image path")
                    val entry = zip.getEntry(path) ?: throw IOException("Missing image: $path")
                    if (entry.isDirectory || entry.size < 0 || entry.size > 20_000_000L) throw IOException("Image size invalid")
                    totalUnpacked += entry.size
                    if (totalUnpacked > 300_000_000L) throw IOException("Unpacked images exceed 300 MB")
                    val dst = File(stage, path)
                    if (!dst.canonicalPath.startsWith(stage.canonicalPath + File.separator)) throw IOException("Unsafe image destination")
                    dst.parentFile?.mkdirs()
                    val digest = MessageDigest.getInstance("SHA-256")
                    var bytes = 0L
                    zip.getInputStream(entry).use { input -> FileOutputStream(dst).use { output ->
                        while (true) {
                            val n = input.read(buffer); if (n == -1) break
                            bytes += n
                            if (bytes > 20_000_000L) throw IOException("Image exceeds limit")
                            digest.update(buffer, 0, n)
                            output.write(buffer, 0, n)
                        }
                    }}
                    if (bytes != entry.size) throw IOException("Truncated image: $path")
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (record.optString("sha256").length != 64 ||
                        !actual.equals(record.getString("sha256"), ignoreCase = true)) throw IOException("Image hash failed: $path")
                }
                File(stage, "part$partId.json").writeText(json, Charsets.UTF_8)
                // Parse + validate all references before changing the active library.
                val result = parse(data, stage)
                if (result.questions.size != questions.length()) throw IOException("Some scored questions failed validation")
                // Prevent content from an earlier installed part being replaced or shadowed.
                for (id in 1..8) if (id != partId) {
                    val previous = readPart(id) ?: continue
                    if (result.questions.any { q -> previous.questions.any { it.id == q.id } } ||
                        result.notes.any { n -> previous.notes.any { it.id == n.id } } ||
                        result.images.keys.any { it in previous.images })
                        throw IOException("Question, note or image IDs conflict with installed Part $id")
                }
                val current = File(root, "part$partId")
                if (current.exists()) {
                    backup = File(root, "part$partId-old-${System.nanoTime()}")
                    if (!current.renameTo(backup)) throw IOException("Cannot preserve previous content")
                }
                if (!stage.renameTo(current)) {
                    backup?.renameTo(current)
                    throw IOException("Cannot activate new content")
                }
                backup?.deleteRecursively()
                return readPart() ?: parse(data, current)
            }
        } finally {
            incoming.delete()
            if (stage.exists()) stage.deleteRecursively()
        }
    }
}

class StudyProgress(context: Context) {
    private val prefs = context.getSharedPreferences("study_progress", Context.MODE_PRIVATE)
    fun attempts(): Int = prefs.getInt("attempted", 0)
    fun correct(): Int = prefs.getInt("correct", 0)
    fun masteredIds(): Set<String> = prefs.getStringSet("masteredQuestionIds", emptySet()).orEmpty().toSet()
    fun incorrectIds(): Set<String> = prefs.getStringSet("incorrectIds", emptySet()).orEmpty().toSet()
    fun record(question: StudyQuestion, answer: String) {
        val wrong = incorrectIds().toMutableSet()
        val mastered = masteredIds().toMutableSet()
        if (answer == question.correct) {
            wrong.remove(question.id)
            mastered.add(question.id)
        } else {
            wrong.add(question.id)
        }
        prefs.edit().putInt("attempted", attempts() + 1)
            .putInt("correct", correct() + if (answer == question.correct) 1 else 0)
            .putStringSet("masteredQuestionIds", mastered)
            .putStringSet("incorrectIds", wrong).apply()
    }
}
