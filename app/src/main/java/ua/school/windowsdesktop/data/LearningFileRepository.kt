package ua.school.windowsdesktop.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ua.school.windowsdesktop.domain.FileContent
import ua.school.windowsdesktop.domain.FileKind
import ua.school.windowsdesktop.domain.FileNode
import ua.school.windowsdesktop.domain.FileOperations
import ua.school.windowsdesktop.domain.FileSystemSnapshot

/**
 * Own one instance for the app lifetime. All I/O runs on Dispatchers.IO.
 * Write new immutable blobs -> commit all Room metadata -> publish the candidate snapshot.
 * Old blobs are only collected after a successful commit or validated reopen.
 */
class LearningFileRepository private constructor(
    private val database: LearningDatabase,
    private val contentStore: AppPrivateContentStore,
    private val lease: RepositoryLease,
    private val hooks: PersistenceHooks,
    private var operations: FileOperations,
) {
    private val mutex = Mutex()
    private var closed = false
    private val mutableSnapshots = MutableStateFlow(operations.snapshot())
    val snapshots: StateFlow<FileSystemSnapshot> = mutableSnapshots.asStateFlow()

    suspend fun children(parentId: String): List<FileNode> = access { it.children(parentId) }
    suspend fun trash(): List<FileNode> = access { it.trash() }
    suspend fun readText(id: String): String = access { it.readText(id) }
    suspend fun readPaint(id: String): ByteArray = access { it.readPaint(id) }
    suspend fun copy(id: String) = access { it.copy(id) }
    suspend fun clearClipboard() = access { it.clearClipboard() }

    suspend fun createFolder(name: String, parentId: String): FileNode = mutate { it.createFolder(name, parentId) }
    suspend fun createText(name: String, parentId: String, text: String = ""): FileNode =
        mutate { it.createText(name, parentId, text) }
    suspend fun createPaint(name: String, parentId: String, png: ByteArray): FileNode {
        val input = png.copyOf()
        return mutate { it.createPaint(name, parentId, input) }
    }
    suspend fun writeText(id: String, text: String): FileNode = mutate { it.writeText(id, text) }
    suspend fun writePaint(id: String, png: ByteArray): FileNode {
        val input = png.copyOf()
        return mutate { it.writePaint(id, input) }
    }
    suspend fun rename(id: String, name: String): FileNode = mutate { it.rename(id, name) }
    suspend fun paste(parentId: String, copyLabel: String = "копія"): FileNode = mutate { it.paste(parentId, copyLabel) }
    suspend fun moveToTrash(id: String): FileNode = mutate { it.moveToTrash(id) }
    suspend fun restore(id: String, newName: String? = null): FileNode = mutate { it.restore(id, newName) }

    suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            if (!closed) {
                closed = true
                try { database.close() } finally { lease.close() }
            }
        }
    }

    private suspend fun <T> access(action: (FileOperations) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed) { "Repository is closed" }
            action(operations)
        }
    }

    private suspend fun mutate(action: (FileOperations) -> FileNode): FileNode = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed) { "Repository is closed" }
            val candidate = operations.fork()
            val result = action(candidate)
            val referenced = persist(database, contentStore, hooks, candidate.snapshot())
            // No suspension point between the synchronous commit and publication.
            operations = candidate
            mutableSnapshots.value = candidate.snapshot()
            cleanupBestEffort(contentStore, referenced)
            result
        }
    }

    companion object {
        private const val CONTENT_DIRECTORY = "learning-content"

        suspend fun open(context: Context): LearningFileRepository = open(context, PersistenceHooks.NONE)

        internal suspend fun open(context: Context, hooks: PersistenceHooks): LearningFileRepository {
            var opened: LearningFileRepository? = null
            try {
                return withContext(Dispatchers.IO) {
                    val appContext = context.applicationContext
                    val directory = File(appContext.filesDir, CONTENT_DIRECTORY)
                    val lease = RepositoryLease.acquire(directory)
                    var database: LearningDatabase? = null
                    try {
                        // A missing DB with existing blobs is not an empty store: preserve it for recovery.
                        if (!appContext.getDatabasePath(LearningDatabase.NAME).exists() &&
                            directory.listFiles().orEmpty().any { it.name.matches(Regex("[a-f0-9]{64}")) }) {
                            throw IOException("Metadata database is missing; existing content was preserved")
                        }
                        val db = LearningDatabase.open(appContext)
                        database = db
                        val store = AppPrivateContentStore(directory, hooks)
                        val rows = db.files().all()
                        val initialized = db.files().initialized()
                        val operations: FileOperations
                        val referenced: Set<String>
                        if (!initialized && rows.isEmpty()) {
                            operations = FileOperations()
                            referenced = persist(db, store, hooks, operations.snapshot())
                        } else {
                            if (!initialized || rows.isEmpty()) throw IOException("Invalid metadata initialization state")
                            operations = load(rows, store)
                            referenced = rows.mapNotNull { it.contentKey }.toSet()
                        }
                        // Loading/validation must succeed before removing any unreferenced bytes.
                        cleanupBestEffort(store, referenced)
                        LearningFileRepository(db, store, lease, hooks, operations).also { opened = it }
                    } catch (error: Throwable) {
                        try { database?.close() } finally { lease.close() }
                        throw error
                    }
                }
            } catch (error: Throwable) {
                // withContext may discard its result on cancellation after opening resources.
                opened?.close()
                throw error
            }
        }

        private fun load(rows: List<FileEntity>, store: AppPrivateContentStore): FileOperations {
            try {
                val nodes = rows.associate { it.id to it.node() }
                val contents = mutableMapOf<String, FileContent>()
                for (row in rows) {
                    if (nodes.getValue(row.id).kind == FileKind.FOLDER) {
                        require(row.contentKey == null)
                    } else {
                        contents[row.id] = FileContent(store.read(requireNotNull(row.contentKey)))
                    }
                }
                return FileOperations(initialState = FileSystemSnapshot(nodes, contents))
            } catch (error: Exception) {
                throw IOException("Learning storage is unreadable; no data was reset", error)
            }
        }

        private fun persist(
            db: LearningDatabase,
            store: AppPrivateContentStore,
            hooks: PersistenceHooks,
            snapshot: FileSystemSnapshot,
        ): Set<String> {
            val keys = snapshot.contents.mapValues { (_, content) -> store.write(content.bytes()) }
            hooks.afterBlobsWritten()
            val rows = snapshot.nodes.values.map { FileEntity.from(it, keys[it.id]) }
            db.runInTransaction {
                db.files().deleteAll()
                db.files().insertAll(rows)
                db.files().markInitialized(StoreMarker())
                hooks.beforeMetadataCommit()
            }
            return keys.values.toSet()
        }

        private fun cleanupBestEffort(store: AppPrivateContentStore, referenced: Set<String>) {
            try { store.cleanup(referenced) } catch (error: Exception) {
                // Cleanup failure cannot turn an already committed save into a reported failure.
                Log.w("LearningFiles", "Content cleanup deferred until a later operation", error)
            }
        }
    }
}
