package com.akanant.viewer

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var currentUri: Uri? = null
    private var currentName: String = ""

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openUri(uri)
    }

    private val docHandler = WebViewAssetLoader.PathHandler { _ ->
        val uri = currentUri ?: return@PathHandler null
        try {
            val stream = contentResolver.openInputStream(uri) ?: return@PathHandler null
            WebResourceResponse("application/octet-stream", null, stream)
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/doc/", docHandler)
            .build()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            addJavascriptInterface(object {
                @JavascriptInterface
                fun pick() { runOnUiThread { picker.launch(arrayOf("*/*")) } }
            }, "Native")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)

                // Keep everything inside the app; never navigate away from our own pages.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
            }
        }
        setContentView(webView)

        val incoming = intent?.data
        if (incoming != null) openUri(incoming) else loadPage("")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { openUri(it) }
    }

    private fun openUri(uri: Uri) {
        currentUri = uri
        currentName = displayName(uri)
        loadPage(currentName)
    }

    private fun loadPage(name: String) {
        val q = if (name.isEmpty()) "" else "?name=${Uri.encode(name)}&t=${System.currentTimeMillis()}"
        webView.loadUrl("https://appassets.androidplatform.net/assets/viewer.html$q")
    }

    private fun displayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) return n
                }
            }
        } catch (_: Exception) {}
        val last = uri.lastPathSegment ?: "file"
        if (last.contains('.')) return last.substringAfterLast('/')
        // No usable name: derive an extension from the MIME type so the page can route it.
        val mime = intent?.type ?: contentResolver.getType(uri)
        val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return if (ext != null) "$last.$ext" else last
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { super.onBackPressed() }
}
