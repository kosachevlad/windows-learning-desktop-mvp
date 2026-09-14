package ua.school.windowsdesktop.domain

import java.util.ArrayDeque
import java.util.UUID

/**
 * In-memory learning filesystem. Each successful operation publishes one immutable snapshot.
 * Failures publish nothing. This guarantees in-memory atomicity, not durability across restarts.
 * Public operations are serialized; a future repository owns disk transactions separately.
 */
class FileOperations(
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    initialState: FileSystemSnapshot? = null,
) {
    companion object { const val ROOT_ID = "root" }

    private val clipboard = Clipboard()
    @Volatile private var state = initialState?.also { it.validate() } ?: FileSystemSnapshot(
        mapOf(ROOT_ID to FileNode(ROOT_ID, null, "", FileKind.FOLDER, clock(), 0)),
        emptyMap(),
    )

    fun snapshot(): FileSystemSnapshot = state

    /** A repository mutates a candidate, then publishes it only after a successful disk commit. */
    @Synchronized internal fun fork(): FileOperations =
        FileOperations(clock, newId, state).also { candidate ->
            clipboard.sourceId?.let { candidate.clipboard.copy(it) }
        }

    @Synchronized fun children(parentId: String): List<FileNode> {
        liveFolder(parentId)
        return state.nodes.values.filter { it.parentId == parentId && it.trashedAt == null }
    }

    /** Only explicit deletion entries, not each descendant of a deleted folder. */
    @Synchronized fun trash(): List<FileNode> = state.nodes.values.filter { it.trashedAt != null }

    @Synchronized fun createFolder(name: String, parentId: String): FileNode =
        create(name, parentId, FileKind.FOLDER, null)

    @Synchronized fun createText(name: String, parentId: String, text: String = ""): FileNode =
        create(name, parentId, FileKind.TEXT, FileContent(text.toByteArray(Charsets.UTF_8)))

    /** PNG encoding/decoding belongs to Paint and the data layer; the domain stores opaque bytes. */
    @Synchronized fun createPaint(name: String, parentId: String, png: ByteArray): FileNode =
        create(name, parentId, FileKind.PAINT, FileContent(png))

    @Synchronized fun readText(id: String): String = content(id, FileKind.TEXT).utf8()

    @Synchronized fun readPaint(id: String): ByteArray = content(id, FileKind.PAINT).bytes()

    @Synchronized fun writeText(id: String, text: String): FileNode =
        write(id, FileKind.TEXT, FileContent(text.toByteArray(Charsets.UTF_8)))

    @Synchronized fun writePaint(id: String, png: ByteArray): FileNode =
        write(id, FileKind.PAINT, FileContent(png))

    @Synchronized fun rename(id: String, newName: String): FileNode {
        protectRoot(id)
        val original = liveNode(id)
        val name = FileNames.prepare(newName, original.kind)
        ensureAvailable(original.parentId!!, name, exceptId = id)
        return publishNode(original.copy(name = name, modifiedAt = clock()))
    }

    @Synchronized fun copy(id: String) {
        protectRoot(id)
        liveNode(id)
        clipboard.copy(id)
    }

    @Synchronized fun clearClipboard() { clipboard.clear() }

    @Synchronized fun paste(parentId: String, copyLabel: String = "копія"): FileNode {
        val source = liveNode(clipboard.sourceId ?: fail(FileError.EMPTY_CLIPBOARD))
        liveFolder(parentId)
        // A finite snapshot could be cloned into itself, but this action is deliberately disallowed.
        if (isWithin(parentId, source.id)) fail(FileError.COPY_INTO_SELF)
        var name = source.name
        var number = 1
        while (!available(parentId, name)) name = FileNames.copyName(source, copyLabel, number++)

        // Take a source traversal before creating nodes. No partial copies escape if IDs or clock fail.
        val originals = liveSubtree(source)
        val nodes = state.nodes.toMutableMap()
        val contents = state.contents.toMutableMap()
        val remapped = mutableMapOf<String, String>()
        val now = clock()
        for (original in originals) {
            val id = allocateId(nodes)
            remapped[original.id] = id
            val clone = original.copy(
                id = id,
                parentId = if (original.id == source.id) parentId else remapped.getValue(original.parentId!!),
                name = if (original.id == source.id) name else original.name,
                modifiedAt = now,
                trashedAt = null,
            )
            nodes[id] = clone
            // Content objects are immutable; later writes replace a single node's content entry.
            state.contents[original.id]?.let { contents[id] = it }
        }
        val result = nodes.getValue(remapped.getValue(source.id))
        state = FileSystemSnapshot(nodes, contents)
        return result
    }

    @Synchronized fun moveToTrash(id: String): FileNode {
        protectRoot(id)
        val original = liveNode(id)
        val now = clock()
        return publishNode(original.copy(trashedAt = now, modifiedAt = now))
    }

    /** Parent is preserved on deletion. Unavailable parent falls back to root; conflicts never overwrite. */
    @Synchronized fun restore(id: String, newName: String? = null): FileNode {
        protectRoot(id)
        val original = node(id)
        if (original.trashedAt == null) fail(FileError.NOT_IN_TRASH)
        val parent = original.parentId?.takeIf { candidate ->
            state.nodes[candidate]?.kind == FileKind.FOLDER && isLive(candidate)
        } ?: ROOT_ID
        val name = FileNames.prepare(newName ?: original.name, original.kind)
        ensureAvailable(parent, name, exceptId = id)
        return publishNode(original.copy(parentId = parent, name = name, trashedAt = null, modifiedAt = clock()))
    }

    /** Permanently remove one explicit Trash entry and its complete subtree. */
    @Synchronized fun deletePermanently(id: String): FileNode {
        protectRoot(id)
        val original = node(id)
        if (original.trashedAt == null) fail(FileError.NOT_IN_TRASH)
        val ids = subtreeIds(id)
        state = FileSystemSnapshot(state.nodes - ids, state.contents - ids)
        if (clipboard.sourceId in ids) clipboard.clear()
        return original
    }

    /** Remove every node hidden by Trash, including descendants of deleted folders. */
    @Synchronized fun emptyTrash(): Int {
        val ids = state.nodes.keys.filter { it != ROOT_ID && !isLive(it) }.toSet()
        if (ids.isEmpty()) return 0
        state = FileSystemSnapshot(state.nodes - ids, state.contents - ids)
        if (clipboard.sourceId in ids) clipboard.clear()
        return ids.size
    }

    private fun create(rawName: String, parentId: String, kind: FileKind, content: FileContent?): FileNode {
        liveFolder(parentId)
        val name = FileNames.prepare(rawName, kind)
        ensureAvailable(parentId, name)
        val result = FileNode(allocateId(state.nodes), parentId, name, kind, clock(), content?.sizeBytes ?: 0)
        state = FileSystemSnapshot(
            state.nodes + (result.id to result),
            if (content == null) state.contents else state.contents + (result.id to content),
        )
        return result
    }

    private fun content(id: String, kind: FileKind): FileContent {
        if (liveNode(id).kind != kind) fail(FileError.WRONG_KIND)
        return state.contents.getValue(id)
    }

    private fun write(id: String, kind: FileKind, content: FileContent): FileNode {
        val original = liveNode(id)
        if (original.kind != kind) fail(FileError.WRONG_KIND)
        val result = original.copy(sizeBytes = content.sizeBytes, modifiedAt = clock())
        state = FileSystemSnapshot(state.nodes + (id to result), state.contents + (id to content))
        return result
    }

    private fun publishNode(value: FileNode): FileNode {
        state = FileSystemSnapshot(state.nodes + (value.id to value), state.contents)
        return value
    }

    private fun allocateId(nodes: Map<String, FileNode>): String {
        val id = newId()
        check(id.isNotBlank() && id !in nodes) { "ID generator must return a new non-blank ID" }
        return id
    }

    private fun protectRoot(id: String) { if (id == ROOT_ID) fail(FileError.ROOT_PROTECTED) }
    private fun node(id: String): FileNode = state.nodes[id] ?: fail(FileError.NOT_FOUND)

    private fun liveNode(id: String): FileNode {
        val value = node(id)
        if (!isLive(id)) fail(FileError.IN_TRASH)
        return value
    }

    private fun liveFolder(id: String): FileNode = liveNode(id).also {
        if (it.kind != FileKind.FOLDER) fail(FileError.NOT_A_FOLDER)
    }

    private fun isLive(id: String): Boolean {
        var current: String? = id
        while (current != null) {
            val value = state.nodes[current] ?: return false
            if (value.trashedAt != null) return false
            current = value.parentId
        }
        return true
    }

    private fun isWithin(id: String, ancestor: String): Boolean {
        var current: String? = id
        while (current != null) {
            if (current == ancestor) return true
            current = node(current).parentId
        }
        return false
    }

    private fun available(parentId: String, name: String, exceptId: String? = null): Boolean {
        val key = FileNames.key(name)
        return state.nodes.values.none {
            it.parentId == parentId && it.trashedAt == null && it.id != exceptId && FileNames.key(it.name) == key
        }
    }

    private fun ensureAvailable(parentId: String, name: String, exceptId: String? = null) {
        if (!available(parentId, name, exceptId)) fail(FileError.NAME_CONFLICT)
    }

    private fun liveSubtree(root: FileNode): List<FileNode> {
        val byParent = state.nodes.values.filter { it.trashedAt == null }.groupBy { it.parentId }
        val result = mutableListOf<FileNode>()
        val pending = ArrayDeque<FileNode>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            result.add(current)
            byParent[current.id]?.forEach { pending.add(it) }
        }
        return result
    }

    private fun subtreeIds(rootId: String): Set<String> {
        val byParent = state.nodes.values.groupBy { it.parentId }
        val result = mutableSetOf<String>()
        val pending = ArrayDeque<String>()
        pending.add(rootId)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (result.add(current)) byParent[current]?.forEach { pending.add(it.id) }
        }
        return result
    }

    private fun fail(code: FileError): Nothing = throw FileOperationException(code)
}
