package ua.school.windowsdesktop.domain

import org.json.JSONObject

/** A document model that keeps formatting ranges separate from the plain text. */
data class RichTextDocument(
    val text: String,
    val spans: List<RichTextSpan> = emptyList(),
)

data class RichTextSpan(
    val start: Int,
    val end: Int,
    val fontFamily: String = "Calibri",
    val fontSize: Int = 11,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val color: Long = 0xFF000000,
)

/** Compact JSON storage for rich documents; plain text files remain readable. */
object RichTextCodec {
    private const val PREFIX = "WL-RICH-1\n"

    fun encode(document: RichTextDocument): String {
        val spans = document.spans.joinToString(",") {
            "{\"s\":${it.start},\"e\":${it.end},\"f\":\"${escape(it.fontFamily)}\",\"z\":${it.fontSize},\"b\":${it.bold},\"i\":${it.italic},\"u\":${it.underline},\"c\":${it.color}}"
        }
        return PREFIX + "{\"text\":\"${escape(document.text)}\",\"spans\":[$spans]}"
    }

    fun decode(value: String): RichTextDocument? {
        if (!value.startsWith(PREFIX)) return null
        return runCatching {
            val root = JSONObject(value.removePrefix(PREFIX))
            val array = root.optJSONArray("spans")
            val spans = buildList {
                for (index in 0 until (array?.length() ?: 0)) {
                    val span = array!!.getJSONObject(index)
                    add(RichTextSpan(span.getInt("s"), span.getInt("e"), span.optString("f", "Calibri"), span.optInt("z", 11), span.optBoolean("b"), span.optBoolean("i"), span.optBoolean("u"), span.optLong("c", 0xFF000000)))
                }
            }
            RichTextDocument(root.optString("text"), spans)
        }.getOrNull()
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
