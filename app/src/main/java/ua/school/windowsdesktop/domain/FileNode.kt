package ua.school.windowsdesktop.domain

import java.util.Collections

enum class FileKind { FOLDER, TEXT, PAINT }

/** Timestamps are epoch milliseconds; folder size is 0 (no recursive size calculation). */
data class FileNode(
    val id: String,
    val parentId: String?,
    val name: String,
    val kind: FileKind,
    val modifiedAt: Long,
    val sizeBytes: Long,
    // Only a directly deleted node is marked; descendants inherit its hidden state.
    val trashedAt: Long? = null,
)

/** Immutable bytes: neither callers nor a later write can mutate an earlier snapshot. */
class FileContent(bytes: ByteArray) {
    private val value = bytes.copyOf()
    val sizeBytes: Long get() = value.size.toLong()
    fun bytes(): ByteArray = value.copyOf()
    fun utf8(): String = value.toString(Charsets.UTF_8)
    override fun equals(other: Any?): Boolean = other is FileContent && value.contentEquals(other.value)
    override fun hashCode(): Int = value.contentHashCode()
}

/** One in-memory commit containing both metadata and content; no disk persistence yet. */
class FileSystemSnapshot internal constructor(
    nodes: Map<String, FileNode>,
    contents: Map<String, FileContent>,
) {
    val nodes: Map<String, FileNode> = Collections.unmodifiableMap(LinkedHashMap(nodes))
    val contents: Map<String, FileContent> = Collections.unmodifiableMap(LinkedHashMap(contents))

    /** Validate loaded data before allowing traversal; corrupted parent links must never loop. */
    internal fun validate() {
        val root = nodes[FileOperations.ROOT_ID]
        require(root != null && root.id == FileOperations.ROOT_ID && root.parentId == null &&
            root.kind == FileKind.FOLDER && root.name.isEmpty() && root.trashedAt == null)
        require(contents.keys == nodes.values.filter { it.kind != FileKind.FOLDER }.map { it.id }.toSet())
        val siblingNames = mutableSetOf<Pair<String?, String>>()
        for ((id, node) in nodes) {
            require(id.isNotBlank() && id == node.id && node.sizeBytes >= 0)
            if (node.kind == FileKind.FOLDER) require(node.sizeBytes == 0L)
            else require(contents.getValue(id).sizeBytes == node.sizeBytes)
            if (id == FileOperations.ROOT_ID) continue
            require(node.name == FileNames.prepare(node.name, node.kind))
            require(nodes[node.parentId]?.kind == FileKind.FOLDER)
            if (node.trashedAt == null) require(siblingNames.add(node.parentId to FileNames.key(node.name)))
            val visited = mutableSetOf<String>()
            var current: FileNode? = node
            while (current != null) {
                require(visited.add(current.id)) { "Cyclic parent links" }
                current = current.parentId?.let { nodes.getValue(it) }
            }
        }
    }
}

enum class FileError {
    NOT_FOUND, NOT_A_FOLDER, IN_TRASH, NOT_IN_TRASH, ROOT_PROTECTED,
    INVALID_NAME, INVALID_EXTENSION, NAME_CONFLICT, EMPTY_CLIPBOARD,
    COPY_INTO_SELF, WRONG_KIND,
}

/** UI maps stable error codes to localized messages. */
class FileOperationException(val code: FileError) : IllegalArgumentException(code.name)
