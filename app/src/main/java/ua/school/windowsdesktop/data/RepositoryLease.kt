package ua.school.windowsdesktop.data

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/** Keep one writer per store, including across processes; stale in-memory snapshots cannot overwrite it. */
internal class RepositoryLease private constructor(
    private val file: RandomAccessFile,
    private val lock: FileLock,
    private val path: String,
) : Closeable {
    override fun close() {
        try { lock.release() } finally {
            try { file.close() } finally { forget(path) }
        }
    }

    companion object {
        private val ownedPaths = mutableSetOf<String>()
        @Synchronized private fun forget(path: String) { ownedPaths.remove(path) }

        @Synchronized fun acquire(directory: File): RepositoryLease {
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create content directory")
            val path = File(directory, ".repository.lock").canonicalPath
            // Avoid opening a second descriptor in this process: on POSIX, closing it can
            // release this process's existing fcntl lock on the same underlying file.
            if (!ownedPaths.add(path)) throw IOException("Learning repository is already open")
            val file = try { RandomAccessFile(path, "rw") } catch (error: Throwable) {
                ownedPaths.remove(path)
                throw error
            }
            try {
                val lock = try { file.channel.tryLock() } catch (error: OverlappingFileLockException) {
                    throw IOException("Learning repository is already open", error)
                } ?: throw IOException("Learning repository is already open")
                return RepositoryLease(file, lock, path)
            } catch (error: Throwable) {
                try { file.close() } finally { ownedPaths.remove(path) }
                throw error
            }
        }
    }
}
