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

data class NclexOption(val id: String, val text: String)
data class NclexQuestion(
    val id: String, val exam: String, val subject: String, val unit: String,
    val topic: String, val heading: String, val mappingNeedsReview: Boolean,
    val mode: String, val qaStatus: String, val stem: String,
    val options: List<NclexOption>, val draftAnswers: List<String>,
    val draftRationale: String, val sourcePage: String, val sourceId: String,
    val reference: String, val imagePaths: List<String>
)
data class NclexArchive(val questions: List<NclexQuestion>, val imagesRoot: File) {
    val draftCount: Int get() = questions.count { it.mode == "working_rewrite" }
    val historicalCount: Int get() = questions.count { it.mode == "historical_source" }
    fun imageFile(path: String): File? {
        if (!path.matches(Regex("images/[A-Za-z0-9_./-]+\\.(png|jpg|jpeg|webp)", RegexOption.IGNORE_CASE))
            || path.split('/').any { it == "." || it == ".." }) return null
        val file = File(imagesRoot, path)
        return file.takeIf { it.isFile && it.canonicalPath.startsWith(imagesRoot.canonicalPath + File.separator) }
    }
}

/** A completely separate PRIVATE NCLEX archive: it cannot become a scored quiz by import. */
class NclexLibrary(private val context: Context) {
    private val root = File(context.filesDir, "studydock-native/nclex-archive")
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun hash(b: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(b).joinToString("") { "%02x".format(it) }

    fun read(): NclexArchive? =
        if (File(root, "nclex/manifest.json").isFile) parse(root) else null

    private fun parse(dir: File): NclexArchive {
        val m = JSONObject(File(dir, "nclex/manifest.json").readText(Charsets.UTF_8))
        if (m.optInt("schemaVersion") != 1 || m.optString("kind") != "studydock-nclex-private-local"
            || m.optInt("count") != 2894 || m.optInt("clinicalFinalQaPassCount") != 0
            || m.optBoolean("releaseEligible")) throw IOException("Invalid or clinically unapproved NCLEX pack")
        val shards = m.getJSONArray("shards")
        if (shards.length() !in 1..100) throw IOException("Invalid NCLEX shard manifest")
        val records = ArrayList<NclexQuestion>(2894)
        val ids = HashSet<String>()
        for (i in 0 until shards.length()) {
            val spec = shards.getJSONObject(i)
            val path = spec.getString("path")
            if (!path.matches(Regex("nclex/records/[0-9]{3}\\.json"))) throw IOException("Unsafe shard name")
            val jsonFile = File(dir, path)
            if (!jsonFile.isFile) throw IOException("NCLEX shard missing: " + path)
            val rows = JSONArray(jsonFile.readText(Charsets.UTF_8))
            if (rows.length() != spec.getInt("count")) throw IOException("NCLEX shard count mismatch")
            for (j in 0 until rows.length()) {
                val item = rows.getJSONObject(j)
                val id = item.getString("id")
                val mode = item.getString("mode")
                val opts = item.getJSONArray("options")
                val options = (0 until opts.length()).map { k ->
                    val x = opts.getJSONObject(k); NclexOption(x.getString("id"), x.getString("text"))
                }
                val answers = item.getJSONArray("answer").strings()
                if (!ids.add(id) || mode !in listOf("historical_source", "working_rewrite")
                    || options.size !in 2..10 || options.map { it.id }.toSet().size != options.size
                    || !options.map { it.id }.containsAll(answers)
                    || (mode == "historical_source" && answers.isNotEmpty())
                    || item.optBoolean("clinicalSignoff") || item.optBoolean("eligibleForScoredQuiz"))
                    throw IOException("Invalid NCLEX question: " + id)
                records.add(NclexQuestion(
                    id, item.getString("exam"), item.getString("subject"), item.getString("unit"),
                    item.getString("topic"), item.getString("heading"), item.optBoolean("mappingNeedsReview"),
                    mode, item.optString("qaStatus"), item.getString("stem"), options, answers,
                    item.optString("rationale"), item.optString("sourcePage"), item.optString("sourceId"),
                    item.optString("reference"), item.optJSONArray("imagePaths")?.strings().orEmpty()
                ))
            }
        }
        if (records.size != 2894) throw IOException("NCLEX pack must contain exactly 2,894 questions")
        return NclexArchive(records, dir)
    }

    /** Byte/manifest-hash validation and atomic activation; retains previous pack if import fails. */
    fun importZip(uri: Uri): NclexArchive {
        val incoming = File.createTempFile("nclex-", ".zip", context.cacheDir)
        val stage = File(root.parentFile, "nclex-stage-" + System.nanoTime())
        val backup = File(root.parentFile, "nclex-backup-" + System.nanoTime())
        var movedOld = false
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                FileOutputStream(incoming).use { output ->
                    val buf = ByteArray(65536); var size = 0L
                    while (true) {
                        val n = stream.read(buf); if (n < 0) break
                        size += n
                        if (size > 100_000_000L) throw IOException("NCLEX archive too large")
                        output.write(buf, 0, n)
                    }
                }
            } ?: throw IOException("Unable to read selected ZIP")
            ZipFile(incoming).use { zip ->
                val entry = zip.getEntry("nclex/manifest.json") ?: throw IOException("Not an NCLEX import ZIP")
                if (entry.size !in 1..500_000L) throw IOException("Invalid NCLEX manifest length")
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val m = JSONObject(String(bytes, Charsets.UTF_8))
                if (m.optInt("schemaVersion") != 1 || m.optString("kind") != "studydock-nclex-private-local"
                    || m.optInt("count") != 2894 || m.optInt("clinicalFinalQaPassCount") != 0
                    || m.optBoolean("releaseEligible")) throw IOException("Invalid NCLEX pack")
                stage.mkdirs()
                val manifestPath = File(stage, "nclex/manifest.json")
                manifestPath.parentFile?.mkdirs(); manifestPath.writeBytes(bytes)
                val wanted = linkedMapOf<String, String>()
                val shardArray = m.getJSONArray("shards")
                val assetArray = m.getJSONArray("assets")
                if (shardArray.length() !in 1..100 || assetArray.length() > 200) throw IOException("Invalid manifest counts")
                for (array in listOf(shardArray, assetArray)) {
                    for (i in 0 until array.length()) {
                        val spec = array.getJSONObject(i)
                        val path = spec.getString("path")
                        if (!path.matches(Regex("(?:nclex/records/[0-9]{3}\\.json|images/[A-Za-z0-9_./-]+\\.(?:png|jpg|jpeg|webp))"))
                            || path.split('/').any { it == ".." || it == "." }
                            || wanted.put(path, spec.getString("sha256")) != null)
                            throw IOException("Invalid NCLEX entry path")
                    }
                }
                if (wanted.size != shardArray.length() + assetArray.length()) throw IOException("Duplicate paths")
                var expanded = 0L
                for ((path, expectedHash) in wanted) {
                    if (!expectedHash.matches(Regex("[a-f0-9]{64}"))) throw IOException("Invalid checksum")
                    val e = zip.getEntry(path) ?: throw IOException("Missing file: " + path)
                    if (e.isDirectory || e.size !in 1..20_000_000L) throw IOException("Oversized file: " + path)
                    expanded += e.size
                    if (expanded > 120_000_000L) throw IOException("NCLEX unpacked size exceeded")
                    val dst = File(stage, path)
                    if (!dst.canonicalPath.startsWith(stage.canonicalPath + File.separator)) throw IOException("Unsafe ZIP")
                    dst.parentFile?.mkdirs()
                    val digest = MessageDigest.getInstance("SHA-256")
                    var copied = 0L
                    zip.getInputStream(e).use { input -> FileOutputStream(dst).use { output ->
                        val buf = ByteArray(65536)
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            copied += n
                            if (copied > 20_000_000L) throw IOException("Entry exceeds bound")
                            digest.update(buf, 0, n); output.write(buf, 0, n)
                        }
                    }}
                    if (copied != e.size || !digest.digest().joinToString("") { "%02x".format(it) }
                        .equals(expectedHash, true)) throw IOException("NCLEX checksum mismatch: " + path)
                }
                val actual = mutableSetOf<String>()
                val enumeration = zip.entries()
                while (enumeration.hasMoreElements()) {
                    val element = enumeration.nextElement()
                    if (!element.isDirectory && !actual.add(element.name)) throw IOException("Duplicate ZIP entry")
                }
                if (actual != wanted.keys + "nclex/manifest.json") throw IOException("Unexpected files in NCLEX ZIP")
            }
            // Parse before swapping the active archive; source-era answers stay excluded from quizzes.
            parse(stage)
            root.parentFile?.mkdirs()
            if (root.exists()) {
                if (!root.renameTo(backup)) throw IOException("Unable to back up existing archive")
                movedOld = true
            }
            if (!stage.renameTo(root)) {
                if (movedOld) backup.renameTo(root)
                throw IOException("Unable to activate archive")
            }
            if (movedOld) backup.deleteRecursively()
            return parse(root)
        } finally {
            incoming.delete()
            if (stage.exists()) stage.deleteRecursively()
            if (movedOld && backup.exists() && root.exists()) backup.deleteRecursively()
        }
    }
}
