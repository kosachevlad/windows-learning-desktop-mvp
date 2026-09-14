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
import androidx.compose.foundation.horizontalScroll
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

private enum class PaintTool { PENCIL, ERASER, LINE, RECTANGLE, OVAL }
private data class PaintAction(
    val tool: PaintTool,
    val points: List<Offset>,
    val color: Int,
    val width: Float,
)

@Composable
fun PaintScreen(
    repository: LearningFileRepository,
    file: FileNode?,
    nodes: Collection<FileNode>,
    onClose: () -> Unit,
    onError: (String) -> Unit,
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

    BackHandler { requestClose() }
    Column(Modifier.fillMaxSize().background(Color(0xFFF2F2F2)).onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.S) { save(); true } else false
    }) {
        WindowTitle(currentName + if (dirty) " *" else "", ::requestClose)
        Row(Modifier.fillMaxWidth().background(Color.White).horizontalScroll(rememberScrollState()).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolButton(stringResource(R.string.pencil), tool == PaintTool.PENCIL) { tool = PaintTool.PENCIL }
            ToolButton(stringResource(R.string.eraser), tool == PaintTool.ERASER) { tool = PaintTool.ERASER }
            ToolButton(stringResource(R.string.line), tool == PaintTool.LINE) { tool = PaintTool.LINE }
            ToolButton(stringResource(R.string.rectangle), tool == PaintTool.RECTANGLE) { tool = PaintTool.RECTANGLE }
            ToolButton(stringResource(R.string.oval), tool == PaintTool.OVAL) { tool = PaintTool.OVAL }
            TextButton(onClick = { if (actions.isNotEmpty()) { redoActions = redoActions + actions.last(); actions = actions.dropLast(1); dirty = true } }, enabled = actions.isNotEmpty()) { Text(stringResource(R.string.undo)) }
            TextButton(onClick = { if (redoActions.isNotEmpty()) { actions = actions + redoActions.last(); redoActions = redoActions.dropLast(1); dirty = true } }, enabled = redoActions.isNotEmpty()) { Text(stringResource(R.string.redo)) }
            TextButton(onClick = { baseBitmap = null; actions = emptyList(); redoActions = emptyList(); dirty = true }) { Text(stringResource(R.string.clear_canvas)) }
            listOf(3f, 7f, 14f).forEach { width -> TextButton(onClick = { selectedWidth = width }) { Text(width.toInt().toString()) } }
            listOf(Color.Black, Color.Red, Color(0xFF1976D2), Color(0xFF2E7D32), Color(0xFFFFC107)).forEach { color ->
                Box(Modifier.padding(4.dp).size(28.dp).background(color).border(if (selectedColor == color.toArgb()) 3.dp else 1.dp, Color.DarkGray).clickable { selectedColor = color.toArgb() })
            }
            TextButton(onClick = { saveAsName = currentName; saveAsRequested = true }) { Text(stringResource(R.string.save_as)) }
            Button(onClick = { save() }, enabled = loaded && dirty) { Text(stringResource(R.string.save)) }
        }
        if (!loaded) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Canvas(
            Modifier.fillMaxSize().padding(10.dp).background(Color.White).border(1.dp, Color.Gray)
                .onSizeChanged { canvasSize = it }
                .pointerInput(tool, selectedColor, selectedWidth) {
                    detectDragGestures(
                        onDragStart = { point ->
                            val color = if (tool == PaintTool.ERASER) AndroidColor.WHITE else selectedColor
                            actions = actions + PaintAction(tool, listOf(point), color, if (tool == PaintTool.ERASER) selectedWidth * 4 else selectedWidth)
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
            baseBitmap?.let { drawImage(it.asImageBitmap(), dstSize = IntSize(size.width.toInt(), size.height.toInt())) }
            actions.forEach { drawPaintAction(it) }
        }
    }

    if (closeRequested) AlertDialog(
        onDismissRequest = { closeRequested = false }, title = { Text(stringResource(R.string.save_changes_question)) },
        confirmButton = { TextButton(onClick = { save(onClose) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { Row { TextButton(onClick = onClose) { Text(stringResource(R.string.dont_save)) }; TextButton(onClick = { closeRequested = false }) { Text(stringResource(R.string.cancel)) } } },
    )
    if (saveAsRequested) AlertDialog(
        onDismissRequest = { saveAsRequested = false }, title = { Text(stringResource(R.string.save_as)) },
        text = { OutlinedTextField(saveAsName, { saveAsName = it }, singleLine = true, label = { Text(stringResource(R.string.name)) }) },
        confirmButton = { TextButton(onClick = ::submitSaveAs, enabled = saveAsName.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { saveAsRequested = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable private fun ToolButton(label: String, selected: Boolean, action: () -> Unit) =
    TextButton(onClick = action, colors = ButtonDefaults.textButtonColors(containerColor = if (selected) Color(0xFFCDE8FF) else Color.Transparent)) { Text(label) }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPaintAction(action: PaintAction) {
    val color = Color(action.color)
    val start = action.points.firstOrNull() ?: return
    val end = action.points.lastOrNull() ?: return
    when (action.tool) {
        PaintTool.PENCIL, PaintTool.ERASER -> action.points.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, action.width) }
        PaintTool.LINE -> drawLine(color, start, end, action.width)
        PaintTool.RECTANGLE -> drawRect(color, Offset(minOf(start.x, end.x), minOf(start.y, end.y)), Size(kotlin.math.abs(end.x - start.x), kotlin.math.abs(end.y - start.y)), style = Stroke(action.width))
        PaintTool.OVAL -> drawOval(color, Offset(minOf(start.x, end.x), minOf(start.y, end.y)), Size(kotlin.math.abs(end.x - start.x), kotlin.math.abs(end.y - start.y)), style = Stroke(action.width))
    }
}

private fun renderPng(size: IntSize, base: Bitmap?, actions: List<PaintAction>): ByteArray {
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap); canvas.drawColor(AndroidColor.WHITE)
    base?.let { canvas.drawBitmap(it, null, Rect(0, 0, size.width, size.height), null) }
    actions.forEach { action ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = action.color; strokeWidth = action.width; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
        val start = action.points.firstOrNull() ?: return@forEach
        val end = action.points.lastOrNull() ?: return@forEach
        when (action.tool) {
            PaintTool.PENCIL, PaintTool.ERASER -> action.points.zipWithNext().forEach { (a, b) -> canvas.drawLine(a.x, a.y, b.x, b.y, paint) }
            PaintTool.LINE -> canvas.drawLine(start.x, start.y, end.x, end.y, paint)
            PaintTool.RECTANGLE -> canvas.drawRect(RectF(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y)), paint)
            PaintTool.OVAL -> canvas.drawOval(RectF(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y)), paint)
        }
    }
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
