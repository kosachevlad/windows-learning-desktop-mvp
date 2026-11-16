package ua.school.windowsdesktop

import android.annotation.SuppressLint
import android.content.Intent
import android.util.Base64
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    private val onOpen: () -> Unit,
    private val onSaveAs: (String, String) -> Unit,
    private val onClose: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface fun open() { main.post(onOpen) }
    @JavascriptInterface fun saveAs(name: String, base64: String) { main.post { onSaveAs(name, base64) } }
    @JavascriptInterface fun close() { main.post(onClose) }
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
    initialFileId: String? = null,
    onClose: () -> Unit,
    onError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val documentsId = nodes.firstOrNull { it.parentId == FileOperations.ROOT_ID && it.kind == FileKind.FOLDER && it.name == "Документи" }?.id
        ?: FileOperations.ROOT_ID
    var openRequested by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<Pair<String, String>?>(null) }
    var closeRequested by remember { mutableStateOf(false) }
    var closeAfterSave by remember { mutableStateOf(false) }
    var folderId by remember { mutableStateOf(documentsId) }
    var fileName by remember { mutableStateOf("Документ") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val bridge = remember(repository, documentsId) {
        Word2Bridge(repository, documentsId, scope, onError,
            onOpen = { folderId = documentsId; openRequested = true },
            onSaveAs = { name, data -> folderId = documentsId; fileName = name.removeSuffix(".docx"); pendingSave = name to data },
            onClose = { closeRequested = true })
    }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback?.onReceiveValue(uri?.let { arrayOf(it) })
        fileCallback = null
    }
    BackHandler { closeRequested = true }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webView = this
                addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                    (view as WebView).updateWord2ViewportHeight()
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.updateWord2ViewportHeight()
                        val document = nodes.firstOrNull { it.id == initialFileId && it.kind == FileKind.TEXT && it.name.endsWith(".docx", true) }
                        if (document != null) scope.launch {
                            runCatching {
                                val data = Base64.encodeToString(repository.readTextBytes(document.id), Base64.NO_WRAP)
                                view?.evaluateJavascript("window.word2OpenFromApp(${org.json.JSONObject.quote(document.name)},${org.json.JSONObject.quote(data)})", null)
                            }.onFailure { onError(it.message ?: "Не вдалося відкрити документ") }
                        }
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
    val selectedFolder = nodes.firstOrNull { it.id == folderId && it.kind == FileKind.FOLDER }
    val parentId = selectedFolder?.parentId
    val children = nodes.filter { it.parentId == folderId && it.trashedAt == null }
        .sortedWith(compareBy<FileNode> { it.kind != FileKind.FOLDER }.thenBy { it.name.lowercase() })
    if (closeRequested) AlertDialog(
        onDismissRequest = { closeRequested = false },
        title = { Text("Зберегти зміни, внесені в документ?") },
        text = { Text("Виберіть, чи потрібно зберегти документ перед закриттям Word 2.0.") },
        confirmButton = { TextButton(onClick = {
            closeRequested = false
            closeAfterSave = true
            webView?.evaluateJavascript("action('saveDoc')", null)
        }) { Text("Зберегти") } },
        dismissButton = {
            Row {
                TextButton(onClick = { closeRequested = false; onClose() }) { Text("Не зберігати") }
                TextButton(onClick = { closeRequested = false }) { Text("Відміна") }
            }
        },
    )
    if (openRequested || pendingSave != null) AlertDialog(
        onDismissRequest = { openRequested = false; pendingSave = null; closeAfterSave = false },
        title = { Text(if (openRequested) "Відкрити документ" else "Зберегти як") },
        text = {
            Column {
                Text("Папка: ${selectedFolder?.name ?: "Мої файли"}")
                Row {
                    TextButton(onClick = { folderId = FileOperations.ROOT_ID }) { Text("⌂ Мої файли") }
                    TextButton(onClick = { folderId = parentId ?: FileOperations.ROOT_ID }, enabled = parentId != null) { Text("↑ Вгору") }
                }
                Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    children.forEach { child ->
                        if (child.kind == FileKind.FOLDER) {
                            TextButton(onClick = { folderId = child.id }) { Text("📁 ${child.name}") }
                        } else if (openRequested && child.kind == FileKind.TEXT && child.name.endsWith(".docx", true)) {
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        val data = Base64.encodeToString(repository.readTextBytes(child.id), Base64.NO_WRAP)
                                        webView?.evaluateJavascript("window.word2OpenFromApp(${org.json.JSONObject.quote(child.name)},${org.json.JSONObject.quote(data)})", null)
                                        openRequested = false
                                    }.onFailure { onError(it.message ?: "Не вдалося відкрити документ") }
                                }
                            }) { Text("📄 ${child.name}") }
                        } else if (pendingSave != null) Text(child.name, Modifier.padding(10.dp))
                    }
                }
                if (pendingSave != null) OutlinedTextField(fileName, { fileName = it.removeSuffix(".docx") }, label = { Text("Ім’я файлу") }, singleLine = true)
            }
        },
        confirmButton = {
            if (pendingSave != null) TextButton(onClick = {
                val data = pendingSave?.second ?: return@TextButton
                val safeName = fileName.trim().replace(Regex("[<>:\"/\\\\|?*]"), "_").removeSuffix(".docx")
                if (safeName.isBlank()) return@TextButton
                scope.launch {
                    runCatching {
                        val target = "$safeName.docx"
                        val existing = repository.children(folderId)
                        val unique = nextWord2Name(target, existing)
                        repository.createTextBytes(unique, folderId, Base64.decode(data, Base64.DEFAULT))
                        webView?.evaluateJavascript("window.word2SavedInApp(${org.json.JSONObject.quote(unique)})", null)
                        pendingSave = null
                        if (closeAfterSave) { closeAfterSave = false; onClose() }
                    }.onFailure { onError(it.message ?: "Не вдалося зберегти документ") }
                }
            }, enabled = fileName.isNotBlank()) { Text("Зберегти") }
        },
        dismissButton = { TextButton(onClick = { openRequested = false; pendingSave = null; closeAfterSave = false }) { Text("Скасувати") } },
    )
}
