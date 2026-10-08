package ua.school.windowsdesktop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Path
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon as ComposePointerIcon
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalView
import android.view.PointerIcon
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch
import ua.school.windowsdesktop.data.LearningFileRepository
import ua.school.windowsdesktop.domain.FileNode
import ua.school.windowsdesktop.domain.FileOperations

internal enum class PaintTool { PENCIL, BRUSH, ERASER, FILL, LINE, CURVE, RECTANGLE, OVAL, TEXT }
internal data class PaintAction(
    val tool: PaintTool,
    val points: List<Offset>,
    val color: Int,
    val width: Float,
    val text: String = "",
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val editBounds: androidx.compose.ui.geometry.Rect? = null,
)

@OptIn(ExperimentalComposeUiApi::class)
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
    var selectedActionIndex by remember { mutableStateOf<Int?>(null) }
    val localView = LocalView.current
    var hoverPoint by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var loaded by remember(file?.id) { mutableStateOf(file == null) }
    var dirty by remember(file?.id) { mutableStateOf(false) }
    var closeRequested by remember { mutableStateOf(false) }
    var saveAsRequested by remember { mutableStateOf(false) }
    var saveAsName by remember { mutableStateOf("") }
    var textPosition by remember { mutableStateOf<Offset?>(null) }
    var enteredText by remember { mutableStateOf("") }
    var activeCurve by remember { mutableStateOf<PaintAction?>(null) }
    var curveStage by remember { mutableIntStateOf(0) }
    var fileMenu by remember { mutableStateOf(false) }
    var editMenu by remember { mutableStateOf(false) }
    var viewMenu by remember { mutableStateOf(false) }
    var shiftPressed by remember { mutableStateOf(false) }
    val paintFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { paintFocusRequester.requestFocus() }

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
            selectedActionIndex = null; actions = emptyList(); redoActions = emptyList(); dirty = false; after()
        } catch (failure: Exception) { onError(failure.message ?: "Не вдалося зберегти малюнок") } }
    }
    fun submitSaveAs() {
        if (saveAsName.isBlank() || canvasSize == IntSize.Zero) return
        val bytes = png()
        scope.launch { try {
            val saved = repository.createPaint(saveAsName, file?.parentId ?: FileOperations.ROOT_ID, bytes)
            currentId = saved.id; currentName = saved.name
            baseBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            selectedActionIndex = null; actions = emptyList(); redoActions = emptyList(); dirty = false; saveAsRequested = false
        } catch (failure: Exception) { onError(failure.message ?: "Не вдалося зберегти малюнок") } }
    }
    fun requestClose() { if (dirty) closeRequested = true else onClose() }
    fun undo() { selectedActionIndex = null; if (actions.isNotEmpty()) { redoActions = redoActions + actions.last(); actions = actions.dropLast(1); dirty = true } }
    fun redo() { selectedActionIndex = null; if (redoActions.isNotEmpty()) { actions = actions + redoActions.last(); redoActions = redoActions.dropLast(1); dirty = true } }
    fun clear() { selectedActionIndex = null; baseBitmap = null; actions = emptyList(); redoActions = emptyList(); dirty = true }
    fun commitCurve() { activeCurve?.let { if (it.points.size == 4) { actions = actions + it; redoActions = emptyList(); dirty = true } }; activeCurve = null; curveStage = 0 }

    BackHandler { requestClose() }
    Column(Modifier.fillMaxSize().background(Color(0xFFF2F2F2)).focusRequester(paintFocusRequester).focusTarget().onPreviewKeyEvent { event ->
        when {
            event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.S -> { save(); true }
            event.type == KeyEventType.KeyDown || event.type == KeyEventType.KeyUp -> { shiftPressed = event.isShiftPressed; false }
            else -> false
        }
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
                Box(Modifier.padding(3.dp).size(32.dp).background(Color.Gray).border(if (selectedColor == Color.Gray.toArgb()) 3.dp else 1.dp, Color.DarkGray).clickable { selectedColor = Color.Gray.toArgb() })
            }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(Color.White).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ToolButton(R.drawable.paint_pencil, stringResource(R.string.pencil), tool == PaintTool.PENCIL) { selectedActionIndex = null; tool = PaintTool.PENCIL }
                    ToolButton(R.drawable.paint_brush, stringResource(R.string.brush), tool == PaintTool.BRUSH) { selectedActionIndex = null; tool = PaintTool.BRUSH }
                    ToolButton(R.drawable.paint_eraser, stringResource(R.string.eraser), tool == PaintTool.ERASER) { selectedActionIndex = null; tool = PaintTool.ERASER }
                    ToolButton(R.drawable.paint_fill, stringResource(R.string.fill), tool == PaintTool.FILL) { selectedActionIndex = null; tool = PaintTool.FILL }
                    ToolButton(R.drawable.paint_line, stringResource(R.string.line), tool == PaintTool.LINE) { selectedActionIndex = null; tool = PaintTool.LINE }
                    ToolButton(R.drawable.paint_line, "Крива", tool == PaintTool.CURVE) { selectedActionIndex = null; commitCurve(); tool = PaintTool.CURVE }
                    ToolButton(R.drawable.paint_rectangle, stringResource(R.string.rectangle), tool == PaintTool.RECTANGLE) { selectedActionIndex = null; tool = PaintTool.RECTANGLE }
                    ToolButton(R.drawable.paint_oval, stringResource(R.string.oval), tool == PaintTool.OVAL) { selectedActionIndex = null; tool = PaintTool.OVAL }
                    ToolButton(R.drawable.paint_text, stringResource(R.string.text_tool), tool == PaintTool.TEXT) { selectedActionIndex = null; tool = PaintTool.TEXT }
                }
                if (!loaded) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else {
                val previewBitmap = remember(canvasSize, baseBitmap, actions, activeCurve) { if (canvasSize.width > 0 && canvasSize.height > 0) renderBitmap(canvasSize, baseBitmap, actions, activeCurve) else null }
                val selectedBounds = selectedActionIndex?.let { actions.getOrNull(it)?.let(::actionBounds) }
                Canvas(Modifier.fillMaxSize().padding(10.dp).background(Color.White).border(1.dp, Color.Gray)
                .onSizeChanged { canvasSize = it }
                .pointerHoverIcon(ComposePointerIcon(PointerIcon.getSystemIcon(localView.context,
                    hoverPoint?.let { point -> selectedBounds?.let { pointerIconTypeForPoint(point, it) } }
                        ?: PointerIcon.TYPE_ARROW)))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            hoverPoint = if (event.type == PointerEventType.Exit) null else event.changes.firstOrNull()?.position
                        }
                    }
                }
                // Keep this handler alive across recompositions, including selection and Shift changes.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        paintFocusRequester.requestFocus()
                        down.consume()
                        val before = actions
                        val activeIndex = selectedActionIndex
                        val original = activeIndex?.let { actions.getOrNull(it) }
                        val bounds = original?.let(::actionBounds)
                        if (original != null && bounds != null) {
                            val mode = selectionModeForPoint(down.position, bounds)
                            if (mode < 0) {
                                // Commit on an outside click; never search older actions for selection.
                                selectedActionIndex = null
                                return@awaitEachGesture
                            }
                            var completed = false
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val delta = change.position - down.position
                                    val target = transformedBounds(bounds, delta, mode)
                                    if (delta != Offset.Zero || actions != before) {
                                        actions = before.toMutableList().also { it[activeIndex] = transformAction(original, bounds, target) }
                                        redoActions = emptyList(); dirty = true
                                    }
                                    change.consume()
                                    completed = !change.pressed
                                } while (!completed)
                            } finally {
                                if (!completed) actions = before
                            }
                            return@awaitEachGesture
                        }
                        val gestureTool = tool
                        if (gestureTool == PaintTool.CURVE) {
                            val start = down.position
                            var released = false
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val point = change.position
                                activeCurve = when (curveStage) {
                                    0 -> PaintAction(PaintTool.CURVE, listOf(start, start + (point - start) / 3f, start + (point - start) * 2f / 3f, point), selectedColor, selectedWidth)
                                    1 -> activeCurve?.copy(points = activeCurve!!.points.toMutableList().also { it[1] = point })
                                    else -> activeCurve?.copy(points = activeCurve!!.points.toMutableList().also { it[2] = point })
                                }
                                change.consume(); released = !change.pressed
                            } while (!released)
                            if (released) { curveStage = if (curveStage < 2) curveStage + 1 else 0; if (curveStage == 0) commitCurve() }
                            return@awaitEachGesture
                        }
                        if (gestureTool == PaintTool.TEXT || gestureTool == PaintTool.FILL) {
                            var released = false
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                released = !change.pressed
                            } while (!released)
                            if (released) {
                                if (gestureTool == PaintTool.TEXT) { textPosition = down.position; enteredText = "" }
                                else {
                                    actions = actions + PaintAction(PaintTool.FILL, listOf(down.position), selectedColor, selectedWidth)
                                    redoActions = emptyList(); dirty = true
                                }
                            }
                            return@awaitEachGesture
                        }
                        val color = if (gestureTool == PaintTool.ERASER) AndroidColor.WHITE else selectedColor
                        val width = when (gestureTool) { PaintTool.ERASER -> selectedWidth * 4; PaintTool.BRUSH -> selectedWidth * 2; else -> selectedWidth }
                        val isShape = gestureTool in listOf(PaintTool.LINE, PaintTool.RECTANGLE, PaintTool.OVAL)
                        var drawing = PaintAction(gestureTool, listOf(down.position, down.position), color, width)
                        actions = before + drawing
                        var completed = false
                        try {
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val end = when {
                                    shiftPressed && gestureTool in listOf(PaintTool.RECTANGLE, PaintTool.OVAL) -> constrainedSquareEnd(down.position, change.position)
                                    shiftPressed && gestureTool == PaintTool.LINE -> constrainedLineEnd(down.position, change.position)
                                    else -> change.position
                                }
                                drawing = drawing.copy(points = if (isShape) listOf(down.position, end) else drawing.points + end)
                                actions = before + drawing
                                change.consume()
                                completed = !change.pressed
                            } while (!completed)
                            if (completed) {
                                redoActions = emptyList(); dirty = true
                                selectedActionIndex = if (isShape) actions.lastIndex else null
                            }
                        } finally {
                            if (!completed) actions = before
                        }
                    }
                }
        ) {
            previewBitmap?.let { drawImage(it.asImageBitmap(), dstSize = canvasSize) }
            selectedActionIndex?.let { index ->
                actions.getOrNull(index)?.let { action ->
                    actionBounds(action)?.let { bounds ->
                        drawRect(Color(0xFF1976D2), bounds.topLeft, bounds.size, style = Stroke(2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                        selectionHandles(bounds).forEach { center ->
                            val origin = center - Offset(4f, 4f)
                            drawRect(Color.White, origin, Size(8f, 8f))
                            drawRect(Color(0xFF1976D2), origin, Size(8f, 8f), style = Stroke(1f))
                        }
                    }
                }
            }
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
            selectedActionIndex = actions.lastIndex; redoActions = emptyList(); dirty = true; textPosition = null
        }, { textPosition = null }),
        onDismissRequest = { textPosition = null },
        title = { Text(stringResource(R.string.enter_text)) },
        text = { OutlinedTextField(enteredText, { enteredText = it }, singleLine = true) },
        confirmButton = { TextButton(enabled = enteredText.isNotBlank(), onClick = {
            actions = actions + PaintAction(PaintTool.TEXT, listOf(position), selectedColor, selectedWidth, enteredText)
            selectedActionIndex = actions.lastIndex; redoActions = emptyList(); dirty = true; textPosition = null
        }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = { textPosition = null }) { Text(stringResource(R.string.cancel)) } },
    ) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolButton(icon: Int, label: String, selected: Boolean, action: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = action,
            modifier = Modifier.size(48.dp).semantics { this.selected = selected },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (selected) Color(0xFFCDE8FF) else Color.Transparent,
            ),
        ) {
            Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(24.dp), tint = Color.Unspecified)
        }
    }
}

private fun renderPng(size: IntSize, base: Bitmap?, actions: List<PaintAction>): ByteArray {
    val bitmap = renderBitmap(size, base, actions)
    return ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
}

private fun renderBitmap(size: IntSize, base: Bitmap?, actions: List<PaintAction>, activeCurve: PaintAction? = null): Bitmap {
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap); canvas.drawColor(AndroidColor.WHITE)
    base?.let { canvas.drawBitmap(it, null, Rect(0, 0, size.width, size.height), null) }
    (actions + listOfNotNull(activeCurve)).forEach { action ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = action.color; strokeWidth = action.width; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
        val start = action.points.firstOrNull() ?: return@forEach
        val end = action.points.lastOrNull() ?: return@forEach
        when (action.tool) {
            PaintTool.PENCIL, PaintTool.BRUSH, PaintTool.ERASER -> action.points.zipWithNext().forEach { (a, b) -> canvas.drawLine(a.x, a.y, b.x, b.y, paint) }
            PaintTool.FILL -> floodFill(bitmap, start.x.toInt(), start.y.toInt(), action.color)
            PaintTool.LINE -> canvas.drawLine(start.x, start.y, end.x, end.y, paint)
            PaintTool.CURVE -> if (action.points.size >= 4) {
                val path = Path().apply { moveTo(start.x, start.y); cubicTo(action.points[1].x, action.points[1].y, action.points[2].x, action.points[2].y, action.points[3].x, action.points[3].y) }
                canvas.drawPath(path, paint)
            }
            PaintTool.RECTANGLE -> canvas.drawRect(RectF(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y)), paint)
            PaintTool.OVAL -> canvas.drawOval(RectF(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y)), paint)
            PaintTool.TEXT -> {
                paint.style = Paint.Style.FILL; paint.textSize = (action.width * 5).coerceAtLeast(18f)
                canvas.save()
                canvas.translate(start.x, start.y)
                canvas.scale(action.scaleX, action.scaleY)
                canvas.drawText(action.text, 0f, 0f, paint)
                canvas.restore()
            }
        }
    }
    return bitmap
}

private fun floodFill(bitmap: Bitmap, startX: Int, startY: Int, replacement: Int) {
    if (startX !in 0 until bitmap.width || startY !in 0 until bitmap.height) return
    val target = bitmap.getPixel(startX, startY)
    if (target == replacement) return

    // A blank canvas is the common case when filling the whole drawing. Avoid
    // allocating one queue slot per pixel (which can freeze large displays).
    var uniform = true
    val rowPixels = IntArray(bitmap.width)
    for (y in 0 until bitmap.height) {
        bitmap.getPixels(rowPixels, 0, bitmap.width, 0, y, bitmap.width, 1)
        if (rowPixels.any { it != target }) {
            uniform = false
            break
        }
    }
    if (uniform) {
        bitmap.eraseColor(replacement)
        return
    }

    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    var queue = IntArray(minOf(4096, pixels.size.coerceAtLeast(1)))
    var head = 0; var tail = 0
    fun enqueue(x: Int, y: Int) {
        val index = y * bitmap.width + x
        if (x in 0 until bitmap.width && y in 0 until bitmap.height && pixels[index] == target) {
            pixels[index] = replacement
            if (tail == queue.size) queue = queue.copyOf(queue.size * 2)
            queue[tail++] = y * bitmap.width + x
        }
    }
    enqueue(startX, startY)
    while (head < tail) {
        val value = queue[head++]
        val x = value % bitmap.width; val y = value / bitmap.width
        enqueue(x + 1, y); enqueue(x - 1, y); enqueue(x, y + 1); enqueue(x, y - 1)
    }
    bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
}

private fun constrainedSquareEnd(start: Offset, end: Offset): Offset {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val side = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
    return Offset(start.x + if (dx < 0) -side else side, start.y + if (dy < 0) -side else side)
}

private fun constrainedLineEnd(start: Offset, end: Offset): Offset {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (length == 0f) return start
    val step = (Math.PI / 4.0)
    val angle = kotlin.math.atan2(dy.toDouble(), dx.toDouble())
    val snapped = kotlin.math.round(angle / step) * step
    return Offset(
        start.x + (kotlin.math.cos(snapped) * length).toFloat(),
        start.y + (kotlin.math.sin(snapped) * length).toFloat(),
    )
}

internal fun actionBounds(action: PaintAction): androidx.compose.ui.geometry.Rect? {
    action.editBounds?.let { return it }
    if (action.points.isEmpty()) return null
    if (action.tool == PaintTool.TEXT) {
        val start = action.points.first()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = (action.width * 5).coerceAtLeast(18f) }
        val ink = Rect()
        paint.getTextBounds(action.text, 0, action.text.length, ink)
        return androidx.compose.ui.geometry.Rect(
            start.x + minOf(0f, ink.left.toFloat()) * action.scaleX - 4f,
            start.y + paint.fontMetrics.ascent * action.scaleY - 4f,
            start.x + maxOf(paint.measureText(action.text), ink.right.toFloat()) * action.scaleX + 4f,
            start.y + paint.fontMetrics.descent * action.scaleY + 4f,
        )
    }
    val padding = maxOf(4f, action.width / 2f)
    return androidx.compose.ui.geometry.Rect(
        action.points.minOf { it.x } - padding, action.points.minOf { it.y } - padding,
        action.points.maxOf { it.x } + padding, action.points.maxOf { it.y } + padding,
    )
}

private fun selectionHandles(bounds: androidx.compose.ui.geometry.Rect): List<Offset> = listOf(
    bounds.topLeft, Offset(bounds.center.x, bounds.top), bounds.topRight,
    Offset(bounds.right, bounds.center.y), bounds.bottomRight, Offset(bounds.center.x, bounds.bottom),
    bounds.bottomLeft, Offset(bounds.left, bounds.center.y),
)

// -1 = outside, 0 = move. Use the same finite hit zones for hover and dragging.
internal fun selectionModeForPoint(point: Offset, bounds: androidx.compose.ui.geometry.Rect): Int {
    if (!bounds.inflate(5f).contains(point)) return -1
    val horizontalTolerance = minOf(8f, bounds.width / 4f)
    val verticalTolerance = minOf(8f, bounds.height / 4f)
    val horizontal = when {
        kotlin.math.abs(point.x - bounds.left) <= horizontalTolerance -> 1
        kotlin.math.abs(point.x - bounds.right) <= horizontalTolerance -> 2
        else -> 0
    }
    val vertical = when {
        kotlin.math.abs(point.y - bounds.top) <= verticalTolerance -> 4
        kotlin.math.abs(point.y - bounds.bottom) <= verticalTolerance -> 8
        else -> 0
    }
    return if (horizontal == 0 && vertical == 0 && !bounds.contains(point)) -1 else horizontal or vertical
}

private fun pointerIconTypeForPoint(point: Offset, bounds: androidx.compose.ui.geometry.Rect): Int = when (selectionModeForPoint(point, bounds)) {
    -1 -> PointerIcon.TYPE_ARROW
    1, 2 -> PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW
    4, 8 -> PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW
    5, 10 -> PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW
    6, 9 -> PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW
    else -> PointerIcon.TYPE_ALL_SCROLL
}

internal fun transformedBounds(bounds: androidx.compose.ui.geometry.Rect, delta: Offset, mode: Int): androidx.compose.ui.geometry.Rect {
    if (mode == 0) return bounds.translate(delta)
    return androidx.compose.ui.geometry.Rect(
        if (mode and 1 != 0) minOf(bounds.left + delta.x, bounds.right - 4f) else bounds.left,
        if (mode and 4 != 0) minOf(bounds.top + delta.y, bounds.bottom - 4f) else bounds.top,
        if (mode and 2 != 0) maxOf(bounds.right + delta.x, bounds.left + 4f) else bounds.right,
        if (mode and 8 != 0) maxOf(bounds.bottom + delta.y, bounds.top + 4f) else bounds.bottom,
    )
}

internal fun transformAction(action: PaintAction, from: androidx.compose.ui.geometry.Rect, to: androidx.compose.ui.geometry.Rect): PaintAction = action.copy(
    points = resizePoints(action.points, from, to),
    scaleX = action.scaleX * to.width / from.width.coerceAtLeast(1f),
    scaleY = action.scaleY * to.height / from.height.coerceAtLeast(1f),
    editBounds = to,
)

private fun resizePoints(points: List<Offset>, from: androidx.compose.ui.geometry.Rect, to: androidx.compose.ui.geometry.Rect): List<Offset> {
    val sx = to.width / from.width.coerceAtLeast(1f)
    val sy = to.height / from.height.coerceAtLeast(1f)
    return points.map { Offset(to.left + (it.x - from.left) * sx, to.top + (it.y - from.top) * sy) }
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
