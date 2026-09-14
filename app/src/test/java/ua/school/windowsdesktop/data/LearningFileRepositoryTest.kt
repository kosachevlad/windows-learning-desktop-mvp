package ua.school.windowsdesktop.data

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import ua.school.windowsdesktop.domain.FileError
import ua.school.windowsdesktop.domain.FileOperationException
import ua.school.windowsdesktop.domain.FileOperations

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LearningFileRepositoryTest {
    @get:Rule val temporary = TemporaryFolder(File(requireNotNull(System.getProperty("java.io.tmpdir"))))
    private lateinit var context: Context
    private val opened = mutableListOf<LearningFileRepository>()
    private val root = FileOperations.ROOT_ID
    private val contentDirectory get() = File(context.filesDir, "learning-content")

    @Before fun setUp() {
        // Robolectric's default dataDir includes the full test method name, exceeding
        // native SQLite path limits in long Windows checkouts. Only the paths are overridden.
        context = StoreTestContext(RuntimeEnvironment.getApplication(), temporary.newFolder("store"))
    }

    @After fun tearDown(): Unit = runBlocking { opened.reversed().forEach { it.close() } }

    private suspend fun open(hooks: PersistenceHooks = PersistenceHooks.NONE): LearningFileRepository =
        LearningFileRepository.open(context, hooks).also { opened.add(it) }

    private suspend fun <T : Throwable> failure(type: Class<T>, action: suspend () -> Unit): T {
        try { action() } catch (error: Throwable) {
            if (!type.isInstance(error)) throw AssertionError("Expected ${type.simpleName}, got $error", error)
            return type.cast(error)
        }
        throw AssertionError("Expected ${type.simpleName}")
    }

    private fun blobs(): List<File> = contentDirectory.listFiles().orEmpty()
        .filter { it.name.matches(Regex("[a-f0-9]{64}")) }

    private suspend fun editDatabase(action: (SupportSQLiteDatabase) -> Unit) = withContext(Dispatchers.IO) {
        val db = LearningDatabase.open(context)
        try { action(db.openHelper.writableDatabase) } finally { db.close() }
    }

    private class Faults : PersistenceHooks {
        var point: String? = null
        var atBlobs: () -> Unit = {}
        private fun check(name: String) { if (point == name) throw IOException("Injected failure: $name") }
        override fun beforeBlobSync() = check("sync")
        override fun afterBlobsWritten() { atBlobs(); check("blobs") }
        override fun beforeMetadataCommit() = check("transaction")
        override fun beforeCleanup() = check("cleanup")
    }

    private class StoreTestContext(base: Context, private val directory: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(directory, "files").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = File(directory, name)
        override fun deleteDatabase(name: String): Boolean = SQLiteDatabase.deleteDatabase(getDatabasePath(name))
        override fun openOrCreateDatabase(
            name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
        ): SQLiteDatabase = openOrCreateDatabase(name, mode, factory, null)
        override fun openOrCreateDatabase(
            name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?,
        ): SQLiteDatabase {
            val flags = SQLiteDatabase.CREATE_IF_NECESSARY or
                if (mode and MODE_ENABLE_WRITE_AHEAD_LOGGING != 0) SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING else 0
            return SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, flags, handler)
        }
    }

    @Test fun text_and_metadata_survive_reopening(): Unit = runBlocking {
        val repo = open()
        val folder = repo.createFolder("Урок", root)
        val text = repo.createText("Моя історія.txt", folder.id, "Привіт")
        val saved = repo.writeText(text.id, "Привіт 🌍\nДругий рядок")
        val before = repo.snapshots.value
        repo.close()
        val reopened = open()
        assertEquals("Привіт 🌍\nДругий рядок", reopened.readText(text.id))
        assertEquals(before.nodes, reopened.snapshots.value.nodes)
        assertEquals(before.contents, reopened.snapshots.value.contents)
        assertEquals(saved.sizeBytes, reopened.readText(text.id).toByteArray(Charsets.UTF_8).size.toLong())
        assertTrue(context.getDatabasePath(LearningDatabase.NAME).isFile)
        assertEquals(1, blobs().size)
    }

    @Test fun png_bytes_survive_reopening_and_copies_are_independent(): Unit = runBlocking {
        val png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )
        val repo = open()
        val paint = repo.createPaint("Малюнок.png", root, png)
        repo.copy(paint.id)
        val copy = repo.paste(root)
        repo.close()
        val reopened = open()
        assertArrayEquals(png, reopened.readPaint(paint.id))
        assertArrayEquals(png, reopened.readPaint(copy.id))
        assertEquals(png.size.toLong(), reopened.snapshots.value.nodes.getValue(copy.id).sizeBytes)
        // Identical content shares an immutable blob while retaining separate metadata identities.
        assertEquals(1, blobs().size)
    }

    @Test fun nested_copy_trash_and_restore_survive_restart(): Unit = runBlocking {
        val repo = open()
        val folder = repo.createFolder("Урок", root)
        val nested = repo.createFolder("Вкладена", folder.id)
        val text = repo.createText("a.txt", nested.id, "original")
        val deleted = repo.createText("deleted.txt", nested.id, "previously deleted")
        repo.moveToTrash(deleted.id)
        repo.copy(folder.id)
        val copied = repo.paste(root)
        val copiedText = repo.children(repo.children(copied.id).single().id).single()
        repo.writeText(copiedText.id, "copy")
        repo.moveToTrash(folder.id)
        repo.close()
        val reopened = open()
        assertEquals(setOf(folder.id, deleted.id), reopened.trash().map { it.id }.toSet())
        assertEquals("copy", reopened.readText(copiedText.id))
        reopened.restore(folder.id)
        assertEquals("original", reopened.readText(text.id))
        assertEquals(listOf(deleted.id), reopened.trash().map { it.id })
        reopened.restore(deleted.id)
        assertEquals("previously deleted", reopened.readText(deleted.id))
    }

    @Test fun blob_write_failure_preserves_previous_memory_and_disk_state(): Unit = runBlocking {
        val faults = Faults()
        val repo = open(faults)
        val text = repo.createText("a.txt", root, "old")
        val before = repo.snapshots.value
        faults.point = "sync"
        failure(IOException::class.java) { repo.writeText(text.id, "new") }
        assertSame(before, repo.snapshots.value)
        assertEquals("old", repo.readText(text.id))
        repo.close()
        assertEquals("old", open().readText(text.id))
        assertFalse(contentDirectory.listFiles().orEmpty().any { it.name.startsWith(".pending-") })
    }

    @Test fun failure_after_blob_publish_keeps_old_reference_and_reopen_collects_orphan(): Unit = runBlocking {
        val faults = Faults()
        val repo = open(faults)
        val text = repo.createText("a.txt", root, "old")
        val before = repo.snapshots.value
        faults.atBlobs = { assertSame(before, repo.snapshots.value) }
        faults.point = "blobs"
        failure(IOException::class.java) { repo.writeText(text.id, "uncommitted") }
        assertSame(before, repo.snapshots.value)
        assertEquals(2, blobs().size)
        repo.close()
        val reopened = open()
        assertEquals("old", reopened.readText(text.id))
        assertEquals(1, blobs().size)
    }

    @Test fun failure_inside_room_transaction_rolls_back_all_metadata(): Unit = runBlocking {
        val faults = Faults()
        val repo = open(faults)
        val text = repo.createText("a.txt", root, "old")
        val before = repo.snapshots.value
        faults.point = "transaction"
        failure(IOException::class.java) { repo.writeText(text.id, "new") }
        assertSame(before, repo.snapshots.value)
        repo.close()
        val reopened = open()
        assertEquals(before.nodes, reopened.snapshots.value.nodes)
        assertEquals("old", reopened.readText(text.id))
        assertEquals(1, blobs().size)
    }

    @Test fun cleanup_failure_does_not_report_a_committed_save_as_failed(): Unit = runBlocking {
        val faults = Faults()
        val repo = open(faults)
        val text = repo.createText("a.txt", root, "old")
        faults.point = "cleanup"
        repo.writeText(text.id, "committed")
        assertEquals("committed", repo.readText(text.id))
        assertEquals(2, blobs().size)
        repo.close()
        assertEquals("committed", open().readText(text.id))
        assertEquals(1, blobs().size)
    }

    @Test fun validation_failure_leaves_disk_and_flow_unchanged(): Unit = runBlocking {
        val repo = open()
        repo.createText("A.txt", root, "one")
        val second = repo.createText("B.txt", root, "two")
        val before = repo.snapshots.value
        val error = failure(FileOperationException::class.java) { repo.rename(second.id, "a.txt") }
        assertEquals(FileError.NAME_CONFLICT, error.code)
        assertSame(before, repo.snapshots.value)
        repo.close()
        assertEquals(before.nodes, open().snapshots.value.nodes)
    }

    @Test fun clipboard_survives_saves_but_is_not_persisted_across_reopen(): Unit = runBlocking {
        val repo = open()
        val text = repo.createText("a.txt", root, "old")
        repo.copy(text.id)
        repo.writeText(text.id, "new")
        assertEquals("new", repo.readText(repo.paste(root).id))
        repo.close()
        val reopened = open()
        val error = failure(FileOperationException::class.java) { reopened.paste(root) }
        assertEquals(FileError.EMPTY_CLIPBOARD, error.code)
    }

    @Test fun missing_blob_fails_open_without_reset_and_releases_writer_lease(): Unit = runBlocking {
        val repo = open()
        val text = repo.createText("a.txt", root, "original")
        repo.close()
        val blob = blobs().single()
        val bytes = blob.readBytes()
        assertTrue(blob.delete())
        failure(IOException::class.java) { open() }
        blob.writeBytes(bytes)
        assertEquals("original", open().readText(text.id))
    }

    @Test fun changed_blob_with_same_size_is_detected_by_hash(): Unit = runBlocking {
        val repo = open()
        val text = repo.createText("a.txt", root, "original")
        repo.close()
        val blob = blobs().single()
        val bytes = blob.readBytes()
        blob.writeBytes(bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })
        failure(IOException::class.java) { open() }
        assertTrue(blob.exists())
        blob.writeBytes(bytes)
        assertEquals("original", open().readText(text.id))
    }

    @Test fun cyclic_metadata_is_rejected_before_traversal_or_cleanup(): Unit = runBlocking {
        val repo = open()
        val folder = repo.createFolder("A", root)
        repo.createText("a.txt", folder.id, "kept")
        repo.close()
        editDatabase { it.execSQL("UPDATE files SET parentId = ? WHERE id = ?", arrayOf(folder.id, folder.id)) }
        failure(IOException::class.java) { open() }
        assertEquals(1, blobs().size)
    }

    @Test fun initialized_database_with_lost_rows_is_not_silently_reset(): Unit = runBlocking {
        val repo = open()
        repo.createText("a.txt", root, "kept")
        repo.close()
        editDatabase { it.execSQL("DELETE FROM files") }
        failure(IOException::class.java) { open() }
        assertEquals("kept", blobs().single().readText())
    }

    @Test fun missing_database_with_existing_content_is_not_recreated(): Unit = runBlocking {
        val repo = open()
        repo.createText("a.txt", root, "kept")
        repo.close()
        assertTrue(context.deleteDatabase(LearningDatabase.NAME))
        failure(IOException::class.java) { open() }
        assertFalse(context.getDatabasePath(LearningDatabase.NAME).exists())
        assertEquals("kept", blobs().single().readText())
    }

    @Test fun second_writer_is_rejected_and_close_is_idempotent(): Unit = runBlocking {
        val first = open()
        failure(IOException::class.java) { open() }
        first.close()
        first.close()
        val second = open()
        assertTrue(second.children(root).isEmpty())
        failure(IllegalStateException::class.java) { first.createText("a.txt", root) }
    }

    @Test fun concurrent_creates_do_not_lose_committed_files(): Unit = runBlocking {
        val repo = open()
        (1..12).map { index -> async { repo.createText("$index.txt", root, "text $index") } }.awaitAll()
        repo.close()
        val reopened = open()
        val children = reopened.children(root)
        assertEquals(12, children.size)
        for (node in children) assertEquals("text ${node.name.substringBefore('.')}", reopened.readText(node.id))
    }

    @Test fun failed_recursive_paste_commits_no_partial_folder(): Unit = runBlocking {
        val faults = Faults()
        val repo = open(faults)
        val folder = repo.createFolder("A", root)
        repo.createText("a.txt", folder.id, "kept")
        repo.copy(folder.id)
        val before = repo.snapshots.value
        faults.point = "transaction"
        failure(IOException::class.java) { repo.paste(root) }
        assertSame(before, repo.snapshots.value)
        repo.close()
        assertEquals(before.nodes, open().snapshots.value.nodes)
    }

    @Test fun cancelled_open_releases_its_database_and_lease(): Unit = runBlocking {
        val parent = Job()
        val hooks = object : PersistenceHooks {
            override fun afterBlobsWritten() { parent.cancel() }
        }
        val attempt = CoroutineScope(parent + Dispatchers.Default).async { open(hooks) }
        attempt.join()
        failure(CancellationException::class.java) { attempt.await() }
        assertTrue(open().children(root).isEmpty())
    }

    @Test fun pending_file_is_collected_only_after_validated_open(): Unit = runBlocking {
        val repo = open()
        val text = repo.createText("a.txt", root, "kept")
        repo.close()
        val pending = File(contentDirectory, ".pending-interrupted")
        pending.writeText("partial bytes")
        val reopened = open()
        assertFalse(pending.exists())
        assertEquals("kept", reopened.readText(text.id))
    }

    @Test fun persisted_content_key_cannot_escape_private_blob_directory(): Unit = runBlocking {
        val repo = open()
        val text = repo.createText("a.txt", root, "kept")
        repo.close()
        editDatabase {
            it.execSQL("UPDATE files SET contentKey = ? WHERE id = ?", arrayOf("../outside.txt", text.id))
        }
        failure(IOException::class.java) { open() }
        assertEquals("kept", blobs().single().readText())
    }

    @Test fun permanent_delete_and_empty_trash_survive_reopen_and_collect_blobs(): Unit = runBlocking {
        val repo = open()
        val first = repo.createText("first.txt", root, "first")
        val second = repo.createText("second.txt", root, "second")
        repo.moveToTrash(first.id)
        repo.moveToTrash(second.id)
        repo.deletePermanently(first.id)
        assertEquals(listOf(second.id), repo.trash().map { it.id })
        assertEquals(1, repo.emptyTrash())
        repo.close()
        val reopened = open()
        assertTrue(reopened.trash().isEmpty())
        assertEquals(0, blobs().size)
    }
}
