package ua.school.windowsdesktop.domain

import java.text.Normalizer
import java.util.Locale

internal object FileNames {
    private val forbidden = "\\/:*?\"<>|".toSet()
    private val reserved = Regex("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]", RegexOption.IGNORE_CASE)

    fun key(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFC).lowercase(Locale.ROOT)

    fun prepare(raw: String, kind: FileKind): String {
        var name = Normalizer.normalize(raw, Normalizer.Form.NFC)
        validate(name)
        val extension = when (kind) {
            FileKind.FOLDER -> ""
            FileKind.TEXT -> ".txt"
            FileKind.PAINT -> ".png"
        }
        if (extension.isNotEmpty() && !name.endsWith(extension, ignoreCase = true)) {
            if ('.' in name) throw FileOperationException(FileError.INVALID_EXTENSION)
            name += extension
        }
        validate(name)
        return name
    }

    private fun validate(name: String) {
        if (name.isBlank() || name.length > 255 || name.endsWith('.') || name.endsWith(' ') ||
            name.any { it.code < 32 || it in forbidden } || reserved.matches(name.substringBefore('.'))
        ) throw FileOperationException(FileError.INVALID_NAME)
    }

    fun copyName(original: FileNode, label: String, number: Int): String {
        validate(label)
        val extension = if (original.kind == FileKind.FOLDER) "" else original.name.takeLast(4)
        val stem = original.name.dropLast(extension.length)
        val suffix = if (number == 1) " ($label)" else " ($label $number)"
        val available = 255 - suffix.length - extension.length
        if (available < 1) throw FileOperationException(FileError.INVALID_NAME)
        var shortened = stem.take(available)
        // Do not split a UTF-16 surrogate pair when truncating a long name.
        if (shortened.lastOrNull()?.isHighSurrogate() == true) shortened = shortened.dropLast(1)
        return prepare(shortened + suffix + extension, original.kind)
    }
}
