package ua.school.windowsdesktop.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ua.school.windowsdesktop.domain.FileKind
import ua.school.windowsdesktop.domain.FileNode

@Entity(tableName = "files", indices = [Index("parentId")])
internal data class FileEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val kind: String,
    val modifiedAt: Long,
    val sizeBytes: Long,
    val trashedAt: Long?,
    val contentKey: String?,
) {
    fun node() = FileNode(id, parentId, name, FileKind.valueOf(kind), modifiedAt, sizeBytes, trashedAt)
    companion object {
        fun from(node: FileNode, contentKey: String?) = FileEntity(
            node.id, node.parentId, node.name, node.kind.name, node.modifiedAt,
            node.sizeBytes, node.trashedAt, contentKey,
        )
    }
}

/** Distinguishes a new database from an initialized database whose root was lost. */
@Entity(tableName = "store_marker")
internal data class StoreMarker(@PrimaryKey val id: Int = 1)
