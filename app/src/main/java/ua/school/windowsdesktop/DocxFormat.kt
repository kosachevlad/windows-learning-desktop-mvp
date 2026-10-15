package ua.school.windowsdesktop

import android.text.Spanned
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.text.style.RelativeSizeSpan
import android.text.style.AlignmentSpan
import android.text.Layout
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Small OOXML writer/reader used for Word documents. It keeps the editor's common spans. */
object DocxFormat {
    fun write(value: CharSequence): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entry(zip, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            entry(zip, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            entry(zip, "word/document.xml", documentXml(value))
        }
        return out.toByteArray()
    }

    fun read(bytes: ByteArray): CharSequence {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name == "word/document.xml") {
                    return parseDocument(xml = zip.readBytes().toString(Charsets.UTF_8))
                }
            }
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun parseDocument(xml: String): CharSequence {
        val result = SpannableStringBuilder()
        paragraphAlignments.clear()
        val paragraphs = Regex("<w:p(?: [^>]*)?>(.*?)</w:p>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
        paragraphs.forEachIndexed { pIndex, paragraph ->
            if (pIndex > 0) result.append('\n')
            Regex("<w:jc[^>]*w:val=\"([^\"]+)\"").find(paragraph.groupValues[1])?.groupValues?.get(1)?.let { value ->
                val alignment = when (value) {
                    "center" -> Layout.Alignment.ALIGN_CENTER
                    "right", "end" -> Layout.Alignment.ALIGN_OPPOSITE
                    "both" -> Layout.Alignment.ALIGN_NORMAL
                    else -> Layout.Alignment.ALIGN_NORMAL
                }
                val paragraphStart = result.length
                // Applied after the runs below, when the paragraph end is known.
                paragraphAlignments.add(ParagraphAlignment(paragraphStart, alignment))
                if (value == "both") result.setSpan(JustifyParagraphSpan(), paragraphStart, paragraphStart, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            Regex("<w:r(?: [^>]*)?>(.*?)</w:r>", RegexOption.DOT_MATCHES_ALL).findAll(paragraph.groupValues[1]).forEach { run ->
                val body = run.groupValues[1]
                val raw = Regex("<w:t(?: [^>]*)?>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1).orEmpty()
                val start = result.length
                result.append(unxml(raw))
                val end = result.length
                if (start == end) return@forEach
                if (body.contains("<w:b")) result.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (body.contains("<w:i")) result.setSpan(StyleSpan(android.graphics.Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (body.contains("<w:u")) result.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                Regex("<w:color[^>]*w:val=\"([0-9A-Fa-f]{6})\"").find(body)?.groupValues?.get(1)?.let { result.setSpan(ForegroundColorSpan(android.graphics.Color.parseColor("#$it")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
                Regex("<w:sz[^>]*w:val=\"(\\d+)\"").find(body)?.groupValues?.get(1)?.toIntOrNull()?.let { result.setSpan(AbsoluteSizeSpan((it / 2).coerceAtLeast(1), true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
                Regex("<w:rFonts[^>]*w:ascii=\"([^\"]+)\"").find(body)?.groupValues?.get(1)?.let { result.setSpan(TypefaceSpan(it), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
            }
        }
        paragraphAlignments.forEach { item ->
            val end = result.indexOf("\n", item.start).let { if (it < 0) result.length else it }
            if (end > item.start) result.setSpan(AlignmentSpan.Standard(item.alignment), item.start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return result
    }

    private data class ParagraphAlignment(val start: Int, val alignment: Layout.Alignment)
    private val paragraphAlignments = mutableListOf<ParagraphAlignment>()

    private fun documentXml(value: CharSequence): String {
        val s = value as? Spanned
        val text = value.toString()
        val body = StringBuilder()
        var paragraph = StringBuilder()
        fun flush(start: Int, end: Int) {
            val alignment = s?.getSpans(start, end, AlignmentSpan.Standard::class.java)?.firstOrNull()?.alignment
            var value = when (alignment) {
                Layout.Alignment.ALIGN_CENTER -> "center"
                Layout.Alignment.ALIGN_OPPOSITE -> "right"
                else -> "left"
            }
            val justify = s?.getSpans(start, end, JustifyParagraphSpan::class.java)?.isNotEmpty() == true
            if (justify) value = "both"
            val paragraphXml = "<w:p><w:pPr><w:jc w:val=\"$value\"/><w:spacing w:after=\"0\"/></w:pPr>" + paragraph + "</w:p>"
            body.append(paragraphXml)
            paragraph = StringBuilder()
        }
        var paragraphStart = 0
        for (i in text.indices) {
            if (text[i] == '\n') { flush(paragraphStart, i); paragraphStart = i + 1; continue }
            val end = i + 1
            val styles = s?.getSpans(i, end, Any::class.java).orEmpty()
            val props = StringBuilder()
            styles.filterIsInstance<StyleSpan>().forEach { if (it.style == android.graphics.Typeface.BOLD || it.style == android.graphics.Typeface.BOLD_ITALIC) props.append("<w:b/>"); if (it.style == android.graphics.Typeface.ITALIC || it.style == android.graphics.Typeface.BOLD_ITALIC) props.append("<w:i/>") }
            if (styles.any { it is UnderlineSpan }) props.append("<w:u w:val=\"single\"/>")
            styles.filterIsInstance<ForegroundColorSpan>().firstOrNull()?.let { props.append("<w:color w:val=\"%06X\"/>".format(it.foregroundColor and 0xFFFFFF)) }
            styles.filterIsInstance<AbsoluteSizeSpan>().firstOrNull()?.let { props.append("<w:sz w:val=\"${it.size * 2}\"/>") }
            styles.filterIsInstance<RelativeSizeSpan>().firstOrNull()?.let { props.append("<w:sz w:val=\"${(it.sizeChange * 22f).toInt().coerceAtLeast(2)}\"/>") }
            styles.filterIsInstance<TypefaceSpan>().firstOrNull()?.family?.let { family -> props.append("<w:rFonts w:ascii=\"${xml(family)}\" w:hAnsi=\"${xml(family)}\"/>") }
            paragraph.append("<w:r>").append(if (props.isNotEmpty()) "<w:rPr>$props</w:rPr>" else "").append("<w:t xml:space=\"preserve\">").append(xml(text[i].toString())).append("</w:t></w:r>")
        }
        if (paragraph.isNotEmpty() || text.isEmpty()) flush(paragraphStart, text.length)
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>$body</w:body></w:document>"
    }
    private fun entry(zip: ZipOutputStream, name: String, data: String) { zip.putNextEntry(ZipEntry(name)); zip.write(data.toByteArray()); zip.closeEntry() }
    private fun xml(v: String) = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    private fun unxml(v: String) = v.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
}
