package ua.school.windowsdesktop

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private val slideAssets = setOf("index.html", "style.css", "app.js", "editor-shapes.js", "share.js", "create.js", "editor-tools.js", "contact.js")

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun PowerPointScreen(onClose: () -> Unit) {
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback?.onReceiveValue(uri?.let { arrayOf(it) })
        fileCallback = null
    }
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().height(38.dp).background(Color(0xFFB7472A)).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("PowerPoint", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Text("✕", color = Color.White) }
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { context ->
                WebView(context).apply {
                    fun syncViewportHeight() {
                        if (height > 0) {
                            val cssHeight = height / resources.displayMetrics.density
                            evaluateJavascript("document.documentElement.style.setProperty('--native-viewport-height', '${cssHeight}px')", null)
                        }
                    }
                    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncViewportHeight() }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) { syncViewportHeight() }
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                            val uri = request.url
                            if (uri.host != "kosachevlad.github.io" || !uri.path.orEmpty().startsWith("/slides/")) return null
                            val file = uri.path.orEmpty().removePrefix("/slides/").ifEmpty { "index.html" }
                            if (file !in slideAssets) return null
                            val mime = when {
                                file.endsWith(".html") -> "text/html"
                                file.endsWith(".css") -> "text/css"
                                else -> "application/javascript"
                            }
                            return WebResourceResponse(mime, "UTF-8", context.assets.open("slides/$file"))
                        }
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                            if (request.url.host == "kosachevlad.github.io" && request.url.path.orEmpty().startsWith("/slides/")) return false
                            context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                            return true
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(webView: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                            fileCallback?.onReceiveValue(null)
                            fileCallback = callback
                            imagePicker.launch("image/*")
                            return true
                        }
                    }
                    loadUrl("https://kosachevlad.github.io/slides/")
                }
            },
            onRelease = { view -> fileCallback?.onReceiveValue(null); fileCallback = null; view.destroy() },
        )
    }
}
