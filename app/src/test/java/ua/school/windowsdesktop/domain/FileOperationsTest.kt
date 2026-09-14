package ua.school.windowsdesktop.domain

import org.junit.Assert.*
import org.junit.Test

class FileOperationsTest {
    private var time = 1_000L
    private var id = 0
    private val files = FileOperations(clock = { time++ }, newId = { "node-${++id}" })
    private val root = FileOperations.ROOT_ID

    private fun expectError(code: FileError, action: () -> Unit) {
        val before = files.snapshot()
        val error = assertThrows(FileOperationException::class.java, action)
        assertEquals(code, error.code)
        assertEquals(before.nodes, files.snapshot().nodes)
        assertEquals(before.contents, files.snapshot().contents)
    }

    @Test fun creates_all_kinds_and_counts_encoded_bytes() {
        val folder = files.createFolder("Урок", root)
        val text = files.createText("Історія", folder.id, "Привіт 🌍")
        val png = byteArrayOf(1, 2, 3)
        val paint = files.createPaint("Малюнок.png", folder.id, png)
        assertEquals(FileKind.FOLDER, folder.kind)
        assertEquals("Історія.txt", text.name)
        assertEquals("Привіт 🌍".toByteArray(Charsets.UTF_8).size.toLong(), text.sizeBytes)
        assertEquals(FileKind.PAINT, paint.kind)
        assertEquals(3L, paint.sizeBytes)
        assertEquals(listOf(folder), files.children(root))
        assertEquals(setOf(text, paint), files.children(folder.id).toSet())
    }

    @Test fun rejects_invalid_names_without_changing_tree() {
        listOf("", " ", ".", "..", "a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b",
            "a<b", "a>b", "a|b", "a\nb", "name.", "name ", "CON", "nul.txt", "COM1", "LPT9", "a".repeat(256))
            .forEach { name -> expectError(FileError.INVALID_NAME) { files.createFolder(name, root) } }
        expectError(FileError.INVALID_EXTENSION) { files.createText("file.png", root) }
        expectError(FileError.INVALID_NAME) { files.createText("a".repeat(252), root) }
    }

    @Test fun sibling_names_are_case_insensitive_and_unicode_normalized() {
        files.createFolder("УРОК", root)
        expectError(FileError.NAME_CONFLICT) { files.createFolder("урок", root) }
        files.createFolder("Café", root)
        expectError(FileError.NAME_CONFLICT) { files.createFolder("Cafe\u0301", root) }
        val secondParent = files.createFolder("Інший", root)
        files.createFolder("урок", secondParent.id)
        assertEquals(1, files.children(secondParent.id).size)
    }

    @Test fun creation_requires_a_live_folder() {
        expectError(FileError.NOT_FOUND) { files.createFolder("A", "missing") }
        val text = files.createText("a.txt", root)
        expectError(FileError.NOT_A_FOLDER) { files.createFolder("A", text.id) }
        val folder = files.createFolder("Папка", root)
        files.moveToTrash(folder.id)
        expectError(FileError.IN_TRASH) { files.createText("a.txt", folder.id) }
    }

    @Test fun rename_preserves_identity_content_and_allows_case_change() {
        val text = files.createText("план.txt", root, "Текст")
        val renamed = files.rename(text.id, "ПЛАН.TXT")
        assertEquals(text.id, renamed.id)
        assertEquals("ПЛАН.TXT", renamed.name)
        assertEquals("Текст", files.readText(text.id))
        assertTrue(renamed.modifiedAt > text.modifiedAt)
        files.createText("Інший.txt", root)
        expectError(FileError.NAME_CONFLICT) { files.rename(text.id, "інший.txt") }
        expectError(FileError.INVALID_EXTENSION) { files.rename(text.id, "план.png") }
    }

    @Test fun saves_are_immutable_and_refresh_size_and_timestamp() {
        val text = files.createText("a.txt", root, "old")
        val old = files.snapshot()
        val saved = files.writeText(text.id, "нове")
        assertEquals("old", old.contents.getValue(text.id).utf8())
        assertEquals("нове", files.readText(text.id))
        assertEquals(8L, saved.sizeBytes)
        assertTrue(saved.modifiedAt > text.modifiedAt)
        expectError(FileError.WRONG_KIND) { files.writePaint(text.id, byteArrayOf(1)) }
    }

    @Test fun paint_bytes_are_defensively_copied_on_input_and_output() {
        val input = byteArrayOf(1, 2, 3)
        val paint = files.createPaint("a.png", root, input)
        input[0] = 9
        val output = files.readPaint(paint.id)
        output[1] = 9
        assertArrayEquals(byteArrayOf(1, 2, 3), files.readPaint(paint.id))
        val savedInput = byteArrayOf(4, 5)
        val saved = files.writePaint(paint.id, savedInput)
        savedInput[0] = 9
        assertArrayEquals(byteArrayOf(4, 5), files.readPaint(paint.id))
        assertEquals(2L, saved.sizeBytes)
        expectError(FileError.WRONG_KIND) { files.writeText(paint.id, "text") }
    }

    @Test fun paste_generates_unique_names_and_independent_content() {
        val original = files.createText("Історія.txt", root, "Привіт")
        files.copy(original.id)
        val first = files.paste(root)
        val second = files.paste(root)
        assertEquals("Історія (копія).txt", first.name)
        assertEquals("Історія (копія 2).txt", second.name)
        assertNotEquals(original.id, first.id)
        files.writeText(first.id, "Змінено")
        assertEquals("Привіт", files.readText(original.id))
        assertEquals("Привіт", files.readText(second.id))
    }

    @Test fun paste_to_another_folder_preserves_name_and_reads_latest_source() {
        val original = files.createText("a.txt", root, "old")
        files.copy(original.id)
        files.rename(original.id, "b.txt")
        files.writeText(original.id, "new")
        val target = files.createFolder("Target", root)
        val pasted = files.paste(target.id)
        assertEquals("b.txt", pasted.name)
        assertEquals("new", files.readText(pasted.id))
        assertEquals(target.id, pasted.parentId)
    }

    @Test fun recursive_copy_excludes_previously_trashed_children() {
        val folder = files.createFolder("Урок", root)
        val nested = files.createFolder("Вкладена", folder.id)
        val text = files.createText("a.txt", nested.id, "hello")
        val removed = files.createText("removed.txt", nested.id)
        files.moveToTrash(removed.id)
        files.copy(folder.id)
        val copied = files.paste(root)
        val copiedNested = files.children(copied.id).single()
        val copiedText = files.children(copiedNested.id).single()
        assertNotEquals(nested.id, copiedNested.id)
        assertNotEquals(text.id, copiedText.id)
        assertEquals("hello", files.readText(copiedText.id))
        assertEquals(listOf(removed.id), files.trash().map { it.id })
    }

    @Test fun invalid_clipboard_actions_leave_tree_unchanged() {
        expectError(FileError.EMPTY_CLIPBOARD) { files.paste(root) }
        val folder = files.createFolder("A", root)
        val nested = files.createFolder("B", folder.id)
        files.copy(folder.id)
        expectError(FileError.COPY_INTO_SELF) { files.paste(folder.id) }
        expectError(FileError.COPY_INTO_SELF) { files.paste(nested.id) }
        files.moveToTrash(folder.id)
        expectError(FileError.IN_TRASH) { files.paste(root) }
        files.clearClipboard()
        expectError(FileError.EMPTY_CLIPBOARD) { files.paste(root) }
    }

    @Test fun trash_hides_entire_subtree_and_restore_returns_it() {
        val folder = files.createFolder("A", root)
        val nested = files.createFolder("B", folder.id)
        val text = files.createText("a.txt", nested.id, "kept")
        files.moveToTrash(folder.id)
        assertTrue(files.children(root).isEmpty())
        assertEquals(listOf(folder.id), files.trash().map { it.id })
        expectError(FileError.IN_TRASH) { files.rename(text.id, "b.txt") }
        expectError(FileError.IN_TRASH) { files.writeText(text.id, "lost") }
        files.restore(folder.id)
        assertEquals("kept", files.readText(text.id))
        assertEquals(nested.id, files.snapshot().nodes.getValue(text.id).parentId)
        assertTrue(files.trash().isEmpty())
    }

    @Test fun restore_to_original_parent_and_conflict_does_not_overwrite() {
        val folder = files.createFolder("A", root)
        val text = files.createText("a.txt", folder.id, "original")
        files.moveToTrash(text.id)
        val replacement = files.createText("A.TXT", folder.id, "replacement")
        expectError(FileError.NAME_CONFLICT) { files.restore(text.id) }
        assertEquals("replacement", files.readText(replacement.id))
        val restored = files.restore(text.id, newName = "Відновлено.txt")
        assertEquals(folder.id, restored.parentId)
        assertEquals("original", files.readText(text.id))
    }

    @Test fun restore_falls_back_to_root_when_original_parent_is_trashed() {
        val folder = files.createFolder("A", root)
        val text = files.createText("a.txt", folder.id, "kept")
        files.moveToTrash(text.id)
        files.moveToTrash(folder.id)
        val restored = files.restore(text.id)
        assertEquals(root, restored.parentId)
        assertEquals("kept", files.readText(text.id))
        files.restore(folder.id)
        assertTrue(files.children(folder.id).isEmpty())
    }

    @Test fun independently_trashed_child_stays_trashed_after_parent_restore() {
        val folder = files.createFolder("A", root)
        val text = files.createText("a.txt", folder.id)
        files.moveToTrash(text.id)
        files.moveToTrash(folder.id)
        files.restore(folder.id)
        assertTrue(files.children(folder.id).isEmpty())
        assertEquals(listOf(text.id), files.trash().map { it.id })
        files.restore(text.id)
        assertEquals(listOf(text.id), files.children(folder.id).map { it.id })
    }

    @Test fun root_and_invalid_restore_are_protected() {
        expectError(FileError.ROOT_PROTECTED) { files.rename(root, "Renamed") }
        expectError(FileError.ROOT_PROTECTED) { files.moveToTrash(root) }
        expectError(FileError.ROOT_PROTECTED) { files.copy(root) }
        expectError(FileError.ROOT_PROTECTED) { files.restore(root) }
        val text = files.createText("a.txt", root)
        expectError(FileError.NOT_IN_TRASH) { files.restore(text.id) }
        expectError(FileError.NOT_FOUND) { files.rename("missing", "a.txt") }
    }

    @Test fun copy_label_can_be_english_and_long_names_stay_within_limit() {
        val text = files.createText("a".repeat(251) + ".txt", root)
        files.copy(text.id)
        val copied = files.paste(root, copyLabel = "copy")
        assertTrue(copied.name.endsWith(" (copy).txt"))
        assertTrue(copied.name.length <= 255)
    }

    @Test fun id_failure_during_recursive_paste_rolls_back_whole_operation() {
        var counter = 0
        val other = FileOperations(newId = {
            counter++
            if (counter == 4) error("ID generator unavailable")
            "id-$counter"
        })
        val folder = other.createFolder("A", root)
        other.createText("a.txt", folder.id, "text")
        other.copy(folder.id)
        val before = other.snapshot()
        assertThrows(IllegalStateException::class.java) { other.paste(root) }
        assertEquals(before.nodes, other.snapshot().nodes)
        assertEquals(before.contents, other.snapshot().contents)
    }

    @Test fun callers_cannot_modify_snapshot_maps() {
        val text = files.createText("a.txt", root, "kept")
        val snapshot = files.snapshot()
        assertThrows(UnsupportedOperationException::class.java) {
            (snapshot.nodes as MutableMap).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (snapshot.contents as MutableMap).remove(text.id)
        }
        assertEquals("kept", files.readText(text.id))
    }

    @Test fun repeated_generated_id_never_overwrites_existing_file() {
        val other = FileOperations(newId = { "same-id" })
        other.createText("a.txt", root, "kept")
        val before = other.snapshot()
        assertThrows(IllegalStateException::class.java) { other.createFolder("B", root) }
        assertEquals(before.nodes, other.snapshot().nodes)
        assertEquals(before.contents, other.snapshot().contents)
        assertEquals("kept", other.readText("same-id"))
    }

    @Test fun failed_copy_keeps_previous_clipboard_and_invalid_paste_target_is_rejected() {
        val text = files.createText("a.txt", root, "source")
        files.copy(text.id)
        expectError(FileError.NOT_FOUND) { files.copy("missing") }
        expectError(FileError.NOT_A_FOLDER) { files.paste(text.id) }
        assertEquals("source", files.readText(files.paste(root).id))
    }

    @Test fun root_fallback_conflict_keeps_deleted_file_recoverable() {
        val parent = files.createFolder("A", root)
        val deleted = files.createText("a.txt", parent.id, "original")
        files.moveToTrash(deleted.id)
        files.moveToTrash(parent.id)
        files.createText("A.TXT", root, "replacement")
        expectError(FileError.NAME_CONFLICT) { files.restore(deleted.id) }
        val restored = files.restore(deleted.id, newName = "restored")
        assertEquals(root, restored.parentId)
        assertEquals("restored.txt", restored.name)
        assertEquals("original", files.readText(restored.id))
    }
}
