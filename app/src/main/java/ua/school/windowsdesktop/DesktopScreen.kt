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
private data class DesktopProperties(val name: String, val type: String)
private data class SystemShortcut(
    val id: String,
    val icon: Int,
    val defaultName: String,
    val open: () -> Unit,
    val isProtected: Boolean = false,
    val isTrash: Boolean = false,
)

@Composable
internal fun DesktopScreen(
    repository: LearningFileRepository,
    nodes: List<FileNode>,
    onFiles: () -> Unit,
    onComputer: () -> Unit,
    onComputerProperties: () -> Unit,
    onNotepad: () -> Unit,
    onTrash: () -> Unit,
    onPaint: () -> Unit,
    onBrowser: () -> Unit,
    onFolder: (String) -> Unit,
    onText: (String) -> Unit,
    onWord: (String) -> Unit,
    onPaintFile: (String) -> Unit,
    onShowLoading: () -> Unit,
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
    var selectedSystemShortcutId by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var deleteTarget by remember { mutableStateOf<FileNode?>(null) }
    var renameSystemShortcut by remember { mutableStateOf<SystemShortcut?>(null) }
    var propertiesTarget by remember { mutableStateOf<DesktopProperties?>(null) }
    var clearTrashRequested by remember { mutableStateOf(false) }
    var systemShortcutNames by remember { mutableStateOf(loadSystemShortcutNames(context)) }
    var hiddenSystemShortcuts by remember { mutableStateOf(loadHiddenSystemShortcuts(context)) }
    val desktopNodes = nodes.filter { it.parentId == FileOperations.ROOT_ID && it.trashedAt == null && it.id in positions }

    fun open(node: FileNode) = when (node.kind) {
        FileKind.FOLDER -> onFolder(node.id)
        FileKind.TEXT -> if (node.name.endsWith(".docx", true)) onWord(node.id) else onText(node.id)
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

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0078D7)).onLongPressContext { point ->
        onShowLoading()
        selectedId = null; selectedSystemShortcutId = null; backgroundMenu = point
    }.onSecondaryClick { point ->
        onShowLoading()
        selectedId = null; selectedSystemShortcutId = null; backgroundMenu = point
    }) {
        val systemShortcutType = stringResource(R.string.system_shortcut)
        val rows = ((maxHeight.value - 12f) / 100f).toInt().coerceAtLeast(1)
        val systemIcons = listOf(
            SystemShortcut("this_pc", R.drawable.this_computer, stringResource(R.string.this_pc), onComputer, isProtected = true),
            SystemShortcut("my_files", R.drawable.my_files, stringResource(R.string.my_files), onFiles),
            SystemShortcut("notepad", R.drawable.notepad, stringResource(R.string.notepad), onNotepad),
            SystemShortcut("paint", R.drawable.paint, "Paint", onPaint),
            SystemShortcut("recycle_bin", if (trashNotEmpty) R.drawable.full_bin else R.drawable.empty_bin, stringResource(R.string.recycle_bin), onTrash, isProtected = true, isTrash = true),
        )
        systemIcons.filterNot { it.id in hiddenSystemShortcuts }.forEachIndexed { index, shortcut ->
            val label = systemShortcutNames[shortcut.id] ?: shortcut.defaultName
            SystemDesktopIcon(shortcut.icon, label, 12 + (index / rows) * 104, 12 + (index % rows) * 100,
                onOpen = shortcut.open,
                onShowLoading = onShowLoading,
                selected = shortcut.id == selectedSystemShortcutId,
                onSelect = { selectedId = null; selectedSystemShortcutId = shortcut.id },
                onRename = if (shortcut.isProtected) null else {
                    { renameSystemShortcut = shortcut; newName = label }
                },
                onDelete = if (shortcut.isProtected) null else {
                    {
                        val updated = hiddenSystemShortcuts + shortcut.id
                        hiddenSystemShortcuts = updated
                        saveHiddenSystemShortcuts(context, updated)
                    }
                },
                onProperties = if (shortcut.id == "this_pc") onComputerProperties else {
                    { propertiesTarget = DesktopProperties(label, systemShortcutType) }
                },
                onEmptyTrash = if (shortcut.isTrash) { { clearTrashRequested = true } } else null,
            )
        }

        desktopNodes.forEach { node ->
            val position = positions.getValue(node.id)
            UserDesktopIcon(node, position, node.id == selectedId, showFileExtensions,
                onSelect = { selectedSystemShortcutId = null; selectedId = node.id }, onOpen = { open(node) },
                onRename = { renameTarget = node; newName = displayName(node, showFileExtensions) },
                onDelete = { deleteTarget = node },
                onProperties = { propertiesTarget = DesktopProperties(displayName(node, showFileExtensions), fileTypeName(node)) },
                onShowLoading = onShowLoading)
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
    renameSystemShortcut?.let { shortcut -> AlertDialog(
        modifier = Modifier.dialogKeys(newName.isNotBlank(), {
            val updated = systemShortcutNames + (shortcut.id to newName.trim())
            systemShortcutNames = updated; saveSystemShortcutNames(context, updated); renameSystemShortcut = null
        }, { renameSystemShortcut = null }),
        onDismissRequest = { renameSystemShortcut = null }, title = { Text(stringResource(R.string.rename)) },
        text = { OutlinedTextField(newName, { newName = it }, singleLine = true) },
        confirmButton = { TextButton({
            val updated = systemShortcutNames + (shortcut.id to newName.trim())
            systemShortcutNames = updated; saveSystemShortcutNames(context, updated); renameSystemShortcut = null
        }, enabled = newName.isNotBlank()) { Text(stringResource(R.string.rename)) } },
        dismissButton = { TextButton({ renameSystemShortcut = null }) { Text(stringResource(R.string.cancel)) } },
    ) }
    deleteTarget?.let { target -> AlertDialog(
        onDismissRequest = { deleteTarget = null }, title = { Text(stringResource(R.string.delete)) },
        text = { Text(stringResource(R.string.delete_question, displayName(target, showFileExtensions))) },
        confirmButton = { TextButton({ scope.launch { try { repository.moveToTrash(target.id); deleteTarget = null; selectedId = null } catch (failure: Exception) { onError(failure.message ?: "Помилка") } } }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton({ deleteTarget = null }) { Text(stringResource(R.string.cancel)) } },
    ) }
    propertiesTarget?.let { target -> AlertDialog(
        onDismissRequest = { propertiesTarget = null }, title = { Text(stringResource(R.string.properties)) },
        text = { Column { Text("${stringResource(R.string.name)}: ${target.name}"); Text("${stringResource(R.string.type)}: ${target.type}") } },
        confirmButton = { TextButton({ propertiesTarget = null }) { Text(stringResource(R.string.ok)) } },
    ) }
    if (clearTrashRequested) AlertDialog(
        onDismissRequest = { clearTrashRequested = false }, title = { Text(stringResource(R.string.empty_recycle_bin)) },
        text = { Text(stringResource(R.string.empty_recycle_bin_question)) },
        confirmButton = { TextButton({ scope.launch { try { repository.emptyTrash(); clearTrashRequested = false } catch (failure: Exception) { onError(failure.message ?: "Помилка") } } }) { Text(stringResource(R.string.empty_recycle_bin)) } },
        dismissButton = { TextButton({ clearTrashRequested = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun SystemDesktopIcon(
    icon: Int, label: String, x: Int, y: Int, onOpen: () -> Unit, onRename: (() -> Unit)?, onDelete: (() -> Unit)?,
    onProperties: () -> Unit, onEmptyTrash: (() -> Unit)?, selected: Boolean, onSelect: () -> Unit, onShowLoading: () -> Unit,
) {
    var menuPosition by remember { mutableStateOf<Offset?>(null) }
    Box(Modifier.offset(x = x.dp, y = y.dp).width(92.dp)
        .onSecondaryClick(PointerEventPass.Initial) { point -> onShowLoading(); onSelect(); menuPosition = point }) {
        Column(Modifier.fillMaxWidth().background(if (selected) Color(0x6633A7FF) else Color.Transparent)
            .combinedClickable(onClick = onSelect, onDoubleClick = onOpen, onLongClick = { onShowLoading(); onSelect(); menuPosition = Offset(8f, 8f) })
            .padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(icon), label, Modifier.size(58.dp))
            Text(label, color = Color.White, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        menuPosition?.let { point -> Box(Modifier.offset { IntOffset(point.x.roundToInt(), point.y.roundToInt()) }.size(1.dp)) {
            DropdownMenu(true, { menuPosition = null }) {
                DropdownMenuItem({ Text(stringResource(R.string.open)) }, { menuPosition = null; onOpen() })
                onRename?.let { rename -> DropdownMenuItem({ Text(stringResource(R.string.rename)) }, { menuPosition = null; rename() }) }
                onDelete?.let { delete -> DropdownMenuItem({ Text(stringResource(R.string.delete)) }, { menuPosition = null; delete() }) }
                onEmptyTrash?.let { emptyTrash -> DropdownMenuItem({ Text(stringResource(R.string.empty_recycle_bin)) }, { menuPosition = null; emptyTrash() }) }
                DropdownMenuItem({ Text(stringResource(R.string.properties)) }, { menuPosition = null; onProperties() })
            }
        } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun UserDesktopIcon(
    node: FileNode, position: DesktopPosition, selected: Boolean, showExtensions: Boolean,
    onSelect: () -> Unit, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
    onProperties: () -> Unit, onShowLoading: () -> Unit,
) {
    var menuPosition by remember { mutableStateOf<Offset?>(null) }
    Box(Modifier.offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }.width(96.dp)
        .background(if (selected) Color(0x6633A7FF) else Color.Transparent)
        .onSecondaryClick(PointerEventPass.Initial) { point -> onShowLoading(); onSelect(); menuPosition = point }) {
        Column(Modifier.fillMaxWidth().combinedClickable(onClick = onSelect, onDoubleClick = onOpen,
            onLongClick = { onShowLoading(); onSelect(); menuPosition = Offset(8f, 8f) }).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(fileIcon(node)), null, Modifier.size(58.dp))
            Text(displayName(node, showExtensions), color = Color.White, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        menuPosition?.let { point -> Box(Modifier.offset { IntOffset(point.x.roundToInt(), point.y.roundToInt()) }.size(1.dp)) {
            DropdownMenu(true, { menuPosition = null }) {
                DropdownMenuItem({ Text(stringResource(R.string.open)) }, { menuPosition = null; onOpen() })
                DropdownMenuItem({ Text(stringResource(R.string.rename)) }, { menuPosition = null; onRename() })
                DropdownMenuItem({ Text(stringResource(R.string.delete)) }, { menuPosition = null; onDelete() })
                DropdownMenuItem({ Text(stringResource(R.string.properties)) }, { menuPosition = null; onProperties() })
            }
        } }
    }
}

private fun fileTypeName(node: FileNode): String = when (node.kind) {
    FileKind.FOLDER -> "Папка"
    FileKind.TEXT -> "Документ"
    FileKind.PAINT -> "Зображення"
}

private fun loadSystemShortcutNames(context: Context): Map<String, String> =
    context.getSharedPreferences("desktop_system_shortcuts", Context.MODE_PRIVATE).all
        .mapNotNull { (id, value) -> (value as? String)?.let { id to it } }.toMap()

private fun saveSystemShortcutNames(context: Context, names: Map<String, String>) {
    context.getSharedPreferences("desktop_system_shortcuts", Context.MODE_PRIVATE).edit().clear().apply {
        names.forEach { (id, name) -> putString(id, name) }
    }.apply()
}

private fun loadHiddenSystemShortcuts(context: Context): Set<String> =
    context.getSharedPreferences("desktop_system_shortcuts", Context.MODE_PRIVATE).getStringSet("hidden", emptySet()).orEmpty()

private fun saveHiddenSystemShortcuts(context: Context, ids: Set<String>) {
    context.getSharedPreferences("desktop_system_shortcuts", Context.MODE_PRIVATE).edit().putStringSet("hidden", ids).apply()
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
