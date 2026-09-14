package ua.school.windowsdesktop

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import ua.school.windowsdesktop.data.LearningFileRepository
import ua.school.windowsdesktop.domain.*

private data class DesktopPosition(val x: Float, val y: Float)

@Composable
internal fun DesktopScreen(
    repository: LearningFileRepository,
    nodes: List<FileNode>,
    onFiles: () -> Unit,
    onNotepad: () -> Unit,
    onTrash: () -> Unit,
    onPaint: () -> Unit,
    onFolder: (String) -> Unit,
    onText: (String) -> Unit,
    onPaintFile: (String) -> Unit,
    onError: (String) -> Unit,
    showFileExtensions: Boolean,
    trashNotEmpty: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var positions by remember { mutableStateOf(loadDesktopPositions(context)) }
    var backgroundMenu by remember { mutableStateOf<Offset?>(null) }
    var createKind by remember { mutableStateOf<FileKind?>(null) }
    var createPosition by remember { mutableStateOf(Offset.Zero) }
    var newName by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var deleteTarget by remember { mutableStateOf<FileNode?>(null) }
    val desktopNodes = nodes.filter { it.parentId == FileOperations.ROOT_ID && it.trashedAt == null && it.id in positions }

    fun open(node: FileNode) = when (node.kind) {
        FileKind.FOLDER -> onFolder(node.id)
        FileKind.TEXT -> onText(node.id)
        FileKind.PAINT -> onPaintFile(node.id)
    }
    fun submitCreate(kind: FileKind) {
        if (newName.isBlank()) return
        scope.launch { try {
            val node = when (kind) {
                FileKind.FOLDER -> repository.createFolder(newName, FileOperations.ROOT_ID)
                FileKind.TEXT -> repository.createText(newName, FileOperations.ROOT_ID)
                FileKind.PAINT -> repository.createPaint(newName, FileOperations.ROOT_ID, blankPaintPng())
            }
            val updated = positions + (node.id to DesktopPosition(createPosition.x, createPosition.y))
            positions = updated; saveDesktopPositions(context, updated); selectedId = node.id; createKind = null
        } catch (failure: Exception) { onError(failure.message ?: "Не вдалося створити об’єкт") } }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0078D7)).onSecondaryClick { point ->
        selectedId = null; backgroundMenu = point
    }) {
        SystemDesktopIcon(R.drawable.this_computer, stringResource(R.string.this_pc), 12, onFiles)
        SystemDesktopIcon(R.drawable.my_files, stringResource(R.string.my_files), 112, onFiles)
        SystemDesktopIcon(R.drawable.notepad, stringResource(R.string.notepad), 212, onNotepad)
        SystemDesktopIcon(R.drawable.paint, "Paint", 312, onPaint)
        SystemDesktopIcon(if (trashNotEmpty) R.drawable.full_bin else R.drawable.empty_bin, stringResource(R.string.recycle_bin), 412, onTrash)

        desktopNodes.forEach { node ->
            val position = positions.getValue(node.id)
            UserDesktopIcon(node, position, node.id == selectedId, showFileExtensions,
                onSelect = { selectedId = node.id }, onOpen = { open(node) },
                onRename = { renameTarget = node; newName = displayName(node, showFileExtensions) },
                onDelete = { deleteTarget = node })
        }

        backgroundMenu?.let { point -> Box(Modifier.offset { IntOffset(point.x.roundToInt(), point.y.roundToInt()) }.size(1.dp)) {
            DropdownMenu(true, { backgroundMenu = null }) {
                fun begin(kind: FileKind) { createPosition = point; createKind = kind; newName = ""; backgroundMenu = null }
                DropdownMenuItem({ Text(stringResource(R.string.new_folder)) }, { begin(FileKind.FOLDER) })
                DropdownMenuItem({ Text(stringResource(R.string.new_text_document)) }, { begin(FileKind.TEXT) })
                DropdownMenuItem({ Text(stringResource(R.string.new_bitmap_image)) }, { begin(FileKind.PAINT) })
            }
        } }
    }

    createKind?.let { kind -> AlertDialog(
        modifier = Modifier.dialogKeys(newName.isNotBlank(), { submitCreate(kind) }, { createKind = null }),
        onDismissRequest = { createKind = null },
        title = { Text(when (kind) { FileKind.FOLDER -> stringResource(R.string.new_folder); FileKind.TEXT -> stringResource(R.string.new_text_document); FileKind.PAINT -> stringResource(R.string.new_bitmap_image) }) },
        text = { OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text(stringResource(R.string.name)) }) },
        confirmButton = { TextButton({ submitCreate(kind) }, enabled = newName.isNotBlank()) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton({ createKind = null }) { Text(stringResource(R.string.cancel)) } },
    ) }
    renameTarget?.let { target -> AlertDialog(
        modifier = Modifier.dialogKeys(newName.isNotBlank(), { scope.launch { try { repository.rename(target.id, newName); renameTarget = null } catch (failure: Exception) { onError(failure.message ?: "Помилка") } } }, { renameTarget = null }),
        onDismissRequest = { renameTarget = null }, title = { Text(stringResource(R.string.rename)) },
        text = { OutlinedTextField(newName, { newName = it }, singleLine = true) },
        confirmButton = { TextButton({ scope.launch { try { repository.rename(target.id, newName); renameTarget = null } catch (failure: Exception) { onError(failure.message ?: "Помилка") } } }, enabled = newName.isNotBlank()) { Text(stringResource(R.string.rename)) } },
        dismissButton = { TextButton({ renameTarget = null }) { Text(stringResource(R.string.cancel)) } },
    ) }
    deleteTarget?.let { target -> AlertDialog(
        onDismissRequest = { deleteTarget = null }, title = { Text(stringResource(R.string.delete)) },
        text = { Text(stringResource(R.string.delete_question, displayName(target, showFileExtensions))) },
        confirmButton = { TextButton({ scope.launch { try { repository.moveToTrash(target.id); deleteTarget = null; selectedId = null } catch (failure: Exception) { onError(failure.message ?: "Помилка") } } }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton({ deleteTarget = null }) { Text(stringResource(R.string.cancel)) } },
    ) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun SystemDesktopIcon(icon: Int, label: String, y: Int, open: () -> Unit) {
    Column(Modifier.offset(x = 12.dp, y = y.dp).width(92.dp).onSecondaryClick(PointerEventPass.Initial) {}
        .combinedClickable(onClick = {}, onDoubleClick = open).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(icon), label, Modifier.size(58.dp))
        Text(label, color = Color.White, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun UserDesktopIcon(
    node: FileNode, position: DesktopPosition, selected: Boolean, showExtensions: Boolean,
    onSelect: () -> Unit, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
) {
    var menuPosition by remember { mutableStateOf<Offset?>(null) }
    Box(Modifier.offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }.width(96.dp)
        .background(if (selected) Color(0x6633A7FF) else Color.Transparent)
        .onSecondaryClick(PointerEventPass.Initial) { menuPosition = it; onSelect() }) {
        Column(Modifier.fillMaxWidth().combinedClickable(onClick = onSelect, onDoubleClick = onOpen).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(fileIcon(node)), null, Modifier.size(58.dp))
            Text(displayName(node, showExtensions), color = Color.White, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        menuPosition?.let { point -> Box(Modifier.offset { IntOffset(point.x.roundToInt(), point.y.roundToInt()) }.size(1.dp)) {
            DropdownMenu(true, { menuPosition = null }) {
                DropdownMenuItem({ Text(stringResource(R.string.open)) }, { menuPosition = null; onOpen() })
                DropdownMenuItem({ Text(stringResource(R.string.rename)) }, { menuPosition = null; onRename() })
                DropdownMenuItem({ Text(stringResource(R.string.delete)) }, { menuPosition = null; onDelete() })
            }
        } }
    }
}

private fun loadDesktopPositions(context: Context): Map<String, DesktopPosition> {
    val preferences = context.getSharedPreferences("desktop_positions", Context.MODE_PRIVATE)
    return preferences.getStringSet("ids", emptySet()).orEmpty().associateWith { id ->
        DesktopPosition(preferences.getFloat("${id}_x", 120f), preferences.getFloat("${id}_y", 20f))
    }
}

private fun saveDesktopPositions(context: Context, positions: Map<String, DesktopPosition>) {
    val editor = context.getSharedPreferences("desktop_positions", Context.MODE_PRIVATE).edit().putStringSet("ids", positions.keys)
    positions.forEach { (id, position) -> editor.putFloat("${id}_x", position.x).putFloat("${id}_y", position.y) }
    editor.apply()
}
