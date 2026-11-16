package ua.school.windowsdesktop

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebToolScreen(tool: String, title: String, onClose: () -> Unit) {
    val files = when (tool) {
        "calculator" -> setOf("index.html", "css/style.css", "js/app.js", "js/calculator.js", "assets/favicon.svg")
        "calendar" -> setOf("index.html", "styles.css", "script.js")
        else -> error("Unknown web tool: $tool")
    }
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().height(38.dp).background(Color(0xFF2F5597)).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) { syncViewportHeight() }
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                            val uri = request.url
                            if (uri.host != "appassets.androidplatform.net" || !uri.path.orEmpty().startsWith("/$tool/")) return null
                            val file = uri.path.orEmpty().removePrefix("/$tool/").ifEmpty { "index.html" }
                            if (file !in files) return null
                            val mime = when {
                                file.endsWith(".html") -> "text/html"
                                file.endsWith(".css") -> "text/css"
                                file.endsWith(".svg") -> "image/svg+xml"
                                else -> "application/javascript"
                            }
                            return WebResourceResponse(mime, "UTF-8", context.assets.open("$tool/$file"))
                        }
                    }
                    loadUrl("https://appassets.androidplatform.net/$tool/index.html")
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
