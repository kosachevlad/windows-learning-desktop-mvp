package ua.school.windowsdesktop.domain

/** App-private copy reference, never the operating system clipboard.
 * Paste reads the latest live source. Rename preserves the reference; deletion blocks paste.
 */
internal class Clipboard {
    var sourceId: String? = null
        private set

    fun copy(id: String) { sourceId = id }
    fun clear() { sourceId = null }
}
