package ua.school.windowsdesktop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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

private enum class PaintTool { PENCIL, ERASER }
private data class Stroke(val points: List<Offset>, val eraser: Boolean)

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
    var strokes by remember(file?.id) { mutableStateOf(emptyList<Stroke>()) }
    var tool by remember { mutableStateOf(PaintTool.PENCIL) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var loaded by remember(file?.id) { mutableStateOf(file == null) }

    LaunchedEffect(file?.id) {
        if (file != null) try {
            baseBitmap = BitmapFactory.decodeByteArray(repository.readPaint(file.id), 0, file.sizeBytes.toInt())
            loaded = true
        } catch (failure: Exception) {
            onError(failure.message ?: "Не вдалося відкрити малюнок")
            onClose()
        }
    }

    fun save() {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return
        val png = renderPng(canvasSize, baseBitmap, strokes)
        scope.launch { try {
            val saved = currentId?.let { repository.writePaint(it, png) }
                ?: repository.createPaint(currentName, file?.parentId ?: FileOperations.ROOT_ID, png)
            currentId = saved.id
            currentName = saved.name
            baseBitmap = BitmapFactory.decodeByteArray(png, 0, png.size)
            strokes = emptyList()
        } catch (failure: Exception) { onError(failure.message ?: "Не вдалося зберегти малюнок") } }
    }

    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(Color(0xFFF2F2F2))) {
        WindowTitle(currentName, onClose)
        Row(Modifier.fillMaxWidth().background(Color.White).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { tool = PaintTool.PENCIL }) { Text(stringResource(R.string.pencil)) }
            Spacer(Modifier.width(6.dp))
            Button(onClick = { tool = PaintTool.ERASER }) { Text(stringResource(R.string.eraser)) }
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = { baseBitmap = null; strokes = emptyList() }) { Text(stringResource(R.string.clear_canvas)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = ::save, enabled = loaded) { Text(stringResource(R.string.save)) }
        }
        if (!loaded) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Canvas(
            Modifier.fillMaxSize().padding(10.dp).background(Color.White).border(1.dp, Color.Gray)
                .onSizeChanged { canvasSize = it }
                .pointerInput(tool) {
                    detectDragGestures(
                        onDragStart = { point -> strokes = strokes + Stroke(listOf(point), tool == PaintTool.ERASER) },
                        onDrag = { change, _ ->
                            change.consume()
                            val last = strokes.lastOrNull() ?: return@detectDragGestures
                            strokes = strokes.dropLast(1) + last.copy(points = last.points + change.position)
                        },
                    )
                }
        ) {
            baseBitmap?.let { drawImage(it.asImageBitmap(), dstSize = IntSize(size.width.toInt(), size.height.toInt())) }
            strokes.forEach { stroke ->
                stroke.points.zipWithNext().forEach { (start, end) ->
                    drawLine(if (stroke.eraser) Color.White else Color.Black, start, end, if (stroke.eraser) 28f else 5f)
                }
            }
        }
    }
}

private fun renderPng(size: IntSize, base: Bitmap?, strokes: List<Stroke>): ByteArray {
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.WHITE)
    base?.let { canvas.drawBitmap(it, null, Rect(0, 0, size.width, size.height), null) }
    strokes.forEach { stroke ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (stroke.eraser) AndroidColor.WHITE else AndroidColor.BLACK
            strokeWidth = if (stroke.eraser) 28f else 5f
            strokeCap = Paint.Cap.ROUND
        }
        stroke.points.zipWithNext().forEach { (start, end) -> canvas.drawLine(start.x, start.y, end.x, end.y, paint) }
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
