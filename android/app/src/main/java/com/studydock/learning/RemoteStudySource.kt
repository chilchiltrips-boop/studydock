package com.studydock.learning

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/** Online-first GitHub release distribution, verified before activation. */
class RemoteStudySource(private val context: Context, private val library: StudyLibrary) {
    private val catalogUrl = "https://raw.githubusercontent.com/chilchiltrips-boop/studydock/main/content/catalog.json"
    private val releasePrefix = "https://github.com/chilchiltrips-boop/studydock/releases/download/"
    private val prefs = context.getSharedPreferences("study_remote_catalog", Context.MODE_PRIVATE)

    data class Result(val part: StudyPart?, val message: String)

    fun sync(): Result {
        val catalog = JSONObject(String(readHttps(catalogUrl, 128_000), Charsets.UTF_8))
        if (catalog.optInt("schemaVersion") != 1) throw IOException("Unsupported GitHub catalog schema")
        val parts = catalog.optJSONArray("parts") ?: throw IOException("GitHub catalog missing parts")
        if (parts.length() > 8) throw IOException("Too many remote parts")
        val item = (0 until parts.length()).map { parts.getJSONObject(it) }.firstOrNull { it.optInt("partId") == 1 }
            ?: return Result(library.readPart(), "GitHub connected. Part 1 has not been published.")
        val url = item.getString("archiveUrl")
        val digest = item.getString("sha256").lowercase()
        if (!url.startsWith(releasePrefix) || !url.matches(Regex("https://github\\.com/chilchiltrips-boop/studydock/releases/download/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+\\.zip")))
            throw IOException("Untrusted GitHub release URL")
        if (!digest.matches(Regex("[a-f0-9]{64}"))) throw IOException("Invalid archive SHA-256")
        val installed = library.readPart()
        if (installed != null && prefs.getString("installedPart1Sha256", null) == digest)
            return Result(installed, "Part 1 is up to date with GitHub.")
        val archive = File.createTempFile("studydock-github-", ".zip", context.cacheDir)
        try {
            if (downloadRelease(url, archive, 250_000_000L) != digest)
                throw IOException("Remote SHA-256 mismatch; previous content retained.")
            val part = library.importZip(Uri.fromFile(archive))
            prefs.edit().putString("installedPart1Sha256", digest).apply()
            return Result(part, "GitHub content verified: " + part.questions.size + " questions, " + part.images.size + " images.")
        } finally { archive.delete() }
    }

    private fun readHttps(url: String, limit: Int): ByteArray {
        val c = URL(url).openConnection() as HttpsURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.instanceFollowRedirects = false
        c.setRequestProperty("Accept", "application/json")
        try {
            if (c.responseCode != HttpURLConnection.HTTP_OK) throw IOException("GitHub catalog HTTP " + c.responseCode)
            val out = java.io.ByteArrayOutputStream()
            c.inputStream.use { stream ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    if (out.size() + n > limit) throw IOException("Catalog too large")
                    out.write(buf, 0, n)
                }
            }
            return out.toByteArray()
        } finally { c.disconnect() }
    }

    private fun downloadRelease(url: String, target: File, limit: Long): String {
        var location = url
        repeat(6) {
            val next = URL(location).openConnection() as HttpsURLConnection
            next.instanceFollowRedirects = false
            next.connectTimeout = 20_000
            next.readTimeout = 60_000
            next.setRequestProperty("Accept", "application/octet-stream")
            val status = next.responseCode
            if (status in listOf(301, 302, 303, 307, 308)) {
                val redirect = next.getHeaderField("Location") ?: throw IOException("GitHub redirect missing")
                val resolved = URL(URL(location), redirect)
                next.disconnect()
                if (resolved.protocol != "https" || resolved.host !in setOf(
                        "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com",
                        "github-releases.githubusercontent.com")) throw IOException("Untrusted asset redirect")
                location = resolved.toString()
                return@repeat
            }
            if (status != HttpURLConnection.HTTP_OK) {
                next.disconnect()
                throw IOException("GitHub Release HTTP " + status)
            }
            val hash = MessageDigest.getInstance("SHA-256")
            var total = 0L
            try {
                next.inputStream.use { input ->
                    FileOutputStream(target).use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > limit) throw IOException("ZIP exceeds 250 MB")
                            hash.update(buf, 0, n)
                            output.write(buf, 0, n)
                        }
                    }
                }
            } finally { next.disconnect() }
            if (total == 0L) throw IOException("Empty remote ZIP")
            return hash.digest().joinToString("") { "%02x".format(it) }
        }
        throw IOException("Too many GitHub redirects")
    }
}
