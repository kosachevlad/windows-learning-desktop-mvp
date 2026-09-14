package ua.school.windowsdesktop.data

/** Fault injection at real persistence boundaries; production uses the no-op implementation. */
internal interface PersistenceHooks {
    fun beforeBlobSync() {}
    fun afterBlobsWritten() {}
    fun beforeMetadataCommit() {}
    fun beforeCleanup() {}
    companion object { val NONE = object : PersistenceHooks {} }
}
