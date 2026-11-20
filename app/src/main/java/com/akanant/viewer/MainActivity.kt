package com.akanant.viewer

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var currentUri: Uri? = null

    private val prefs by lazy { getSharedPreferences("recent", MODE_PRIVATE) }

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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/doc/", docHandler)
            .build()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true      // remembers reading position, night mode
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(false)          // zoom is handled by the page
            settings.builtInZoomControls = false
            settings.useWideViewPort = true
            addJavascriptInterface(Bridge(), "Native")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)

                // Never navigate away from our own pages.
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

    inner class Bridge {
        @JavascriptInterface
        fun pick() { runOnUiThread { picker.launch(arrayOf("*/*")) } }

        @JavascriptInterface
        fun getRecents(): String {
            val arr = readRecents()
            val out = JSONArray()
            for (i in 0 until arr.length()) out.put(JSONObject().put("n", arr.getJSONObject(i).getString("n")))
            return out.toString()
        }

        @JavascriptInterface
        fun openRecent(i: Int) {
            runOnUiThread {
                val arr = readRecents()
                if (i in 0 until arr.length()) openUri(Uri.parse(arr.getJSONObject(i).getString("u")))
            }
        }
    }

    private fun readRecents(): JSONArray =
        try { JSONArray(prefs.getString("list", "[]")) } catch (_: Exception) { JSONArray() }

    private fun addRecent(uri: Uri, name: String) {
        // Only files we can reopen later (persistable grants from the file picker / Files app).
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { return }
        val old = readRecents()
        val out = JSONArray()
        out.put(JSONObject().put("u", uri.toString()).put("n", name))
        for (i in 0 until old.length()) {
            val o = old.getJSONObject(i)
            if (o.getString("u") != uri.toString() && out.length() < 12) out.put(o)
        }
        prefs.edit().putString("list", out.toString()).apply()
    }

    private fun openUri(uri: Uri) {
        currentUri = uri
        val name = displayName(uri)
        addRecent(uri, name)
        loadPage(name)
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
        val last = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        if (last.contains('.')) return last
        val mime = intent?.type ?: contentResolver.getType(uri)
        val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return if (ext != null) "$last.$ext" else last
    }
}
