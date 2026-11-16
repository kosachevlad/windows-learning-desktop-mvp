package ua.school.windowsdesktop

import android.annotation.SuppressLint
import android.content.Intent
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ua.school.windowsdesktop.data.LearningFileRepository
import ua.school.windowsdesktop.domain.FileKind
import ua.school.windowsdesktop.domain.FileNode
import ua.school.windowsdesktop.domain.FileOperations

private class Word2Bridge(
    private val repository: LearningFileRepository,
    private val documentsId: String,
    private val scope: CoroutineScope,
    private val onError: (String) -> Unit,
) {
    @JavascriptInterface
    fun save(name: String, base64: String) {
        scope.launch {
            try {
                val safeName = name.substringAfterLast('/').substringAfterLast('\\').take(200)
                require(safeName.endsWith(".docx", ignoreCase = true))
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val existing = repository.children(documentsId)
                val uniqueName = nextWord2Name(safeName, existing)
                repository.createTextBytes(uniqueName, documentsId, bytes)
            } catch (failure: Exception) {
                onError("Не вдалося зберегти документ Word 2.0: ${failure.message.orEmpty()}")
            }
        }
    }
}

private fun nextWord2Name(name: String, existing: List<FileNode>): String {
    val stem = name.dropLast(5)
    var candidate = name
    var number = 1
    while (existing.any { it.name.equals(candidate, ignoreCase = true) }) {
        candidate = "$stem ($number).docx"
        number++
    }
    return candidate
}

private fun WebView.updateWord2ViewportHeight() {
    if (height <= 0) return
    val cssHeight = height / resources.displayMetrics.density
    evaluateJavascript(
        "document.documentElement.style.setProperty('--native-viewport-height', '${cssHeight}px')",
        null,
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun Word2Screen(
    repository: LearningFileRepository,
    nodes: List<FileNode>,
    onClose: () -> Unit,
    onError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val documentsId = nodes.firstOrNull { it.parentId == FileOperations.ROOT_ID && it.kind == FileKind.FOLDER && it.name == "Документи" }?.id
        ?: FileOperations.ROOT_ID
    val bridge = remember(repository, documentsId) { Word2Bridge(repository, documentsId, scope, onError) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback?.onReceiveValue(uri?.let { arrayOf(it) })
        fileCallback = null
    }
    BackHandler(onBack = onClose)
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                    (view as WebView).updateWord2ViewportHeight()
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.updateWord2ViewportHeight()
                    }
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                        val uri = request.url
                        if (uri.host != "kosachevlad.github.io" || !uri.path.orEmpty().startsWith("/word/")) return null
                        val file = uri.path.orEmpty().removePrefix("/word/").ifEmpty { "index.html" }
                        val mime = when (file) {
                            "index.html" -> "text/html"
                            "style.css" -> "text/css"
                            "editor.js", "docx.js", "sharing.js" -> "application/javascript"
                            else -> return null
                        }
                        return WebResourceResponse(mime, "UTF-8", context.assets.open("word2/$file"))
                    }
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                        if (request.url.host == "kosachevlad.github.io" && request.url.path.orEmpty().startsWith("/word/")) return false
                        context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                        return true
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(webView: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                        fileCallback?.onReceiveValue(null)
                        fileCallback = callback
                        picker.launch(params?.acceptTypes?.firstOrNull()?.takeIf { it.contains('/') } ?: "*/*")
                        return true
                    }
                }
                addJavascriptInterface(bridge, "AndroidWord2")
                loadUrl("https://kosachevlad.github.io/word/")
            }
        },
        onRelease = { view -> view.removeJavascriptInterface("AndroidWord2"); view.destroy() },
    )
}
