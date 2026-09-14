package ua.school.windowsdesktop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch
import ua.school.windowsdesktop.data.LearningFileRepository
import ua.school.windowsdesktop.domain.FileNode
import ua.school.windowsdesktop.domain.FileOperations

private enum class PaintTool { PENCIL, BRUSH, ERASER, FILL, LINE, RECTANGLE, OVAL, TEXT }
private data class PaintAction(
    val tool: PaintTool,
    val points: List<Offset>,
    val color: Int,
    val width: Float,
    val text: String = "",
)

@Composable
fun PaintScreen(
    repository: LearningFileRepository,
    file: FileNode?,
    nodes: Collection<FileNode>,
    onClose: () -> Unit,
    onError: (String) -> Unit,
    showFileExtensions: Boolean,
) {
    val scope = rememberCoroutineScope()
    var currentId by remember(file?.id) { mutableStateOf(file?.id) }
    var currentName by remember(file?.id) { mutableStateOf(file?.name ?: nextDrawingName(nodes)) }
    var baseBitmap by remember(file?.id) { mutableStateOf<Bitmap?>(null) }
    var actions by remember(file?.id) { mutableStateOf(emptyList<PaintAction>()) }
    var redoActions by remember(file?.id) { mutableStateOf(emptyList<PaintAction>()) }
    var tool by remember { mutableStateOf(PaintTool.PENCIL) }
    var selectedColor by remember { mutableIntStateOf(AndroidColor.BLACK) }
    var selectedWidth by remember { mutableFloatStateOf(5f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var loaded by remember(file?.id) { mutableStateOf(file == null) }
    var dirty by remember(file?.id) { mutableStateOf(false) }
    var closeRequested by remember { mutableStateOf(false) }
    var saveAsRequested by remember { mutableStateOf(false) }
    var saveAsName by remember { mutableStateOf("") }
    var textPosition by remember { mutableStateOf<Offset?>(null) }
    var enteredText by remember { mutableStateOf("") }
    var fileMenu by remember { mutableStateOf(false) }
    var editMenu by remember { mutableStateOf(false) }
    var viewMenu by remember { mutableStateOf(false) }

    LaunchedEffect(file?.id) {
        if (file != null) try {
            val bytes = repository.readPaint(file.id)
            baseBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            loaded = true
        } catch (failure: Exception) {
            onError(failure.message ?: "Не вдалося відкрити малюнок")
            onClose()
        }
    }

    fun png(): ByteArray = renderPng(canvasSize, baseBitmap, actions)
    fun save(after: () -> Unit = {}) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return
        val bytes = png()
        scope.launch { try {
            val saved = currentId?.let { repository.writePaint(it, bytes) }
                ?: repository.createPaint(currentName, file?.parentId ?: FileOperations.ROOT_ID, bytes)
            currentId = saved.id; currentName = saved.name
            baseBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            actions = emptyList(); redoActions = emptyList(); dirty = false; after()
        } catch (failure: Exception) { onError(failure.message ?: "Не вдалося зберегти малюнок") } }
    }
    fun submitSaveAs() {
        if (saveAsName.isBlank() || canvasSize == IntSize.Zero) return
        val bytes = png()
        scope.launch { try {
            val saved = repository.createPaint(saveAsName, file?.parentId ?: FileOperations.ROOT_ID, bytes)
            currentId = saved.id; currentName = saved.name
            baseBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            actions = emptyList(); redoActions = emptyList(); dirty = false; saveAsRequested = false
        } catch (failure: Exception) { onError(failure.message ?: "Не вдалося зберегти малюнок") } }
    }
    fun requestClose() { if (dirty) closeRequested = true else onClose() }
    fun undo() { if (actions.isNotEmpty()) { redoActions = redoActions + actions.last(); actions = actions.dropLast(1); dirty = true } }
    fun redo() { if (redoActions.isNotEmpty()) { actions = actions + redoActions.last(); redoActions = redoActions.dropLast(1); dirty = true } }
    fun clear() { baseBitmap = null; actions = emptyList(); redoActions = emptyList(); dirty = true }

    BackHandler { requestClose() }
    Column(Modifier.fillMaxSize().background(Color(0xFFF2F2F2)).onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.S) { save(); true } else false
    }) {
        WindowTitle(displayFileName(currentName, ua.school.windowsdesktop.domain.FileKind.PAINT, showFileExtensions) + if (dirty) " *" else "", ::requestClose)
        Row(Modifier.fillMaxWidth().background(Color(0xFFF5F5F5))) {
            Box { TextButton(onClick = { fileMenu = true }) { Text(stringResource(R.string.file_menu)) }
                DropdownMenu(fileMenu, { fileMenu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.save)) }, { fileMenu = false; save() }, enabled = loaded && dirty)
                    DropdownMenuItem({ Text(stringResource(R.string.save_as)) }, { fileMenu = false; saveAsName = displayFileName(currentName, ua.school.windowsdesktop.domain.FileKind.PAINT, showFileExtensions); saveAsRequested = true })
                    DropdownMenuItem({ Text(stringResource(R.string.exit)) }, { fileMenu = false; requestClose() })
                }
            }
            Box { TextButton(onClick = { editMenu = true }) { Text(stringResource(R.string.edit_menu)) }
                DropdownMenu(editMenu, { editMenu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.undo)) }, { editMenu = false; undo() }, enabled = actions.isNotEmpty())
                    DropdownMenuItem({ Text(stringResource(R.string.redo)) }, { editMenu = false; redo() }, enabled = redoActions.isNotEmpty())
                    DropdownMenuItem({ Text(stringResource(R.string.clear_canvas)) }, { editMenu = false; clear() })
                }
            }
            Box { TextButton(onClick = { viewMenu = true }) { Text(stringResource(R.string.view_menu)) }
                DropdownMenu(viewMenu, { viewMenu = false }) {
                    listOf(3f, 7f, 14f).forEach { width -> DropdownMenuItem({ Text("${stringResource(R.string.thickness)}: ${width.toInt()}") }, { selectedWidth = width; viewMenu = false }) }
                }
            }
        }
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxHeight().width(52.dp).background(Color(0xFFE7E7E7)).verticalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                listOf(Color.Black, Color.Red, Color(0xFF1976D2), Color(0xFF2E7D32), Color(0xFFFFC107), Color(0xFF7B1FA2), Color(0xFFFF7A00), Color(0xFFFF69B4), Color(0xFF795548), Color(0xFF81D4FA)).forEach { color ->
                    Box(Modifier.padding(3.dp).size(32.dp).background(color).border(if (selectedColor == color.toArgb()) 3.dp else 1.dp, Color.DarkGray).clickable { selectedColor = color.toArgb() })
                }
            }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(Color.White).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ToolButton(stringResource(R.string.pencil), tool == PaintTool.PENCIL) { tool = PaintTool.PENCIL }
                    ToolButton(stringResource(R.string.brush), tool == PaintTool.BRUSH) { tool = PaintTool.BRUSH }
                    ToolButton(stringResource(R.string.eraser), tool == PaintTool.ERASER) { tool = PaintTool.ERASER }
                    ToolButton(stringResource(R.string.fill), tool == PaintTool.FILL) { tool = PaintTool.FILL }
                    ToolButton(stringResource(R.string.line), tool == PaintTool.LINE) { tool = PaintTool.LINE }
                    ToolButton(stringResource(R.string.rectangle), tool == PaintTool.RECTANGLE) { tool = PaintTool.RECTANGLE }
                    ToolButton(stringResource(R.string.oval), tool == PaintTool.OVAL) { tool = PaintTool.OVAL }
                    ToolButton(stringResource(R.string.text_tool), tool == PaintTool.TEXT) { tool = PaintTool.TEXT }
                }
                if (!loaded) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else {
                val previewBitmap = remember(canvasSize, baseBitmap, actions) { if (canvasSize.width > 0 && canvasSize.height > 0) renderBitmap(canvasSize, baseBitmap, actions) else null }
                Canvas(Modifier.fillMaxSize().padding(10.dp).background(Color.White).border(1.dp, Color.Gray)
                .onSizeChanged { canvasSize = it }
                .pointerInput(tool, selectedColor, selectedWidth) {
                    if (tool == PaintTool.FILL || tool == PaintTool.TEXT) detectTapGestures { point ->
                        if (tool == PaintTool.FILL) {
                            actions = actions + PaintAction(PaintTool.FILL, listOf(point), selectedColor, selectedWidth)
                            redoActions = emptyList(); dirty = true
                        } else {
                            textPosition = point; enteredText = ""
                        }
                    } else detectDragGestures(
                        onDragStart = { point ->
                            val color = if (tool == PaintTool.ERASER) AndroidColor.WHITE else selectedColor
                            val width = when (tool) { PaintTool.ERASER -> selectedWidth * 4; PaintTool.BRUSH -> selectedWidth * 2; else -> selectedWidth }
                            actions = actions + PaintAction(tool, listOf(point), color, width)
                            redoActions = emptyList(); dirty = true
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val last = actions.lastOrNull() ?: return@detectDragGestures
                            actions = actions.dropLast(1) + last.copy(points = last.points + change.position)
                        },
                    )
                }
        ) {
            previewBitmap?.let { drawImage(it.asImageBitmap(), dstSize = canvasSize) }
        }
        }
            }
        }
    }

    if (closeRequested) AlertDialog(
        onDismissRequest = { closeRequested = false }, title = { Text(stringResource(R.string.save_changes_question)) },
        confirmButton = { TextButton(onClick = { save(onClose) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { Row { TextButton(onClick = onClose) { Text(stringResource(R.string.dont_save)) }; TextButton(onClick = { closeRequested = false }) { Text(stringResource(R.string.cancel)) } } },
    )
    if (saveAsRequested) AlertDialog(
        modifier = Modifier.dialogKeys(saveAsName.isNotBlank(), ::submitSaveAs) { saveAsRequested = false },
        onDismissRequest = { saveAsRequested = false }, title = { Text(stringResource(R.string.save_as)) },
        text = { OutlinedTextField(saveAsName, { saveAsName = it }, singleLine = true, label = { Text(stringResource(R.string.name)) }) },
        confirmButton = { TextButton(onClick = ::submitSaveAs, enabled = saveAsName.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { saveAsRequested = false }) { Text(stringResource(R.string.cancel)) } },
    )
    textPosition?.let { position -> AlertDialog(
        modifier = Modifier.dialogKeys(enteredText.isNotBlank(), {
            actions = actions + PaintAction(PaintTool.TEXT, listOf(position), selectedColor, selectedWidth, enteredText)
            redoActions = emptyList(); dirty = true; textPosition = null
        }, { textPosition = null }),
        onDismissRequest = { textPosition = null },
        title = { Text(stringResource(R.string.enter_text)) },
        text = { OutlinedTextField(enteredText, { enteredText = it }, singleLine = true) },
        confirmButton = { TextButton(enabled = enteredText.isNotBlank(), onClick = {
            actions = actions + PaintAction(PaintTool.TEXT, listOf(position), selectedColor, selectedWidth, enteredText)
            redoActions = emptyList(); dirty = true; textPosition = null
        }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = { textPosition = null }) { Text(stringResource(R.string.cancel)) } },
    ) }
}

@Composable private fun ToolButton(label: String, selected: Boolean, action: () -> Unit) =
    TextButton(onClick = action, colors = ButtonDefaults.textButtonColors(containerColor = if (selected) Color(0xFFCDE8FF) else Color.Transparent)) { Text(label) }

private fun renderPng(size: IntSize, base: Bitmap?, actions: List<PaintAction>): ByteArray {
    val bitmap = renderBitmap(size, base, actions)
    return ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
}

private fun renderBitmap(size: IntSize, base: Bitmap?, actions: List<PaintAction>): Bitmap {
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap); canvas.drawColor(AndroidColor.WHITE)
    base?.let { canvas.drawBitmap(it, null, Rect(0, 0, size.width, size.height), null) }
    actions.forEach { action ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = action.color; strokeWidth = action.width; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
        val start = action.points.firstOrNull() ?: return@forEach
        val end = action.points.lastOrNull() ?: return@forEach
        when (action.tool) {
            PaintTool.PENCIL, PaintTool.BRUSH, PaintTool.ERASER -> action.points.zipWithNext().forEach { (a, b) -> canvas.drawLine(a.x, a.y, b.x, b.y, paint) }
            PaintTool.FILL -> floodFill(bitmap, start.x.toInt(), start.y.toInt(), action.color)
            PaintTool.LINE -> canvas.drawLine(start.x, start.y, end.x, end.y, paint)
            PaintTool.RECTANGLE -> canvas.drawRect(RectF(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y)), paint)
            PaintTool.OVAL -> canvas.drawOval(RectF(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y)), paint)
            PaintTool.TEXT -> { paint.style = Paint.Style.FILL; paint.textSize = (action.width * 5).coerceAtLeast(18f); canvas.drawText(action.text, start.x, start.y, paint) }
        }
    }
    return bitmap
}

private fun floodFill(bitmap: Bitmap, startX: Int, startY: Int, replacement: Int) {
    if (startX !in 0 until bitmap.width || startY !in 0 until bitmap.height) return
    val target = bitmap.getPixel(startX, startY)
    if (target == replacement) return
    val queue = IntArray(bitmap.width * bitmap.height)
    var head = 0; var tail = 0
    fun enqueue(x: Int, y: Int) {
        if (x in 0 until bitmap.width && y in 0 until bitmap.height && bitmap.getPixel(x, y) == target) {
            bitmap.setPixel(x, y, replacement)
            queue[tail++] = y * bitmap.width + x
        }
    }
    enqueue(startX, startY)
    while (head < tail) {
        val value = queue[head++]
        val x = value % bitmap.width; val y = value / bitmap.width
        enqueue(x + 1, y); enqueue(x - 1, y); enqueue(x, y + 1); enqueue(x, y - 1)
    }
}

internal fun blankPaintPng(): ByteArray {
    val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(AndroidColor.WHITE) }
    return ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
}

private fun nextDrawingName(nodes: Collection<FileNode>): String {
    val names = nodes.filter { it.parentId == FileOperations.ROOT_ID && it.trashedAt == null }.map { it.name.lowercase() }.toSet()
    var number = 1
    while (true) {
        val candidate = if (number == 1) "Новий малюнок.png" else "Новий малюнок ($number).png"
        if (candidate.lowercase() !in names) return candidate
        number++
    }
}
