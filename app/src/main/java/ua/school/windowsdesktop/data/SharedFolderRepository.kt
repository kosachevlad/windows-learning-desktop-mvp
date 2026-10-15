package ua.school.windowsdesktop.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

data class SharedFolderEntry(val uri: Uri, val name: String, val mimeType: String?, val isDirectory: Boolean, val size: Long?)

/** SAF-only bridge for the user-selected Android folder. It never touches the private learning store. */
class SharedFolderRepository(context: Context) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val preferences = context.applicationContext.getSharedPreferences("shared-folder", Context.MODE_PRIVATE)
    var treeUri: Uri?
        get() = preferences.getString(KEY_URI, null)?.let(Uri::parse)
        private set(value) { preferences.edit().putString(KEY_URI, value?.toString()).apply() }

    fun remember(uri: Uri) {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        treeUri = uri
    }

    fun forget() { treeUri = null }

    fun hasAccess(): Boolean = treeUri?.let { uri ->
        resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
    } == true

    fun list(): List<SharedFolderEntry> {
        val tree = treeUri ?: return emptyList()
        if (!hasAccess()) return emptyList()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val result = mutableListOf<SharedFolderEntry>()
        resolver.query(children, PROJECTION, null, null, "document_id")?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val name = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mime = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val size = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(id))
                val mimeType = cursor.getString(mime)
                result += SharedFolderEntry(documentUri, cursor.getString(name), mimeType, mimeType == DocumentsContract.Document.MIME_TYPE_DIR, if (size >= 0 && !cursor.isNull(size)) cursor.getLong(size) else null)
            }
        }
        return result
    }

    private companion object {
        const val KEY_URI = "tree_uri"
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }
}
