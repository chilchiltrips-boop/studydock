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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
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
                if (url.host == "appassets.androidplatform.net" && url.scheme == "https") return false
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
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    inner class LocalExport {
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
