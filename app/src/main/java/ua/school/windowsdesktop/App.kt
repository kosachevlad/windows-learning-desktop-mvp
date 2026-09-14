package ua.school.windowsdesktop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
    data class Paint(val fileId: String? = null) : AppScreen
    data object Trash : AppScreen
}

@Composable
fun WindowsLearningDesktopApp(
    repository: LearningFileRepository,
    keyboardLanguage: String = "uk",
    onKeyboardLanguage: (String) -> Unit = {},
) {
    val snapshot by repository.snapshots.collectAsState()
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Desktop) }
    var error by remember { mutableStateOf<String?>(null) }
    var clipboardReady by remember { mutableStateOf(false) }
    var showFileExtensions by remember { mutableStateOf(false) }
    MaterialTheme {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val current = screen) {
                    AppScreen.Desktop -> DesktopScreen(
                        onFiles = { screen = AppScreen.Explorer() },
                        onNotepad = { screen = AppScreen.Notepad() },
                        onTrash = { screen = AppScreen.Trash },
                        onPaint = { screen = AppScreen.Paint() },
                        trashNotEmpty = snapshot.nodes.values.any { it.trashedAt != null },
                    )
                    is AppScreen.Explorer -> ExplorerScreen(repository, current.folderId, snapshot.nodes.values.toList(),
                        onFolder = { screen = AppScreen.Explorer(it) }, onText = { screen = AppScreen.Notepad(it) },
                        onPaint = { screen = AppScreen.Paint(it) },
                        onDesktop = { screen = AppScreen.Desktop }, onError = { error = it },
                        clipboardReady = clipboardReady, onClipboardReady = { clipboardReady = it },
                        showFileExtensions = showFileExtensions)
                    is AppScreen.Notepad -> NotepadScreen(repository, current.fileId?.let(snapshot.nodes::get), snapshot.nodes.values,
                        onClose = { screen = AppScreen.Explorer(current.fileId?.let(snapshot.nodes::get)?.parentId ?: FileOperations.ROOT_ID) },
                        onError = { error = it }, keyboardLanguage = keyboardLanguage,
                        onKeyboardLanguage = onKeyboardLanguage, showFileExtensions = showFileExtensions)
                    is AppScreen.Paint -> PaintScreen(repository, current.fileId?.let(snapshot.nodes::get), snapshot.nodes.values,
                        onClose = { screen = AppScreen.Explorer(current.fileId?.let(snapshot.nodes::get)?.parentId ?: FileOperations.ROOT_ID) },
                        onError = { error = it }, showFileExtensions = showFileExtensions)
                    AppScreen.Trash -> TrashScreen(repository, snapshot.nodes.values.toList(),
                        onDesktop = { screen = AppScreen.Desktop }, onError = { error = it },
                        showFileExtensions = showFileExtensions)
                }
            }
            Taskbar(
                keyboardLanguage = keyboardLanguage,
                onKeyboardLanguage = onKeyboardLanguage,
                onDesktop = { screen = AppScreen.Desktop },
                onFiles = { screen = AppScreen.Explorer() },
                onNotepad = { screen = AppScreen.Notepad() },
                onPaint = { screen = AppScreen.Paint() },
            )
        }
        error?.let { message -> AlertDialog(onDismissRequest = { error = null },
            title = { Text(stringResource(R.string.error_title)) }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(R.string.ok)) } }) }
    }
}

@Composable private fun DesktopScreen(
    onFiles: () -> Unit,
    onNotepad: () -> Unit,
    onTrash: () -> Unit,
    onPaint: () -> Unit,
    trashNotEmpty: Boolean,
) {
    Column(Modifier.fillMaxSize().background(Color(0xFF0078D7)).semantics { contentDescription = "Робочий стіл" }) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DesktopItem(R.drawable.this_computer, stringResource(R.string.this_pc), onFiles)
            DesktopItem(R.drawable.my_files, stringResource(R.string.my_files), onFiles)
            DesktopItem(R.drawable.notepad, stringResource(R.string.notepad), onNotepad)
            DesktopItem(R.drawable.paint, "Paint", onPaint)
            DesktopItem(if (trashNotEmpty) R.drawable.full_bin else R.drawable.empty_bin, stringResource(R.string.recycle_bin), onTrash)
        }
    }
}

@Composable private fun Taskbar(
    keyboardLanguage: String,
    onKeyboardLanguage: (String) -> Unit,
    onDesktop: () -> Unit,
    onFiles: () -> Unit,
    onNotepad: () -> Unit,
    onPaint: () -> Unit,
) {
    var languageMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(Color(0xE61B1B1B)).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDesktop) { Image(painterResource(R.drawable.start_btn), "Пуск", Modifier.size(32.dp)) }
        TextButton(onClick = {}) { Text("⌕", color = Color.White) }
        IconButton(onClick = onFiles) { Image(painterResource(R.drawable.my_files), stringResource(R.string.my_files), Modifier.size(30.dp)) }
        IconButton(onClick = onNotepad) { Image(painterResource(R.drawable.notepad), stringResource(R.string.notepad), Modifier.size(30.dp)) }
        IconButton(onClick = onPaint) { Image(painterResource(R.drawable.paint), "Paint", Modifier.size(30.dp)) }
        Spacer(Modifier.weight(1f))
        Box {
        TextButton(onClick = { languageMenu = true }) {
            Text(
                when (keyboardLanguage) {
                    "uk" -> "УКР"
                    "en" -> "ENG"
                    else -> "--"
                },
                color = Color.White,
            )
        }
            DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                DropdownMenuItem(text = { Text("Українська") }, onClick = { onKeyboardLanguage("uk"); languageMenu = false })
                DropdownMenuItem(text = { Text("English") }, onClick = { onKeyboardLanguage("en"); languageMenu = false })
            }
        }
        Text(SimpleDateFormat("HH:mm").format(Date()), color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable private fun DesktopItem(icon: Int, label: String, action: () -> Unit) {
    Row(Modifier.width(190.dp).clickable(onClick = action).padding(vertical = 9.dp).semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(icon), label, Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp)); Text(label, color = Color.White)
    }
}

@Composable private fun ExplorerScreen(
    repository: LearningFileRepository, folderId: String, nodes: List<FileNode>,
    onFolder: (String) -> Unit, onText: (String) -> Unit, onPaint: (String) -> Unit,
    onDesktop: () -> Unit, onError: (String) -> Unit,
    clipboardReady: Boolean, onClipboardReady: (Boolean) -> Unit,
    showFileExtensions: Boolean,
) {
    val scope = rememberCoroutineScope()
    var createKind by remember { mutableStateOf<FileKind?>(null) }
    var newName by remember { mutableStateOf("") }
    var selectedId by remember(folderId) { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var deleteTarget by remember { mutableStateOf<FileNode?>(null) }
    var backgroundMenuPosition by remember { mutableStateOf<Offset?>(null) }
    var pendingRevealId by remember { mutableStateOf<String?>(null) }
    val explorerScroll = rememberScrollState()
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val current = nodes.firstOrNull { it.id == folderId }
    val children = nodes.filter { it.parentId == folderId && it.trashedAt == null }.sortedBy { it.name.lowercase() }
    val selected = children.firstOrNull { it.id == selectedId }
    fun openNode(node: FileNode) { when (node.kind) {
        FileKind.FOLDER -> onFolder(node.id); FileKind.TEXT -> onText(node.id)
        FileKind.PAINT -> onPaint(node.id)
    } }
    fun openSelected() { selected?.let(::openNode) }
    fun copySelected() { selected?.let { node -> scope.launch { try { repository.copy(node.id); onClipboardReady(true) } catch (failure: Exception) { onError(errorMessage(failure)) } } } }
    fun paste() { scope.launch { try { repository.paste(folderId); selectedId = null } catch (failure: Exception) { onError(errorMessage(failure)) } } }
    fun submitCreate(kind: FileKind) { if (newName.isBlank()) return else scope.launch { try {
        val created = when (kind) {
            FileKind.FOLDER -> repository.createFolder(newName, folderId)
            FileKind.TEXT -> repository.createText(newName, folderId)
            FileKind.PAINT -> repository.createPaint(newName, folderId, blankPaintPng())
        }
        selectedId = created.id
        pendingRevealId = created.id
        createKind = null
    } catch (failure: Exception) { onError(errorMessage(failure)) } } }
    fun submitRename(target: FileNode) { if (newName.isBlank()) return else scope.launch { try {
        repository.rename(target.id, newName); renameTarget = null
    } catch (failure: Exception) { onError(errorMessage(failure)) } } }
    fun goBack() { current?.parentId?.let(onFolder) ?: onDesktop() }
    BackHandler { goBack() }
    LaunchedEffect(folderId) { focusRequester.requestFocus() }
    LaunchedEffect(children.map { it.id }, pendingRevealId) {
        val target = pendingRevealId ?: return@LaunchedEffect
        val index = children.indexOfFirst { it.id == target }
        if (index >= 0) {
            val rowHeight = with(density) { 45.dp.toPx() }
            explorerScroll.animateScrollTo((index * rowHeight).roundToInt())
            pendingRevealId = null
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF4F4F4)).focusRequester(focusRequester).focusable()
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) false else when {
                event.isCtrlPressed && event.key == Key.C -> { copySelected(); true }
                event.isCtrlPressed && event.key == Key.V -> { if (clipboardReady) paste(); true }
                event.key == Key.F2 && selected != null -> { renameTarget = selected; newName = displayName(selected, showFileExtensions); true }
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
            TextButton(onClick = { selected?.let { renameTarget = it; newName = displayName(it, showFileExtensions) } }, enabled = selected != null) { Text(stringResource(R.string.rename)) }
            TextButton(onClick = { selected?.let { deleteTarget = it } }, enabled = selected != null) { Text(stringResource(R.string.delete)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = { createKind = FileKind.FOLDER; newName = "" }) { Text(stringResource(R.string.new_folder)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { createKind = FileKind.TEXT; newName = "" }) { Text(stringResource(R.string.new_text_document)) }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSecondaryClick { position ->
                    selectedId = null
                    backgroundMenuPosition = position
                }
        ) {
            Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).verticalScroll(explorerScroll)) {
                FileHeader()

                if (children.isEmpty()) {
                    Text(
                        stringResource(R.string.folder_empty),
                        Modifier.padding(24.dp),
                        color = Color.DarkGray,
                    )
                }

                children.forEach { node ->
                    FileRow(
                        node = node,
                        showFileExtensions = showFileExtensions,
                        selected = node.id == selectedId,
                        select = { selectedId = node.id },
                        open = {
                            selectedId = node.id
                            openNode(node)
                        },
                        copy = {
                            selectedId = node.id
                            scope.launch {
                                try {
                                    repository.copy(node.id)
                                    onClipboardReady(true)
                                } catch (failure: Exception) {
                                    onError(errorMessage(failure))
                                }
                            }
                        },
                        rename = {
                            selectedId = node.id
                            renameTarget = node
                            newName = displayName(node, showFileExtensions)
                        },
                        delete = {
                            selectedId = node.id
                            deleteTarget = node
                        },
                    )
                }
            }
            
            backgroundMenuPosition?.let { position ->
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                position.x.roundToInt(),
                                position.y.roundToInt(),
                            )
                        }
                        .size(1.dp)
                ) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { backgroundMenuPosition = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_folder)) },
                            onClick = {
                                backgroundMenuPosition = null
                                createKind = FileKind.FOLDER
                                newName = ""
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_text_document)) },
                            onClick = {
                                backgroundMenuPosition = null
                                createKind = FileKind.TEXT
                                newName = ""
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_bitmap_image)) },
                            onClick = {
                                backgroundMenuPosition = null
                                createKind = FileKind.PAINT
                                newName = ""
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.paste)) },
                            enabled = clipboardReady,
                            onClick = {
                                backgroundMenuPosition = null
                                paste()
                            },
                        )
                    }
                }
            }
            VerticalScrollIndicator(explorerScroll, Modifier.align(Alignment.CenterEnd))
        }
    }
    createKind?.let { kind -> AlertDialog(
        modifier = Modifier.dialogKeys(newName.isNotBlank(), { submitCreate(kind) }, { createKind = null }),
        onDismissRequest = { createKind = null },
        title = { Text(when (kind) {
            FileKind.FOLDER -> stringResource(R.string.new_folder)
            FileKind.TEXT -> stringResource(R.string.new_text_document)
            FileKind.PAINT -> stringResource(R.string.new_bitmap_image)
        }) },
        text = { OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text(stringResource(R.string.name)) }) },
        confirmButton = { TextButton(enabled = newName.isNotBlank(), onClick = { submitCreate(kind) }) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton(onClick = { createKind = null }) { Text(stringResource(R.string.cancel)) } }) }
    renameTarget?.let { target -> AlertDialog(
        modifier = Modifier.dialogKeys(newName.isNotBlank(), { submitRename(target) }, { renameTarget = null }),
        onDismissRequest = { renameTarget = null },
        title = { Text(stringResource(R.string.rename)) },
        text = { OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text(stringResource(R.string.name)) }) },
        confirmButton = { TextButton(enabled = newName.isNotBlank(), onClick = { submitRename(target) }) { Text(stringResource(R.string.rename)) } },
        dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) } }) }
    deleteTarget?.let { target -> AlertDialog(onDismissRequest = { deleteTarget = null },
        title = { Text(stringResource(R.string.delete)) }, text = { Text(stringResource(R.string.delete_question, displayName(target, showFileExtensions))) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun FileRow(
    node: FileNode, showFileExtensions: Boolean, selected: Boolean, select: () -> Unit, open: () -> Unit,
    copy: () -> Unit, rename: () -> Unit, delete: () -> Unit,
) {
    val type = when (node.kind) {
        FileKind.FOLDER -> stringResource(R.string.file_folder)
        FileKind.TEXT -> stringResource(R.string.text_document)
        FileKind.PAINT -> stringResource(R.string.paint_image)
    }
    var menuPosition by remember { mutableStateOf<Offset?>(null) }
    Box(
        Modifier
            .width(760.dp)
            .onSecondaryClick(PointerEventPass.Initial) { position ->
                select()
                menuPosition = position
            }
    ) {
        Row(
            Modifier
                .width(760.dp)
                .background(if (selected) Color(0xFFCDE8FF) else Color.Transparent)
                .combinedClickable(onClick = select, onDoubleClick = open)
                .padding(vertical = 10.dp)
                .semantics { contentDescription = displayName(node, showFileExtensions) }
        ) {
            Row(Modifier.width(300.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(fileIcon(node)), null, Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text(displayName(node, showFileExtensions), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(node.modifiedAt)),
                Modifier.width(180.dp).padding(horizontal = 12.dp),
                maxLines = 1,
            )
            Text(type, Modifier.width(160.dp).padding(horizontal = 12.dp), maxLines = 1)
            Text(
                if (node.kind == FileKind.FOLDER) "" else "${node.sizeBytes} B",
                Modifier.width(100.dp).padding(horizontal = 12.dp),
                maxLines = 1,
            )
        }
        menuPosition?.let { position -> Box(
            Modifier.offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }.size(1.dp)
        ) {
            DropdownMenu(expanded = true, onDismissRequest = { menuPosition = null }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.open)) }, onClick = { menuPosition = null; open() })
                DropdownMenuItem(text = { Text(stringResource(R.string.copy)) }, onClick = { menuPosition = null; copy() })
                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menuPosition = null; rename() })
                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuPosition = null; delete() })
            }
        }
    }
    }
    HorizontalDivider(color = Color(0xFFE8E8E8))
}

@Composable private fun TrashScreen(
    repository: LearningFileRepository, nodes: List<FileNode>, onDesktop: () -> Unit, onError: (String) -> Unit,
    showFileExtensions: Boolean,
) {
    val scope = rememberCoroutineScope()
    val deleted = nodes.filter { it.trashedAt != null }.sortedByDescending { it.trashedAt }
    var permanentTarget by remember { mutableStateOf<FileNode?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val trashScroll = rememberScrollState()
    BackHandler(onBack = onDesktop)
    Column(Modifier.fillMaxSize().background(Color(0xFFF4F4F4))) {
        WindowTitle(stringResource(R.string.recycle_bin), onDesktop)
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp)) {
            TextButton(onClick = { confirmEmpty = true }, enabled = deleted.isNotEmpty()) {
                Text(stringResource(R.string.empty_recycle_bin))
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (deleted.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.recycle_bin_empty)) }
            } else Column(Modifier.fillMaxWidth().verticalScroll(trashScroll)) {
                deleted.forEach { node ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(fileIcon(node)), null, Modifier.size(28.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(displayName(node, showFileExtensions), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { scope.launch { try { repository.restore(node.id) } catch (failure: Exception) { onError(errorMessage(failure)) } } }) {
                            Text(stringResource(R.string.restore))
                        }
                        TextButton(onClick = { permanentTarget = node }) { Text(stringResource(R.string.delete_permanently)) }
                    }
                    HorizontalDivider(color = Color(0xFFE0E0E0))
                }
            }
            VerticalScrollIndicator(trashScroll, Modifier.align(Alignment.CenterEnd))
        }
    }
    permanentTarget?.let { target -> AlertDialog(
        onDismissRequest = { permanentTarget = null },
        title = { Text(stringResource(R.string.delete_permanently)) },
        text = { Text(stringResource(R.string.delete_permanently_question, displayName(target, showFileExtensions))) },
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

@Composable private fun NotepadScreen(
    repository: LearningFileRepository, file: FileNode?, nodes: Collection<FileNode>,
    onClose: () -> Unit, onError: (String) -> Unit,
    keyboardLanguage: String, onKeyboardLanguage: (String) -> Unit,
    showFileExtensions: Boolean,
) {
    val scope = rememberCoroutineScope()
    var currentFileId by remember(file?.id) { mutableStateOf(file?.id) }
    var currentName by remember(file?.id) { mutableStateOf(file?.name ?: "Новий текстовий документ.txt") }
    var text by remember(file?.id) { mutableStateOf("") }; var saved by remember(file?.id) { mutableStateOf("") }
    var loaded by remember(file?.id) { mutableStateOf(file == null) }; var closeRequested by remember { mutableStateOf(false) }
    var saveAsRequested by remember { mutableStateOf(false) }
    var saveAsName by remember { mutableStateOf("") }
    val parentId = file?.parentId ?: FileOperations.ROOT_ID
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
    fun requestSaveAs() { saveAsName = displayFileName(currentName, FileKind.TEXT, showFileExtensions); saveAsRequested = true }
    fun submitSaveAs() { if (saveAsName.isBlank()) return else scope.launch { try {
        val created = repository.createText(saveAsName, parentId, text)
        currentFileId = created.id; currentName = created.name; saved = text; saveAsRequested = false
    } catch (failure: Exception) { onError(errorMessage(failure)) } } }
    BackHandler { requestClose() }
    Column(Modifier.fillMaxSize().background(Color.White).onPreviewKeyEvent { event ->
        when {
            event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_SPACE -> {
                if (keyboardLanguage != "unknown") onKeyboardLanguage(if (keyboardLanguage == "uk") "en" else "uk")
                false
            }
            event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.S -> { save(); true }
            else -> false
        }
    }) {
        WindowTitle(displayFileName(currentName, FileKind.TEXT, showFileExtensions) + if (dirty) " *" else "", ::requestClose)
        Row(Modifier.fillMaxWidth().background(Color(0xFFF3F3F3)).padding(horizontal = 8.dp)) {
            TextButton(onClick = { save() }, enabled = loaded && dirty) { Text(stringResource(R.string.save)) }
            TextButton(onClick = ::requestSaveAs, enabled = loaded) { Text(stringResource(R.string.save_as)) }
        }
        if (!loaded) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else OutlinedTextField(
            text,
            { updated ->
                if (updated.length > text.length) {
                    updated.lastOrNull { it.isLetter() }?.let { character ->
                        when {
                            character in 'А'..'я' || character == 'І' || character == 'і' || character == 'Ї' || character == 'ї' || character == 'Є' || character == 'є' || character == 'Ґ' || character == 'ґ' -> onKeyboardLanguage("uk")
                            character in 'A'..'Z' || character in 'a'..'z' -> onKeyboardLanguage("en")
                        }
                    }
                }
                text = updated
            },
            Modifier.fillMaxSize().padding(8.dp).semantics { contentDescription = "Редактор тексту" },
            textStyle = MaterialTheme.typography.bodyLarge,
        )
    }
    if (closeRequested) AlertDialog(onDismissRequest = { closeRequested = false }, title = { Text(stringResource(R.string.save_changes_question)) },
        confirmButton = { TextButton(onClick = { save(onClose) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { Row { TextButton(onClick = onClose) { Text(stringResource(R.string.dont_save)) }; TextButton(onClick = { closeRequested = false }) { Text(stringResource(R.string.cancel)) } } })
    if (saveAsRequested) AlertDialog(
        modifier = Modifier.dialogKeys(saveAsName.isNotBlank(), ::submitSaveAs) { saveAsRequested = false },
        onDismissRequest = { saveAsRequested = false },
        title = { Text(stringResource(R.string.save_as)) },
        text = { OutlinedTextField(saveAsName, { saveAsName = it }, singleLine = true, label = { Text(stringResource(R.string.name)) }) },
        confirmButton = { TextButton(enabled = saveAsName.isNotBlank(), onClick = ::submitSaveAs) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { saveAsRequested = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable internal fun WindowTitle(title: String, onClose: () -> Unit) = Row(Modifier.fillMaxWidth().background(Color(0xFF1F4E79)).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    TextButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "Закрити" }) { Text("×", color = Color.White, style = MaterialTheme.typography.headlineSmall) }
}

private fun nextUntitledName(nodes: Collection<FileNode>): String {
    val names = nodes.filter { it.parentId == FileOperations.ROOT_ID && it.trashedAt == null }.map { it.name.lowercase() }.toSet()
    var number = 1
    while (true) { val candidate = if (number == 1) "Новий текстовий документ.txt" else "Новий текстовий документ ($number).txt"; if (candidate.lowercase() !in names) return candidate; number++ }
}

internal fun displayName(node: FileNode, showFileExtensions: Boolean): String =
    displayFileName(node.name, node.kind, showFileExtensions)

internal fun displayFileName(name: String, kind: FileKind, showFileExtensions: Boolean): String {
    if (showFileExtensions || kind == FileKind.FOLDER) return name
    val extension = when (kind) { FileKind.TEXT -> ".txt"; FileKind.PAINT -> ".png"; FileKind.FOLDER -> "" }
    return if (name.endsWith(extension, ignoreCase = true)) name.dropLast(extension.length) else name
}

private fun fileIcon(node: FileNode): Int = when (node.kind) {
    FileKind.FOLDER -> R.drawable.folder_icon
    FileKind.TEXT -> R.drawable.text_icon
    FileKind.PAINT -> R.drawable.image_icon
}

private fun Modifier.onSecondaryClick(
    pass: PointerEventPass = PointerEventPass.Main,
    action: (Offset) -> Unit,
): Modifier =
    pointerInput(pass, action) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(pass)

                if (
                    event.type == PointerEventType.Press &&
                    event.buttons.isSecondaryPressed &&
                    event.changes.none { it.isConsumed }
                ) {
                    val position = event.changes.first().position
                    event.changes.forEach { it.consume() }
                    action(position)
                }
            }
        }
    }

internal fun Modifier.dialogKeys(
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) false else when (event.key) {
        Key.Enter -> { if (confirmEnabled) onConfirm(); true }
        Key.Escape -> { onCancel(); true }
        else -> false
    }
}

@Composable private fun VerticalScrollIndicator(state: ScrollState, modifier: Modifier = Modifier) {
    val maxScroll = state.maxValue
    if (maxScroll <= 0) return
    BoxWithConstraints(modifier.fillMaxHeight().width(8.dp).background(Color(0x22000000))) {
        val viewportPx = constraints.maxHeight.toFloat()
        val totalPx = viewportPx + maxScroll
        val minimumPx = with(LocalDensity.current) { 32.dp.toPx() }
        val thumbPx = (viewportPx * viewportPx / totalPx).coerceAtLeast(minimumPx).coerceAtMost(viewportPx)
        val offsetPx = (viewportPx - thumbPx) * state.value.coerceIn(0, maxScroll).toFloat() / maxScroll.toFloat()
        Box(
            Modifier.offset { IntOffset(0, if (offsetPx.isFinite()) offsetPx.roundToInt() else 0) }
                .fillMaxWidth()
                .height(with(LocalDensity.current) { thumbPx.toDp() })
                .background(Color(0x99000000))
        )
    }
}

private fun errorMessage(failure: Exception): String = when ((failure as? FileOperationException)?.code) {
    FileError.NAME_CONFLICT -> "Об’єкт із таким ім’ям уже існує."
    FileError.INVALID_NAME, FileError.INVALID_EXTENSION -> "Це ім’я не можна використати."
    else -> failure.message ?: "Невідома помилка"
}
