package ua.school.windowsdesktop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.launch
import ua.school.windowsdesktop.data.LearningFileRepository
import ua.school.windowsdesktop.domain.*

private sealed interface AppScreen {
    data object Desktop : AppScreen
    data class Explorer(val folderId: String = FileOperations.ROOT_ID) : AppScreen
    data class Notepad(val fileId: String? = null) : AppScreen
    data object Trash : AppScreen
}

@Composable
fun WindowsLearningDesktopApp(repository: LearningFileRepository) {
    val snapshot by repository.snapshots.collectAsState()
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Desktop) }
    var error by remember { mutableStateOf<String?>(null) }
    var clipboardReady by remember { mutableStateOf(false) }
    MaterialTheme {
        when (val current = screen) {
            AppScreen.Desktop -> DesktopScreen(
                onFiles = { screen = AppScreen.Explorer() },
                onNotepad = { screen = AppScreen.Notepad() },
                onTrash = { screen = AppScreen.Trash },
            )
            is AppScreen.Explorer -> ExplorerScreen(repository, current.folderId, snapshot.nodes.values.toList(),
                onFolder = { screen = AppScreen.Explorer(it) }, onText = { screen = AppScreen.Notepad(it) },
                onDesktop = { screen = AppScreen.Desktop }, onError = { error = it },
                clipboardReady = clipboardReady, onClipboardReady = { clipboardReady = it })
            is AppScreen.Notepad -> NotepadScreen(repository, current.fileId?.let(snapshot.nodes::get), snapshot.nodes.values,
                onClose = { screen = AppScreen.Explorer(current.fileId?.let(snapshot.nodes::get)?.parentId ?: FileOperations.ROOT_ID) },
                onError = { error = it })
            AppScreen.Trash -> TrashScreen(repository, snapshot.nodes.values.toList(),
                onDesktop = { screen = AppScreen.Desktop }, onError = { error = it })
        }
        error?.let { message -> AlertDialog(onDismissRequest = { error = null },
            title = { Text(stringResource(R.string.error_title)) }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(R.string.ok)) } }) }
    }
}

@Composable private fun DesktopScreen(onFiles: () -> Unit, onNotepad: () -> Unit, onTrash: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF0078D7)).semantics { contentDescription = "Робочий стіл" },
        verticalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DesktopItem("▣", stringResource(R.string.this_pc), onFiles)
            DesktopItem("□", stringResource(R.string.my_files), onFiles)
            DesktopItem("▤", stringResource(R.string.notepad), onNotepad)
            DesktopItem("◩", "Paint", {})
            DesktopItem("♲", stringResource(R.string.recycle_bin), onTrash)
        }
        Row(Modifier.fillMaxWidth().background(Color(0xE61B1B1B)).padding(16.dp, 10.dp), Arrangement.SpaceBetween) {
            Text("⊞    ⌕    ▣    □    ▤    ◩", color = Color.White)
            Text("УКР    ${SimpleDateFormat("HH:mm").format(Date())}", color = Color.White)
        }
    }
}

@Composable private fun DesktopItem(symbol: String, label: String, action: () -> Unit) {
    Row(Modifier.width(190.dp).clickable(onClick = action).padding(vertical = 9.dp).semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically) {
        Text(symbol, color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.width(12.dp)); Text(label, color = Color.White)
    }
}

@Composable private fun ExplorerScreen(
    repository: LearningFileRepository, folderId: String, nodes: List<FileNode>,
    onFolder: (String) -> Unit, onText: (String) -> Unit, onDesktop: () -> Unit, onError: (String) -> Unit,
    clipboardReady: Boolean, onClipboardReady: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var createKind by remember { mutableStateOf<FileKind?>(null) }
    var newName by remember { mutableStateOf("") }
    var selectedId by remember(folderId) { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var deleteTarget by remember { mutableStateOf<FileNode?>(null) }
    var backgroundMenu by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val current = nodes.firstOrNull { it.id == folderId }
    val children = nodes.filter { it.parentId == folderId && it.trashedAt == null }.sortedBy { it.name.lowercase() }
    val selected = children.firstOrNull { it.id == selectedId }
    fun openNode(node: FileNode) { when (node.kind) {
        FileKind.FOLDER -> onFolder(node.id); FileKind.TEXT -> onText(node.id)
        FileKind.PAINT -> onError("Paint буде доступний у наступному етапі")
    } }
    fun openSelected() { selected?.let(::openNode) }
    fun copySelected() { selected?.let { node -> scope.launch { try { repository.copy(node.id); onClipboardReady(true) } catch (failure: Exception) { onError(errorMessage(failure)) } } } }
    fun paste() { scope.launch { try { repository.paste(folderId); selectedId = null } catch (failure: Exception) { onError(errorMessage(failure)) } } }
    fun goBack() { current?.parentId?.let(onFolder) ?: onDesktop() }
    BackHandler { goBack() }
    LaunchedEffect(folderId) { focusRequester.requestFocus() }

    Column(Modifier.fillMaxSize().background(Color(0xFFF4F4F4)).focusRequester(focusRequester).focusable()
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) false else when {
                event.isCtrlPressed && event.key == Key.C -> { copySelected(); true }
                event.isCtrlPressed && event.key == Key.V -> { if (clipboardReady) paste(); true }
                event.key == Key.F2 && selected != null -> { renameTarget = selected; newName = selected.name; true }
                event.key == Key.Delete && selected != null -> { deleteTarget = selected; true }
                event.key == Key.Enter && selected != null -> { openSelected(); true }
                else -> false
            }
        }) {
        WindowTitle(if (folderId == FileOperations.ROOT_ID) stringResource(R.string.my_files) else current?.name.orEmpty(), onDesktop)
        Row(Modifier.fillMaxWidth().background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = ::goBack) { Text("← ${stringResource(R.string.back)}") }
            TextButton(onClick = ::openSelected, enabled = selected != null) { Text(stringResource(R.string.open)) }
            TextButton(onClick = ::copySelected, enabled = selected != null) { Text(stringResource(R.string.copy)) }
            TextButton(onClick = ::paste, enabled = clipboardReady) { Text(stringResource(R.string.paste)) }
            TextButton(onClick = { selected?.let { renameTarget = it; newName = it.name } }, enabled = selected != null) { Text(stringResource(R.string.rename)) }
            TextButton(onClick = { selected?.let { deleteTarget = it } }, enabled = selected != null) { Text(stringResource(R.string.delete)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = { createKind = FileKind.FOLDER; newName = "" }) { Text(stringResource(R.string.new_folder)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { createKind = FileKind.TEXT; newName = "" }) { Text(stringResource(R.string.new_text_document)) }
        }
        Box(Modifier.fillMaxWidth().weight(1f).onSecondaryClick { selectedId = null; backgroundMenu = true }) {
            Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                FileHeader()
                if (children.isEmpty()) Text(stringResource(R.string.folder_empty), Modifier.padding(24.dp), color = Color.DarkGray)
                children.forEach { node -> FileRow(
                    node = node, selected = node.id == selectedId,
                    select = { selectedId = node.id }, open = { selectedId = node.id; openNode(node) },
                    copy = { selectedId = node.id; scope.launch { try { repository.copy(node.id); onClipboardReady(true) } catch (failure: Exception) { onError(errorMessage(failure)) } } },
                    rename = { selectedId = node.id; renameTarget = node; newName = node.name },
                    delete = { selectedId = node.id; deleteTarget = node },
                ) }
            }
            DropdownMenu(expanded = backgroundMenu, onDismissRequest = { backgroundMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.new_folder)) }, onClick = { backgroundMenu = false; createKind = FileKind.FOLDER; newName = "" })
                DropdownMenuItem(text = { Text(stringResource(R.string.new_text_document)) }, onClick = { backgroundMenu = false; createKind = FileKind.TEXT; newName = "" })
                DropdownMenuItem(text = { Text(stringResource(R.string.paste)) }, enabled = clipboardReady, onClick = { backgroundMenu = false; paste() })
            }
        }
    }
    createKind?.let { kind -> AlertDialog(onDismissRequest = { createKind = null },
        title = { Text(if (kind == FileKind.FOLDER) stringResource(R.string.new_folder) else stringResource(R.string.new_text_document)) },
        text = { OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text(stringResource(R.string.name)) }) },
        confirmButton = { TextButton(enabled = newName.isNotBlank(), onClick = {
            scope.launch { try {
                if (kind == FileKind.FOLDER) repository.createFolder(newName, folderId) else repository.createText(newName, folderId)
                createKind = null
            } catch (failure: Exception) { onError(errorMessage(failure)) } }
        }) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton(onClick = { createKind = null }) { Text(stringResource(R.string.cancel)) } }) }
    renameTarget?.let { target -> AlertDialog(onDismissRequest = { renameTarget = null },
        title = { Text(stringResource(R.string.rename)) },
        text = { OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text(stringResource(R.string.name)) }) },
        confirmButton = { TextButton(enabled = newName.isNotBlank(), onClick = { scope.launch { try {
            repository.rename(target.id, newName); renameTarget = null
        } catch (failure: Exception) { onError(errorMessage(failure)) } } }) { Text(stringResource(R.string.rename)) } },
        dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) } }) }
    deleteTarget?.let { target -> AlertDialog(onDismissRequest = { deleteTarget = null },
        title = { Text(stringResource(R.string.delete)) }, text = { Text(stringResource(R.string.delete_question, target.name)) },
        confirmButton = { TextButton(onClick = { scope.launch { try {
            repository.moveToTrash(target.id); selectedId = null; deleteTarget = null
        } catch (failure: Exception) { onError(errorMessage(failure)) } } }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable private fun FileHeader() = Row(Modifier.width(760.dp).background(Color(0xFFE5E5E5)).padding(vertical = 9.dp)) {
    Header(stringResource(R.string.name), 300); Header(stringResource(R.string.modified), 180)
    Header(stringResource(R.string.type), 160); Header(stringResource(R.string.size), 100)
}
@Composable private fun RowScope.Header(text: String, width: Int) = Text(text, Modifier.width(width.dp).padding(horizontal = 12.dp), fontWeight = FontWeight.SemiBold)

@Composable private fun FileRow(
    node: FileNode, selected: Boolean, select: () -> Unit, open: () -> Unit,
    copy: () -> Unit, rename: () -> Unit, delete: () -> Unit,
) {
    val type = when (node.kind) { FileKind.FOLDER -> stringResource(R.string.file_folder); FileKind.TEXT -> stringResource(R.string.text_document); FileKind.PAINT -> stringResource(R.string.paint_image) }
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.width(760.dp).onSecondaryClick { select(); menu = true }) {
        Row(Modifier.width(760.dp).background(if (selected) Color(0xFFCDE8FF) else Color.Transparent).clickable(onClick = select).padding(vertical = 10.dp).semantics { contentDescription = node.name }) {
            Text((if (node.kind == FileKind.FOLDER) "□  " else "▤  ") + node.name, Modifier.width(300.dp).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(node.modifiedAt)), Modifier.width(180.dp).padding(horizontal = 12.dp), maxLines = 1)
            Text(type, Modifier.width(160.dp).padding(horizontal = 12.dp), maxLines = 1)
            Text(if (node.kind == FileKind.FOLDER) "" else "${node.sizeBytes} B", Modifier.width(100.dp).padding(horizontal = 12.dp), maxLines = 1)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.open)) }, onClick = { menu = false; open() })
            DropdownMenuItem(text = { Text(stringResource(R.string.copy)) }, onClick = { menu = false; copy() })
            DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; rename() })
            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; delete() })
        }
    }
    HorizontalDivider(color = Color(0xFFE8E8E8))
}

@Composable private fun TrashScreen(
    repository: LearningFileRepository, nodes: List<FileNode>, onDesktop: () -> Unit, onError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val deleted = nodes.filter { it.trashedAt != null }.sortedByDescending { it.trashedAt }
    var permanentTarget by remember { mutableStateOf<FileNode?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    BackHandler(onBack = onDesktop)
    Column(Modifier.fillMaxSize().background(Color(0xFFF4F4F4))) {
        WindowTitle(stringResource(R.string.recycle_bin), onDesktop)
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp)) {
            TextButton(onClick = { confirmEmpty = true }, enabled = deleted.isNotEmpty()) {
                Text(stringResource(R.string.empty_recycle_bin))
            }
        }
        if (deleted.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.recycle_bin_empty)) }
        } else deleted.forEach { node ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((if (node.kind == FileKind.FOLDER) "□  " else "▤  ") + node.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { scope.launch { try { repository.restore(node.id) } catch (failure: Exception) { onError(errorMessage(failure)) } } }) {
                    Text(stringResource(R.string.restore))
                }
                TextButton(onClick = { permanentTarget = node }) { Text(stringResource(R.string.delete_permanently)) }
            }
            HorizontalDivider(color = Color(0xFFE0E0E0))
        }
    }
    permanentTarget?.let { target -> AlertDialog(
        onDismissRequest = { permanentTarget = null },
        title = { Text(stringResource(R.string.delete_permanently)) },
        text = { Text(stringResource(R.string.delete_permanently_question, target.name)) },
        confirmButton = { TextButton(onClick = { scope.launch { try {
            repository.deletePermanently(target.id); permanentTarget = null
        } catch (failure: Exception) { onError(errorMessage(failure)) } } }) { Text(stringResource(R.string.delete_permanently)) } },
        dismissButton = { TextButton(onClick = { permanentTarget = null }) { Text(stringResource(R.string.cancel)) } },
    ) }
    if (confirmEmpty) AlertDialog(
        onDismissRequest = { confirmEmpty = false },
        title = { Text(stringResource(R.string.empty_recycle_bin)) },
        text = { Text(stringResource(R.string.empty_recycle_bin_question)) },
        confirmButton = { TextButton(onClick = { scope.launch { try {
            repository.emptyTrash(); confirmEmpty = false
        } catch (failure: Exception) { onError(errorMessage(failure)) } } }) { Text(stringResource(R.string.empty_recycle_bin)) } },
        dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable private fun NotepadScreen(repository: LearningFileRepository, file: FileNode?, nodes: Collection<FileNode>, onClose: () -> Unit, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var currentFileId by remember(file?.id) { mutableStateOf(file?.id) }
    var currentName by remember(file?.id) { mutableStateOf(file?.name ?: "Новий текстовий документ.txt") }
    var text by remember(file?.id) { mutableStateOf("") }; var saved by remember(file?.id) { mutableStateOf("") }
    var loaded by remember(file?.id) { mutableStateOf(file == null) }; var closeRequested by remember { mutableStateOf(false) }
    val dirty = loaded && text != saved
    LaunchedEffect(file?.id) { if (file != null) try { repository.readText(file.id).let { text = it; saved = it; loaded = true } } catch (failure: Exception) { onError(errorMessage(failure)); onClose() } }
    fun save(after: () -> Unit = {}) { scope.launch { try {
        val id = currentFileId
        if (id == null) {
            val created = repository.createText(nextUntitledName(nodes), FileOperations.ROOT_ID, text)
            currentFileId = created.id; currentName = created.name
        } else repository.writeText(id, text)
        saved = text; after()
    } catch (failure: Exception) { onError(errorMessage(failure)) } } }
    fun requestClose() { if (dirty) closeRequested = true else onClose() }
    BackHandler { requestClose() }
    Column(Modifier.fillMaxSize().background(Color.White).onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.S) { save(); true } else false
    }) {
        WindowTitle(currentName + if (dirty) " *" else "", ::requestClose)
        Row(Modifier.fillMaxWidth().background(Color(0xFFF3F3F3)).padding(horizontal = 8.dp)) { TextButton(onClick = { save() }, enabled = loaded && dirty) { Text(stringResource(R.string.save)) } }
        if (!loaded) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else OutlinedTextField(text, { text = it }, Modifier.fillMaxSize().padding(8.dp).semantics { contentDescription = "Редактор тексту" }, textStyle = MaterialTheme.typography.bodyLarge)
    }
    if (closeRequested) AlertDialog(onDismissRequest = { closeRequested = false }, title = { Text(stringResource(R.string.save_changes_question)) },
        confirmButton = { TextButton(onClick = { save(onClose) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { Row { TextButton(onClick = onClose) { Text(stringResource(R.string.dont_save)) }; TextButton(onClick = { closeRequested = false }) { Text(stringResource(R.string.cancel)) } } })
}

@Composable private fun WindowTitle(title: String, onClose: () -> Unit) = Row(Modifier.fillMaxWidth().background(Color(0xFF1F4E79)).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    TextButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "Закрити" }) { Text("×", color = Color.White, style = MaterialTheme.typography.headlineSmall) }
}

private fun nextUntitledName(nodes: Collection<FileNode>): String {
    val names = nodes.filter { it.parentId == FileOperations.ROOT_ID && it.trashedAt == null }.map { it.name.lowercase() }.toSet()
    var number = 1
    while (true) { val candidate = if (number == 1) "Новий текстовий документ.txt" else "Новий текстовий документ ($number).txt"; if (candidate.lowercase() !in names) return candidate; number++ }
}

private fun Modifier.onSecondaryClick(action: () -> Unit): Modifier = pointerInput(action) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                event.changes.forEach { it.consume() }
                action()
            }
        }
    }
}

private fun errorMessage(failure: Exception): String = when ((failure as? FileOperationException)?.code) {
    FileError.NAME_CONFLICT -> "Об’єкт із таким ім’ям уже існує."
    FileError.INVALID_NAME, FileError.INVALID_EXTENSION -> "Це ім’я не можна використати."
    else -> failure.message ?: "Невідома помилка"
}
