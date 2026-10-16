package com.studydock.learning

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import android.widget.Toast
import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.json.JSONObject

/**
 * Android WebView shell for the SAME bundled, offline StudyDock web app.
 * File chooser supports local JSON imports. The small JS bridge writes exported JSON
 * via the Android Storage Access Framework, avoiding fragile blob: downloads.
 * No textbook images or student data leave the device.
 */
class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private var pendingChooser: ValueCallback<Array<Uri>>? = null
    private var pendingExport: String? = null
    private val CHOOSE_FILE = 201
    private val SAVE_FILE = 202
    private val IMPORT_ZIP = 203
    private val imageDir by lazy { File(filesDir, "studydock-images").apply { mkdirs() } }
    private val importDir by lazy { File(filesDir, "studydock-imports").apply { mkdirs() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assetLoader = WebViewAssetLoader.Builder()
            // The first handler serves verified original textbook images stored privately on device.
            .addPathHandler("/v2/assets/images/", WebViewAssetLoader.InternalStoragePathHandler(this, imageDir))
            .addPathHandler("/v2/imports/", WebViewAssetLoader.InternalStoragePathHandler(this, importDir))
            .addPathHandler("/v2/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true
        web.settings.allowFileAccessFromFileURLs = false
        web.settings.allowUniversalAccessFromFileURLs = false
        web.settings.setSupportZoom(false)
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == "appassets.androidplatform.net" && url.scheme == "https" && url.path?.startsWith("/v2/") == true) return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                    true
                } catch (_: ActivityNotFoundException) { true }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                pendingChooser?.onReceiveValue(null)
                pendingChooser = filePathCallback
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "application/json"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                return try { startActivityForResult(Intent.createChooser(intent, "Select textbook JSON"), CHOOSE_FILE); true }
                catch (_: ActivityNotFoundException) { pendingChooser = null; false }
            }
        }
        web.addJavascriptInterface(LocalExport(), "AndroidBridge")
        web.loadUrl("https://appassets.androidplatform.net/v2/index.html")
    }

    inner class LocalExport {
        @JavascriptInterface
        fun chooseTextbookZip() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "application/zip"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                }
                try {
                    startActivityForResult(Intent.createChooser(intent, "Choose StudyDock textbook content ZIP"), IMPORT_ZIP)
                } catch (_: ActivityNotFoundException) {
                    notifyZipError("No ZIP file picker available on this device")
                }
            }
        }

        @JavascriptInterface
        fun saveJson(name: String, body: String) {
            // Only the first-party, bundled offline app can use this bridge.
            val filename = name.replace(Regex("[^a-zA-Z0-9_.-]"), "_").take(90).ifBlank { "studydock-backup.json" }
            runOnUiThread {
                pendingExport = body
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, filename)
                }
                startActivityForResult(intent, SAVE_FILE)
            }
        }
    }

    @Deprecated("Compatibility with the self-contained Android shell")
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        when (requestCode) {
            CHOOSE_FILE -> {
                val uri = if (resultCode == RESULT_OK) resultData?.data else null
                pendingChooser?.onReceiveValue(uri?.let { arrayOf(it) })
                pendingChooser = null
            }
            IMPORT_ZIP -> {
                val uri = if (resultCode == RESULT_OK) resultData?.data else null
                if (uri != null) {
                    runOnUiThread { web.evaluateJavascript("window.studyDockZipStatus?.('Reading and verifying textbook ZIP…')", null) }
                    Thread {
                        try { importTextbookZip(uri) }
                        catch (e: Exception) { notifyZipError(e.message ?: "Unable to import ZIP") }
                    }.start()
                }
            }
            SAVE_FILE -> {
                val json = pendingExport
                pendingExport = null
                if (resultCode == RESULT_OK && json != null && resultData?.data != null) {
                    try {
                        contentResolver.openOutputStream(resultData.data!!)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        Toast.makeText(this, "JSON saved", Toast.LENGTH_SHORT).show()
                    } catch (_: IOException) {
                        Toast.makeText(this, "Unable to save JSON", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun notifyZipError(message: String) {
        runOnUiThread {
            if (::web.isInitialized) {
                web.evaluateJavascript("window.studyDockZipError?.(" + JSONObject.quote(message.take(300)) + ")", null)
            }
        }
    }

    /**
     * Import verified part JSON + original images with bounded memory usage.
     * Images are extracted only from manifest-declared safe paths and SHA-256 checked.
     * Other archive contents (PDFs, evidence exports) stay inside the uploaded ZIP.
     */
    private fun importTextbookZip(uri: Uri) {
        val archive = File.createTempFile("studydock-incoming-", ".zip", cacheDir)
        val stage = File(cacheDir, "studydock-stage-" + System.nanoTime())
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(archive).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 250_000_000) throw IOException("ZIP exceeds 250 MB size limit")
                        output.write(buffer, 0, count)
                    }
                }
            } ?: throw IOException("Unable to read selected ZIP")

            ZipFile(archive).use { zip ->
                val manifests = zip.entries().asSequence().filter { it.name.matches(Regex("part[1-8]\\.json")) }.toList()
                if (manifests.size != 1) throw IOException("ZIP must contain exactly one part1.json … part8.json in its root")
                val manifestFile = manifests.single()
                if (manifestFile.size !in 1..8_000_000) throw IOException("Invalid textbook JSON size")
                val content = zip.getInputStream(manifestFile).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val payload = JSONObject(content)
                if (payload.optInt("schemaVersion") != 1) throw IOException("Unsupported textbook schema")
                val part = payload.optInt("partId", 0)
                if (part !in 1..8 || manifestFile.name != "part$part.json") throw IOException("Part ID and JSON filename differ")
                val images = payload.getJSONArray("images")
                if (images.length() > 4000) throw IOException("Too many images in content manifest")
                stage.mkdirs()
                var totalBytes = 0L
                val buffer = ByteArray(64 * 1024)
                for (i in 0 until images.length()) {
                    val im = images.getJSONObject(i)
                    val name = im.getString("path")
                    if (!name.matches(Regex("assets/images/[a-zA-Z0-9_./-]+\\.(?i:jpg|jpeg|png|webp)")) ||
                        name.split('/').any { it == ".." || it == "." }) {
                        throw IOException("Unsafe image path: " + name.take(80))
                    }
                    val zipEntry = zip.getEntry(name) ?: throw IOException("Missing original image: " + name.take(80))
                    if (zipEntry.isDirectory || zipEntry.size < 0 || zipEntry.size > 20_000_000) throw IOException("Image size is invalid")
                    totalBytes += zipEntry.size
                    if (totalBytes > 250_000_000) throw IOException("Images exceed 250 MB unpacked limit")
                    val relative = name.removePrefix("assets/images/")
                    val target = File(stage, relative)
                    if (!target.canonicalPath.startsWith(stage.canonicalPath + File.separator)) throw IOException("Unsafe archive path")
                    target.parentFile?.mkdirs()
                    val md = MessageDigest.getInstance("SHA-256")
                    var copied = 0L
                    zip.getInputStream(zipEntry).use { input ->
                        FileOutputStream(target).use { output ->
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                copied += count
                                if (copied > 20_000_000) throw IOException("Image too large")
                                md.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    if (copied != zipEntry.size) throw IOException("Image is truncated")
                    val hash = md.digest().joinToString("") { "%02x".format(it) }
                    if (im.has("sha256") && !hash.equals(im.getString("sha256"), true)) throw IOException("Image checksum failed: " + name.take(80))
                }
                // Commit only after ALL images have passed validation.
                stage.walkTopDown().filter { it.isFile }.forEach { source ->
                    val relative = source.relativeTo(stage).path
                    val target = File(imageDir, relative)
                    target.parentFile?.mkdirs()
                    val tmp = File(target.parentFile, target.name + ".incoming")
                    source.copyTo(tmp, overwrite = true)
                    if (target.exists() && !target.delete()) throw IOException("Cannot replace previous image")
                    if (!tmp.renameTo(target)) throw IOException("Cannot save image")
                }
                val filename = "part$part.json"
                val saved = File(importDir, filename)
                FileOutputStream(saved).bufferedWriter(Charsets.UTF_8).use { it.write(content) }
                runOnUiThread {
                    val url = "imports/$filename"
                    web.evaluateJavascript("window.studyDockImportFromZip?.(" + JSONObject.quote(url) + ")", null)
                }
            }
        } finally {
            archive.delete()
            stage.deleteRecursively()
        }
    }

    @Deprecated("Back behavior for app shell")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web.removeJavascriptInterface("AndroidBridge")
        web.destroy()
        super.onDestroy()
    }
}
