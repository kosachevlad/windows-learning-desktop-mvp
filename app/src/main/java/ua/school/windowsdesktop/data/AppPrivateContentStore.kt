package ua.school.windowsdesktop.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/** Immutable, content-addressed blobs. Only generated SHA-256 keys can be used as file paths. */
internal class AppPrivateContentStore(
    private val directory: File,
    private val hooks: PersistenceHooks,
) {
    init {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create content directory")
    }

    fun read(key: String): ByteArray {
        val bytes = blob(key).readBytes()
        if (digest(bytes) != key) throw IOException("Content integrity check failed")
        return bytes
    }

    fun write(bytes: ByteArray): String {
        val key = digest(bytes)
        val target = blob(key)
        if (target.exists()) {
            read(key) // Refuse to reuse a corrupt existing blob.
            return key
        }
        val pending = File(directory, ".pending-${UUID.randomUUID()}")
        try {
            FileOutputStream(pending).use { output ->
                output.write(bytes)
                hooks.beforeBlobSync()
                output.fd.sync()
            }
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            // Failure to delete a temporary file is retried by cleanup after a later open.
            pending.delete()
        }
        return key
    }

    fun cleanup(referenced: Set<String>) {
        hooks.beforeCleanup()
        val entries = directory.listFiles() ?: throw IOException("Cannot list content directory")
        for (entry in entries) {
            if (entry.isFile && ((KEY.matches(entry.name) && entry.name !in referenced) ||
                    entry.name.startsWith(".pending-"))) {
                if (!entry.delete()) throw IOException("Cannot clean unreferenced content")
            }
        }
    }

    private fun blob(key: String): File {
        if (!KEY.matches(key)) throw IOException("Invalid content key")
        return File(directory, key)
    }

    companion object {
        private val KEY = Regex("[a-f0-9]{64}")
        private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
